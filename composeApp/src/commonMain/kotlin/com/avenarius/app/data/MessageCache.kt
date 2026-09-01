package com.avenarius.app.data

import com.avenarius.app.model.Message
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Recent messages per chat, so opening a conversation shows it immediately instead
 * of an empty screen until the history request comes back.
 *
 * One blob per chat (see [AppStorage]'s blob methods): a chat costs a read when it
 * is opened and nothing at all otherwise. Only the newest [MAX_PER_CHAT] messages
 * of each chat are kept — this is a cache for the first screenful, not an archive.
 */
class MessageCache(
    private val storage: AppStorage,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

    /** Cached messages for [chatId], oldest first, or empty if there are none. */
    fun load(chatId: Long): List<Message> {
        val raw = storage.readBlob(blobName(chatId)) ?: return emptyList()
        return runCatching { json.decodeFromString<List<Message>>(raw) }
            .getOrElse {
                // Unreadable (corrupt, or written by an incompatible build) — drop it.
                storage.deleteBlob(blobName(chatId))
                emptyList()
            }
    }

    /**
     * Stores the tail of [messages] for [chatId]. Messages that never reached the
     * server are skipped: they live in the send queue, not in history.
     */
    fun save(
        chatId: Long,
        messages: List<Message>,
    ) {
        val keep = messages.filter { it.id != null }.takeLast(MAX_PER_CHAT)
        if (keep.isEmpty()) {
            storage.deleteBlob(blobName(chatId))
            return
        }
        runCatching { storage.writeBlob(blobName(chatId), json.encodeToString(keep)) }
    }

    fun clear() = storage.deleteAllBlobs()

    fun sizeBytes(): Long = storage.blobsSizeBytes()

    private fun blobName(chatId: Long) = "messages_$chatId"

    private companion object {
        const val MAX_PER_CHAT = 60
    }
}
