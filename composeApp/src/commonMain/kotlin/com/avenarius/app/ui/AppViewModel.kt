package com.avenarius.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avenarius.app.data.Prefs
import com.avenarius.app.model.Account
import com.avenarius.app.model.Chat
import com.avenarius.app.model.MediaAttach
import com.avenarius.app.model.MediaType
import com.avenarius.app.model.Message
import com.avenarius.app.model.MessageStatus
import com.avenarius.app.model.Reaction
import com.avenarius.app.model.SearchResult
import com.avenarius.app.model.UserInfo
import com.avenarius.app.net.CodeResult
import com.avenarius.app.net.MaxApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Screen { LOADING, LOGIN, CODE, PASSWORD, REGISTER, CHATS, CHAT, USER }

/** Bottom-navigation tabs on the main (CHATS) screen. */
enum class Tab { CHATS, CONTACTS, SETTINGS }

/** Full-screen media viewer overlay state. */
sealed interface MediaViewer {
    data object Loading : MediaViewer

    data class Image(
        val url: String,
    ) : MediaViewer

    data class Video(
        val url: String,
    ) : MediaViewer
}

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
    /** True while older messages are being loaded (scroll-up pagination). */
    val loadingOlder: Boolean = false,
    /** True once the top of the chat history has been reached. */
    val noMoreOlder: Boolean = false,
    /** True while the app is transparently re-establishing a dropped connection. */
    val reconnecting: Boolean = false,
    /** Non-null when a full-screen image/video viewer is open. */
    val mediaViewer: MediaViewer? = null,
    /** Optional server-provided hint shown on the password screen. */
    val passwordHint: String? = null,
    /** Selected bottom-nav tab on the main screen. */
    val tab: Tab = Tab.CHATS,
    /** Full contact list for the Contacts tab. */
    val contactsList: List<UserInfo> = emptyList(),
    /** Ids of users currently online (for the green presence dot). */
    val onlineUsers: Set<Long> = emptySet(),
    /** The user whose profile page is open (Screen.USER). */
    val viewingUser: UserInfo? = null,
    /** Live results while searching by name in the new-chat dialog. */
    val searchResults: List<SearchResult> = emptyList(),
    val searching: Boolean = false,
    /** The message currently being replied to (shown as a banner above the input). */
    val replyingTo: Message? = null,
)

/**
 * Holds all app state and drives the [MaxClient]. Lives in commonMain, so the
 * exact same logic runs on Android and on desktop.
 */
class AppViewModel(
    private val prefs: Prefs,
    // The client is app-scoped (shared with the background service), so the
    // ViewModel must NOT create or tear it down — it's injected.
    private val client: MaxApi,
) : ViewModel() {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var pendingPhone: String? = null

    init {
        // Forward server-pushed messages into whichever chat is open AND keep the
        // chat-list row live (preview text, timestamp, unread badge, ordering).
        viewModelScope.launch {
            client.incoming.collect { msg ->
                val openChatId = _state.value.currentChat?.id
                val isOpen = openChatId == msg.chatId
                val fromMe =
                    _state.value.account
                        ?.userId
                        ?.let { it == msg.senderId } ?: false
                val alreadyShown = isOpen && _state.value.messages.any { it.id != null && it.id == msg.id }
                _state.update { s ->
                    // Replace an existing message (picks up edits/reaction changes if
                    // they arrive as op128) or append a genuinely new one.
                    val messages =
                        when {
                            !isOpen -> s.messages
                            alreadyShown -> s.messages.map { if (it.id == msg.id) msg else it }
                            else -> s.messages + msg
                        }
                    val chats =
                        s.chats
                            .map { c ->
                                if (c.id != msg.chatId) {
                                    c
                                } else {
                                    c.copy(
                                        lastMessageText = msg.text.ifBlank { c.lastMessageText },
                                        lastEventTime = maxOf(c.lastEventTime, msg.time),
                                        // Bump the badge only for chats we aren't looking at,
                                        // and never for our own (echoed) messages.
                                        unreadCount = if (!isOpen && !fromMe) c.unreadCount + 1 else c.unreadCount,
                                    )
                                }
                            }.sortedByDescending { it.lastEventTime }
                    s.copy(messages = messages, chats = chats)
                }
                // We're looking at this chat -> immediately mark the new message read.
                // Use max(now, msg.time) so device-clock skew can't make the mark
                // fall short of the (server-timestamped) message.
                val mid = msg.id
                if (isOpen && mid != null) {
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
                    val chats =
                        s.chats.map {
                            if (it.id == rm.chatId) it.copy(otherReadMark = maxOf(it.otherReadMark, rm.mark)) else it
                        }
                    val current =
                        s.currentChat?.let {
                            if (it.id == rm.chatId) it.copy(otherReadMark = maxOf(it.otherReadMark, rm.mark)) else it
                        }
                    val messages =
                        if (s.currentChat?.id == rm.chatId) {
                            s.messages.map { m ->
                                if (m.senderId == myId && m.time <= rm.mark && m.status != MessageStatus.READ) {
                                    m.copy(status = MessageStatus.READ)
                                } else {
                                    m
                                }
                            }
                        } else {
                            s.messages
                        }
                    s.copy(chats = chats, currentChat = current, messages = messages)
                }
            }
        }
        // Live presence: flip the green dot on/off as contacts come and go.
        viewModelScope.launch {
            client.presence.collect { p ->
                _state.update {
                    it.copy(
                        onlineUsers = if (p.online) it.onlineUsers + p.userId else it.onlineUsers - p.userId,
                    )
                }
            }
        }
        // Live reaction counts (op155): the push has counts only, so we keep our own
        // reaction flag from local state and just refresh the numbers/emojis.
        viewModelScope.launch {
            client.reactionUpdates.collect { u ->
                _state.update { s ->
                    if (s.currentChat?.id != u.chatId) {
                        s
                    } else {
                        s.copy(
                            messages =
                                s.messages.map { m ->
                                    if (m.id != u.messageId) {
                                        m
                                    } else {
                                        val mineEmoji = m.reactions.firstOrNull { it.mine }?.emoji
                                        m.copy(reactions = u.reactions.map { it.copy(mine = it.emoji == mineEmoji) })
                                    }
                                },
                        )
                    }
                }
            }
        }
        // Live chat-list updates (op135): a chat created/updated (added to a group,
        // a new dialog from someone) appears immediately instead of only after sync.
        viewModelScope.launch {
            client.chatUpdates.collect { chat ->
                _state.update { s ->
                    // Resolve a dialog's title from our contacts if the push lacked one.
                    val resolved =
                        if (chat.isDialog && chat.title.startsWith("Диалог ") && s.account != null) {
                            s.contacts[chat.id xor s.account.userId]?.let { chat.copy(title = it) } ?: chat
                        } else {
                            chat
                        }
                    val merged =
                        if (s.chats.any { it.id == chat.id }) {
                            s.chats.map { if (it.id == chat.id) resolved else it }
                        } else {
                            s.chats + resolved
                        }
                    s.copy(chats = merged.sortedByDescending { it.lastEventTime })
                }
            }
        }
        // Auto-reconnect transparently whenever the connection drops.
        viewModelScope.launch {
            client.drops.collect {
                val screen = _state.value.screen
                if (prefs.token != null && (screen == Screen.CHATS || screen == Screen.CHAT)) {
                    connectWithRetry(freshSession = false)
                }
            }
        }
        // Auto-login if we already have a token.
        if (prefs.token == null) {
            _state.update { it.copy(screen = Screen.LOGIN) }
        } else {
            connectWithRetry(freshSession = true)
        }
    }

    private var connectJob: Job? = null

    /**
     * Establishes the session and keeps retrying on transient failures (with
     * backoff) instead of bouncing to login. Only a real auth rejection logs out.
     * [freshSession] forces a clean reconnect (needed when a previous session may
     * still be alive, since sync is once-per-connection).
     */
    private fun connectWithRetry(freshSession: Boolean) {
        if (connectJob?.isActive == true) return
        val token = prefs.token ?: return
        connectJob =
            viewModelScope.launch {
                if (freshSession && client.isConnected) client.disconnect()
                _state.update { it.copy(reconnecting = true) }
                var backoff = 1_000L
                while (isActive) {
                    try {
                        client.connect(prefs.deviceId, prefs.mtInstance)
                        val result = client.sync(token)
                        result.refreshedToken?.let { if (it != prefs.token) prefs.token = it }
                        prefs.userId = result.account.userId
                        _state.update { s ->
                            s.copy(
                                screen = if (s.screen == Screen.LOADING) Screen.CHATS else s.screen,
                                account = result.account,
                                chats = result.chats,
                                contacts = result.contacts,
                                contactsList = result.contactsList,
                                onlineUsers = result.online,
                                reconnecting = false,
                                busy = false,
                                error = null,
                            )
                        }
                        _state.value.currentChat?.let { reloadOpenChat(it) } // catch up missed messages
                        return@launch
                    } catch (e: Throwable) {
                        val msg = e.message ?: "Ошибка"
                        if (msg.contains("вход", true) || msg.contains("авториз", true)) {
                            // Genuine auth rejection -> the token is dead, must re-login.
                            prefs.clear()
                            client.disconnect()
                            _state.update { AppState(screen = Screen.LOGIN, error = msg) }
                            return@launch
                        }
                        // Transient (connectivity/other): keep retrying with backoff.
                        _state.update { it.copy(reconnecting = true) }
                        delay(backoff)
                        backoff = (backoff * 2).coerceAtMost(20_000L)
                    }
                }
            }
    }

    private fun reloadOpenChat(chat: Chat) {
        viewModelScope.launch {
            runCatching {
                val history = withReadMarks(client.fetchHistory(chat.id, fromTime = nowMillis(), count = 50), chat)
                _state.update { if (it.currentChat?.id == chat.id) it.copy(messages = history) else it }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private var passwordTrackId: String? = null

    fun requestCode(phone: String) =
        run {
            // Server expects clean international format, e.g. +79991234567.
            val normalized = "+" + phone.filter { it.isDigit() }
            pendingPhone = normalized
            launchBusy {
                client.connect(prefs.deviceId, prefs.mtInstance)
                val len = client.startAuth(normalized)
                _state.update { it.copy(screen = Screen.CODE, codeLength = len) }
            }
        }

    fun submitCode(code: String) =
        launchBusy {
            when (val result = client.checkCode(code)) {
                is CodeResult.Success -> {
                    prefs.token = result.loginToken
                    connectAndSync(result.loginToken)
                }
                is CodeResult.NeedPassword -> {
                    passwordTrackId = result.trackId
                    _state.update { it.copy(screen = Screen.PASSWORD, passwordHint = result.hint) }
                }
                CodeResult.NeedRegister -> {
                    _state.update { it.copy(screen = Screen.REGISTER) }
                }
            }
        }

    fun submitRegister(firstName: String) =
        launchBusy {
            val token = client.register(firstName.trim())
            prefs.token = token
            connectAndSync(token)
        }

    fun submitPassword(password: String) =
        launchBusy {
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
        // Token refresh: the server rolls the login token on each sync. Persist the
        // new one so it doesn't expire between launches and force a re-login.
        result.refreshedToken?.let { if (it != prefs.token) prefs.token = it }
        prefs.userId = result.account.userId
        _state.update {
            it.copy(
                screen = Screen.CHATS,
                account = result.account,
                chats = result.chats,
                contacts = result.contacts,
                contactsList = result.contactsList,
                onlineUsers = result.online,
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
                loadingOlder = false,
                noMoreOlder = false,
            )
        }
        launchBusy {
            val history =
                withReadMarks(
                    client.fetchHistory(chat.id, fromTime = nowMillis(), count = 50),
                    chat,
                )
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

    fun selectTab(tab: Tab) = _state.update { it.copy(tab = tab) }

    /** Opens a user's profile page (from a chat, the chat list, or contacts). */
    fun openUser(userId: Long) {
        // Seed from cached info, then fetch full details.
        val cached =
            _state.value.contactsList.firstOrNull { it.id == userId }
                ?: _state.value.contacts[userId]?.let { UserInfo(userId, it) }
        _state.update { it.copy(screen = Screen.USER, viewingUser = cached) }
        viewModelScope.launch {
            runCatching { client.fetchUser(userId) }.getOrNull()?.let { full ->
                _state.update { if (it.screen == Screen.USER) it.copy(viewingUser = full) else it }
            }
        }
    }

    fun closeUser() {
        // Return to the conversation if one is open, otherwise the main screen.
        _state.update {
            it.copy(screen = if (it.currentChat != null) Screen.CHAT else Screen.CHATS, viewingUser = null)
        }
    }

    /** Searches by name for the new-chat dialog: your contacts (instant) + public chats. */
    fun searchUsers(query: String) {
        val q = query.trim()
        if (q.isBlank()) {
            _state.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }
        val myId = _state.value.account?.userId
        // Local contacts matching the query, opened as 1:1 dialogs.
        val local =
            if (myId == null) {
                emptyList()
            } else {
                _state.value.contactsList
                    .filter { it.name.contains(q, ignoreCase = true) }
                    .map { SearchResult(client.dialogChatId(myId, it.id), it.name, it.avatarUrl, isDialog = true) }
            }
        _state.update { it.copy(searchResults = local, searching = true) }
        viewModelScope.launch {
            val remote = runCatching { client.searchChats(q) }.getOrDefault(emptyList())
            _state.update { it.copy(searchResults = (local + remote).distinctBy { r -> r.chatId }, searching = false) }
        }
    }

    fun clearSearch() = _state.update { it.copy(searchResults = emptyList(), searching = false) }

    /** Opens a chat/channel chosen from search results. */
    fun openSearchResult(result: SearchResult) {
        openChat(
            Chat(
                id = result.chatId,
                title = result.title,
                lastMessageText = null,
                lastEventTime = nowMillis(),
                unreadCount = 0,
                isDialog = result.isDialog,
            ),
        )
    }

    /** Opens (or starts) a dialog with a user found via search/contacts. */
    fun openDialogWith(user: UserInfo) {
        val myId = _state.value.account?.userId ?: return
        _state.update { it.copy(contacts = it.contacts + (user.id to user.name)) }
        val chat =
            Chat(
                id = client.dialogChatId(myId, user.id),
                title = user.name,
                lastMessageText = null,
                lastEventTime = nowMillis(),
                unreadCount = 0,
                isDialog = true,
            )
        openChat(chat)
    }

    /** Opens the full-screen viewer for a tapped photo/video. */
    fun openMedia(
        media: MediaAttach,
        messageId: String?,
    ) {
        when (media.type) {
            MediaType.PHOTO -> _state.update { it.copy(mediaViewer = MediaViewer.Image(media.url)) }
            MediaType.VIDEO -> {
                val chat = _state.value.currentChat
                val mid = messageId?.toLongOrNull()
                if (chat == null || mid == null || media.videoId == 0L) {
                    _state.update { it.copy(mediaViewer = MediaViewer.Image(media.url)) } // fallback: thumbnail
                    return
                }
                _state.update { it.copy(mediaViewer = MediaViewer.Loading) }
                viewModelScope.launch {
                    val url = runCatching { client.getVideoUrl(chat.id, mid, media.videoId) }.getOrNull()
                    _state.update {
                        it.copy(mediaViewer = if (url != null) MediaViewer.Video(url) else MediaViewer.Image(media.url))
                    }
                }
            }
        }
    }

    /** Opens an arbitrary image URL (e.g. a profile avatar) in the full-screen viewer. */
    fun openImage(url: String) = _state.update { it.copy(mediaViewer = MediaViewer.Image(url)) }

    fun closeMedia() = _state.update { it.copy(mediaViewer = null) }

    /** Reconstructs ✓✓ on loaded history: our messages the other side has already read. */
    private fun withReadMarks(
        messages: List<Message>,
        chat: Chat,
    ): List<Message> {
        val myId = _state.value.account?.userId ?: return messages
        if (chat.otherReadMark <= 0) return messages
        return messages.map { m ->
            if (m.senderId == myId && m.time <= chat.otherReadMark && m.status != MessageStatus.READ) {
                m.copy(status = MessageStatus.READ)
            } else {
                m
            }
        }
    }

    /** Loads an older page of messages when the user scrolls to the top. */
    fun loadOlder() {
        val s = _state.value
        val chat = s.currentChat ?: return
        val oldest = s.messages.firstOrNull() ?: return
        if (s.loadingOlder || s.noMoreOlder) return
        _state.update { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            try {
                val older = client.fetchHistory(chat.id, fromTime = oldest.time, count = 50)
                val existingIds =
                    _state.value.messages
                        .mapNotNull { it.id }
                        .toSet()
                val fresh =
                    withReadMarks(
                        older.filter { it.time < oldest.time && (it.id == null || it.id !in existingIds) },
                        chat,
                    )
                _state.update {
                    it.copy(
                        messages = fresh + it.messages,
                        loadingOlder = false,
                        noMoreOlder = fresh.isEmpty(),
                    )
                }
            } catch (e: Throwable) {
                _state.update { it.copy(loadingOlder = false, error = e.message ?: "Не удалось загрузить историю") }
            }
        }
    }

    /** Resolves a phone number to a Max user and opens a dialog with them. */
    fun startChatByPhone(phone: String) {
        val normalized = "+" + phone.filter { it.isDigit() }
        val myId = _state.value.account?.userId ?: return
        launchBusy {
            val found = client.findByPhone(normalized)
            runCatching { client.addContact(found.userId, found.name) }
            _state.update { it.copy(contacts = it.contacts + (found.userId to found.name)) }
            val chat =
                Chat(
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
        val s = _state.value
        when (s.screen) {
            Screen.USER -> closeUser()
            Screen.CHAT -> backToChats()
            Screen.CODE, Screen.PASSWORD, Screen.REGISTER ->
                _state.update { it.copy(screen = Screen.LOGIN, error = null) }
            Screen.CHATS -> if (s.tab != Tab.CHATS) _state.update { it.copy(tab = Tab.CHATS) }
            else -> Unit
        }
    }

    /** True when there is a screen/tab to go back to (so we should intercept "back"). */
    fun canGoBack(
        screen: Screen,
        tab: Tab,
    ): Boolean =
        when (screen) {
            Screen.CHAT, Screen.USER, Screen.CODE, Screen.PASSWORD, Screen.REGISTER -> true
            Screen.CHATS -> tab != Tab.CHATS
            else -> false
        }

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
                result.refreshedToken?.let { if (it != prefs.token) prefs.token = it }
                _state.update {
                    it.copy(
                        chats = result.chats,
                        account = result.account,
                        contacts = result.contacts,
                        contactsList = result.contactsList,
                        onlineUsers = result.online,
                        refreshing = false,
                        error = null,
                    )
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
        val replyToId = _state.value.replyingTo?.id
        _state.update { it.copy(replyingTo = null) } // clear the reply banner on send
        launchBusyless {
            val sent = client.sendMessage(chat.id, trimmed, cid, replyToId)
            if (sent != null) {
                _state.update { s ->
                    if (s.messages.any { it.id == sent.id }) {
                        s
                    } else {
                        s.copy(messages = s.messages + sent)
                    }
                }
            }
        }
    }

    /** Begins replying to [msg] (shows a banner above the input). */
    fun startReply(msg: Message) = _state.update { it.copy(replyingTo = msg) }

    fun cancelReply() = _state.update { it.copy(replyingTo = null) }

    /**
     * Toggles our [emoji] reaction on [msg]: tapping the one we already chose removes
     * it. Updates the bubble optimistically, then tells the server.
     */
    fun toggleReaction(
        msg: Message,
        emoji: String,
    ) {
        val id = msg.id ?: return
        val chatId = msg.chatId
        // Tapping our current reaction clears it; otherwise it replaces/sets ours.
        val had = msg.reactions.any { it.mine && it.emoji == emoji }
        val target = if (had) null else emoji
        _state.update { s ->
            s.copy(messages = s.messages.map { if (it.id == id) applyMyReaction(it, target) else it })
        }
        launchBusyless { client.setReaction(chatId, id, target) }
    }

    /** Deletes the current 1:1 chat (removes it locally and returns to the list). */
    fun deleteCurrentChat() {
        val chat = _state.value.currentChat ?: return
        launchBusy {
            // Removes our own copy. Max doesn't allow deleting a dialog for the other
            // party (the official client can't either), so forAll stays false.
            client.deleteChat(chat.id, chat.lastEventTime, forAll = false)
            _state.update {
                it.copy(
                    screen = Screen.CHATS,
                    currentChat = null,
                    messages = emptyList(),
                    chats = it.chats.filterNot { c -> c.id == chat.id },
                )
            }
        }
    }

    /** Leaves the current group chat (removes it locally and returns to the list). */
    fun leaveCurrentGroup() {
        val chat = _state.value.currentChat ?: return
        launchBusy {
            client.leaveGroup(chat.id)
            _state.update {
                it.copy(
                    screen = Screen.CHATS,
                    currentChat = null,
                    messages = emptyList(),
                    chats = it.chats.filterNot { c -> c.id == chat.id },
                )
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

    /** Returns [msg] with our own reaction changed to [emoji] (or removed when null). */
    private fun applyMyReaction(
        msg: Message,
        emoji: String?,
    ): Message {
        if (msg.reactions.firstOrNull { it.mine }?.emoji == emoji) return msg
        // Drop our previous reaction (decrement, removing the bucket if it empties).
        var list =
            msg.reactions.mapNotNull { r ->
                if (!r.mine) {
                    r
                } else {
                    (r.count - 1).takeIf { it > 0 }?.let { r.copy(count = it, mine = false) }
                }
            }
        if (emoji != null) {
            val idx = list.indexOfFirst { it.emoji == emoji }
            list =
                if (idx >= 0) {
                    list.mapIndexed { i, r -> if (i == idx) r.copy(count = r.count + 1, mine = true) else r }
                } else {
                    list + Reaction(emoji, 1, mine = true)
                }
        }
        return msg.copy(reactions = list)
    }

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
