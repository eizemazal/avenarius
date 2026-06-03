package com.avenarius.app.net

import com.avenarius.app.model.Account
import com.avenarius.app.model.Chat
import com.avenarius.app.model.MediaAttach
import com.avenarius.app.model.MediaType
import com.avenarius.app.model.Message
import com.avenarius.app.model.MessageStatus
import com.avenarius.app.model.OutAttach
import com.avenarius.app.model.Reaction
import com.avenarius.app.model.ReplyInfo
import com.avenarius.app.model.SearchResult
import com.avenarius.app.model.UserInfo
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** A read-mark update: [userId] has read everything up to [mark] (ms) in [chatId]. */
data class ReadMark(
    val chatId: Long,
    val userId: Long,
    val mark: Long,
)

/** A live presence change: [userId] went [online] (server push, opcode 132). */
data class Presence(
    val userId: Long,
    val online: Boolean,
)

/**
 * A live reaction-count change for a message (server push, opcode 155). The push
 * carries only aggregate counts (no "did I react"), so [reactions] always has
 * `mine = false`; the ViewModel re-derives our own reaction from local state.
 */
data class ReactionUpdate(
    val chatId: Long,
    val messageId: String,
    val reactions: List<Reaction>,
)

/** Result of submitting an SMS code. */
sealed interface CodeResult {
    data class Success(
        val loginToken: String,
    ) : CodeResult

    data class NeedPassword(
        val trackId: String,
        val hint: String?,
    ) : CodeResult

    /** This phone has no account yet — registration (a name) is required. */
    data object NeedRegister : CodeResult
}

/** A user resolved by phone lookup. */
data class FoundUser(
    val userId: Long,
    val name: String,
)

/** Result of a sync (chats + profile + contacts + a possibly-rolled token). */
data class SyncResult(
    val account: Account,
    val chats: List<Chat>,
    /** userId -> display name, for avatars and sender labels. */
    val contacts: Map<Long, String>,
    /** Full contact info for the Contacts page. */
    val contactsList: List<UserInfo>,
    /** Ids of users currently online (presence carries a `status` field). */
    val online: Set<Long>,
    /** The server rolls the login token on each sync; persist it if present. */
    val refreshedToken: String?,
)

/**
 * Protocol surface used by the UI/ViewModel. Implemented by [MaxClient] for real;
 * a fake implementation drives the ViewModel tests.
 */
interface MaxApi {
    val incoming: SharedFlow<Message>
    val readMarks: SharedFlow<ReadMark>
    val presence: SharedFlow<Presence>
    val reactionUpdates: SharedFlow<ReactionUpdate>

    /** A chat that was created or updated (NOTIF_CHAT) — upsert it into the list. */
    val chatUpdates: SharedFlow<Chat>
    val drops: SharedFlow<Unit>
    val isConnected: Boolean

    suspend fun connect(
        deviceId: String,
        mtInstance: String,
    )

    fun disconnect()

    suspend fun startAuth(phone: String): Int

    suspend fun checkCode(code: String): CodeResult

    suspend fun register(
        firstName: String,
        lastName: String? = null,
    ): String

    suspend fun checkPassword(
        password: String,
        trackId: String,
    ): String

    suspend fun sync(token: String): SyncResult

    suspend fun fetchHistory(
        chatId: Long,
        fromTime: Long,
        count: Int = 50,
    ): List<Message>

    suspend fun sendMessage(
        chatId: Long,
        text: String,
        cid: Long,
        replyToId: String? = null,
        attaches: List<OutAttach> = emptyList(),
    ): Message?

    /** Uploads a photo and returns the attach descriptor to include in [sendMessage]. */
    suspend fun uploadPhoto(
        bytes: ByteArray,
        fileName: String,
        mime: String,
    ): OutAttach.Photo

    /** Uploads a video (waits for server processing) and returns its attach descriptor. */
    suspend fun uploadVideo(
        bytes: ByteArray,
        fileName: String,
        mime: String,
    ): OutAttach.Video

    /** Uploads an arbitrary file (waits for server processing) and returns its attach. */
    suspend fun uploadFile(
        bytes: ByteArray,
        fileName: String,
        mime: String,
    ): OutAttach.File

    suspend fun markRead(
        chatId: Long,
        messageId: String,
        mark: Long,
    )

    /** Sets [emoji] as our reaction on a message, or removes our reaction when [emoji] is null. */
    suspend fun setReaction(
        chatId: Long,
        messageId: String,
        emoji: String?,
    )

    /**
     * Deletes a chat. [forAll] = true also removes the messages for the other
     * party (delete for everyone); [lastEventTime] is the chat's last event time.
     */
    suspend fun deleteChat(
        chatId: Long,
        lastEventTime: Long,
        forAll: Boolean,
    )

    /** Leaves/exits a group chat. */
    suspend fun leaveGroup(chatId: Long)

    suspend fun findByPhone(phone: String): FoundUser

    suspend fun addContact(
        userId: Long,
        firstName: String,
    )

    fun dialogChatId(
        myId: Long,
        otherId: Long,
    ): Long

    suspend fun fetchUser(userId: Long): UserInfo?

    suspend fun searchChats(query: String): List<SearchResult>

    suspend fun fetchContactName(userId: Long): String?

    suspend fun getVideoUrl(
        chatId: Long,
        messageId: Long,
        videoId: Long,
    ): String?
}

/**
 * A minimal client for the "Max" messenger MOBILE protocol.
 *
 * Unlike the web client (JSON over WebSocket, QR login only), the mobile protocol
 * runs over a raw TLS connection with binary-framed, MessagePack-encoded payloads
 * (see [MobileTransport]) and — crucially — supports phone + SMS login, exactly
 * like the official Android app.
 *
 * Everything here is platform-independent (commonMain) and shared by the Android
 * and desktop clients; only [TlsSocket] differs per platform.
 */
class MaxClient : MaxApi {
    companion object {
        // Mirrors the official Android client / rumax. appVersion + buildNumber
        // are taken from the current MAX.apk (26.17.0 / 6713).
        const val APP_VERSION = "26.17.0"
        const val BUILD_NUMBER = 6713

        // Opcodes (verified against PyMax protocol enums and rumax).
        private const val OP_HANDSHAKE = 6
        private const val OP_START_AUTH = 17
        private const val OP_CHECK_CODE = 18
        private const val OP_SYNC = 19
        private const val OP_REGISTER = 23
        private const val OP_CONTACT_INFO = 32
        private const val OP_CONTACT_UPDATE = 34
        private const val OP_CONTACT_BY_PHONE = 46
        private const val OP_FETCH_HISTORY = 49
        private const val OP_PUBLIC_SEARCH = 60
        private const val OP_VIDEO_PLAY = 83
        private const val OP_PHOTO_UPLOAD = 80 // PHOTO_UPLOAD: request a photo upload URL
        private const val OP_VIDEO_UPLOAD = 82 // VIDEO_UPLOAD: request a video upload URL
        private const val OP_FILE_UPLOAD = 87 // FILE_UPLOAD: request a file upload URL
        private const val OP_MARK_READ = 50
        private const val OP_SEND_MESSAGE = 64
        private const val OP_CHECK_PASSWORD = 115
        const val OP_NEW_MESSAGE = 128
        const val OP_MARK_UPDATE = 130 // server push: read/delivery marks changed
        const val OP_PRESENCE = 132 // server push: a contact's online state changed
        const val OP_REACTION_UPDATE = 155 // NOTIF_MSG_REACTIONS_CHANGED push
        const val OP_NOTIF_CHAT = 135 // NOTIF_CHAT push: a chat was created/updated (e.g. added to a group)
        const val OP_NOTIF_ATTACH = 136 // NOTIF_ATTACH push: an uploaded video/file finished processing

        // Confirmed from the official client's opcode enum (ru.ok.tamtam.api.d,
        // recovered by decompiling MAX.apk).
        private const val OP_REACTION = 178 // MSG_REACTION: add/set a reaction
        private const val OP_CANCEL_REACTION = 179 // MSG_CANCEL_REACTION: remove our reaction
        private const val OP_DELETE_CHAT = 52 // CHAT_DELETE
        private const val OP_LEAVE_CHAT = 58 // CHAT_LEAVE
    }

    private val transport = MobileTransport()
    private val scope = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, _ -> })

    // Plain HTTP client for media upload/download (separate from the raw-socket
    // protocol transport). Uses the platform Ktor engine (OkHttp on Android).
    private val http by lazy { HttpClient() }

    /** Temporary token from START_AUTH / a 2FA challenge, needed for the next step. */
    private var authToken: String? = null

    /** Stream of newly received messages (server push, opcode 128). */
    private val _incoming = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    override val incoming: SharedFlow<Message> = _incoming

    /** Stream of read-mark updates (server push, opcode 130) — e.g. the other side read our messages. */
    private val _readMarks = MutableSharedFlow<ReadMark>(extraBufferCapacity = 64)
    override val readMarks: SharedFlow<ReadMark> = _readMarks

    /** Stream of live presence changes (server push, opcode 132). */
    private val _presence = MutableSharedFlow<Presence>(extraBufferCapacity = 64)
    override val presence: SharedFlow<Presence> = _presence

    /** Stream of live reaction-count changes (server push, opcode 155). */
    private val _reactionUpdates = MutableSharedFlow<ReactionUpdate>(extraBufferCapacity = 64)
    override val reactionUpdates: SharedFlow<ReactionUpdate> = _reactionUpdates

    // Video-processing-ready signals (NOTIF_ATTACH, op 136), keyed by videoId. A
    // small replay buffer covers the race where the push lands before the uploader
    // starts awaiting it. (Internal only — no public counterpart.)
    private val videoReadyFlow = MutableSharedFlow<Long>(replay = 8, extraBufferCapacity = 16)

    // File-processing-ready signals (NOTIF_ATTACH, op 136), keyed by fileId.
    private val fileReadyFlow = MutableSharedFlow<Long>(replay = 8, extraBufferCapacity = 16)

    /** Stream of created/updated chats (server push, opcode 135). */
    private val _chatUpdates = MutableSharedFlow<Chat>(extraBufferCapacity = 64)
    override val chatUpdates: SharedFlow<Chat> = _chatUpdates

    override val isConnected: Boolean get() = transport.isConnected

    /** Emitted when the connection drops unexpectedly (for auto-reconnect). */
    override val drops: SharedFlow<Unit> get() = transport.drops

    /** Preset avatar id to use when registering a new account (rumax's default). */
    private var registerPhotoId: Long = 2981369L

    /** Our own user id, remembered from the last sync (for NOTIF_CHAT dialog titles). */
    private var myId: Long = 0L

    // ---------------------------------------------------------------------
    // Connection
    // ---------------------------------------------------------------------

    init {
        // Subscribe to server-pushed new-message frames ONCE. The transport's
        // event flow survives reconnects, so we must not re-subscribe in connect()
        // (that would stack duplicate collectors on every refresh/reconnect).
        scope.launch {
            transport.events.collect { (opcode, payload) ->
                when (opcode) {
                    OP_NEW_MESSAGE -> {
                        val chatId = payload["chatId"]?.jsonPrimitive?.long
                        val msg = payload["message"]?.jsonObject
                        if (chatId != null && msg != null) parseMessage(msg, chatId)?.let { _incoming.emit(it) }
                    }
                    OP_MARK_UPDATE -> {
                        val chatId = payload["chatId"]?.jsonPrimitive?.longOrNullSafe()
                        val mark = payload["mark"]?.jsonPrimitive?.longOrNullSafe()
                        val userId = payload["userId"]?.jsonPrimitive?.longOrNullSafe() ?: 0L
                        if (chatId != null && mark != null) _readMarks.emit(ReadMark(chatId, userId, mark))
                    }
                    OP_PRESENCE -> parsePresence(payload)?.let { _presence.emit(it) }
                    OP_REACTION_UPDATE -> parseReactionUpdate(payload)?.let { _reactionUpdates.emit(it) }
                    OP_NOTIF_CHAT -> {
                        // {chat: {...}} — a chat was created or updated (new dialog, added to a group).
                        payload["chat"]?.jsonObject?.let { parseChat(it, emptyMap()) }?.let { _chatUpdates.emit(it) }
                    }
                    OP_NOTIF_ATTACH -> {
                        // {videoId} or {fileId} — an uploaded media finished processing.
                        payload["videoId"]?.jsonPrimitive?.longOrNullSafe()?.let { videoReadyFlow.emit(it) }
                        payload["fileId"]?.jsonPrimitive?.longOrNullSafe()?.let { fileReadyFlow.emit(it) }
                    }
                }
            }
        }
    }

    /**
     * Parses a live presence push: `{userId: <id>, presence: {seen, status?}}`.
     * Online iff the nested presence carries a `status` field (see [isOnline]).
     */
    private fun parsePresence(payload: JsonObject): Presence? {
        val uid = payload["userId"]?.jsonPrimitive?.longOrNullSafe() ?: return null
        val p = payload["presence"]?.jsonObject
        return Presence(uid, p?.isOnline() == true)
    }

    /** Parses one chat object (from sync's `chats` array or a NOTIF_CHAT push). */
    private fun parseChat(
        c: JsonObject,
        names: Map<Long, String>,
    ): Chat? {
        val id = c["id"]?.jsonPrimitive?.long ?: return null
        val type = c["type"]?.jsonPrimitive?.contentOrNullSafe()
        val rawTitle = c["title"]?.jsonPrimitive?.contentOrNullSafe()
        val lastText =
            c["lastMessage"]
                ?.jsonObject
                ?.get("text")
                ?.jsonPrimitive
                ?.contentOrNullSafe()
                ?.let { localize(it) }
        val lastTime = c["lastEventTime"]?.jsonPrimitive?.longOrNullSafe() ?: 0L
        val unread = c["newMessages"]?.jsonPrimitive?.intOrNullSafe() ?: 0
        val isDialog = type == "DIALOG"
        // participants = {userId: lastReadMarkMs}; the other side's mark tells us how
        // far they've read (so our messages up to it show ✓✓).
        val otherReadMark =
            c["participants"]
                ?.jsonObject
                ?.entries
                ?.filter { it.key.toLongOrNull() != null && it.key.toLong() != myId }
                ?.mapNotNull { it.value.jsonPrimitive.longOrNullSafe() }
                ?.maxOrNull() ?: 0L
        val title =
            when {
                !rawTitle.isNullOrBlank() -> rawTitle
                isDialog -> {
                    val other =
                        c["participants"]
                            ?.jsonObject
                            ?.keys
                            ?.mapNotNull { it.toLongOrNull() }
                            ?.firstOrNull { it != myId }
                    when {
                        other == null -> "Избранное" // self-chat (chatId == myId xor myId == 0)
                        else -> names[other] ?: "Диалог $id"
                    }
                }
                else -> "Чат $id"
            }
        return Chat(
            id = id,
            title = title,
            lastMessageText = lastText,
            lastEventTime = lastTime,
            unreadCount = unread,
            isDialog = isDialog,
            // Dialogs get their avatar from the contact (resolved in the UI);
            // groups/channels carry their own avatar on the chat object.
            avatarUrl = if (isDialog) null else c.avatarUrl(),
            otherReadMark = otherReadMark,
        )
    }

    /**
     * Parses a live reaction push: `{chatId, messageId, counters:[{reaction, count}]}`.
     * `messageId` arrives as a number; we normalise it to a string to match [Message.id].
     */
    private fun parseReactionUpdate(payload: JsonObject): ReactionUpdate? {
        val chatId = payload["chatId"]?.jsonPrimitive?.longOrNullSafe() ?: return null
        val messageId = payload["messageId"]?.jsonPrimitive?.longOrNullSafe()?.toString() ?: return null
        val counters =
            payload["counters"]
                ?.jsonArray
                .orEmptyList()
                .mapNotNull { el ->
                    val c = el.jsonObject
                    val emoji = c["reaction"]?.jsonPrimitive?.contentOrNullSafe() ?: return@mapNotNull null
                    Reaction(emoji, c["count"]?.jsonPrimitive?.intOrNullSafe() ?: 0, mine = false)
                }
        return ReactionUpdate(chatId, messageId, counters)
    }

    /** Opens the TLS connection and performs the opcode-6 ANDROID handshake. */
    override suspend fun connect(
        deviceId: String,
        mtInstance: String,
    ) {
        if (transport.isConnected) return
        transport.connect()

        transport.request(
            OP_HANDSHAKE,
            buildJsonObject {
                put("clientSessionId", 1)
                put("mt_instanceid", mtInstance)
                putJsonObject("userAgent") {
                    put("deviceType", "ANDROID")
                    put("appVersion", APP_VERSION)
                    put("osVersion", "Android 13")
                    put("timezone", "Europe/Moscow")
                    put("screen", "130dpi 130dpi 600x874")
                    put("pushDeviceType", "GCM")
                    put("locale", "ru")
                    put("buildNumber", BUILD_NUMBER)
                    put("deviceName", "unknown Generic Android-x86_64")
                    put("deviceLocale", "ru")
                }
                put("deviceId", deviceId)
            },
        )
        transport.startPing()
    }

    override fun disconnect() = transport.disconnect()

    // ---------------------------------------------------------------------
    // Authentication
    // ---------------------------------------------------------------------

    /** Requests an SMS code. Returns the expected code length; throws on refusal. */
    override suspend fun startAuth(phone: String): Int {
        val payload =
            transport.request(
                OP_START_AUTH,
                buildJsonObject {
                    put("phone", phone)
                    put("type", "START_AUTH")
                    put("language", "ru")
                },
            )
        authToken = payload["token"]?.jsonPrimitive?.contentOrNullSafe()
        if (authToken == null) error(payload.serverMessage("Не удалось отправить код"))
        return payload["codeLength"]?.jsonPrimitive?.int ?: 6
    }

    /** Submits the SMS code. Either logs in, or signals that a 2FA password is needed. */
    override suspend fun checkCode(code: String): CodeResult {
        val token = authToken ?: error("Сначала запросите код")
        val payload =
            transport.request(
                OP_CHECK_CODE,
                buildJsonObject {
                    put("token", token)
                    put("verifyCode", code)
                    put("authTokenType", "CHECK_CODE")
                },
            )
        payload.loginToken()?.let { return CodeResult.Success(it) }
        payload.passwordTrackId()?.let { return CodeResult.NeedPassword(it, payload.passwordHint()) }
        // New, unregistered phone: the server returns a REGISTER token + preset avatars.
        payload.registerToken()?.let { regToken ->
            authToken = regToken
            payload.firstPresetAvatarId()?.let { registerPhotoId = it }
            return CodeResult.NeedRegister
        }
        error(payload.serverMessage("Неверный код"))
    }

    /** Completes registration of a new account with [firstName]. Returns the login token. */
    override suspend fun register(
        firstName: String,
        lastName: String?,
    ): String {
        val payload =
            transport.request(
                OP_REGISTER,
                buildJsonObject {
                    put("token", authToken) // the REGISTER token from checkCode — required
                    put("firstName", firstName)
                    put("lastName", lastName)
                    put("photoId", registerPhotoId)
                    put("avatarType", "PRESET_AVATAR")
                    put("tokenType", "REGISTER")
                },
            )
        // The login token may come back under tokenAttrs.LOGIN or as a top-level field.
        return payload.loginToken()
            ?: payload["token"]?.jsonPrimitive?.contentOrNullSafe()
            ?: error(payload.serverMessage("Не удалось зарегистрироваться"))
    }

    /** Submits the 2FA cloud password. Returns the login token; throws on failure. */
    override suspend fun checkPassword(
        password: String,
        trackId: String,
    ): String {
        val payload =
            transport.request(
                OP_CHECK_PASSWORD,
                buildJsonObject {
                    put("password", password)
                    put("trackId", trackId)
                },
            )
        return payload.loginToken() ?: error(payload.serverMessage("Неверный пароль"))
    }

    // ---------------------------------------------------------------------
    // Contacts / starting a dialog
    // ---------------------------------------------------------------------

    /** Looks up a Max user by phone number. Throws if not found. */
    override suspend fun findByPhone(phone: String): FoundUser {
        val payload = transport.request(OP_CONTACT_BY_PHONE, buildJsonObject { put("phone", phone) })
        val contact =
            payload["contact"]?.jsonObject
                ?: error(payload.serverMessage("Пользователь Max с таким номером не найден"))
        val id =
            contact["id"]?.jsonPrimitive?.longOrNullSafe()
                ?: error("Пользователь Max с таким номером не найден")
        return FoundUser(id, contact.displayName() ?: phone)
    }

    /** Adds [userId] to the contact list under [firstName]. */
    override suspend fun addContact(
        userId: Long,
        firstName: String,
    ) {
        transport.request(
            OP_CONTACT_UPDATE,
            buildJsonObject {
                put("contactId", userId)
                put("firstName", firstName)
                put("action", "ADD")
            },
        )
    }

    /** The 1:1 dialog chat id between two users is the XOR of their ids. */
    override fun dialogChatId(
        myId: Long,
        otherId: Long,
    ): Long = myId xor otherId

    /** Looks up a single user's display name by id (for notifications/group senders). */
    override suspend fun fetchContactName(userId: Long): String? {
        val payload =
            transport.request(
                OP_CONTACT_INFO,
                buildJsonObject {
                    put("contactIds", buildJsonArray { add(userId) })
                },
            )
        val contact = payload["contacts"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        return contact.displayName()
    }

    /** Resolves a playable video URL (best available MP4, else HLS) for a VIDEO attach. */
    override suspend fun getVideoUrl(
        chatId: Long,
        messageId: Long,
        videoId: Long,
    ): String? {
        val payload =
            transport.request(
                OP_VIDEO_PLAY,
                buildJsonObject {
                    put("chatId", chatId)
                    put("messageId", messageId)
                    put("videoId", videoId)
                },
            )
        for (q in listOf("MP4_1080", "MP4_720", "MP4_480", "MP4_360", "HLS")) {
            payload[q]?.jsonPrimitive?.contentOrNullSafe()?.let { return it }
        }
        return null
    }

    // ---------------------------------------------------------------------
    // Sync (chats + contacts + profile)
    // ---------------------------------------------------------------------

    /** Authenticates the connection with [token] and pulls the chat list. */
    override suspend fun sync(token: String): SyncResult {
        val payload =
            transport.request(
                OP_SYNC,
                buildJsonObject {
                    put("interactive", true)
                    put("token", token)
                    put("chatsSync", 0)
                    put("contactsSync", 0)
                    put("presenceSync", 0)
                    put("draftsSync", 0)
                    put("chatsCount", 40)
                },
            )

        payload["error"]?.let { error(payload.serverMessage("Ошибка синхронизации")) }

        // --- my profile ---
        val contact = payload["profile"]?.jsonObject?.get("contact")?.jsonObject
        val myId = contact?.get("id")?.jsonPrimitive?.long ?: 0L
        val account =
            Account(
                userId = myId,
                firstName = contact.firstName() ?: "Я",
                lastName = contact.lastName(),
                avatarUrl = contact?.avatarUrl(),
            )

        // --- contacts: full info for the Contacts page + id->name for dialog titles ---
        val contactsList =
            payload["contacts"]
                ?.jsonArray
                .orEmptyList()
                .mapNotNull { parseUser(it.jsonObject) }
                .sortedBy { it.name.lowercase() }
        val names = contactsList.associate { it.id to it.name }.toMutableMap()

        this.myId = myId // remembered so NOTIF_CHAT pushes can resolve dialog titles

        // --- chats ---
        val chats =
            payload["chats"]
                ?.jsonArray
                .orEmptyList()
                .mapNotNull { parseChat(it.jsonObject, names) }
                .sortedByDescending { it.lastEventTime }

        // presence = {userId: {seen: <unixSec>, status: <int>}} — a user is online
        // when a `status` field is present (offline entries carry only `seen`).
        val online =
            payload["presence"]
                ?.jsonObject
                ?.entries
                ?.mapNotNull { (key, value) ->
                    val uid = key.toLongOrNull() ?: return@mapNotNull null
                    if ((value as? JsonObject)?.isOnline() == true) uid else null
                }?.toSet() ?: emptySet()

        // The server returns a (possibly rolled) login token here — persist it.
        val refreshedToken = payload["token"]?.jsonPrimitive?.contentOrNullSafe()

        return SyncResult(account, chats, names, contactsList, online, refreshedToken)
    }

    /** Full profile of a single user (for the user page). */
    override suspend fun fetchUser(userId: Long): UserInfo? {
        val payload =
            transport.request(
                OP_CONTACT_INFO,
                buildJsonObject {
                    put("contactIds", buildJsonArray { add(userId) })
                },
            )
        return payload["contacts"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.let { parseUser(it) }
    }

    /** Public search by name — returns openable chats/channels (each result wraps a `chat`). */
    override suspend fun searchChats(query: String): List<SearchResult> {
        val payload =
            transport.request(
                OP_PUBLIC_SEARCH,
                buildJsonObject {
                    put("query", query)
                    put("count", 20)
                    put("type", "ALL")
                },
            )
        return payload["result"]
            ?.jsonArray
            .orEmptyList()
            .mapNotNull { el ->
                val chat = el.jsonObject["chat"]?.jsonObject ?: return@mapNotNull null
                val id = chat["id"]?.jsonPrimitive?.longOrNullSafe() ?: return@mapNotNull null
                val type = chat["type"]?.jsonPrimitive?.contentOrNullSafe()
                val title = chat["title"]?.jsonPrimitive?.contentOrNullSafe()?.ifBlank { null } ?: "Чат $id"
                SearchResult(chatId = id, title = title, avatarUrl = chat.avatarUrl(), isDialog = type == "DIALOG")
            }.distinctBy { it.chatId }
    }

    // ---------------------------------------------------------------------
    // Messages
    // ---------------------------------------------------------------------

    /** Loads recent messages for [chatId], oldest-first. */
    override suspend fun fetchHistory(
        chatId: Long,
        fromTime: Long,
        count: Int,
    ): List<Message> {
        val payload =
            transport.request(
                OP_FETCH_HISTORY,
                buildJsonObject {
                    put("chatId", chatId)
                    put("from", fromTime)
                    put("forward", 0)
                    put("backward", count)
                    put("getMessages", true)
                },
            )
        return payload["messages"]
            ?.jsonArray
            .orEmptyList()
            .mapNotNull { parseMessage(it.jsonObject, chatId) }
            .sortedBy { it.time }
    }

    /** Sends [text] to [chatId], optionally replying to [replyToId]. Returns the echo, or null. */
    override suspend fun sendMessage(
        chatId: Long,
        text: String,
        cid: Long,
        replyToId: String?,
        attaches: List<OutAttach>,
    ): Message? {
        val payload =
            transport.request(
                OP_SEND_MESSAGE,
                buildJsonObject {
                    put("chatId", chatId)
                    putJsonObject("message") {
                        put("text", text)
                        put("cid", cid)
                        put("elements", buildJsonArrayEmpty())
                        putJsonArray("attaches") {
                            attaches.forEach { a ->
                                when (a) {
                                    is OutAttach.Photo ->
                                        addJsonObject {
                                            put("_type", "PHOTO")
                                            put("photoToken", a.token)
                                        }
                                    is OutAttach.Video ->
                                        addJsonObject {
                                            put("_type", "VIDEO")
                                            put("videoId", a.videoId)
                                            put("token", a.token)
                                        }
                                    is OutAttach.File ->
                                        addJsonObject {
                                            put("_type", "FILE")
                                            put("fileId", a.fileId)
                                        }
                                }
                            }
                        }
                        // A reply carries a link: {type:"REPLY", messageId} (per maxplus).
                        // messageId MUST be a NUMBER — sending it as a string makes the
                        // server error out and drop the connection (same as markRead).
                        val replyMid = replyToId?.toLongOrNull()
                        if (replyMid != null) {
                            putJsonObject("link") {
                                put("type", "REPLY")
                                put("messageId", replyMid)
                            }
                        } else {
                            put("link", kotlinx.serialization.json.JsonNull)
                        }
                    }
                    put("notify", true)
                },
            )
        // A closed/forbidden chat replies with an error payload whose `message` is the
        // error TEXT (a string), not a message object. Guard the cast (it would
        // otherwise throw "JsonLiteral is not a JsonObject") and surface the server's
        // message cleanly so the UI shows e.g. "Чат закрыт" instead of a raw exception.
        val msgObj = payload["message"] as? JsonObject
        if (msgObj == null) {
            if (payload["error"] != null || payload["message"] != null) {
                error(payload.serverMessage("Не удалось отправить сообщение"))
            }
            return null
        }
        return parseMessage(msgObj, chatId)
    }

    override suspend fun uploadPhoto(
        bytes: ByteArray,
        fileName: String,
        mime: String,
    ): OutAttach.Photo {
        // 1) Ask the server for an upload URL (it embeds the photoId in its query).
        val data = transport.request(OP_PHOTO_UPLOAD, buildJsonObject { put("count", 1) })
        val url =
            data["url"]?.jsonPrimitive?.contentOrNullSafe()
                ?: error(data.serverMessage("Не удалось получить ссылку для загрузки"))
        // 2) Multipart-upload the bytes. Response: {photos:{<photoId>:{token}}}. We
        // requested a single photo, so take the one entry rather than matching by id
        // (the URL's photoId is URL-encoded but the JSON key is decoded).
        val ext = fileName.substringAfterLast('.', "").ifBlank { if (mime.endsWith("png")) "png" else "jpg" }
        val response =
            http.submitFormWithBinaryData(
                url = url,
                formData =
                    formData {
                        append(
                            "file",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, mime)
                                append(HttpHeaders.ContentDisposition, "filename=\"image.$ext\"")
                            },
                        )
                    },
            )
        val token =
            Json
                .parseToJsonElement(response.bodyAsText())
                .jsonObject["photos"]
                ?.jsonObject
                ?.values
                ?.firstOrNull()
                ?.jsonObject
                ?.get("token")
                ?.jsonPrimitive
                ?.contentOrNullSafe()
                ?: error("Сервер не вернул токен загруженного фото")
        return OutAttach.Photo(token)
    }

    override suspend fun uploadVideo(
        bytes: ByteArray,
        fileName: String,
        mime: String,
    ): OutAttach.Video {
        // 1) Request an upload slot: {info:[{url, videoId, token}]}.
        val data = transport.request(OP_VIDEO_UPLOAD, buildJsonObject { put("count", 1) })
        val info =
            data["info"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: error(data.serverMessage("Не удалось начать загрузку видео"))
        val url = info["url"]?.jsonPrimitive?.contentOrNullSafe() ?: error("Нет ссылки для загрузки видео")
        val videoId = info["videoId"]?.jsonPrimitive?.longOrNullSafe() ?: error("Сервер не вернул videoId")
        val token = info["token"]?.jsonPrimitive?.contentOrNullSafe() ?: error("Сервер не вернул токен видео")
        // 2) Upload the bytes (single range covering the whole file).
        val response =
            http.post(url) {
                headers {
                    append(HttpHeaders.ContentDisposition, "attachment; filename=\"$fileName\"")
                    append(HttpHeaders.ContentRange, "0-${bytes.size - 1}/${bytes.size}")
                }
                setBody(bytes)
            }
        if (!response.status.isSuccess()) error("Загрузка видео не удалась (${response.status.value})")
        // 3) Wait for the server to finish processing (NOTIF_ATTACH with our videoId).
        withTimeoutOrNull(60_000) { videoReadyFlow.first { it == videoId } }
            ?: error("Видео не было обработано сервером вовремя")
        return OutAttach.Video(videoId, token)
    }

    override suspend fun uploadFile(
        bytes: ByteArray,
        fileName: String,
        mime: String,
    ): OutAttach.File {
        // Mirrors uploadVideo: {info:[{url, fileId, token}]} -> POST bytes -> await ready.
        val data = transport.request(OP_FILE_UPLOAD, buildJsonObject { put("count", 1) })
        val info =
            data["info"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: error(data.serverMessage("Не удалось начать загрузку файла"))
        val url = info["url"]?.jsonPrimitive?.contentOrNullSafe() ?: error("Нет ссылки для загрузки файла")
        val fileId = info["fileId"]?.jsonPrimitive?.longOrNullSafe() ?: error("Сервер не вернул fileId")
        val response =
            http.post(url) {
                headers {
                    append(HttpHeaders.ContentDisposition, "attachment; filename=\"$fileName\"")
                    append(HttpHeaders.ContentRange, "0-${bytes.size - 1}/${bytes.size}")
                }
                setBody(bytes)
            }
        if (!response.status.isSuccess()) error("Загрузка файла не удалась (${response.status.value})")
        withTimeoutOrNull(60_000) { fileReadyFlow.first { it == fileId } }
            ?: error("Файл не был обработан сервером вовремя")
        return OutAttach.File(fileId)
    }

    override suspend fun setReaction(
        chatId: Long,
        messageId: String,
        emoji: String?,
    ) {
        val mid = messageId.toLongOrNull() ?: return
        // Add via MSG_REACTION (178) with {reactionType:"EMOJI", id}; remove via the
        // dedicated MSG_CANCEL_REACTION (179), which just takes {chatId, messageId}.
        if (emoji != null) {
            transport.request(
                OP_REACTION,
                buildJsonObject {
                    put("chatId", chatId)
                    put("messageId", mid)
                    putJsonObject("reaction") {
                        put("reactionType", "EMOJI")
                        put("id", emoji)
                    }
                },
            )
        } else {
            transport.request(
                OP_CANCEL_REACTION,
                buildJsonObject {
                    put("chatId", chatId)
                    put("messageId", mid)
                },
            )
        }
    }

    override suspend fun deleteChat(
        chatId: Long,
        lastEventTime: Long,
        forAll: Boolean,
    ) {
        // Deletes our copy of the chat. NB: Max does not support deleting a 1:1
        // dialog for the other party — even the official client can't (the server
        // ignores forAll for dialogs), so this only ever removes our own container.
        transport.request(
            OP_DELETE_CHAT,
            buildJsonObject {
                put("chatId", chatId)
                put("lastEventTime", lastEventTime)
                put("forAll", forAll)
            },
        )
    }

    override suspend fun leaveGroup(chatId: Long) {
        // CHAT_LEAVE payload is just {chatId}.
        transport.request(OP_LEAVE_CHAT, buildJsonObject { put("chatId", chatId) })
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun parseMessage(
        obj: JsonObject,
        chatId: Long,
    ): Message? {
        val baseText = localize(obj["text"]?.jsonPrimitive?.contentOrNullSafe() ?: "")
        val attaches = obj["attaches"]?.jsonArray.orEmptyList()

        // Images/videos we render inline (PHOTO baseUrl, VIDEO thumbnail).
        val media =
            attaches.mapNotNull { el ->
                val a = el.jsonObject
                val w = a["width"]?.jsonPrimitive?.intOrNullSafe() ?: 0
                val h = a["height"]?.jsonPrimitive?.intOrNullSafe() ?: 0
                when (a["_type"]?.jsonPrimitive?.contentOrNullSafe()) {
                    "PHOTO" ->
                        a["baseUrl"]
                            ?.jsonPrimitive
                            ?.contentOrNullSafe()
                            ?.let { MediaAttach(MediaType.PHOTO, it, w, h) }
                    "VIDEO" ->
                        a["thumbnail"]
                            ?.jsonPrimitive
                            ?.contentOrNullSafe()
                            ?.let { MediaAttach(MediaType.VIDEO, it, w, h, a["videoId"]?.jsonPrimitive?.longOrNullSafe() ?: 0L) }
                    else -> null
                }
            }
        // Non-renderable attaches still get a text label so they aren't invisible.
        // For SHARE we surface the actual URL/title so it renders as a tappable link.
        val mediaLabel =
            attaches.firstNotNullOfOrNull { el ->
                val a = el.jsonObject
                when (a["_type"]?.jsonPrimitive?.contentOrNullSafe()) {
                    "FILE" -> "📎 Файл"
                    "AUDIO" -> "🎵 Голосовое сообщение"
                    "SHARE" -> {
                        val url = (a["url"] ?: a["link"])?.jsonPrimitive?.contentOrNullSafe()
                        val shareTitle = a["title"]?.jsonPrimitive?.contentOrNullSafe()?.ifBlank { null }
                        listOfNotNull(shareTitle, url).joinToString("\n").ifBlank { "🔗 Ссылка" }
                    }
                    else -> null
                }
            }
        // System/service messages carry their text in a CONTROL attach.
        val controlText =
            attaches.firstNotNullOfOrNull { el ->
                val a = el.jsonObject
                if (a["_type"]?.jsonPrimitive?.contentOrNullSafe() == "CONTROL") {
                    (a["message"] ?: a["shortMessage"])?.jsonPrimitive?.contentOrNullSafe()
                } else {
                    null
                }
            }
        val text =
            buildString {
                append(if (baseText.isNotEmpty()) baseText else (controlText ?: ""))
                if (mediaLabel != null) {
                    if (isNotEmpty()) append('\n')
                    append(mediaLabel)
                }
            }
        val sender = obj["sender"]?.jsonPrimitive?.longOrNullSafe() ?: 0L
        val time = obj["time"]?.jsonPrimitive?.longOrNullSafe() ?: 0L
        val id = obj["id"]?.jsonPrimitive?.contentOrNullSafe()
        val cid = obj["cid"]?.jsonPrimitive?.longOrNullSafe()
        // Server `status`: 3 == READ (per the maxplus reference), otherwise treat as SENT.
        val status =
            when (obj["status"]?.jsonPrimitive?.intOrNullSafe()) {
                null -> MessageStatus.UNKNOWN
                3 -> MessageStatus.READ
                else -> MessageStatus.SENT
            }
        val reactions = parseReactions(obj["reactionInfo"]?.jsonObject)
        // A reply carries the quoted message inline under link.message (type REPLY).
        val replyTo =
            (obj["link"] as? JsonObject)
                ?.takeIf { it["type"]?.jsonPrimitive?.contentOrNullSafe() == "REPLY" }
                ?.get("message")
                ?.let { it as? JsonObject }
                ?.let { q ->
                    ReplyInfo(
                        senderId = q["sender"]?.jsonPrimitive?.longOrNullSafe() ?: 0L,
                        text = localize(q["text"]?.jsonPrimitive?.contentOrNullSafe() ?: "").ifBlank { "Вложение" },
                    )
                }
        // Skip empty service messages with no text, no media and no id.
        if (text.isEmpty() && media.isEmpty() && id == null) return null
        return Message(
            id = id,
            cid = cid,
            chatId = chatId,
            senderId = sender,
            text = text,
            time = time,
            status = status,
            media = media,
            reactions = reactions,
            replyTo = replyTo,
        )
    }

    /**
     * Parses `reactionInfo = {counters:[{reaction, count}], totalCount, yourReaction}`.
     * `reaction`/`yourReaction` may be a bare emoji string or an object with an `id`.
     */
    private fun parseReactions(info: JsonObject?): List<Reaction> {
        if (info == null) return emptyList()

        fun emojiOf(node: kotlinx.serialization.json.JsonElement?): String? =
            when (node) {
                is JsonObject -> node["id"]?.jsonPrimitive?.contentOrNullSafe() ?: node["reaction"]?.jsonPrimitive?.contentOrNullSafe()
                else -> node?.jsonPrimitive?.contentOrNullSafe()
            }
        val mine = emojiOf(info["yourReaction"])
        return info["counters"]
            ?.jsonArray
            .orEmptyList()
            .mapNotNull { el ->
                val c = el.jsonObject
                val emoji = emojiOf(c["reaction"]) ?: return@mapNotNull null
                val count = c["count"]?.jsonPrimitive?.intOrNullSafe() ?: 0
                Reaction(emoji, count, mine = emoji == mine)
            }
    }

    /** Tells the server we've read everything up to [messageId] in [chatId]. */
    override suspend fun markRead(
        chatId: Long,
        messageId: String,
        mark: Long,
    ) {
        // The server expects messageId as a NUMBER; sending it as a string makes
        // the server reply with an error AND drop the connection.
        val mid = messageId.toLongOrNull() ?: return
        transport.request(
            OP_MARK_READ,
            buildJsonObject {
                put("type", "READ_MESSAGE")
                put("chatId", chatId)
                put("messageId", mid)
                put("mark", mark)
            },
        )
    }
}

/** Extracts tokenAttrs.LOGIN.token if present. */
private fun JsonObject.loginToken(): String? =
    this["tokenAttrs"]
        ?.jsonObject
        ?.get("LOGIN")
        ?.jsonObject
        ?.get("token")
        ?.jsonPrimitive
        ?.contentOrNullSafe()

/** Extracts tokenAttrs.REGISTER.token if present (returned for unregistered phones). */
private fun JsonObject.registerToken(): String? =
    this["tokenAttrs"]
        ?.jsonObject
        ?.get("REGISTER")
        ?.jsonObject
        ?.get("token")
        ?.jsonPrimitive
        ?.contentOrNullSafe()

/** The id of the first server-offered preset avatar, if any. */
private fun JsonObject.firstPresetAvatarId(): Long? =
    this["presetAvatars"]
        ?.jsonArray
        ?.firstOrNull()
        ?.jsonObject
        ?.get("avatars")
        ?.jsonArray
        ?.firstOrNull()
        ?.jsonObject
        ?.get("id")
        ?.jsonPrimitive
        ?.longOrNullSafe()

/** Extracts the 2FA challenge trackId (passwordChallenge may be a string or an object). */
private fun JsonObject.passwordTrackId(): String? {
    val challenge = this["passwordChallenge"] ?: return null
    (challenge as? JsonObject)?.let { obj ->
        return (obj["trackId"] ?: obj["track_id"])?.jsonPrimitive?.contentOrNullSafe()
    }
    return challenge.jsonPrimitive.contentOrNullSafe()
}

/** Optional password hint from the passwordChallenge. */
private fun JsonObject.passwordHint(): String? = (this["passwordChallenge"] as? JsonObject)?.get("hint")?.jsonPrimitive?.contentOrNullSafe()

/** Maps the server's localization-key placeholders to Russian text. */
private val LOCALIZED_TEXT =
    mapOf(
        "welcome.saved.dialog.message" to "Добро пожаловать! Здесь хранятся ваши сохранённые сообщения.",
    )

private fun localize(text: String): String = LOCALIZED_TEXT[text] ?: text

/** Builds a human-readable message from a server error payload. */
private fun JsonObject.serverMessage(default: String): String {
    val localized = this["localizedMessage"]?.jsonPrimitive?.contentOrNullSafe()
    val error = this["error"]?.jsonPrimitive?.contentOrNullSafe()
    val message = this["message"]?.jsonPrimitive?.contentOrNullSafe()
    return localized ?: error ?: message ?: "$default. Ответ сервера: $this"
}

private fun buildJsonArrayEmpty() = kotlinx.serialization.json.buildJsonArray { }

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content

private fun kotlinx.serialization.json.JsonPrimitive.longOrNullSafe(): Long? = contentOrNullSafe()?.toLongOrNull()

private fun kotlinx.serialization.json.JsonPrimitive.intOrNullSafe(): Int? = contentOrNullSafe()?.toIntOrNull()

private fun kotlinx.serialization.json.JsonArray?.orEmptyList(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()

private fun JsonObject?.firstName(): String? =
    this
        ?.get("names")
        ?.jsonArray
        ?.firstOrNull()
        ?.jsonObject
        ?.get("firstName")
        ?.jsonPrimitive
        ?.contentOrNullSafe()

private fun JsonObject?.lastName(): String? =
    this
        ?.get("names")
        ?.jsonArray
        ?.firstOrNull()
        ?.jsonObject
        ?.get("lastName")
        ?.jsonPrimitive
        ?.contentOrNullSafe()

private fun JsonObject.displayName(): String? {
    val first = firstName()
    val last = lastName()
    return listOfNotNull(first, last).joinToString(" ").ifBlank { null }
}

/** A presence object means "online" when it carries a [status] (offline = only `seen`). */
private fun JsonObject.isOnline(): Boolean = (this["status"]?.jsonPrimitive?.intOrNullSafe() ?: 0) >= 1

/** Best available avatar URL from a user/contact/chat object. */
private fun JsonObject.avatarUrl(): String? =
    (this["baseRawUrl"] ?: this["baseUrl"] ?: this["baseRawIconUrl"] ?: this["baseIconUrl"])
        ?.jsonPrimitive
        ?.contentOrNullSafe()

/** Parses a server user/contact object into [UserInfo]. */
private fun parseUser(o: JsonObject): UserInfo? {
    val id = o["id"]?.jsonPrimitive?.longOrNullSafe() ?: return null
    return UserInfo(
        id = id,
        name = o.displayName() ?: "Пользователь $id",
        avatarUrl = o.avatarUrl(),
        description = o["description"]?.jsonPrimitive?.contentOrNullSafe(),
        phone = o["phone"]?.jsonPrimitive?.contentOrNullSafe(),
        gender = o["gender"]?.jsonPrimitive?.contentOrNullSafe(),
        link = o["link"]?.jsonPrimitive?.contentOrNullSafe(),
        country = o["country"]?.jsonPrimitive?.contentOrNullSafe(),
        registrationTime = o["registrationTime"]?.jsonPrimitive?.longOrNullSafe(),
    )
}
