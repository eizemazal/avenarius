package com.avenarius.app.net

import com.avenarius.app.model.Account
import com.avenarius.app.model.BotButton
import com.avenarius.app.model.CallKind
import com.avenarius.app.model.CallSetup
import com.avenarius.app.model.Chat
import com.avenarius.app.model.ChatFolder
import com.avenarius.app.model.ContactCard
import com.avenarius.app.model.FileAttach
import com.avenarius.app.model.GeoPoint
import com.avenarius.app.model.IncomingCall
import com.avenarius.app.model.LinkPreview
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
import com.avenarius.app.model.SearchResult
import com.avenarius.app.model.ServiceEvent
import com.avenarius.app.model.Sticker
import com.avenarius.app.model.StickerSet
import com.avenarius.app.model.StickerShelf
import com.avenarius.app.model.TextSpan
import com.avenarius.app.model.UserInfo
import com.avenarius.app.model.VoiceAttach
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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

/**
 * Account privacy (CONFIG `settings.user`); the "who" fields are [ALL], [CONTACTS] or
 * [NOBODY] (the server takes only the first two for [searchByPhone]).
 */
data class Privacy(
    val searchByPhone: String = ALL,
    val incomingCall: String = ALL,
    val chatsInvite: String = ALL,
    /** Server `HIDDEN`: hide our online status. */
    val hideOnline: Boolean = false,
    val showReadMarks: Boolean = true,
) {
    companion object {
        const val ALL = "ALL"
        const val CONTACTS = "CONTACTS"

        /** What the official client writes for "nobody"; the server may answer `NOBODY` too. */
        const val NOBODY = "_NONE_"
    }
}

/**
 * A user's presence (sync reply or push, opcode 132). [status]: 1 online, 2 "recently",
 * 3 "long ago" (last seen hidden); absent means [seen] (epoch seconds) is exact.
 */
data class Presence(
    val userId: Long,
    val online: Boolean,
    val seen: Long? = null,
    val status: Int? = null,
)

/** [userId] is typing in [chatId] (server push NOTIF_TYPING, opcode 129). */
data class TypingEvent(
    val chatId: Long,
    val userId: Long,
)

/** A recovery code was sent to the account's e-mail (reply to AUTH_VERIFY_EMAIL). */
data class EmailCodeInfo(
    /** Digits in the code, when the server says. */
    val codeLength: Int?,
    /** How long before another code may be requested. */
    val resendAfterMs: Long,
)

/** State of the account's login password (2FA), from AUTH_2FA_DETAILS. Nulls = unknown. */
data class TwoFaDetails(
    val enabled: Boolean?,
    val hint: String?,
    /** Masked recovery e-mail, if one is linked. */
    val email: String?,
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
        /** Masked recovery e-mail, if the account has one (for the "forgot password" hint). */
        val email: String? = null,
    ) : CodeResult

    /** This phone has no account yet — registration (a name) is required. */
    data object NeedRegister : CodeResult

    /**
     * The code was accepted, but the server refuses to sign this device in until the
     * account has a login password — and none is set. It can only be created from a
     * device that is already signed in (the official client shows the same dead end).
     */
    data object PasswordRequiredNotSet : CodeResult
}

/** The server rejected our login token outright: the session is dead, re-login is required. */
class AuthRejectedException(
    message: String,
) : RuntimeException(message)

/**
 * The server won't sign this device in until the account has a login password (2FA).
 * [serverText] is the server's own wording, shown as a detail under our explanation.
 */
class PasswordSetupRequiredException(
    val serverText: String?,
) : RuntimeException(serverText ?: "Для входа нужен пароль")

/** The multi-step login (code → password) expired server-side; start over from the phone. */
class AuthSessionExpiredException(
    message: String,
) : RuntimeException(message)

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
    /** Last-seen info per user, for the chat header. */
    val presence: Map<Long, Presence> = emptyMap(),
    /** Privacy settings from the login reply's `config.user`; null if absent. */
    val privacy: Privacy? = null,
    /** The server's reaction picker (`config.server.reactions-menu`); empty if absent. */
    val reactionsMenu: List<String> = emptyList(),
    /** How many chats may be pinned, when the server's config says. */
    val pinLimit: Int? = null,
    /** Polls may be sent in 1:1 dialogs too (`config.server.polls-in-p2p-chats`). */
    val pollsInDialogs: Boolean = false,
)

/** A message found by search, with its chat when the server sent one along. */
data class MessageHit(
    val message: Message,
    val chat: Chat?,
)

/** A page of public search results; [marker] asks for the next one (null: no more). */
data class SearchPage(
    val results: List<SearchResult>,
    val marker: Long? = null,
)

/**
 * Chat folders as the server sent them (FOLDERS_GET, a FOLDERS_* reply or the NOTIF_FOLDERS push):
 * [folders] may be only the changed ones; [order], when present, is the full order.
 */
data class FoldersUpdate(
    val folders: List<ChatFolder>,
    val order: List<String>? = null,
)

/** These folders with [update]'s upserted, then put in (and cut down to) its order when it has one. */
fun List<ChatFolder>.applying(update: FoldersUpdate): List<ChatFolder> {
    val byId = associateBy { it.id } + update.folders.associateBy { it.id }
    return update.order?.mapNotNull { byId[it] } ?: byId.values.toList()
}

/**
 * The server's full folder order after [update]: its order when it sends one (system folders it lists by id
 * alone included), else this one with any new folder at the end.
 */
fun List<String>.applyingOrder(update: FoldersUpdate): List<String> =
    update.order ?: (this + update.folders.map { it.id }.filterNot { it in this })

/** This order with [a] and [b] trading places; every other id, system ones included, stays where it was. */
fun List<String>.swapping(
    a: String,
    b: String,
): List<String> =
    (this + listOf(a, b).filterNot { it in this }).map {
        when (it) {
            a -> b
            b -> a
            else -> it
        }
    }

/** A page of the black list: the users we could read, and how many entries the server sent ([received]). */
data class BlockedPage(
    val users: List<UserInfo>,
    val received: Int,
) {
    companion object {
        /** Entries per page, as the official client asks. */
        const val SIZE = 40
    }
}

/** One signed-in device from Settings → Устройства (SESSIONS_INFO). */
data class SessionInfo(
    /** Client / platform label, e.g. "MAX Android". */
    val client: String,
    /** Device and OS line. */
    val info: String,
    /** Where it signed in from (city, country, maybe the IP). */
    val location: String,
    /** Last activity, epoch ms (0 if unknown). */
    val time: Long,
    val current: Boolean,
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

    /** Someone started/kept typing in a chat (NOTIF_TYPING). Defaults to silent for fakes. */
    val typing: SharedFlow<TypingEvent> get() = MutableSharedFlow()

    /**
     * The profile-recovery web URL from the server's session-init (`recovery-url`), or null
     * until we've connected. The official client opens this for a restricted account; the
     * carrier verification happens on that page, not in this protocol.
     */
    val recoveryUrl: String? get() = null

    /** Tells the server we are typing in [chatId] (MSG_TYPING, opcode 65); best-effort. */
    suspend fun sendTyping(chatId: Long) = Unit

    /** The devices signed in to this account; empty where the mode has none (demo). */
    suspend fun getSessions(): List<SessionInfo> = emptyList()

    /** Signs out every other device; returns the new login token the server issues for this one. */
    suspend fun closeOtherSessions(): String? = null

    // --- Login password (2FA) management, mirroring the official client's Settings →
    // Безопасность → Пароль для входа. Every step is bound to a server-side "track"
    // (AUTH_CREATE_TRACK) that the final AUTH_SET_2FA commits. Defaults throw so the
    // demo/test fakes need not implement them.

    /** Opens a 2FA change track; returns its id. */
    suspend fun twoFaCreateTrack(): String = error("Недоступно в этом режиме")

    /** Current password state (enabled, hint, recovery e-mail); [trackId] optional. */
    suspend fun twoFaDetails(trackId: String?): TwoFaDetails = error("Недоступно в этом режиме")

    /** Server-side password rules check; throws with the reason if it is not acceptable. */
    suspend fun twoFaValidatePassword(
        trackId: String,
        password: String,
    ): Unit = error("Недоступно в этом режиме")

    suspend fun twoFaValidateHint(
        trackId: String,
        hint: String,
    ): Unit = error("Недоступно в этом режиме")

    /** Sends a verification code to [email]. */
    suspend fun twoFaVerifyEmail(
        trackId: String,
        email: String,
    ): Unit = error("Недоступно в этом режиме")

    /** Confirms the e-mail with the [code] it received. */
    suspend fun twoFaCheckEmail(
        trackId: String,
        code: String,
    ): Unit = error("Недоступно в этом режиме")

    /** Confirms the current password before changing/removing it; returns the track to use. */
    suspend fun twoFaCheckPassword(
        trackId: String,
        password: String,
    ): String = error("Недоступно в этом режиме")

    /** Commits a new/updated password (+ optional hint / verified e-mail) on [trackId]. */
    suspend fun twoFaSet(
        trackId: String,
        password: String,
        hint: String?,
        update: Boolean,
        withEmail: Boolean,
    ): Unit = error("Недоступно в этом режиме")

    /** Turns the login password off on [trackId] (a password-confirmed track). */
    suspend fun twoFaRemove(trackId: String): Unit = error("Недоступно в этом режиме")

    // --- Forgotten login password, during sign-in (the official client's "Забыли пароль?").
    // All on the password challenge's trackId: e-mail code → check it → new password (+hint)
    // → AUTH_LOGIN_RESTORE_PASSWORD, whose reply signs the user in.

    /** Sends a recovery code to the account's linked e-mail. */
    suspend fun restoreSendEmailCode(trackId: String): EmailCodeInfo = error("Недоступно в этом режиме")

    suspend fun restoreCheckEmailCode(
        trackId: String,
        code: String,
    ): Unit = error("Недоступно в этом режиме")

    /** Sets the new password (checked with [twoFaValidatePassword] first); returns the login token. */
    suspend fun restorePassword(
        trackId: String,
        password: String,
        hint: String?,
    ): String = error("Недоступно в этом режиме")

    /** A chat that was created or updated (NOTIF_CHAT) — upsert it into the list. */
    val chatUpdates: SharedFlow<Chat>

    /** Messages the other party deleted (NOTIF_MSG_DELETE) — drop them from the chat. */
    val deletions: SharedFlow<MessageDeletion>

    /** Inbound calls ringing (NOTIF_CALL_START) — show the incoming-call UI. */
    val incomingCalls: SharedFlow<IncomingCall>
    val drops: SharedFlow<Unit>
    val isConnected: Boolean

    suspend fun connect(
        deviceId: String,
        mtInstance: String,
    )

    fun disconnect()

    /**
     * STAGE 1 of placing a call: sends VIDEO_CHAT_START_ACTIVE for a 1:1 call with
     * [peerId] and returns the [CallSetup] (SFU endpoints + ICE credentials) parsed
     * from the reply. The caller then opens the media session (STAGE 2).
     */
    suspend fun startCall(
        peerId: Long,
        isVideo: Boolean,
    ): CallSetup

    /**
     * STAGE 1 of accepting an inbound call: the callee also sends VIDEO_CHAT_START_ACTIVE
     * for [conversationId] to obtain its own [CallSetup], then joins the SFU.
     */
    suspend fun acceptCall(
        conversationId: String,
        peerId: Long,
        isVideo: Boolean,
    ): CallSetup

    /**
     * Ends or declines the call [conversationId] on the Max socket. [reason] is one of
     * the official client's hangup types — HUNGUP (ended after connecting), CANCELED
     * (caller gave up before an answer), REJECTED (callee declined), MISSED — and is
     * what the other side sees; [peerId] is the other participant.
     */
    suspend fun hangupCall(
        conversationId: String,
        reason: String,
        peerId: Long?,
    )

    /** Saves privacy settings; returns what the server now holds. */
    suspend fun updatePrivacy(privacy: Privacy): Privacy = privacy

    /**
     * Pins [messageId] in [chatId] (null unpins) and returns the chat as the server now
     * has it, when it says. The default does nothing.
     */
    suspend fun pinMessage(
        chatId: Long,
        messageId: String?,
        notify: Boolean = false,
    ): Chat? = null

    /**
     * Messages of [chatId] carrying attaches of [types] (PHOTO, VIDEO, FILE, SHARE, AUDIO),
     * up to [count] older than message [anchor], newest included when [withAnchor]. Backs
     * the info pages' Медиа / Файлы / Ссылки / Голосовые tabs. The default has none.
     */
    suspend fun fetchChatMedia(
        chatId: Long,
        types: List<String>,
        anchor: String,
        count: Int,
        withAnchor: Boolean,
    ): List<Message> = emptyList()

    /** Blocks [userId] (they can't write, call or add us to chats) or unblocks them. The default does nothing. */
    suspend fun setBlocked(
        userId: Long,
        blocked: Boolean,
    ) = Unit

    /** Empties [chatId]'s history for us up to [lastEventTime] (the chat itself stays). The default does nothing. */
    suspend fun clearHistory(
        chatId: Long,
        lastEventTime: Long,
    ) = Unit

    /**
     * Marks [chatId] unread (the chat list's «Отметить непрочитанным»). [mark] is a time in
     * ms; what the server wants there was never measured. The default does nothing.
     */
    suspend fun markUnread(
        chatId: Long,
        mark: Long,
    ) = Unit

    /** Presses a bot's CALLBACK [button]; the bot answers with a message or a toast of its own. The default does nothing. */
    suspend fun pressBotButton(button: BotButton) = Unit

    /** Who reacted to [messageId] in [chatId], as (userId, emoji) pairs; empty where the mode has none. */
    suspend fun getReactionUsers(
        chatId: Long,
        messageId: String,
    ): List<Pair<Long, String>> = emptyList()

    /** Votes in [poll] of message [messageId] for [answerIds] (empty retracts); returns the poll as the server now counts it. */
    suspend fun votePoll(
        chatId: Long,
        messageId: String,
        poll: Poll,
        answerIds: List<Int>,
    ): Poll? = null

    /** A page of the black list from offset [from]: who we blocked, and how many entries the server sent. */
    suspend fun getBlockedPage(from: Int): BlockedPage = BlockedPage(emptyList(), 0)

    /** The user's sticker sets and recent stickers; empty where the mode has none. */
    suspend fun getStickerShelf(): StickerShelf = StickerShelf(emptyList(), emptyList())

    /** Stickers by id (their still images). */
    suspend fun getStickers(ids: List<Long>): List<Sticker> = emptyList()

    /** Folder changes pushed by the server (NOTIF_FOLDERS). Defaults to silent for fakes. */
    val folderUpdates: SharedFlow<FoldersUpdate> get() = MutableSharedFlow()

    /** The account's chat folders in their order; null where the mode has none. */
    suspend fun getFolders(): FoldersUpdate? = null

    /** Creates [folder] (under an id the server hasn't seen) or replaces it: the whole folder is sent. */
    suspend fun saveFolder(folder: ChatFolder) = Unit

    /** Puts the folders in [order]: every id the server listed (system ones too), «Все» first. */
    suspend fun reorderFolders(order: List<String>) = Unit

    /** Deletes folder [id]; its chats stay. */
    suspend fun deleteFolder(id: String) = Unit

    /** Pins chats (favIndex = when, epoch ms) or unpins them (0), all in one CONFIG write. The default does nothing. */
    suspend fun setFavIndex(indexes: Map<Long, Long>) = Unit

    /** Mutes or unmutes notifications for [chatId] (server `dontDisturbUntil`). */
    suspend fun setChatMuted(
        chatId: Long,
        muted: Boolean,
    )

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

    /** Sends [text] (formatted by [spans]) with [attaches] to [chatId], as a reply to [replyToId] when set. */
    suspend fun sendMessage(
        chatId: Long,
        text: String,
        cid: Long,
        replyToId: String? = null,
        attaches: List<OutAttach> = emptyList(),
        spans: List<TextSpan> = emptyList(),
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

    /** Edits our own message [messageId] in [chatId], replacing its text and formatting [spans] (null leaves `elements` out). */
    suspend fun editMessage(
        chatId: Long,
        messageId: String,
        text: String,
        spans: List<TextSpan>? = emptyList(),
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

    /** Resolves the playable URL of a voice message, or null if the server has none. */
    suspend fun getAudioUrl(
        chatId: Long,
        messageId: Long,
        audioId: Long,
        token: String? = null,
    ): String?

    /** Uploads a video (waits for server processing) and returns its attach descriptor. */
    suspend fun uploadVideo(
        content: MediaContent,
        fileName: String,
        mime: String,
        onProgress: ((Float) -> Unit)? = null,
    ): OutAttach.Video

    /**
     * Uploads a voice recording and returns its attach. Uses the same upload slot as
     * video, asked for with the AUDIO media type.
     */
    suspend fun uploadVoice(
        content: MediaContent,
        durationSeconds: Int,
        onProgress: ((Float) -> Unit)? = null,
    ): OutAttach.Voice

    /**
     * Uploads a round video message. Same upload slot as video, asked for with the
     * VIDEO_MESSAGE media type.
     */
    suspend fun uploadVideoNote(
        content: MediaContent,
        durationSeconds: Int,
        onProgress: ((Float) -> Unit)? = null,
    ): OutAttach.VideoNote

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

    /**
     * Changes [chatId]'s name, description, photo and/or option flags (only what is non-null is
     * sent); returns the chat as the server now has it, when it says. The default does nothing.
     */
    suspend fun editChat(
        chatId: Long,
        title: String? = null,
        description: String? = null,
        photoToken: String? = null,
        options: Map<String, Boolean> = emptyMap(),
    ): Chat? = null

    /** Our own name for contact [userId]; returns the contact as the server now has it, when it says. */
    suspend fun renameContact(
        userId: Long,
        firstName: String,
        lastName: String?,
    ): UserInfo? = null

    /** Takes [userId] off our contacts (the dialog stays). */
    suspend fun removeContact(userId: Long) = Unit

    /** Replaces [chatId]'s private invite link with a new one (the old stops working); returns the chat. */
    suspend fun revokeChatLink(chatId: Long): Chat? = null

    /** Creates a channel named [title] (optionally with a [photoToken]); returns its id when the server says. */
    suspend fun createChannel(
        title: String,
        photoToken: String?,
    ): Long? = null

    /**
     * Joins the chat or channel behind an invite [link] and returns it, or null if
     * the server accepted the request without describing the chat.
     */
    suspend fun joinByLink(link: String): Chat?

    /** Approves a web/desktop login by the token scanned from its QR code. */
    suspend fun approveQrLogin(qrLink: String)

    /** Returns the members of a group/channel chat (with names + avatars). */
    suspend fun getChatMembers(chatId: Long): List<UserInfo>

    /**
     * Fresh per-user read marks for [chatId]: userId → last read time (ms), us
     * excluded. Backs the "who read this message" list. Defaults to empty so the
     * demo/test fakes need not implement it.
     */
    suspend fun getReadMarks(chatId: Long): Map<Long, Long> = emptyMap()

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

    /** Several users in one go (CONTACT_INFO takes a list); the default asks one by one. */
    suspend fun fetchUsers(ids: List<Long>): List<UserInfo> = ids.mapNotNull { fetchUser(it) }

    /** Public chats/channels named like [query]; [marker] (from the previous page) for the next page. */
    suspend fun searchChats(
        query: String,
        marker: Long? = null,
    ): SearchPage

    /** Messages matching [query] in [chatId] (MSG_SEARCH), or across all chats when it is null (CHAT_SEARCH). */
    suspend fun searchMessages(
        chatId: Long?,
        query: String,
    ): List<MessageHit> = emptyList()

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
 * Parses a chat `participants` object into per-user read marks (userId → ms), leaving
 * out [myId] — our own mark says nothing about who read our messages. Entries with a
 * non-numeric key or value are skipped rather than failing the whole chat.
 */
internal fun JsonObject.toReadMarks(myId: Long): Map<Long, Long> =
    entries
        .mapNotNull { (key, value) ->
            val userId = key.toLongOrNull()?.takeIf { it != myId } ?: return@mapNotNull null
            val mark = value.jsonPrimitive.longOrNullSafe() ?: return@mapNotNull null
            userId to mark
        }.toMap()

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

/**
 * Picks a playable link out of an AUDIO_PLAY reply.
 *
 * The reply carries three of them — opus, m4a and mp3 (the official client's own
 * response class names exactly those three) — but their key names aren't recoverable,
 * since that parser is compiled away. So a link is identified by what it looks like
 * rather than what it is called. m4a is preferred: it is the format Android's
 * MediaPlayer handles most reliably, with mp3 next and opus last.
 */
internal fun JsonObject.audioLink(): String? {
    val links = mutableListOf<String>()

    fun collect(obj: JsonObject) {
        obj.values.forEach { value ->
            when (value) {
                is JsonPrimitive -> value.contentOrNullSafe()?.let { if (it.startsWith("http")) links += it }
                is JsonObject -> collect(value)
                else -> Unit
            }
        }
    }
    collect(this)
    return links.firstOrNull { it.contains(".m4a") }
        ?: links.firstOrNull { it.contains(".mp3") }
        ?: links.firstOrNull()
}

/**
 * The `duration` of an audio attach, in seconds.
 *
 * The wire value is milliseconds. An earlier version guessed the unit from the
 * magnitude, which misread every clip under 3.6 seconds — a two-second message came
 * out as 33:20.
 */
private fun audioDurationSeconds(raw: Long?): Int = ((raw ?: 0L) / 1000).toInt()

/**
 * The server's amplitude sketch, normalised to 0..1.
 *
 * It arrives as a list of numbers whose scale isn't fixed, so it is scaled by its
 * own peak — the shape is what the bubble draws, not the absolute values.
 */
private fun parseWaveform(raw: JsonElement?): List<Float> {
    val values =
        (raw as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNullSafe()?.toFloatOrNull() }
            ?.takeIf { it.isNotEmpty() } ?: return emptyList()
    val peak = values.max()
    if (peak <= 0f) return emptyList()
    return values.map { (it / peak).coerceIn(0f, 1f) }
}

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
        // Mirrors the official Android client / rumax. appVersion + buildNumber are
        // taken from the current MAX.apk and gated by the server ("Приложение устарело"
        // otherwise). Bump these together with the AuthAttest integrity hashes whenever
        // Max forces an update. (26.31.0 / 6822)
        const val APP_VERSION = "26.31.0"
        const val BUILD_NUMBER = 6822

        // Calls-SDK descriptor sent in VIDEO_CHAT_START_ACTIVE's `internalParams`.
        // Constants pinned from a real Android call (see "max-calls-feasibility" note).
        private const val CALLS_SDK_VERSION = "0.3.2.2"
        private const val CALLS_CLIENT_APP_KEY = "CGPGAGLGDIHBABABA"
        private const val CALLS_PROTOCOL_VERSION = 5
        private const val CALLS_CAPABILITIES = "3c03f"
        private const val CALLS_FALLBACK_DEVICE_ID = "994d1bc966a3b2ce"

        // Only reached when a content provider doesn't report a size, so the upload
        // has to be measured before it can be sent. Bounded so that path can't OOM.
        // VIDEO_UPLOAD's media types: 0 VIDEO, 1 VIDEO_MESSAGE, 2 AUDIO.
        private const val UPLOAD_TYPE_VIDEO_MESSAGE = 1
        private const val UPLOAD_TYPE_AUDIO = 2

        // The attach's `videoType` marking a round video message.
        private const val VIDEO_TYPE_VIDEO_NOTE = 1

        private const val MAX_BUFFERED_UPLOAD_BYTES = 64 * 1024 * 1024

        /** Sticker ids asked of ASSETS_GET_BY_IDS at once. */
        private const val STICKERS_PER_REQUEST = 100

        // Upload budgets: total per request, and the allowed gap between packets.
        private const val UPLOAD_REQUEST_TIMEOUT_MS = 10 * 60 * 1000L
        private const val UPLOAD_SOCKET_TIMEOUT_MS = 2 * 60 * 1000L

        // Opcodes (verified against PyMax protocol enums and rumax).
        private const val OP_HANDSHAKE = 6
        private const val OP_SESSIONS_INFO = 96 // SESSIONS_INFO: the account's signed-in devices
        private const val OP_SESSIONS_CLOSE = 97 // SESSIONS_CLOSE: end every other session; answers a new token
        private const val OP_LOG = 5 // LOG: batched analytics events (ru.ok.tamtam.api.d.LOG)
        private const val OP_PROFILE = 16 // PROFILE: update own profile (name/bio/avatar)
        private const val OP_START_AUTH = 17
        private const val OP_CHECK_CODE = 18
        private const val OP_SYNC = 19
        private const val OP_CHAT_SETTINGS = 22 // set per-chat settings (e.g. dontDisturbUntil mute)
        private const val OP_REGISTER = 23
        private const val OP_CONTACT_INFO = 32
        private const val OP_CONTACT_UPDATE = 34
        private const val OP_CONTACT_PRESENCE = 35
        private const val OP_CONTACT_LIST = 36 // CONTACT_LIST: contacts by status (the black list)
        private const val OP_CONTACT_BY_PHONE = 46
        private const val OP_FETCH_HISTORY = 49
        private const val OP_PUBLIC_SEARCH = 60
        private const val OP_AUDIO_PLAY = 301 // AUDIO_PLAY: playback URL for a voice message
        private const val OP_VIDEO_PLAY = 83
        private const val OP_PHOTO_UPLOAD = 80 // PHOTO_UPLOAD: request a photo upload URL
        private const val OP_VIDEO_UPLOAD = 82 // VIDEO_UPLOAD: request a video upload URL
        private const val OP_FILE_UPLOAD = 87 // FILE_UPLOAD: request a file upload URL
        private const val OP_FILE_DOWNLOAD = 88 // FILE_DOWNLOAD: get a file's download URL
        private const val OP_MARK_READ = 50
        private const val OP_MSG_SEND_CALLBACK = 118 // MSG_SEND_CALLBACK: a bot's callback button pressed
        private const val OP_SEND_MESSAGE = 64
        private const val OP_CHECK_PASSWORD = 115

        // Login-password (2FA) management, as the official client names them.
        private const val OP_AUTH_LOGIN_RESTORE_PASSWORD = 101
        private const val OP_AUTH_2FA_DETAILS = 104
        private const val OP_AUTH_VALIDATE_PASSWORD = 107
        private const val OP_AUTH_VALIDATE_HINT = 108
        private const val OP_AUTH_VERIFY_EMAIL = 109
        private const val OP_AUTH_CHECK_EMAIL = 110
        private const val OP_AUTH_SET_2FA = 111
        private const val OP_AUTH_CREATE_TRACK = 112
        private const val OP_AUTH_CHECK_PASSWORD = 113

        // AUTH_SET_2FA `expectedCapabilities` values (official enum x7j).
        private const val CAP_SET_PASSWORD = 0
        private const val CAP_UPDATE_PASSWORD = 1
        private const val CAP_HINT = 3
        private const val CAP_EMAIL = 4
        private const val CAP_REMOVE_2FA = 5

        private const val OP_AUTH_QR_APPROVE = 290 // AUTH_QR_APPROVE: confirm a web/desktop login QR
        private const val OP_SEND_VOTE = 304 // SEND_VOTE: vote in a poll
        private const val OP_ASSETS_UPDATE = 27 // ASSETS_UPDATE: the user's sticker sections and recents
        private const val OP_ASSETS_GET_BY_IDS = 28 // ASSETS_GET_BY_IDS: sticker sets / stickers by id
        private const val OP_FOLDERS_GET = 272 // FOLDERS_GET: the account's chat folders
        private const val OP_FOLDERS_UPDATE = 274 // FOLDERS_UPDATE: create or replace a folder (sent whole)
        private const val OP_FOLDERS_REORDER = 275 // FOLDERS_REORDER: every folder id in the new order
        private const val OP_FOLDERS_DELETE = 276 // FOLDERS_DELETE
        const val OP_NOTIF_FOLDERS = 277 // NOTIF_FOLDERS push: {foldersOrder, folderSync, folder}
        const val OP_NEW_MESSAGE = 128
        const val OP_MARK_UPDATE = 130 // server push: read/delivery marks changed
        const val OP_NOTIF_TYPING = 129 // server push: someone is typing in a chat
        private const val OP_MSG_TYPING = 65 // MSG_TYPING: we are typing
        const val OP_PRESENCE = 132 // server push: a contact's online state changed
        const val OP_REACTION_UPDATE = 155 // NOTIF_MSG_REACTIONS_CHANGED push
        const val OP_NOTIF_CHAT = 135 // NOTIF_CHAT push: a chat was created/updated (e.g. added to a group)
        const val OP_NOTIF_MSG_DELETE = 142 // NOTIF_MSG_DELETE push: the other party deleted a message
        const val OP_NOTIF_ATTACH = 136 // NOTIF_ATTACH push: an uploaded video/file finished processing

        // Confirmed from the official client's opcode enum (ru.ok.tamtam.api.d,
        // recovered by decompiling MAX.apk).
        private const val OP_REACTION = 178 // MSG_REACTION: add/set a reaction
        private const val OP_CANCEL_REACTION = 179 // MSG_CANCEL_REACTION: remove our reaction
        private const val OP_DETAILED_REACTIONS = 181 // MSG_GET_DETAILED_REACTIONS: who reacted with what
        private const val OP_DELETE_CHAT = 52 // CHAT_DELETE
        private const val OP_CHAT_JOIN = 57 // CHAT_JOIN: join a chat/channel by its invite link
        private const val OP_LEAVE_CHAT = 58 // CHAT_LEAVE
        private const val OP_CHAT_MEMBERS = 59 // CHAT_MEMBERS: paginated member list
        private const val OP_CHAT_INFO = 48 // CHAT_INFO: one chat's full object (incl. read marks)
        private const val OP_CHAT_CLEAR = 54 // CHAT_CLEAR: empty a chat's history, keep the chat
        private const val OP_CHAT_UPDATE = 55 // CHAT_UPDATE: chat settings, incl. the pinned message
        private const val OP_CHAT_MEDIA = 51 // CHAT_MEDIA: a chat's messages with given attach types
        private const val OP_CHAT_SEARCH = 68 // CHAT_SEARCH: messages across all chats
        private const val OP_MSG_SEARCH = 73 // MSG_SEARCH: messages in one chat
        private const val OP_CHAT_MEMBERS_UPDATE = 77 // CHAT_MEMBERS_UPDATE: add/remove members
        private const val OP_MSG_DELETE = 66 // MSG_DELETE
        private const val OP_MSG_EDIT = 67 // MSG_EDIT

        // ----- Calls (voice/video). See model/Call.kt and the "max-calls-feasibility" note. -----
        // VIDEO_CHAT_START_ACTIVE: place a 1:1 call; reply carries the SFU endpoints + ICE creds.
        private const val OP_VIDEO_CHAT_START_ACTIVE = 78
        const val OP_NOTIF_CALL_START = 137 // server push: an inbound call is ringing
        private const val OP_VIDEO_CHAT_HANGUP = 167 // end/decline a call on the Max socket
    }

    private val transport = MobileTransport()
    private val scope = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, _ -> })

    // Each chat's `config.chats.<id>` as last known (the sync reply, then our own writes); see writeChatSettings.
    private val knownChatSettings = HashMap<Long, JsonObject>()
    private val chatSettingsLock = Mutex()

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

    /** The current session's SMS-attestation inputs, captured at [connect]. */
    private var callsSeed: Long? = null
    private var sessionDeviceId: String? = null

    /** Stream of newly received messages (server push, opcode 128). */
    private val _incoming = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    override val incoming: SharedFlow<Message> = _incoming

    /** Stream of read-mark updates (server push, opcode 130) — e.g. the other side read our messages. */
    private val _readMarks = MutableSharedFlow<ReadMark>(extraBufferCapacity = 64)
    override val readMarks: SharedFlow<ReadMark> = _readMarks

    /** Stream of live presence changes (server push, opcode 132). */
    private val _presence = MutableSharedFlow<Presence>(extraBufferCapacity = 64)
    override val presence: SharedFlow<Presence> = _presence
    private val _typing = MutableSharedFlow<TypingEvent>(extraBufferCapacity = 64)
    override val typing: SharedFlow<TypingEvent> = _typing

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

    /** Folder changes (server push, opcode 277). */
    private val _folderUpdates = MutableSharedFlow<FoldersUpdate>(extraBufferCapacity = 16)
    override val folderUpdates: SharedFlow<FoldersUpdate> = _folderUpdates

    /** Stream of inbound calls ringing (server push, opcode 137). */
    private val _incomingCalls = MutableSharedFlow<IncomingCall>(extraBufferCapacity = 8)
    override val incomingCalls: SharedFlow<IncomingCall> = _incomingCalls

    override val isConnected: Boolean get() = transport.isConnected

    /** Emitted when the connection drops unexpectedly (for auto-reconnect). */
    override val drops: SharedFlow<Unit> get() = transport.drops

    /** Preset avatar id to use when registering a new account (rumax's default). */
    private var registerPhotoId: Long = 2981369L

    /** Profile-recovery URL from session-init; see [recoveryUrl]. */
    @Volatile private var recoveryUrlValue: String? = null
    override val recoveryUrl: String? get() = recoveryUrlValue

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
                // One malformed push must not kill this collector: it is created once per
                // process, and losing it silently ends all realtime updates (messages,
                // read marks, presence, upload completions, incoming calls) until restart.
                runCatching { handlePush(opcode, payload) }
            }
        }
    }

    private suspend fun handlePush(
        opcode: Int,
        payload: JsonObject,
    ) {
        when (opcode) {
            OP_NEW_MESSAGE -> {
                val chatId = payload["chatId"]?.jsonPrimitive?.longOrNullSafe()
                val msg = payload["message"] as? JsonObject
                if (chatId != null && msg != null) parseMessage(msg, chatId)?.let { _incoming.emit(it) }
            }
            OP_MARK_UPDATE -> {
                val chatId = payload["chatId"]?.jsonPrimitive?.longOrNullSafe()
                val mark = payload["mark"]?.jsonPrimitive?.longOrNullSafe()
                val userId = payload["userId"]?.jsonPrimitive?.longOrNullSafe() ?: 0L
                if (chatId != null && mark != null) _readMarks.emit(ReadMark(chatId, userId, mark))
            }
            OP_PRESENCE -> parsePresence(payload)?.let { _presence.emit(it) }
            OP_NOTIF_TYPING -> {
                // {chatId, userId, type?} — `type` names the attach being composed; we
                // show a single "печатает…" regardless.
                val chatId = payload["chatId"]?.jsonPrimitive?.longOrNullSafe()
                val userId = payload["userId"]?.jsonPrimitive?.longOrNullSafe()
                if (chatId != null && userId != null && userId != myId) _typing.emit(TypingEvent(chatId, userId))
            }
            OP_REACTION_UPDATE -> parseReactionUpdate(payload)?.let { _reactionUpdates.emit(it) }
            OP_NOTIF_CHAT -> {
                // {chat: {...}} — a chat was created or updated (new dialog, added to a group).
                (payload["chat"] as? JsonObject)?.let { parseChat(it, emptyMap()) }?.let { _chatUpdates.emit(it) }
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
                        ?: (payload["chat"] as? JsonObject)
                            ?.get("id")
                            ?.jsonPrimitive
                            ?.longOrNullSafe()
                val ids =
                    buildList {
                        (payload["messageIds"] as? JsonArray)
                            ?.forEach { (it as? JsonPrimitive)?.longOrNullSafe()?.let { id -> add(id.toString()) } }
                        payload["messageId"]?.jsonPrimitive?.longOrNullSafe()?.let { add(it.toString()) }
                    }
                if (chatId != null && ids.isNotEmpty()) _deletions.emit(MessageDeletion(chatId, ids))
            }
            OP_NOTIF_CALL_START -> parseIncomingCall(payload)?.let { _incomingCalls.emit(it) }
            OP_NOTIF_FOLDERS -> _folderUpdates.emit(parseFolders(payload))
        }
    }

    /**
     * Parses a NOTIF_CALL_START push into an [IncomingCall]. The exact field layout is
     * best-effort (pinned from the CALL_HISTORY / call-message shapes we captured) and
     * tolerant of missing fields — refine against a live inbound capture.
     */
    private fun parseIncomingCall(payload: JsonObject): IncomingCall? {
        val conversationId =
            payload["conversationId"]?.jsonPrimitive?.contentOrNullSafe()
                ?: payload["callId"]?.jsonPrimitive?.contentOrNullSafe()
                ?: return null
        val callerId =
            payload["callerId"]?.jsonPrimitive?.longOrNullSafe()
                ?: payload["initiatorId"]?.jsonPrimitive?.longOrNullSafe()
                ?: 0L
        val chatId = payload["chatId"]?.jsonPrimitive?.longOrNullSafe() ?: 0L
        // The `vcp` blob (len:base64(LZ4(JSON))) carries the callee's SFU params.
        val setup =
            payload["vcp"]?.jsonPrimitive?.contentOrNullSafe()?.let {
                runCatching { parseVcp(conversationId, it) }.getOrNull()
            }
        val isVideo =
            setup?.let { payload["type"]?.jsonPrimitive?.contentOrNullSafe() == "VIDEO" }
                ?: (
                    payload["isVideo"]?.jsonPrimitive?.contentOrNullSafe() == "true" ||
                        payload["type"]?.jsonPrimitive?.contentOrNullSafe() == "VIDEO" ||
                        payload["callType"]?.jsonPrimitive?.contentOrNullSafe() == "VIDEO"
                )
        return IncomingCall(
            conversationId = conversationId,
            callerId = callerId,
            chatId = chatId,
            kind = if (isVideo) CallKind.VIDEO else CallKind.AUDIO,
            setup = setup,
        )
    }

    /**
     * Parses a live presence push: `{userId: <id>, presence: {seen, status?}}`.
     */
    private fun parsePresence(payload: JsonObject): Presence? {
        val uid = payload["userId"]?.jsonPrimitive?.longOrNullSafe() ?: return null
        return (payload["presence"] as? JsonObject)?.toPresence(uid) ?: Presence(uid, online = false)
    }

    /** Parses one chat object (from sync's `chats` array or a NOTIF_CHAT push). */
    internal fun parseChat(
        c: JsonObject,
        names: Map<Long, String>,
    ): Chat? {
        val id = c["id"]?.jsonPrimitive?.longOrNullSafe() ?: return null
        val type = c["type"]?.jsonPrimitive?.contentOrNullSafe()
        val rawTitle = c["title"]?.jsonPrimitive?.contentOrNullSafe()
        // Parsed rather than read straight off `text`, so a photo/file/voice message
        // gets a label instead of leaving the row blank (or, worse, showing whatever
        // text came before it).
        val lastMessage = (c["lastMessage"] as? JsonObject)?.let { parseMessage(it, id) }
        val lastText =
            lastMessage
                ?.previewLabel()
                ?.ifBlank { null }
                ?.let { localize(it) }
        val lastTime = c["lastEventTime"]?.jsonPrimitive?.longOrNullSafe() ?: 0L
        val unread = c["newMessages"]?.jsonPrimitive?.intOrNullSafe() ?: 0
        val isDialog = type == "DIALOG"
        // participants = {userId: lastReadMarkMs}: who has read up to which point.
        // The per-user map backs the "who read this message" list in groups; the
        // highest mark is all a dialog needs for its ✓✓.
        val readMarks = c["participants"]?.jsonObject?.toReadMarks(myId) ?: emptyMap()
        val otherReadMark = readMarks.values.maxOrNull() ?: 0L
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
        // The server names it `owner` (seen live); `ownerId` is kept for older replies.
        val ownerId = (c["owner"] ?: c["ownerId"])?.jsonPrimitive?.longOrNullSafe() ?: 0L
        // adminParticipants = {adminUserId: adminInfo}; we only need the ids.
        // Admins come as an `admins` id array (seen live) or, in older replies, as the
        // keys of an `adminParticipants` map.
        val adminIds =
            (c["admins"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.longOrNullSafe() }?.toSet()
                ?: c["adminParticipants"]
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
                // Every known flag is listed with a boolean; only the true ones are set.
                is JsonObject -> o.filterValues { (it as? JsonPrimitive)?.content == "true" }.keys
                else -> emptySet()
            }
        val amAdmin = myId == ownerId || myId in adminIds
        // In a CHANNEL only the owner/admins post; regular groups (CHAT) allow any
        // member; dialogs are writable except a read-only service chat («Безопасность»).
        val canWrite =
            when {
                isDialog -> "SERVICE_CHAT" !in options
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
            readMarks = readMarks,
            isChannel = isChannel,
            ownerId = ownerId,
            adminIds = adminIds,
            memberIds = memberIds,
            participantsCount = participantsCount,
            link = link,
            canWrite = canWrite,
            canAddMembers = canAddMembers,
            // Mute state isn't on the chat object; it comes from the sync's config.chats
            // map and is applied in sync(). Defaults to false here.
            muted = false,
            description = c["description"]?.jsonPrimitive?.contentOrNullSafe()?.ifBlank { null },
            official = options.contains("OFFICIAL"),
            isServiceChat = options.contains("SERVICE_CHAT") || c["hasBots"]?.jsonPrimitive?.asBoolean() == true,
            allCanPin = options.contains("ALL_CAN_PIN_MESSAGE"),
            options = options,
            pinnedMessage = (c["pinnedMessage"] as? JsonObject)?.let { parseMessage(it, id) },
            lastSenderId = lastMessage?.senderId ?: 0L,
            lastMessageTime = lastMessage?.time ?: 0L,
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

        // Real per-device values (not a shared static string — see [DeviceProfile]). Keys
        // are emitted in the official client's order; `arch` stays arm64-v8a to match the
        // login attestation's native-library hashes.
        val device = deviceProfile(deviceId)
        val handshake =
            transport.request(
                OP_HANDSHAKE,
                buildJsonObject {
                    putJsonObject("userAgent") {
                        put("deviceType", "ANDROID")
                        put("pushDeviceType", "GCM")
                        put("appVersion", APP_VERSION)
                        put("arch", "arm64-v8a")
                        put("buildNumber", BUILD_NUMBER)
                        put("osVersion", device.osVersion)
                        put("locale", device.locale)
                        put("deviceLocale", device.deviceLocale)
                        put("deviceName", device.deviceName)
                        put("screen", device.screen)
                        put("timezone", device.timezone)
                    }
                    put("deviceId", deviceId)
                    put("clientSessionId", 1)
                    put("mt_instanceid", mtInstance)
                },
            )
        // Kept for the login-code attestation: `mode` is bound to this session's
        // server-issued callsSeed plus our deviceId (see startAuth / AuthAttest).
        callsSeed = handshake["callsSeed"]?.jsonPrimitive?.longOrNullSafe()
        // `recovery-url`: where a restricted account restores access (see [recoveryUrl]).
        handshake["recovery-url"]
            ?.jsonPrimitive
            ?.contentOrNullSafe()
            ?.ifBlank { null }
            ?.let { recoveryUrlValue = it }
        sessionDeviceId = deviceId
        transport.startPing()
    }

    override fun disconnect() = transport.disconnect()

    // ---------------------------------------------------------------------
    // Calls (STAGE 1 — call setup over the Max socket)
    // ---------------------------------------------------------------------

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun startCall(
        peerId: Long,
        isVideo: Boolean,
    ): CallSetup {
        val conversationId = Uuid.random().toString()
        return videoChatStartActive(conversationId, listOf(peerId), isVideo)
    }

    override suspend fun acceptCall(
        conversationId: String,
        peerId: Long,
        isVideo: Boolean,
    ): CallSetup = videoChatStartActive(conversationId, listOf(peerId), isVideo)

    override suspend fun hangupCall(
        conversationId: String,
        reason: String,
        peerId: Long?,
    ) {
        // Same shape as the official client's VIDEO_CHAT_HANGUP: without `reason` and
        // `internalParams` the server ignores the request, and the peer keeps ringing.
        runCatching {
            transport.request(
                OP_VIDEO_CHAT_HANGUP,
                buildJsonObject {
                    put("conversationId", conversationId)
                    put("reason", reason)
                    if (peerId != null) put("peerId", peerId.toString())
                    put("internalParams", callInternalParams())
                },
                timeoutMs = 10_000,
            )
        }
    }

    override suspend fun getSessions(): List<SessionInfo> {
        val payload = transport.request(OP_SESSIONS_INFO, buildJsonObject { })
        payload.errorCode()?.let { error(payload.serverMessage("Не удалось получить список устройств")) }
        return (payload["sessions"] as? JsonArray).orEmptyList().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            SessionInfo(
                client = o["client"]?.jsonPrimitive?.contentOrNullSafe() ?: "",
                info = o["info"]?.jsonPrimitive?.contentOrNullSafe() ?: "",
                location = o["location"]?.jsonPrimitive?.contentOrNullSafe() ?: "",
                time = o["time"]?.jsonPrimitive?.longOrNullSafe() ?: 0L,
                current = o["current"]?.jsonPrimitive?.contentOrNullSafe() == "true",
            )
        }
    }

    override suspend fun closeOtherSessions(): String? {
        // SESSIONS_CLOSE (op 97) {} -> {token}: our session goes on under that new token.
        if (!transport.isConnected) error("Нет соединения с сервером")
        // Once sent, the server may have acted and revoked our token: a lost reply is "no token", not a failure.
        val payload =
            try {
                transport.request(OP_SESSIONS_CLOSE, buildJsonObject { })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return null
            }
        payload.errorCode()?.let { error(payload.serverMessage("Не удалось завершить сеансы")) }
        return (payload["token"] as? JsonPrimitive)?.contentOrNullSafe()?.ifBlank { null }
    }

    override suspend fun sendTyping(chatId: Long) {
        // Fire-and-forget like the official client; a reply, if any, is just ignored.
        runCatching { transport.notify(OP_MSG_TYPING, buildJsonObject { put("chatId", chatId) }) }
    }

    override suspend fun updatePrivacy(privacy: Privacy): Privacy {
        // CONFIG (op 22) with settings.user; the reply carries the full updated `user` map.
        val payload =
            transport.request(
                OP_CHAT_SETTINGS,
                buildJsonObject {
                    putJsonObject("settings") {
                        putJsonObject("user") {
                            put("SEARCH_BY_PHONE", privacy.searchByPhone)
                            put("INCOMING_CALL", privacy.incomingCall)
                            put("CHATS_INVITE", privacy.chatsInvite)
                            put("HIDDEN", privacy.hideOnline)
                            put("SHOW_READ_MARK", privacy.showReadMarks)
                        }
                    }
                },
            )
        payload.errorCode()?.let { error(payload.serverMessage("Не удалось сохранить настройки")) }
        return (payload["user"] as? JsonObject)?.toPrivacy() ?: privacy
    }

    override suspend fun fetchChatMedia(
        chatId: Long,
        types: List<String>,
        anchor: String,
        count: Int,
        withAnchor: Boolean,
    ): List<Message> {
        // CHAT_MEDIA (op 51): {chatId, messageId, attachTypes, forward, backward}, paged from a
        // message like history (official dc2 o=7; web.max.ru per max-cli). The official reply
        // parser (kf2) reads {messages: [message…], total, pos, forward, backward}.
        val mid = anchor.toLongOrNull() ?: return emptyList()
        val payload =
            transport.request(
                OP_CHAT_MEDIA,
                buildJsonObject {
                    put("chatId", chatId)
                    put("messageId", mid)
                    putJsonArray("attachTypes") { types.forEach { add(it) } }
                    // One step forward takes the anchor itself in, should backward leave it out.
                    put("forward", if (withAnchor) 1 else 0)
                    put("backward", count)
                },
            )
        payload.errorCode()?.let { error(payload.serverMessage("Не удалось загрузить вложения")) }
        return (payload["messages"] as? JsonArray)
            .orEmptyList()
            .mapNotNull { el -> (el as? JsonObject)?.let { parseMessage(it, chatId) } }
    }

    override suspend fun pinMessage(
        chatId: Long,
        messageId: String?,
        notify: Boolean,
    ): Chat? {
        // CHAT_UPDATE (op 55): {chatId, pinMessageId, notifyPin}; 0 unpins, and the official
        // client sends notifyPin only when true. Answers {chat} with its pinnedMessage.
        val payload =
            transport.request(
                OP_CHAT_UPDATE,
                buildJsonObject {
                    put("chatId", chatId)
                    put("pinMessageId", messageId?.toLongOrNull() ?: 0L)
                    if (messageId != null && notify) put("notifyPin", true)
                },
            )
        payload.errorCode()?.let { error(payload.serverMessage(if (messageId != null) "Не удалось закрепить" else "Не удалось открепить")) }
        return (payload["chat"] as? JsonObject)?.let { parseChat(it, emptyMap()) }
    }

    override suspend fun setFavIndex(indexes: Map<Long, Long>) =
        // favIndex = when the chat was pinned (epoch ms), as the official client's addToFavorites; 0 unpins
        // (removeFromFavorites).
        writeChatSettings(
            "favIndex",
            indexes,
            if (indexes.values.all { it == 0L }) "Не удалось открепить чат" else "Не удалось закрепить чат",
        )

    override suspend fun setChatMuted(
        chatId: Long,
        muted: Boolean,
    ) = // dontDisturbUntil: -1 = muted forever, 0 = notifications on.
        writeChatSettings(
            "dontDisturbUntil",
            mapOf(chatId to if (muted) -1L else 0L),
            if (muted) "Не удалось отключить уведомления" else "Не удалось включить уведомления",
        )

    /**
     * CONFIG (op 22) `settings.chats`: sets [key] for each chat in [values]. Every chat goes as a whole entry
     * built from what we know of it ([chatSettingsEntry]), the way the official client writes, so a mute
     * never drops a pin and a pin never drops a mute. One write at a time, each built on the last one kept.
     */
    private suspend fun writeChatSettings(
        key: String,
        values: Map<Long, Long>,
        failure: String,
    ) = chatSettingsLock.withLock {
        val entries = values.mapValues { (id, value) -> chatSettingsEntry(knownChatSettings[id], key, value) }
        val payload =
            transport.request(
                OP_CHAT_SETTINGS,
                buildJsonObject {
                    putJsonObject("settings") { put("chats", JsonObject(entries.mapKeys { it.key.toString() })) }
                },
            )
        payload.errorCode()?.let { error(payload.serverMessage(failure)) }
        val echoed = payload["chats"] as? JsonObject
        entries.forEach { (id, sent) ->
            knownChatSettings[id] = JsonObject(sent + ((echoed?.get(id.toString()) as? JsonObject).orEmpty()))
        }
    }

    /**
     * Sends VIDEO_CHAT_START_ACTIVE (opcode 78) and parses the reply's
     * `internalCallerParams` JSON into a [CallSetup]. `internalParams` mirrors the
     * genuine client's calls-SDK descriptor (constants pinned from a real call).
     */
    private suspend fun videoChatStartActive(
        conversationId: String,
        calleeIds: List<Long>,
        isVideo: Boolean,
    ): CallSetup {
        val reply =
            transport.request(
                OP_VIDEO_CHAT_START_ACTIVE,
                buildJsonObject {
                    put("conversationId", conversationId)
                    putJsonArray("calleeIds") { calleeIds.forEach { add(it) } }
                    put("internalParams", callInternalParams())
                    put("isVideo", isVideo)
                },
            )
        return parseCallSetup(conversationId, reply)
    }

    /** The calls-SDK descriptor the official client attaches to every call command. */
    private fun callInternalParams(): String =
        buildJsonObject {
            put("platform", "ANDROID")
            put("sdkVersion", CALLS_SDK_VERSION)
            put("clientAppKey", CALLS_CLIENT_APP_KEY)
            put("deviceId", sessionDeviceId ?: CALLS_FALLBACK_DEVICE_ID)
            put("protocolVersion", CALLS_PROTOCOL_VERSION)
            put("onlyAdminCanRecord", false)
            put("waitForAdmin", false)
            put("capabilities", CALLS_CAPABILITIES)
        }.toString()

    /** Parses the VIDEO_CHAT_START_ACTIVE reply (its `internalCallerParams` JSON string). */
    private fun parseCallSetup(
        conversationId: String,
        reply: JsonObject,
    ): CallSetup {
        val raw =
            reply["internalCallerParams"]?.jsonPrimitive?.contentOrNullSafe()
                ?: error(reply.serverMessage("Не удалось начать звонок"))
        return parseInternalCallerParams(conversationId, raw)
    }

    // ---------------------------------------------------------------------
    // Authentication
    // ---------------------------------------------------------------------

    /**
     * Requests an SMS code. Returns the expected code length; throws on refusal.
     *
     * The server only dispatches an SMS when the AUTH_REQUEST carries a valid `mode`
     * attestation ([AuthAttest]) bound to this session's callsSeed and our deviceId, and
     * only when the request is framed like the official client (ver=10, the `f0 7c`
     * payload prefix) — hence the raw send rather than [MobileTransport.request]. Without
     * both, the server still returns a token but no code arrives.
     */
    override suspend fun startAuth(phone: String): Int {
        val seed = callsSeed
        val device = sessionDeviceId
        val payload =
            if (seed != null && device != null) {
                val mode = AuthAttest.mode(seed, device)
                transport.requestRaw(
                    OP_START_AUTH,
                    ver = 10,
                    flag = 0x01,
                    payload = AuthAttest.startAuthPayload(mode, phone),
                )
            } else {
                // No session seed (shouldn't happen post-connect) — best-effort plain request.
                transport.request(
                    OP_START_AUTH,
                    buildJsonObject {
                        put("phone", phone)
                        put("type", "START_AUTH")
                    },
                )
            }
        authToken = payload["token"]?.jsonPrimitive?.contentOrNullSafe()
        if (authToken == null) throw authFailure(payload, "Не удалось отправить код")
        return payload["codeLength"]?.jsonPrimitive?.intOrNullSafe() ?: 6
    }

    /**
     * Submits the SMS code. Mirrors the official client's branching on the reply:
     * `tokenAttrs.LOGIN` → signed in; `passwordChallenge` → the account's login
     * password is required; `tokenAttrs.REGISTER` → new account; an error → shown;
     * and none of the above → the server enforces a login password this account
     * doesn't have yet ([CodeResult.PasswordRequiredNotSet]).
     */
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
        payload.passwordTrackId()?.let {
            return CodeResult.NeedPassword(it, payload.passwordHint(), payload.passwordEmail())
        }
        // New, unregistered phone: the server returns a REGISTER token + preset avatars.
        payload.registerToken()?.let { regToken ->
            authToken = regToken
            payload.firstPresetAvatarId()?.let { registerPhotoId = it }
            return CodeResult.NeedRegister
        }
        if (payload.isErrorReply()) throw authFailure(payload, "Неверный код")
        return CodeResult.PasswordRequiredNotSet
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
            ?: throw authFailure(payload, "Не удалось зарегистрироваться")
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
        payload.loginToken()?.let { return it }
        if (payload.isErrorReply()) throw authFailure(payload, "Неверный пароль")
        // Accepted but no token — the official client treats this as a failed sign-in too.
        error("Не удалось войти в профиль, попробуйте ещё раз")
    }

    /**
     * Turns a login-step error reply into the exception the UI should see: known
     * server codes (as the official client names them) get a proper message, and the
     * codes that mean "this login attempt is over" become [AuthSessionExpiredException]
     * so the flow restarts from the phone number instead of retrying a dead track.
     */
    private fun authFailure(
        payload: JsonObject,
        default: String,
    ): Exception {
        val code = payload.errorCode()
        return when (code) {
            "login.token", "track.not.found", "phone.not.checked" ->
                AuthSessionExpiredException("Время на вход истекло, запросите код ещё раз")
            "login.blocked" -> IllegalStateException("Вход временно заблокирован, попробуйте позже")
            "login.flood", "too.many.requests" -> IllegalStateException("Слишком много попыток, попробуйте позже")
            "password2fa.wrong" -> IllegalStateException("Неверный пароль")
            "password.invalid" -> IllegalStateException("Пароль не подходит: ${payload.serverMessage("попробуйте другой")}")
            "password2fa.no.attempts" ->
                AuthSessionExpiredException("Попытки ввода пароля исчерпаны. Попробуйте войти позже")
            "hint.invalid" -> IllegalStateException("Подсказка не подходит: она не должна совпадать с паролем")
            "email.wrong" -> IllegalStateException("Неверный код из письма")
            "email.compromised" -> IllegalStateException("Эту почту нельзя использовать для восстановления")
            "verify.code.wrong", "code.invalid" -> IllegalStateException("Неверный код")
            else ->
                if (payload.demandsPasswordSetup()) {
                    PasswordSetupRequiredException(payload.readableServerMessage())
                } else {
                    IllegalStateException(payload.serverMessage(default))
                }
        }
    }

    /**
     * Any other refusal that is about the login password (the codes for a wrong password
     * or exhausted attempts are handled above): the server wants a password set up
     * before it lets this device in. Matched loosely, since the exact code isn't known.
     */
    private fun JsonObject.demandsPasswordSetup(): Boolean {
        val code = errorCode().orEmpty().lowercase()
        val text = readableServerMessage().orEmpty().lowercase()
        return listOf("2fa", "twofa", "password", "restrict").any { it in code } ||
            listOf("парол", "двухфактор", "2fa").any { it in text }
    }

    /** The server's human-readable text only (no raw-payload fallback). */
    private fun JsonObject.readableServerMessage(): String? =
        (this["localizedMessage"] as? JsonPrimitive)?.contentOrNullSafe()?.ifBlank { null }
            ?: (this["message"] as? JsonPrimitive)?.contentOrNullSafe()?.ifBlank { null }

    /** Returns the reply, or throws the mapped auth error when it is an error frame. */
    private fun JsonObject.orAuthFailure(default: String): JsonObject {
        if (isErrorReply()) throw authFailure(this, default)
        return this
    }

    // ---------------------------------------------------------------------
    // Login password (2FA) management
    // ---------------------------------------------------------------------

    override suspend fun twoFaCreateTrack(): String {
        val payload =
            transport
                .request(OP_AUTH_CREATE_TRACK, buildJsonObject { put("type", 0) })
                .orAuthFailure("Не удалось начать настройку пароля")
        return payload["trackId"]?.jsonPrimitive?.contentOrNullSafe()
            ?: error("Не удалось начать настройку пароля")
    }

    override suspend fun twoFaDetails(trackId: String?): TwoFaDetails {
        val payload =
            transport
                .request(OP_AUTH_2FA_DETAILS, buildJsonObject { if (!trackId.isNullOrEmpty()) put("trackId", trackId) })
                .orAuthFailure("Не удалось получить настройки пароля")
        // `password` = the official client's PasswordDetails {enabled, hint, email}.
        return when (val p = payload["password"]) {
            is JsonObject ->
                TwoFaDetails(
                    enabled = (p["enabled"] as? JsonPrimitive)?.asBoolean(),
                    hint = p["hint"]?.jsonPrimitive?.contentOrNullSafe()?.ifBlank { null },
                    email = p["email"]?.jsonPrimitive?.contentOrNullSafe()?.ifBlank { null },
                )
            is JsonPrimitive -> TwoFaDetails(enabled = p.asBoolean(), hint = null, email = null)
            else -> TwoFaDetails(enabled = null, hint = null, email = null)
        }
    }

    override suspend fun twoFaValidatePassword(
        trackId: String,
        password: String,
    ) {
        transport
            .request(
                OP_AUTH_VALIDATE_PASSWORD,
                buildJsonObject {
                    put("trackId", trackId)
                    put("password", password)
                },
            ).orAuthFailure("Пароль не подходит")
    }

    override suspend fun twoFaValidateHint(
        trackId: String,
        hint: String,
    ) {
        transport
            .request(
                OP_AUTH_VALIDATE_HINT,
                buildJsonObject {
                    put("trackId", trackId)
                    put("hint", hint)
                },
            ).orAuthFailure("Подсказка не подходит")
    }

    override suspend fun twoFaVerifyEmail(
        trackId: String,
        email: String,
    ) {
        transport
            .request(
                OP_AUTH_VERIFY_EMAIL,
                buildJsonObject {
                    put("trackId", trackId)
                    put("email", email)
                },
            ).orAuthFailure("Не удалось отправить код на почту")
    }

    override suspend fun twoFaCheckEmail(
        trackId: String,
        code: String,
    ) {
        transport
            .request(
                OP_AUTH_CHECK_EMAIL,
                buildJsonObject {
                    put("trackId", trackId)
                    put("verifyCode", code)
                },
            ).orAuthFailure("Неверный код из письма")
    }

    override suspend fun twoFaCheckPassword(
        trackId: String,
        password: String,
    ): String {
        val payload =
            transport
                .request(
                    OP_AUTH_CHECK_PASSWORD,
                    buildJsonObject {
                        put("trackId", trackId)
                        put("password", password)
                    },
                ).orAuthFailure("Неверный пароль")
        // The reply carries the (password-confirmed) track to continue on.
        return payload["trackId"]?.jsonPrimitive?.contentOrNullSafe() ?: trackId
    }

    override suspend fun twoFaSet(
        trackId: String,
        password: String,
        hint: String?,
        update: Boolean,
        withEmail: Boolean,
    ) {
        transport
            .request(
                OP_AUTH_SET_2FA,
                buildJsonObject {
                    put("trackId", trackId)
                    put("password", password)
                    if (!hint.isNullOrBlank()) put("hint", hint)
                    putJsonArray("expectedCapabilities") {
                        add(if (update) CAP_UPDATE_PASSWORD else CAP_SET_PASSWORD)
                        if (!hint.isNullOrBlank()) add(CAP_HINT)
                        if (withEmail) add(CAP_EMAIL)
                    }
                },
            ).orAuthFailure("Не удалось установить пароль")
    }

    override suspend fun restoreSendEmailCode(trackId: String): EmailCodeInfo {
        // No `email`: the code goes to the address already linked to the account.
        val payload =
            transport
                .request(OP_AUTH_VERIFY_EMAIL, buildJsonObject { put("trackId", trackId) })
                .orAuthFailure("Не удалось отправить код на почту")
        val blocking = payload["blockingDuration"]?.jsonPrimitive?.longOrNullSafe() ?: 0L
        return EmailCodeInfo(
            codeLength = payload["codeLength"]?.jsonPrimitive?.intOrNullSafe(),
            // Sent in seconds; anything implausibly large for seconds is already ms.
            resendAfterMs = if (blocking > 3_600) blocking else blocking * 1_000,
        )
    }

    override suspend fun restoreCheckEmailCode(
        trackId: String,
        code: String,
    ) = twoFaCheckEmail(trackId, code)

    override suspend fun restorePassword(
        trackId: String,
        password: String,
        hint: String?,
    ): String {
        val payload =
            transport
                .request(
                    OP_AUTH_LOGIN_RESTORE_PASSWORD,
                    buildJsonObject {
                        put("trackId", trackId)
                        put("password", password)
                        if (!hint.isNullOrBlank()) put("hint", hint)
                    },
                ).orAuthFailure("Не удалось восстановить пароль")
        return payload.loginToken() ?: error("Пароль изменён, но войти не удалось — войдите с новым паролем")
    }

    override suspend fun twoFaRemove(trackId: String) {
        transport
            .request(
                OP_AUTH_SET_2FA,
                buildJsonObject {
                    put("trackId", trackId)
                    put("remove2fa", true)
                    putJsonArray("expectedCapabilities") { add(CAP_REMOVE_2FA) }
                },
            ).orAuthFailure("Не удалось отключить пароль")
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

    override suspend fun pressBotButton(button: BotButton) {
        // MSG_SEND_CALLBACK (op 118) {callbackId, type, payload, timestamp}: no chat or message id.
        val callbackId = button.callbackId ?: return
        val payload =
            transport.request(
                OP_MSG_SEND_CALLBACK,
                buildJsonObject {
                    put("callbackId", callbackId)
                    put("type", "CALLBACK")
                    button.payload?.let { put("payload", it) }
                    put("timestamp", nowMillis())
                },
            )
        payload.errorCode()?.let { error(payload.serverMessage("Бот не ответил на кнопку")) }
    }

    override suspend fun votePoll(
        chatId: Long,
        messageId: String,
        poll: Poll,
        answerIds: List<Int>,
    ): Poll? {
        // SEND_VOTE (op 304) {chatId, messageId, pollId, answersIds}; ids as plain small ints
        // (64-bit ones are refused), an empty list retracts. Answers {state}.
        val mid = messageId.toLongOrNull() ?: return null
        val payload =
            transport.request(
                OP_SEND_VOTE,
                buildJsonObject {
                    put("chatId", chatId)
                    put("messageId", mid)
                    put("pollId", poll.pollId)
                    putJsonArray("answersIds") { answerIds.forEach { add(it) } }
                },
            )
        payload.errorCode()?.let { error(payload.serverMessage("Не удалось проголосовать")) }
        return (payload["state"] as? JsonObject)?.let { poll.withState(it) }
    }

    override suspend fun getReactionUsers(
        chatId: Long,
        messageId: String,
    ): List<Pair<Long, String>> {
        // MSG_GET_DETAILED_REACTIONS (op 181) {chatId, messageId, count} -> {reactions: [{userId, reaction}], …}.
        val mid = messageId.toLongOrNull() ?: return emptyList()
        val payload =
            transport.request(
                OP_DETAILED_REACTIONS,
                buildJsonObject {
                    put("chatId", chatId)
                    put("messageId", mid)
                    put("count", 100)
                },
            )
        payload.errorCode()?.let { error(payload.serverMessage("Не удалось загрузить реакции")) }
        return (payload["reactions"] as? JsonArray).orEmptyList().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val user = o["userId"]?.jsonPrimitive?.longOrNullSafe() ?: return@mapNotNull null
            emojiOf(o["reaction"])?.let { user to it }
        }
    }

    override suspend fun markUnread(
        chatId: Long,
        mark: Long,
    ) {
        // CHAT_MARK (op 50) {type: SET_AS_UNREAD, chatId, mark}, no messageId: the frame the web
        // client was captured sending (vkmax). `mark` there was a recent ms time; ours is the
        // chat's last message time (unverified guess: the server may want its read mark).
        val payload =
            transport.request(
                OP_MARK_READ,
                buildJsonObject {
                    put("type", "SET_AS_UNREAD")
                    put("chatId", chatId)
                    put("mark", mark)
                },
            )
        payload.errorCode()?.let { error(payload.serverMessage("Не удалось отметить непрочитанным")) }
    }

    override suspend fun clearHistory(
        chatId: Long,
        lastEventTime: Long,
    ) {
        // CHAT_CLEAR (op 54) {chatId, lastEventTime, forAll}; answers {}. Only for us: what
        // forAll does to the others was never measured, so it is not offered.
        val payload =
            transport.request(
                OP_CHAT_CLEAR,
                buildJsonObject {
                    put("chatId", chatId)
                    put("lastEventTime", lastEventTime)
                    put("forAll", false)
                },
            )
        payload.errorCode()?.let { error(payload.serverMessage("Не удалось стереть переписку")) }
    }

    override suspend fun setBlocked(
        userId: Long,
        blocked: Boolean,
    ) {
        // CONTACT_UPDATE (op 34) {contactId, action: BLOCK | UNBLOCK}; answers {}. Works for non-contacts too.
        val payload =
            transport.request(
                OP_CONTACT_UPDATE,
                buildJsonObject {
                    put("contactId", userId)
                    put("action", if (blocked) "BLOCK" else "UNBLOCK")
                },
            )
        payload.errorCode()?.let { error(payload.serverMessage(if (blocked) "Не удалось заблокировать" else "Не удалось разблокировать")) }
    }

    override suspend fun getStickerShelf(): StickerShelf {
        // ASSETS_UPDATE (op 27) {type: STICKER, sync: 0} -> {sync, sections: [{id, type, title, stickerSets, stickers}], …},
        // then the sets themselves by id (op 28): two requests, once a session.
        val payload =
            transport.request(
                OP_ASSETS_UPDATE,
                buildJsonObject {
                    put("type", "STICKER")
                    put("sync", 0)
                },
            )
        payload.errorCode()?.let { error(payload.serverMessage("Не удалось загрузить стикеры")) }
        val (setIds, recent) = parseStickerSections(payload)
        if (setIds.isEmpty()) return StickerShelf(emptyList(), recent)
        val sets =
            transport.request(
                OP_ASSETS_GET_BY_IDS,
                buildJsonObject {
                    put("type", "STICKER_SET")
                    putJsonArray("ids") { setIds.forEach { add(it) } }
                },
            )
        sets.errorCode()?.let { error(sets.serverMessage("Не удалось загрузить стикеры")) }
        val byId = parseStickerSets(sets).associateBy { it.id }
        return StickerShelf(setIds.mapNotNull { byId[it] }, recent)
    }

    override suspend fun getStickers(ids: List<Long>): List<Sticker> =
        // ASSETS_GET_BY_IDS (op 28) {type: STICKER, ids} -> {stickers: [{id, url, lottieUrl, width, height, …}]}.
        ids.chunked(STICKERS_PER_REQUEST).flatMap { chunk ->
            val payload =
                transport.request(
                    OP_ASSETS_GET_BY_IDS,
                    buildJsonObject {
                        put("type", "STICKER")
                        putJsonArray("ids") { chunk.forEach { add(it) } }
                    },
                )
            payload.errorCode()?.let { error(payload.serverMessage("Не удалось загрузить стикеры")) }
            parseStickers(payload)
        }

    override suspend fun getFolders(): FoldersUpdate {
        // FOLDERS_GET (op 272) {folderSync: 0} = everything -> {foldersOrder, folders, folderSync, allFilterExcludeFolders}.
        val payload = transport.request(OP_FOLDERS_GET, buildJsonObject { put("folderSync", 0) })
        payload.errorCode()?.let { error(payload.serverMessage("Не удалось загрузить папки")) }
        return parseFolders(payload)
    }

    override suspend fun saveFolder(folder: ChatFolder) {
        // FOLDERS_UPDATE (op 274) with the whole folder, in the official client's key order: a field
        // left out is emptied. Answers {folder, folderSync, foldersOrder}; NOTIF_FOLDERS follows.
        val payload =
            transport.request(
                OP_FOLDERS_UPDATE,
                buildJsonObject {
                    put("id", folder.id)
                    put("title", folder.title)
                    putJsonArray("include") { folder.include.forEach { add(it) } }
                    putJsonArray("favorites") { folder.favorites.forEach { add(it) } }
                    put("filters", folder.filters)
                    put("options", folder.options)
                },
            )
        if (payload.errorCode() == "folder.validation.title.too-long") error("Слишком длинное название папки")
        payload.errorCode()?.let { error(payload.serverMessage("Не удалось сохранить папку")) }
    }

    override suspend fun reorderFolders(order: List<String>) {
        // FOLDERS_REORDER (op 275) {foldersOrder}: every id; `all.chat.folder` must stay first.
        val payload = transport.request(OP_FOLDERS_REORDER, buildJsonObject { putJsonArray("foldersOrder") { order.forEach { add(it) } } })
        payload.errorCode()?.let { error(payload.serverMessage("Не удалось изменить порядок папок")) }
    }

    override suspend fun deleteFolder(id: String) {
        // FOLDERS_DELETE (op 276) {folderIds}: the folder goes, its chats stay.
        val payload = transport.request(OP_FOLDERS_DELETE, buildJsonObject { putJsonArray("folderIds") { add(id) } })
        payload.errorCode()?.let { error(payload.serverMessage("Не удалось удалить папку")) }
    }

    override suspend fun getBlockedPage(from: Int): BlockedPage {
        // CONTACT_LIST (op 36) {status: "BLOCKED", from (only when > 0), count: 40} -> {contacts}.
        // `status` is required: without it the server drops the connection.
        val payload =
            transport.request(
                OP_CONTACT_LIST,
                buildJsonObject {
                    put("status", "BLOCKED")
                    if (from > 0) put("from", from)
                    put("count", BlockedPage.SIZE)
                },
            )
        payload.errorCode()?.let { error(payload.serverMessage("Не удалось загрузить чёрный список")) }
        val raw = (payload["contacts"] as? JsonArray).orEmptyList()
        return BlockedPage(raw.mapNotNull { (it as? JsonObject)?.let(::parseUser)?.copy(blocked = true) }, raw.size)
    }

    override suspend fun renameContact(
        userId: Long,
        firstName: String,
        lastName: String?,
    ): UserInfo? {
        // CONTACT_UPDATE (op 34) {contactId, action: UPDATE, firstName, lastName}. `lastName` always goes,
        // null when empty: without it the server answers with the old name and changes nothing (max-cli).
        val payload =
            transport.request(
                OP_CONTACT_UPDATE,
                buildJsonObject {
                    put("contactId", userId)
                    put("action", "UPDATE")
                    put("firstName", firstName)
                    put("lastName", lastName)
                },
            )
        payload.errorCode()?.let { error(payload.serverMessage("Не удалось изменить контакт")) }
        return (payload["contact"] as? JsonObject)?.let(::parseUser)
    }

    override suspend fun removeContact(userId: Long) {
        // CONTACT_UPDATE (op 34) {contactId, action: REMOVE}.
        val payload =
            transport.request(
                OP_CONTACT_UPDATE,
                buildJsonObject {
                    put("contactId", userId)
                    put("action", "REMOVE")
                },
            )
        payload.errorCode()?.let { error(payload.serverMessage("Не удалось удалить контакт")) }
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

    /** CONTACT_PRESENCE (op 35): presence of [ids]; a failed chunk is just left out. */
    private suspend fun fetchPresence(ids: List<Long>): Map<Long, Presence> {
        val out = HashMap<Long, Presence>()
        ids.chunked(100).forEach { chunk ->
            val payload =
                runCatching {
                    transport.request(
                        OP_CONTACT_PRESENCE,
                        buildJsonObject { put("contactIds", buildJsonArray { chunk.forEach { add(it) } }) },
                    )
                }.getOrNull()
            out += presenceIn(payload?.get("presence"))
        }
        return out
    }

    /**
     * Batch-resolves users by id via CONTACT_INFO. Since app 26.31.0 the login reply no
     * longer includes contacts inline, [sync] uses this to fetch the people we have
     * dialogs with (for titles and the Contacts page). Chunked to keep each request small.
     */
    private suspend fun fetchContactsByIds(ids: List<Long>): List<UserInfo> {
        if (ids.isEmpty()) return emptyList()
        val out = ArrayList<UserInfo>(ids.size)
        ids.chunked(100).forEach { chunk ->
            val payload =
                transport.request(
                    OP_CONTACT_INFO,
                    buildJsonObject {
                        put("contactIds", buildJsonArray { chunk.forEach { add(it) } })
                    },
                )
            payload["contacts"]?.jsonArray.orEmptyList().mapNotNullTo(out) { parseUser(it.jsonObject) }
        }
        return out
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

    override suspend fun getAudioUrl(
        chatId: Long,
        messageId: Long,
        audioId: Long,
        token: String?,
    ): String? {
        val payload =
            transport.request(
                OP_AUDIO_PLAY,
                buildJsonObject {
                    put("audioId", audioId)
                    // Sent only when meaningful, matching the official client's builder.
                    if (chatId != 0L) put("chatId", chatId)
                    if (messageId > 0) put("messageId", messageId)
                    if (!token.isNullOrBlank()) put("token", token)
                },
            )
        return payload.audioLink()
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

        payload.errorCode()?.let { code ->
            val message = payload.serverMessage("Ошибка синхронизации")
            // These mean the token itself is dead (revoked, expired, account blocked) —
            // the caller must drop the session and re-login rather than keep retrying.
            if (code == "login.token" || code == "auth.token" || code == "login.blocked") throw AuthRejectedException(message)
            error(message)
        }

        val rawChats = payload["chats"]?.jsonArray.orEmptyList().map { it.jsonObject }

        // Per-chat notification settings live under config.chats.<id>.dontDisturbUntil
        // (0 = notifications on; -1 or a future epoch-ms = muted).
        // …and favIndex = when the chat was pinned (epoch ms; 0 or absent = not pinned).
        val chatSettings = ((payload["config"] as? JsonObject)?.get("chats") as? JsonObject).orEmpty()
        knownChatSettings.clear()
        chatSettings.forEach { (id, entry) -> id.toLongOrNull()?.let { k -> (entry as? JsonObject)?.let { knownChatSettings[k] = it } } }

        fun chatSetting(
            id: Long,
            key: String,
        ) = ((chatSettings[id.toString()] as? JsonObject)?.get(key) as? JsonPrimitive)?.longOrNullSafe() ?: 0L

        // As of app 26.31.0 the login reply no longer carries our own profile or the
        // contact list inline — only chats. So we derive our own id from the chats and
        // fetch the people we talk to by id.
        //
        // myId: our own account is the participant common to our DIALOGs (present in the
        // most of them). Robust for any account with a couple of one-to-one chats.
        val dialogParticipantIds =
            rawChats
                .filter { it["type"]?.jsonPrimitive?.contentOrNullSafe() == "DIALOG" }
                .map { c -> c["participants"]?.jsonObject?.keys?.mapNotNull { it.toLongOrNull() } ?: emptyList() }
        val freq = HashMap<Long, Int>()
        dialogParticipantIds.forEach { set -> set.forEach { id -> freq[id] = (freq[id] ?: 0) + 1 } }
        val myId = freq.maxByOrNull { it.value }?.key ?: 0L
        this.myId = myId // parseChat resolves dialog partners against this

        // Names for the id->name map (dialog titles) and the Contacts page: fetch the
        // dialog partners (and ourselves, for the account card) by id.
        val fetched = fetchContactsByIds((dialogParticipantIds.flatten() + myId).distinct().filter { it != 0L })
        val names = fetched.associate { it.id to it.name }.toMutableMap()
        val me = fetched.firstOrNull { it.id == myId }
        val account =
            Account(
                userId = myId,
                // Kept apart (not the joined display name), so the profile editor shows each in its field.
                firstName = me?.firstName ?: me?.name ?: "Я",
                lastName = me?.lastName,
                avatarUrl = me?.avatarUrl,
                twoFaEnabled = me?.twoFaEnabled,
            )
        val contactsList = fetched.filter { it.id != myId }.sortedBy { it.name.lowercase() }

        // --- chats ---
        val chats =
            rawChats
                .mapNotNull { parseChat(it, names) }
                .map { it.copy(muted = chatSetting(it.id, "dontDisturbUntil") != 0L, favIndex = chatSetting(it.id, "favIndex")) }
                .sortedByDescending { it.lastEventTime }

        // presence = {userId: {seen: <unixSec>, status: <int>}} — a user is online
        // when a `status` field is present (offline entries carry only `seen`).
        // Newer servers no longer put presence in the login reply; ask for the people we
        // talk to explicitly (this also subscribes us to their NOTIF_PRESENCE pushes).
        val presence = presenceIn(payload["presence"]) + fetchPresence(contactsList.map { it.id })
        val online = presence.filterValues { it.online }.keys

        // The server returns a (possibly rolled) login token here — persist it.
        val refreshedToken = payload["token"]?.jsonPrimitive?.contentOrNullSafe()

        // Answer the server's host-reachability expectation once the session is live.
        // Sync runs on every (re)connect and pull-to-refresh, which is a good proxy
        // for the "came to foreground" moment the official client uses. Fire-and-forget
        // on our own scope so it never delays the sync result.
        scope.launch { runCatching { reportHostReachability() } }

        val privacy = ((payload["config"] as? JsonObject)?.get("user") as? JsonObject)?.toPrivacy()
        val reactionsMenu =
            (((payload["config"] as? JsonObject)?.get("server") as? JsonObject)?.get("reactions-menu") as? JsonArray)
                .orEmptyList()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNullSafe()?.ifBlank { null } }
        val server = (payload["config"] as? JsonObject)?.get("server") as? JsonObject
        // How many chats may be pinned: the older official client reads `max-favorite-chats`, the newer `favorite.chats.limit`.
        val pinLimit =
            listOf("max-favorite-chats", "favorite.chats.limit")
                .firstNotNullOfOrNull { (server?.get(it) as? JsonPrimitive)?.intOrNullSafe() }
                ?.takeIf { it > 0 }
        val pollsInDialogs =
            (server?.get("polls-in-p2p-chats") as? JsonPrimitive)?.let { it.asBoolean() ?: ((it.intOrNullSafe() ?: 0) != 0) } ?: false
        return SyncResult(
            account,
            chats,
            names,
            contactsList,
            online,
            refreshedToken,
            presence,
            privacy,
            reactionsMenu,
            pinLimit,
            pollsInDialogs,
        )
    }

    override suspend fun fetchUsers(ids: List<Long>): List<UserInfo> = fetchContactsByIds(ids)

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
    override suspend fun searchChats(
        query: String,
        marker: Long?,
    ): SearchPage {
        val payload =
            transport.request(
                OP_PUBLIC_SEARCH,
                buildJsonObject {
                    put("query", query)
                    put("count", 20)
                    // The newer official client pages with the reply's marker, sent only when non-zero.
                    if (marker != null && marker != 0L) put("marker", marker)
                    put("type", "ALL")
                },
            )
        val results =
            payload["result"]
                ?.jsonArray
                .orEmptyList()
                .mapNotNull { el ->
                    val chat = el.jsonObject["chat"]?.jsonObject ?: return@mapNotNull null
                    val id = chat["id"]?.jsonPrimitive?.longOrNullSafe() ?: return@mapNotNull null
                    val type = chat["type"]?.jsonPrimitive?.contentOrNullSafe()
                    val title = chat["title"]?.jsonPrimitive?.contentOrNullSafe()?.ifBlank { null } ?: "Чат $id"
                    SearchResult(chatId = id, title = title, avatarUrl = chat.avatarUrl(), isDialog = type == "DIALOG")
                }.distinctBy { it.chatId }
        return SearchPage(results, payload["marker"]?.jsonPrimitive?.longOrNullSafe()?.takeIf { it != 0L })
    }

    override suspend fun searchMessages(
        chatId: Long?,
        query: String,
    ): List<MessageHit> {
        // MSG_SEARCH (73) {chatId, query, count: 100} in one chat; CHAT_SEARCH (68) {query, count: 30}
        // across all of them (one hit per chat). Both answer {result: [{chatId, message, chat?}], total, marker?}.
        val payload =
            transport.request(
                if (chatId != null) OP_MSG_SEARCH else OP_CHAT_SEARCH,
                buildJsonObject {
                    if (chatId != null) put("chatId", chatId)
                    put("query", query)
                    put("count", if (chatId != null) 100 else 30)
                },
            )
        payload.errorCode()?.let { error(payload.serverMessage("Не удалось найти сообщения")) }
        return parseMessageHits(payload, chatId)
    }

    /** The message hits of a MSG_SEARCH / CHAT_SEARCH reply; results without a message (chats, channels) are skipped. */
    internal fun parseMessageHits(
        payload: JsonObject,
        chatId: Long?,
    ): List<MessageHit> =
        (payload["result"] as? JsonArray).orEmptyList().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val chat = o["chat"] as? JsonObject
            val cid =
                (o["chatId"] as? JsonPrimitive)?.longOrNullSafe()
                    ?: (chat?.get("id") as? JsonPrimitive)?.longOrNullSafe()
                    ?: chatId
                    ?: return@mapNotNull null
            val message = (o["message"] as? JsonObject)?.let { parseMessage(it, cid) } ?: return@mapNotNull null
            MessageHit(message, chat?.let { parseChat(it, emptyMap()) })
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
        spans: List<TextSpan>,
    ): Message? {
        val payload =
            transport.request(
                OP_SEND_MESSAGE,
                buildJsonObject {
                    put("chatId", chatId)
                    putJsonObject("message") {
                        put("text", text)
                        put("cid", cid)
                        // Formatting runs, UTF-16 offsets (as Kotlin's): {type, from, length}.
                        put("elements", spans.toElements())
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
                                    is OutAttach.VideoNote ->
                                        addJsonObject {
                                            // Same VIDEO attach as a plain video; `videoType`
                                            // is what makes it a round message. The value is
                                            // inferred from the upload media types (0 VIDEO,
                                            // 1 VIDEO_MESSAGE) — the attach enum's own numbers
                                            // aren't recoverable from the official client.
                                            put("_type", "VIDEO")
                                            put("token", a.token)
                                            put("videoType", VIDEO_TYPE_VIDEO_NOTE)
                                            if (a.durationSeconds > 0) put("duration", a.durationSeconds * 1000L)
                                        }
                                    // A new poll: no pollId (that is only for edits), answers as their text alone.
                                    is OutAttach.Poll ->
                                        addJsonObject {
                                            put("_type", "POLL")
                                            put("title", a.title)
                                            putJsonArray("answers") { a.answers.forEach { t -> addJsonObject { put("text", t) } } }
                                            put("settings", a.settings)
                                        }
                                    // Only the id: the official serializer writes nothing else.
                                    is OutAttach.Sticker ->
                                        addJsonObject {
                                            put("_type", "STICKER")
                                            put("stickerId", a.stickerId)
                                        }
                                    is OutAttach.Voice ->
                                        addJsonObject {
                                            // Shape per the official client's audio attach:
                                            // the upload token stands in for an audioId on
                                            // a fresh recording. `wave` is optional there
                                            // and omitted here — it is a raw byte array in
                                            // the protocol, and we have no confirmation of
                                            // how it survives this transport.
                                            put("_type", "AUDIO")
                                            put("token", a.token)
                                            // Milliseconds, matching what the server sends us.
                                            if (a.durationSeconds > 0) put("duration", a.durationSeconds * 1000L)
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
        spans: List<TextSpan>?,
    ) {
        // MSG_EDIT (op 67): {chatId, messageId (numeric), text, elements} (official app: aic).
        // Without `elements` the edit drops the message's formatting. `attachments` stays
        // out: only attachment-free messages are editable here, and what omitting it does
        // was never measured.
        val mid = messageId.toLongOrNull() ?: return
        val payload =
            transport.request(
                OP_MSG_EDIT,
                buildJsonObject {
                    put("chatId", chatId)
                    put("messageId", mid)
                    put("text", text)
                    spans?.let { put("elements", it.toElements()) }
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
        val payload =
            transport.request(
                OP_MSG_DELETE,
                buildJsonObject {
                    put("chatId", chatId)
                    putJsonArray("messageIds") { ids.forEach { add(it) } }
                    put("forMe", !forAll)
                    put("itemType", "REGULAR")
                },
            )
        payload.errorCode()?.let { error(payload.serverMessage("Не удалось удалить сообщения")) }
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

    override suspend fun uploadVideoNote(
        content: MediaContent,
        durationSeconds: Int,
        onProgress: ((Float) -> Unit)?,
    ): OutAttach.VideoNote {
        val data =
            transport.request(
                OP_VIDEO_UPLOAD,
                buildJsonObject {
                    put("type", UPLOAD_TYPE_VIDEO_MESSAGE)
                    put("count", 1)
                },
            )
        val info =
            data["info"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: error(data.serverMessage("Не удалось начать загрузку видеосообщения"))
        val url = info["url"]?.jsonPrimitive?.contentOrNullSafe() ?: error("Нет ссылки для загрузки видеосообщения")
        val token = info["token"]?.jsonPrimitive?.contentOrNullSafe() ?: error("Сервер не вернул токен видео")
        val response = putContent(url, "video_note.mp4", "video/mp4", content, onProgress)
        if (!response.status.isSuccess()) error("Загрузка видеосообщения не удалась (${response.status.value})")
        return OutAttach.VideoNote(token = token, durationSeconds = durationSeconds)
    }

    override suspend fun uploadVoice(
        content: MediaContent,
        durationSeconds: Int,
        onProgress: ((Float) -> Unit)?,
    ): OutAttach.Voice {
        // VIDEO_UPLOAD serves every media kind; `type` picks which. 2 = AUDIO, per the
        // official client's upload request (0 VIDEO, 1 VIDEO_MESSAGE, 2 AUDIO).
        val data =
            transport.request(
                OP_VIDEO_UPLOAD,
                buildJsonObject {
                    put("type", UPLOAD_TYPE_AUDIO)
                    put("count", 1)
                },
            )
        val info =
            data["info"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: error(data.serverMessage("Не удалось начать загрузку аудио"))
        val url = info["url"]?.jsonPrimitive?.contentOrNullSafe() ?: error("Нет ссылки для загрузки аудио")
        val token = info["token"]?.jsonPrimitive?.contentOrNullSafe() ?: error("Сервер не вернул токен аудио")
        val response = putContent(url, "voice.m4a", "audio/mp4", content, onProgress)
        if (!response.status.isSuccess()) error("Загрузка аудио не удалась (${response.status.value})")
        // No wait for a processing notification here, unlike video and file uploads:
        // the server doesn't appear to announce audio, so waiting for one stalled every
        // voice message for the full timeout with its progress ring already full. The
        // caller retries the send briefly instead, which is quick when the clip is
        // already usable.
        return OutAttach.Voice(token = token, durationSeconds = durationSeconds)
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
        val payload =
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
        payload.errorCode()?.let {
            error(
                payload.serverMessage(
                    if (emoji !=
                        null
                    ) {
                        "Не удалось поставить реакцию"
                    } else {
                        "Не удалось убрать реакцию"
                    },
                ),
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

    override suspend fun joinByLink(link: String): Chat? {
        val payload = transport.request(OP_CHAT_JOIN, buildJsonObject { put("link", link) })
        // The reply carries the chat on success. Its exact shape isn't documented, so
        // both spellings are tried and a miss is not an error — the caller re-syncs and
        // finds the chat by its link instead.
        val chat =
            (payload["chat"] as? JsonObject)
                ?: (payload["chats"] as? JsonArray)?.firstOrNull()?.jsonObject
        if (chat == null && payload["error"] != null) {
            error(payload.serverMessage("Не удалось перейти по ссылке"))
        }
        return chat?.let { parseChat(it, emptyMap()) }
    }

    override suspend fun leaveGroup(chatId: Long) {
        // CHAT_LEAVE payload is just {chatId}.
        val payload = transport.request(OP_LEAVE_CHAT, buildJsonObject { put("chatId", chatId) })
        payload.errorCode()?.let { error(payload.serverMessage("Не удалось выйти из чата")) }
    }

    /**
     * Re-reads [chatId]'s chat object to get its `participants` read marks. Synchronous
     * refresh for the "who read this message" list: the sync call's copy can be stale
     * by the time the user opens a message hours later.
     */
    override suspend fun getReadMarks(chatId: Long): Map<Long, Long> {
        val payload =
            transport.request(
                OP_CHAT_INFO,
                buildJsonObject { put("chatId", chatId) },
                // Best-effort: the list is also seeded from sync + op-130 pushes, so a
                // slow or unsupported opcode must not hold the sheet hostage.
                timeoutMs = 10_000,
            )
        val chat =
            (payload["chat"] as? JsonObject)
                ?: (payload["chats"] as? JsonArray)?.firstOrNull()?.jsonObject
        return chat?.let { parseChat(it, emptyMap()) }?.readMarks ?: emptyMap()
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
    ): Long? = createChat("CHAT", title, memberIds, photoToken, "Не удалось создать группу")

    // The same CONTROL "new" with chatType CHANNEL makes a (private) channel (max-cli, measured).
    override suspend fun createChannel(
        title: String,
        photoToken: String?,
    ): Long? = createChat("CHANNEL", title, emptyList(), photoToken, "Не удалось создать канал")

    override suspend fun editChat(
        chatId: Long,
        title: String?,
        description: String?,
        photoToken: String?,
        options: Map<String, Boolean>,
    ): Chat? =
        // CHAT_UPDATE (op 55), keys in the official client's order; the name is `theme`. Options carry
        // only the flags that change. Answers {chat}.
        chatUpdate(chatId, "Не удалось изменить чат") {
            description?.let { put("description", it) }
            if (options.isNotEmpty()) putJsonObject("options") { options.forEach { (k, v) -> put(k, v) } }
            title?.let { put("theme", it) }
            photoToken?.let { put("photoToken", it) }
        }

    override suspend fun revokeChatLink(chatId: Long): Chat? =
        // CHAT_UPDATE (op 55) {chatId, revokePrivateLink: true} -> {chat} with the new `link`.
        chatUpdate(chatId, "Не удалось сбросить ссылку") { put("revokePrivateLink", true) }

    private suspend fun chatUpdate(
        chatId: Long,
        failure: String,
        fields: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): Chat? {
        val payload =
            transport.request(
                OP_CHAT_UPDATE,
                buildJsonObject {
                    put("chatId", chatId)
                    fields()
                },
            )
        payload.errorCode()?.let { error(payload.serverMessage(failure)) }
        return (payload["chat"] as? JsonObject)?.let { parseChat(it, emptyMap()) }
    }

    private suspend fun createChat(
        chatType: String,
        title: String,
        memberIds: List<Long>,
        photoToken: String?,
        failure: String,
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
                                put("chatType", chatType)
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
            error(payload.serverMessage(failure))
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

    internal fun parseMessage(
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
        // Formatting runs (`elements`): {type, from (absent = 0), length, attributes:{url}}.
        // Offsets index the server's text, so they are dropped if we replaced it.
        val spans =
            if (baseText != content["text"]?.jsonPrimitive?.contentOrNullSafe()) {
                emptyList()
            } else {
                (content["elements"] as? JsonArray).orEmptyList().mapNotNull { el ->
                    val e = el as? JsonObject ?: return@mapNotNull null
                    val type = e["type"]?.jsonPrimitive?.contentOrNullSafe() ?: return@mapNotNull null
                    val length = e["length"]?.jsonPrimitive?.intOrNullSafe() ?: return@mapNotNull null
                    TextSpan(
                        type = type,
                        from = e["from"]?.jsonPrimitive?.intOrNullSafe() ?: 0,
                        length = length,
                        url = (e["attributes"] as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNullSafe(),
                    )
                }
            }
        // A bot's buttons: {callbackId, keyboard: {buttons: rows of {text, type, url?, payload?…}}}.
        val keyboard =
            attaches.firstNotNullOfOrNull { el ->
                (el as? JsonObject)?.takeIf { it["_type"]?.jsonPrimitive?.contentOrNullSafe() == "INLINE_KEYBOARD" }
            }
        val callbackId = keyboard?.get("callbackId")?.jsonPrimitive?.contentOrNullSafe()
        val buttons =
            ((keyboard?.get("keyboard") as? JsonObject)?.get("buttons") as? JsonArray)
                .orEmptyList()
                .mapNotNull { row ->
                    (row as? JsonArray)
                        ?.mapNotNull { btn ->
                            val o = btn as? JsonObject ?: return@mapNotNull null
                            val label = o["text"]?.jsonPrimitive?.contentOrNullSafe()?.ifBlank { null } ?: return@mapNotNull null
                            BotButton(
                                text = label,
                                url = o["url"]?.jsonPrimitive?.contentOrNullSafe()?.ifBlank { null },
                                type = o["type"]?.jsonPrimitive?.contentOrNullSafe(),
                                payload = o["payload"]?.jsonPrimitive?.contentOrNullSafe(),
                                callbackId = callbackId,
                            )
                        }?.ifEmpty { null }
                }
        // `url` is the sticker's still image for every kind; a LOTTIE one also has a
        // `lottieUrl` (Lottie JSON) we can't play, so the still stands in for it.
        val stickerUrl =
            attaches.firstNotNullOfOrNull { el ->
                (el as? JsonObject)
                    ?.takeIf { it["_type"]?.jsonPrimitive?.contentOrNullSafe() == "STICKER" }
                    ?.let { (it["url"] ?: it["baseUrl"])?.jsonPrimitive?.contentOrNullSafe() }
            }
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

        val poll =
            attaches.firstNotNullOfOrNull { el ->
                (el as? JsonObject)?.takeIf { it["_type"]?.jsonPrimitive?.contentOrNullSafe() == "POLL" }?.let(::parsePoll)
            }

        fun JsonObject.typed(type: String) = takeIf { it["_type"]?.jsonPrimitive?.contentOrNullSafe() == type }
        val location =
            attaches.firstNotNullOfOrNull { el ->
                val a = (el as? JsonObject)?.typed("LOCATION") ?: return@firstNotNullOfOrNull null
                val lat = a["latitude"]?.jsonPrimitive?.contentOrNullSafe()?.toDoubleOrNull() ?: return@firstNotNullOfOrNull null
                val lon = a["longitude"]?.jsonPrimitive?.contentOrNullSafe()?.toDoubleOrNull() ?: return@firstNotNullOfOrNull null
                GeoPoint(lat, lon)
            }
        val contact =
            attaches.firstNotNullOfOrNull { el ->
                val a = (el as? JsonObject)?.typed("CONTACT") ?: return@firstNotNullOfOrNull null
                val vcf = a["vcfBody"]?.jsonPrimitive?.contentOrNullSafe()
                val name =
                    a["name"]?.jsonPrimitive?.contentOrNullSafe()?.ifBlank { null }
                        ?: listOfNotNull(a["firstName"], a["lastName"])
                            .mapNotNull { it.jsonPrimitive.contentOrNullSafe() }
                            .joinToString(" ")
                            .ifBlank { null }
                        ?: vcf?.let {
                            Regex("""(?m)^FN[^:]*:(.+)$""")
                                .find(it)
                                ?.groupValues
                                ?.get(1)
                                ?.trim()
                        }
                        ?: "Контакт"
                ContactCard(
                    userId = a["contactId"]?.jsonPrimitive?.longOrNullSafe() ?: 0L,
                    name = name,
                    phone =
                        a["phone"]?.jsonPrimitive?.contentOrNullSafe()?.ifBlank { null }
                            ?: vcf?.let {
                                Regex("""(?m)^TEL[^:]*:(.+)$""")
                                    .find(it)
                                    ?.groupValues
                                    ?.get(1)
                                    ?.trim()
                            },
                )
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
                            ?.let {
                                MediaAttach(
                                    type = MediaType.VIDEO,
                                    url = it,
                                    width = w,
                                    height = h,
                                    videoId = a["videoId"]?.jsonPrimitive?.longOrNullSafe() ?: 0L,
                                    durationSec = a["duration"]?.jsonPrimitive?.intOrNullSafe() ?: 0,
                                    // A round video message carries a videoType; an
                                    // unexpected value simply renders as a plain video.
                                    isVideoNote =
                                        a["videoType"]?.jsonPrimitive?.intOrNullSafe() == VIDEO_TYPE_VIDEO_NOTE,
                                )
                            }
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
        val voice =
            attaches.firstNotNullOfOrNull { el ->
                val a = el.jsonObject
                if (a["_type"]?.jsonPrimitive?.contentOrNullSafe() != "AUDIO") return@firstNotNullOfOrNull null
                val audioId = a["audioId"]?.jsonPrimitive?.longOrNullSafe() ?: return@firstNotNullOfOrNull null
                VoiceAttach(
                    audioId = audioId,
                    durationSeconds = audioDurationSeconds(a["duration"]?.jsonPrimitive?.longOrNullSafe()),
                    waveform = parseWaveform(a["wave"]),
                    token = a["token"]?.jsonPrimitive?.contentOrNullSafe(),
                )
            }
        // Non-renderable attaches still get a text label so they aren't invisible.
        // For SHARE we surface the actual URL/title so it renders as a tappable link.
        val mediaLabel =
            attaches.firstNotNullOfOrNull { el ->
                val a = el.jsonObject
                when (a["_type"]?.jsonPrimitive?.contentOrNullSafe()) {
                    // Only as a stand-in: a voice attach we could parse gets a real
                    // bubble instead, and its chat-list line comes from previewLabel().
                    "AUDIO" -> if (voice == null) "🎵 Голосовое сообщение" else null
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
        // A CALL attach is a call-history entry: {callType, hangupType, duration(ms), contactIds}.
        // It carries no text, so without a label the bubble renders empty.
        val callLabel =
            attaches
                .firstNotNullOfOrNull { el ->
                    el.jsonObject.takeIf { it["_type"]?.jsonPrimitive?.contentOrNullSafe() == "CALL" }
                }?.let { a ->
                    val isVideo = a["callType"]?.jsonPrimitive?.contentOrNullSafe() == "VIDEO"
                    val kind = if (isVideo) "видеозвонок" else "звонок"
                    val durMs = a["duration"]?.jsonPrimitive?.longOrNullSafe() ?: 0L
                    val outgoing = (obj["sender"]?.jsonPrimitive?.longOrNullSafe() ?: 0L) == myId
                    when {
                        durMs > 0 -> {
                            val secs = durMs / 1000
                            val mmss = "${secs / 60}:${(secs % 60).toString().padStart(2, '0')}"
                            (if (outgoing) "Исходящий $kind" else "Входящий $kind") + " · $mmss"
                        }
                        outgoing -> "Отменённый $kind"
                        else -> "Пропущенный $kind"
                    }.replaceFirstChar { it.uppercase() }
                }
        // A call entry renders as a centered system chip (see [service]), never an
        // editable/repliable bubble — so it does NOT go into the message text.
        val callService =
            callLabel?.let {
                ServiceEvent(
                    event = "call",
                    actorId = obj["sender"]?.jsonPrimitive?.longOrNullSafe() ?: 0L,
                    message = it,
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
                        messageId = q["id"]?.jsonPrimitive?.contentOrNullSafe(),
                        senderId = q["sender"]?.jsonPrimitive?.longOrNullSafe() ?: 0L,
                        text = localize(q["text"]?.jsonPrimitive?.contentOrNullSafe() ?: "").ifBlank { "Вложение" },
                    )
                }
        // A CONTROL service event takes precedence; otherwise a call entry is its own
        // service chip.
        val effectiveService = service ?: callService
        // Skip empty service messages with no text, no media and no id.
        if (text.isEmpty() &&
            media.isEmpty() &&
            files.isEmpty() &&
            linkPreview == null &&
            effectiveService == null &&
            stickerUrl == null &&
            poll == null &&
            location == null &&
            contact == null &&
            id == null
        ) {
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
            service = effectiveService,
            voice = voice,
            edited = content["status"]?.jsonPrimitive?.contentOrNullSafe() == "EDITED",
            spans = spans,
            buttons = buttons,
            stickerUrl = stickerUrl,
            views = (obj["stats"] as? JsonObject)?.get("views")?.jsonPrimitive?.intOrNullSafe() ?: 0,
            poll = poll,
            location = location,
            contact = contact,
        )
    }

    /**
     * Parses `reactionInfo = {counters:[{reaction, count}], totalCount, yourReaction}`.
     * `reaction`/`yourReaction` may be a bare emoji string or an object with an `id`.
     */
    private fun parseReactions(info: JsonObject?): List<Reaction> {
        if (info == null) return emptyList()
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

/** Formatting runs in the wire shape: `{type, from, length, attributes: {url}?}` (UTF-16 offsets, as Kotlin's). */
internal fun List<TextSpan>.toElements(): JsonArray =
    buildJsonArray {
        forEach { span ->
            addJsonObject {
                put("type", span.type)
                put("from", span.from)
                put("length", span.length)
                span.url?.let { url -> putJsonObject("attributes") { put("url", url) } }
            }
        }
    }

/** A POLL attach: {pollId, title, settings, answers: [{answerId, text}], state}. */
internal fun parsePoll(a: JsonObject): Poll? {
    val pollId = a["pollId"]?.jsonPrimitive?.longOrNullSafe() ?: return null
    val answers =
        (a["answers"] as? JsonArray).orEmptyList().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            PollAnswer(
                o["answerId"]?.jsonPrimitive?.intOrNullSafe() ?: return@mapNotNull null,
                o["text"]?.jsonPrimitive?.contentOrNullSafe() ?: "",
            )
        }
    return Poll(
        pollId = pollId,
        title = a["title"]?.jsonPrimitive?.contentOrNullSafe() ?: "",
        settings = a["settings"]?.jsonPrimitive?.intOrNullSafe() ?: 0,
        answers = answers,
    ).withState(a["state"] as? JsonObject)
}

/**
 * [state] = {total, result: [{answerId, voteCount, options}]} applied to this poll; bit 1 of
 * `options` marks the answer we voted for. A missing state leaves the poll as it is.
 */
internal fun Poll.withState(state: JsonObject?): Poll {
    if (state == null) return this
    val results =
        (state["result"] as? JsonArray)
            .orEmptyList()
            .mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                (o["answerId"]?.jsonPrimitive?.intOrNullSafe() ?: return@mapNotNull null) to o
            }.toMap()
    return copy(
        total = state["total"]?.jsonPrimitive?.intOrNullSafe() ?: total,
        answers =
            answers.map { a ->
                val r = results[a.id]
                a.copy(
                    votes = r?.get("voteCount")?.jsonPrimitive?.intOrNullSafe() ?: 0,
                    mine = ((r?.get("options")?.jsonPrimitive?.intOrNullSafe() ?: 0) and 1) != 0,
                )
            },
    )
}

private fun idsIn(el: JsonElement?): List<Long> =
    (el as? JsonArray).orEmptyList().mapNotNull { e ->
        (e as? JsonPrimitive)?.longOrNullSafe()
            ?: (e as? JsonObject)?.let { o -> (o["id"] ?: o["stickerId"]) as? JsonPrimitive }?.longOrNullSafe()
    }

/**
 * An ASSETS_UPDATE (STICKER) reply: every sticker-set id in section order, and the recent
 * stickers' ids (a RECENTS section, or `recentsList`).
 */
internal fun parseStickerSections(p: JsonObject): Pair<List<Long>, List<Long>> {
    val sections = (p["sections"] as? JsonArray).orEmptyList().mapNotNull { it as? JsonObject }
    val sets = sections.flatMap { idsIn(it["stickerSets"]) }.distinct()
    val recents = sections.filter { (it["type"] as? JsonPrimitive)?.contentOrNullSafe() == "RECENTS" }.flatMap { idsIn(it["stickers"]) }
    return sets to (recents + idsIn(p["recentsList"])).distinct()
}

/** ASSETS_GET_BY_IDS (STICKER_SET): `{stickerSets: [{id, name, iconUrl, stickers: [ids]}]}`. */
internal fun parseStickerSets(p: JsonObject): List<StickerSet> =
    (p["stickerSets"] as? JsonArray).orEmptyList().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        StickerSet(
            id = (o["id"] as? JsonPrimitive)?.longOrNullSafe() ?: return@mapNotNull null,
            name = (o["name"] as? JsonPrimitive)?.contentOrNullSafe() ?: "",
            iconUrl = (o["iconUrl"] as? JsonPrimitive)?.contentOrNullSafe()?.ifBlank { null },
            stickerIds = idsIn(o["stickers"]),
        )
    }

/** ASSETS_GET_BY_IDS (STICKER): stickers with a still image; `url` is that image for every kind. */
internal fun parseStickers(p: JsonObject): List<Sticker> =
    (p["stickers"] as? JsonArray).orEmptyList().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        Sticker(
            id = (o["id"] as? JsonPrimitive)?.longOrNullSafe() ?: return@mapNotNull null,
            url = (o["url"] as? JsonPrimitive)?.contentOrNullSafe()?.ifBlank { null } ?: return@mapNotNull null,
        )
    }

// The keys of a per-chat CONFIG entry (OFF-25 dc2 o=15 writes them; ss2 reads them back).
private val CHAT_SETTING_KEYS = setOf("dontDisturbUntil", "favIndex", "sound", "vibr", "led")

/**
 * One chat's entry for a CONFIG (op 22) `settings.chats` write, as the official builder makes it: always
 * `dontDisturbUntil`, `sound`, `vibr` and `led` (false when unknown), and `favIndex` when it has one. Starts
 * from [stored], the chat's `config.chats.<id>` as last known, and sets [key] to [value].
 */
internal fun chatSettingsEntry(
    stored: JsonObject?,
    key: String,
    value: Long,
): JsonObject {
    val kept = stored.orEmpty().filter { (k, v) -> k in CHAT_SETTING_KEYS && v is JsonPrimitive && v !is JsonNull }
    val defaults =
        mapOf(
            "dontDisturbUntil" to JsonPrimitive(0),
            "sound" to JsonPrimitive(false),
            "vibr" to JsonPrimitive(false),
            "led" to JsonPrimitive(false),
        )
    return JsonObject(defaults + kept + (key to JsonPrimitive(value)))
}

/** Folders from a FOLDERS_GET reply (`folders`) or a push/update reply (`folder`), with `foldersOrder` when sent. */
internal fun parseFolders(p: JsonObject): FoldersUpdate {
    fun ids(el: JsonElement?) = (el as? JsonArray).orEmptyList().mapNotNull { (it as? JsonPrimitive)?.longOrNullSafe() }
    val folders =
        ((p["folders"] as? JsonArray).orEmptyList() + listOfNotNull(p["folder"])).mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            ChatFolder(
                id = (o["id"] as? JsonPrimitive)?.contentOrNullSafe()?.ifBlank { null } ?: return@mapNotNull null,
                title = (o["title"] as? JsonPrimitive)?.contentOrNullSafe() ?: "",
                include = ids(o["include"]),
                favorites = ids(o["favorites"]),
                filters = o["filters"] as? JsonArray ?: JsonArray(emptyList()),
                options = o["options"] as? JsonArray ?: JsonArray(emptyList()),
            )
        }
    val order = (p["foldersOrder"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNullSafe() }
    return FoldersUpdate(folders, order)
}

/** A reaction's emoji: a bare string, or an object with an `id` (or `reaction`). */
private fun emojiOf(node: JsonElement?): String? =
    when (node) {
        is JsonObject -> node["id"]?.jsonPrimitive?.contentOrNullSafe() ?: node["reaction"]?.jsonPrimitive?.contentOrNullSafe()
        else -> (node as? JsonPrimitive)?.contentOrNullSafe()
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

/** Masked recovery e-mail from the passwordChallenge (shown so the user knows recovery exists). */
private fun JsonObject.passwordEmail(): String? =
    (this["passwordChallenge"] as? JsonObject)?.get("email")?.jsonPrimitive?.contentOrNullSafe()

/** The server's dotted error code (`error` field), e.g. `login.token`, if this is an error reply. */
private fun JsonObject.errorCode(): String? = (this["error"] as? JsonPrimitive)?.contentOrNullSafe()

/** True for an error frame's payload: `{error, message, localizedMessage?}`. */
private fun JsonObject.isErrorReply(): Boolean = errorCode() != null || this["message"] != null || this["localizedMessage"] != null

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

/** The `names` entry to show: our own name for them (`CUSTOM`) when we gave one, else the first. */
private fun JsonObject?.nameEntry(): JsonObject? {
    val entries = (this?.get("names") as? JsonArray).orEmptyList().filterIsInstance<JsonObject>()
    return entries.firstOrNull { (it["type"] as? JsonPrimitive)?.contentOrNullSafe() == "CUSTOM" } ?: entries.firstOrNull()
}

// Trimmed: a stray space the server kept would show, split the name differently and change the avatar's colour.
private fun JsonObject?.nameField(key: String): String? =
    (nameEntry()?.get(key) as? JsonPrimitive)?.contentOrNullSafe()?.trim()?.ifEmpty { null }

// An entry may carry only `name` (max-cli reads just that): it then stands in for the first name.
private fun JsonObject?.firstName(): String? = nameField("firstName") ?: nameField("name")

private fun JsonObject?.lastName(): String? = if (nameField("firstName") != null) nameField("lastName") else null

private fun JsonObject.displayName(): String? {
    val first = firstName()
    val last = lastName()
    return listOfNotNull(first, last).joinToString(" ").ifBlank { null }
}

internal fun JsonObject.toPrivacy(): Privacy {
    fun who(key: String) =
        when (val v = this[key]?.jsonPrimitive?.contentOrNullSafe()) {
            null -> Privacy.ALL
            "NOBODY" -> Privacy.NOBODY
            else -> v
        }
    return Privacy(
        searchByPhone = who("SEARCH_BY_PHONE"),
        incomingCall = who("INCOMING_CALL"),
        chatsInvite = who("CHATS_INVITE"),
        hideOnline = this["HIDDEN"]?.jsonPrimitive?.asBoolean() ?: false,
        showReadMarks = this["SHOW_READ_MARK"]?.jsonPrimitive?.asBoolean() ?: true,
    )
}

/** Parses a presence map `{userId: {seen, status?}}`. */
private fun presenceIn(presence: JsonElement?): Map<Long, Presence> =
    (presence as? JsonObject)
        ?.entries
        ?.mapNotNull { (key, value) -> key.toLongOrNull()?.let { uid -> (value as? JsonObject)?.toPresence(uid) } }
        ?.associateBy { it.userId } ?: emptyMap()

private fun JsonObject.toPresence(userId: Long) =
    Presence(
        userId = userId,
        online = isOnline(),
        seen = this["seen"]?.jsonPrimitive?.longOrNullSafe(),
        status = this["status"]?.jsonPrimitive?.intOrNullSafe(),
    )

/**
 * A presence object means "online" only when its `status` is 1. The official client's
 * values: 0 offline (only `seen` present), 1 online, 2 "was recently", 3 "was long ago"
 * — 2 and 3 must not light the green dot.
 */
private fun JsonObject.isOnline(): Boolean = (this["status"]?.jsonPrimitive?.intOrNullSafe() ?: 0) == 1

/** Best available avatar URL from a user/contact/chat object. */
private fun JsonObject.avatarUrl(): String? =
    (this["baseRawUrl"] ?: this["baseUrl"] ?: this["baseRawIconUrl"] ?: this["baseIconUrl"])
        ?.jsonPrimitive
        ?.contentOrNullSafe()

/** Parses a server user/contact object into [UserInfo]. */
internal fun parseUser(o: JsonObject): UserInfo? {
    val id = o["id"]?.jsonPrimitive?.longOrNullSafe() ?: return null
    return UserInfo(
        id = id,
        name = o.displayName() ?: "Пользователь $id",
        avatarUrl = o.avatarUrl(),
        firstName = o.firstName(),
        lastName = o.lastName()?.ifBlank { null },
        description = o["description"]?.jsonPrimitive?.contentOrNullSafe(),
        phone = o["phone"]?.jsonPrimitive?.contentOrNullSafe(),
        gender = o["gender"]?.jsonPrimitive?.contentOrNullSafe(),
        link = o["link"]?.jsonPrimitive?.contentOrNullSafe(),
        country = o["country"]?.jsonPrimitive?.contentOrNullSafe(),
        registrationTime = o["registrationTime"]?.jsonPrimitive?.longOrNullSafe(),
        blocked = o["status"]?.jsonPrimitive?.contentOrNullSafe() == "BLOCKED",
        // NOTE: a contact's `options` is a different enum from the profile's second-factor
        // options (which only the login-time `profile` object carries), so the password
        // state is not derivable here — it comes from AUTH_2FA_DETAILS instead.
    )
}

/** MessagePack booleans decode as `true`/`false`; some servers send 0/1 — accept both. */
private fun JsonPrimitive.asBoolean(): Boolean? =
    when (contentOrNullSafe()) {
        "true", "1" -> true
        "false", "0" -> false
        else -> null
    }
