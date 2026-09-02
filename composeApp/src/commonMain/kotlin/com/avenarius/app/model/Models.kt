package com.avenarius.app.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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
    val description: String? = null,
    val phone: String? = null,
    val gender: String? = null,
    val link: String? = null,
    val country: String? = null,
    val registrationTime: Long? = null,
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
)

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
)

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
