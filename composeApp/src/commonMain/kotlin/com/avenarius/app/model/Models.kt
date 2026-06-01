package com.avenarius.app.model

/**
 * Domain models used by the UI. These are deliberately small and decoupled from
 * the raw Max wire format (which is parsed in [com.avenarius.app.net.MaxClient]).
 *
 * Everything in this file lives in commonMain, so it is shared as-is between the
 * Android app and the desktop app.
 */

/** The signed-in user. */
data class Account(
    val userId: Long,
    val firstName: String,
    val lastName: String? = null,
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
)

/** Message delivery state for outgoing messages (server `status`). */
enum class MessageStatus { UNKNOWN, SENT, READ }

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
)
