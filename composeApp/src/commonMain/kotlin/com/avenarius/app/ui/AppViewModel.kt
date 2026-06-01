package com.avenarius.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avenarius.app.data.Prefs
import com.avenarius.app.model.Account
import com.avenarius.app.model.Chat
import com.avenarius.app.model.Message
import com.avenarius.app.model.MessageStatus
import com.avenarius.app.net.MaxClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Screen { LOADING, LOGIN, CODE, PASSWORD, REGISTER, CHATS, CHAT }

data class AppState(
    val screen: Screen = Screen.LOADING,
    val busy: Boolean = false,
    val error: String? = null,
    val account: Account? = null,
    val chats: List<Chat> = emptyList(),
    val currentChat: Chat? = null,
    val messages: List<Message> = emptyList(),
    val codeLength: Int = 6,
    /** True while a pull-to-refresh re-sync of the chat list is in flight. */
    val refreshing: Boolean = false,
    /** userId -> display name, for avatars and sender labels. */
    val contacts: Map<Long, String> = emptyMap(),
    /** Unread count of the open chat at the moment it was opened (for the divider). */
    val openUnreadCount: Int = 0,
)

/**
 * Holds all app state and drives the [MaxClient]. Lives in commonMain, so the
 * exact same logic runs on Android and on desktop.
 */
class AppViewModel(
    private val prefs: Prefs,
    // The client is app-scoped (shared with the background service), so the
    // ViewModel must NOT create or tear it down — it's injected.
    private val client: MaxClient,
) : ViewModel() {

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var pendingPhone: String? = null

    init {
        // Forward server-pushed messages into whichever chat is open.
        viewModelScope.launch {
            client.incoming.collect { msg ->
                val openChatId = _state.value.currentChat?.id
                _state.update { s ->
                    if (s.currentChat?.id == msg.chatId && s.messages.none { it.id == msg.id }) {
                        s.copy(messages = s.messages + msg)
                    } else s
                }
                // We're looking at this chat -> immediately mark the new message read.
                // Use max(now, msg.time) so device-clock skew can't make the mark
                // fall short of the (server-timestamped) message.
                val mid = msg.id
                if (openChatId == msg.chatId && mid != null) {
                    runCatching { client.markRead(msg.chatId, mid, maxOf(nowMillis(), msg.time)) }
                }
            }
        }
        // Apply read-mark updates (op130): turn our sent messages ✓ -> ✓✓ live.
        viewModelScope.launch {
            client.readMarks.collect { rm ->
                val myId = _state.value.account?.userId ?: return@collect
                // Only the OTHER party reading our messages flips them to ✓✓.
                if (rm.userId == myId) return@collect
                _state.update { s ->
                    // Persist the new read mark on the chat so re-opening it (same
                    // session) still shows ✓✓ — not just the currently open messages.
                    val chats = s.chats.map {
                        if (it.id == rm.chatId) it.copy(otherReadMark = maxOf(it.otherReadMark, rm.mark)) else it
                    }
                    val current = s.currentChat?.let {
                        if (it.id == rm.chatId) it.copy(otherReadMark = maxOf(it.otherReadMark, rm.mark)) else it
                    }
                    val messages = if (s.currentChat?.id == rm.chatId) {
                        s.messages.map { m ->
                            if (m.senderId == myId && m.time <= rm.mark && m.status != MessageStatus.READ) {
                                m.copy(status = MessageStatus.READ)
                            } else m
                        }
                    } else s.messages
                    s.copy(chats = chats, currentChat = current, messages = messages)
                }
            }
        }
        // Auto-login if we already have a token.
        viewModelScope.launch {
            val token = prefs.token
            if (token == null) {
                _state.update { it.copy(screen = Screen.LOGIN) }
            } else {
                connectAndSync(token)
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private var passwordTrackId: String? = null

    fun requestCode(phone: String) = run {
        // Server expects clean international format, e.g. +79991234567.
        val normalized = "+" + phone.filter { it.isDigit() }
        pendingPhone = normalized
        launchBusy {
            client.connect(prefs.deviceId, prefs.mtInstance)
            val len = client.startAuth(normalized)
            _state.update { it.copy(screen = Screen.CODE, codeLength = len) }
        }
    }

    fun submitCode(code: String) = launchBusy {
        when (val result = client.checkCode(code)) {
            is MaxClient.CodeResult.Success -> {
                prefs.token = result.loginToken
                connectAndSync(result.loginToken)
            }
            is MaxClient.CodeResult.NeedPassword -> {
                passwordTrackId = result.trackId
                _state.update { it.copy(screen = Screen.PASSWORD) }
            }
            MaxClient.CodeResult.NeedRegister -> {
                _state.update { it.copy(screen = Screen.REGISTER) }
            }
        }
    }

    fun submitRegister(firstName: String) = launchBusy {
        val token = client.register(firstName.trim())
        prefs.token = token
        connectAndSync(token)
    }

    fun submitPassword(password: String) = launchBusy {
        val trackId = passwordTrackId ?: error("Нет идентификатора пароля")
        val token = client.checkPassword(password, trackId)
        prefs.token = token
        connectAndSync(token)
    }

    private suspend fun connectAndSync(token: String) {
        // The shared client may already be connected (and already synced once) from
        // a previous Activity/background session, and sync is once-per-connection —
        // so always (re)connect fresh here.
        client.disconnect()
        client.connect(prefs.deviceId, prefs.mtInstance)
        val result = client.sync(token)
        prefs.userId = result.account.userId
        _state.update {
            it.copy(
                screen = Screen.CHATS,
                account = result.account,
                chats = result.chats,
                contacts = result.contacts,
                busy = false,
                error = null,
            )
        }
        // If a notification asked us to open a specific chat, do it now.
        val pending = pendingOpenChatId
        if (pending != null) {
            pendingOpenChatId = null
            result.chats.firstOrNull { it.id == pending }?.let { openChat(it) }
        }
    }

    private var pendingOpenChatId: Long? = null

    /** Opens a chat by id (used when a message notification is tapped). */
    fun openChatById(id: Long) {
        val chat = _state.value.chats.firstOrNull { it.id == id }
        if (chat != null) openChat(chat) else pendingOpenChatId = id // open once chats load
    }

    fun openChat(chat: Chat) {
        // Snapshot the unread count now (for the "new messages" divider), since
        // we're about to clear it by marking the chat read.
        _state.update {
            it.copy(
                screen = Screen.CHAT,
                currentChat = chat,
                messages = emptyList(),
                openUnreadCount = chat.unreadCount,
            )
        }
        launchBusy {
            val myId = _state.value.account?.userId
            val history = client.fetchHistory(chat.id, fromTime = nowMillis(), count = 50)
                .map { m ->
                    // Reconstruct ✓✓ on relaunch: our messages the other side has
                    // already read (time <= their read mark) are READ.
                    if (myId != null && m.senderId == myId && chat.otherReadMark >= m.time &&
                        chat.otherReadMark > 0 && m.status != MessageStatus.READ
                    ) m.copy(status = MessageStatus.READ) else m
                }
            _state.update { it.copy(messages = history) }
            // Mark read on the server and clear the local unread badge.
            history.lastOrNull()?.let { last ->
                val lastId = last.id
                if (lastId != null) {
                    runCatching { client.markRead(chat.id, lastId, maxOf(nowMillis(), last.time)) }
                }
            }
            _state.update { s ->
                s.copy(chats = s.chats.map { if (it.id == chat.id) it.copy(unreadCount = 0) else it })
            }
        }
    }

    fun backToChats() {
        _state.update { it.copy(screen = Screen.CHATS, currentChat = null, messages = emptyList()) }
    }

    /** Resolves a phone number to a Max user and opens a dialog with them. */
    fun startChatByPhone(phone: String) {
        val normalized = "+" + phone.filter { it.isDigit() }
        val myId = _state.value.account?.userId ?: return
        launchBusy {
            val found = client.findByPhone(normalized)
            runCatching { client.addContact(found.userId, found.name) }
            _state.update { it.copy(contacts = it.contacts + (found.userId to found.name)) }
            val chat = Chat(
                id = client.dialogChatId(myId, found.userId),
                title = found.name,
                lastMessageText = null,
                lastEventTime = nowMillis(),
                unreadCount = 0,
                isDialog = true,
            )
            openChat(chat)
        }
    }

    /**
     * Handles the platform "back" action. Returns the user one screen up the
     * stack instead of leaving the app. The chat list / login are roots, so
     * [PlatformBackHandler] is disabled there and the system handles it (exit).
     */
    fun onBack() {
        when (_state.value.screen) {
            Screen.CHAT -> backToChats()
            Screen.CODE, Screen.PASSWORD -> _state.update { it.copy(screen = Screen.LOGIN, error = null) }
            else -> Unit
        }
    }

    /** True when there is a screen to go back to (so we should intercept "back"). */
    fun canGoBack(screen: Screen): Boolean =
        screen == Screen.CHAT || screen == Screen.CODE || screen == Screen.PASSWORD

    /** Pull-to-refresh on the chat list: re-runs sync over the open connection. */
    fun refresh() {
        val token = prefs.token ?: return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            try {
                // The server allows sync (op 19) only ONCE per connection, so a
                // refresh re-establishes a fresh session. This also recovers if the
                // previous connection had dropped.
                client.disconnect()
                client.connect(prefs.deviceId, prefs.mtInstance)
                val result = client.sync(token)
                _state.update {
                    it.copy(chats = result.chats, account = result.account, refreshing = false, error = null)
                }
            } catch (e: Throwable) {
                _state.update { it.copy(refreshing = false, error = e.message ?: "Ошибка обновления") }
            }
        }
    }

    fun sendMessage(text: String) {
        val chat = _state.value.currentChat ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val cid = nowMillis()
        launchBusyless {
            val sent = client.sendMessage(chat.id, trimmed, cid)
            if (sent != null) {
                _state.update { s ->
                    if (s.messages.any { it.id == sent.id }) s
                    else s.copy(messages = s.messages + sent)
                }
            }
        }
    }

    fun logout() {
        prefs.clear()
        client.disconnect()
        _state.update {
            AppState(screen = Screen.LOGIN)
        }
    }

    // --- helpers ---

    private fun launchBusy(block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Throwable) {
                _state.update { it.copy(busy = false, error = e.message ?: "Ошибка") }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    /** Like [launchBusy] but does not toggle the global busy spinner (for sends). */
    private fun launchBusyless(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.message ?: "Ошибка отправки") }
            }
        }
    }

    // NOTE: we intentionally do NOT disconnect in onCleared — the client is
    // app-scoped and kept alive by the background service. Only logout disconnects.
}
