package com.avenarius.app.ui

import com.avenarius.app.data.AppStorage
import com.avenarius.app.data.Prefs
import com.avenarius.app.model.Account
import com.avenarius.app.model.Chat
import com.avenarius.app.model.Message
import com.avenarius.app.model.MessageStatus
import com.avenarius.app.model.SearchResult
import com.avenarius.app.model.UserInfo
import com.avenarius.app.net.CodeResult
import com.avenarius.app.net.FoundUser
import com.avenarius.app.net.MaxApi
import com.avenarius.app.net.ReadMark
import com.avenarius.app.net.SyncResult
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

/** In-memory key/value store so [Prefs] works without a real platform. */
private class FakeStorage : AppStorage {
    private val map = mutableMapOf<String, String>()

    override fun getString(key: String): String? = map[key]

    override fun putString(
        key: String,
        value: String?,
    ) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

/**
 * A scriptable [MaxApi] for driving [AppViewModel] without a network. Server
 * pushes are simulated by emitting into the public shared flows; outgoing calls
 * are recorded so tests can assert on them.
 */
private class FakeMaxClient : MaxApi {
    private val _incoming = MutableSharedFlow<Message>(extraBufferCapacity = 16)
    private val _readMarks = MutableSharedFlow<ReadMark>(extraBufferCapacity = 16)
    private val _drops = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    override val incoming: SharedFlow<Message> get() = _incoming
    override val readMarks: SharedFlow<ReadMark> get() = _readMarks
    override val drops: SharedFlow<Unit> get() = _drops
    override var isConnected: Boolean = false
        private set

    // --- scriptable responses ---
    var account = Account(userId = 100L, firstName = "Тест")
    var chats: List<Chat> = emptyList()
    var contacts: Map<Long, String> = emptyMap()
    var contactsList: List<UserInfo> = emptyList()
    var refreshedToken: String? = null
    var codeResult: CodeResult = CodeResult.Success("login-token")
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

    override suspend fun sync(token: String): SyncResult = SyncResult(account, chats, contacts, contactsList, refreshedToken)

    override suspend fun fetchHistory(
        chatId: Long,
        fromTime: Long,
        count: Int,
    ): List<Message> = history

    override suspend fun sendMessage(
        chatId: Long,
        text: String,
        cid: Long,
    ): Message? {
        sentMessages += chatId to text
        return Message(id = "srv-$cid", cid = cid, chatId = chatId, senderId = account.userId, text = text, time = cid)
    }

    override suspend fun markRead(
        chatId: Long,
        messageId: String,
        mark: Long,
    ) {
        markReadCalls += Triple(chatId, messageId, mark)
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
        prefs = Prefs(FakeStorage())
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
