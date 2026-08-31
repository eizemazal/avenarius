package com.avenarius.app.ui

import com.avenarius.app.data.AppCache
import com.avenarius.app.data.CachedSession
import com.avenarius.app.data.InMemoryStorage
import com.avenarius.app.data.Prefs
import com.avenarius.app.model.Account
import com.avenarius.app.model.Chat
import com.avenarius.app.model.FileAttach
import com.avenarius.app.model.MediaAttach
import com.avenarius.app.model.MediaContent
import com.avenarius.app.model.MediaType
import com.avenarius.app.model.Message
import com.avenarius.app.model.MessageStatus
import com.avenarius.app.model.OutAttach
import com.avenarius.app.model.PickedKind
import com.avenarius.app.model.PickedMedia
import com.avenarius.app.model.Reaction
import com.avenarius.app.model.SearchResult
import com.avenarius.app.model.UploadState
import com.avenarius.app.model.UserInfo
import com.avenarius.app.net.CodeResult
import com.avenarius.app.net.FoundUser
import com.avenarius.app.net.MaxApi
import com.avenarius.app.net.MessageDeletion
import com.avenarius.app.net.Presence
import com.avenarius.app.net.ReactionUpdate
import com.avenarius.app.net.ReadMark
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

    /** When set, [sync] throws it — lets a test hold the session unestablished. */
    var syncFailure: Throwable? = null
    var history: List<Message> = emptyList()
    var userToReturn: UserInfo? = null
    var searchResultsToReturn: List<SearchResult> = emptyList()

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

    override suspend fun sync(token: String): SyncResult {
        syncFailure?.let { throw it }
        return SyncResult(account, chats, contacts, contactsList, online, refreshedToken)
    }

    override suspend fun fetchHistory(
        chatId: Long,
        fromTime: Long,
        count: Int,
    ): List<Message> = history

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

    override suspend fun sendMessage(
        chatId: Long,
        text: String,
        cid: Long,
        replyToId: String?,
        attaches: List<OutAttach>,
    ): Message? {
        sendGate?.await()
        sendFailure?.let { throw it }
        if (attaches.size > maxAttachesAccepted) {
            error("errors.send-message.attachment.max-size-reached")
        }
        if (attaches.isNotEmpty()) sentAttachCounts += attaches.size
        sentMessages += chatId to text
        lastReplyToId = replyToId
        lastAttaches = attaches
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
    ): Message? = Message(id = "fwd-$cid", cid = cid, chatId = toChatId, senderId = account.userId, text = "", time = cid)

    val editedMessages = mutableListOf<Triple<Long, String, String>>()
    val deletedMessages = mutableListOf<Triple<Long, List<String>, Boolean>>()

    override suspend fun editMessage(
        chatId: Long,
        messageId: String,
        text: String,
    ) {
        editedMessages += Triple(chatId, messageId, text)
    }

    override suspend fun deleteMessages(
        chatId: Long,
        messageIds: List<String>,
        forAll: Boolean,
    ) {
        deletedMessages += Triple(chatId, messageIds, forAll)
    }

    override suspend fun uploadVideo(
        content: MediaContent,
        fileName: String,
        mime: String,
        onProgress: ((Float) -> Unit)?,
    ): OutAttach.Video {
        record(content, fileName, onProgress)
        return OutAttach.Video(videoId = 1L, token = "fake-token")
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
    }

    override suspend fun deleteChat(
        chatId: Long,
        lastEventTime: Long,
        forAll: Boolean,
    ) {
        deletedChats += chatId
        lastDeleteForAll = forAll
    }

    override suspend fun leaveGroup(chatId: Long) {
        leftGroups += chatId
    }

    override suspend fun approveQrLogin(qrLink: String) {
        approvedQrLinks += qrLink
    }

    override suspend fun getChatMembers(chatId: Long): List<UserInfo> = emptyList()

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

    override suspend fun searchChats(query: String): List<SearchResult> = searchResultsToReturn

    override suspend fun fetchContactName(userId: Long): String? = null

    override suspend fun getVideoUrl(
        chatId: Long,
        messageId: Long,
        videoId: Long,
    ): String? = null

    /** URL handed back for a file attachment; null means "no link". */
    var fileUrl: String? = null

    val fileUrlRequests = mutableListOf<Long>()

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

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private lateinit var fake: FakeMaxClient
    private lateinit var prefs: Prefs

    @BeforeTest
    fun setUp() {
        // viewModelScope runs on Dispatchers.Main; an unconfined test dispatcher
        // runs launched work eagerly so each call's effects are visible at once.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fake = FakeMaxClient()
        prefs = Prefs(InMemoryStorage())
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AppViewModel(prefs, fake)

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
        assertTrue(fake.markReadCalls.any { it.first == 42L && it.second == "m1" })
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
}
