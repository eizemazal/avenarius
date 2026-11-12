package com.avenarius.app.net

import com.avenarius.app.model.Account
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
import com.avenarius.app.model.Poll
import com.avenarius.app.model.PollAnswer
import com.avenarius.app.model.Reaction
import com.avenarius.app.model.ReplyInfo
import com.avenarius.app.model.ServiceEvent
import com.avenarius.app.model.Sticker
import com.avenarius.app.model.StickerShelf
import com.avenarius.app.model.TextSpan
import com.avenarius.app.model.UserInfo
import com.avenarius.app.model.VoiceAttach
import com.avenarius.app.model.previewLabel
import com.avenarius.app.net.DemoFixtures.Companion.ME
import com.avenarius.app.ui.nowMillis
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * A fully offline [MaxApi] backed by in-memory sample data, used for the Google Play
 * review "demo account" (entered via the magic phone/code at login). It never opens a
 * network connection. The starting data is in [DemoFixtures]; every action (sending,
 * editing, reacting, voting, pinning, folders, the black list…) changes that copy, so
 * reopening a chat shows what was done in it. Calls stay unavailable.
 */
class DemoMaxApi(
    now: Long = nowMillis(),
) : MaxApi {
    override val incoming = MutableSharedFlow<Message>()
    override val readMarks = MutableSharedFlow<ReadMark>()
    override val presence = MutableSharedFlow<Presence>()
    override val reactionUpdates = MutableSharedFlow<ReactionUpdate>()
    override val chatUpdates = MutableSharedFlow<Chat>()
    override val deletions = MutableSharedFlow<MessageDeletion>()
    override val incomingCalls = MutableSharedFlow<IncomingCall>()
    override val drops = MutableSharedFlow<Unit>()
    override val isConnected = true

    private val f = DemoFixtures(now)
    private var me: Account = f.me
    private var meInfo: UserInfo = f.meInfo
    private val users = f.users.associateBy { it.id }.toMutableMap()
    private val contactIds = f.contactIds.toMutableSet()
    private val blocked = f.blocked.toMutableSet()
    private val chats = f.chats.associateBy { it.id }.toMutableMap()
    private val history = f.history.mapValues { it.value.toMutableList() }.toMutableMap()
    private val pinned = f.pinned.toMutableMap()
    private var folders = f.folders
    private var privacy = f.privacy
    private val files = mutableMapOf<Long, FileAttach>()
    private var seq = 900_000L
    private var nextChatId = -5000L

    /** Who is typing where, for the ViewModel to show (the demo has no live pushes). */
    val typingNow: Map<Long, Map<Long, Long>> get() = f.typing

    private fun nextId() = seq++

    /** [c] as the list shows it: last message, pin and title taken from the current data. */
    private fun row(c: Chat): Chat {
        val list = history[c.id].orEmpty()
        val last = list.lastOrNull()
        return c.copy(
            title = if (c.isDialog && c.id != DemoFixtures.SAVED) users[c.id xor ME]?.name ?: c.title else c.title,
            lastMessageText = last?.previewLabel(),
            lastSenderId = last?.senderId ?: 0,
            lastMessageTime = last?.time ?: 0,
            lastEventTime = maxOf(c.lastEventTime, last?.time ?: 0),
            pinnedMessage = pinned[c.id]?.let { id -> list.firstOrNull { it.id == id } },
        )
    }

    private fun rowOf(chatId: Long) = chats[chatId]?.let(::row)

    private fun find(
        chatId: Long,
        id: String,
    ) = history[chatId]?.firstOrNull { it.id == id }

    /** Replaces message [id] of [chatId] with [change] of it; returns the new copy. */
    private fun update(
        chatId: Long,
        id: String,
        change: (Message) -> Message,
    ): Message? {
        val list = history[chatId] ?: return null
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return null
        return change(list[i]).also { list[i] = it }
    }

    private fun updateChat(
        chatId: Long,
        change: (Chat) -> Chat,
    ): Chat? = chats[chatId]?.let(change)?.also { chats[chatId] = it }

    /** Adds [m] to its chat, creating a dialog row when it is the first message to someone. */
    private fun append(m: Message): Message {
        if (m.chatId !in chats) {
            chats[m.chatId] = Chat(m.chatId, users[m.chatId xor ME]?.name ?: "Диалог", null, m.time, isDialog = true)
        }
        history.getOrPut(m.chatId) { mutableListOf() } += m
        return m
    }

    private fun service(
        chatId: Long,
        event: ServiceEvent,
    ) = append(Message(nextId().toString(), null, chatId, event.actorId, "", nowMillis(), service = event))

    // --- session ------------------------------------------------------------

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
            chats = chats.values.map(::row).sortedByDescending { it.lastEventTime },
            contacts = users.mapValues { it.value.name },
            contactsList = contactIds.mapNotNull { users[it] },
            online = f.online,
            refreshedToken = null,
            presence = f.presence,
            privacy = privacy,
            reactionsMenu = f.reactionsMenu,
            pinLimit = 5,
            pollsInDialogs = true,
        )

    override suspend fun reportHostReachability() = Unit

    override suspend fun approveQrLogin(qrLink: String) = Unit

    override suspend fun getSessions(): List<SessionInfo> = f.sessions

    // Its new token would be stored as the real login token, so the demo refuses instead.
    override suspend fun closeOtherSessions(): String? = error("В демо-режиме другие сеансы не завершаются")

    override suspend fun updatePrivacy(privacy: Privacy): Privacy = privacy.also { this.privacy = it }

    // --- calls (not in the demo) ---------------------------------------------

    override suspend fun startCall(
        peerId: Long,
        isVideo: Boolean,
    ): CallSetup = error("Звонки недоступны в демо-режиме")

    override suspend fun acceptCall(
        conversationId: String,
        peerId: Long,
        isVideo: Boolean,
    ): CallSetup = error("Звонки недоступны в демо-режиме")

    override suspend fun hangupCall(
        conversationId: String,
        reason: String,
        peerId: Long?,
    ) = Unit

    // --- messages -----------------------------------------------------------

    override suspend fun fetchHistory(
        chatId: Long,
        fromTime: Long,
        count: Int,
    ): List<Message> = history[chatId].orEmpty().filter { it.time <= fromTime }.takeLast(count)

    override suspend fun sendMessage(
        chatId: Long,
        text: String,
        cid: Long,
        replyToId: String?,
        attaches: List<OutAttach>,
        spans: List<TextSpan>,
    ): Message {
        val base =
            Message(
                id = nextId().toString(),
                cid = cid,
                chatId = chatId,
                senderId = ME,
                text = text,
                time = nowMillis(),
                status = MessageStatus.SENT,
                spans = spans,
                replyTo = replyToId?.let { id -> find(chatId, id)?.let { ReplyInfo(it.senderId, it.previewLabel(), id) } },
            )
        return append(attaches.fold(base, ::withAttach))
    }

    private fun withAttach(
        m: Message,
        a: OutAttach,
    ): Message =
        when (a) {
            is OutAttach.Photo -> m.copy(media = m.media + MediaAttach(MediaType.PHOTO, a.token, 0, 0))
            is OutAttach.Video -> m.copy(media = m.media + MediaAttach(MediaType.VIDEO, a.token, 0, 0))
            is OutAttach.VideoNote ->
                m.copy(media = m.media + MediaAttach(MediaType.VIDEO, a.token, 1, 1, durationSec = a.durationSeconds, isVideoNote = true))
            is OutAttach.File -> files[a.fileId]?.let { m.copy(files = m.files + it) } ?: m
            is OutAttach.Voice -> m.copy(voice = VoiceAttach(nextId(), a.durationSeconds, DemoFixtures.wave(a.durationSeconds)))
            is OutAttach.Poll ->
                m.copy(poll = Poll(nextId(), a.title, a.settings, a.answers.mapIndexed { i, t -> PollAnswer(i, t) }))
            is OutAttach.Sticker -> m.copy(stickerUrl = f.stickers.firstOrNull { it.id == a.stickerId }?.url)
        }

    // An upload keeps what the picker gave to draw it with (a content URI on Android), so a sent photo shows.
    override suspend fun uploadPhoto(
        content: MediaContent,
        fileName: String,
        mime: String,
        profile: Boolean,
        onProgress: ((Float) -> Unit)?,
    ): OutAttach.Photo = OutAttach.Photo(content.previewModel?.toString().orEmpty())

    override suspend fun uploadVideo(
        content: MediaContent,
        fileName: String,
        mime: String,
        onProgress: ((Float) -> Unit)?,
    ): OutAttach.Video = OutAttach.Video(nextId(), content.previewModel?.toString().orEmpty())

    override suspend fun uploadVideoNote(
        content: MediaContent,
        durationSeconds: Int,
        onProgress: ((Float) -> Unit)?,
    ): OutAttach.VideoNote = OutAttach.VideoNote(token = "", durationSeconds = durationSeconds)

    override suspend fun uploadVoice(
        content: MediaContent,
        durationSeconds: Int,
        onProgress: ((Float) -> Unit)?,
    ): OutAttach.Voice = OutAttach.Voice(token = "", durationSeconds = durationSeconds)

    override suspend fun uploadFile(
        content: MediaContent,
        fileName: String,
        mime: String,
        onProgress: ((Float) -> Unit)?,
    ): OutAttach.File {
        val id = nextId()
        files[id] = FileAttach(id, fileName, content.size.coerceAtLeast(0))
        return OutAttach.File(id)
    }

    override suspend fun forwardMessage(
        toChatId: Long,
        messageId: String,
        fromChatId: Long,
        cid: Long,
    ): Message? {
        val src = find(fromChatId, messageId) ?: return null
        return append(
            src.copy(
                id = nextId().toString(),
                cid = cid,
                chatId = toChatId,
                senderId = ME,
                time = nowMillis(),
                status = MessageStatus.SENT,
                forwardedFrom = src.forwardedFrom ?: src.senderId,
                reactions = emptyList(),
                replyTo = null,
                edited = false,
                views = 0,
            ),
        )
    }

    override suspend fun editMessage(
        chatId: Long,
        messageId: String,
        text: String,
        spans: List<TextSpan>?,
    ) {
        update(chatId, messageId) { it.copy(text = text, spans = spans ?: it.spans, edited = true) }
    }

    override suspend fun deleteMessages(
        chatId: Long,
        messageIds: List<String>,
        forAll: Boolean,
    ) {
        history[chatId]?.removeAll { it.id in messageIds }
        if (pinned[chatId] in messageIds) pinned.remove(chatId)
    }

    override suspend fun markRead(
        chatId: Long,
        messageId: String,
        mark: Long,
    ) {
        updateChat(chatId) { it.copy(unreadCount = 0) }
    }

    override suspend fun markUnread(
        chatId: Long,
        mark: Long,
    ) {
        updateChat(chatId) { it.copy(unreadCount = maxOf(it.unreadCount, 1)) }
    }

    override suspend fun setReaction(
        chatId: Long,
        messageId: String,
        emoji: String?,
    ) {
        update(chatId, messageId) { m ->
            val dropped =
                m.reactions.mapNotNull { r ->
                    if (!r.mine) r else r.copy(count = r.count - 1, mine = false).takeIf { it.count > 0 }
                }
            val list =
                when {
                    emoji == null -> dropped
                    dropped.any { it.emoji == emoji } ->
                        dropped.map {
                            if (it.emoji ==
                                emoji
                            ) {
                                it.copy(count = it.count + 1, mine = true)
                            } else {
                                it
                            }
                        }
                    else -> dropped + Reaction(emoji, 1, mine = true)
                }
            m.copy(reactions = list)
        }
    }

    /** Who reacted: us for our reaction, the chat's other members for the rest (as many as the count, as far as they go). */
    override suspend fun getReactionUsers(
        chatId: Long,
        messageId: String,
    ): List<Pair<Long, String>> {
        val m = find(chatId, messageId) ?: return emptyList()
        val others = (chats[chatId]?.memberIds?.takeIf { it.isNotEmpty() } ?: setOf(chatId xor ME)).filter { it != ME && it in users }
        var next = 0
        return m.reactions.flatMap { r ->
            val theirs = (r.count - if (r.mine) 1 else 0).coerceAtMost(others.size)
            val people = (if (r.mine) listOf(ME) else emptyList()) + List(theirs) { others[next++ % others.size] }
            people.map { it to r.emoji }
        }
    }

    override suspend fun pinMessage(
        chatId: Long,
        messageId: String?,
        notify: Boolean,
    ): Chat? {
        if (messageId == null) pinned.remove(chatId) else pinned[chatId] = messageId
        return rowOf(chatId)
    }

    override suspend fun votePoll(
        chatId: Long,
        messageId: String,
        poll: Poll,
        answerIds: List<Int>,
    ): Poll? {
        val counted = (find(chatId, messageId)?.poll ?: poll).withMyVotes(answerIds.toSet())
        update(chatId, messageId) { it.copy(poll = counted) }
        return counted
    }

    override suspend fun searchMessages(
        chatId: Long?,
        query: String,
    ): List<MessageHit> =
        history
            .filterKeys { chatId == null || it == chatId }
            .values
            .flatten()
            .filter { it.text.contains(query, ignoreCase = true) }
            .sortedByDescending { it.time }
            .map { MessageHit(it, rowOf(it.chatId)) }

    override suspend fun fetchChatMedia(
        chatId: Long,
        types: List<String>,
        anchor: String,
        count: Int,
        withAnchor: Boolean,
    ): List<Message> {
        val list = history[chatId].orEmpty()
        val at = list.firstOrNull { it.id == anchor }?.time ?: Long.MAX_VALUE
        return list.filter { (if (withAnchor) it.time <= at else it.time < at) && types.any { t -> it.carries(t) } }.takeLast(count)
    }

    private fun Message.carries(type: String): Boolean =
        when (type) {
            "PHOTO" -> media.any { it.type == MediaType.PHOTO }
            "VIDEO" -> media.any { it.type == MediaType.VIDEO }
            "FILE" -> files.isNotEmpty()
            "SHARE" -> linkPreview != null
            "AUDIO" -> voice != null
            else -> false
        }

    // Nothing to play or download offline: the bubbles show, playback and saving say they failed.
    override suspend fun getVideoUrl(
        chatId: Long,
        messageId: Long,
        videoId: Long,
    ): String? = null

    override suspend fun getAudioUrl(
        chatId: Long,
        messageId: Long,
        audioId: Long,
        token: String?,
    ): String? = null

    override suspend fun getFileUrl(
        chatId: Long,
        messageId: Long,
        fileId: Long,
    ): String? = null

    // --- stickers -----------------------------------------------------------

    override suspend fun getStickerShelf(): StickerShelf = StickerShelf(f.stickerSets, listOf(DemoFixtures.STICKER_HEART))

    override suspend fun getStickers(ids: List<Long>): List<Sticker> = f.stickers.filter { it.id in ids }

    // --- chats --------------------------------------------------------------

    override suspend fun setChatMuted(
        chatId: Long,
        muted: Boolean,
    ) {
        updateChat(chatId) { it.copy(muted = muted) }
    }

    override suspend fun setFavIndex(indexes: Map<Long, Long>) {
        indexes.forEach { (id, index) -> updateChat(id) { it.copy(favIndex = index) } }
    }

    override suspend fun clearHistory(
        chatId: Long,
        lastEventTime: Long,
    ) {
        history[chatId]?.clear()
        pinned.remove(chatId)
    }

    override suspend fun deleteChat(
        chatId: Long,
        lastEventTime: Long,
        forAll: Boolean,
    ) {
        chats.remove(chatId)
        history.remove(chatId)
    }

    override suspend fun leaveGroup(chatId: Long) = deleteChat(chatId, 0, false)

    override suspend fun joinByLink(link: String): Chat? = null

    override suspend fun getChatMembers(chatId: Long): List<UserInfo> =
        chats[chatId]?.memberIds.orEmpty().mapNotNull { if (it == ME) meInfo else users[it] }

    override suspend fun getReadMarks(chatId: Long): Map<Long, Long> = chats[chatId]?.readMarks.orEmpty()

    override suspend fun addMembers(
        chatId: Long,
        userIds: List<Long>,
    ) {
        val added = userIds.filter { it !in chats[chatId]?.memberIds.orEmpty() }
        updateChat(chatId) { it.copy(memberIds = it.memberIds + added, participantsCount = it.participantsCount + added.size) }
        if (added.isNotEmpty()) service(chatId, ServiceEvent("add", ME, added, message = "Вы добавили участников"))
    }

    override suspend fun removeMember(
        chatId: Long,
        userId: Long,
    ) {
        updateChat(chatId) {
            it.copy(
                memberIds = it.memberIds - userId,
                adminIds = it.adminIds - userId,
                participantsCount =
                    it.participantsCount - 1,
            )
        }
        service(chatId, ServiceEvent("remove", ME, listOf(userId), message = "Вы исключили участника"))
    }

    override suspend fun setAdmin(
        chatId: Long,
        userId: Long,
        admin: Boolean,
    ) {
        updateChat(chatId) { it.copy(adminIds = if (admin) it.adminIds + userId else it.adminIds - userId) }
    }

    override suspend fun createGroup(
        title: String,
        memberIds: List<Long>,
        photoToken: String?,
    ): Long {
        val id = nextChatId--
        val members = memberIds.toSet() + ME
        chats[id] =
            Chat(
                id = id,
                title = title,
                lastMessageText = null,
                lastEventTime = nowMillis(),
                ownerId = ME,
                adminIds = setOf(ME),
                memberIds = members,
                participantsCount = members.size,
                canAddMembers = true,
                link = "https://max.ru/join/demo${-id}",
            )
        service(id, ServiceEvent("new", ME, message = "Вы создали группу"))
        return id
    }

    override suspend fun createChannel(
        title: String,
        photoToken: String?,
    ): Long {
        val id = nextChatId--
        chats[id] =
            Chat(
                id = id,
                title = title,
                lastMessageText = null,
                lastEventTime = nowMillis(),
                isChannel = true,
                ownerId = ME,
                adminIds = setOf(ME),
                memberIds = setOf(ME),
                participantsCount = 1,
                link = "https://max.ru/demo_channel${-id}",
            )
        service(id, ServiceEvent("system", ME, message = "Канал создан"))
        return id
    }

    override suspend fun editChat(
        chatId: Long,
        title: String?,
        description: String?,
        photoToken: String?,
        options: Map<String, Boolean>,
    ): Chat? {
        updateChat(chatId) { c ->
            val opts = c.options + options.filterValues { it }.keys - options.filterValues { !it }.keys
            c.copy(
                title = title ?: c.title,
                description = if (description != null) description.ifEmpty { null } else c.description,
                options = opts,
                allCanPin = "ALL_CAN_PIN_MESSAGE" in opts,
            )
        }
        return rowOf(chatId)
    }

    override suspend fun revokeChatLink(chatId: Long): Chat? {
        updateChat(chatId) { it.copy(link = "https://max.ru/join/demo${nextId()}") }
        return rowOf(chatId)
    }

    override suspend fun searchChats(
        query: String,
        marker: Long?,
    ): SearchPage = SearchPage(emptyList())

    // --- folders ------------------------------------------------------------

    override suspend fun getFolders(): FoldersUpdate = FoldersUpdate(folders, folders.map { it.id })

    override suspend fun saveFolder(folder: ChatFolder) {
        folders = if (folders.any { it.id == folder.id }) folders.map { if (it.id == folder.id) folder else it } else folders + folder
    }

    override suspend fun reorderFolders(order: List<String>) {
        folders = folders.sortedBy { order.indexOf(it.id) }
    }

    override suspend fun deleteFolder(id: String) {
        folders = folders.filterNot { it.id == id }
    }

    // --- people -------------------------------------------------------------

    override suspend fun updateProfile(
        firstName: String,
        lastName: String?,
        description: String?,
        photoToken: String?,
    ) {
        me = me.copy(firstName = firstName, lastName = lastName)
        meInfo =
            meInfo.copy(
                name = listOfNotNull(firstName, lastName).joinToString(" "),
                firstName = firstName,
                lastName = lastName,
                description = description,
            )
    }

    override suspend fun fetchUser(userId: Long): UserInfo? =
        if (userId == ME) meInfo else users[userId]?.copy(blocked = userId in blocked) ?: UserInfo(userId, "Пользователь")

    override suspend fun fetchContactName(userId: Long): String? = users[userId]?.name

    override suspend fun findByPhone(phone: String): FoundUser = FoundUser(DemoFixtures.ALICE, users.getValue(DemoFixtures.ALICE).name)

    override suspend fun addContact(
        userId: Long,
        firstName: String,
    ) {
        contactIds += userId
    }

    override suspend fun renameContact(
        userId: Long,
        firstName: String,
        lastName: String?,
    ): UserInfo? {
        val renamed =
            users[userId]?.copy(name = listOfNotNull(firstName, lastName).joinToString(" "), firstName = firstName, lastName = lastName)
                ?: return null
        users[userId] = renamed
        return renamed
    }

    override suspend fun removeContact(userId: Long) {
        contactIds -= userId
    }

    override suspend fun setBlocked(
        userId: Long,
        blocked: Boolean,
    ) {
        if (blocked) this.blocked += userId else this.blocked -= userId
    }

    override suspend fun getBlockedPage(from: Int): BlockedPage {
        val page = blocked.drop(from).mapNotNull { users[it]?.copy(blocked = true) }
        return BlockedPage(page, page.size)
    }

    override fun dialogChatId(
        myId: Long,
        otherId: Long,
    ): Long = myId xor otherId
}
