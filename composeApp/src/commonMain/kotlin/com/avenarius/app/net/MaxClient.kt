package com.avenarius.app.net

import com.avenarius.app.model.Account
import com.avenarius.app.model.Chat
import com.avenarius.app.model.MediaAttach
import com.avenarius.app.model.MediaType
import com.avenarius.app.model.Message
import com.avenarius.app.model.MessageStatus
import com.avenarius.app.model.SearchResult
import com.avenarius.app.model.UserInfo
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
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
    ): Message?

    suspend fun markRead(
        chatId: Long,
        messageId: String,
        mark: Long,
    )

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
        private const val OP_MARK_READ = 50
        private const val OP_SEND_MESSAGE = 64
        private const val OP_CHECK_PASSWORD = 115
        const val OP_NEW_MESSAGE = 128
        const val OP_MARK_UPDATE = 130 // server push: read/delivery marks changed
        const val OP_PRESENCE = 132 // server push: a contact's online state changed
    }

    private val transport = MobileTransport()
    private val scope = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, _ -> })

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

    override val isConnected: Boolean get() = transport.isConnected

    /** Emitted when the connection drops unexpectedly (for auto-reconnect). */
    override val drops: SharedFlow<Unit> get() = transport.drops

    /** Preset avatar id to use when registering a new account (rumax's default). */
    private var registerPhotoId: Long = 2981369L

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

        // --- chats ---
        val chats =
            payload["chats"]
                ?.jsonArray
                .orEmptyList()
                .mapNotNull { el ->
                    val c = el.jsonObject
                    val id = c["id"]?.jsonPrimitive?.long ?: return@mapNotNull null
                    val type = c["type"]?.jsonPrimitive?.contentOrNullSafe()
                    val rawTitle = c["title"]?.jsonPrimitive?.contentOrNullSafe()
                    val lastMessage = c["lastMessage"]?.jsonObject
                    val lastText =
                        lastMessage
                            ?.get("text")
                            ?.jsonPrimitive
                            ?.contentOrNullSafe()
                            ?.let { localize(it) }
                    val lastTime = c["lastEventTime"]?.jsonPrimitive?.longOrNullSafe() ?: 0L
                    val unread = c["newMessages"]?.jsonPrimitive?.intOrNullSafe() ?: 0
                    val isDialog = type == "DIALOG"
                    // participants = {userId: lastReadMarkMs}; the other side's mark tells us
                    // how far they've read (so our messages up to it show ✓✓).
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
                    Chat(
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
                }.sortedByDescending { it.lastEventTime }

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

    /** Sends [text] to [chatId]. Returns the server-confirmed message, or null. */
    override suspend fun sendMessage(
        chatId: Long,
        text: String,
        cid: Long,
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
                        put("attaches", buildJsonArrayEmpty())
                        put("link", kotlinx.serialization.json.JsonNull)
                    }
                    put("notify", true)
                },
            )
        val msg = payload["message"]?.jsonObject ?: return null
        return parseMessage(msg, chatId)
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
        val mediaLabel =
            attaches.firstNotNullOfOrNull { el ->
                when (el.jsonObject["_type"]?.jsonPrimitive?.contentOrNullSafe()) {
                    "FILE" -> "📎 Файл"
                    "AUDIO" -> "🎵 Голосовое сообщение"
                    "SHARE" -> "🔗 Ссылка"
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
        )
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
