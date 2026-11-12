package com.avenarius.app.ui

import com.avenarius.app.data.AppCache
import com.avenarius.app.data.CachedSession
import com.avenarius.app.data.InMemoryStorage
import com.avenarius.app.data.MessageCache
import com.avenarius.app.data.Prefs
import com.avenarius.app.model.Account
import com.avenarius.app.model.BotButton
import com.avenarius.app.model.CallSetup
import com.avenarius.app.model.Chat
import com.avenarius.app.model.ChatFolder
import com.avenarius.app.model.FileAttach
import com.avenarius.app.model.IncomingCall
import com.avenarius.app.model.MediaAttach
import com.avenarius.app.model.MediaContent
import com.avenarius.app.model.MediaType
import com.avenarius.app.model.Message
import com.avenarius.app.model.MessageStatus
import com.avenarius.app.model.OutAttach
import com.avenarius.app.model.PickedKind
import com.avenarius.app.model.PickedMedia
import com.avenarius.app.model.Poll
import com.avenarius.app.model.PollAnswer
import com.avenarius.app.model.Reaction
import com.avenarius.app.model.RecordedVoice
import com.avenarius.app.model.SearchResult
import com.avenarius.app.model.Sticker
import com.avenarius.app.model.StickerSet
import com.avenarius.app.model.StickerShelf
import com.avenarius.app.model.TextSpan
import com.avenarius.app.model.UploadState
import com.avenarius.app.model.UserInfo
import com.avenarius.app.model.VoiceAttach
import com.avenarius.app.net.AuthSessionExpiredException
import com.avenarius.app.net.BlockedPage
import com.avenarius.app.net.CodeResult
import com.avenarius.app.net.EmailCodeInfo
import com.avenarius.app.net.FoldersUpdate
import com.avenarius.app.net.FoundUser
import com.avenarius.app.net.MaxApi
import com.avenarius.app.net.MessageDeletion
import com.avenarius.app.net.MessageHit
import com.avenarius.app.net.Presence
import com.avenarius.app.net.ReactionUpdate
import com.avenarius.app.net.ReadMark
import com.avenarius.app.net.SearchPage
import com.avenarius.app.net.SessionInfo
import com.avenarius.app.net.SyncResult
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A scriptable [MaxApi] for driving [AppViewModel] without a network. Server
 * pushes are simulated by emitting into the public shared flows; outgoing calls
 * are recorded so tests can assert on them.
 */
private class FakeMaxClient : MaxApi {
    private val _incoming = MutableSharedFlow<Message>(extraBufferCapacity = 16)
    private val _readMarks = MutableSharedFlow<ReadMark>(extraBufferCapacity = 16)
    private val _presence = MutableSharedFlow<Presence>(extraBufferCapacity = 16)
    private val _reactionUpdates = MutableSharedFlow<ReactionUpdate>(extraBufferCapacity = 16)
    private val _chatUpdates = MutableSharedFlow<Chat>(extraBufferCapacity = 16)
    private val _deletions = MutableSharedFlow<MessageDeletion>(extraBufferCapacity = 16)
    private val _drops = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    override val incoming: SharedFlow<Message> get() = _incoming
    override val readMarks: SharedFlow<ReadMark> get() = _readMarks
    override val presence: SharedFlow<Presence> get() = _presence
    override val reactionUpdates: SharedFlow<ReactionUpdate> get() = _reactionUpdates
    override val chatUpdates: SharedFlow<Chat> get() = _chatUpdates
    override val deletions: SharedFlow<MessageDeletion> get() = _deletions
    private val _incomingCalls = MutableSharedFlow<IncomingCall>(extraBufferCapacity = 8)
    override val incomingCalls: SharedFlow<IncomingCall> get() = _incomingCalls
    override val drops: SharedFlow<Unit> get() = _drops
    override var isConnected: Boolean = false
        private set

    // --- scriptable responses ---
    var account = Account(userId = 100L, firstName = "Тест")
    var chats: List<Chat> = emptyList()
    var contacts: Map<Long, String> = emptyMap()
    var contactsList: List<UserInfo> = emptyList()
    var online: Set<Long> = emptySet()
    var refreshedToken: String? = null
    var codeResult: CodeResult = CodeResult.Success("login-token")
    var pinLimit: Int? = null
    val favWrites = mutableListOf<Map<Long, Long>>()
    var favFailure: Throwable? = null

    override suspend fun setFavIndex(indexes: Map<Long, Long>) {
        favWrites += indexes
        favFailure?.let { throw it }
    }

    /** When set, [sync] throws it — lets a test hold the session unestablished. */
    var syncFailure: Throwable? = null
    var history: List<Message> = emptyList()
    var userToReturn: UserInfo? = null
    var searchResultsToReturn: List<SearchResult> = emptyList()

    /** What [getReadMarks] returns (userId → mark), for the "who read" sheet. */
    var readMarksToReturn: Map<Long, Long> = emptyMap()
    var readMarksRequested = mutableListOf<Long>()

    // --- recorded calls ---
    var connectCount = 0
    var disconnectCount = 0
    val markReadCalls = mutableListOf<Triple<Long, String, Long>>()
    val sentMessages = mutableListOf<Pair<Long, String>>()

    fun emitIncoming(message: Message) = _incoming.tryEmit(message)

    fun emitReadMark(mark: ReadMark) = _readMarks.tryEmit(mark)

    fun emitPresence(p: Presence) = _presence.tryEmit(p)

    fun emitReactionUpdate(u: ReactionUpdate) = _reactionUpdates.tryEmit(u)

    fun emitChatUpdate(c: Chat) = _chatUpdates.tryEmit(c)

    override suspend fun connect(
        deviceId: String,
        mtInstance: String,
    ) {
        connectCount++
        isConnected = true
    }

    override suspend fun startCall(
        peerId: Long,
        isVideo: Boolean,
    ): CallSetup = error("not used in tests")

    override suspend fun acceptCall(
        conversationId: String,
        peerId: Long,
        isVideo: Boolean,
    ): CallSetup = error("not used in tests")

    override suspend fun hangupCall(
        conversationId: String,
        reason: String,
        peerId: Long?,
    ) = Unit

    var mutedChats = mutableSetOf<Long>()

    override suspend fun setChatMuted(
        chatId: Long,
        muted: Boolean,
    ) {
        muteFailure?.let { throw it }
        if (muted) mutedChats.add(chatId) else mutedChats.remove(chatId)
    }

    var muteFailure: Throwable? = null
    var reactionFailure: Throwable? = null
    var leaveFailure: Throwable? = null

    override fun disconnect() {
        disconnectCount++
        isConnected = false
    }

    override suspend fun startAuth(phone: String): Int = 6

    override suspend fun checkCode(code: String): CodeResult = codeResult

    override suspend fun register(
        firstName: String,
        lastName: String?,
    ): String = "reg-token"

    override suspend fun checkPassword(
        password: String,
        trackId: String,
    ): String = "pwd-token"

    // Forgotten-password flow: records the calls so tests can check order and arguments.
    val restoreCalls = mutableListOf<String>()
    var restoreCodeFailure: Throwable? = null

    override suspend fun restoreSendEmailCode(trackId: String): EmailCodeInfo {
        restoreCalls += "send:$trackId"
        return EmailCodeInfo(codeLength = 6, resendAfterMs = 60_000)
    }

    override suspend fun restoreCheckEmailCode(
        trackId: String,
        code: String,
    ) {
        restoreCodeFailure?.let { throw it }
        restoreCalls += "check:$trackId:$code"
    }

    override suspend fun twoFaValidatePassword(
        trackId: String,
        password: String,
    ) {
        restoreCalls += "validatePassword:$password"
    }

    override suspend fun twoFaValidateHint(
        trackId: String,
        hint: String,
    ) {
        restoreCalls += "validateHint:$hint"
    }

    override suspend fun restorePassword(
        trackId: String,
        password: String,
        hint: String?,
    ): String {
        restoreCalls += "restore:$trackId:$password:$hint"
        return "restored-token"
    }

    val syncTokens = mutableListOf<String>()
    var closeSessionsToken: String? = "new-token"
    var closeSessionsFailure: Throwable? = null
    var closeSessionsCalls = 0

    override suspend fun closeOtherSessions(): String? {
        closeSessionsCalls++
        closeSessionsFailure?.let { throw it }
        return closeSessionsToken
    }

    override suspend fun sync(token: String): SyncResult {
        syncTokens += token
        syncFailure?.let { throw it }
        return SyncResult(account, chats, contacts, contactsList, online, refreshedToken, pinLimit = pinLimit)
    }

    override suspend fun fetchHistory(
        chatId: Long,
        fromTime: Long,
        count: Int,
    ): List<Message> {
        historyCalls++
        historyHook?.invoke()
        historyGate?.await()
        return history.filter { it.time <= fromTime }.takeLast(count)
    }

    var historyCalls = 0

    /** Run at the start of every [fetchHistory], e.g. to poke the ViewModel mid-paging. */
    var historyHook: (() -> Unit)? = null

    var lastReplyToId: String? = null
    val reactionCalls = mutableListOf<Triple<Long, String, String?>>()
    val deletedChats = mutableListOf<Long>()
    var lastDeleteForAll: Boolean? = null
    val leftGroups = mutableListOf<Long>()
    val approvedQrLinks = mutableListOf<String>()
    val addedMembers = mutableListOf<Pair<Long, List<Long>>>()
    val removedMembers = mutableListOf<Pair<Long, Long>>()
    val adminChanges = mutableListOf<Triple<Long, Long, Boolean>>()
    val createdGroups = mutableListOf<Pair<String, List<Long>>>()

    /** Formatting carried by the most recent send. */
    var lastSpans: List<TextSpan> = emptyList()

    /** Attaches carried by the most recent send. */
    var lastAttaches: List<OutAttach> = emptyList()

    /** Attach counts of every accepted send, in order. */
    val sentAttachCounts = mutableListOf<Int>()

    /** Sends carrying more than this are rejected the way the real server does. */
    var maxAttachesAccepted = Int.MAX_VALUE

    /** When set, [sendMessage] waits on it — lets a test inspect the in-flight state. */
    var sendGate: CompletableDeferred<Unit>? = null

    /** When set, [sendMessage] throws it. */
    var sendFailure: Throwable? = null

    /** Number of sends to refuse before accepting one (server-side processing lag). */
    var sendFailuresRemaining = 0

    /** Every call to [sendMessage], accepted or not. */
    var sendAttempts = 0

    override suspend fun sendMessage(
        chatId: Long,
        text: String,
        cid: Long,
        replyToId: String?,
        attaches: List<OutAttach>,
        spans: List<TextSpan>,
    ): Message? {
        sendAttempts++
        sendGate?.await()
        sendFailure?.let { throw it }
        if (sendFailuresRemaining > 0) {
            sendFailuresRemaining--
            error("errors.send-message.attachment.not-ready")
        }
        if (attaches.size > maxAttachesAccepted) {
            error("errors.send-message.attachment.max-size-reached")
        }
        if (attaches.isNotEmpty()) sentAttachCounts += attaches.size
        sentMessages += chatId to text
        lastReplyToId = replyToId
        lastAttaches = attaches
        lastSpans = spans
        return Message(id = "srv-$cid", cid = cid, chatId = chatId, senderId = account.userId, text = text, time = cid)
    }

    /** Names of the items handed to an upload, in order. */
    val uploaded = mutableListOf<String>()

    /** File names that should fail to upload. */
    var failingUploads = setOf<String>()

    /** When set, every upload parks half-way through, mid-progress. */
    var uploadGate: CompletableDeferred<Unit>? = null

    private suspend fun record(
        content: MediaContent,
        fileName: String,
        onProgress: ((Float) -> Unit)?,
    ) {
        uploaded += fileName
        // A real client streams the content; draining it here proves the handle is
        // still usable at upload time.
        content.openChannel().toByteArray()
        if (fileName in failingUploads) error("Загрузка не удалась: $fileName")
        onProgress?.invoke(0.5f)
        uploadGate?.await()
        onProgress?.invoke(1f)
    }

    override suspend fun uploadPhoto(
        content: MediaContent,
        fileName: String,
        mime: String,
        profile: Boolean,
        onProgress: ((Float) -> Unit)?,
    ): OutAttach.Photo {
        record(content, fileName, onProgress)
        return OutAttach.Photo(token = "fake-token")
    }

    override suspend fun updateProfile(
        firstName: String,
        lastName: String?,
        description: String?,
        photoToken: String?,
    ) {}

    override suspend fun forwardMessage(
        toChatId: Long,
        messageId: String,
        fromChatId: Long,
        cid: Long,
    ): Message? {
        forwarded += Triple(toChatId, messageId, fromChatId)
        return Message(id = "fwd-$cid", cid = cid, chatId = toChatId, senderId = account.userId, text = "", time = cid)
    }

    /** Every [forwardMessage] call as (to chat, message id, from chat), in order. */
    val forwarded = mutableListOf<Triple<Long, String, Long>>()

    var sessionsToReturn: List<SessionInfo> = emptyList()
    var sessionsFailure: Throwable? = null

    override suspend fun getSessions(): List<SessionInfo> {
        sessionsFailure?.let { throw it }
        return sessionsToReturn
    }

    val editedMessages = mutableListOf<Triple<Long, String, String>>()
    val deletedMessages = mutableListOf<Triple<Long, List<String>, Boolean>>()

    val editedSpans = mutableListOf<List<TextSpan>>()

    override suspend fun editMessage(
        chatId: Long,
        messageId: String,
        text: String,
        spans: List<TextSpan>?,
    ) {
        editedMessages += Triple(chatId, messageId, text)
        editedSpans += listOfNotNull(spans)
        if (spans == null) editsWithoutElements++
    }

    /** Edits sent with `elements` left out (null spans). */
    var editsWithoutElements = 0

    override suspend fun deleteMessages(
        chatId: Long,
        messageIds: List<String>,
        forAll: Boolean,
    ) {
        deletedMessages += Triple(chatId, messageIds, forAll)
        deleteFailure?.let { throw it }
    }

    var deleteFailure: Throwable? = null

    override suspend fun uploadVideo(
        content: MediaContent,
        fileName: String,
        mime: String,
        onProgress: ((Float) -> Unit)?,
    ): OutAttach.Video {
        record(content, fileName, onProgress)
        return OutAttach.Video(videoId = 1L, token = "fake-token")
    }

    /** Recordings handed to [uploadVoice], with their reported length. */
    val uploadedVoices = mutableListOf<Int>()

    var voiceUploadFailure: Throwable? = null

    val uploadedVideoNotes = mutableListOf<Int>()

    override suspend fun uploadVideoNote(
        content: MediaContent,
        durationSeconds: Int,
        onProgress: ((Float) -> Unit)?,
    ): OutAttach.VideoNote {
        uploadedVideoNotes += durationSeconds
        content.openChannel().toByteArray()
        return OutAttach.VideoNote(token = "note-token", durationSeconds = durationSeconds)
    }

    override suspend fun uploadVoice(
        content: MediaContent,
        durationSeconds: Int,
        onProgress: ((Float) -> Unit)?,
    ): OutAttach.Voice {
        content.openChannel().toByteArray()
        voiceUploadFailure?.let { throw it }
        uploadedVoices += durationSeconds
        onProgress?.invoke(1f)
        return OutAttach.Voice(token = "voice-token", durationSeconds = durationSeconds)
    }

    override suspend fun uploadFile(
        content: MediaContent,
        fileName: String,
        mime: String,
        onProgress: ((Float) -> Unit)?,
    ): OutAttach.File {
        record(content, fileName, onProgress)
        return OutAttach.File(fileId = 1L)
    }

    override suspend fun markRead(
        chatId: Long,
        messageId: String,
        mark: Long,
    ) {
        markReadCalls += Triple(chatId, messageId, mark)
    }

    override suspend fun reportHostReachability() = Unit

    override suspend fun setReaction(
        chatId: Long,
        messageId: String,
        emoji: String?,
    ) {
        reactionCalls += Triple(chatId, messageId, emoji)
        reactionFailure?.let { throw it }
    }

    override suspend fun deleteChat(
        chatId: Long,
        lastEventTime: Long,
        forAll: Boolean,
    ) {
        deletedChats += chatId
        lastDeleteForAll = forAll
    }

    /** Chat handed back by [joinByLink]; null means "joined, but no chat described". */
    var joinResult: Chat? = null

    val joinedLinks = mutableListOf<String>()

    /** Only this spelling of a link is accepted; others are refused like the server. */
    var acceptedJoinForm: String? = null

    override suspend fun joinByLink(link: String): Chat? {
        joinedLinks += link
        acceptedJoinForm?.let { if (link != it) error("Ссылка не найдена") }
        return joinResult
    }

    override suspend fun leaveGroup(chatId: Long) {
        leftGroups += chatId
        leaveFailure?.let { throw it }
    }

    override suspend fun approveQrLogin(qrLink: String) {
        approvedQrLinks += qrLink
    }

    override suspend fun getChatMembers(chatId: Long): List<UserInfo> = emptyList()

    override suspend fun getReadMarks(chatId: Long): Map<Long, Long> {
        readMarksRequested += chatId
        return readMarksToReturn
    }

    override suspend fun addMembers(
        chatId: Long,
        userIds: List<Long>,
    ) {
        addedMembers += chatId to userIds
    }

    override suspend fun removeMember(
        chatId: Long,
        userId: Long,
    ) {
        removedMembers += chatId to userId
    }

    override suspend fun setAdmin(
        chatId: Long,
        userId: Long,
        admin: Boolean,
    ) {
        adminChanges += Triple(chatId, userId, admin)
    }

    override suspend fun createGroup(
        title: String,
        memberIds: List<Long>,
        photoToken: String?,
    ): Long {
        createdGroups += title to memberIds
        return 999L
    }

    override suspend fun findByPhone(phone: String): FoundUser = FoundUser(1L, "Найден")

    override suspend fun addContact(
        userId: Long,
        firstName: String,
    ) {}

    override fun dialogChatId(
        myId: Long,
        otherId: Long,
    ): Long = myId xor otherId

    override suspend fun fetchUser(userId: Long): UserInfo? = userToReturn

    /** CHAT_MEDIA answers, and the (chatId, types, anchor, withAnchor) each request carried. */
    var chatMedia: List<Message> = emptyList()

    /** When set, answers every CHAT_MEDIA request by its anchor. */
    var chatMediaPage: ((String) -> List<Message>)? = null
    val chatMediaCalls = mutableListOf<List<Any>>()

    override suspend fun fetchChatMedia(
        chatId: Long,
        types: List<String>,
        anchor: String,
        count: Int,
        withAnchor: Boolean,
    ): List<Message> {
        chatMediaCalls += listOf(chatId, types, anchor, withAnchor)
        chatMediaPage?.let { return it(anchor) }
        return if (withAnchor) chatMedia else emptyList()
    }

    val votes = mutableListOf<List<Int>>()
    var voteFailure: Throwable? = null
    var voteGate: CompletableDeferred<Unit>? = null

    override suspend fun votePoll(
        chatId: Long,
        messageId: String,
        poll: Poll,
        answerIds: List<Int>,
    ): Poll? {
        votes += answerIds
        voteGate?.await()
        voteFailure?.let { throw it }
        return null
    }

    var reactionUsers: List<Pair<Long, String>> = emptyList()

    override suspend fun getReactionUsers(
        chatId: Long,
        messageId: String,
    ): List<Pair<Long, String>> = reactionUsers

    val pressed = mutableListOf<BotButton>()
    var pressGate: CompletableDeferred<Unit>? = null

    override suspend fun pressBotButton(button: BotButton) {
        pressed += button
        pressGate?.await()
    }

    val clearCalls = mutableListOf<Pair<Long, Long>>()
    var clearFailure: Throwable? = null

    override suspend fun clearHistory(
        chatId: Long,
        lastEventTime: Long,
    ) {
        clearCalls += chatId to lastEventTime
        clearFailure?.let { throw it }
    }

    var unreadMarks = mutableListOf<Pair<Long, Long>>()
    var unreadFailure: Throwable? = null

    override suspend fun markUnread(
        chatId: Long,
        mark: Long,
    ) {
        unreadMarks += chatId to mark
        unreadFailure?.let { throw it }
    }

    val blockCalls = mutableListOf<Pair<Long, Boolean>>()
    var blockFailure: Throwable? = null
    var blockedToReturn: List<UserInfo> = emptyList()

    override suspend fun setBlocked(
        userId: Long,
        blocked: Boolean,
    ) {
        blockCalls += userId to blocked
        blockFailure?.let { throw it }
    }

    /** When set, every page starts at 0 whatever `from` says (a server that ignores it). */
    var blockedIgnoresFrom = false
    val blockedFroms = mutableListOf<Int>()

    override suspend fun getBlockedPage(from: Int): BlockedPage {
        blockedFroms += from
        val page = blockedToReturn.drop(if (blockedIgnoresFrom) 0 else from).take(BlockedPage.SIZE)
        return BlockedPage(page, page.size)
    }

    data class ChatEdit(
        val chatId: Long,
        val title: String?,
        val description: String?,
        val photoToken: String?,
        val options: Map<String, Boolean>,
    )

    val chatEdits = mutableListOf<ChatEdit>()
    var chatEditFailure: Throwable? = null
    val createdChannels = mutableListOf<String>()

    override suspend fun editChat(
        chatId: Long,
        title: String?,
        description: String?,
        photoToken: String?,
        options: Map<String, Boolean>,
    ): Chat? {
        chatEdits += ChatEdit(chatId, title, description, photoToken, options)
        chatEditFailure?.let { throw it }
        return null
    }

    override suspend fun createChannel(
        title: String,
        photoToken: String?,
    ): Long {
        createdChannels += title
        return -77L
    }

    val renames = mutableListOf<Triple<Long, String, String?>>()
    val removedContacts = mutableListOf<Long>()
    var contactFailure: Throwable? = null

    override suspend fun renameContact(
        userId: Long,
        firstName: String,
        lastName: String?,
    ): UserInfo? {
        renames += Triple(userId, firstName, lastName)
        contactFailure?.let { throw it }
        return null
    }

    override suspend fun removeContact(userId: Long) {
        removedContacts += userId
        contactFailure?.let { throw it }
    }

    var shelfCalls = 0
    var shelf = StickerShelf(emptyList(), emptyList())
    val stickerAsks = mutableListOf<List<Long>>()

    override suspend fun getStickerShelf(): StickerShelf {
        shelfCalls++
        return shelf
    }

    override suspend fun getStickers(ids: List<Long>): List<Sticker> {
        stickerAsks += ids
        return ids.map { Sticker(it, "https://s/$it") }
    }

    var foldersToReturn: FoldersUpdate? = null
    val savedFolders = mutableListOf<ChatFolder>()
    val folderOrders = mutableListOf<List<String>>()
    val deletedFolders = mutableListOf<String>()
    var folderFailure: Throwable? = null

    override suspend fun getFolders(): FoldersUpdate? = foldersToReturn

    /** When not empty, each [saveFolder] waits for the next gate; failing a gate refuses that write. */
    val folderGates = ArrayDeque<CompletableDeferred<Unit>>()

    override suspend fun saveFolder(folder: ChatFolder) {
        savedFolders += folder
        folderGates.removeFirstOrNull()?.await()
        folderFailure?.let { throw it }
    }

    override suspend fun reorderFolders(order: List<String>) {
        folderOrders += order
        folderFailure?.let { throw it }
    }

    override suspend fun deleteFolder(id: String) {
        deletedFolders += id
        folderFailure?.let { throw it }
    }

    val pinCalls = mutableListOf<Pair<Long, String?>>()
    var pinFailure: Throwable? = null

    val pinNotifies = mutableListOf<Boolean>()

    override suspend fun pinMessage(
        chatId: Long,
        messageId: String?,
        notify: Boolean,
    ): Chat? {
        pinCalls += chatId to messageId
        pinNotifies += notify
        pinFailure?.let { throw it }
        return null
    }

    /** Every batch asked of [fetchUsers]; answered from [usersToReturn]. */
    val userBatches = mutableListOf<List<Long>>()
    var usersToReturn: List<UserInfo> = emptyList()

    override suspend fun fetchUsers(ids: List<Long>): List<UserInfo> {
        userBatches += ids
        return usersToReturn.filter { it.id in ids }
    }

    var searchMarker: Long? = null
    val searchMarkersAsked = mutableListOf<Long?>()

    override suspend fun searchChats(
        query: String,
        marker: Long?,
    ): SearchPage {
        searchMarkersAsked += marker
        return SearchPage(searchResultsToReturn, searchMarker.takeIf { marker == null })
    }

    var messageHits: List<MessageHit> = emptyList()
    val messageSearches = mutableListOf<Pair<Long?, String>>()

    override suspend fun searchMessages(
        chatId: Long?,
        query: String,
    ): List<MessageHit> {
        messageSearches += chatId to query
        return messageHits
    }

    override suspend fun fetchContactName(userId: Long): String? = null

    override suspend fun getVideoUrl(
        chatId: Long,
        messageId: Long,
        videoId: Long,
    ): String? {
        videoRequests += videoId
        return videoUrl
    }

    /** When set, [fetchHistory] waits on it — lets a test see the pre-fetch state. */
    var historyGate: CompletableDeferred<Unit>? = null

    /** URL handed back for a file attachment; null means "no link". */
    var fileUrl: String? = null

    val fileUrlRequests = mutableListOf<Long>()

    /** URL handed back for a voice message; null means "no link". */
    var audioUrl: String? = null

    val audioRequests = mutableListOf<Long>()

    /** When set, [getAudioUrl] waits on it — holds the bubble in its loading state. */
    var audioGate: CompletableDeferred<Unit>? = null

    /** URL handed back for a video; null means "no link". */
    var videoUrl: String? = null

    val videoRequests = mutableListOf<Long>()

    override suspend fun getAudioUrl(
        chatId: Long,
        messageId: Long,
        audioId: Long,
        token: String?,
    ): String? {
        audioRequests += audioId
        audioGate?.await()
        return audioUrl
    }

    override suspend fun getFileUrl(
        chatId: Long,
        messageId: Long,
        fileId: Long,
    ): String? {
        fileUrlRequests += fileId
        return fileUrl
    }
}

/**
 * [MediaContent] that records how often it is opened, so a test can tell whether
 * staging an attachment reads it (it must not — that is what used to OOM).
 */
private class CountingContent(
    private val payload: ByteArray = ByteArray(32),
) : MediaContent {
    var opens = 0
        private set

    override val size: Long get() = payload.size.toLong()
    override val previewModel: Any = "counting-content"

    override fun openChannel(): ByteReadChannel {
        opens++
        return ByteReadChannel(payload)
    }
}

/**
 * Stand-in for the platform player. The real desktop one hands the clip to the
 * system, which opened a browser tab per test run.
 */
private class FakeVoicePlayer : VoicePlayback {
    val played = mutableListOf<String>()
    val seeks = mutableListOf<Float>()
    var pauses = 0
    var resumes = 0
    var stops = 0

    /** Set to have [play] report that audio started (and then progress/finish). */
    var autoStart = false

    override fun play(
        url: String,
        durationHintMs: Long,
        onStarted: () -> Unit,
        onProgress: (Float) -> Unit,
        onFinished: () -> Unit,
    ) {
        played += url
        if (autoStart) onStarted()
    }

    override fun pause() {
        pauses++
    }

    override fun resume() {
        resumes++
    }

    override fun seekTo(fraction: Float) {
        seeks += fraction
    }

    override fun stop() {
        stops++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private lateinit var fake: FakeMaxClient
    private lateinit var prefs: Prefs
    private lateinit var player: FakeVoicePlayer

    @BeforeTest
    fun setUp() {
        // viewModelScope runs on Dispatchers.Main; an unconfined test dispatcher
        // runs launched work eagerly so each call's effects are visible at once.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fake = FakeMaxClient()
        player = FakeVoicePlayer()
        prefs = Prefs(InMemoryStorage())
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        AppViewModel(
            prefs,
            fake,
            // Zero, so the voice-send retry doesn't park on a delay the unconfined
            // test dispatcher never advances.
            voiceSendRetryDelayMs = 0,
            voicePlayer = player,
        )

    /** Logs in (token preset) and returns a ViewModel already on the CHATS screen. */
    private fun loggedIn(chats: List<Chat> = emptyList()): AppViewModel {
        prefs.token = "tok"
        fake.chats = chats
        return viewModel()
    }

    // --- warm-start cache + drafts ---

    private fun cache() = AppCache(prefs.storage)

    @Test
    fun cachedSessionPaintsTheChatListBeforeSyncCompletes() {
        cache().saveSession(
            CachedSession(
                userId = 100L,
                account = Account(userId = 100L, firstName = "Я"),
                chats = listOf(Chat(id = 5, title = "Аня", lastMessageText = "привет", lastEventTime = 9)),
                contacts = mapOf(7L to "Аня"),
            ),
        )
        prefs.token = "tok"
        prefs.userId = 100L
        // Sync never lands, so whatever is on screen came from the cache.
        fake.syncFailure = RuntimeException("нет сети")

        val s = viewModel().state.value

        assertEquals(Screen.CHATS, s.screen, "cached chats should show immediately, not a loading screen")
        assertEquals("Аня", s.chats.single().title, "the cached title must not be a 'Диалог <id>' placeholder")
        assertFalse(s.syncedOnce, "nothing has synced yet")
    }

    @Test
    fun cachedSessionOfAnotherAccountIsIgnored() {
        cache().saveSession(
            CachedSession(
                userId = 100L,
                chats = listOf(Chat(id = 5, title = "Аня", lastMessageText = null, lastEventTime = 9)),
            ),
        )
        prefs.token = "tok"
        prefs.userId = 555L // signed in as someone else
        fake.syncFailure = RuntimeException("нет сети")

        val s = viewModel().state.value

        assertTrue(s.chats.isEmpty(), "another account's cached chats must never be shown")
        assertEquals(Screen.LOADING, s.screen)
    }

    @Test
    fun successfulSyncWritesTheWarmStartCache() {
        val chat = Chat(id = 7, title = "Аня", lastMessageText = "привет", lastEventTime = 1)
        loggedIn(listOf(chat))

        val cached = cache().loadSession(100L)
        assertEquals(listOf(chat), cached?.chats)
        assertEquals(100L, cached?.account?.userId)
    }

    @Test
    fun demoSessionDoesNotOverwriteTheCache() {
        cache().saveSession(
            CachedSession(
                userId = 100L,
                chats = listOf(Chat(id = 5, title = "Настоящий чат", lastMessageText = null, lastEventTime = 9)),
            ),
        )
        val vm = viewModel()
        vm.requestCode("+79990000000")
        vm.submitCode("00000")

        assertTrue(vm.state.value.demoMode)
        assertEquals(
            "Настоящий чат",
            cache()
                .loadSession(100L)
                ?.chats
                ?.single()
                ?.title,
            "the offline demo's fake chats must not replace a real account's cache",
        )
    }

    @Test
    fun draftSurvivesLeavingAndReopeningTheChat() {
        val chat = Chat(id = 42, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        vm.setDraft("недописанное сообщение")
        vm.onBack()

        vm.openChat(chat)

        assertEquals("недописанное сообщение", vm.state.value.draft)
    }

    @Test
    fun draftIsPersistedForTheNextLaunch() {
        val chat = Chat(id = 42, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val first = loggedIn(listOf(chat))
        first.openChat(chat)
        first.setDraft("недописанное сообщение")
        first.onBack() // closing the chat flushes the draft to storage

        // A fresh ViewModel over the same storage = the next app launch.
        val next = viewModel()
        next.openChat(chat)

        assertEquals("недописанное сообщение", next.state.value.draft)
    }

    @Test
    fun draftsAreKeptPerChat() {
        val a = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val b = Chat(id = 2, title = "Борис", lastMessageText = null, lastEventTime = 2)
        val vm = loggedIn(listOf(a, b))
        vm.openChat(a)
        vm.setDraft("для Ани")
        vm.onBack()
        vm.openChat(b)

        assertEquals("", vm.state.value.draft, "a draft must not leak into another chat")

        vm.setDraft("для Бориса")
        vm.onBack()
        vm.openChat(a)
        assertEquals("для Ани", vm.state.value.draft)
    }

    // --- staged media is streamed, not held in memory ---

    @Test
    fun stagingMediaDoesNotReadItsContent() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        val content = CountingContent()

        // Sharing media in stages it for a chat pick — the point where the old code
        // had already read every byte into the heap.
        vm.beginShare(listOf(PickedMedia(content, "image/jpeg", "photo.jpg", PickedKind.PHOTO)))

        assertEquals(0, content.opens, "staged media must not be read until it is sent")
    }

    @Test
    fun contentIsOpenedOnceWhenSent() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        val content = CountingContent()

        vm.sendMedia(listOf(PickedMedia(content, "image/jpeg", "photo.jpg", PickedKind.PHOTO)), "подпись")

        assertEquals(listOf("photo.jpg"), fake.uploaded)
        assertEquals(1, content.opens, "the content should be streamed exactly once")
    }

    @Test
    fun mediaBubbleAppearsBeforeTheUploadsFinish() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        // Hold the send back so the optimistic bubble can be observed mid-flight.
        fake.sendGate = CompletableDeferred()

        vm.sendMedia(
            listOf(
                PickedMedia(CountingContent(), "image/jpeg", "a.jpg", PickedKind.PHOTO),
                PickedMedia(CountingContent(), "image/jpeg", "b.jpg", PickedKind.PHOTO),
            ),
            "подпись",
        )

        val bubble =
            vm.state.value.visibleMessages
                .single { it.id == null }
        assertEquals("подпись", bubble.text)
        assertEquals(2, bubble.pending.size, "each item needs its own thumbnail")
        assertTrue(bubble.pending.all { it.state == UploadState.DONE }, "uploads finished, the send has not")
        fake.sendGate?.complete(Unit)
    }

    @Test
    fun eachAttachmentShowsItsOwnProgressWhileUploading() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        // Park the first upload half-way so the in-flight state can be inspected.
        fake.uploadGate = CompletableDeferred()

        vm.sendMedia(
            listOf(
                PickedMedia(CountingContent(), "image/jpeg", "a.jpg", PickedKind.PHOTO),
                PickedMedia(CountingContent(), "image/jpeg", "b.jpg", PickedKind.PHOTO),
            ),
            "",
        )

        val pending =
            vm.state.value.visibleMessages
                .single { it.id == null }
                .pending
        assertEquals(UploadState.UPLOADING, pending[0].state)
        assertEquals(0.5f, pending[0].progress, "progress should follow the body going out")
        assertEquals(UploadState.QUEUED, pending[1].state, "the rest wait their turn")

        fake.uploadGate?.complete(Unit)
    }

    @Test
    fun inFlightSendSurvivesLeavingAndReenteringTheChat() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.uploadGate = CompletableDeferred() // keep the upload in flight

        vm.sendMedia(listOf(PickedMedia(CountingContent(), "image/jpeg", "a.jpg", PickedKind.PHOTO)), "подпись")
        vm.onBack()
        vm.openChat(chat)

        val bubble =
            vm.state.value.visibleMessages
                .singleOrNull { it.id == null }
        assertTrue(bubble != null, "the uploading bubble must come back with the chat")
        assertEquals(1, bubble.pending.size)
        assertEquals("подпись", bubble.text)

        fake.uploadGate?.complete(Unit)
    }

    @Test
    fun aPendingSendOfAnotherChatIsNotShownHere() {
        val a = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val b = Chat(id = 2, title = "Борис", lastMessageText = null, lastEventTime = 2)
        val vm = loggedIn(listOf(a, b))
        vm.openChat(a)
        fake.uploadGate = CompletableDeferred()
        vm.sendMedia(listOf(PickedMedia(CountingContent(), "image/jpeg", "a.jpg", PickedKind.PHOTO)), "для Ани")

        vm.onBack()
        vm.openChat(b)

        assertTrue(
            vm.state.value.visibleMessages
                .none { it.id == null },
            "another chat's pending send must not leak into this one",
        )
        fake.uploadGate?.complete(Unit)
    }

    @Test
    fun serverMessageReplacesTheOptimisticBubble() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        vm.sendMedia(listOf(PickedMedia(CountingContent(), "image/jpeg", "a.jpg", PickedKind.PHOTO)), "")

        val messages = vm.state.value.visibleMessages
        assertEquals(1, messages.size, "the optimistic bubble must not be left behind as a duplicate")
        assertTrue(messages.single().id != null, "the server's copy should have taken over")
        assertTrue(messages.single().pending.isEmpty())
        assertTrue(
            vm.state.value.pendingSends
                .isEmpty(),
            "the pending send should be retired",
        )
    }

    @Test
    fun pushedEchoOfOurOwnMediaDoesNotDuplicateTheBubble() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.sendGate = CompletableDeferred()
        vm.sendMedia(listOf(PickedMedia(CountingContent(), "image/jpeg", "a.jpg", PickedKind.PHOTO)), "подпись")
        val cid =
            vm.state.value.visibleMessages
                .single { it.id == null }
                .cid!!

        // The server pushes our own message back before the send call returns.
        fake.emitIncoming(
            Message(id = "srv-$cid", cid = cid, chatId = 1, senderId = 100L, text = "подпись", time = cid),
        )

        val messages = vm.state.value.visibleMessages
        assertEquals(1, messages.size, "the echo should replace the optimistic bubble, not sit beside it")
        assertEquals("srv-$cid", messages.single().id)
        fake.sendGate?.complete(Unit)
    }

    @Test
    fun oneFailedUploadStillSendsTheRest() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.failingUploads = setOf("bad.jpg")

        vm.sendMedia(
            listOf(
                PickedMedia(CountingContent(), "image/jpeg", "good.jpg", PickedKind.PHOTO),
                PickedMedia(CountingContent(), "image/jpeg", "bad.jpg", PickedKind.PHOTO),
                PickedMedia(CountingContent(), "video/mp4", "clip.mp4", PickedKind.VIDEO),
            ),
            "",
        )

        // All three were attempted, and the good two were sent rather than lost.
        assertEquals(listOf("good.jpg", "bad.jpg", "clip.mp4"), fake.uploaded)
        assertEquals(2, fake.lastAttaches.size, "the batch should carry the items that uploaded")
        assertEquals("Не отправлено вложений: 1", vm.state.value.notice)
        assertFalse(vm.state.value.sendingAttachment)
    }

    @Test
    fun aFailedSendMarksTheUploadedAttachmentsAsFailed() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.sendFailure = RuntimeException("чат закрыт")

        vm.sendMedia(listOf(PickedMedia(CountingContent(), "image/jpeg", "a.jpg", PickedKind.PHOTO)), "")

        val s = vm.state.value
        assertEquals("чат закрыт", s.error)
        assertEquals(
            UploadState.FAILED,
            s.visibleMessages
                .single()
                .pending
                .single()
                .state,
            "uploads succeeded but the message didn't send — the bubble must not look finished",
        )
        assertFalse(s.sendingAttachment)
    }

    @Test
    fun nothingIsSentWhenEveryUploadFails() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.failingUploads = setOf("a.jpg")

        vm.sendMedia(listOf(PickedMedia(CountingContent(), "image/jpeg", "a.jpg", PickedKind.PHOTO)), "")

        val s = vm.state.value
        assertEquals("Не удалось загрузить вложения", s.error)
        assertTrue(
            s.visibleMessages
                .single()
                .pending
                .single()
                .state == UploadState.FAILED,
            "the bubble should show the failure",
        )
        assertFalse(s.sendingAttachment)
    }

    @Test
    fun theCacheSizeReadoutCountsMessageHistory() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        fake.history = listOf(Message(id = "9", cid = null, chatId = 1, senderId = 200, text = "с сервера", time = 9))
        vm.openChat(chat)

        vm.refreshCacheSize()

        // The images live in Coil's cache, which isn't wired up in tests — but the
        // data side must at least account for the history we just stored.
        assertTrue(vm.state.value.dataCacheBytes > 0, "cached history should be counted")
        assertEquals(0L, vm.state.value.imageCacheBytes, "no image cache in tests")
    }

    @Test
    fun clearingTheCacheZeroesTheReadout() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        fake.history = listOf(Message(id = "9", cid = null, chatId = 1, senderId = 200, text = "с сервера", time = 9))
        vm.openChat(chat)
        vm.refreshCacheSize()
        val before = vm.state.value.dataCacheBytes

        vm.clearCache()

        assertTrue(before > 0)
        assertTrue(
            vm.state.value.dataCacheBytes < before,
            "the readout should drop once the cache is emptied",
        )
    }

    // --- voice messages ---

    private fun voiceMessage(id: String = "7") =
        Message(
            id = id,
            cid = null,
            chatId = 1,
            senderId = 200,
            text = "",
            time = 5,
            voice = VoiceAttach(audioId = 42, durationSeconds = 7, waveform = listOf(0.2f, 1f)),
        )

    @Test
    fun playingAVoiceMessageResolvesItsUrl() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.audioUrl = "https://cdn/voice.m4a"

        vm.toggleVoice(voiceMessage())

        assertEquals(listOf(42L), fake.audioRequests)
        assertEquals(listOf("https://cdn/voice.m4a"), player.played, "the clip has to reach the player")
        assertNull(vm.state.value.error)
    }

    @Test
    fun leavingTheChatStopsThePlayerItself() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.audioUrl = "https://cdn/voice.m4a"
        player.autoStart = true
        vm.toggleVoice(voiceMessage())

        vm.onBack()

        assertTrue(player.stops > 0, "the player must be stopped, not just the state cleared")
    }

    @Test
    fun tappingAPlayingMessagePausesItRatherThanStopping() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.audioUrl = "https://cdn/voice.m4a"
        fake.audioGate = CompletableDeferred() // hold it "loaded" for the test
        vm.toggleVoice(voiceMessage())

        vm.toggleVoice(voiceMessage())

        // Still loaded, just held: the position has to survive a pause.
        val paused = vm.state.value.playingVoice
        assertEquals("7", paused?.messageId)
        assertTrue(paused?.paused == true)
        assertEquals(1, player.pauses, "the player has to be paused, not stopped")
        fake.audioGate?.complete(Unit)
    }

    @Test
    fun tappingAPausedMessageResumesIt() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.audioUrl = "https://cdn/voice.m4a"
        fake.audioGate = CompletableDeferred()
        vm.toggleVoice(voiceMessage())
        vm.toggleVoice(voiceMessage()) // pause

        vm.toggleVoice(voiceMessage()) // resume

        assertTrue(
            vm.state.value.playingVoice
                ?.paused == false,
        )
        assertEquals(1, player.resumes)
        fake.audioGate?.complete(Unit)
    }

    @Test
    fun seekingWorksWhilePaused() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.audioUrl = "https://cdn/voice.m4a"
        fake.audioGate = CompletableDeferred()
        vm.toggleVoice(voiceMessage())
        vm.toggleVoice(voiceMessage()) // pause

        vm.seekVoice(0.25f)

        assertEquals(listOf(0.25f), player.seeks, "the seek has to reach the player")
        val state = vm.state.value.playingVoice
        assertEquals(0.25f, state?.progress)
        assertTrue(state?.paused == true, "seeking must not silently resume")
        fake.audioGate?.complete(Unit)
    }

    @Test
    fun aVoiceMessageWithNoUrlReportsInsteadOfHanging() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.audioUrl = null

        vm.toggleVoice(voiceMessage())

        assertNull(vm.state.value.playingVoice, "the bubble must not be left spinning")
        assertEquals("Сервер не вернул ссылку на аудио", vm.state.value.error)
    }

    @Test
    fun leavingTheChatStopsPlayback() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.audioUrl = "https://cdn/voice.m4a"
        vm.toggleVoice(voiceMessage())

        vm.onBack()

        assertNull(vm.state.value.playingVoice)
    }

    @Test
    fun aBubbleIsLoadingUntilPlaybackStarts() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.audioUrl = "https://cdn/voice.m4a"
        fake.audioGate = CompletableDeferred() // still resolving the link

        vm.toggleVoice(voiceMessage())

        val playing = vm.state.value.playingVoice
        assertEquals("7", playing?.messageId)
        assertNull(playing?.progress, "a null position is what marks the bubble as still loading")
        fake.audioGate?.complete(Unit)
    }

    @Test
    fun seekingMovesThePositionAtOnce() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.audioUrl = "https://cdn/voice.m4a"
        fake.audioGate = CompletableDeferred()
        vm.toggleVoice(voiceMessage())

        vm.seekVoice(0.5f)

        // Reflected without waiting for the player, so the bar tracks the finger.
        assertEquals(
            0.5f,
            vm.state.value.playingVoice
                ?.progress,
        )
        fake.audioGate?.complete(Unit)
    }

    @Test
    fun seekingWithNothingPlayingIsHarmless() {
        val vm = loggedIn()
        vm.seekVoice(0.5f)
        assertNull(vm.state.value.playingVoice)
    }

    // --- sending a voice message ---

    private fun recorded(seconds: Int = 5) = RecordedVoice(content = CountingContent(), durationSeconds = seconds)

    @Test
    fun aRecordingIsUploadedAndSent() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        vm.sendVoice(recorded(seconds = 12))

        assertEquals(listOf(12), fake.uploadedVoices)
        assertEquals(
            listOf(OutAttach.Voice(token = "voice-token", durationSeconds = 12)),
            fake.lastAttaches,
            "the message should carry the voice attach",
        )
        assertTrue(
            vm.state.value.pendingSends
                .isEmpty(),
            "the bubble retires once sent",
        )
    }

    @Test
    fun aRecordingAppearsAsABubbleWhileItUploads() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.sendGate = CompletableDeferred()

        vm.sendVoice(recorded())

        val bubble =
            vm.state.value.visibleMessages
                .single { it.id == null }
        assertEquals(1, bubble.pending.size, "a voice send needs its own progress entry")
        fake.sendGate?.complete(Unit)
    }

    @Test
    fun aFailedVoiceUploadLeavesARetryableBubble() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.voiceUploadFailure = RuntimeException("нет сети")

        vm.sendVoice(recorded())

        val bubble =
            vm.state.value.visibleMessages
                .single { it.id == null }
        assertEquals(UploadState.FAILED, bubble.pending.single().state)

        // Retrying uploads again and gets it away.
        fake.voiceUploadFailure = null
        vm.retrySend(bubble)

        assertTrue(
            vm.state.value.pendingSends
                .isEmpty(),
        )
        assertEquals(1, fake.uploadedVoices.size)
    }

    @Test
    fun aRetryAfterAFailedVoiceSendDoesNotReuploadTheClip() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.sendFailure = RuntimeException("чат закрыт")

        vm.sendVoice(recorded())
        val bubble =
            vm.state.value.visibleMessages
                .single { it.id == null }
        fake.sendFailure = null
        vm.retrySend(bubble)

        assertEquals(1, fake.uploadedVoices.size, "the clip was already up; only the send is retried")
        assertTrue(
            vm.state.value.pendingSends
                .isEmpty(),
        )
    }

    @Test
    fun aRecordingRefusedTwiceStillGetsThrough() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        // Refused twice while the server finishes with the clip; the third try lands.
        fake.sendFailuresRemaining = 2

        vm.sendVoice(recorded())

        assertTrue(
            vm.state.value.pendingSends
                .isEmpty(),
            "the send should recover on its own",
        )
        assertEquals(1, fake.uploadedVoices.size, "the clip is uploaded once, not per attempt")
    }

    @Test
    fun aRecordingRefusedEveryTimeEndsAsAFailedBubble() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        // A real rejection must surface rather than be retried away.
        fake.sendFailuresRemaining = 99

        vm.sendVoice(recorded())

        val bubble =
            vm.state.value.visibleMessages
                .single { it.id == null }
        assertEquals(UploadState.FAILED, bubble.pending.single().state)
        assertEquals(3, fake.sendAttempts, "retries are bounded, not endless")
    }

    @Test
    fun aRecordingRefusedOnceIsSentWithoutTheUserRetrying() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        // The server refuses the first send while it finishes processing the clip.
        fake.sendFailuresRemaining = 1

        vm.sendVoice(recorded())

        assertTrue(
            vm.state.value.pendingSends
                .isEmpty(),
            "the send should recover on its own",
        )
        assertEquals(1, fake.uploadedVoices.size, "the clip is uploaded once, not per attempt")
    }

    // --- video messages ---

    private fun videoNoteMessage(id: String = "8") =
        Message(
            id = id,
            cid = null,
            chatId = 1,
            senderId = 200,
            text = "",
            time = 6,
            media = listOf(MediaAttach(MediaType.VIDEO, "https://cdn/t.jpg", 200, 200, videoId = 55, isVideoNote = true)),
        )

    @Test
    fun aVideoNotePlaysInPlaceRatherThanInTheViewer() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.videoUrl = "https://cdn/note.mp4"

        vm.toggleVideoNote(videoNoteMessage())

        assertEquals(listOf(55L), fake.videoRequests)
        assertEquals(
            "https://cdn/note.mp4",
            vm.state.value.playingVideoNote
                ?.url,
        )
        assertNull(vm.state.value.mediaViewer, "the full-screen viewer must not open")
    }

    @Test
    fun tappingAPlayingVideoNoteStopsIt() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.videoUrl = "https://cdn/note.mp4"
        vm.toggleVideoNote(videoNoteMessage())

        vm.toggleVideoNote(videoNoteMessage())

        assertNull(vm.state.value.playingVideoNote)
    }

    @Test
    fun aVoiceMessageAndACircleDoNotPlayTogether() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.videoUrl = "https://cdn/note.mp4"
        fake.audioUrl = "https://cdn/voice.m4a"
        fake.audioGate = CompletableDeferred()
        vm.toggleVideoNote(videoNoteMessage())

        vm.toggleVoice(voiceMessage())

        assertNull(vm.state.value.playingVideoNote, "starting a voice clip stops the circle")
        fake.audioGate?.complete(Unit)
    }

    @Test
    fun aVideoNoteWithNoStreamReportsInsteadOfSpinning() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.videoUrl = null

        vm.toggleVideoNote(videoNoteMessage())

        assertNull(vm.state.value.playingVideoNote)
        assertEquals("Не удалось воспроизвести видеосообщение", vm.state.value.error)
    }

    @Test
    fun aVideoNoteIsUploadedAsOneAndSentOnItsOwn() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        vm.sendVideoNote(PickedMedia(CountingContent(), "video/mp4", "clip.mp4", PickedKind.VIDEO))

        assertEquals(1, fake.uploadedVideoNotes.size, "the clip goes through the video-message upload")
        assertTrue(fake.uploaded.isEmpty(), "not through the plain video upload")
        assertEquals(1, fake.lastAttaches.size)
        assertTrue(
            vm.state.value.pendingSends
                .isEmpty(),
            "the bubble retires once sent",
        )
    }

    @Test
    fun aVideoNoteShowsAnOptimisticBubbleWhileItUploads() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.sendGate = CompletableDeferred()

        vm.sendVideoNote(PickedMedia(CountingContent(), "video/mp4", "clip.mp4", PickedKind.VIDEO))

        assertEquals(
            1,
            vm.state.value.visibleMessages
                .count { it.id == null },
        )
        fake.sendGate?.complete(Unit)
    }

    // --- max.ru links ---

    @Test
    fun aLinkToAChatWeAreInJustOpensIt() {
        val chat =
            Chat(
                id = 5,
                title = "Группа",
                lastMessageText = null,
                lastEventTime = 1,
                link = "https://max.ru/joinABC",
            )
        val vm = loggedIn(listOf(chat))

        val handled = vm.openLink("https://max.ru/joinABC")

        assertTrue(handled)
        assertEquals(Screen.CHAT, vm.state.value.screen)
        assertEquals(
            5L,
            vm.state.value.currentChat
                ?.id,
        )
        assertTrue(fake.joinedLinks.isEmpty(), "no need to join a chat we're already in")
    }

    @Test
    fun anUnknownLinkJoinsAndOpensTheChat() {
        val vm = loggedIn()
        fake.joinResult = Chat(id = 9, title = "Новый канал", lastMessageText = null, lastEventTime = 2)

        val handled = vm.openLink("https://max.ru/someInvite")

        assertTrue(handled)
        assertEquals(listOf("someInvite"), fake.joinedLinks)
        assertEquals(
            9L,
            vm.state.value.currentChat
                ?.id,
        )
        assertTrue(
            vm.state.value.chats
                .any { it.id == 9L },
            "the joined chat belongs in the list",
        )
    }

    @Test
    fun aJoinThatDescribesNoChatFallsBackToASync() {
        val joined =
            Chat(id = 11, title = "Группа", lastMessageText = null, lastEventTime = 3, link = "https://max.ru/inv")
        val vm = loggedIn()
        fake.joinResult = null // server accepted, told us nothing
        fake.chats = listOf(joined) // ...but a re-sync shows it

        vm.openLink("https://max.ru/inv")

        assertEquals(
            11L,
            vm.state.value.currentChat
                ?.id,
            "the chat should be found by its link after a sync",
        )
    }

    @Test
    fun groupCallLinksAreNotSupportedYet() {
        val vm = loggedIn()

        val handled = vm.openLink("https://max.ru/joincall/abc")

        assertTrue(handled, "a call link is still ours to handle, not the browser's")
        assertEquals("Групповые звонки по ссылке пока не поддерживаются", vm.state.value.notice)
    }

    @Test
    fun otherLinksAreLeftToTheBrowser() {
        val vm = loggedIn()
        assertFalse(vm.openLink("https://example.com/page"))
        assertEquals(Screen.CHATS, vm.state.value.screen)
    }

    @Test
    fun aLinkOpenedBeforeSignInIsHandledAfterIt() {
        // No token yet: the link has to wait for the session.
        val vm = viewModel()
        vm.openLink("https://max.ru/waiting")
        assertTrue(fake.joinedLinks.isEmpty())

        fake.joinResult = Chat(id = 3, title = "Позже", lastMessageText = null, lastEventTime = 1)
        fake.codeResult = CodeResult.Success("tok")
        vm.submitCode("123456")

        assertEquals(listOf("waiting"), fake.joinedLinks, "the link should be acted on once signed in")
        assertEquals(
            3L,
            vm.state.value.currentChat
                ?.id,
        )
    }

    @Test
    fun theInviteHashIsWhatIsSentFirst() {
        val vm = loggedIn()
        fake.joinResult = Chat(id = 9, title = "Группа", lastMessageText = null, lastEventTime = 2)

        vm.openLink("https://max.ru/join/SOMEHASH")

        // The official client reduces a link to a single path segment, so the hash is
        // the first thing tried — not "max.ru/join/SOMEHASH".
        assertEquals(listOf("SOMEHASH"), fake.joinedLinks)
        assertEquals(
            9L,
            vm.state.value.currentChat
                ?.id,
        )
    }

    @Test
    fun otherLinkFormsAreTriedIfTheHashIsRefused() {
        val vm = loggedIn()
        fake.acceptedJoinForm = "https://max.ru/join/SOMEHASH" // this server wants the URL
        fake.joinResult = Chat(id = 9, title = "Группа", lastMessageText = null, lastEventTime = 2)

        vm.openLink("https://max.ru/join/SOMEHASH")

        assertEquals(listOf("SOMEHASH", "https://max.ru/join/SOMEHASH"), fake.joinedLinks)
        assertEquals(
            9L,
            vm.state.value.currentChat
                ?.id,
        )
        assertNull(vm.state.value.openExternally, "it worked, so nothing goes to the browser")
    }

    @Test
    fun anInviteTheServerRefusesFallsBackToTheBrowser() {
        val vm = loggedIn()
        fake.acceptedJoinForm = "nothing matches this"

        vm.openLink("https://max.ru/join/SOMEHASH")

        // Every form was refused, so the tap must still lead somewhere.
        assertEquals(3, fake.joinedLinks.size, "hash, url and host/path should all be tried")
        assertEquals("https://max.ru/join/SOMEHASH", vm.state.value.openExternally)
        assertEquals("Ссылку не удалось открыть в приложении", vm.state.value.notice)

        vm.consumedExternalLink()
        assertNull(vm.state.value.openExternally)
    }

    // --- cached history ---

    private fun messageCache() = MessageCache(prefs.storage)

    @Test
    fun openingAChatShowsItsCachedMessagesBeforeTheFetch() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        messageCache().save(1, listOf(Message(id = "7", cid = null, chatId = 1, senderId = 200, text = "из кэша", time = 5)))
        val vm = loggedIn(listOf(chat))
        // Nothing comes back from the server, so what's on screen came from the cache.
        fake.history = emptyList()
        fake.historyGate = CompletableDeferred()

        vm.openChat(chat)

        assertEquals(
            listOf("из кэша"),
            vm.state.value.messages
                .map { it.text },
        )
        fake.historyGate?.complete(Unit)
    }

    @Test
    fun fetchedHistoryIsCachedForNextTime() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        fake.history = listOf(Message(id = "9", cid = null, chatId = 1, senderId = 200, text = "с сервера", time = 9))

        vm.openChat(chat)

        assertEquals(listOf("с сервера"), messageCache().load(1).map { it.text })
    }

    @Test
    fun clearingTheCacheAlsoForgetsMessageHistory() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        fake.history = listOf(Message(id = "9", cid = null, chatId = 1, senderId = 200, text = "с сервера", time = 9))
        vm.openChat(chat)

        vm.clearCache()

        assertTrue(messageCache().load(1).isEmpty())
    }

    // --- splitting, cancelling and ordering ---

    private fun photos(count: Int) = (1..count).map { PickedMedia(CountingContent(), "image/jpeg", "p$it.jpg", PickedKind.PHOTO) }

    @Test
    fun aBigPickIsSplitAcrossSeveralMessages() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        vm.sendMedia(photos(23), "подпись")

        // 10 per message by default, so 10 + 10 + 3.
        assertEquals(listOf(10, 10, 3), fake.sentAttachCounts)
        assertEquals(
            listOf("подпись", "", ""),
            fake.sentMessages.map { it.second },
            "the caption belongs on the first message only",
        )
        assertTrue(
            vm.state.value.pendingSends
                .isEmpty(),
            "every chunk should be delivered",
        )
    }

    @Test
    fun aStricterServerLimitIsDiscoveredBySplittingFurther() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        // Tighter than our default chunk of 10: the send must adapt, not fail.
        fake.maxAttachesAccepted = 3

        vm.sendMedia(photos(10), "")

        assertEquals(10, fake.sentAttachCounts.sum(), "every attachment should still be delivered")
        assertTrue(
            fake.sentAttachCounts.all { it <= 3 },
            "no message may exceed what the server accepts: ${fake.sentAttachCounts}",
        )
        assertTrue(
            vm.state.value.pendingSends
                .isEmpty(),
        )
    }

    @Test
    fun aFailedBubbleKeepsItsPlaceInTime() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.sendFailure = RuntimeException("нет сети")
        vm.sendMedia(listOf(PickedMedia(CountingContent(), "image/jpeg", "a.jpg", PickedKind.PHOTO)), "")
        fake.sendFailure = null

        // A later text message must land BELOW the older failed bubble.
        vm.sendMessage("после")

        val visible = vm.state.value.visibleMessages
        val failedIndex = visible.indexOfFirst { it.id == null }
        val laterIndex = visible.indexOfFirst { it.text == "после" }
        assertTrue(failedIndex >= 0 && laterIndex >= 0)
        assertTrue(failedIndex < laterIndex, "the newer message should sit below the older failed one")
    }

    @Test
    fun discardingAPendingSendRemovesItAndItsRetryMaterial() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.sendFailure = RuntimeException("нет сети")
        vm.sendMedia(listOf(PickedMedia(CountingContent(), "image/jpeg", "a.jpg", PickedKind.PHOTO)), "")
        val bubble =
            vm.state.value.visibleMessages
                .single { it.id == null }

        vm.discardPendingSend(bubble)

        assertTrue(
            vm.state.value.pendingSends
                .isEmpty(),
            "the bubble should be gone",
        )
        assertFalse(vm.state.value.sendingAttachment)
        // And it must not come back to life on a retry.
        vm.retrySend(bubble)
        assertTrue(
            vm.state.value.pendingSends
                .isEmpty(),
        )
    }

    @Test
    fun deletingAnUnsentBubbleDiscardsItLocally() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.sendFailure = RuntimeException("нет сети")
        vm.sendMedia(listOf(PickedMedia(CountingContent(), "image/jpeg", "a.jpg", PickedKind.PHOTO)), "")
        val bubble =
            vm.state.value.visibleMessages
                .single { it.id == null }

        vm.deleteMessage(bubble, forAll = false)

        assertTrue(
            vm.state.value.pendingSends
                .isEmpty(),
            "delete must work on a message that never sent",
        )
        assertTrue(fake.deletedMessages.isEmpty(), "nothing to delete on the server")
    }

    @Test
    fun cancellingAnUploadInFlightStopsIt() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.uploadGate = CompletableDeferred() // park the upload
        vm.sendMedia(photos(2), "")
        val bubble =
            vm.state.value.visibleMessages
                .single { it.id == null }

        vm.discardPendingSend(bubble)

        assertTrue(
            vm.state.value.pendingSends
                .isEmpty(),
        )
        fake.uploadGate?.complete(Unit)
        assertTrue(fake.sentMessages.isEmpty(), "a cancelled send must not deliver anything")
    }

    @Test
    fun anIncomingPhotoUpdatesTheChatListPreview() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = "старое сообщение", lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        // Not looking at this chat, so it gets an unread badge.
        fake.emitIncoming(
            Message(
                id = "9",
                cid = null,
                chatId = 1,
                senderId = 200L,
                text = "",
                time = 20,
                media = listOf(MediaAttach(MediaType.PHOTO, "https://cdn/p.jpg", 10, 10)),
            ),
        )

        val row =
            vm.state.value.chats
                .single { it.id == 1L }
        assertEquals("📷 Фото", row.lastMessageText, "an unread badge must not sit next to an already-read message")
        assertEquals(1, row.unreadCount)
    }

    // --- file attachments ---

    @Test
    fun aDownloadedFileIsRememberedAcrossLaunches() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.fileUrl = "https://cdn/doc.pdf"
        val file = FileAttach(fileId = 77L, name = "doc.pdf", size = 1024)
        val message = Message(id = "10", cid = null, chatId = 1, senderId = 200L, text = "", time = 5, files = listOf(file))

        vm.openOrDownloadFile(message, file)

        // The desktop downloader is a stub (no reference), so nothing is remembered
        // here — what matters is that the link was fetched and the flag cleared.
        assertEquals(listOf(77L), fake.fileUrlRequests)
        assertTrue(
            vm.state.value.downloadingFiles
                .isEmpty(),
            "the in-progress flag must be cleared",
        )
    }

    @Test
    fun aFileWithNoLinkDoesNotGetStuckAsDownloading() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.fileUrl = null // server won't give us a link
        val file = FileAttach(fileId = 5L, name = "doc.pdf", size = 10)
        val message = Message(id = "10", cid = null, chatId = 1, senderId = 200L, text = "", time = 5, files = listOf(file))

        vm.openOrDownloadFile(message, file)

        assertTrue(
            vm.state.value.downloadingFiles
                .isEmpty(),
            "a failed download must not leave a spinner behind",
        )
        assertEquals("Не удалось получить ссылку на файл", vm.state.value.error)
    }

    @Test
    fun downloadRecordsAreRestoredOnTheNextLaunch() {
        prefs.token = "tok"
        prefs.userId = 100L
        cache().saveDownloadedFiles(mapOf(42L to "content://downloads/9"))

        assertEquals(mapOf(42L to "content://downloads/9"), viewModel().state.value.downloadedFiles)
    }

    @Test
    fun logoutForgetsWhereDownloadsWent() {
        val vm = loggedIn()
        cache().saveDownloadedFiles(mapOf(42L to "content://downloads/9"))

        vm.logout()

        assertTrue(cache().downloadedFiles().isEmpty())
    }

    // --- retrying a failed send ---

    @Test
    fun retryResendsOnlyTheItemsThatFailed() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.failingUploads = setOf("bad.jpg")

        vm.sendMedia(
            listOf(
                PickedMedia(CountingContent(), "image/jpeg", "good.jpg", PickedKind.PHOTO),
                PickedMedia(CountingContent(), "image/jpeg", "bad.jpg", PickedKind.PHOTO),
            ),
            "подпись",
        )

        // The good one went out; the failed one is left behind, retryable.
        val leftover =
            vm.state.value.visibleMessages
                .single { it.id == null }
        assertEquals(1, leftover.pending.size)
        assertEquals(UploadState.FAILED, leftover.pending.single().state)

        // Second attempt: the upload now succeeds.
        fake.failingUploads = emptySet()
        fake.uploaded.clear()
        vm.retrySend(leftover)

        assertEquals(listOf("bad.jpg"), fake.uploaded, "only the failed item should be uploaded again")
        assertTrue(
            vm.state.value.pendingSends
                .isEmpty(),
            "a successful retry retires the bubble",
        )
        assertEquals(
            2,
            vm.state.value.visibleMessages
                .count { it.id != null },
            "both messages should be delivered",
        )
    }

    @Test
    fun retryAfterAFailedSendDoesNotReuploadTheAttachments() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        // Uploads fine, but the message won't go out.
        fake.sendFailure = RuntimeException("чат закрыт")
        vm.sendMedia(listOf(PickedMedia(CountingContent(), "image/jpeg", "a.jpg", PickedKind.PHOTO)), "")

        val bubble =
            vm.state.value.visibleMessages
                .single { it.id == null }
        assertEquals(UploadState.FAILED, bubble.pending.single().state)

        fake.sendFailure = null
        fake.uploaded.clear()
        vm.retrySend(bubble)

        assertTrue(fake.uploaded.isEmpty(), "the attachment was already up; only the send should be retried")
        assertTrue(
            vm.state.value.pendingSends
                .isEmpty(),
        )
        assertEquals(
            1,
            vm.state.value.visibleMessages
                .count { it.id != null },
        )
    }

    @Test
    fun retryReusesTheOriginalCidSoTheServerCanDeduplicate() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.sendFailure = RuntimeException("нет сети")
        vm.sendMedia(listOf(PickedMedia(CountingContent(), "image/jpeg", "a.jpg", PickedKind.PHOTO)), "")
        val bubble =
            vm.state.value.visibleMessages
                .single { it.id == null }
        val cid = bubble.cid!!

        fake.sendFailure = null
        vm.retrySend(bubble)

        assertEquals(
            "srv-$cid",
            vm.state.value.visibleMessages
                .single { it.id != null }
                .id,
        )
    }

    @Test
    fun retryIsIgnoredWhileTheSendIsStillRunning() {
        val chat = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.uploadGate = CompletableDeferred()
        vm.sendMedia(listOf(PickedMedia(CountingContent(), "image/jpeg", "a.jpg", PickedKind.PHOTO)), "")
        val bubble =
            vm.state.value.visibleMessages
                .single { it.id == null }

        vm.retrySend(bubble) // still in flight — must not start a second upload

        assertEquals(listOf("a.jpg"), fake.uploaded, "a retry must not double-send an in-flight item")
        fake.uploadGate?.complete(Unit)
    }

    @Test
    fun draftIsPublishedForTheChatListPreview() {
        val chat = Chat(id = 42, title = "Аня", lastMessageText = "привет", lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        vm.setDraft("недописанное")
        vm.onBack()

        assertEquals("недописанное", vm.state.value.drafts[42L], "the chat list needs the draft to preview it")
    }

    @Test
    fun draftsAreAvailableForThePreviewOnTheNextLaunch() {
        val chat = Chat(id = 42, title = "Аня", lastMessageText = "привет", lastEventTime = 1)
        val first = loggedIn(listOf(chat))
        first.openChat(chat)
        first.setDraft("недописанное")
        first.onBack()

        // A fresh ViewModel = the next launch: the preview must be there without
        // having to open the chat first.
        assertEquals("недописанное", viewModel().state.value.drafts[42L])
    }

    @Test
    fun sendingClearsTheDraft() {
        val chat = Chat(id = 42, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        vm.setDraft("почти готово")
        // The input reports the now-empty field after a send.
        vm.setDraft("")
        vm.onBack()

        vm.openChat(chat)
        assertEquals("", vm.state.value.draft)
    }

    @Test
    fun clearCacheDropsTheSnapshotButKeepsDrafts() {
        val chat = Chat(id = 42, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        vm.setDraft("черновик")
        vm.onBack()

        vm.clearCache()

        assertNull(cache().loadSession(100L))
        assertEquals("черновик", cache().drafts()[42L], "clearing the cache must not delete unsent text")
        assertEquals(listOf(chat), vm.state.value.chats, "the live session stays as it is")
    }

    @Test
    fun logoutDropsCachedSessionAndDrafts() {
        val chat = Chat(id = 42, title = "Аня", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        vm.setDraft("черновик")
        vm.onBack()

        vm.logout()

        assertNull(cache().loadSession(100L))
        assertTrue(cache().drafts().isEmpty(), "the next user must not see the previous one's drafts")
    }

    @Test
    fun startsOnLoginWhenNoToken() {
        val vm = viewModel()
        assertEquals(Screen.LOGIN, vm.state.value.screen)
    }

    @Test
    fun autoLoginWithTokenSyncsToChats() {
        val chat = Chat(id = 7, title = "Чат", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        val s = vm.state.value
        assertEquals(Screen.CHATS, s.screen)
        assertEquals(listOf(chat), s.chats)
        assertEquals(100L, s.account?.userId)
        assertFalse(s.reconnecting)
        assertEquals(100L, prefs.userId)
    }

    @Test
    fun onlineUsersFromSyncArePublishedToState() {
        fake.online = setOf(200L, 300L)
        val vm = loggedIn()
        assertEquals(setOf(200L, 300L), vm.state.value.onlineUsers)
    }

    @Test
    fun submitCodeSuccessGoesToChats() {
        fake.codeResult = CodeResult.Success("fresh-token")
        val vm = viewModel()
        vm.submitCode("123456")
        assertEquals(Screen.CHATS, vm.state.value.screen)
        assertEquals("fresh-token", prefs.token)
    }

    @Test
    fun forgottenPasswordRestoresViaEmailAndSignsIn() {
        fake.codeResult = CodeResult.NeedPassword(trackId = "track-1", hint = null, email = "a***@mail.ru")
        val vm = viewModel()
        vm.submitCode("123456")
        vm.startPasswordRestore()
        assertEquals(Screen.PASSWORD_RESTORE, vm.state.value.screen)
        assertEquals(
            RestoreStep.EMAIL_CODE,
            vm.state.value.restore
                ?.step,
        )
        assertEquals(
            "a***@mail.ru",
            vm.state.value.restore
                ?.email,
        )

        vm.restoreSubmitCode(" 424242 ")
        assertEquals(
            RestoreStep.NEW_PASSWORD,
            vm.state.value.restore
                ?.step,
        )

        vm.restoreSubmitPassword("new-secret", "typo")
        assertEquals(
            RestoreStep.NEW_PASSWORD,
            vm.state.value.restore
                ?.step,
        ) // mismatch: stays
        vm.restoreSubmitPassword("new-secret", "new-secret")
        assertEquals(
            RestoreStep.HINT,
            vm.state.value.restore
                ?.step,
        )

        vm.restoreSubmitHint("  ")
        assertEquals(Screen.CHATS, vm.state.value.screen)
        assertEquals("restored-token", prefs.token)
        assertNull(vm.state.value.restore)
        // Same track throughout, a blank hint skipped (neither validated nor sent).
        assertEquals(
            listOf("send:track-1", "check:track-1:424242", "validatePassword:new-secret", "restore:track-1:new-secret:null"),
            fake.restoreCalls,
        )
    }

    @Test
    fun forgottenPasswordNeedsARecoveryEmail() {
        fake.codeResult = CodeResult.NeedPassword(trackId = "track-1", hint = null, email = null)
        val vm = viewModel()
        vm.submitCode("123456")
        vm.startPasswordRestore()
        assertEquals(Screen.PASSWORD, vm.state.value.screen)
        assertTrue(fake.restoreCalls.isEmpty())
    }

    @Test
    fun expiredRestoreTrackGoesBackToThePhoneStep() {
        fake.codeResult = CodeResult.NeedPassword(trackId = "track-1", hint = null, email = "a***@mail.ru")
        fake.restoreCodeFailure = AuthSessionExpiredException("Время на вход истекло, запросите код ещё раз")
        val vm = viewModel()
        vm.submitCode("123456")
        vm.startPasswordRestore()
        vm.restoreSubmitCode("111111")
        assertEquals(Screen.LOGIN, vm.state.value.screen)
        assertEquals("Время на вход истекло, запросите код ещё раз", vm.state.value.error)
    }

    @Test
    fun submitCodeNeedPasswordGoesToPasswordScreen() {
        fake.codeResult = CodeResult.NeedPassword(trackId = "track-1", hint = "подсказка")
        val vm = viewModel()
        vm.submitCode("123456")
        val s = vm.state.value
        assertEquals(Screen.PASSWORD, s.screen)
        assertEquals("подсказка", s.passwordHint)
        assertNull(prefs.token)
    }

    @Test
    fun submitCodeNeedRegisterGoesToRegisterScreen() {
        fake.codeResult = CodeResult.NeedRegister
        val vm = viewModel()
        vm.submitCode("123456")
        assertEquals(Screen.REGISTER, vm.state.value.screen)
    }

    @Test
    fun submitCodePasswordRequiredNotSetShowsRestrictedScreenAndRetryReturnsToLogin() {
        fake.codeResult = CodeResult.PasswordRequiredNotSet
        val vm = viewModel()
        vm.submitCode("123456")
        assertEquals(Screen.LOGIN_RESTRICTED, vm.state.value.screen)
        assertNull(prefs.token)
        vm.restartLogin()
        assertEquals(Screen.LOGIN, vm.state.value.screen)
        assertNull(vm.state.value.error)
    }

    @Test
    fun openChatThenBackReturnsToChats() {
        val chat = Chat(id = 42, title = "Диалог", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        assertEquals(Screen.CHAT, vm.state.value.screen)
        vm.onBack()
        assertEquals(Screen.CHATS, vm.state.value.screen)
        assertNull(vm.state.value.currentChat)
    }

    @Test
    fun canGoBackReflectsNavigationDepth() {
        val vm = loggedIn()
        assertTrue(vm.canGoBack(Screen.CHAT, Tab.CHATS))
        assertTrue(vm.canGoBack(Screen.USER, Tab.CHATS))
        assertTrue(vm.canGoBack(Screen.CHATS, Tab.CONTACTS))
        assertFalse(vm.canGoBack(Screen.CHATS, Tab.CHATS))
        assertFalse(vm.canGoBack(Screen.LOGIN, Tab.CHATS))
    }

    @Test
    fun openUserFetchesProfileThenCloseReturnsToChats() {
        val vm = loggedIn()
        fake.userToReturn = UserInfo(id = 5, name = "Полное Имя", description = "био")
        vm.openUser(5)
        val s = vm.state.value
        assertEquals(Screen.USER, s.screen)
        assertEquals("Полное Имя", s.viewingUser?.name)
        vm.closeUser()
        assertEquals(Screen.CHATS, vm.state.value.screen)
        assertNull(vm.state.value.viewingUser)
    }

    @Test
    fun selectTabThenBackResetsToChatsTab() {
        val vm = loggedIn()
        vm.selectTab(Tab.CONTACTS)
        assertEquals(Tab.CONTACTS, vm.state.value.tab)
        vm.onBack()
        assertEquals(Tab.CHATS, vm.state.value.tab)
    }

    @Test
    fun incomingMessageForOpenChatIsAppendedAndMarkedRead() {
        val chat = Chat(id = 42, title = "Диалог", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.markReadCalls.clear() // ignore the open-time mark

        fake.emitIncoming(
            Message(id = "m1", cid = null, chatId = 42, senderId = 200, text = "привет", time = 1000),
        )

        val msgs = vm.state.value.messages
        assertEquals(1, msgs.size)
        assertEquals("m1", msgs.first().id)
        // The mark is the message's own time, not "now" (which would also cover newer messages).
        assertEquals(Triple(42L, "m1", 1000L), fake.markReadCalls.single())
    }

    @Test
    fun pinningShowsAtOnceAndARefusalPutsTheOldPinBack() {
        val old = Message(id = "p0", cid = null, chatId = -5, senderId = 200, text = "старый", time = 1)
        val chat = Chat(id = -5, title = "Группа", lastMessageText = null, lastEventTime = 1, pinnedMessage = old)
        val msg = Message(id = "p1", cid = null, chatId = -5, senderId = 200, text = "важно", time = 2)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        vm.setPinned(msg, pin = true)
        assertEquals(
            "p1",
            vm.state.value.currentChat
                ?.pinnedMessage
                ?.id,
        )
        assertEquals(-5L to "p1", fake.pinCalls.last())

        fake.pinFailure = RuntimeException("chat.denied")
        vm.setPinned(msg, pin = false)

        assertEquals(-5L to null, fake.pinCalls.last(), "unpinning sends no message id")
        assertEquals(
            "p1",
            vm.state.value.currentChat
                ?.pinnedMessage
                ?.id,
            "refused: the pin stays",
        )
        assertEquals("chat.denied", vm.state.value.notice)
    }

    @Test
    fun aChatPushUpdatesTheOpenChatsPinnedBarAndKeepsItsMute() {
        val chat = Chat(id = -5, title = "Группа", lastMessageText = null, lastEventTime = 1, muted = true)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        val pinned = Message(id = "p1", cid = null, chatId = -5, senderId = 200, text = "важно", time = 2)
        fake.emitChatUpdate(chat.copy(muted = false, pinnedMessage = pinned))

        assertEquals(
            "p1",
            vm.state.value.currentChat
                ?.pinnedMessage
                ?.id,
        )
        assertTrue(
            vm.state.value.currentChat!!
                .muted,
        )
        assertTrue(
            vm.state.value.chats
                .single()
                .muted,
        )
    }

    private fun photoMessage(
        id: String,
        time: Long,
    ) = Message(
        id = id,
        cid = null,
        chatId = -5,
        senderId = 200,
        text = "",
        time = time,
        media = listOf(MediaAttach(MediaType.PHOTO, "u$id", 1, 1)),
    )

    @Test
    fun mediaTabPagesBackFromTheNewestMessageAndShowsNewestFirst() {
        fake.history = listOf(Message(id = "last", cid = null, chatId = -5, senderId = 200, text = "hi", time = 9))
        fake.chatMedia = listOf(photoMessage("a", 1), photoMessage("b", 5))
        val vm = loggedIn()

        vm.loadChatMedia(-5, MediaKind.MEDIA)

        assertEquals(listOf(-5L, listOf("PHOTO", "VIDEO"), "last", true), fake.chatMediaCalls.first())
        val media = vm.state.value.infoMedia!!
        assertEquals(listOf("b", "a"), media.items.map { it.id })
        assertFalse(media.loading)

        // A short page is the last one: scrolling on asks for nothing more.
        assertTrue(media.end)
        vm.loadChatMedia(-5, MediaKind.MEDIA, more = true)
        assertEquals(1, fake.chatMediaCalls.size)
    }

    @Test
    fun mediaTabFallsBackToTheLoadedMessagesWhenTheServerHasNone() {
        val chat = Chat(id = -5, title = "Г", lastMessageText = null, lastEventTime = 1)
        fake.history = listOf(photoMessage("p1", 3), Message(id = "t", cid = null, chatId = -5, senderId = 200, text = "текст", time = 4))
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        vm.loadChatMedia(-5, MediaKind.MEDIA)

        assertEquals(
            listOf("p1"),
            vm.state.value.infoMedia!!
                .items
                .map { it.id },
        )
    }

    @Test
    fun publicSearchShowsMoreWithTheMarkerAndFindsMessages() {
        val vm = loggedIn()
        fake.searchResultsToReturn = listOf(SearchResult(chatId = -9, title = "Канал", avatarUrl = null, isDialog = false))
        fake.searchMarker = 77L
        val hit = Message(id = "h1", cid = null, chatId = -9, senderId = 1, text = "отчёт готов", time = 3)
        fake.messageHits = listOf(MessageHit(hit, chat = null))

        vm.searchUsers("отчёт")
        assertTrue(fake.messageSearches.isEmpty(), "people search alone (the new-chat sheet) never searches messages")
        vm.searchAllMessages("отчёт")
        assertEquals(77L, vm.state.value.searchMarker)
        assertEquals(
            listOf(hit),
            vm.state.value.messageHits
                .map { it.message },
        )
        assertEquals(null to "отчёт", fake.messageSearches.single(), "the chat-list search looks through every chat")

        fake.searchResultsToReturn = listOf(SearchResult(chatId = -10, title = "Ещё канал", avatarUrl = null, isDialog = false))
        vm.searchMore()

        assertEquals(77L, fake.searchMarkersAsked.last())
        assertEquals(
            listOf(-9L, -10L),
            vm.state.value.searchResults
                .map { it.chatId },
        )
        assertNull(vm.state.value.searchMarker, "no marker back: nothing more to show")
    }

    @Test
    fun openingAMessageHitLoadsHistoryBackToItAndJumpsThere() {
        val chat = Chat(id = 42, title = "Диалог", lastMessageText = null, lastEventTime = 1)
        // 120 messages: the first page holds the newest 50, the hit is the 10th oldest.
        fake.history = (1..120).map { Message(id = "m$it", cid = null, chatId = 42, senderId = 200, text = "t$it", time = it.toLong()) }
        val vm = loggedIn(listOf(chat))

        vm.openMessageHit(MessageHit(fake.history[9], chat = null))

        assertTrue(
            vm.state.value.messages
                .any { it.id == "m10" },
            "older pages were loaded down to the hit",
        )
        assertEquals("m10", vm.state.value.jumpTo)
        assertEquals(
            (1..120).map { "m$it" },
            vm.state.value.messages
                .map { it.id },
            "history stays contiguous",
        )
    }

    @Test
    fun blockingShowsAtOnceAndUnblockingFromTheListRevertsWhenRefused() {
        val vm = loggedIn()
        val user = UserInfo(300L, "Спамер")
        fake.userToReturn = user
        vm.openUser(300L)

        vm.setBlocked(user, true)
        assertTrue(
            vm.state.value.viewingUser!!
                .blocked,
        )
        assertEquals(300L to true, fake.blockCalls.single())

        fake.blockedToReturn = listOf(user.copy(blocked = true))
        vm.loadBlockedUsers()
        fake.blockFailure = RuntimeException("contact.not.found")
        vm.setBlocked(user, false)

        assertEquals(
            listOf(300L),
            vm.state.value.blockedUsers
                ?.map { it.id },
            "refused: still on the list",
        )
        assertTrue(
            vm.state.value.viewingUser!!
                .blocked,
        )
        assertEquals("contact.not.found", vm.state.value.notice)
    }

    @Test
    fun clearingHistoryEmptiesTheChatAtOnceAndARefusalBringsItBack() {
        val chat = Chat(id = 42, title = "Диалог", lastMessageText = "привет", lastEventTime = 5)
        fake.history = listOf(Message(id = "m1", cid = null, chatId = 42, senderId = 200, text = "привет", time = 7))
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        fake.clearFailure = RuntimeException("chat.denied")
        vm.clearHistory(chat)
        assertEquals(42L to 7L, fake.clearCalls.single(), "cleared up to the newest message we know of")
        assertEquals(
            listOf("m1"),
            vm.state.value.messages
                .map { it.id },
            "refused: the messages come back",
        )
        assertEquals(
            "привет",
            vm.state.value.chats
                .single()
                .lastMessageText,
        )
        assertEquals("chat.denied", vm.state.value.notice)

        fake.clearFailure = null
        vm.clearHistory(chat)
        assertTrue(
            vm.state.value.messages
                .isEmpty(),
        )
        assertNull(
            vm.state.value.chats
                .single()
                .lastMessageText,
        )
    }

    @Test
    fun markUnreadBadgesTheRowAndARefusalTakesTheBadgeOff() {
        val chat = Chat(id = 42, title = "Диалог", lastMessageText = "a", lastEventTime = 9, lastMessageTime = 8)
        val vm = loggedIn(listOf(chat))

        vm.markUnread(chat)
        assertEquals(
            1,
            vm.state.value.chats
                .single()
                .unreadCount,
        )
        assertEquals(42L to 8L, fake.unreadMarks.single(), "the mark is the last message's time")

        vm.openChat(chat) // reading it clears the badge again
        fake.unreadFailure = RuntimeException("chat.not.found")
        vm.markUnread(
            vm.state.value.chats
                .single(),
        )

        assertEquals(
            0,
            vm.state.value.chats
                .single()
                .unreadCount,
        )
        assertEquals("chat.not.found", vm.state.value.notice)
    }

    @Test
    fun aCallbackButtonIsBusyUntilTheServerHasThePress() {
        val vm = loggedIn()
        val button = BotButton("Да", type = "CALLBACK", payload = "yes", callbackId = "cb1")
        fake.pressGate = CompletableDeferred()

        vm.pressBotButton(button)
        vm.pressBotButton(button) // a second tap while busy is ignored
        assertEquals(setOf(button), vm.state.value.pressingButtons)

        fake.pressGate!!.complete(Unit)
        assertTrue(
            vm.state.value.pressingButtons
                .isEmpty(),
        )
        assertEquals(listOf(button), fake.pressed)

        vm.pressBotButton(BotButton("Сайт", "https://a.b", type = "LINK", callbackId = "cb1"))
        assertEquals(1, fake.pressed.size, "a link button is opened, not sent")
    }

    @Test
    fun reactionsSheetGroupsUsersByEmojiAndNamesStrangersInOneBatch() {
        val vm = loggedIn()
        fake.reactionUsers = listOf(301L to "❤️", 302L to "👍", 303L to "❤️")
        fake.usersToReturn = listOf(UserInfo(301L, "Аня"), UserInfo(303L, "Боря"))
        val msg =
            Message(id = "m1", cid = null, chatId = 42, senderId = 100, text = "x", time = 1, reactions = listOf(Reaction("❤️", 2, false)))

        vm.openReactions(msg)

        assertEquals(mapOf("❤️" to listOf(301L, 303L), "👍" to listOf(302L)), vm.state.value.reactedBy)
        assertEquals("Аня", vm.readerInfo(301L).name)
        assertEquals(listOf(listOf(301L, 302L, 303L)), fake.userBatches.takeLast(1))
    }

    @Test
    fun votingShowsAtOnceAndARefusalTakesTheVoteBack() {
        val chat = Chat(id = -5, title = "Г", lastMessageText = null, lastEventTime = 1)
        val poll = Poll(1, "Обед?", settings = 4, answers = listOf(PollAnswer(1, "Да"), PollAnswer(2, "Нет")))
        fake.history = listOf(Message(id = "p", cid = null, chatId = -5, senderId = 200, text = "", time = 3, poll = poll))
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        fun live() =
            vm.state.value.messages
                .single()

        vm.vote(live(), setOf(2))
        assertEquals(listOf(false, true), live().poll!!.answers.map { it.mine })
        assertEquals(listOf(2), fake.votes.single())

        fake.voteFailure = RuntimeException("poll.denied")
        vm.vote(live(), live().poll!!.votesAfterTapping(2)!!)

        assertEquals(emptyList(), fake.votes.last(), "tapping our answer again retracts")
        assertEquals(listOf(false, true), live().poll!!.answers.map { it.mine }, "refused: the vote is back")
        assertEquals("poll.denied", vm.state.value.notice)
    }

    @Test
    fun aMultipleChoiceVoteGoesOutOnceAndTapsWhileItFliesAreIgnored() {
        val chat = Chat(id = -5, title = "Г", lastMessageText = null, lastEventTime = 1)
        val poll = Poll(1, "Что?", settings = 2, answers = listOf(PollAnswer(1, "А"), PollAnswer(2, "Б"), PollAnswer(3, "В")))
        fake.history = listOf(Message(id = "p", cid = null, chatId = -5, senderId = 200, text = "", time = 3, poll = poll))
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        val gate = CompletableDeferred<Unit>()
        fake.voteGate = gate

        vm.vote(
            vm.state.value.messages
                .single(),
            setOf(1, 3),
        )
        vm.vote(
            vm.state.value.messages
                .single(),
            setOf(2),
        ) // in flight: ignored

        assertEquals(listOf(listOf(1, 3)), fake.votes.map { it.sorted() })
        gate.complete(Unit)
        assertEquals(
            listOf(true, false, true),
            vm.state.value.messages
                .single()
                .poll!!
                .answers
                .map { it.mine },
        )
        // Voted, no revote: nothing more goes out.
        vm.vote(
            vm.state.value.messages
                .single(),
            setOf(2),
        )
        assertEquals(1, fake.votes.size)
    }

    @Test
    fun ownSendAndDeleteUpdateTheChatRowAtOnce() {
        val other = Chat(id = 7, title = "Другой", lastMessageText = "позже", lastEventTime = 50)
        val chat = Chat(id = 42, title = "Диалог", lastMessageText = "старое", lastEventTime = 10, lastMessageTime = 10)
        val old = Message(id = "m1", cid = null, chatId = 42, senderId = 200, text = "старое", time = 10)
        fake.history = listOf(old)
        val vm = loggedIn(listOf(other, chat))
        vm.openChat(chat)

        vm.sendMessage("новое")

        val row =
            vm.state.value.chats
                .first()
        assertEquals(42L, row.id, "the chat moves to the top")
        assertEquals("новое", row.lastMessageText)
        assertEquals(100L, row.lastSenderId)

        vm.deleteMessage(
            vm.state.value.messages
                .last(),
            forAll = true,
        )

        val after =
            vm.state.value.chats
                .first { it.id == 42L }
        assertEquals("старое", after.lastMessageText, "the row falls back to the message before")
        assertEquals(10L, after.lastMessageTime)
        assertEquals(10L, after.lastEventTime, "and so does its time")
        assertEquals(
            listOf(7L, 42L),
            vm.state.value.chats
                .map { it.id },
            "and its place in the list",
        )
    }

    @Test
    fun backFromTheProfileEditorOpenedInSettingsReturnsToSettings() {
        val vm = loggedIn()
        vm.selectTab(Tab.SETTINGS)
        fake.userToReturn = UserInfo(100L, "Тест", description = "о себе")

        vm.openEditProfile()
        assertEquals(
            "о себе",
            vm.state.value.viewingUser
                ?.description,
            "the bio is loaded, so saving won't wipe it",
        )
        vm.onBack()

        assertEquals(Screen.CHATS, vm.state.value.screen)
        assertEquals(Tab.SETTINGS, vm.state.value.tab)
        assertNull(vm.state.value.viewingUser)
    }

    @Test
    fun groupLastSendersAreResolvedInOneBatch() {
        fake.usersToReturn = listOf(UserInfo(301L, "Лора Ш"), UserInfo(302L, "Иван"))
        val vm =
            loggedIn(
                listOf(
                    Chat(id = -1, title = "Г1", lastMessageText = "a", lastEventTime = 2, lastSenderId = 301),
                    Chat(id = -2, title = "Г2", lastMessageText = "b", lastEventTime = 1, lastSenderId = 302),
                ),
            )

        assertEquals(listOf(listOf(301L, 302L)), fake.userBatches)
        assertEquals(
            "Лора Ш",
            vm.state.value.groupMembers[301L]
                ?.name,
        )
    }

    @Test
    fun editKeepsTheFormattingItCanAndMarksTheBubbleEdited() {
        val chat = Chat(id = 42, title = "Диалог", lastMessageText = null, lastEventTime = 1)
        val bold = TextSpan("STRONG", from = 0, length = 5)
        val original = Message(id = "m1", cid = null, chatId = 42, senderId = 100, text = "Hello wrld", time = 5, spans = listOf(bold))
        fake.history = listOf(original)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        vm.editMessage(original, "Hello world")

        val shown =
            vm.state.value.messages
                .single()
        assertTrue(shown.edited, "«изм.» shows at once, not after reopening the chat")
        assertEquals(listOf(bold), shown.spans)
        assertEquals(listOf(bold), fake.editedSpans.single())
    }

    @Test
    fun incomingMessageForOtherChatIsIgnored() {
        val chat = Chat(id = 42, title = "Диалог", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        fake.emitIncoming(
            Message(id = "x", cid = null, chatId = 999, senderId = 200, text = "другой чат", time = 1000),
        )
        assertTrue(
            vm.state.value.messages
                .none { it.id == "x" },
        )
    }

    @Test
    fun incomingMessageForBackgroundChatBumpsUnreadAndReorders() {
        val open = Chat(id = 42, title = "Открытый", lastMessageText = null, lastEventTime = 100)
        val other = Chat(id = 99, title = "Фоновый", lastMessageText = null, lastEventTime = 50)
        val vm = loggedIn(listOf(open, other))
        vm.openChat(open)

        fake.emitIncoming(
            Message(id = "b1", cid = null, chatId = 99, senderId = 200, text = "новое", time = 999),
        )

        val chats = vm.state.value.chats
        val bg = chats.first { it.id == 99L }
        assertEquals(1, bg.unreadCount)
        assertEquals("новое", bg.lastMessageText)
        assertEquals(99L, chats.first().id, "the chat with the newest message should sort first")
    }

    @Test
    fun incomingMessageForOpenChatDoesNotBumpUnread() {
        val chat = Chat(id = 42, title = "Открытый", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        fake.emitIncoming(
            Message(id = "m1", cid = null, chatId = 42, senderId = 200, text = "привет", time = 1000),
        )
        assertEquals(
            0,
            vm.state.value.chats
                .first { it.id == 42L }
                .unreadCount,
        )
    }

    @Test
    fun livePresencePushTogglesOnlineUsers() {
        val vm = loggedIn()
        fake.emitPresence(Presence(userId = 200, online = true))
        assertTrue(200L in vm.state.value.onlineUsers)
        fake.emitPresence(Presence(userId = 200, online = false))
        assertFalse(200L in vm.state.value.onlineUsers)
    }

    @Test
    fun replySendsReplyToIdThenClearsBanner() {
        val chat = Chat(id = 42, title = "Диалог", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        val target = Message(id = "orig", cid = null, chatId = 42, senderId = 200, text = "вопрос", time = 5)
        vm.startReply(target)
        assertEquals(
            "orig",
            vm.state.value.replyingTo
                ?.id,
        )

        vm.sendMessage("ответ")
        assertEquals("orig", fake.lastReplyToId)
        assertNull(vm.state.value.replyingTo, "reply banner should clear after sending")
    }

    @Test
    fun toggleReactionAddsOptimisticallyAndCallsClient() {
        val chat = Chat(id = 42, title = "Диалог", lastMessageText = null, lastEventTime = 1)
        fake.history = listOf(Message(id = "m1", cid = null, chatId = 42, senderId = 200, text = "hi", time = 5))
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        vm.toggleReaction(
            vm.state.value.messages
                .first(),
            "❤️",
        )

        val r =
            vm.state.value.messages
                .first { it.id == "m1" }
                .reactions
                .first()
        assertEquals("❤️", r.emoji)
        assertEquals(1, r.count)
        assertTrue(r.mine)
        assertEquals(Triple(42L, "m1", "❤️"), fake.reactionCalls.last())
    }

    @Test
    fun toggleReactionRemovesWhenAlreadyMine() {
        val chat = Chat(id = 42, title = "Диалог", lastMessageText = null, lastEventTime = 1)
        fake.history =
            listOf(
                Message(
                    id = "m1",
                    cid = null,
                    chatId = 42,
                    senderId = 200,
                    text = "hi",
                    time = 5,
                    reactions = listOf(Reaction("❤️", 1, mine = true)),
                ),
            )
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        vm.toggleReaction(
            vm.state.value.messages
                .first(),
            "❤️",
        )

        assertTrue(
            vm.state.value.messages
                .first { it.id == "m1" }
                .reactions
                .isEmpty(),
        )
        // Cleared locally; the server is told via the dedicated remove (emoji = null).
        assertEquals(Triple(42L, "m1", null), fake.reactionCalls.last())
    }

    @Test
    fun reactionPushUpdatesCountsAndPreservesMyReaction() {
        val chat = Chat(id = 42, title = "Диалог", lastMessageText = null, lastEventTime = 1)
        fake.history =
            listOf(
                Message(
                    id = "m1",
                    cid = null,
                    chatId = 42,
                    senderId = 200,
                    text = "hi",
                    time = 5,
                    reactions = listOf(Reaction("👍", 1, mine = true)),
                ),
            )
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        // Another party also reacts 👍 → count becomes 2; my reaction must stay highlighted.
        fake.emitReactionUpdate(ReactionUpdate(42, "m1", listOf(Reaction("👍", 2, mine = false))))

        val r =
            vm.state.value.messages
                .first { it.id == "m1" }
                .reactions
                .first()
        assertEquals(2, r.count)
        assertTrue(r.mine, "my own reaction flag must survive a count-only push")
    }

    @Test
    fun chatUpdatePushAddsNewChatToList() {
        val vm = loggedIn(emptyList())
        assertTrue(
            vm.state.value.chats
                .isEmpty(),
        )

        // e.g. another user starts a dialog, or we're added to a group.
        fake.emitChatUpdate(Chat(id = 77, title = "Новый чат", lastMessageText = "привет", lastEventTime = 9))

        val chats = vm.state.value.chats
        assertEquals(1, chats.size)
        assertEquals(77L, chats.first().id)
    }

    @Test
    fun chatUpdatePushReplacesExistingChat() {
        val chat = Chat(id = 5, title = "Чат", lastMessageText = "старое", lastEventTime = 1)
        val vm = loggedIn(listOf(chat))

        fake.emitChatUpdate(chat.copy(lastMessageText = "новое", lastEventTime = 50))

        assertEquals(1, vm.state.value.chats.size)
        assertEquals(
            "новое",
            vm.state.value.chats
                .first()
                .lastMessageText,
        )
    }

    @Test
    fun deleteCurrentChatRemovesItAndReturnsToList() {
        val chat = Chat(id = 42, title = "Диалог", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        vm.deleteCurrentChat()

        assertEquals(Screen.CHATS, vm.state.value.screen)
        assertTrue(
            vm.state.value.chats
                .none { it.id == 42L },
        )
        assertEquals(listOf(42L), fake.deletedChats)
        // Max can't delete a dialog for the other party, so we only delete our copy.
        assertEquals(false, fake.lastDeleteForAll)
    }

    @Test
    fun leaveCurrentGroupRemovesItAndReturnsToList() {
        val group = Chat(id = 7, title = "Группа", lastMessageText = null, lastEventTime = 1, isDialog = false)
        val vm = loggedIn(listOf(group))
        vm.openChat(group)

        vm.leaveCurrentGroup()

        assertEquals(Screen.CHATS, vm.state.value.screen)
        assertTrue(
            vm.state.value.chats
                .none { it.id == 7L },
        )
        assertEquals(listOf(7L), fake.leftGroups)
    }

    @Test
    fun readMarkFromOtherPartyFlipsOwnMessageToRead() {
        val chat = Chat(id = 42, title = "Диалог", lastMessageText = null, lastEventTime = 1)
        fake.history =
            listOf(
                Message(id = "mine", cid = 1, chatId = 42, senderId = 100, text = "моё", time = 500, status = MessageStatus.SENT),
            )
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        fake.emitReadMark(ReadMark(chatId = 42, userId = 200, mark = 1000))

        assertEquals(
            MessageStatus.READ,
            vm.state.value.messages
                .first { it.id == "mine" }
                .status,
        )
    }

    // --- "who read this message" (issue #50) ---

    private val groupChat = Chat(id = 7, title = "Группа", lastMessageText = null, lastEventTime = 1, isDialog = false)

    @Test
    fun readMarkPushRecordsWhoRead() {
        val chat = groupChat.copy(readMarks = mapOf(200L to 500L))
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        fake.emitReadMark(ReadMark(chatId = 7, userId = 300, mark = 900))

        // Both readers are remembered — the old code kept only the highest mark.
        assertEquals(
            mapOf(200L to 500L, 300L to 900L),
            vm.state.value.currentChat
                ?.readMarks,
        )
    }

    @Test
    fun readMarkNeverMovesBackwards() {
        val chat = groupChat.copy(readMarks = mapOf(200L to 900L))
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        // A late/duplicate push with an older mark must not un-read anything.
        fake.emitReadMark(ReadMark(chatId = 7, userId = 200, mark = 400))

        assertEquals(
            mapOf(200L to 900L),
            vm.state.value.currentChat
                ?.readMarks,
        )
    }

    @Test
    fun openReadByRefreshesMarksFromServer() {
        val chat = groupChat.copy(readMarks = mapOf(200L to 500L))
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        fake.readMarksToReturn = mapOf(200L to 500L, 300L to 900L)
        val message = Message(id = "m1", cid = 1, chatId = 7, senderId = 100, text = "привет", time = 700)

        vm.openReadBy(message)

        assertEquals("m1", vm.state.value.readingMessageId)
        assertEquals(listOf(7L), fake.readMarksRequested)
        // The fresh reply is merged in, so the newer reader appears.
        assertEquals(mapOf(200L to 500L, 300L to 900L), vm.state.value.readMarks)
        assertFalse(vm.state.value.readMarksLoading)
    }

    @Test
    fun closeReadByClearsTheSheet() {
        val vm = loggedIn(listOf(groupChat))
        vm.openChat(groupChat)
        vm.openReadBy(Message(id = "m1", cid = 1, chatId = 7, senderId = 100, text = "x", time = 700))

        vm.closeReadBy()

        assertNull(vm.state.value.readingMessageId)
        assertTrue(
            vm.state.value.readMarks
                .isEmpty(),
        )
    }

    @Test
    fun openReadBySurvivesAFailingRefresh() {
        val chat = groupChat.copy(readMarks = mapOf(200L to 500L))
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        // Server refuses (e.g. unsupported opcode): the seeded marks must still show.
        fake.readMarksToReturn = emptyMap()

        vm.openReadBy(Message(id = "m1", cid = 1, chatId = 7, senderId = 100, text = "x", time = 700))

        assertEquals(mapOf(200L to 500L), vm.state.value.readMarks)
        assertFalse(vm.state.value.readMarksLoading)
    }

    @Test
    fun sendMessageAppendsServerEcho() {
        val chat = Chat(id = 42, title = "Диалог", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        vm.sendMessage("  hello  ")

        assertEquals(listOf(42L to "hello"), fake.sentMessages)
        assertTrue(
            vm.state.value.messages
                .any { it.text == "hello" },
        )
    }

    @Test
    fun logoutClearsTokenAndReturnsToLogin() {
        val vm = loggedIn()
        vm.logout()
        assertEquals(Screen.LOGIN, vm.state.value.screen)
        assertNull(prefs.token)
        assertTrue(fake.disconnectCount > 0)
    }

    // --- Избранное, forwarding, bulk delete, settings, sessions ---

    private fun msg(
        id: String?,
        chatId: Long = 42,
        time: Long = 1,
    ) = Message(id = id, cid = null, chatId = chatId, senderId = 200, text = "t$id", time = time)

    @Test
    fun openSavedMessagesOpensTheExistingChatWithIdZero() {
        val saved = Chat(id = 0, title = "Мои заметки", lastMessageText = "x", lastEventTime = 5, isDialog = true)
        val vm = loggedIn(listOf(Chat(id = 42, title = "Аня", lastMessageText = null, lastEventTime = 1), saved))

        vm.openSavedMessages()

        assertEquals(Screen.CHAT, vm.state.value.screen)
        assertEquals(saved, vm.state.value.currentChat)
    }

    @Test
    fun openSavedMessagesSynthesisesTheDialogWhenThereIsNone() {
        val vm = loggedIn(listOf(Chat(id = 42, title = "Аня", lastMessageText = null, lastEventTime = 1)))

        vm.openSavedMessages()

        val chat = vm.state.value.currentChat
        assertEquals(Screen.CHAT, vm.state.value.screen)
        assertEquals(0L, chat?.id)
        assertEquals("Избранное", chat?.title)
        assertEquals(true, chat?.isDialog)
    }

    @Test
    fun startForwardSkipsUnsentMessagesAndKeepsTheRest() {
        val vm = loggedIn()

        vm.startForward(listOf(msg("a"), msg(null), msg("b")))

        assertEquals(
            listOf("a", "b"),
            vm.state.value.forwarding
                .map { it.id },
        )
        assertEquals(Screen.SHARE_PICK, vm.state.value.screen)
    }

    @Test
    fun startForwardOfOnlyUnsentMessagesDoesNothing() {
        val vm = loggedIn()

        vm.startForward(listOf(msg(null)))

        assertTrue(
            vm.state.value.forwarding
                .isEmpty(),
        )
        assertTrue(vm.state.value.screen != Screen.SHARE_PICK)
    }

    @Test
    fun pickShareTargetForwardsEveryMessageInTimeOrderAndClearsForwarding() {
        val target = Chat(id = 77, title = "Куда", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(target))
        // Picked out of order (multi-select can be tapped in any order).
        vm.startForward(listOf(msg("late", time = 30), msg("early", time = 10), msg("mid", time = 20)))

        vm.pickShareTarget(target)

        assertEquals(listOf("early", "mid", "late"), fake.forwarded.map { it.second })
        assertTrue(fake.forwarded.all { it.first == 77L && it.third == 42L })
        assertTrue(
            vm.state.value.forwarding
                .isEmpty(),
        )
        assertEquals(target, vm.state.value.currentChat)
        assertEquals(
            3,
            vm.state.value.messages
                .count { it.id?.startsWith("fwd-") == true },
        )
    }

    @Test
    fun deleteMessagesRemovesThemFromStateInOneRequest() {
        val chat = Chat(id = 42, title = "Диалог", lastMessageText = null, lastEventTime = 1)
        fake.history = listOf(msg("a"), msg("b"), msg("c"))
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        vm.deleteMessages(listOf(msg("a"), msg("c")))

        assertEquals(
            listOf("b"),
            vm.state.value.messages
                .map { it.id },
        )
        assertEquals(listOf(Triple(42L, listOf("a", "c"), false)), fake.deletedMessages, "one MSG_DELETE for the lot")
    }

    @Test
    fun aRefusedDeleteBringsTheMessagesBackWithANotice() {
        val chat = Chat(id = 42, title = "Диалог", lastMessageText = null, lastEventTime = 1)
        fake.history = listOf(msg("a", time = 1), msg("b", time = 2), msg("c", time = 3))
        fake.deleteFailure = RuntimeException("message.not.found")
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        vm.deleteMessages(listOf(fake.history[0], fake.history[2]))

        assertEquals(
            listOf("a", "b", "c"),
            vm.state.value.messages
                .map { it.id },
        )
        assertEquals("message.not.found", vm.state.value.notice)
        assertEquals(1, fake.deletedMessages.size)
    }

    @Test
    fun sendOnEnterUpdatesStateAndPrefs() {
        val vm = loggedIn()
        assertFalse(vm.state.value.sendOnEnter)

        vm.setSendOnEnter(true)

        assertTrue(vm.state.value.sendOnEnter)
        assertTrue(prefs.sendOnEnter)
    }

    @Test
    fun quickReactionUpdatesStateAndPrefsAndCanBeTurnedOff() {
        val vm = loggedIn()

        vm.setQuickReaction("🔥")
        assertEquals("🔥", vm.state.value.quickReaction)
        assertEquals("🔥", prefs.quickReaction)

        vm.setQuickReaction(null)
        assertNull(vm.state.value.quickReaction)
        assertNull(prefs.quickReaction)
    }

    @Test
    fun savedInputPreferencesAreRestoredOnStart() {
        prefs.sendOnEnter = true
        prefs.quickReaction = "👍"

        val s = loggedIn().state.value

        assertTrue(s.sendOnEnter)
        assertEquals("👍", s.quickReaction)
    }

    @Test
    fun toggleMuteAlsoUpdatesTheOpenGroupPage() {
        val group = Chat(id = -5, title = "Группа", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(group))
        vm.openGroup(group)

        vm.toggleMute(group)

        assertTrue(
            vm.state.value.viewingGroup
                ?.muted == true,
        )
        assertTrue(
            vm.state.value.chats
                .single()
                .muted,
        )
        assertEquals(setOf(-5L), fake.mutedChats)

        vm.toggleMute(vm.state.value.viewingGroup!!)
        assertFalse(
            vm.state.value.viewingGroup
                ?.muted == true,
        )
    }

    @Test
    fun toggleMuteLeavesADifferentOpenGroupPageAlone() {
        val a = Chat(id = -5, title = "A", lastMessageText = null, lastEventTime = 1)
        val b = Chat(id = -6, title = "B", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(a, b))
        vm.openGroup(a)

        vm.toggleMute(b)

        assertFalse(
            vm.state.value.viewingGroup
                ?.muted == true,
        )
    }

    @Test
    fun loadSessionsPutsTheClientsListInState() {
        val list = listOf(SessionInfo("MAX Android", "Pixel", "Москва", 5, current = true))
        fake.sessionsToReturn = list
        val vm = loggedIn()
        assertNull(vm.state.value.sessions)

        vm.loadSessions()

        assertEquals(list, vm.state.value.sessions)
    }

    @Test
    fun loadSessionsFailureGivesAnEmptyListAndANotice() {
        fake.sessionsFailure = RuntimeException("нет связи")
        val vm = loggedIn()

        vm.loadSessions()

        assertEquals(emptyList(), vm.state.value.sessions)
        assertEquals("нет связи", vm.state.value.notice)
    }

    @Test
    fun incomingMessageUpdatesTheChatRowsLastSenderAndTime() {
        val chat = Chat(id = 99, title = "Фоновый", lastMessageText = null, lastEventTime = 50)
        val vm = loggedIn(listOf(chat))

        fake.emitIncoming(Message(id = "n1", cid = null, chatId = 99, senderId = 321, text = "новое", time = 999))

        val row =
            vm.state.value.chats
                .single()
        assertEquals(321L, row.lastSenderId)
        assertEquals(999L, row.lastMessageTime)
    }

    // --- server folders ---

    private val allFolder = ChatFolder(ChatFolder.ALL_ID, "Все")
    private val work = ChatFolder("w", "Работа", include = listOf(7L))
    private val home = ChatFolder("h", "Дом")

    @Test
    fun foldersAreLoadedAfterSyncInTheServersOrder() {
        fake.foldersToReturn = FoldersUpdate(listOf(work, allFolder, home), listOf(ChatFolder.ALL_ID, "h", "w"))
        val vm = loggedIn()

        assertEquals(
            listOf(ChatFolder.ALL_ID, "h", "w"),
            vm.state.value.folders
                .map { it.id },
        )
    }

    @Test
    fun addingAChatSendsTheWholeFolderAndARefusalRevertsIt() {
        fake.foldersToReturn = FoldersUpdate(listOf(allFolder, work))
        val vm = loggedIn()

        vm.toggleChatInFolder("w", 9L)

        assertEquals(listOf(work.copy(include = listOf(7L, 9L))), fake.savedFolders)
        assertEquals(
            listOf(7L, 9L),
            vm.state.value.folders
                .single { it.id == "w" }
                .include,
        )

        fake.folderFailure = RuntimeException("folder.not.found")
        vm.toggleChatInFolder("w", 7L)

        assertEquals(listOf(9L), fake.savedFolders.last().include, "taking a chat out still sends the rest of the folder")
        assertEquals(
            listOf(7L, 9L),
            vm.state.value.folders
                .single { it.id == "w" }
                .include,
            "a refused change is undone",
        )
        assertEquals("folder.not.found", vm.state.value.notice)
    }

    @Test
    fun movingAFolderSendsEveryIdWithAllFirstAndNeverMovesAll() {
        fake.foldersToReturn = FoldersUpdate(listOf(allFolder, work, home))
        val vm = loggedIn()

        vm.moveFolder("w", -1) // would pass «Все»: nothing is sent
        vm.moveFolder("w", 1)

        assertEquals(listOf(listOf(ChatFolder.ALL_ID, "h", "w")), fake.folderOrders)
        assertEquals(
            listOf(ChatFolder.ALL_ID, "h", "w"),
            vm.state.value.folders
                .map { it.id },
        )
    }

    @Test
    fun reorderingSendsTheServersWholeOrderSystemFoldersIncluded() {
        // The server lists «Все» and «Каналы» in its order without folder objects for them.
        fake.foldersToReturn = FoldersUpdate(listOf(work, home), listOf(ChatFolder.ALL_ID, "w", ChatFolder.CHANNELS_ID, "h"))
        val vm = loggedIn()

        vm.moveFolder("w", -1) // the first shown folder: nothing above it
        vm.moveFolder("h", -1)

        assertEquals(listOf(listOf(ChatFolder.ALL_ID, "h", ChatFolder.CHANNELS_ID, "w")), fake.folderOrders)
        assertEquals(
            listOf("h", "w"),
            vm.state.value.folders
                .map { it.id },
        )

        fake.folderFailure = RuntimeException("нельзя")
        vm.moveFolder("w", -1)

        assertEquals(listOf(ChatFolder.ALL_ID, "w", ChatFolder.CHANNELS_ID, "h"), fake.folderOrders.last())
        assertEquals(listOf(ChatFolder.ALL_ID, "h", ChatFolder.CHANNELS_ID, "w"), vm.state.value.folderOrder, "a refusal puts it back")
        assertEquals(
            listOf("h", "w"),
            vm.state.value.folders
                .map { it.id },
        )
    }

    private fun AppViewModel.include(id: String) =
        state.value.folders
            .single { it.id == id }
            .include

    @Test
    fun folderSavesGoOneAtATimeAndARefusalRestoresTheStateBeforeIt() {
        fake.foldersToReturn = FoldersUpdate(listOf(allFolder, work))
        val vm = loggedIn()
        val first = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()
        fake.folderGates += listOf(first, second)

        vm.toggleChatInFolder("w", 9L)
        vm.toggleChatInFolder("w", 5L)

        assertEquals(1, fake.savedFolders.size, "the second write waits for the first")
        assertEquals(listOf(7L, 9L, 5L), vm.include("w"))

        first.complete(Unit)
        assertEquals(listOf(7L, 9L, 5L), fake.savedFolders.last().include)
        second.completeExceptionally(RuntimeException("нельзя"))

        assertEquals(listOf(7L, 9L), vm.include("w"), "back to what the first write left, not to before both")
        assertEquals("нельзя", vm.state.value.notice)
    }

    @Test
    fun twoRefusedFolderSavesEndWhereTheServerIs() {
        fake.foldersToReturn = FoldersUpdate(listOf(allFolder, work))
        val vm = loggedIn()
        val first = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()
        fake.folderGates += listOf(first, second)

        vm.toggleChatInFolder("w", 9L)
        vm.toggleChatInFolder("w", 5L)
        first.completeExceptionally(RuntimeException("нельзя"))

        assertEquals(listOf(7L, 9L, 5L), vm.include("w"), "the newer write still carries both changes")

        second.completeExceptionally(RuntimeException("нельзя"))
        assertEquals(listOf(7L), vm.include("w"))
    }

    @Test
    fun aRefusedDeleteBringsTheFolderBack() {
        fake.foldersToReturn = FoldersUpdate(listOf(allFolder, work))
        fake.folderFailure = RuntimeException("нельзя")
        val vm = loggedIn()

        vm.deleteFolder("w")

        assertEquals(listOf("w"), fake.deletedFolders)
        assertEquals(
            listOf(ChatFolder.ALL_ID, "w"),
            vm.state.value.folders
                .map { it.id },
        )
        assertEquals("нельзя", vm.state.value.notice)
    }

    @Test
    fun aCreatedFolderGetsAFreshIdAndItsChats() {
        val vm = loggedIn()

        vm.createFolder("  Новая ", listOf(5L))

        val sent = fake.savedFolders.single()
        assertEquals("Новая", sent.title)
        assertEquals(listOf(5L), sent.include)
        assertTrue(sent.id.length >= 16)
        assertEquals(listOf(sent), vm.state.value.folders)
    }

    // --- request budgets ---

    @Test
    fun aBlackListServerIgnoringFromIsAskedTwiceNotForever() {
        fake.blockedToReturn = (1L..40L).map { UserInfo(it, "U$it", blocked = true) }
        fake.blockedIgnoresFrom = true
        val vm = loggedIn()

        vm.loadBlockedUsers()

        assertEquals(listOf(0, 40), fake.blockedFroms, "the repeat brought nothing new, so it stopped")
        assertEquals(
            40,
            vm.state.value.blockedUsers
                ?.size,
        )
    }

    @Test
    fun theBlackListPagesByRawCountAndStopsAtTheCap() {
        fake.blockedToReturn = (1L..5000L).map { UserInfo(it, "U$it", blocked = true) }
        val vm = loggedIn()

        vm.loadBlockedUsers()

        assertEquals(AppViewModel.MAX_BLACKLIST_PAGES, fake.blockedFroms.size)
        assertEquals((0 until AppViewModel.MAX_BLACKLIST_PAGES).map { it * 40 }, fake.blockedFroms)
    }

    private fun media(
        id: Int,
        withLink: Boolean,
    ) = Message(
        id = "$id",
        cid = null,
        chatId = 42,
        senderId = 200,
        text = "",
        time = id.toLong(),
        linkPreview =
            if (withLink) {
                com.avenarius.app.model
                    .LinkPreview("https://a.b/$id", null, null, null)
            } else {
                null
            },
    )

    @Test
    fun mediaPagingAnchorsOnTheOldestReturnedMessageNotTheOldestShown() {
        val chat = Chat(id = 42, title = "Диалог", lastMessageText = null, lastEventTime = 1)
        fake.history = listOf(msg("1000"))
        // Every page: 60 messages below the anchor, only the newest carries a link.
        fake.chatMediaPage = { anchor ->
            val top = anchor.toInt()
            (top - 60 until top).map { media(it, withLink = it == top - 1) }
        }
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        vm.loadChatMedia(42, MediaKind.LINKS)
        vm.loadChatMedia(42, MediaKind.LINKS, more = true)

        assertEquals(listOf("1000", "940"), fake.chatMediaCalls.map { it[2] }, "the second page starts below everything returned")
        assertEquals(
            2,
            vm.state.value.infoMedia
                ?.items
                ?.size,
        )
    }

    @Test
    fun mediaPagingStopsOnAPageWithNothingNewAndAtTheCap() {
        val chat = Chat(id = 42, title = "Диалог", lastMessageText = null, lastEventTime = 1)
        fake.history = listOf(msg("100000"))
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        // A server that answers every anchor with the same page.
        fake.chatMediaPage = { _ -> (1..60).map { media(it, withLink = true) } }
        vm.loadChatMedia(42, MediaKind.LINKS)
        repeat(5) { vm.loadChatMedia(42, MediaKind.LINKS, more = true) }
        assertEquals(2, fake.chatMediaCalls.size, "the repeat brought nothing new")

        // A server with endless history: the cap ends it.
        vm.loadChatMedia(42, MediaKind.FILES) // another tab resets the visit
        fake.chatMediaCalls.clear()
        vm.loadChatMedia(42, MediaKind.LINKS)
        fake.chatMediaPage = { anchor -> (anchor.toInt() - 60 until anchor.toInt()).map { media(it, withLink = true) } }
        fake.chatMediaCalls.clear()
        repeat(40) { vm.loadChatMedia(42, MediaKind.LINKS, more = true) }
        assertTrue(fake.chatMediaCalls.size <= AppViewModel.MAX_MEDIA_PAGES, "at most ${AppViewModel.MAX_MEDIA_PAGES} pages")
    }

    @Test
    fun jumpingToAHitBlocksAParallelOlderPageAndKeepsHistoryOpen() {
        val chat = Chat(id = 42, title = "Г", lastMessageText = null, lastEventTime = 1)
        fake.history = (1..120).map { Message(id = "m$it", cid = null, chatId = 42, senderId = 200, text = "t$it", time = it.toLong()) }
        val vm = loggedIn(listOf(chat))
        var poked = false
        fake.historyHook = {
            // While the jump pages back, a scroll to the top asks for older history too.
            if (fake.historyCalls == 2 && !poked) {
                poked = true
                vm.loadOlder()
            }
        }

        vm.openChat(chat, jumpTo = fake.history[30])

        assertTrue(poked)
        assertEquals(2, fake.historyCalls, "the opening page and one page back; the parallel ask was refused")
        assertTrue(
            vm.state.value.messages
                .any { it.id == "m31" },
        )
        assertFalse(vm.state.value.noMoreOlder, "older history stays loadable")
        assertFalse(vm.state.value.loadingOlder)
    }

    // --- chat pins (kept on the device, also written to the server) ---

    private fun row(id: Long) = Chat(id = id, title = "Ч$id", lastMessageText = null, lastEventTime = id)

    private fun AppViewModel.favIndex(id: Long) =
        state.value.chats
            .single { it.id == id }
            .favIndex

    @Test
    fun pinningWritesTheDeviceAndTheServer() {
        val vm = loggedIn(listOf(row(1), row(2)))

        vm.togglePin(2)
        val pinned = vm.favIndex(2)
        assertTrue(pinned > 0)
        assertEquals(listOf(mapOf(2L to pinned)), fake.favWrites)
        assertEquals(mapOf(2L to pinned), prefs.pinnedAt)

        fake.favFailure = RuntimeException("config.denied")
        vm.togglePin(2)
        assertEquals(mapOf(2L to 0L), fake.favWrites.last(), "0 unpins")
        assertEquals(0L, vm.favIndex(2), "the device's unpin holds although the server refused")
        assertEquals(emptySet(), prefs.pinnedChats)
        assertNull(vm.state.value.notice)
    }

    @Test
    fun aRefusedServerPinStaysPinnedWithoutANotice() {
        fake.favFailure = RuntimeException("config.denied")
        val vm = loggedIn(listOf(row(1)))

        vm.togglePin(1)

        assertTrue(vm.favIndex(1) > 0)
        assertEquals(setOf(1L), prefs.pinnedChats)
        assertNull(vm.state.value.notice)
    }

    @Test
    fun backFromAChatOpenedByTheInfoPagesFindReturnsToThePage() {
        val group = row(1).copy(isDialog = false)
        val vm = loggedIn(listOf(group))

        // From the list: the info page, «Найти», then back.
        vm.openGroup(group)
        vm.searchInChat(group)
        assertEquals(Screen.CHAT, vm.state.value.screen)
        vm.onBack()
        assertEquals(Screen.GROUP, vm.state.value.screen)
        assertNull(vm.state.value.currentChat, "the chat «Найти» opened is closed again")
        vm.onBack()
        assertEquals(Screen.CHATS, vm.state.value.screen)

        // From the open chat: chat, info page, «Найти», back to the page, back to the chat.
        vm.openChat(group)
        vm.openGroup(group)
        vm.searchInChat(group)
        vm.onBack()
        assertEquals(Screen.GROUP, vm.state.value.screen)
        vm.onBack()
        assertEquals(Screen.CHAT, vm.state.value.screen)
        vm.onBack()
        assertEquals(Screen.CHATS, vm.state.value.screen)
    }

    @Test
    fun theRefreshFlagEndsOnEveryPath() {
        val vm = loggedIn(listOf(row(1)))
        vm.refresh()
        assertFalse(vm.state.value.refreshing, "after a successful refresh")

        fake.syncFailure = RuntimeException("нет сети")
        vm.refresh()
        assertFalse(vm.state.value.refreshing, "after a failed refresh")

        prefs.token = null
        vm.refresh()
        assertFalse(vm.state.value.refreshing, "without a session")
    }

    @Test
    fun aPinTheServerDoesNotKeepSurvivesARefresh() {
        val vm = loggedIn(listOf(row(1), row(2)))
        vm.togglePin(1)
        val pinned = vm.favIndex(1)

        // As measured: the write is accepted, yet the next sync reports favIndex 0.
        vm.refresh()

        assertEquals(pinned, vm.favIndex(1))
        assertEquals(0L, vm.favIndex(2))
    }

    @Test
    fun theServersPinLimitIsKeptWithoutAsking() {
        fake.pinLimit = 1
        val vm = loggedIn(listOf(row(1).copy(favIndex = 5), row(2)))

        vm.togglePin(2)

        assertTrue(fake.favWrites.isEmpty())
        assertEquals(0L, vm.favIndex(2))
        assertEquals("Закрепить можно не больше 1 чата", vm.state.value.notice)
    }

    @Test
    fun devicePinsCountAsPinnedAndAreNeverCleared() {
        prefs.pinnedChats = setOf(1L, 3L, 99L)
        val vm = loggedIn(listOf(row(1), row(2), row(3)))

        assertEquals(
            setOf(1L, 3L),
            vm.state.value.chats
                .filter { it.favIndex > 0 }
                .map { it.id }
                .toSet(),
        )
        vm.refresh()
        assertEquals(setOf(1L, 3L, 99L), prefs.pinnedChats, "a chat not loaded yet keeps its pin too")
        assertTrue(fake.favWrites.isEmpty(), "nothing is moved anywhere on its own")
    }

    @Test
    fun theLatestPinIsOnTopWhicheverSideHoldsIt() {
        prefs.pinnedAt = mapOf(1L to 100L, 2L to 300L, 4L to 50L)
        val vm = loggedIn(listOf(row(1), row(2), row(3).copy(favIndex = 200), row(4).copy(favIndex = 400)))

        assertEquals(
            listOf(4L, 2L, 3L, 1L),
            vm.state.value.chats
                .sortedByDescending { it.favIndex }
                .map { it.id },
        )
    }

    // --- stickers ---

    @Test
    fun theStickerShelfLoadsOnceAndASetsStickersOnceEach() {
        fake.shelf = StickerShelf(listOf(StickerSet(10, "Коты", null, listOf(1, 2, 3))), recent = listOf(2))
        val vm = loggedIn()

        vm.loadStickerShelf()
        vm.loadStickerShelf()
        vm.loadStickers(listOf(2))
        vm.loadStickers(listOf(1, 2, 3))
        vm.loadStickers(listOf(1, 2, 3))

        assertEquals(1, fake.shelfCalls)
        assertEquals(listOf(listOf(2L), listOf(1L, 3L)), fake.stickerAsks, "only stickers not asked for before")
        assertEquals(setOf(1L, 2L, 3L), vm.state.value.stickers.keys)
    }

    @Test
    fun aStickerIsSentAsItsIdAndBecomesTheFirstRecent() {
        val chat = Chat(id = 42, title = "Диалог", lastMessageText = null, lastEventTime = 1)
        fake.shelf = StickerShelf(emptyList(), recent = listOf(5, 6))
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)
        vm.loadStickerShelf()

        vm.sendSticker(Sticker(6, "https://s/6"))

        assertEquals(listOf(OutAttach.Sticker(6)), fake.lastAttaches)
        assertEquals(listOf(42L to ""), fake.sentMessages)
        assertEquals(
            listOf(6L, 5L),
            vm.state.value.stickerShelf
                ?.recent,
        )
    }

    // --- polls ---

    @Test
    fun aNewPollGoesOutAsOnePollAttachWithBlankAnswersDropped() {
        val chat = Chat(id = -5, title = "Г", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        vm.sendPoll("  Обед? ", listOf("Да", " ", "Нет "), settings = 4)
        vm.sendPoll("Один ответ", listOf("Да", ""), settings = 0)

        assertEquals(listOf(OutAttach.Poll("Обед?", listOf("Да", "Нет"), 4)), fake.lastAttaches)
        assertEquals(1, fake.sentMessages.size, "fewer than two answers sends nothing")
    }

    // --- group / channel editing ---

    @Test
    fun editingAGroupSendsOnlyWhatChangedAndARefusalRevertsIt() {
        val group =
            Chat(
                id = -5,
                title = "Старое",
                lastMessageText = null,
                lastEventTime = 1,
                description = "о нас",
                options = setOf("ALL_CAN_PIN_MESSAGE"),
            )
        val vm = loggedIn(listOf(group))
        vm.openGroup(group)

        vm.editChat(group, "Новое", "о нас", null, mapOf("ALL_CAN_PIN_MESSAGE" to true, "ONLY_ADMIN_CAN_ADD_MEMBER" to true))

        assertEquals(FakeMaxClient.ChatEdit(-5, "Новое", null, null, mapOf("ONLY_ADMIN_CAN_ADD_MEMBER" to true)), fake.chatEdits.single())
        assertEquals(
            "Новое",
            vm.state.value.chats
                .single()
                .title,
        )
        assertEquals(
            "Новое",
            vm.state.value.viewingGroup
                ?.title,
        )

        fake.chatEditFailure = RuntimeException("chat.denied")
        val shown = vm.state.value.viewingGroup!!
        vm.editChat(shown, "Третье", "", null, emptyMap())

        assertEquals("", fake.chatEdits.last().description, "a cleared description is sent as empty")
        assertEquals(
            "Новое",
            vm.state.value.viewingGroup
                ?.title,
            "refused: back to what it was",
        )
        assertEquals("chat.denied", vm.state.value.notice)
    }

    @Test
    fun aNewChannelGetsItsDescriptionRightAfter() {
        val vm = loggedIn()

        vm.createChannel(" Новости ", "Всё о нас", null)

        assertEquals(listOf("Новости"), fake.createdChannels)
        assertEquals(FakeMaxClient.ChatEdit(-77, null, "Всё о нас", null, emptyMap()), fake.chatEdits.single())
    }

    // --- contacts ---

    @Test
    fun renamingAContactShowsEverywhereAndSendsAnEmptyLastNameAsNull() {
        val anna = UserInfo(7, "Анна Петрова", firstName = "Анна", lastName = "Петрова")
        fake.contactsList = listOf(anna)
        fake.contacts = mapOf(7L to anna.name)
        fake.userToReturn = anna
        val dialog = Chat(id = 100L xor 7L, title = anna.name, lastMessageText = null, lastEventTime = 1, isDialog = true)
        val vm = loggedIn(listOf(dialog))
        vm.openUser(7)

        vm.renameContact(anna, " Аня ", "  ")

        assertEquals(Triple(7L, "Аня", null), fake.renames.single())
        assertEquals(
            "Аня",
            vm.state.value.viewingUser
                ?.name,
        )
        assertEquals("Аня", vm.state.value.contacts[7L])
        assertEquals(
            "Аня",
            vm.state.value.chats
                .single()
                .title,
        )

        fake.contactFailure = RuntimeException("contact.not.found")
        vm.renameContact(vm.state.value.viewingUser!!, "Нюра", "")
        assertEquals(
            "Аня",
            vm.state.value.viewingUser
                ?.name,
            "refused: the name is back",
        )
        assertEquals("contact.not.found", vm.state.value.notice)
    }

    @Test
    fun aRenameShowsInTheOpenDialogsHeaderAtOnce() {
        val anna = UserInfo(7, "Анна", firstName = "Анна")
        fake.contactsList = listOf(anna)
        fake.userToReturn = anna
        val dialog = Chat(id = 100L xor 7L, title = anna.name, lastMessageText = null, lastEventTime = 1, isDialog = true)
        val vm = loggedIn(listOf(dialog))
        vm.openChat(dialog)
        vm.openUser(7)

        vm.renameContact(anna, "Аня", "")
        vm.closeUser()

        assertEquals(Screen.CHAT, vm.state.value.screen)
        assertEquals(
            "Аня",
            vm.state.value.currentChat
                ?.title,
            "straight back from their page, the header has the new name",
        )
    }

    @Test
    fun aPinTheServerHoldsReadsBackTheSameOnRefreshAsOnTheFirstSync() {
        val vm = loggedIn(listOf(row(1), row(2).copy(favIndex = 5, muted = true)))
        assertEquals(
            5L,
            vm.state.value.chats
                .single { it.id == 2L }
                .favIndex,
        )

        vm.togglePin(1)
        val pinned = fake.favWrites.single().getValue(1L)
        // The server now holds both pins and the mute.
        fake.chats = listOf(row(1).copy(favIndex = pinned), row(2).copy(favIndex = 5, muted = true))
        vm.refresh()

        val chats =
            vm.state.value.chats
                .associateBy { it.id }
        assertEquals(pinned, chats.getValue(1L).favIndex, "the pin survives a refresh")
        assertEquals(5L, chats.getValue(2L).favIndex)
        assertTrue(chats.getValue(2L).muted)
    }

    @Test
    fun aRefusedContactRemovalPutsThemBack() {
        val anna = UserInfo(7, "Анна")
        fake.contactsList = listOf(anna)
        fake.contactFailure = RuntimeException("нельзя")
        val vm = loggedIn()

        vm.removeContact(anna)

        assertEquals(listOf(7L), fake.removedContacts)
        assertEquals(listOf(anna), vm.state.value.contactsList)
        assertEquals("нельзя", vm.state.value.notice)
    }

    // --- sessions ---

    @Test
    fun endingOtherSessionsStoresTheNewTokenAndTheNextSyncUsesIt() {
        val vm = loggedIn()
        assertEquals(listOf("tok"), fake.syncTokens)

        vm.closeOtherSessions()

        assertEquals(1, fake.closeSessionsCalls)
        assertEquals("new-token", prefs.token, "the reply's token replaces the stored one")
        assertEquals("Другие сеансы завершены", vm.state.value.notice)
        vm.refresh()
        assertEquals("new-token", fake.syncTokens.last(), "and the next sync signs in with it")
    }

    @Test
    fun aRefusedSessionCloseKeepsTheTokenAndSays() {
        fake.closeSessionsFailure = RuntimeException("session.denied")
        val vm = loggedIn()

        vm.closeOtherSessions()

        assertEquals("tok", prefs.token)
        assertEquals("session.denied", vm.state.value.notice)
    }

    @Test
    fun aReplyWithoutATokenLeavesTheStoredOneAndDoesNotClaimSuccess() {
        // Also what the client returns when the reply is lost after sending.
        fake.closeSessionsToken = null
        val vm = loggedIn()

        vm.closeOtherSessions()

        assertEquals("tok", prefs.token)
        val notice =
            vm.state.value.notice
                .orEmpty()
        assertTrue("войти заново" in notice, notice)
        assertFalse("Другие сеансы завершены" == notice)
    }

    // --- composer formatting ---

    @Test
    fun formattingGoesOutWithTheMessageAndFollowsTheTrim() {
        val chat = Chat(id = 42, title = "Диалог", lastMessageText = null, lastEventTime = 1)
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        // "  важно и срочно  ": «важно» bold (from 2), «срочно» italic (from 10).
        vm.sendMessage("  важно и срочно  ", listOf(TextSpan("STRONG", 2, 5), TextSpan("EMPHASIZED", 10, 6)))

        assertEquals(listOf(42L to "важно и срочно"), fake.sentMessages)
        assertEquals(listOf(TextSpan("STRONG", 0, 5), TextSpan("EMPHASIZED", 8, 6)), fake.lastSpans)
    }

    @Test
    fun theChatBackgroundIsKeptAndOutlivesALogout() {
        val vm = loggedIn()

        vm.setWallpaper(3)
        vm.logout()

        assertEquals(3, prefs.chatWallpaper)
        assertEquals(3, vm.state.value.wallpaper)
        assertEquals(3, viewModel().state.value.wallpaper, "a fresh start reads it back")
    }

    // --- refusals of the older requests ---

    @Test
    fun aRefusedMuteComesBackWithANotice() {
        val chat = Chat(id = 42, title = "Г", lastMessageText = null, lastEventTime = 1)
        fake.muteFailure = RuntimeException("chat.not.found")
        val vm = loggedIn(listOf(chat))

        vm.toggleMute(chat)

        assertFalse(
            vm.state.value.chats
                .single()
                .muted,
        )
        assertEquals("chat.not.found", vm.state.value.notice)
    }

    @Test
    fun aRefusedReactionIsTakenBack() {
        val chat = Chat(id = 42, title = "Г", lastMessageText = null, lastEventTime = 1)
        fake.history =
            listOf(
                Message(
                    id = "m",
                    cid = null,
                    chatId = 42,
                    senderId = 200,
                    text = "a",
                    time = 1,
                    reactions = listOf(Reaction("👍", 2, mine = false)),
                ),
            )
        fake.reactionFailure = RuntimeException("reaction.denied")
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        vm.toggleReaction(
            vm.state.value.messages
                .single(),
            "❤️",
        )

        assertEquals(
            listOf(Reaction("👍", 2, mine = false)),
            vm.state.value.messages
                .single()
                .reactions,
        )
        assertEquals("reaction.denied", vm.state.value.notice)
    }

    @Test
    fun aRefusedLeaveKeepsTheChatAndSays() {
        val chat = Chat(id = -5, title = "Г", lastMessageText = null, lastEventTime = 1)
        fake.leaveFailure = RuntimeException("chat.owner.leave")
        val vm = loggedIn(listOf(chat))
        vm.openChat(chat)

        vm.leaveGroup(chat)

        assertEquals(Screen.CHAT, vm.state.value.screen)
        assertEquals(
            listOf(chat.id),
            vm.state.value.chats
                .map { it.id },
        )
        assertEquals("chat.owner.leave", vm.state.value.notice)
    }
}
