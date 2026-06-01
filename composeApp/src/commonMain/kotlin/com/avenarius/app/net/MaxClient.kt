package com.avenarius.app.net

import com.avenarius.app.model.Account
import com.avenarius.app.model.Chat
import com.avenarius.app.model.Message
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
        private const val OP_FETCH_HISTORY = 49
        private const val OP_SEND_MESSAGE = 64
        private const val OP_CHECK_PASSWORD = 115
        const val OP_NEW_MESSAGE = 128
    }

    private val transport = MobileTransport()
    private val scope = CoroutineScope(SupervisorJob())

    /** Temporary token from START_AUTH / a 2FA challenge, needed for the next step. */
    private var authToken: String? = null

    /** Stream of newly received messages (server push, opcode 128). */
    private val _incoming = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val incoming: SharedFlow<Message> = _incoming

    val isConnected: Boolean get() = transport.isConnected

    /** Result of submitting an SMS code: either logged in, or 2FA password needed. */
    sealed interface CodeResult {
        data class Success(val loginToken: String) : CodeResult
        data class NeedPassword(val trackId: String) : CodeResult
    }

    // ---------------------------------------------------------------------
    // Connection
    // ---------------------------------------------------------------------

    /** Opens the TLS connection and performs the opcode-6 ANDROID handshake. */
    suspend fun connect(deviceId: String, mtInstance: String) {
        if (transport.isConnected) return
        transport.connect()

        // Route server-pushed new-message frames into [incoming].
        scope.launch {
            transport.events.collect { (opcode, payload) ->
                if (opcode == OP_NEW_MESSAGE) {
                    val chatId = payload["chatId"]?.jsonPrimitive?.long
                    val msg = payload["message"]?.jsonObject
                    if (chatId != null && msg != null) parseMessage(msg, chatId)?.let { _incoming.emit(it) }
                }
            }
        }

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
        error(payload.serverMessage("Неверный код"))
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
    // Sync (chats + contacts + profile)
    // ---------------------------------------------------------------------

    data class SyncResult(val account: Account, val chats: List<Chat>)

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
            val lastText = lastMessage?.get("text")?.jsonPrimitive?.contentOrNullSafe()
            val lastTime = c["lastEventTime"]?.jsonPrimitive?.longOrNullSafe() ?: 0L

            val title = when {
                !rawTitle.isNullOrBlank() -> rawTitle
                type == "DIALOG" -> {
                    val other = c["participants"]?.jsonObject?.keys
                        ?.mapNotNull { it.toLongOrNull() }
                        ?.firstOrNull { it != myId }
                    other?.let { names[it] } ?: "Диалог $id"
                }
                else -> "Чат $id"
            }
            Chat(id = id, title = title, lastMessageText = lastText, lastEventTime = lastTime)
        }.sortedByDescending { it.lastEventTime }

        return SyncResult(account, chats)
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
        val text = obj["text"]?.jsonPrimitive?.contentOrNullSafe() ?: ""
        val sender = obj["sender"]?.jsonPrimitive?.longOrNullSafe() ?: 0L
        val time = obj["time"]?.jsonPrimitive?.longOrNullSafe() ?: 0L
        val id = obj["id"]?.jsonPrimitive?.contentOrNullSafe()
        val cid = obj["cid"]?.jsonPrimitive?.longOrNullSafe()
        // Skip empty service messages with no text and no id.
        if (text.isEmpty() && id == null) return null
        return Message(id = id, cid = cid, chatId = chatId, senderId = sender, text = text, time = time)
    }
}

/** Extracts tokenAttrs.LOGIN.token if present. */
private fun JsonObject.loginToken(): String? =
    this["tokenAttrs"]?.jsonObject?.get("LOGIN")?.jsonObject
        ?.get("token")?.jsonPrimitive?.contentOrNullSafe()

/** Extracts the 2FA challenge trackId (passwordChallenge may be a string or an object). */
private fun JsonObject.passwordTrackId(): String? {
    val challenge = this["passwordChallenge"] ?: return null
    (challenge as? JsonObject)?.let { obj ->
        return (obj["trackId"] ?: obj["track_id"])?.jsonPrimitive?.contentOrNullSafe()
    }
    return challenge.jsonPrimitive.contentOrNullSafe()
}

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
