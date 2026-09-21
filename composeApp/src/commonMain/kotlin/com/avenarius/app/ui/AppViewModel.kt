package com.avenarius.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avenarius.app.data.AppCache
import com.avenarius.app.data.CachedSession
import com.avenarius.app.data.MessageCache
import com.avenarius.app.data.Prefs
import com.avenarius.app.model.Account
import com.avenarius.app.model.CallState
import com.avenarius.app.model.Chat
import com.avenarius.app.model.DeviceContact
import com.avenarius.app.model.FileAttach
import com.avenarius.app.model.MaxLink
import com.avenarius.app.model.MediaAttach
import com.avenarius.app.model.MediaContent
import com.avenarius.app.model.MediaType
import com.avenarius.app.model.Message
import com.avenarius.app.model.MessageStatus
import com.avenarius.app.model.OutAttach
import com.avenarius.app.model.PendingAttach
import com.avenarius.app.model.PickedKind
import com.avenarius.app.model.PickedMedia
import com.avenarius.app.model.Reaction
import com.avenarius.app.model.RecordedVoice
import com.avenarius.app.model.SearchResult
import com.avenarius.app.model.UploadState
import com.avenarius.app.model.UserInfo
import com.avenarius.app.model.parseMaxLink
import com.avenarius.app.model.previewLabel
import com.avenarius.app.net.CallEngine
import com.avenarius.app.net.CallSession
import com.avenarius.app.net.CodeResult
import com.avenarius.app.net.DemoMaxApi
import com.avenarius.app.net.FoundUser
import com.avenarius.app.net.MaxApi
import com.avenarius.app.ui.theme.ThemeMode
import com.avenarius.app.ui.theme.prefValue
import com.avenarius.app.ui.theme.themeModeOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { LOADING, LOGIN, CODE, PASSWORD, REGISTER, CHATS, CHAT, USER, SHARE_PICK, ABOUT, EDIT_PROFILE, GROUP }

/** Bottom-navigation tabs on the main (CHATS) screen. */
enum class Tab { CHATS, CONTACTS, SETTINGS }

/** Full-screen media viewer overlay state. */
sealed interface MediaViewer {
    /** The downloadable/shareable URL of the currently shown media (null while loading). */
    val url: String?
        get() = null

    /** The message this media came from, if any (enables "forward"); null for avatars. */
    val source: Message?
        get() = null

    /** True for video (affects the suggested filename/mime on download/share). */
    val isVideo: Boolean
        get() = false

    data object Loading : MediaViewer

    data class Image(
        override val url: String,
        override val source: Message? = null,
    ) : MediaViewer

    data class Video(
        override val url: String,
        override val source: Message? = null,
    ) : MediaViewer {
        override val isVideo = true
    }
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
    /** The group whose info page is open (Screen.GROUP). */
    val viewingGroup: Chat? = null,
    /** Members of [viewingGroup], resolved with names/avatars. */
    val groupMemberList: List<UserInfo> = emptyList(),
    /** True while [groupMemberList] is loading. */
    val groupMembersLoading: Boolean = false,
    /** Live results while searching by name in the new-chat dialog. */
    val searchResults: List<SearchResult> = emptyList(),
    val searching: Boolean = false,
    /** The message currently being replied to (shown as a banner above the input). */
    val replyingTo: Message? = null,
    /** True while a picked photo/video is being uploaded and sent. */
    val sendingAttachment: Boolean = false,
    /** Media shared in from another app, awaiting a chat pick (Screen.SHARE_PICK). */
    val sharePending: List<PickedMedia> = emptyList(),
    /** Media to pre-stage in the chat that's just been opened (e.g. from a share). */
    val stagedMedia: List<PickedMedia> = emptyList(),
    /** Resolved info for group senders who aren't in our contacts (name + avatar). */
    val groupMembers: Map<Long, UserInfo> = emptyMap(),
    /** Selected app theme (System/Dark/Light). */
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** A message awaiting a chat pick to forward it (Screen.SHARE_PICK). */
    val forwarding: Message? = null,
    /** True in the offline Google Play review "demo account" session. */
    val demoMode: Boolean = false,
    /** Transient message shown as a snackbar (e.g. "web login confirmed"). */
    val notice: String? = null,
    /**
     * A URL the app couldn't handle itself and wants opened in a browser. The UI
     * consumes it and calls [consumedExternalLink].
     */
    val openExternally: String? = null,
    /** The saved unsent text of the chat being opened (restored into the input). */
    val draft: String = "",
    /** Saved unsent text per chat id, previewed in the chat list ("Черновик: …"). */
    val drafts: Map<Long, String> = emptyMap(),
    /** fileId -> platform reference, for attachments already saved to the device. */
    val downloadedFiles: Map<Long, String> = emptyMap(),
    /** File attachments being fetched right now, as fileId -> fraction done (0..1). */
    val downloadingFiles: Map<Long, Float> = emptyMap(),
    /** Size of the cached conversation data (snapshot, drafts, message history). */
    val dataCacheBytes: Long = 0,
    /** Size of Coil's on-disk thumbnail cache — usually the bulk of it. */
    val imageCacheBytes: Long = 0,
    /**
     * Messages created locally whose attachments are still uploading, keyed by chat.
     *
     * Kept out of [messages] on purpose: that list is cleared whenever a chat is
     * left or opened, which would drop an in-flight send from view while it was
     * still running. These survive navigation and reappear with the chat.
     */
    val pendingSends: Map<Long, List<Message>> = emptyMap(),
    /** False until the first successful sync of this launch (drives the status bar text). */
    val syncedOnce: Boolean = false,
    /** The voice message playing right now, if any. */
    val playingVoice: PlayingVoice? = null,
    /** The round video message playing right now, if any. */
    val playingVideoNote: PlayingVideoNote? = null,
    /** The in-progress voice/video call (ringing, dialing, or active), or null. */
    val call: CallState? = null,
) {
    /**
     * The open chat's messages with its still-sending bubbles merged in.
     *
     * Ordered by time, not appended: a bubble that failed a while ago belongs where
     * it was written, so anything sent since shows up below it rather than above.
     */
    val visibleMessages: List<Message>
        get() {
            val pending = currentChat?.let { pendingSends[it.id] }.orEmpty()
            return if (pending.isEmpty()) messages else (messages + pending).sortedBy { it.time }
        }
}

/**
 * Holds all app state and drives the [MaxClient]. Lives in commonMain, so the
 * exact same logic runs on Android and on desktop.
 */
class AppViewModel(
    private val prefs: Prefs,
    // The client is app-scoped (shared with the background service), so the
    // ViewModel must NOT create or tear it down — it's injected.
    realClient: MaxApi,
    private val cache: AppCache = AppCache(prefs.storage),
    private val messageCache: MessageCache = MessageCache(prefs.storage),
    /** Pause before re-sending a recording the server wasn't ready for; 0 in tests. */
    private val voiceSendRetryDelayMs: Long = VOICE_SEND_RETRY_DELAY_MS,
    /** The audio player. Substituted in tests, which must not touch the real one. */
    private val voicePlayer: VoicePlayback = VoiceAudio,
) : ViewModel() {
    // Swappable: the demo login (Google Play review account) replaces this with an
    // offline [DemoMaxApi] so it never touches the real servers. [originalClient] is
    // the real one, restored on logout.
    private val originalClient: MaxApi = realClient
    private var client: MaxApi = realClient

    /** Orchestrates voice/video calls (STAGE 1 + STAGE 2 + WebRTC). */
    private val callSession = CallSession(realClient, ::nowMillis)

    /** The live media engine, for the call screen's video renderers. */
    fun currentCallEngine(): CallEngine? = callSession.engine

    private val _state = MutableStateFlow(AppState(theme = themeModeOf(prefs.theme)))
    val state: StateFlow<AppState> = _state.asStateFlow()

    /** Persists and applies the selected app theme. */
    fun setTheme(mode: ThemeMode) {
        prefs.theme = mode.prefValue()
        _state.update { it.copy(theme = mode) }
    }

    /**
     * Handles a link the user tapped (in a message) or opened from outside the app.
     *
     * A Max link is resolved in-app; anything else is left to the caller, which
     * returns false so it can hand the URL to the browser.
     */
    fun openLink(url: String): Boolean {
        when (val link = parseMaxLink(url) ?: return false) {
            is MaxLink.JoinCall -> _state.update { it.copy(notice = "Групповые звонки по ссылке пока не поддерживаются") }
            is MaxLink.Invite -> openInvite(link)
        }
        return true
    }

    // ---------------------------------------------------------------------
    // Calls
    // ---------------------------------------------------------------------

    /** Places an outgoing call to [userId]. Ignored in demo mode. */
    fun startCall(
        userId: Long,
        isVideo: Boolean,
    ) {
        if (_state.value.demoMode) {
            _state.update { it.copy(notice = "Звонки недоступны в демо-режиме") }
            return
        }
        val chatId = _state.value.currentChat?.id ?: userId
        val name = _state.value.contacts[userId] ?: _state.value.currentChat?.title
        val avatar =
            _state.value.contactsList
                .firstOrNull { it.id == userId }
                ?.avatarUrl
        callSession.placeCall(userId, chatId, isVideo, peerName = name, peerAvatarUrl = avatar)
    }

    fun acceptCall() = callSession.accept()

    fun declineCall() = callSession.decline()

    fun hangupCall() = callSession.hangup()

    fun toggleCallMic() = callSession.toggleMic()

    fun toggleCallCamera() = callSession.toggleCamera()

    fun switchCallCamera() = callSession.switchCamera()

    /** Dismisses an ENDED call from the UI. */
    fun dismissCall() = callSession.clear()

    /**
     * Opens what an invite points at: a chat we are already in, or one the server
     * lets us join.
     */
    private fun openInvite(invite: MaxLink.Invite) {
        // Already a member? Then this is just a pointer to a chat we have.
        knownChatFor(invite)?.let { chat ->
            openChat(chat)
            return
        }
        if (prefs.token == null) {
            // Not signed in yet: remember it and act once the session is up.
            pendingInvite = invite
            return
        }
        launchBusy {
            val outcome = joinByAnyForm(invite)
            val joined = outcome.getOrNull()
            if (outcome.isFailure) {
                // The server didn't recognise the invite in any form we know. Hand it
                // to the browser rather than leaving a tap that appears to do nothing.
                _state.update {
                    it.copy(
                        notice = "Ссылку не удалось открыть в приложении",
                        openExternally = invite.url,
                        error = null,
                    )
                }
                return@launchBusy
            }
            if (joined != null) {
                _state.update { s ->
                    val merged =
                        if (s.chats.any { it.id == joined.id }) {
                            s.chats.map { if (it.id == joined.id) joined else it }
                        } else {
                            s.chats + joined
                        }
                    s.copy(chats = merged.sortedByDescending { it.lastEventTime })
                }
                openChat(joined)
                return@launchBusy
            }
            // The server accepted the join but didn't describe the chat: re-sync and
            // find it by its link rather than guessing at the reply's shape. Awaited,
            // so the lookup below sees the fresh list.
            resync()
            val found = knownChatFor(invite)
            if (found != null) {
                openChat(found)
            } else {
                _state.update {
                    it.copy(notice = "Ссылку не удалось открыть в приложении", openExternally = invite.url)
                }
            }
        }
    }

    /**
     * Joins with whichever spelling of the invite the server accepts.
     *
     * The hash alone is what the official client sends, so it is tried first; the
     * other forms cover a deployment that wants the whole link. Only if every form is
     * refused does this fail — the caller then falls back to a browser.
     */
    private suspend fun joinByAnyForm(invite: MaxLink.Invite): Result<Chat?> {
        var last: Result<Chat?> = Result.failure(IllegalStateException("Ссылка не найдена"))
        for (form in listOf(invite.token, invite.url, invite.link).distinct()) {
            last = runCatching { client.joinByLink(form) }
            if (last.isSuccess) return last
        }
        return last
    }

    /** Called once the UI has opened [AppState.openExternally]. */
    fun consumedExternalLink() = _state.update { it.copy(openExternally = null) }

    /** A chat we already have whose public link points at the same thing as [invite]. */
    private fun knownChatFor(invite: MaxLink.Invite): Chat? =
        _state.value.chats.firstOrNull { chat ->
            val parsed = chat.link?.let { parseMaxLink(it) }
            parsed is MaxLink.Invite && parsed.token == invite.token
        }

    // An invite that arrived before the session was up.
    private var pendingInvite: MaxLink.Invite? = null

    /** Opens the "About" screen. */
    fun openAbout() = _state.update { it.copy(screen = Screen.ABOUT) }

    /** Opens the edit-profile screen (for the signed-in user). */
    fun openEditProfile() = _state.update { it.copy(screen = Screen.EDIT_PROFILE) }

    /**
     * Saves the profile: uploads [avatar] (if any) as the new photo, then PROFILE-updates
     * name + bio. On success refreshes our account/profile and returns to the profile.
     */
    fun saveProfile(
        firstName: String,
        lastName: String,
        description: String,
        avatar: PickedMedia?,
    ) {
        val myId = _state.value.account?.userId ?: return
        launchBusy {
            val token = avatar?.let { client.uploadPhoto(it.content, it.fileName, it.mime, profile = true).token }
            client.updateProfile(
                firstName.trim(),
                lastName.trim().ifBlank { null },
                description.trim().ifBlank { null },
                token,
            )
            // Re-fetch our own profile so the new avatar/bio show immediately.
            val me = runCatching { client.fetchUser(myId) }.getOrNull()
            _state.update { s ->
                s.copy(
                    screen = Screen.USER,
                    account =
                        s.account?.copy(
                            firstName = firstName.trim(),
                            lastName = lastName.trim().ifBlank { null },
                            avatarUrl = me?.avatarUrl ?: s.account.avatarUrl,
                        ),
                    viewingUser = me ?: s.viewingUser,
                )
            }
        }
    }

    private var pendingPhone: String? = null

    // Google Play review "demo account": this phone + code starts an offline session.
    private var demoPending = false

    /**
     * A media send in progress: its items and whatever has uploaded so far. Held
     * here rather than in [AppState] because it owns live content handles; the
     * state only carries what the bubble needs to draw itself.
     */
    private class OutgoingSend(
        val chatId: Long,
        val cid: Long,
        val caption: String,
        val replyToId: String?,
        val items: List<PickedMedia>,
        /** Set instead of [items] when this send is one self-contained recording. */
        val solo: SoloRecording? = null,
        /** Parallel to [items]; non-null once that item has uploaded. */
        val attaches: MutableList<OutAttach?> = MutableList(items.size) { null },
    ) {
        /** A solo send has one attachment, tracked separately from [attaches]. */
        var soloAttach: OutAttach? = null
    }

    /**
     * A recording sent on its own: a voice message or a round video message. They
     * differ only in which upload they use and how they are drawn, so they share the
     * send, retry and cancel machinery.
     */
    private class SoloRecording(
        val content: MediaContent,
        val durationSeconds: Int,
        val isVideoNote: Boolean,
    )

    // Retry material for the bubbles in [AppState.pendingSends], keyed by cid.
    private val outgoing = mutableMapOf<Long, OutgoingSend>()

    private var lastCid = 0L

    /**
     * A client id for an outgoing message. Wall-clock millis on its own can repeat —
     * two sends inside the same millisecond would share a cid, which the server uses
     * to deduplicate and we use to match a bubble to its echo — so it only ever
     * moves forward.
     */
    private fun nextCid(): Long {
        val now = nowMillis()
        lastCid = if (now > lastCid) now else lastCid + 1
        return lastCid
    }

    // cids with an upload/send job running, so a retry can't double-fire.
    private val activeSends = mutableMapOf<Long, Job>()

    // Unsent per-chat input, loaded once and written back on a debounce.
    private val drafts: MutableMap<Long, String> by lazy { cache.drafts().toMutableMap() }
    private var draftFlushJob: Job? = null

    init {
        // Mirror the call session's state into AppState for the call UI.
        viewModelScope.launch {
            callSession.state.collect { call -> _state.update { it.copy(call = call) } }
        }
        // An inbound call is ringing: hand it to the session, resolving caller name/avatar.
        viewModelScope.launch {
            originalClient.incomingCalls.collect { call ->
                val name = _state.value.contacts[call.callerId]
                val avatar =
                    _state.value.contactsList
                        .firstOrNull { it.id == call.callerId }
                        ?.avatarUrl
                callSession.onIncomingCall(call, peerName = name, peerAvatarUrl = avatar)
            }
        }
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
                    // Our own echo (matched on the cid we sent): the server's copy is
                    // now in the list, so the local bubble has done its job.
                    val prunedPending =
                        msg.cid?.let { cid ->
                            outgoing.remove(cid)
                            s.pendingSends.minusPending(msg.chatId, cid)
                        } ?: s.pendingSends
                    val known = s.chats.any { it.id == msg.chatId }
                    val updated =
                        if (known) {
                            s.chats.map { c ->
                                if (c.id != msg.chatId) {
                                    c
                                } else {
                                    c.copy(
                                        lastMessageText = msg.previewLabel().ifBlank { c.lastMessageText },
                                        lastEventTime = maxOf(c.lastEventTime, msg.time),
                                        // Bump the badge only for chats we aren't looking at,
                                        // and never for our own (echoed) messages.
                                        unreadCount = if (!isOpen && !fromMe) c.unreadCount + 1 else c.unreadCount,
                                    )
                                }
                            }
                        } else {
                            // A message for a chat not in our list: a dialog we deleted that
                            // the other side revived, or a new dialog whose NOTIF_CHAT we
                            // missed. Synthesize a row so it appears immediately (the title
                            // is refined on the next sync). For a 1:1 dialog the chatId is
                            // myId xor otherUserId, so it equals myId xor senderId here.
                            val myId = s.account?.userId
                            val isDialog = myId != null && msg.chatId == (myId xor msg.senderId)
                            val title =
                                if (isDialog) s.contacts[msg.senderId] ?: "Диалог ${msg.chatId}" else "Чат"
                            s.chats +
                                Chat(
                                    id = msg.chatId,
                                    title = title,
                                    lastMessageText = msg.previewLabel(),
                                    lastEventTime = msg.time,
                                    unreadCount = if (fromMe) 0 else 1,
                                    isDialog = isDialog,
                                )
                        }
                    s.copy(
                        messages = messages,
                        pendingSends = prunedPending,
                        chats = updated.sortedByDescending { it.lastEventTime },
                    )
                }
                // We're looking at this chat -> immediately mark the new message read.
                // Use max(now, msg.time) so device-clock skew can't make the mark
                // fall short of the (server-timestamped) message.
                val mid = msg.id
                if (isOpen && mid != null) {
                    runCatching { client.markRead(msg.chatId, mid, maxOf(nowMillis(), msg.time)) }
                }
                if (isOpen && !fromMe) resolveUnknownSenders(listOf(msg))
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
                // A pushed dialog from a non-contact still reads "Диалог <id>" — resolve it.
                resolveDialogTitles()
            }
        }
        // The other party deleted a message (op142): drop it from the open chat live.
        viewModelScope.launch {
            client.deletions.collect { d ->
                _state.update { s ->
                    if (s.currentChat?.id != d.chatId) {
                        s
                    } else {
                        s.copy(messages = s.messages.filterNot { it.id != null && it.id in d.messageIds })
                    }
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
        // Keep the warm-start snapshot fresh. Collected from the state (rather than
        // written at each mutation site) so every path that changes the chat list —
        // sync, live pushes, late dialog-title resolution — is covered. conflate()
        // plus a trailing delay throttles it to at most one write per interval.
        viewModelScope.launch {
            _state
                .map(::cacheableSnapshot)
                .distinctUntilChanged()
                .conflate()
                .collect { snapshot ->
                    if (snapshot != null) {
                        cache.saveSession(snapshot.copy(savedAt = nowMillis()))
                        delay(CACHE_WRITE_THROTTLE_MS)
                    }
                }
        }
        // Keep each open chat's cached tail current. Driven off the state so every
        // path is covered — history fetches, live pushes, edits, deletions — and
        // throttled the same way as the session snapshot.
        viewModelScope.launch {
            _state
                .map { s -> s.currentChat?.id?.let { id -> id to s.messages } }
                .distinctUntilChanged()
                .conflate()
                .collect { open ->
                    // An empty list is "not loaded yet" far more often than "this chat
                    // is empty", so it is left alone rather than wiping a good cache.
                    if (open != null && open.second.isNotEmpty() && !_state.value.demoMode) {
                        messageCache.save(open.first, open.second)
                        delay(CACHE_WRITE_THROTTLE_MS)
                    }
                }
        }
        // Auto-login if we already have a token.
        if (prefs.token == null) {
            _state.update { it.copy(screen = Screen.LOGIN) }
        } else {
            restoreCachedSession()
            connectWithRetry(freshSession = true)
        }
    }

    /**
     * The slice of the state worth persisting, or null when there is nothing to
     * cache (not signed in, or in the offline demo session — whose fake data must
     * never overwrite a real account's snapshot). [CachedSession.savedAt] is left
     * at 0 here so the timestamp doesn't defeat distinctUntilChanged.
     */
    private fun cacheableSnapshot(s: AppState): CachedSession? {
        if (s.demoMode) return null
        val account = s.account ?: return null
        return CachedSession(
            userId = account.userId,
            account = account,
            chats = s.chats,
            contacts = s.contacts,
            contactsList = s.contactsList,
            peers = s.groupMembers,
        )
    }

    /**
     * Paints the last known chat list before the network answers. Without it the
     * first frames after launch show placeholder dialog titles ("Диалог <id>"),
     * because a dialog's title is derived from the contact map when the chat is
     * parsed — and is only patched up later, one fetchUser per dialog.
     */
    private fun restoreCachedSession() {
        // Drafts and download records are stored separately from the chat-list
        // snapshot, so they are restored even when there is no snapshot to paint.
        if (drafts.isNotEmpty()) _state.update { it.copy(drafts = drafts.toMap()) }
        cache.downloadedFiles().takeIf { it.isNotEmpty() }?.let { refs ->
            _state.update { it.copy(downloadedFiles = refs) }
        }
        val cached = prefs.userId?.let { cache.loadSession(it) } ?: return
        _state.update {
            it.copy(
                screen = Screen.CHATS,
                account = cached.account ?: it.account,
                chats = cached.chats,
                contacts = cached.contacts,
                contactsList = cached.contactsList,
                groupMembers = cached.peers,
            )
        }
    }

    private var connectJob: Job? = null

    /**
     * Records the unsent text of the open chat so it survives leaving the screen.
     * Held in memory and flushed to storage shortly after typing stops (or at once
     * when the chat closes), to avoid a write per keystroke.
     */
    fun setDraft(text: String) {
        val chatId = _state.value.currentChat?.id ?: return
        if (text.isBlank()) drafts.remove(chatId) else drafts[chatId] = text
        draftFlushJob?.cancel()
        draftFlushJob =
            viewModelScope.launch {
                delay(DRAFT_FLUSH_DELAY_MS)
                persistDrafts()
            }
    }

    private fun flushDrafts() {
        draftFlushJob?.cancel()
        persistDrafts()
    }

    /**
     * Writes drafts to storage and publishes them for the chat list. Deliberately
     * not done per keystroke: the list isn't on screen while typing, so a write
     * (and a state update) once typing settles or the chat closes is enough.
     */
    private fun persistDrafts() {
        cache.saveDrafts(drafts)
        _state.update { it.copy(drafts = drafts.toMap()) }
    }

    /** Drops all locally stored data (snapshot + drafts). Used when the account goes away. */
    private fun wipeLocalData() {
        draftFlushJob?.cancel()
        drafts.clear()
        cache.clearAll()
        messageCache.clear()
        outgoing.clear()
        activeSends.values.forEach { it.cancel() }
        activeSends.clear()
    }

    /**
     * Forgets the cached conversation snapshot. Drafts (unsent user content) are
     * kept, and the live session is untouched — the snapshot simply gets rewritten
     * on the next sync or incoming message.
     */
    fun clearCache() {
        cache.clearCache()
        messageCache.clear()
        _state.update { it.copy(notice = "Кэш очищен") }
        refreshCacheSize()
        // Emptying the image cache can mean deleting thousands of files, so it goes
        // off the main thread — and the size is re-read once it's done.
        viewModelScope.launch {
            withContext(Dispatchers.Default) { ImageDiskCache.clear() }
            refreshCacheSize()
        }
    }

    /**
     * Re-reads what the caches hold (the settings screen shows it). Both parts are
     * cheap to measure: the data side is a handful of files, and Coil tracks its own
     * size rather than walking the directory.
     */
    fun refreshCacheSize() =
        _state.update {
            it.copy(
                dataCacheBytes = cache.sizeBytes() + messageCache.sizeBytes(),
                imageCacheBytes = ImageDiskCache.sizeBytes,
            )
        }

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
                                syncedOnce = true,
                            )
                        }
                        resolveDialogTitles()
                        _state.value.currentChat?.let { reloadOpenChat(it) } // catch up missed messages
                        return@launch
                    } catch (e: Throwable) {
                        val msg = e.message ?: "Ошибка"
                        if (msg.contains("вход", true) || msg.contains("авториз", true)) {
                            // Genuine auth rejection -> the token is dead, must re-login.
                            prefs.clear()
                            wipeLocalData()
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
            // Demo account (Play review): skip the network entirely.
            if (normalized == DEMO_PHONE) {
                demoPending = true
                _state.update { it.copy(screen = Screen.CODE, codeLength = DEMO_CODE.length, busy = false, error = null) }
                return@run
            }
            demoPending = false
            pendingPhone = normalized
            launchBusy {
                client.connect(prefs.deviceId, prefs.mtInstance)
                val len = client.startAuth(normalized)
                _state.update { it.copy(screen = Screen.CODE, codeLength = len) }
            }
        }

    fun submitCode(code: String) {
        if (demoPending) {
            if (code.trim() == DEMO_CODE) {
                startDemo()
            } else {
                _state.update { it.copy(error = "Неверный код") }
            }
            return
        }
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
    }

    /** Starts the offline demo session against [DemoMaxApi] (no network). */
    private fun startDemo() {
        demoPending = false
        client = DemoMaxApi()
        viewModelScope.launch {
            val result = client.sync("demo")
            _state.update {
                it.copy(
                    screen = Screen.CHATS,
                    demoMode = true,
                    account = result.account,
                    chats = result.chats,
                    contacts = result.contacts,
                    contactsList = result.contactsList,
                    onlineUsers = result.online,
                    busy = false,
                    error = null,
                    reconnecting = false,
                )
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
                syncedOnce = true,
            )
        }
        resolveDialogTitles()
        pendingInvite?.let { invite ->
            pendingInvite = null
            openInvite(invite)
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
        // Paint the cached tail straight away; the history fetch below replaces it.
        val cached = messageCache.load(chat.id)
        _state.update {
            it.copy(
                screen = Screen.CHAT,
                currentChat = chat,
                messages = cached,
                draft = drafts[chat.id] ?: "",
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
            // Cached at once rather than waiting for the throttled collector: this is
            // the authoritative copy, and it's what the next open will paint.
            if (!_state.value.demoMode) messageCache.save(chat.id, history)
            resolveUnknownSenders(history)
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
        stopVoice()
        _state.update { it.copy(playingVideoNote = null) }
        flushDrafts()
        _state.update { it.copy(screen = Screen.CHATS, currentChat = null, messages = emptyList(), draft = "") }
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

    /** Opens the group info page for [chat] and loads its member list. */
    fun openGroup(chat: Chat) {
        _state.update {
            it.copy(screen = Screen.GROUP, viewingGroup = chat, groupMemberList = emptyList(), groupMembersLoading = true)
        }
        viewModelScope.launch {
            val members = runCatching { client.getChatMembers(chat.id) }.getOrDefault(emptyList())
            // Owner + admins first, then the rest, each alphabetical.
            val sorted =
                members.sortedWith(
                    compareByDescending<UserInfo> { it.id == chat.ownerId }
                        .thenByDescending { it.id in chat.adminIds }
                        .thenBy { it.name.lowercase() },
                )
            _state.update {
                if (it.screen == Screen.GROUP && it.viewingGroup?.id == chat.id) {
                    it.copy(groupMemberList = sorted, groupMembersLoading = false)
                } else {
                    it
                }
            }
        }
    }

    fun closeGroup() {
        _state.update {
            it.copy(screen = if (it.currentChat != null) Screen.CHAT else Screen.CHATS, viewingGroup = null)
        }
    }

    /** Adds [userIds] to the currently-open group, then refreshes the member list. */
    fun addMembersToGroup(
        userIds: List<Long>,
        phones: List<String> = emptyList(),
    ) {
        val group = _state.value.viewingGroup ?: return
        if (userIds.isEmpty() && phones.isEmpty()) return
        launchBusyless {
            // Resolve the selected address-book phones to Max ids through the rate
            // limiter (spaced out). Stop early if we hit the cap.
            val resolved = mutableListOf<Long>()
            var notOnMax = 0
            var rateLimited = false
            for (p in phones) {
                when (val r = lookupByPhone(p)) {
                    is LookupResult.Ok -> resolved += r.user.userId
                    is LookupResult.NotOnMax -> notOnMax++
                    is LookupResult.RateLimited -> {
                        rateLimited = true
                        break
                    }
                }
            }
            val allIds = (userIds + resolved).distinct()
            if (allIds.isNotEmpty()) {
                client.addMembers(group.id, allIds)
                val members = runCatching { client.getChatMembers(group.id) }.getOrDefault(_state.value.groupMemberList)
                val sorted =
                    members.sortedWith(
                        compareByDescending<UserInfo> { it.id == group.ownerId }
                            .thenByDescending { it.id in group.adminIds }
                            .thenBy { it.name.lowercase() },
                    )
                _state.update { it.copy(groupMemberList = sorted) }
            }
            val msg =
                when {
                    rateLimited -> RATE_LIMIT_MSG
                    notOnMax > 0 -> "Не в MAX: $notOnMax контакт(ов) не добавлено"
                    else -> null
                }
            if (msg != null) _state.update { it.copy(notice = msg) }
        }
    }

    /** Removes [userId] from the currently-open group. */
    fun removeGroupMember(userId: Long) {
        val group = _state.value.viewingGroup ?: return
        launchBusyless {
            client.removeMember(group.id, userId)
            _state.update {
                it.copy(
                    groupMemberList = it.groupMemberList.filterNot { m -> m.id == userId },
                    viewingGroup = it.viewingGroup?.let { g -> g.copy(memberIds = g.memberIds - userId) },
                )
            }
        }
    }

    /** Grants ([admin] = true) or revokes admin rights for [userId] in the open group. */
    fun setGroupAdmin(
        userId: Long,
        admin: Boolean,
    ) {
        val group = _state.value.viewingGroup ?: return
        launchBusyless {
            client.setAdmin(group.id, userId, admin)
            // Reflect the role change immediately (the badge reads viewingGroup.adminIds).
            _state.update {
                val g = it.viewingGroup ?: return@update it
                it.copy(viewingGroup = g.copy(adminIds = if (admin) g.adminIds + userId else g.adminIds - userId))
            }
        }
    }

    /**
     * Creates a group with [name], [memberIds] and an optional [avatar], then opens it.
     */
    fun createGroup(
        name: String,
        memberIds: List<Long>,
        phones: List<String>,
        avatar: PickedMedia?,
    ) {
        val title = name.trim()
        if (title.isEmpty()) return
        launchBusy {
            val token = avatar?.let { client.uploadPhoto(it.content, it.fileName, it.mime).token }
            // Resolve selected address-book phones through the rate limiter (spaced).
            val resolved = mutableListOf<Long>()
            var rateLimited = false
            for (p in phones) {
                when (val r = lookupByPhone(p)) {
                    is LookupResult.Ok -> resolved += r.user.userId
                    is LookupResult.NotOnMax -> {}
                    is LookupResult.RateLimited -> {
                        rateLimited = true
                        break
                    }
                }
            }
            if (rateLimited) _state.update { it.copy(notice = RATE_LIMIT_MSG) }
            val newId = client.createGroup(title, (memberIds + resolved).distinct(), token)
            // Re-sync so the new group appears, then open it if we learned its id.
            val syncToken = prefs.token
            if (syncToken != null) {
                runCatching {
                    client.disconnect()
                    client.connect(prefs.deviceId, prefs.mtInstance)
                    val result = client.sync(syncToken)
                    result.refreshedToken?.let { if (it != prefs.token) prefs.token = it }
                    _state.update {
                        it.copy(chats = result.chats, contacts = result.contacts, contactsList = result.contactsList)
                    }
                }
            }
            val chat = newId?.let { id -> _state.value.chats.firstOrNull { it.id == id } }
            if (chat != null) openChat(chat) else _state.update { it.copy(screen = Screen.CHATS, busy = false) }
        }
    }

    // Device address book (pushed in from the UI, which owns the READ_CONTACTS flow).
    private var deviceContacts: List<DeviceContact> = emptyList()
    private var lastSearchQuery: String = ""

    /** The new-chat picker supplies the loaded address book; re-rank if a query is live. */
    fun setDeviceContacts(list: List<DeviceContact>) {
        deviceContacts = list
        if (lastSearchQuery.isNotBlank()) searchUsers(lastSearchQuery)
    }

    /**
     * New-chat search, ranked: your Max contacts (those with an existing chat first),
     * then address-book contacts not already among them, then public results (channels).
     */
    fun searchUsers(query: String) {
        val q = query.trim()
        lastSearchQuery = q
        if (q.isBlank()) {
            _state.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }
        val myId = _state.value.account?.userId
        val s = _state.value
        // 1) Server contacts matching the query, ones with an existing chat ranked first.
        val serverLocal =
            if (myId == null) {
                emptyList()
            } else {
                s.contactsList
                    .filter { it.name.contains(q, ignoreCase = true) }
                    .map { u -> u to client.dialogChatId(myId, u.id) }
                    .sortedByDescending { (_, cid) -> s.chats.any { it.id == cid } }
                    .map { (u, cid) -> SearchResult(cid, u.name, u.avatarUrl, isDialog = true) }
            }
        // 2) Address-book contacts. We dedup only by NAME (not phone): a person saved
        // under a different name in Max vs. the address book must still be findable by
        // the local name, so we keep the book entry even when the phone is a contact.
        val serverNames = s.contactsList.map { it.name.lowercase() }.toSet()
        val book =
            deviceContacts
                .filter { it.name.contains(q, ignoreCase = true) && it.name.lowercase() !in serverNames }
                .distinctBy { it.phone }
                .map { c ->
                    SearchResult(
                        // Synthetic negative id (real dialog ids are positive) so list keys stay unique.
                        chatId = -(c.phone.filter(Char::isDigit).toLongOrNull() ?: c.phone.hashCode().toLong()),
                        title = c.name,
                        avatarUrl = null,
                        isDialog = true,
                        phone = c.phone,
                        subtitle = c.phone,
                    )
                }
        _state.update { it.copy(searchResults = (serverLocal + book).distinctBy { r -> r.chatId }, searching = true) }
        viewModelScope.launch {
            // 3) Public search (channels etc.) ranked last.
            val remote = runCatching { client.searchChats(q) }.getOrDefault(emptyList())
            if (lastSearchQuery == q) {
                _state.update {
                    it.copy(searchResults = (serverLocal + book + remote).distinctBy { r -> r.chatId }, searching = false)
                }
            }
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
        val source = messageId?.let { id -> _state.value.messages.firstOrNull { it.id == id } }
        when (media.type) {
            MediaType.PHOTO -> _state.update { it.copy(mediaViewer = MediaViewer.Image(media.url, source)) }
            MediaType.VIDEO -> {
                val chat = _state.value.currentChat
                val mid = messageId?.toLongOrNull()
                if (chat == null || mid == null || media.videoId == 0L) {
                    _state.update { it.copy(mediaViewer = MediaViewer.Image(media.url, source)) } // fallback: thumbnail
                    return
                }
                _state.update { it.copy(mediaViewer = MediaViewer.Loading) }
                viewModelScope.launch {
                    val url = runCatching { client.getVideoUrl(chat.id, mid, media.videoId) }.getOrNull()
                    _state.update {
                        it.copy(
                            mediaViewer =
                                if (url != null) MediaViewer.Video(url, source) else MediaViewer.Image(media.url, source),
                        )
                    }
                }
            }
        }
    }

    /** Saves the media currently shown in the viewer to the device's Downloads. */
    fun downloadCurrentMedia() {
        val v = _state.value.mediaViewer ?: return
        val url = v.url ?: return
        // The platform reports its own outcome (a toast); nothing here needs the result.
        launchBusyless { downloadToDevice(url, suggestedMediaName(v), if (v.isVideo) "video/mp4" else "image/jpeg") }
    }

    /** Shares the media currently shown in the viewer to other apps (Android share sheet). */
    fun shareCurrentMedia() {
        val v = _state.value.mediaViewer ?: return
        val url = v.url ?: return
        shareMediaToOtherApps(url, suggestedMediaName(v), if (v.isVideo) "video/mp4" else "image/jpeg")
    }

    /** Forwards the source message of the media currently shown to a chat (picker). */
    fun forwardCurrentMedia() {
        val source = _state.value.mediaViewer?.source ?: return
        closeMedia()
        startForward(source)
    }

    private fun suggestedMediaName(v: MediaViewer): String {
        val ts = nowMillis()
        return if (v.isVideo) "video_$ts.mp4" else "photo_$ts.jpg"
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
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val result = lookupByPhone(normalized)
            _state.update { it.copy(busy = false) }
            when (result) {
                is LookupResult.RateLimited -> _state.update { it.copy(notice = RATE_LIMIT_MSG) }
                is LookupResult.NotOnMax ->
                    _state.update { it.copy(notice = "У этого контакта нет аккаунта MAX") }
                is LookupResult.Ok -> {
                    val found = result.user
                    runCatching { client.addContact(found.userId, found.name) }
                    _state.update { it.copy(contacts = it.contacts + (found.userId to found.name)) }
                    openChat(
                        Chat(
                            id = client.dialogChatId(myId, found.userId),
                            title = found.name,
                            lastMessageText = null,
                            lastEventTime = nowMillis(),
                            unreadCount = 0,
                            isDialog = true,
                        ),
                    )
                }
            }
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
            Screen.GROUP -> closeGroup()
            Screen.CHAT -> backToChats()
            Screen.SHARE_PICK -> cancelShare()
            Screen.ABOUT -> _state.update { it.copy(screen = Screen.CHATS) }
            Screen.EDIT_PROFILE -> _state.update { it.copy(screen = Screen.USER) }
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
            Screen.CHAT, Screen.USER, Screen.GROUP, Screen.CODE, Screen.PASSWORD, Screen.REGISTER,
            Screen.SHARE_PICK, Screen.ABOUT, Screen.EDIT_PROFILE,
            -> true
            Screen.CHATS -> tab != Tab.CHATS
            else -> false
        }

    /** Pull-to-refresh on the chat list: re-runs sync over the open connection. */
    fun refresh() {
        if (prefs.token == null) return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            try {
                resync()
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.message ?: "Ошибка обновления") }
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    /**
     * Re-establishes the session and reloads the chat list, suspending until it is
     * done — callers that need the fresh list (following an invite link, say) must be
     * able to await it rather than read the state a moment too early.
     */
    private suspend fun resync() {
        val token = prefs.token ?: return
        // The server allows sync (op 19) only ONCE per connection, so this
        // re-establishes a fresh session. It also recovers a dropped connection.
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
                error = null,
            )
        }
        resolveDialogTitles()
    }

    fun sendMessage(text: String) {
        val chat = _state.value.currentChat ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val cid = nextCid()
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

    /**
     * Sends [items] (photos, videos and/or files) as a SINGLE message carrying all
     * attaches, with an optional [caption].
     *
     * The bubble goes into the chat straight away with a thumbnail per item, and
     * each item's progress ring fills as its upload runs — rather than hiding the
     * whole batch behind one spinner until every byte is up. A single failed item
     * no longer costs the whole batch: whatever uploaded is still sent, and the
     * rest stays behind as a failed bubble that [retrySend] can pick up.
     */
    fun sendMedia(
        items: List<PickedMedia>,
        caption: String,
    ) {
        val chat = _state.value.currentChat ?: return
        val myId = _state.value.account?.userId ?: return
        if (items.isEmpty()) return
        val replyToId = _state.value.replyingTo?.id
        // A message can only carry so many attachments (the official client caps it
        // with a server-set `max-attach-count`, 10 by default), so a bigger pick goes
        // out as several messages. The caption rides on the first one.
        val sends =
            items.chunked(MAX_ATTACHES_PER_MESSAGE).mapIndexed { chunkIndex, chunk ->
                OutgoingSend(
                    chatId = chat.id,
                    cid = nextCid(),
                    caption = if (chunkIndex == 0) caption.trim() else "",
                    replyToId = if (chunkIndex == 0) replyToId else null,
                    items = chunk,
                )
            }
        sends.forEach { send -> outgoing[send.cid] = send }
        _state.update { st ->
            st.copy(
                replyingTo = null,
                pendingSends =
                    sends.fold(st.pendingSends) { acc, send ->
                        acc.plusPending(
                            chat.id,
                            Message(
                                id = null,
                                cid = send.cid,
                                chatId = send.chatId,
                                senderId = myId,
                                text = send.caption,
                                time = send.cid,
                                pending = send.items.map { PendingAttach(it.content.previewModel, it.kind) },
                            ),
                        )
                    },
            )
        }
        startSend(sends)
    }

    /**
     * Drops a pending send: cancels its upload if one is running, and takes the
     * bubble off the chat. The way out of a send that won't go through.
     */
    fun discardPendingSend(message: Message) {
        val cid = message.cid ?: return
        if (message.id != null) return // a delivered message isn't ours to discard
        activeSends.remove(cid)?.cancel()
        outgoing.remove(cid)
        _state.update {
            it.copy(
                pendingSends = it.pendingSends.minusPending(message.chatId, cid),
                sendingAttachment = activeSends.isNotEmpty(),
            )
        }
    }

    /**
     * Sends a finished recording as a voice message.
     *
     * Uses the same optimistic bubble as any other attachment, so it appears at once
     * with a progress ring and can be retried or discarded if the upload fails.
     */
    fun sendVoice(recorded: RecordedVoice) =
        sendSolo(
            SoloRecording(
                content = recorded.content,
                durationSeconds = recorded.durationSeconds,
                isVideoNote = false,
            ),
        )

    /**
     * Sends [media] as a round video message rather than a plain video attachment.
     */
    fun sendVideoNote(media: PickedMedia) =
        sendSolo(
            SoloRecording(
                content = media.content,
                // The camera capture doesn't report a length; the server and the
                // receiving client can work it out from the file.
                durationSeconds = 0,
                isVideoNote = true,
            ),
        )

    private fun sendSolo(recording: SoloRecording) {
        val chat = _state.value.currentChat ?: return
        val myId = _state.value.account?.userId ?: return
        val send =
            OutgoingSend(
                chatId = chat.id,
                cid = nextCid(),
                caption = "",
                replyToId = _state.value.replyingTo?.id,
                items = emptyList(),
                solo = recording,
            )
        outgoing[send.cid] = send
        _state.update {
            it.copy(
                replyingTo = null,
                pendingSends =
                    it.pendingSends.plusPending(
                        chat.id,
                        Message(
                            id = null,
                            cid = send.cid,
                            chatId = send.chatId,
                            senderId = myId,
                            text = "",
                            time = send.cid,
                            // Its own thumbnail-less pending entry: the bubble shows a
                            // ring while the clip goes up.
                            pending = listOf(PendingAttach(preview = null, kind = PickedKind.FILE)),
                        ),
                    ),
            )
        }
        startSend(listOf(send))
    }

    /**
     * Retries a bubble whose attachments failed. Items that had already uploaded are
     * not sent up again, and the original cid is reused so a send the server did
     * receive (but never acknowledged to us) is deduplicated rather than doubled.
     */
    fun retrySend(message: Message) {
        val cid = message.cid ?: return
        val send = outgoing[cid] ?: return
        if (cid in activeSends) return // already on its way
        mapPending(send.chatId, cid) { m ->
            m.copy(
                pending =
                    m.pending.mapIndexed { i, p ->
                        if (send.attaches.getOrNull(i) == null) {
                            p.copy(state = UploadState.QUEUED, progress = 0f)
                        } else {
                            p
                        }
                    },
            )
        }
        _state.update { it.copy(error = null) }
        startSend(listOf(send))
    }

    /**
     * Runs [sends] one after another in a single job, so uploads take turns on the
     * connection instead of fighting over it, and any of their bubbles can cancel it.
     */
    private fun startSend(sends: List<OutgoingSend>) {
        val queue = sends.filter { it.cid !in activeSends }
        if (queue.isEmpty()) return
        // LAZY: the job must be registered before it runs, or a fast completion would
        // tidy up entries that hadn't been added yet.
        val job =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    queue.forEach { send -> performSend(send) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    _state.update { it.copy(error = e.message ?: "Ошибка отправки") }
                } finally {
                    queue.forEach { activeSends.remove(it.cid) }
                    _state.update { it.copy(sendingAttachment = activeSends.isNotEmpty()) }
                }
            }
        queue.forEach { activeSends[it.cid] = job }
        _state.update { it.copy(sendingAttachment = true) }
        job.start()
    }

    /** Uploads whatever [send] still needs, then delivers it. */
    private suspend fun performSend(send: OutgoingSend) {
        send.solo?.let { recording ->
            performSoloSend(send, recording)
            return
        }
        var failed = 0
        send.items.forEachIndexed { index, media ->
            if (send.attaches[index] != null) return@forEachIndexed // already up
            updatePending(send.chatId, send.cid, index) {
                it.copy(state = UploadState.UPLOADING, progress = 0f)
            }
            // Progress arrives per written buffer; only meaningful steps are
            // published, so a big file doesn't cause a state update per chunk.
            val onProgress: (Float) -> Unit = { fraction ->
                updatePending(send.chatId, send.cid, index) { p ->
                    if (fraction - p.progress >= PROGRESS_STEP || fraction >= 1f) {
                        p.copy(progress = fraction)
                    } else {
                        p
                    }
                }
            }
            try {
                send.attaches[index] =
                    when (media.kind) {
                        PickedKind.PHOTO ->
                            client.uploadPhoto(media.content, media.fileName, media.mime, onProgress = onProgress)
                        PickedKind.VIDEO ->
                            client.uploadVideo(media.content, media.fileName, media.mime, onProgress)
                        PickedKind.FILE ->
                            client.uploadFile(media.content, media.fileName, media.mime, onProgress)
                    }
                updatePending(send.chatId, send.cid, index) {
                    it.copy(state = UploadState.DONE, progress = 1f)
                }
            } catch (e: CancellationException) {
                // Discarded mid-upload: stop here rather than working through the rest.
                throw e
            } catch (e: Throwable) {
                // One bad item must not sink the others.
                failed++
                updatePending(send.chatId, send.cid, index) { it.copy(state = UploadState.FAILED) }
            }
        }
        val ready = send.attaches.filterNotNull()
        if (ready.isEmpty()) {
            // Nothing made it: the bubble stays, showing what failed.
            _state.update { it.copy(error = "Не удалось загрузить вложения") }
            return
        }
        val sent =
            try {
                deliver(send, ready, send.cid, send.caption)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Uploaded, but the message itself didn't go out — the bubble has
                // to say so rather than showing finished rings. The attaches are
                // kept, so a retry goes straight to sending.
                markPendingFailed(send.chatId, send.cid)
                throw e
            }
        finishSend(send, sent, failed)
    }

    /**
     * Sends a recording, briefly retrying a refusal.
     *
     * A freshly uploaded clip is refused for a moment while the server finishes with
     * it, and it doesn't announce when it's done — so the send is attempted again a
     * couple of times with a short backoff. That is fast when the clip is already
     * usable, and it is what the user was otherwise doing by hand. Three attempts at
     * most: a genuine rejection should surface rather than hide behind retries.
     *
     * Every attempt reuses the same cid, so a send the server did receive is
     * deduplicated rather than doubled.
     */
    private suspend fun deliverSolo(
        send: OutgoingSend,
        attach: OutAttach,
    ): List<Message> {
        var last: Throwable? = null
        for (attempt in 0 until VOICE_SEND_ATTEMPTS) {
            if (attempt > 0) delay(voiceSendRetryDelayMs * attempt)
            try {
                return deliver(send, listOf(attach), send.cid, caption = "")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                last = e
                // Printed so the reason a fresh recording is refused can be read off
                // logcat (`./dev.sh logs`). If it turns out not to be the server still
                // ingesting the clip, this ladder is the wrong fix and should go.
                println("Avenarius: voice send attempt ${attempt + 1} refused: ${e.message}")
            }
        }
        throw last ?: IllegalStateException("Не удалось отправить голосовое сообщение")
    }

    /** Uploads a recording (if it isn't up already) and sends it. */
    private suspend fun performSoloSend(
        send: OutgoingSend,
        recording: SoloRecording,
    ) {
        updatePending(send.chatId, send.cid, 0) { it.copy(state = UploadState.UPLOADING, progress = 0f) }
        val attach =
            send.soloAttach ?: run {
                val onProgress: (Float) -> Unit = { fraction ->
                    updatePending(send.chatId, send.cid, 0) { p ->
                        if (fraction - p.progress >= PROGRESS_STEP || fraction >= 1f) {
                            p.copy(progress = fraction)
                        } else {
                            p
                        }
                    }
                }
                try {
                    if (recording.isVideoNote) {
                        client.uploadVideoNote(recording.content, recording.durationSeconds, onProgress)
                    } else {
                        client.uploadVoice(recording.content, recording.durationSeconds, onProgress)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    markPendingFailed(send.chatId, send.cid)
                    throw e
                }
            }
        // Kept, so a retry after a failed send doesn't upload the clip again.
        send.soloAttach = attach
        updatePending(send.chatId, send.cid, 0) { it.copy(state = UploadState.DONE, progress = 1f) }
        val sent =
            try {
                deliverSolo(send, attach)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                markPendingFailed(send.chatId, send.cid)
                throw e
            }
        finishSend(send, sent, failedCount = 0)
    }

    /**
     * Sends [attaches] as one message — or, if the server says that is more than a
     * message may carry, as two halves and so on down.
     *
     * The ceiling isn't published (the official client reads a `max-attach-count`
     * from its server config, 10 by default) and this error may also be about total
     * size rather than count, so it is discovered here instead of assumed. Only the
     * first message keeps the caption.
     */
    private suspend fun deliver(
        send: OutgoingSend,
        attaches: List<OutAttach>,
        cid: Long,
        caption: String,
        replyToId: String? = send.replyToId,
    ): List<Message> =
        try {
            listOfNotNull(client.sendMessage(send.chatId, caption, cid, replyToId, attaches))
        } catch (e: Throwable) {
            if (attaches.size <= 1 || !isAttachLimitError(e)) throw e
            // Caption and reply link stay with the first message of a split.
            val half = attaches.size / 2
            deliver(send, attaches.take(half), cid, caption, replyToId) +
                deliver(send, attaches.drop(half), nextCid(), caption = "", replyToId = null)
        }

    /** True if [error] is the server refusing a message for carrying too much. */
    private fun isAttachLimitError(error: Throwable): Boolean {
        val text = error.message ?: return false
        return text.contains("max-size-reached", ignoreCase = true) ||
            text.contains("attachment.max", ignoreCase = true)
    }

    /**
     * Retires [send]'s bubble now that its message is away. Items that never
     * uploaded are left behind as their own failed bubble (under a fresh cid, since
     * [send]'s belongs to the message just sent) so they can still be retried.
     */
    private fun finishSend(
        send: OutgoingSend,
        sent: List<Message>,
        failedCount: Int,
    ) {
        outgoing.remove(send.cid)
        val leftovers = send.items.filterIndexed { i, _ -> send.attaches[i] == null }
        val leftover =
            leftovers
                .takeIf { it.isNotEmpty() }
                ?.let { OutgoingSend(send.chatId, nextCid(), caption = "", replyToId = null, items = it) }
                ?.also { outgoing[it.cid] = it }
        val leftoverBubble =
            leftover?.let { ls ->
                Message(
                    id = null,
                    cid = ls.cid,
                    chatId = ls.chatId,
                    senderId = _state.value.account?.userId ?: 0L,
                    text = "",
                    time = ls.cid,
                    pending = ls.items.map { PendingAttach(it.content.previewModel, it.kind, UploadState.FAILED) },
                )
            }
        _state.update { s ->
            val retired = s.pendingSends.minusPending(send.chatId, send.cid)
            s.copy(
                pendingSends = if (leftoverBubble == null) retired else retired.plusPending(send.chatId, leftoverBubble),
                messages =
                    if (s.currentChat?.id == send.chatId) {
                        // Skip any the server already pushed to us.
                        val known = s.messages.mapNotNull { it.id }.toSet()
                        s.messages + sent.filter { it.id !in known }
                    } else {
                        s.messages
                    },
                notice = if (failedCount > 0) "Не отправлено вложений: $failedCount" else s.notice,
            )
        }
    }

    /** Marks every pending attachment of the local message [cid] as failed. */
    private fun markPendingFailed(
        chatId: Long,
        cid: Long,
    ) = mapPending(chatId, cid) { m -> m.copy(pending = m.pending.map { it.copy(state = UploadState.FAILED) }) }

    /** Applies [transform] to the [index]-th pending attachment of local message [cid]. */
    private fun updatePending(
        chatId: Long,
        cid: Long,
        index: Int,
        transform: (PendingAttach) -> PendingAttach,
    ) = mapPending(chatId, cid) { m ->
        if (index !in m.pending.indices) {
            m
        } else {
            m.copy(pending = m.pending.mapIndexed { i, p -> if (i == index) transform(p) else p })
        }
    }

    private fun mapPending(
        chatId: Long,
        cid: Long,
        transform: (Message) -> Message,
    ) {
        _state.update { s ->
            val current = s.pendingSends[chatId] ?: return@update s
            s.copy(
                pendingSends =
                    s.pendingSends + (chatId to current.map { if (it.cid == cid) transform(it) else it }),
            )
        }
    }

    /**
     * Entry point for media shared in from another app: show the chat picker so the
     * user can choose a destination. Ignored if we aren't signed in yet.
     */
    fun beginShare(items: List<PickedMedia>) {
        if (items.isEmpty() || prefs.token == null) return
        _state.update { it.copy(sharePending = items, screen = Screen.SHARE_PICK) }
    }

    /** Begins forwarding [msg]: show the chat picker to choose where to send it. */
    fun startForward(msg: Message) {
        if (msg.id == null) return
        _state.update { it.copy(forwarding = msg, screen = Screen.SHARE_PICK) }
    }

    /**
     * Picks the destination [chat] for a pending share or forward: open it, then stage
     * the shared media or send the forwarded message.
     */
    fun pickShareTarget(chat: Chat) {
        val media = _state.value.sharePending
        val fwd = _state.value.forwarding
        _state.update { it.copy(sharePending = emptyList(), forwarding = null, stagedMedia = media) }
        openChat(chat)
        val fwdId = fwd?.id
        if (fwdId != null) {
            launchBusyless {
                val sent = client.forwardMessage(chat.id, fwdId, fwd.chatId, nextCid())
                if (sent != null) {
                    _state.update { s ->
                        if (s.messages.any { it.id == sent.id }) s else s.copy(messages = s.messages + sent)
                    }
                }
            }
        }
    }

    fun cancelShare() = _state.update { it.copy(sharePending = emptyList(), forwarding = null, screen = Screen.CHATS) }

    /** ChatScreen calls this once it has moved [AppState.stagedMedia] into its input. */
    fun consumeStagedMedia() = _state.update { it.copy(stagedMedia = emptyList()) }

    /**
     * For group chats, fetches display info for senders not in our contacts so their
     * name + avatar show instead of a "—" placeholder. Results are cached in state.
     */
    private fun resolveUnknownSenders(messages: List<Message>) {
        val s = _state.value
        val chat = s.currentChat ?: return
        val myId = s.account?.userId
        val ids =
            buildSet {
                // Group message senders (a dialog has one obvious peer).
                if (!chat.isDialog) messages.forEach { add(it.senderId) }
                // Original authors of forwarded messages (relevant in any chat type).
                messages.forEach { m -> m.forwardedFrom?.let { add(it) } }
                // Actors + affected members of group service events ("X added Y").
                messages.forEach { m ->
                    m.service?.let { s ->
                        add(s.actorId)
                        addAll(s.userIds)
                    }
                }
            }
        val unknown =
            ids.filter { it != 0L && it != myId && !s.contacts.containsKey(it) && !s.groupMembers.containsKey(it) }
        if (unknown.isEmpty()) return
        viewModelScope.launch {
            val fetched = unknown.mapNotNull { id -> runCatching { client.fetchUser(id) }.getOrNull() }
            if (fetched.isNotEmpty()) {
                _state.update { st -> st.copy(groupMembers = st.groupMembers + fetched.associateBy { it.id }) }
            }
        }
    }

    /**
     * Dialogs whose peer isn't in our contacts arrive titled "Диалог <id>" (the
     * server sends no title and we have no contact name). Fetch each such peer's
     * profile, patch the chat title with their real name, and cache the profile so
     * the list avatar resolves too.
     */
    private fun resolveDialogTitles() {
        val s = _state.value
        val myId = s.account?.userId ?: return
        val unresolved = s.chats.filter { it.isDialog && it.title.startsWith("Диалог ") }
        if (unresolved.isEmpty()) return
        viewModelScope.launch {
            val fetched =
                unresolved.mapNotNull { chat ->
                    runCatching { client.fetchUser(chat.id xor myId) }.getOrNull()
                }
            if (fetched.isEmpty()) return@launch
            val byId = fetched.associateBy { it.id }
            _state.update { st ->
                st.copy(
                    chats =
                        st.chats.map { c ->
                            if (c.isDialog && c.title.startsWith("Диалог ")) {
                                byId[c.id xor myId]?.let { c.copy(title = it.name) } ?: c
                            } else {
                                c
                            }
                        },
                    groupMembers = st.groupMembers + byId,
                )
            }
        }
    }

    /**
     * Opens a file attachment if it has already been saved to the device, and
     * downloads it otherwise — so the same tap does the obvious thing, and a file
     * fetched once can be opened again straight from the message.
     */
    fun openOrDownloadFile(
        message: Message,
        file: FileAttach,
    ) {
        if (file.fileId in _state.value.downloadingFiles) return
        val msgId = message.id?.toLongOrNull() ?: return
        launchBusyless {
            _state.value.downloadedFiles[file.fileId]?.let { existing ->
                if (openDownloadedFile(existing, file.name)) return@launchBusyless
                // Gone from the device (cleared Downloads, moved, revoked): forget it
                // and fetch again, rather than leaving a button that does nothing.
                forgetDownloadedFile(file.fileId)
            }
            _state.update { it.copy(downloadingFiles = it.downloadingFiles + (file.fileId to 0f)) }
            try {
                val url =
                    client.getFileUrl(message.chatId, msgId, file.fileId)
                        ?: error("Не удалось получить ссылку на файл")
                // Progress goes to the row in the message; the platform stays quiet
                // (no toast) so it doesn't cover the very indicator it duplicates.
                val reference =
                    downloadToDevice(url, file.name, "application/octet-stream") { fraction ->
                        _state.update { st ->
                            val shown = st.downloadingFiles[file.fileId] ?: 0f
                            if (fraction - shown >= PROGRESS_STEP || fraction >= 1f) {
                                st.copy(downloadingFiles = st.downloadingFiles + (file.fileId to fraction))
                            } else {
                                st
                            }
                        }
                    }
                if (reference != null) {
                    val refs = _state.value.downloadedFiles + (file.fileId to reference)
                    cache.saveDownloadedFiles(refs)
                    _state.update { it.copy(downloadedFiles = refs) }
                }
            } finally {
                _state.update { it.copy(downloadingFiles = it.downloadingFiles - file.fileId) }
            }
        }
    }

    /**
     * Starts or stops a voice message. Tapping the one already playing stops it;
     * tapping another switches to it, since only one clip plays at a time.
     */
    fun toggleVoice(message: Message) {
        val voice = message.voice ?: return
        val id = message.id ?: return
        val current = _state.value.playingVoice
        if (current?.messageId == id) {
            // Same bubble: pause and resume in place. Stopping here would throw the
            // position away, which is what made the pause button look like a rewind.
            if (current.paused) {
                voicePlayer.resume()
                _state.update { it.copy(playingVoice = current.copy(paused = false)) }
            } else {
                voicePlayer.pause()
                _state.update { it.copy(playingVoice = current.copy(paused = true)) }
            }
            return
        }
        voicePlayer.stop()
        // A circle and a voice clip must not play over each other.
        _state.update { it.copy(playingVoice = PlayingVoice(messageId = id), playingVideoNote = null) }
        launchBusyless {
            // Reported rather than swallowed: "no link in the reply" and "the server
            // refused" need telling apart when this goes wrong.
            val resolved =
                runCatching {
                    client.getAudioUrl(message.chatId, id.toLongOrNull() ?: 0L, voice.audioId, voice.token)
                }
            val url = resolved.getOrNull()
            if (url == null) {
                val reason =
                    resolved.exceptionOrNull()?.message
                        ?: "Сервер не вернул ссылку на аудио"
                _state.update { it.copy(playingVoice = null, error = reason) }
                return@launchBusyless
            }
            // The bubble may have been stopped (or another started) while we fetched.
            if (_state.value.playingVoice?.messageId != id) return@launchBusyless
            voicePlayer.play(
                url = url,
                // The player often can't tell how long a streamed clip is; the attach can.
                durationHintMs = voice.durationSeconds * 1000L,
                onStarted = {
                    // A position of 0 (rather than null) is what tells the bubble it is
                    // playing rather than still loading.
                    _state.update { s ->
                        val playing = s.playingVoice
                        if (playing?.messageId != id) s else s.copy(playingVoice = playing.copy(progress = 0f))
                    }
                },
                onProgress = { fraction ->
                    _state.update { s ->
                        val playing = s.playingVoice
                        if (playing?.messageId != id) {
                            s
                        } else {
                            s.copy(playingVoice = playing.copy(progress = fraction))
                        }
                    }
                },
                onFinished = {
                    _state.update { s -> if (s.playingVoice?.messageId == id) s.copy(playingVoice = null) else s }
                },
            )
        }
    }

    /** Jumps to [fraction] (0..1) of the voice message that is loaded. */
    fun seekVoice(fraction: Float) {
        val playing = _state.value.playingVoice ?: return
        voicePlayer.seekTo(fraction)
        // Moved at once so the bar follows the finger; the next tick confirms it.
        _state.update { it.copy(playingVoice = playing.copy(progress = fraction.coerceIn(0f, 1f))) }
    }

    /**
     * Starts or stops a round video message, played inside its own circle rather than
     * in the full-screen viewer. Tapping the one already playing stops it.
     */
    fun toggleVideoNote(message: Message) {
        val note = message.media.firstOrNull { it.isVideoNote } ?: return
        val id = message.id ?: return
        if (_state.value.playingVideoNote?.messageId == id) {
            _state.update { it.copy(playingVideoNote = null) }
            return
        }
        // Only one thing plays at a time.
        stopVoice()
        _state.update { it.copy(playingVideoNote = PlayingVideoNote(messageId = id)) }
        launchBusyless {
            val resolved =
                runCatching { client.getVideoUrl(message.chatId, id.toLongOrNull() ?: 0L, note.videoId) }
            val url = resolved.getOrNull()
            if (url == null) {
                _state.update {
                    it.copy(
                        playingVideoNote = null,
                        error = resolved.exceptionOrNull()?.message ?: "Не удалось воспроизвести видеосообщение",
                    )
                }
                return@launchBusyless
            }
            // It may have been stopped (or another started) while the URL was fetched.
            _state.update { s ->
                if (s.playingVideoNote?.messageId != id) s else s.copy(playingVideoNote = PlayingVideoNote(id, url))
            }
        }
    }

    /** Stops voice playback, if any. */
    fun stopVoice() {
        voicePlayer.stop()
        _state.update { it.copy(playingVoice = null) }
    }

    private fun forgetDownloadedFile(fileId: Long) {
        val refs = _state.value.downloadedFiles - fileId
        cache.saveDownloadedFiles(refs)
        _state.update { it.copy(downloadedFiles = refs) }
    }

    /** Edits our own [msg] to [newText] (optimistically updates the bubble). */
    fun editMessage(
        msg: Message,
        newText: String,
    ) {
        val id = msg.id ?: return
        val trimmed = newText.trim()
        if (trimmed.isEmpty() || trimmed == msg.text) return
        _state.update { s ->
            s.copy(messages = s.messages.map { if (it.id == id) it.copy(text = trimmed) else it })
        }
        launchBusyless { client.editMessage(msg.chatId, id, trimmed) }
    }

    /** Deletes [msg] ([forAll] = for everyone), removing it from the open chat. */
    fun deleteMessage(
        msg: Message,
        forAll: Boolean,
    ) {
        // A bubble that never made it to the server is deleted locally.
        if (msg.id == null) {
            discardPendingSend(msg)
            return
        }
        val id = msg.id
        _state.update { s -> s.copy(messages = s.messages.filterNot { it.id == id }) }
        launchBusyless { client.deleteMessages(msg.chatId, listOf(id), forAll) }
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

    /** Mutes/unmutes notifications for the open chat (optimistic; persisted server-side). */
    fun toggleMuteCurrentChat() {
        val chat = _state.value.currentChat ?: return
        val newMuted = !chat.muted
        _state.update { s ->
            s.copy(
                currentChat = s.currentChat?.copy(muted = newMuted),
                chats = s.chats.map { if (it.id == chat.id) it.copy(muted = newMuted) else it },
            )
        }
        viewModelScope.launch { runCatching { client.setChatMuted(chat.id, newMuted) } }
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

    /**
     * Confirms a web/desktop login from a scanned QR code ([qrLink] = the raw
     * decoded string). Shows a snackbar with the outcome.
     */
    fun confirmWebLogin(qrLink: String) {
        val link = qrLink.trim()
        if (link.isEmpty()) return
        viewModelScope.launch {
            val result = runCatching { client.approveQrLogin(link) }
            _state.update {
                it.copy(
                    notice =
                        if (result.isSuccess) {
                            "Вход в веб-версию подтверждён"
                        } else {
                            "Не удалось подтвердить вход: ${result.exceptionOrNull()?.message ?: "ошибка"}"
                        },
                )
            }
        }
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    fun logout() {
        prefs.clear()
        wipeLocalData()
        client.disconnect()
        client = originalClient // leave demo mode if we were in it
        demoPending = false
        _state.update {
            AppState(screen = Screen.LOGIN, theme = it.theme)
        }
    }

    // --- helpers ---

    // Timestamps (ms) of recent findByPhone calls, oldest first, for rate limiting.
    private val phoneLookupTimes = ArrayDeque<Long>()

    /**
     * Gated wrapper around [MaxApi.findByPhone]. Rapid phone lookups look like number
     * enumeration to Max's anti-abuse system and can LOCK the account, so all lookups
     * go through here: a minimum spacing plus short- and long-window caps. When a limit
     * is hit it returns [LookupResult.RateLimited] WITHOUT calling the server. A call
     * that does go out is spaced by [LOOKUP_MIN_SPACING_MS] (awaited via delay).
     */
    private suspend fun lookupByPhone(phone: String): LookupResult {
        val now = nowMillis()
        while (phoneLookupTimes.isNotEmpty() && now - phoneLookupTimes.first() > LOOKUP_LONG_WINDOW_MS) {
            phoneLookupTimes.removeFirst()
        }
        val inShort = phoneLookupTimes.count { now - it <= LOOKUP_SHORT_WINDOW_MS }
        if (phoneLookupTimes.size >= LOOKUP_MAX_PER_LONG || inShort >= LOOKUP_MAX_PER_SHORT) {
            return LookupResult.RateLimited
        }
        phoneLookupTimes.lastOrNull()?.let { last ->
            val since = now - last
            if (since < LOOKUP_MIN_SPACING_MS) delay(LOOKUP_MIN_SPACING_MS - since)
        }
        // Count the request even if it fails — the server still saw it.
        phoneLookupTimes.addLast(nowMillis())
        val found = runCatching { client.findByPhone(phone) }.getOrNull()
        return if (found != null) LookupResult.Ok(found) else LookupResult.NotOnMax
    }

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

    companion object {
        // Offline demo account for Google Play review (no real server access).
        const val DEMO_PHONE = "+79990000000"
        const val DEMO_CODE = "00000"

        // Phone-lookup rate limiting. Rapid findByPhone bursts read as number
        // enumeration to Max and can lock the account, so lookups are spaced out and
        // capped. Conservative on purpose — the exact server limit is unknown.
        const val LOOKUP_MIN_SPACING_MS = 3_000L
        const val LOOKUP_SHORT_WINDOW_MS = 60_000L
        const val LOOKUP_MAX_PER_SHORT = 5
        const val LOOKUP_LONG_WINDOW_MS = 3_600_000L
        const val LOOKUP_MAX_PER_LONG = 20
        const val RATE_LIMIT_MSG = "Слишком много проверок номеров. Подождите немного и повторите."

        // Attachments per message. Mirrors the official client's `max-attach-count`
        // default; the server can be stricter, which [deliver] discovers.
        const val MAX_ATTACHES_PER_MESSAGE = 10

        // Backoff step before re-sending a recording the server wasn't ready for:
        // the second attempt waits one step, the third waits two.
        //
        // 700ms, not less: at 250ms the ladder sometimes ran out before the server was
        // ready and the send failed outright. The visible cost is a brief pause with a
        // full progress ring, which is the better trade.
        const val VOICE_SEND_RETRY_DELAY_MS = 700L

        const val VOICE_SEND_ATTEMPTS = 3

        // Smallest upload-progress change worth publishing to the UI.
        const val PROGRESS_STEP = 0.02f

        // How long to wait after the last keystroke before writing a draft to storage.
        const val DRAFT_FLUSH_DELAY_MS = 800L

        // Minimum interval between warm-start snapshot writes.
        const val CACHE_WRITE_THROTTLE_MS = 1_000L
    }
}

/** The voice message currently playing, and how far through it is. */
data class PlayingVoice(
    /** Server id of the message whose bubble is playing. */
    val messageId: String,
    /**
     * Position as 0..1, or null while the clip is still being fetched and started —
     * which is what distinguishes "loading" from "playing" in the bubble.
     */
    val progress: Float? = null,
    /** Held at its position rather than playing. The clip is still loaded. */
    val paused: Boolean = false,
)

/** The round video message playing in place, and the stream it plays. */
data class PlayingVideoNote(
    /** Server id of the message whose circle is playing. */
    val messageId: String,
    /** The playable stream, or null while it is being resolved. */
    val url: String? = null,
)

/** Adds [message] to the pending-send list of [chatId]. */
private fun Map<Long, List<Message>>.plusPending(
    chatId: Long,
    message: Message,
): Map<Long, List<Message>> = this + (chatId to (this[chatId].orEmpty() + message))

/** Removes the pending send with [cid] from [chatId], dropping the key when empty. */
private fun Map<Long, List<Message>>.minusPending(
    chatId: Long,
    cid: Long,
): Map<Long, List<Message>> {
    val current = this[chatId] ?: return this
    val remaining = current.filterNot { it.cid == cid }
    return when {
        remaining.size == current.size -> this
        remaining.isEmpty() -> this - chatId
        else -> this + (chatId to remaining)
    }
}

/** Outcome of a rate-limited phone lookup. */
private sealed interface LookupResult {
    data class Ok(
        val user: FoundUser,
    ) : LookupResult

    data object NotOnMax : LookupResult

    data object RateLimited : LookupResult
}
