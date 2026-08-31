package com.avenarius.app.net

import com.avenarius.app.model.Account
import com.avenarius.app.model.Chat
import com.avenarius.app.model.FileAttach
import com.avenarius.app.model.LinkPreview
import com.avenarius.app.model.MediaAttach
import com.avenarius.app.model.MediaContent
import com.avenarius.app.model.MediaType
import com.avenarius.app.model.Message
import com.avenarius.app.model.MessageStatus
import com.avenarius.app.model.OutAttach
import com.avenarius.app.model.Reaction
import com.avenarius.app.model.ReplyInfo
import com.avenarius.app.model.SearchResult
import com.avenarius.app.model.ServiceEvent
import com.avenarius.app.model.UserInfo
import com.avenarius.app.model.previewLabel
import com.avenarius.app.ui.nowMillis
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.onUpload
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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

/** A server push that [messageIds] were deleted in [chatId] (NOTIF_MSG_DELETE). */
data class MessageDeletion(
    val chatId: Long,
    val messageIds: List<String>,
)

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

    /** Messages the other party deleted (NOTIF_MSG_DELETE) — drop them from the chat. */
    val deletions: SharedFlow<MessageDeletion>
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

    /**
     * Uploads a photo and returns the attach descriptor. [profile] = an avatar upload.
     * [onProgress] is called with 0..1 as the body goes out, for a determinate
     * progress ring on the staged thumbnail.
     */
    suspend fun uploadPhoto(
        content: MediaContent,
        fileName: String,
        mime: String,
        profile: Boolean = false,
        onProgress: ((Float) -> Unit)? = null,
    ): OutAttach.Photo

    /** Forwards message [messageId] (from chat [fromChatId]) into chat [toChatId]. */
    suspend fun forwardMessage(
        toChatId: Long,
        messageId: String,
        fromChatId: Long,
        cid: Long,
    ): Message?

    /** Edits our own message [messageId] in [chatId], replacing its text. */
    suspend fun editMessage(
        chatId: Long,
        messageId: String,
        text: String,
    )

    /** Deletes [messageIds] in [chatId]. [forAll] = delete for everyone (only our own). */
    suspend fun deleteMessages(
        chatId: Long,
        messageIds: List<String>,
        forAll: Boolean,
    )

    /** Updates the signed-in user's profile (name, bio, and optionally a new avatar). */
    suspend fun updateProfile(
        firstName: String,
        lastName: String?,
        description: String?,
        photoToken: String?,
    )

    /** Uploads a video (waits for server processing) and returns its attach descriptor. */
    suspend fun uploadVideo(
        content: MediaContent,
        fileName: String,
        mime: String,
        onProgress: ((Float) -> Unit)? = null,
    ): OutAttach.Video

    /** Uploads an arbitrary file (waits for server processing) and returns its attach. */
    suspend fun uploadFile(
        content: MediaContent,
        fileName: String,
        mime: String,
        onProgress: ((Float) -> Unit)? = null,
    ): OutAttach.File

    suspend fun markRead(
        chatId: Long,
        messageId: String,
        mark: Long,
    )

    /**
     * Sends the `HOST_REACHABILITY` analytics event the official client emits when it
     * comes to the foreground. The official app probes a set of hosts (DNS + TCP),
     * the cellular operator, the connection type, the public IP and the VPN state and
     * reports all of it. Avenarius answers the command — so the server sees the same
     * signal real clients send and is less likely to flag us — but, being a privacy
     * client, it does NOT actually probe anything and NEVER includes the public IP or
     * VPN state (both are optional in the official payload). Fire-and-forget.
     */
    suspend fun reportHostReachability()

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

    /** Approves a web/desktop login by the token scanned from its QR code. */
    suspend fun approveQrLogin(qrLink: String)

    /** Returns the members of a group/channel chat (with names + avatars). */
    suspend fun getChatMembers(chatId: Long): List<UserInfo>

    /** Adds [userIds] to a group/channel chat as regular members. */
    suspend fun addMembers(
        chatId: Long,
        userIds: List<Long>,
    )

    /** Removes [userId] from a group/channel chat. */
    suspend fun removeMember(
        chatId: Long,
        userId: Long,
    )

    /** Grants or revokes admin rights for [userId] in a group/channel chat. */
    suspend fun setAdmin(
        chatId: Long,
        userId: Long,
        admin: Boolean,
    )

    /**
     * Creates a new group with [title] and [memberIds] (optionally a [photoToken]
     * from [uploadPhoto]). Returns the new chat's id if the server provided it.
     */
    suspend fun createGroup(
        title: String,
        memberIds: List<Long>,
        photoToken: String?,
    ): Long?

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

    /** Resolves a temporary download URL for a file attachment (FILE_DOWNLOAD). */
    suspend fun getFileUrl(
        chatId: Long,
        messageId: Long,
        fileId: Long,
    ): String?
}

/**
 * A file name fit for the upload's `Content-Disposition`.
 *
 * The value goes into the header unquoted, exactly as the official client sends it:
 * the server stores whatever follows `filename=`, so quoting it once made every
 * uploaded document arrive called `"doc.pdf"` — quotes and all — which in turn hid
 * its extension from the phone when opening it. Quotes and line breaks are stripped
 * (the latter would also let a name inject headers).
 */
internal fun headerFileName(fileName: String): String =
    fileName
        // Cut at a line break rather than deleting it: that both stops a name from
        // injecting a header and leaves something sensible behind.
        .substringBefore('\r')
        .substringBefore('\n')
        .replace("\"", "")
        .trim()
        .ifBlank { "file" }

/**
 * Cleans a file name received from the server. Older uploads (ours included) were
 * labelled with a quoted `filename=`, and those quotes became part of the stored
 * name — so they are trimmed off here rather than reaching the UI, the saved file's
 * name, or the extension that decides which app opens it.
 */
internal fun String.cleanFileName(): String = trim().removeSurrounding("\"").trim()

/** [mime] as a content type, falling back to octet-stream if it doesn't parse. */
private fun contentTypeOf(mime: String): ContentType =
    runCatching { ContentType.parse(mime) }.getOrDefault(ContentType.Application.OctetStream)

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

        // Only reached when a content provider doesn't report a size, so the upload
        // has to be measured before it can be sent. Bounded so that path can't OOM.
        private const val MAX_BUFFERED_UPLOAD_BYTES = 64 * 1024 * 1024

        // Upload budgets: total per request, and the allowed gap between packets.
        private const val UPLOAD_REQUEST_TIMEOUT_MS = 10 * 60 * 1000L
        private const val UPLOAD_SOCKET_TIMEOUT_MS = 2 * 60 * 1000L

        // Opcodes (verified against PyMax protocol enums and rumax).
        private const val OP_HANDSHAKE = 6
        private const val OP_LOG = 5 // LOG: batched analytics events (ru.ok.tamtam.api.d.LOG)
        private const val OP_PROFILE = 16 // PROFILE: update own profile (name/bio/avatar)
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
        private const val OP_FILE_DOWNLOAD = 88 // FILE_DOWNLOAD: get a file's download URL
        private const val OP_MARK_READ = 50
        private const val OP_SEND_MESSAGE = 64
        private const val OP_CHECK_PASSWORD = 115
        private const val OP_AUTH_QR_APPROVE = 290 // AUTH_QR_APPROVE: confirm a web/desktop login QR
        const val OP_NEW_MESSAGE = 128
        const val OP_MARK_UPDATE = 130 // server push: read/delivery marks changed
        const val OP_PRESENCE = 132 // server push: a contact's online state changed
        const val OP_REACTION_UPDATE = 155 // NOTIF_MSG_REACTIONS_CHANGED push
        const val OP_NOTIF_CHAT = 135 // NOTIF_CHAT push: a chat was created/updated (e.g. added to a group)
        const val OP_NOTIF_MSG_DELETE = 142 // NOTIF_MSG_DELETE push: the other party deleted a message
        const val OP_NOTIF_ATTACH = 136 // NOTIF_ATTACH push: an uploaded video/file finished processing

        // Confirmed from the official client's opcode enum (ru.ok.tamtam.api.d,
        // recovered by decompiling MAX.apk).
        private const val OP_REACTION = 178 // MSG_REACTION: add/set a reaction
        private const val OP_CANCEL_REACTION = 179 // MSG_CANCEL_REACTION: remove our reaction
        private const val OP_DELETE_CHAT = 52 // CHAT_DELETE
        private const val OP_LEAVE_CHAT = 58 // CHAT_LEAVE
        private const val OP_CHAT_MEMBERS = 59 // CHAT_MEMBERS: paginated member list
        private const val OP_CHAT_MEMBERS_UPDATE = 77 // CHAT_MEMBERS_UPDATE: add/remove members
        private const val OP_MSG_DELETE = 66 // MSG_DELETE
        private const val OP_MSG_EDIT = 67 // MSG_EDIT
    }

    private val transport = MobileTransport()
    private val scope = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, _ -> })

    // Plain HTTP client for media upload/download (separate from the raw-socket
    // protocol transport). Uses the platform Ktor engine (OkHttp on Android).
    private val http by lazy {
        HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 60_000
                requestTimeoutMillis = 60_000
            }
        }
    }

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

    /** Stream of message deletions by the other party (server push, opcode 142). */
    private val _deletions = MutableSharedFlow<MessageDeletion>(extraBufferCapacity = 64)
    override val deletions: SharedFlow<MessageDeletion> = _deletions

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
                    OP_NOTIF_MSG_DELETE -> {
                        // The chat id is nested under `chat` (no top-level chatId); messageIds is top-level.
                        val chatId =
                            payload["chatId"]?.jsonPrimitive?.longOrNullSafe()
                                ?: payload["chat"]
                                    ?.jsonObject
                                    ?.get("id")
                                    ?.jsonPrimitive
                                    ?.longOrNullSafe()
                        val ids =
                            buildList {
                                payload["messageIds"]
                                    ?.jsonArray
                                    ?.forEach { it.jsonPrimitive.longOrNullSafe()?.let { id -> add(id.toString()) } }
                                payload["messageId"]?.jsonPrimitive?.longOrNullSafe()?.let { add(it.toString()) }
                            }
                        if (chatId != null && ids.isNotEmpty()) _deletions.emit(MessageDeletion(chatId, ids))
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
        // Parsed rather than read straight off `text`, so a photo/file/voice message
        // gets a label instead of leaving the row blank (or, worse, showing whatever
        // text came before it).
        val lastText =
            (c["lastMessage"] as? JsonObject)
                ?.let { parseMessage(it, id) }
                ?.previewLabel()
                ?.ifBlank { null }
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
        val isChannel = type == "CHANNEL"
        val ownerId = c["ownerId"]?.jsonPrimitive?.longOrNullSafe() ?: 0L
        // adminParticipants = {adminUserId: adminInfo}; we only need the ids.
        val adminIds =
            c["adminParticipants"]
                ?.jsonObject
                ?.keys
                ?.mapNotNull { it.toLongOrNull() }
                ?.toSet() ?: emptySet()
        val memberIds =
            c["participants"]
                ?.jsonObject
                ?.keys
                ?.mapNotNull { it.toLongOrNull() }
                ?.toSet() ?: emptySet()
        val participantsCount = c["participantsCount"]?.jsonPrimitive?.intOrNullSafe() ?: memberIds.size
        val link = c["link"]?.jsonPrimitive?.contentOrNullSafe()?.ifBlank { null }
        // `options` is a set of flag strings (e.g. ONLY_ADMIN_CAN_ADD_MEMBER); accept
        // either an array or an object whose truthy keys are the flags.
        val options =
            when (val o = c["options"]) {
                is JsonArray -> o.mapNotNull { it.jsonPrimitive.contentOrNullSafe() }.toSet()
                is JsonObject -> o.keys
                else -> emptySet()
            }
        val amAdmin = myId == ownerId || myId in adminIds
        // In a CHANNEL only the owner/admins post; regular groups (CHAT) allow any
        // member; dialogs are always writable.
        val canWrite =
            when {
                isDialog -> true
                isChannel -> amAdmin
                else -> true
            }
        val canAddMembers =
            when {
                isDialog -> false
                isChannel -> amAdmin
                options.contains("ONLY_ADMIN_CAN_ADD_MEMBER") -> amAdmin
                else -> true
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
            isChannel = isChannel,
            ownerId = ownerId,
            adminIds = adminIds,
            memberIds = memberIds,
            participantsCount = participantsCount,
            link = link,
            canWrite = canWrite,
            canAddMembers = canAddMembers,
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

    override suspend fun getFileUrl(
        chatId: Long,
        messageId: Long,
        fileId: Long,
    ): String? {
        val payload =
            transport.request(
                OP_FILE_DOWNLOAD,
                buildJsonObject {
                    put("chatId", chatId)
                    put("messageId", messageId)
                    put("fileId", fileId)
                },
            )
        return payload["url"]?.jsonPrimitive?.contentOrNullSafe()
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

        // Answer the server's host-reachability expectation once the session is live.
        // Sync runs on every (re)connect and pull-to-refresh, which is a good proxy
        // for the "came to foreground" moment the official client uses. Fire-and-forget
        // on our own scope so it never delays the sync result.
        scope.launch { runCatching { reportHostReachability() } }

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

    override suspend fun forwardMessage(
        toChatId: Long,
        messageId: String,
        fromChatId: Long,
        cid: Long,
    ): Message? {
        val mid = messageId.toLongOrNull() ?: return null
        val payload =
            transport.request(
                OP_SEND_MESSAGE,
                buildJsonObject {
                    put("chatId", toChatId)
                    putJsonObject("message") {
                        put("text", "")
                        put("cid", cid)
                        put("elements", buildJsonArrayEmpty())
                        put("attaches", buildJsonArrayEmpty())
                        // A forward references the source message: {type:"FORWARD", messageId, chatId}.
                        putJsonObject("link") {
                            put("type", "FORWARD")
                            put("messageId", mid)
                            put("chatId", fromChatId)
                        }
                    }
                    put("notify", true)
                },
            )
        val msgObj = payload["message"] as? JsonObject
        if (msgObj == null) {
            if (payload["error"] != null || payload["message"] != null) {
                error(payload.serverMessage("Не удалось переслать сообщение"))
            }
            return null
        }
        return parseMessage(msgObj, toChatId)
    }

    override suspend fun editMessage(
        chatId: Long,
        messageId: String,
        text: String,
    ) {
        // MSG_EDIT (op 67): {chatId, messageId (numeric), text} (official app: aic).
        val mid = messageId.toLongOrNull() ?: return
        val payload =
            transport.request(
                OP_MSG_EDIT,
                buildJsonObject {
                    put("chatId", chatId)
                    put("messageId", mid)
                    put("text", text)
                },
            )
        if ((payload["message"] as? JsonObject) == null && payload["error"] != null) {
            error(payload.serverMessage("Не удалось изменить сообщение"))
        }
    }

    override suspend fun deleteMessages(
        chatId: Long,
        messageIds: List<String>,
        forAll: Boolean,
    ) {
        // MSG_DELETE (op 66): {chatId, messageIds:[numeric], forMe, itemType} (official app: rhc).
        val ids = messageIds.mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) return
        transport.request(
            OP_MSG_DELETE,
            buildJsonObject {
                put("chatId", chatId)
                putJsonArray("messageIds") { ids.forEach { add(it) } }
                put("forMe", !forAll)
                put("itemType", "REGULAR")
            },
        )
    }

    override suspend fun uploadPhoto(
        content: MediaContent,
        fileName: String,
        mime: String,
        profile: Boolean,
        onProgress: ((Float) -> Unit)?,
    ): OutAttach.Photo {
        // 1) Ask the server for an upload URL (it embeds the photoId in its query).
        val data =
            transport.request(
                OP_PHOTO_UPLOAD,
                buildJsonObject {
                    put("count", 1)
                    if (profile) put("profile", true)
                },
            )
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
                        // Streamed from the source: a photo is never held in the heap.
                        append(
                            "file",
                            ChannelProvider(content.size.takeIf { it >= 0 }) { content.openChannel() },
                            Headers.build {
                                append(HttpHeaders.ContentType, mime)
                                append(HttpHeaders.ContentDisposition, "filename=\"image.$ext\"")
                            },
                        )
                    },
            ) {
                applyUploadTimeouts()
                reportProgress(onProgress)
            }
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

    override suspend fun updateProfile(
        firstName: String,
        lastName: String?,
        description: String?,
        photoToken: String?,
    ) {
        val payload =
            transport.request(
                OP_PROFILE,
                buildJsonObject {
                    put("firstName", firstName)
                    put("lastName", lastName ?: "")
                    put("description", description ?: "")
                    if (photoToken != null) {
                        put("photoToken", photoToken)
                        put("avatarType", "USER_AVATAR")
                    }
                },
            )
        if (payload["profile"] == null && payload["error"] != null) {
            error(payload.serverMessage("Не удалось обновить профиль"))
        }
    }

    override suspend fun uploadVideo(
        content: MediaContent,
        fileName: String,
        mime: String,
        onProgress: ((Float) -> Unit)?,
    ): OutAttach.Video {
        // 1) Request an upload slot: {info:[{url, videoId, token}]}.
        val data = transport.request(OP_VIDEO_UPLOAD, buildJsonObject { put("count", 1) })
        val info =
            data["info"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: error(data.serverMessage("Не удалось начать загрузку видео"))
        val url = info["url"]?.jsonPrimitive?.contentOrNullSafe() ?: error("Нет ссылки для загрузки видео")
        val videoId = info["videoId"]?.jsonPrimitive?.longOrNullSafe() ?: error("Сервер не вернул videoId")
        val token = info["token"]?.jsonPrimitive?.contentOrNullSafe() ?: error("Сервер не вернул токен видео")
        // 2) Stream the content up (single range covering the whole file).
        val response = putContent(url, fileName, mime, content, onProgress)
        if (!response.status.isSuccess()) error("Загрузка видео не удалась (${response.status.value})")
        // 3) Wait for the server to finish processing (NOTIF_ATTACH with our videoId).
        withTimeoutOrNull(60_000) { videoReadyFlow.first { it == videoId } }
            ?: error("Видео не было обработано сервером вовремя")
        return OutAttach.Video(videoId, token)
    }

    /**
     * Uploads run far longer than an ordinary call: a big body takes a while to go
     * out, and the CDN can be slow to answer once the last byte is in. The client's
     * default 60s budget would cut those off ("socket timeout has expired"), which
     * is why every upload request widens it.
     */
    private fun HttpRequestBuilder.applyUploadTimeouts() {
        timeout {
            requestTimeoutMillis = UPLOAD_REQUEST_TIMEOUT_MS
            socketTimeoutMillis = UPLOAD_SOCKET_TIMEOUT_MS
        }
    }

    /** Forwards body-transfer progress as a 0..1 fraction, when the size is known. */
    private fun HttpRequestBuilder.reportProgress(onProgress: ((Float) -> Unit)?) {
        if (onProgress == null) return
        onUpload { sent, total ->
            if (total != null && total > 0) onProgress((sent.toFloat() / total).coerceIn(0f, 1f))
        }
    }

    /**
     * POSTs [content] to a CDN upload slot, streaming it rather than materialising
     * it in memory.
     *
     * The CDN wants a single Content-Range covering the whole file, which needs an
     * exact length up front. When the platform couldn't report a size, the content
     * is buffered first to measure it — capped, so an unknown-size monster fails
     * with a message instead of an OutOfMemoryError.
     */
    private suspend fun putContent(
        url: String,
        fileName: String,
        mime: String,
        content: MediaContent,
        onProgress: ((Float) -> Unit)? = null,
    ): HttpResponse {
        val declared = content.size
        val body: OutgoingContent =
            if (declared >= 0) {
                object : OutgoingContent.ReadChannelContent() {
                    override val contentType = contentTypeOf(mime)
                    override val contentLength = declared

                    override fun readFrom(): ByteReadChannel = content.openChannel()
                }
            } else {
                // Read one byte past the cap: enough to know the content is too big,
                // without pulling all of it in to find out.
                val buffered =
                    content
                        .openChannel()
                        .readRemaining(MAX_BUFFERED_UPLOAD_BYTES + 1L)
                        .readByteArray()
                if (buffered.size > MAX_BUFFERED_UPLOAD_BYTES) {
                    error("Файл слишком большой для отправки")
                }
                ByteArrayContent(buffered, contentTypeOf(mime))
            }
        val length = body.contentLength ?: 0L
        // The range header below can't describe an empty body, and the server has
        // nothing to store anyway.
        if (length <= 0L) error("Файл пуст")
        return http.post(url) {
            applyUploadTimeouts()
            reportProgress(onProgress)
            headers {
                append(HttpHeaders.ContentDisposition, "attachment; filename=${headerFileName(fileName)}")
                append(HttpHeaders.ContentRange, "0-${length - 1}/$length")
            }
            setBody(body)
        }
    }

    override suspend fun uploadFile(
        content: MediaContent,
        fileName: String,
        mime: String,
        onProgress: ((Float) -> Unit)?,
    ): OutAttach.File {
        // Mirrors uploadVideo: {info:[{url, fileId, token}]} -> POST bytes -> await ready.
        val data = transport.request(OP_FILE_UPLOAD, buildJsonObject { put("count", 1) })
        val info =
            data["info"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: error(data.serverMessage("Не удалось начать загрузку файла"))
        val url = info["url"]?.jsonPrimitive?.contentOrNullSafe() ?: error("Нет ссылки для загрузки файла")
        val fileId = info["fileId"]?.jsonPrimitive?.longOrNullSafe() ?: error("Сервер не вернул fileId")
        val response = putContent(url, fileName, mime, content, onProgress)
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

    override suspend fun approveQrLogin(qrLink: String) {
        // AUTH_QR_APPROVE (op 290): confirm a web/desktop login by the URL encoded in
        // its QR code. Payload is just {qrLink: <scanned string>} (official app: bi0).
        transport.request(OP_AUTH_QR_APPROVE, buildJsonObject { put("qrLink", qrLink) })
    }

    override suspend fun getChatMembers(chatId: Long): List<UserInfo> {
        // CHAT_MEMBERS (op 59): {chatId, count} -> {members:[{contact:{…}, presence, …}], marker}.
        val payload =
            transport.request(
                OP_CHAT_MEMBERS,
                buildJsonObject {
                    put("chatId", chatId)
                    put("count", 200)
                },
            )
        return payload["members"]
            ?.jsonArray
            .orEmptyList()
            .mapNotNull { el ->
                val member = el.jsonObject
                // Each member wraps a `contact` object with the user's id/name/avatar.
                (member["contact"]?.jsonObject ?: member)
                    .let { parseUser(it) }
            }
    }

    override suspend fun addMembers(
        chatId: Long,
        userIds: List<Long>,
    ) = membersUpdate(chatId, operation = "add", type = "MEMBER", userIds = userIds, showHistory = true)

    override suspend fun removeMember(
        chatId: Long,
        userId: Long,
    ) = membersUpdate(chatId, operation = "remove", type = "MEMBER", userIds = listOf(userId))

    override suspend fun setAdmin(
        chatId: Long,
        userId: Long,
        admin: Boolean,
    ) = membersUpdate(chatId, operation = if (admin) "add" else "remove", type = "ADMIN", userIds = listOf(userId))

    /**
     * CHAT_MEMBERS_UPDATE (op 77, official app: rd3). [operation] is "add"/"remove",
     * [type] is "MEMBER"/"ADMIN". Promoting to admin omits an explicit permission
     * bitmask, so the server assigns its default admin rights.
     */
    private suspend fun membersUpdate(
        chatId: Long,
        operation: String,
        type: String,
        userIds: List<Long>,
        showHistory: Boolean = false,
    ) {
        transport.request(
            OP_CHAT_MEMBERS_UPDATE,
            buildJsonObject {
                put("chatId", chatId)
                put("operation", operation)
                put("type", type)
                putJsonArray("userIds") { userIds.forEach { add(it) } }
                if (showHistory) put("showHistory", true)
            },
        )
    }

    override suspend fun createGroup(
        title: String,
        memberIds: List<Long>,
        photoToken: String?,
    ): Long? {
        // A new group is a MSG_SEND (op 64) with chatId=0 and a CONTROL attach whose
        // event is "new" (official app: vw4). The server creates the chat and echoes
        // the first (service) message; we read the new chatId off it.
        val payload =
            transport.request(
                OP_SEND_MESSAGE,
                buildJsonObject {
                    // NB: no chatId — the official client omits it entirely when creating
                    // (qjc only writes chatId when non-zero). Sending chatId:0 is rejected.
                    putJsonObject("message") {
                        put("text", "")
                        put("cid", nowMillis())
                        put("elements", buildJsonArrayEmpty())
                        putJsonArray("attaches") {
                            addJsonObject {
                                put("_type", "CONTROL")
                                put("event", "new")
                                put("chatType", "CHAT")
                                put("title", title)
                                putJsonArray("userIds") { memberIds.forEach { add(it) } }
                                if (photoToken != null) put("photoToken", photoToken)
                            }
                        }
                        put("link", kotlinx.serialization.json.JsonNull)
                    }
                    put("notify", true)
                },
            )
        val msgObj = payload["message"] as? JsonObject
        if (msgObj == null && (payload["error"] != null || payload["message"] != null)) {
            error(payload.serverMessage("Не удалось создать группу"))
        }
        // The new chatId may arrive on the echoed message or a `chat` object.
        return payload["chat"]
            ?.jsonObject
            ?.get("id")
            ?.jsonPrimitive
            ?.longOrNullSafe()
            ?: msgObj?.get("chatId")?.jsonPrimitive?.longOrNullSafe()
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun parseMessage(
        obj: JsonObject,
        chatId: Long,
    ): Message? {
        // A forwarded message carries its real content (text + attaches) inside
        // link.message; the outer object is empty. Parse content from there, but keep
        // the outer id/sender/time/status (sender = who forwarded it).
        val link = obj["link"] as? JsonObject
        val linkType = link?.get("type")?.jsonPrimitive?.contentOrNullSafe()
        val forwardObj = if (linkType == "FORWARD") link?.get("message") as? JsonObject else null
        val content = forwardObj ?: obj
        val forwardedFrom = forwardObj?.get("sender")?.jsonPrimitive?.longOrNullSafe()

        val baseText = localize(content["text"]?.jsonPrimitive?.contentOrNullSafe() ?: "")
        val attaches = content["attaches"]?.jsonArray.orEmptyList()
        // A SHARE attach is a link preview: {url, title, description, image:{url}}.
        val linkPreview =
            attaches.firstNotNullOfOrNull { el ->
                val a = el.jsonObject
                if (a["_type"]?.jsonPrimitive?.contentOrNullSafe() != "SHARE") {
                    null
                } else {
                    val u = (a["url"] ?: a["link"])?.jsonPrimitive?.contentOrNullSafe()
                    if (u.isNullOrBlank()) {
                        null
                    } else {
                        LinkPreview(
                            url = u,
                            title = a["title"]?.jsonPrimitive?.contentOrNullSafe()?.ifBlank { null },
                            description = a["description"]?.jsonPrimitive?.contentOrNullSafe()?.ifBlank { null },
                            imageUrl =
                                a["image"]
                                    ?.jsonObject
                                    ?.get("url")
                                    ?.jsonPrimitive
                                    ?.contentOrNullSafe(),
                        )
                    }
                }
            }

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
        // Downloadable file attachments (rendered as tappable rows, not text).
        val files =
            attaches.mapNotNull { el ->
                val a = el.jsonObject
                if (a["_type"]?.jsonPrimitive?.contentOrNullSafe() != "FILE") {
                    null
                } else {
                    val fileId = a["fileId"]?.jsonPrimitive?.longOrNullSafe() ?: return@mapNotNull null
                    FileAttach(
                        fileId = fileId,
                        name =
                            a["name"]
                                ?.jsonPrimitive
                                ?.contentOrNullSafe()
                                ?.cleanFileName()
                                ?.ifBlank { null }
                                ?: "Файл",
                        size = a["size"]?.jsonPrimitive?.longOrNullSafe() ?: 0L,
                    )
                }
            }
        // Non-renderable attaches still get a text label so they aren't invisible.
        // For SHARE we surface the actual URL/title so it renders as a tappable link.
        val mediaLabel =
            attaches.firstNotNullOfOrNull { el ->
                val a = el.jsonObject
                when (a["_type"]?.jsonPrimitive?.contentOrNullSafe()) {
                    "AUDIO" -> "🎵 Голосовое сообщение"
                    else -> null
                }
            }
        // System/service messages carry a CONTROL attach: an `event` (new/add/remove/
        // join/leave/title/…), the `userId` who acted, affected `userIds`, and maybe
        // a `title` or a ready-made `message`/`shortMessage` text.
        val control =
            attaches.firstNotNullOfOrNull { el ->
                val a = el.jsonObject
                if (a["_type"]?.jsonPrimitive?.contentOrNullSafe() == "CONTROL") a else null
            }
        val controlText = control?.let { (it["message"] ?: it["shortMessage"])?.jsonPrimitive?.contentOrNullSafe() }
        val service =
            control?.get("event")?.jsonPrimitive?.contentOrNullSafe()?.let { event ->
                ServiceEvent(
                    event = event,
                    actorId =
                        control["userId"]?.jsonPrimitive?.longOrNullSafe()
                            ?: obj["sender"]?.jsonPrimitive?.longOrNullSafe() ?: 0L,
                    userIds =
                        control["userIds"]
                            ?.jsonArray
                            .orEmptyList()
                            .mapNotNull { it.jsonPrimitive.longOrNullSafe() },
                    title = control["title"]?.jsonPrimitive?.contentOrNullSafe()?.ifBlank { null },
                    message = controlText?.ifBlank { null },
                )
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
        if (text.isEmpty() && media.isEmpty() && files.isEmpty() && linkPreview == null && service == null && id == null) {
            return null
        }
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
            files = files,
            forwardedFrom = forwardedFrom,
            linkPreview = linkPreview,
            service = service,
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

    /**
     * Emits a single `HOST_REACHABILITY` / `GET_HOST_REACHABILITY` analytics event,
     * matching the official client's opcode-5 LOG wire format:
     *   { events: [ { time, userId, type, event, params } ] }
     * (mirrors `defpackage.dv.g()` + `defpackage.p9a` in the decompiled APK).
     *
     * The `params` deliberately carry only a plausible, fabricated reachability
     * snapshot — we do NOT open any sockets, resolve DNS, read the cellular operator,
     * fetch the public IP, or inspect the VPN state. The official app reports the API
     * host plus gstatic.com, calls.okcdn.ru, gosuslugi.ru and mtalk.google.com with a
     * per-host status (0=unreachable, 1=DNS-only, 2=timeout, 3=fully reachable). We
     * report the hosts we'd genuinely expect to reach as 3, and mtalk.google.com as 1
     * (DNS resolves but no socket) — honest for a client without Google push, and a
     * less anomalous signal than claiming a Google connection we never make.
     * `operator` is "undefined" (what the official app sends without telephony) and
     * `ip`/`vpn` are omitted entirely (both optional in the official payload).
     */
    override suspend fun reportHostReachability() {
        if (!transport.isConnected) return
        val event =
            buildJsonObject {
                put("time", nowMillis())
                put("userId", myId)
                put("type", "HOST_REACHABILITY")
                put("event", "GET_HOST_REACHABILITY")
                putJsonObject("params") {
                    putJsonObject("hosts") {
                        put(transport.host, 3) // the API host we actually have a live TLS session to
                        put("gstatic.com", 3)
                        put("calls.okcdn.ru", 3)
                        put("gosuslugi.ru", 3)
                        put("mtalk.google.com", 1) // DNS-only: no Google push connection on this client
                    }
                    put("operator", "undefined")
                    put("connection_type", 2) // WIFI; generic, leaks nothing real
                }
            }
        val payload = buildJsonObject { putJsonArray("events") { add(event) } }
        runCatching { transport.notify(OP_LOG, payload) }
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
