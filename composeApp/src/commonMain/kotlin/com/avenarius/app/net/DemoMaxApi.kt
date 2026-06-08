package com.avenarius.app.net

import com.avenarius.app.model.Account
import com.avenarius.app.model.Chat
import com.avenarius.app.model.Message
import com.avenarius.app.model.MessageStatus
import com.avenarius.app.model.OutAttach
import com.avenarius.app.model.SearchResult
import com.avenarius.app.model.UserInfo
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * A fully offline [MaxApi] backed by canned data, used for the Google Play review
 * "demo account" (entered via the magic phone/code at login). It never opens a
 * network connection — every call returns local sample data — so reviewers can
 * browse the chat list, conversations, profile and settings without a real account.
 */
class DemoMaxApi : MaxApi {
    override val incoming = MutableSharedFlow<Message>()
    override val readMarks = MutableSharedFlow<ReadMark>()
    override val presence = MutableSharedFlow<Presence>()
    override val reactionUpdates = MutableSharedFlow<ReactionUpdate>()
    override val chatUpdates = MutableSharedFlow<Chat>()
    override val drops = MutableSharedFlow<Unit>()
    override val isConnected = true

    private val me = Account(userId = ME, firstName = "Демо", lastName = "Пользователь")

    private val contactsList =
        listOf(
            UserInfo(id = ALICE, name = "Алиса Демидова", description = "Демонстрационный контакт"),
            UserInfo(id = BORIS, name = "Борис Петров"),
        )
    private val contactsMap = contactsList.associate { it.id to it.name }

    private val chats =
        listOf(
            Chat(
                id = DIALOG_ID,
                title = "Алиса Демидова",
                lastMessageText = "Увидимся завтра!",
                lastEventTime = BASE + 5,
                isDialog = true,
            ),
            Chat(
                id = GROUP_ID,
                title = "Команда проекта",
                lastMessageText = "Борис: Готово ✅",
                lastEventTime = BASE + 3,
                isDialog = false,
            ),
        )

    private val history =
        mapOf(
            DIALOG_ID to
                listOf(
                    msg("d1", DIALOG_ID, ALICE, "Привет! Это демо-режим Авенариуса.", BASE),
                    msg("d2", DIALOG_ID, ME, "Здорово, всё работает офлайн.", BASE + 1, MessageStatus.READ),
                    msg("d3", DIALOG_ID, ALICE, "Увидимся завтра!", BASE + 5),
                ),
            GROUP_ID to
                listOf(
                    msg("g1", GROUP_ID, ALICE, "Кто закончил задачу?", BASE),
                    msg("g2", GROUP_ID, BORIS, "Готово ✅", BASE + 3),
                    msg("g3", GROUP_ID, ME, "Отлично, спасибо!", BASE + 4, MessageStatus.READ),
                ),
        )

    private fun msg(
        id: String,
        chatId: Long,
        sender: Long,
        text: String,
        time: Long,
        status: MessageStatus = MessageStatus.UNKNOWN,
    ) = Message(id = id, cid = null, chatId = chatId, senderId = sender, text = text, time = time, status = status)

    override suspend fun connect(
        deviceId: String,
        mtInstance: String,
    ) = Unit

    override fun disconnect() = Unit

    override suspend fun startAuth(phone: String): Int = 5

    override suspend fun checkCode(code: String): CodeResult = CodeResult.Success("demo")

    override suspend fun register(
        firstName: String,
        lastName: String?,
    ): String = "demo"

    override suspend fun checkPassword(
        password: String,
        trackId: String,
    ): String = "demo"

    override suspend fun sync(token: String): SyncResult =
        SyncResult(
            account = me,
            chats = chats,
            contacts = contactsMap,
            contactsList = contactsList,
            online = setOf(ALICE),
            refreshedToken = null,
        )

    override suspend fun fetchHistory(
        chatId: Long,
        fromTime: Long,
        count: Int,
    ): List<Message> = history[chatId] ?: emptyList()

    override suspend fun sendMessage(
        chatId: Long,
        text: String,
        cid: Long,
        replyToId: String?,
        attaches: List<OutAttach>,
    ): Message = Message(id = "demo-$cid", cid = cid, chatId = chatId, senderId = ME, text = text, time = cid, status = MessageStatus.SENT)

    override suspend fun uploadPhoto(
        bytes: ByteArray,
        fileName: String,
        mime: String,
        profile: Boolean,
    ): OutAttach.Photo = OutAttach.Photo("demo")

    override suspend fun uploadVideo(
        bytes: ByteArray,
        fileName: String,
        mime: String,
    ): OutAttach.Video = OutAttach.Video(0L, "demo")

    override suspend fun uploadFile(
        bytes: ByteArray,
        fileName: String,
        mime: String,
    ): OutAttach.File = OutAttach.File(0L)

    override suspend fun forwardMessage(
        toChatId: Long,
        messageId: String,
        fromChatId: Long,
        cid: Long,
    ): Message = Message(id = "demo-fwd-$cid", cid = cid, chatId = toChatId, senderId = ME, text = "", time = cid)

    override suspend fun updateProfile(
        firstName: String,
        lastName: String?,
        description: String?,
        photoToken: String?,
    ) = Unit

    override suspend fun markRead(
        chatId: Long,
        messageId: String,
        mark: Long,
    ) = Unit

    override suspend fun reportHostReachability() = Unit

    override suspend fun setReaction(
        chatId: Long,
        messageId: String,
        emoji: String?,
    ) = Unit

    override suspend fun deleteChat(
        chatId: Long,
        lastEventTime: Long,
        forAll: Boolean,
    ) = Unit

    override suspend fun leaveGroup(chatId: Long) = Unit

    override suspend fun findByPhone(phone: String): FoundUser = FoundUser(ALICE, "Алиса Демидова")

    override suspend fun addContact(
        userId: Long,
        firstName: String,
    ) = Unit

    override fun dialogChatId(
        myId: Long,
        otherId: Long,
    ): Long = myId xor otherId

    override suspend fun fetchUser(userId: Long): UserInfo? =
        contactsList.firstOrNull { it.id == userId } ?: UserInfo(id = userId, name = "Пользователь")

    override suspend fun searchChats(query: String): List<SearchResult> = emptyList()

    override suspend fun fetchContactName(userId: Long): String? = contactsMap[userId]

    override suspend fun getVideoUrl(
        chatId: Long,
        messageId: Long,
        videoId: Long,
    ): String? = null

    override suspend fun getFileUrl(
        chatId: Long,
        messageId: Long,
        fileId: Long,
    ): String? = null

    private companion object {
        const val ME = 1L
        const val ALICE = 2L
        const val BORIS = 3L
        const val DIALOG_ID = ME xor ALICE // a 1:1 chatId is myId xor otherId
        const val GROUP_ID = -1000L
        const val BASE = 1_780_000_000_000L
    }
}
