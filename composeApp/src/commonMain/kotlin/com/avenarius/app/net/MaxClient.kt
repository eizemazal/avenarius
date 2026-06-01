package com.avenarius.app.net

import com.avenarius.app.model.Account
import com.avenarius.app.model.Chat
import com.avenarius.app.model.Message
import com.avenarius.app.model.MessageStatus
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

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
class MaxClient {

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
        private const val OP_CONTACT_UPDATE = 34
        private const val OP_CONTACT_BY_PHONE = 46
        private const val OP_FETCH_HISTORY = 49
        private const val OP_MARK_READ = 50
        private const val OP_SEND_MESSAGE = 64
        private const val OP_CHECK_PASSWORD = 115
        const val OP_NEW_MESSAGE = 128
        const val OP_MARK_UPDATE = 130 // server push: read/delivery marks changed
    }

    private val transport = MobileTransport()
    private val scope = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, _ -> })

    /** Temporary token from START_AUTH / a 2FA challenge, needed for the next step. */
    private var authToken: String? = null

    /** Stream of newly received messages (server push, opcode 128). */
    private val _incoming = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val incoming: SharedFlow<Message> = _incoming

    /** A read-mark update: [userId] has read everything up to [mark] (ms) in [chatId]. */
    data class ReadMark(val chatId: Long, val userId: Long, val mark: Long)

    /** Stream of read-mark updates (server push, opcode 130) — e.g. the other side read our messages. */
    private val _readMarks = MutableSharedFlow<ReadMark>(extraBufferCapacity = 64)
    val readMarks: SharedFlow<ReadMark> = _readMarks

    val isConnected: Boolean get() = transport.isConnected

    /** Preset avatar id to use when registering a new account (rumax's default). */
    private var registerPhotoId: Long = 2981369L

    /** Result of submitting an SMS code. */
    sealed interface CodeResult {
        data class Success(val loginToken: String) : CodeResult
        data class NeedPassword(val trackId: String) : CodeResult
        /** This phone has no account yet — registration (a name) is required. */
        data object NeedRegister : CodeResult
    }

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
                }
            }
        }
    }

    /** Opens the TLS connection and performs the opcode-6 ANDROID handshake. */
    suspend fun connect(deviceId: String, mtInstance: String) {
        if (transport.isConnected) return
        transport.connect()

        transport.request(OP_HANDSHAKE, buildJsonObject {
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
        })
        transport.startPing()
    }

    fun disconnect() = transport.disconnect()

    // ---------------------------------------------------------------------
    // Authentication
    // ---------------------------------------------------------------------

    /** Requests an SMS code. Returns the expected code length; throws on refusal. */
    suspend fun startAuth(phone: String): Int {
        val payload = transport.request(OP_START_AUTH, buildJsonObject {
            put("phone", phone)
            put("type", "START_AUTH")
            put("language", "ru")
        })
        authToken = payload["token"]?.jsonPrimitive?.contentOrNullSafe()
        if (authToken == null) error(payload.serverMessage("Не удалось отправить код"))
        return payload["codeLength"]?.jsonPrimitive?.int ?: 6
    }

    /** Submits the SMS code. Either logs in, or signals that a 2FA password is needed. */
    suspend fun checkCode(code: String): CodeResult {
        val token = authToken ?: error("Сначала запросите код")
        val payload = transport.request(OP_CHECK_CODE, buildJsonObject {
            put("token", token)
            put("verifyCode", code)
            put("authTokenType", "CHECK_CODE")
        })
        payload.loginToken()?.let { return CodeResult.Success(it) }
        payload.passwordTrackId()?.let { return CodeResult.NeedPassword(it) }
        // New, unregistered phone: the server returns a REGISTER token + preset avatars.
        payload.registerToken()?.let { regToken ->
            authToken = regToken
            payload.firstPresetAvatarId()?.let { registerPhotoId = it }
            return CodeResult.NeedRegister
        }
        error(payload.serverMessage("Неверный код"))
    }

    /** Completes registration of a new account with [firstName]. Returns the login token. */
    suspend fun register(firstName: String, lastName: String? = null): String {
        val payload = transport.request(OP_REGISTER, buildJsonObject {
            put("token", authToken) // the REGISTER token from checkCode — required
            put("firstName", firstName)
            put("lastName", lastName)
            put("photoId", registerPhotoId)
            put("avatarType", "PRESET_AVATAR")
            put("tokenType", "REGISTER")
        })
        // The login token may come back under tokenAttrs.LOGIN or as a top-level field.
        return payload.loginToken()
            ?: payload["token"]?.jsonPrimitive?.contentOrNullSafe()
            ?: error(payload.serverMessage("Не удалось зарегистрироваться"))
    }

    /** Submits the 2FA cloud password. Returns the login token; throws on failure. */
    suspend fun checkPassword(password: String, trackId: String): String {
        val payload = transport.request(OP_CHECK_PASSWORD, buildJsonObject {
            put("password", password)
            put("trackId", trackId)
        })
        return payload.loginToken() ?: error(payload.serverMessage("Неверный пароль"))
    }

    // ---------------------------------------------------------------------
    // Contacts / starting a dialog
    // ---------------------------------------------------------------------

    data class FoundUser(val userId: Long, val name: String)

    /** Looks up a Max user by phone number. Throws if not found. */
    suspend fun findByPhone(phone: String): FoundUser {
        val payload = transport.request(OP_CONTACT_BY_PHONE, buildJsonObject { put("phone", phone) })
        val contact = payload["contact"]?.jsonObject
            ?: error(payload.serverMessage("Пользователь Max с таким номером не найден"))
        val id = contact["id"]?.jsonPrimitive?.longOrNullSafe()
            ?: error("Пользователь Max с таким номером не найден")
        return FoundUser(id, contact.displayName() ?: phone)
    }

    /** Adds [userId] to the contact list under [firstName]. */
    suspend fun addContact(userId: Long, firstName: String) {
        transport.request(OP_CONTACT_UPDATE, buildJsonObject {
            put("contactId", userId)
            put("firstName", firstName)
            put("action", "ADD")
        })
    }

    /** The 1:1 dialog chat id between two users is the XOR of their ids. */
    fun dialogChatId(myId: Long, otherId: Long): Long = myId xor otherId

    // ---------------------------------------------------------------------
    // Sync (chats + contacts + profile)
    // ---------------------------------------------------------------------

    data class SyncResult(
        val account: Account,
        val chats: List<Chat>,
        /** userId -> display name, for avatars and sender labels. */
        val contacts: Map<Long, String>,
    )

    /** Authenticates the connection with [token] and pulls the chat list. */
    suspend fun sync(token: String): SyncResult {
        val payload = transport.request(OP_SYNC, buildJsonObject {
            put("interactive", true)
            put("token", token)
            put("chatsSync", 0)
            put("contactsSync", 0)
            put("presenceSync", 0)
            put("draftsSync", 0)
            put("chatsCount", 40)
        })

        payload["error"]?.let { error(payload.serverMessage("Ошибка синхронизации")) }

        // --- my profile ---
        val contact = payload["profile"]?.jsonObject?.get("contact")?.jsonObject
        val myId = contact?.get("id")?.jsonPrimitive?.long ?: 0L
        val account = Account(
            userId = myId,
            firstName = contact.firstName() ?: "Я",
            lastName = contact.lastName(),
        )

        // --- contacts: id -> display name, for naming 1:1 dialogs ---
        val names = mutableMapOf<Long, String>()
        payload["contacts"]?.jsonArray?.forEach { el ->
            val c = el.jsonObject
            val id = c["id"]?.jsonPrimitive?.long ?: return@forEach
            names[id] = c.displayName() ?: "Контакт $id"
        }

        // --- chats ---
        val chats = payload["chats"]?.jsonArray.orEmptyList().mapNotNull { el ->
            val c = el.jsonObject
            val id = c["id"]?.jsonPrimitive?.long ?: return@mapNotNull null
            val type = c["type"]?.jsonPrimitive?.contentOrNullSafe()
            val rawTitle = c["title"]?.jsonPrimitive?.contentOrNullSafe()
            val lastMessage = c["lastMessage"]?.jsonObject
            val lastText = lastMessage?.get("text")?.jsonPrimitive?.contentOrNullSafe()?.let { localize(it) }
            val lastTime = c["lastEventTime"]?.jsonPrimitive?.longOrNullSafe() ?: 0L
            val unread = c["newMessages"]?.jsonPrimitive?.intOrNullSafe() ?: 0
            val isDialog = type == "DIALOG"
            // participants = {userId: lastReadMarkMs}; the other side's mark tells us
            // how far they've read (so our messages up to it show ✓✓).
            val otherReadMark = c["participants"]?.jsonObject?.entries
                ?.filter { it.key.toLongOrNull() != null && it.key.toLong() != myId }
                ?.mapNotNull { it.value.jsonPrimitive.longOrNullSafe() }
                ?.maxOrNull() ?: 0L

            val title = when {
                !rawTitle.isNullOrBlank() -> rawTitle
                isDialog -> {
                    val other = c["participants"]?.jsonObject?.keys
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
                otherReadMark = otherReadMark,
            )
        }.sortedByDescending { it.lastEventTime }

        return SyncResult(account, chats, names)
    }

    // ---------------------------------------------------------------------
    // Messages
    // ---------------------------------------------------------------------

    /** Loads recent messages for [chatId], oldest-first. */
    suspend fun fetchHistory(chatId: Long, fromTime: Long, count: Int = 50): List<Message> {
        val payload = transport.request(OP_FETCH_HISTORY, buildJsonObject {
            put("chatId", chatId)
            put("from", fromTime)
            put("forward", 0)
            put("backward", count)
            put("getMessages", true)
        })
        return payload["messages"]?.jsonArray.orEmptyList()
            .mapNotNull { parseMessage(it.jsonObject, chatId) }
            .sortedBy { it.time }
    }

    /** Sends [text] to [chatId]. Returns the server-confirmed message, or null. */
    suspend fun sendMessage(chatId: Long, text: String, cid: Long): Message? {
        val payload = transport.request(OP_SEND_MESSAGE, buildJsonObject {
            put("chatId", chatId)
            putJsonObject("message") {
                put("text", text)
                put("cid", cid)
                put("elements", buildJsonArrayEmpty())
                put("attaches", buildJsonArrayEmpty())
                put("link", kotlinx.serialization.json.JsonNull)
            }
            put("notify", true)
        })
        val msg = payload["message"]?.jsonObject ?: return null
        return parseMessage(msg, chatId)
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun parseMessage(obj: JsonObject, chatId: Long): Message? {
        val baseText = localize(obj["text"]?.jsonPrimitive?.contentOrNullSafe() ?: "")
        val attaches = obj["attaches"]?.jsonArray.orEmptyList()
        // We don't render media, but we label it so the message isn't blank.
        val mediaLabel = attaches.firstNotNullOfOrNull { el ->
            when (el.jsonObject["_type"]?.jsonPrimitive?.contentOrNullSafe()) {
                "VIDEO" -> "🎬 Видео"
                "PHOTO" -> "🖼 Фото"
                "FILE" -> "📎 Файл"
                "AUDIO" -> "🎵 Голосовое сообщение"
                "SHARE" -> "🔗 Ссылка"
                else -> null
            }
        }
        // System/service messages carry their text in a CONTROL attach.
        val controlText = attaches.firstNotNullOfOrNull { el ->
            val a = el.jsonObject
            if (a["_type"]?.jsonPrimitive?.contentOrNullSafe() == "CONTROL") {
                (a["message"] ?: a["shortMessage"])?.jsonPrimitive?.contentOrNullSafe()
            } else null
        }
        val text = buildString {
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
        val status = when (obj["status"]?.jsonPrimitive?.intOrNullSafe()) {
            null -> MessageStatus.UNKNOWN
            3 -> MessageStatus.READ
            else -> MessageStatus.SENT
        }
        // Skip empty service messages with no text and no id.
        if (text.isEmpty() && id == null) return null
        return Message(id = id, cid = cid, chatId = chatId, senderId = sender, text = text, time = time, status = status)
    }

    /** Tells the server we've read everything up to [messageId] in [chatId]. */
    suspend fun markRead(chatId: Long, messageId: String, mark: Long) {
        // The server expects messageId as a NUMBER; sending it as a string makes
        // the server reply with an error AND drop the connection.
        val mid = messageId.toLongOrNull() ?: return
        transport.request(OP_MARK_READ, buildJsonObject {
            put("type", "READ_MESSAGE")
            put("chatId", chatId)
            put("messageId", mid)
            put("mark", mark)
        })
    }
}

/** Extracts tokenAttrs.LOGIN.token if present. */
private fun JsonObject.loginToken(): String? =
    this["tokenAttrs"]?.jsonObject?.get("LOGIN")?.jsonObject
        ?.get("token")?.jsonPrimitive?.contentOrNullSafe()

/** Extracts tokenAttrs.REGISTER.token if present (returned for unregistered phones). */
private fun JsonObject.registerToken(): String? =
    this["tokenAttrs"]?.jsonObject?.get("REGISTER")?.jsonObject
        ?.get("token")?.jsonPrimitive?.contentOrNullSafe()

/** The id of the first server-offered preset avatar, if any. */
private fun JsonObject.firstPresetAvatarId(): Long? =
    this["presetAvatars"]?.jsonArray?.firstOrNull()?.jsonObject
        ?.get("avatars")?.jsonArray?.firstOrNull()?.jsonObject
        ?.get("id")?.jsonPrimitive?.longOrNullSafe()

/** Extracts the 2FA challenge trackId (passwordChallenge may be a string or an object). */
private fun JsonObject.passwordTrackId(): String? {
    val challenge = this["passwordChallenge"] ?: return null
    (challenge as? JsonObject)?.let { obj ->
        return (obj["trackId"] ?: obj["track_id"])?.jsonPrimitive?.contentOrNullSafe()
    }
    return challenge.jsonPrimitive.contentOrNullSafe()
}

/** Maps the server's localization-key placeholders to Russian text. */
private val LOCALIZED_TEXT = mapOf(
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

private fun kotlinx.serialization.json.JsonPrimitive.longOrNullSafe(): Long? =
    contentOrNullSafe()?.toLongOrNull()

private fun kotlinx.serialization.json.JsonPrimitive.intOrNullSafe(): Int? =
    contentOrNullSafe()?.toIntOrNull()

private fun kotlinx.serialization.json.JsonArray?.orEmptyList(): List<kotlinx.serialization.json.JsonElement> =
    this ?: emptyList()

private fun JsonObject?.firstName(): String? =
    this?.get("names")?.jsonArray?.firstOrNull()?.jsonObject
        ?.get("firstName")?.jsonPrimitive?.contentOrNullSafe()

private fun JsonObject?.lastName(): String? =
    this?.get("names")?.jsonArray?.firstOrNull()?.jsonObject
        ?.get("lastName")?.jsonPrimitive?.contentOrNullSafe()

private fun JsonObject.displayName(): String? {
    val first = firstName()
    val last = lastName()
    return listOfNotNull(first, last).joinToString(" ").ifBlank { null }
}
