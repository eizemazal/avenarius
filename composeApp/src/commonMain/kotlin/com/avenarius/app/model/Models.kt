package com.avenarius.app.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/*
 * Domain models used by the UI. These are deliberately small and decoupled from
 * the raw Max wire format (which is parsed in com.avenarius.app.net.MaxClient).
 *
 * Everything in this file lives in commonMain, so it is shared as-is between the
 * Android app and the desktop app.
 */

/** The signed-in user. */
@Serializable
data class Account(
    val userId: Long,
    val firstName: String,
    val lastName: String? = null,
    val avatarUrl: String? = null,
    /** Login password (2FA) set on the account; null = not reported by the server yet. */
    val twoFaEnabled: Boolean? = null,
)

/** A name-search hit (a contact-dialog or a public chat/channel) that can be opened. */
data class SearchResult(
    val chatId: Long,
    val title: String,
    val avatarUrl: String?,
    val isDialog: Boolean,
    /** Set for an address-book entry with no known chat yet — opened by phone lookup. */
    val phone: String? = null,
    /** A short label shown under the title (e.g. the phone number for book entries). */
    val subtitle: String? = null,
)

/** A phone contact read from the device address book. */
data class DeviceContact(
    val name: String,
    val phone: String,
)

/** A user/contact, used for the contacts list, search results and the profile page. */
@Serializable
data class UserInfo(
    val id: Long,
    val name: String,
    val avatarUrl: String? = null,
    /** The parts of [name], when the server gave them; the profile editor needs them apart. */
    val firstName: String? = null,
    val lastName: String? = null,
    val description: String? = null,
    val phone: String? = null,
    val gender: String? = null,
    val link: String? = null,
    val country: String? = null,
    val registrationTime: Long? = null,
    /** From the profile's `options`; only present for our own profile. */
    val twoFaEnabled: Boolean? = null,
    /** We blocked them (contact `status` BLOCKED). */
    val blocked: Boolean = false,
)

/** A conversation in the chat list. */
@Serializable
data class Chat(
    val id: Long,
    val title: String,
    val lastMessageText: String?,
    val lastEventTime: Long,
    /** Number of unread (incoming) messages, from the server's `newMessages`. */
    val unreadCount: Int = 0,
    /** True for one-to-one dialogs (vs. groups/channels). */
    val isDialog: Boolean = false,
    /** Group/channel avatar URL (dialogs derive their avatar from the contact instead). */
    val avatarUrl: String? = null,
    /**
     * Latest "read up to" timestamp (ms) of the other participant(s), from the
     * chat's `participants` map. Our messages with time <= this are read (✓✓).
     */
    val otherReadMark: Long = 0,
    /**
     * Who has read up to which point in this chat: userId → last read time (ms),
     * taken from the chat's `participants` map and kept current by the op-130
     * push. Excludes us. [otherReadMark] is just the latest value here; the map is
     * what backs the "who read this message" list in groups.
     */
    val readMarks: Map<Long, Long> = emptyMap(),
    /** True for a CHANNEL (broadcast) vs. a regular CHAT group. Dialogs are neither. */
    val isChannel: Boolean = false,
    /** Group/channel owner's user id (0 if unknown/not applicable). */
    val ownerId: Long = 0,
    /** User ids with admin rights (owner is implicitly an admin). */
    val adminIds: Set<Long> = emptySet(),
    /** Ids of all participants (from the chat's `participants` map). */
    val memberIds: Set<Long> = emptySet(),
    /** Total participant count (may exceed [memberIds] for large chats). */
    val participantsCount: Int = 0,
    /** Public join link (`https://…`), if the chat has one. */
    val link: String? = null,
    /** Whether the signed-in user may post here (false for channels we don't run). */
    val canWrite: Boolean = true,
    /** Whether the signed-in user may add members (admins-only in some groups/channels). */
    val canAddMembers: Boolean = false,
    /** True when notifications for this chat are muted (`dontDisturbUntil` != 0). */
    val muted: Boolean = false,
    /** The group's / channel's "about" text, if it has one. */
    val description: String? = null,
    /** A verified account (the server's `OFFICIAL` option): drawn with a check badge. */
    val official: Boolean = false,
    /** A service-notice chat (`SERVICE_CHAT` option) or one with a bot (`hasBots`): no calls, no "last seen". */
    val isServiceChat: Boolean = false,
    /** The group lets every member pin messages (`ALL_CAN_PIN_MESSAGE` option), not only admins. */
    val allCanPin: Boolean = false,
    /** The message pinned at the top of the conversation, if any. */
    val pinnedMessage: Message? = null,
    /** Who wrote the last message (0 if unknown), for the "Имя: …" prefix and the ticks in the list. */
    val lastSenderId: Long = 0,
    /** When the last message was sent (ms); compared with [otherReadMark] for its read tick. */
    val lastMessageTime: Long = 0,
    /** Pinned at the top of the list since this time (server `favIndex`, epoch ms); 0 = not pinned. */
    val favIndex: Long = 0,
    /** The chat's option flags that are on (ONLY_ADMIN_CAN_ADD_MEMBER, ALL_CAN_PIN_MESSAGE, …). */
    val options: Set<String> = emptySet(),
)

/**
 * A server-side chat folder (FOLDERS_GET). [filters] and [options] are kept exactly as the
 * server sent them, because an update has to send the whole folder back.
 */
data class ChatFolder(
    val id: String,
    val title: String,
    /** Chats put in by hand. */
    val include: List<Long> = emptyList(),
    /** Chats pinned inside the folder. */
    val favorites: List<Long> = emptyList(),
    val filters: JsonArray = JsonArray(emptyList()),
    val options: JsonArray = JsonArray(emptyList()),
) {
    /** The server's «Все» folder, which always comes first. */
    val isAll: Boolean get() = id == ALL_ID

    /** Filter ids (the official enum's ordinals); a name the server spelled out is mapped back. */
    val filterIds: List<Int>
        get() =
            filters.mapNotNull { el ->
                val p = el as? JsonPrimitive ?: return@mapNotNull null
                p.content.toIntOrNull() ?: FOLDER_FILTERS.indexOf(p.content).takeIf { it >= 0 }
            }

    companion object {
        const val ALL_ID = "all.chat.folder"

        /** The server's «Каналы» folder (filter CHANNEL); its order may list it with no folder object. */
        const val CHANNELS_ID = "chat.channel.folder"
    }
}

// Folder filter ids, in the official client's enum order (the id is the position).
private val FOLDER_FILTERS =
    listOf(
        "UNREAD",
        "READ",
        "CHANNEL",
        "CHAT",
        "DIALOG",
        "OWNER",
        "ADMIN",
        "MUTED",
        "CONTACT",
        "NOT_CONTACT",
        "BOT",
        "NOT_MUTED",
        "MARKED_UNREAD",
        "ORG",
    )

// Ids that narrow by state (read, mute) rather than name a kind of chat.
private val STATE_FILTERS = setOf(0, 1, 7, 11, 12)

/**
 * Whether [chat] belongs in this folder: put in by hand, or matching its filters.
 * shortcut: nobody described how filters combine; kinds are OR'ed and states AND'ed with them,
 * CONTACT counts every dialog, NOT_CONTACT and ORG match nothing. Upgrade when a mixed folder is measured.
 */
fun ChatFolder.matches(
    chat: Chat,
    myId: Long,
): Boolean {
    if (chat.id in include) return true
    val ids = filterIds
    if (ids.isEmpty()) return isAll

    fun test(f: Int) =
        when (f) {
            0, 12 -> chat.unreadCount > 0
            1 -> chat.unreadCount == 0
            2 -> chat.isChannel
            3 -> !chat.isDialog && !chat.isChannel
            4, 8 -> chat.isDialog
            5 -> chat.ownerId == myId
            6 -> myId in chat.adminIds
            7 -> chat.muted
            10 -> chat.isServiceChat
            11 -> !chat.muted
            else -> false
        }
    val (state, kind) = ids.partition { it in STATE_FILTERS }
    return (kind.isEmpty() || kind.any(::test)) && state.all(::test)
}

/** "Избранное": the dialog with ourselves, which the server keeps under chat id 0. */
val Chat.isSavedMessages: Boolean get() = isDialog && id == 0L

/** Whether [myId] may pin messages here: any dialog, a group when admin or all may, a channel when admin. */
fun Chat.canPinMessages(myId: Long): Boolean {
    val admin = myId == ownerId || myId in adminIds
    return when {
        // The server refuses pinning in a 1:1 dialog (measured by max-cli), Избранное included.
        isDialog -> false
        isChannel -> admin
        else -> admin || allCanPin
    }
}

/** Message delivery state for outgoing messages (server `status`). */
@Serializable
enum class MessageStatus { UNKNOWN, SENT, READ }

@Serializable
enum class MediaType { PHOTO, VIDEO }

/** An image/video attachment we can show inline (by its CDN URL). */
@Serializable
data class MediaAttach(
    val type: MediaType,
    /** Image URL (PHOTO) or thumbnail URL (VIDEO). */
    val url: String,
    val width: Int,
    val height: Int,
    /** For VIDEO: the id needed to resolve the playable stream (opcode 83). */
    val videoId: Long = 0,
    /** For VIDEO: length in seconds (0 = unknown), for the duration badge on the tile. */
    val durationSec: Int = 0,
    /**
     * A round video message ("video note") rather than a plain video, from the
     * attach's `videoType`. Only changes how it is drawn.
     */
    val isVideoNote: Boolean = false,
)

/** One emoji reaction bucket on a message: [emoji] with [count], [mine] if we reacted with it. */
@Serializable
data class Reaction(
    val emoji: String,
    val count: Int,
    val mine: Boolean,
)

/** The quoted message a reply points at (server `link` of type REPLY). */
@Serializable
data class ReplyInfo(
    val senderId: Long,
    val text: String,
    /** Id of the quoted message, to jump to it when it is loaded. */
    val messageId: String? = null,
)

/**
 * A formatted run of a message's text (server `elements`): [type] (STRONG, EMPHASIZED,
 * UNDERLINE, STRIKETHROUGH, MONOSPACED, QUOTE, LINK…) over [length] chars from [from].
 */
@Serializable
data class TextSpan(
    val type: String,
    val from: Int,
    val length: Int,
    /** Target of a LINK span. */
    val url: String? = null,
)

// Run types whose whole meaning is in [TextSpan]; mentions also need an entity id we don't keep.
private val EDITABLE_SPAN_TYPES =
    setOf("STRONG", "EMPHASIZED", "UNDERLINE", "STRIKETHROUGH", "MONOSPACED", "CODE", "HEADING", "QUOTE", "LINK")

/** A run whose whole meaning is in the span itself, so it can be sent back with an edit (mentions can't). */
val TextSpan.editable: Boolean get() = type in EDITABLE_SPAN_TYPES && (type != "LINK" || url != null)

/**
 * [spans] of [old] carried over to its edited version [new], sent back with the edit so
 * the formatting survives it. Only what is certain is kept: runs in the unchanged head keep
 * their offsets, runs in the unchanged tail shift with it, a run wrapping the whole edit
 * stretches over it, and a run cut by the edit is dropped rather than guessed at.
 */
fun remapSpans(
    old: String,
    new: String,
    spans: List<TextSpan>,
): List<TextSpan> {
    val max = minOf(old.length, new.length)
    var head = 0
    while (head < max && old[head] == new[head]) head++
    var tail = 0
    while (tail < max - head && old[old.length - 1 - tail] == new[new.length - 1 - tail]) tail++
    val shift = new.length - old.length
    val editEnd = old.length - tail
    return spans
        .filter { it.editable }
        .mapNotNull { s ->
            val end = s.from + s.length
            when {
                end <= head -> s
                s.from >= editEnd -> s.copy(from = s.from + shift)
                s.from <= head && end >= editEnd -> s.copy(length = s.length + shift).takeIf { it.length > 0 }
                else -> null
            }
        }
}

/**
 * [type] over [from]..[from]+[length] put on, or taken off when exactly that run is already there:
 * the composer's formatting bar.
 */
fun List<TextSpan>.toggled(
    type: String,
    from: Int,
    length: Int,
): List<TextSpan> {
    if (length <= 0) return this
    val same = TextSpan(type, from, length)
    return if (same in this) this - same else this + same
}

/** A button under a bot's message (INLINE_KEYBOARD attach); [url] is set for link buttons. */
@Serializable
data class BotButton(
    val text: String,
    val url: String? = null,
    /** CALLBACK, LINK, REQUEST_CONTACT, …; null when the server named none. */
    val type: String? = null,
    /** What a CALLBACK button sends back to the bot. */
    val payload: String? = null,
    /** The keyboard's id, which a CALLBACK press quotes. */
    val callbackId: String? = null,
) {
    val isCallback: Boolean get() = type == "CALLBACK" && callbackId != null
}

/**
 * A voice message (an AUDIO attach), played via AUDIO_PLAY.
 *
 * [waveform] is the server's amplitude sketch, already normalised to 0..1 — empty
 * when it didn't send one, in which case the bubble draws a flat bar.
 */
@Serializable
data class VoiceAttach(
    val audioId: Long,
    /** Length in seconds, or 0 when the server didn't say. */
    val durationSeconds: Int,
    val waveform: List<Float> = emptyList(),
    /** Playback token, when the attach carries one — some clips need it to resolve. */
    val token: String? = null,
)

/** A shared place (LOCATION attach). */
@Serializable
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

/** A shared contact (CONTACT attach): a MAX user when [userId] is set, else just a card. */
@Serializable
data class ContactCard(
    val userId: Long,
    val name: String,
    val phone: String? = null,
)

/** One answer of a [Poll]: [votes] for it, [mine] when we picked it. */
@Serializable
data class PollAnswer(
    val id: Int,
    val text: String,
    val votes: Int = 0,
    val mine: Boolean = false,
)

/** A poll (POLL attach). [settings] is the server's bit set; [total] counts voters. */
@Serializable
data class Poll(
    val pollId: Long,
    val title: String,
    val settings: Int,
    val answers: List<PollAnswer>,
    val total: Int = 0,
) {
    val anonymous: Boolean get() = settings and 1 != 0
    val multiple: Boolean get() = settings and 2 != 0
    val revote: Boolean get() = settings and 4 != 0
    val closed: Boolean get() = settings and 8 != 0
    val voted: Boolean get() = answers.any { it.mine }

    /** Answers can still be picked: open, and not yet voted unless revoting is allowed. */
    val canChoose: Boolean get() = !closed && (!voted || revote)

    /** What a tap on answer [id] votes for (empty retracts), or null when it can't change anything. */
    fun votesAfterTapping(id: Int): Set<Int>? {
        val mine = answers.filter { it.mine }.map { it.id }.toSet()
        return when {
            closed -> null
            mine.isEmpty() -> setOf(id)
            !revote -> null
            id in mine -> if (multiple) mine - id else emptySet()
            multiple -> mine + id
            else -> setOf(id)
        }
    }

    /** This poll with our votes set to [ids], counts moved to match, until the server's own tally lands. */
    fun withMyVotes(ids: Set<Int>): Poll =
        copy(
            answers =
                answers.map { a ->
                    a.copy(
                        mine = a.id in ids,
                        votes =
                            a.votes - (if (a.mine) 1 else 0) + (
                                if (a.id in
                                    ids
                                ) {
                                    1
                                } else {
                                    0
                                }
                            ),
                    )
                },
            total = total - (if (voted) 1 else 0) + (if (ids.isNotEmpty()) 1 else 0),
        )
}

/** One of the user's sticker sets; its stickers are fetched by id when it is opened. */
data class StickerSet(
    val id: Long,
    val name: String,
    val iconUrl: String?,
    val stickerIds: List<Long>,
)

/** A sticker we can send; [url] is its still image (animated ones included). */
data class Sticker(
    val id: Long,
    val url: String,
)

/** The sticker panel's contents: the user's sets in order, and recently sent stickers' ids. */
data class StickerShelf(
    val sets: List<StickerSet>,
    val recent: List<Long>,
)

/** A file attachment on a received message (downloadable via FILE_DOWNLOAD). */
@Serializable
data class FileAttach(
    val fileId: Long,
    val name: String,
    val size: Long,
)

/** A link/URL preview (server SHARE attach): title, description and an optional image. */
@Serializable
data class LinkPreview(
    val url: String,
    val title: String?,
    val description: String?,
    val imageUrl: String?,
)

/**
 * A group service/system event (a CONTROL attach): "created the group", "joined",
 * "added X", "left", "changed the title", etc. [actorId] is who did it, [userIds]
 * the affected members (for add/remove), [title] the new title (for a title change).
 */
@Serializable
data class ServiceEvent(
    val event: String,
    val actorId: Long,
    val userIds: List<Long> = emptyList(),
    val title: String? = null,
    /** Server-rendered text (CONTROL `message`/`shortMessage`), used when we don't format the event ourselves. */
    val message: String? = null,
)

/**
 * One line describing a message for the chat list: its text, or a label for what it
 * carries when it has none.
 *
 * Without this an attachment-only message left the row showing an *older* text
 * message, so a chat could sit there with an unread badge next to something the
 * reader had already seen.
 */
fun Message.previewLabel(): String =
    when {
        text.isNotBlank() -> text
        service != null -> service.message ?: "Служебное сообщение"
        voice != null -> "🎵 Голосовое сообщение"
        stickerUrl != null -> "Стикер"
        poll != null -> "📊 " + poll.title
        location != null -> "📍 Местоположение"
        contact != null -> "👤 " + contact.name
        media.any { it.isVideoNote } -> "📹 Видеосообщение"
        media.any { it.type == MediaType.VIDEO } -> "🎥 Видео"
        media.isNotEmpty() -> "📷 Фото"
        files.isNotEmpty() -> "📎 " + files.first().name
        linkPreview != null -> linkPreview.url
        else -> ""
    }

/** Where an outgoing attachment has got to in its upload. */
enum class UploadState { QUEUED, UPLOADING, DONE, FAILED }

/**
 * One attachment of a message that is still being sent: what to show as its
 * thumbnail, and how far its upload has got. Lets the bubble appear in the chat
 * immediately, with a progress ring per item, instead of the whole batch waiting
 * behind a single spinner.
 */
data class PendingAttach(
    /** A value Coil can render (the picked item's local URI). */
    val preview: Any?,
    val kind: PickedKind,
    val state: UploadState = UploadState.QUEUED,
    /** Fraction uploaded, 0..1. */
    val progress: Float = 0f,
)

/** A single message inside a chat. */
@Serializable
data class Message(
    /** Server message id (string in the protocol). Null for messages we just sent locally. */
    val id: String?,
    /** Client id we generated when sending; used to match the server echo. */
    val cid: Long?,
    val chatId: Long,
    val senderId: Long,
    val text: String,
    val time: Long,
    /** Delivery state (meaningful for our own outgoing messages). */
    val status: MessageStatus = MessageStatus.UNKNOWN,
    /** Inline image/video attachments. */
    val media: List<MediaAttach> = emptyList(),
    /** Emoji reactions on this message (aggregated per emoji). */
    val reactions: List<Reaction> = emptyList(),
    /** The quoted message, if this is a reply. */
    val replyTo: ReplyInfo? = null,
    /** Downloadable file attachments on this message. */
    val files: List<FileAttach> = emptyList(),
    /** If this is a forwarded message, the original author's user id. */
    val forwardedFrom: Long? = null,
    /** Link/URL preview (from a SHARE attach), if any. */
    val linkPreview: LinkPreview? = null,
    /** Set when this is a group service/system message (rendered as a centered chip). */
    val service: ServiceEvent? = null,
    /** Set when this message is a voice recording. */
    val voice: VoiceAttach? = null,
    /** True once the author has edited the message (server `status` EDITED). */
    val edited: Boolean = false,
    /** Formatting runs over [text]; empty for plain text. */
    val spans: List<TextSpan> = emptyList(),
    /** Rows of buttons a bot attached under the message. */
    val buttons: List<List<BotButton>> = emptyList(),
    /** Image URL of a sticker, when the message is one. */
    val stickerUrl: String? = null,
    /** How many have seen it (channel posts carry `stats.views`); 0 when not counted. */
    val views: Int = 0,
    /** The poll this message carries, if it is one. */
    val poll: Poll? = null,
    /** A shared place, if this message is one. */
    val location: GeoPoint? = null,
    /** A shared contact, if this message is one. */
    val contact: ContactCard? = null,
    /**
     * Attachments still uploading, on a message we created locally and have not yet
     * sent. Replaced by the server's copy (with real [media]) once the send lands.
     *
     * Not serialized: [PendingAttach.preview] is a live platform handle, and an
     * unsent message isn't something to restore from a cache anyway.
     */
    @Transient
    val pending: List<PendingAttach> = emptyList(),
)
