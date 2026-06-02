package com.avenarius.app.model

/*
 * Domain models used by the UI. These are deliberately small and decoupled from
 * the raw Max wire format (which is parsed in com.avenarius.app.net.MaxClient).
 *
 * Everything in this file lives in commonMain, so it is shared as-is between the
 * Android app and the desktop app.
 */

/** The signed-in user. */
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
)

/** A user/contact, used for the contacts list, search results and the profile page. */
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
)

/** Message delivery state for outgoing messages (server `status`). */
enum class MessageStatus { UNKNOWN, SENT, READ }

enum class MediaType { PHOTO, VIDEO }

/** An image/video attachment we can show inline (by its CDN URL). */
data class MediaAttach(
    val type: MediaType,
    /** Image URL (PHOTO) or thumbnail URL (VIDEO). */
    val url: String,
    val width: Int,
    val height: Int,
    /** For VIDEO: the id needed to resolve the playable stream (opcode 83). */
    val videoId: Long = 0,
)

/** A single message inside a chat. */
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
)
