package com.avenarius.app.data

import com.avenarius.app.model.Account
import com.avenarius.app.model.Chat
import com.avenarius.app.model.UserInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The last known session state, written after every sync and read back at startup
 * so the chat list paints immediately (with real names) instead of showing
 * placeholder titles until the network answers.
 *
 * [userId] guards the snapshot: a cache belonging to a different account is never
 * shown. Titles are stored as resolved, because [com.avenarius.app.net.MaxClient]
 * derives a dialog's title from the contact map at parse time — a chat parsed
 * before its contact was known reads "Диалог <id>" forever otherwise.
 */
@Serializable
data class CachedSession(
    val userId: Long,
    val account: Account? = null,
    val chats: List<Chat> = emptyList(),
    /** userId -> display name (the sender/avatar label map). */
    val contacts: Map<Long, String> = emptyMap(),
    val contactsList: List<UserInfo> = emptyList(),
    /** Resolved non-contact peers (group senders, dialog partners). */
    val peers: Map<Long, UserInfo> = emptyMap(),
    /** When this snapshot was written (ms since epoch). */
    val savedAt: Long = 0,
)

/**
 * Cached conversation state and unsent drafts, layered over the same
 * [AppStorage] key/value store that backs [Prefs].
 *
 * Drafts are deliberately *not* part of the cache: they are unsent user content,
 * so [clearCache] leaves them alone and only [clearAll] (logout) drops them.
 */
class AppCache(
    private val storage: AppStorage,
) {
    // Unknown keys are ignored so a snapshot written by an older build still loads
    // after a model gains a field.
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

    /** The cached session for [userId], or null if there is none (or it is another account's). */
    fun loadSession(userId: Long): CachedSession? {
        val raw = storage.getString(KEY_SESSION) ?: return null
        val parsed = runCatching { json.decodeFromString<CachedSession>(raw) }.getOrNull()
        if (parsed == null) {
            // Unreadable (corrupt, or written by an incompatible build) — drop it.
            storage.putString(KEY_SESSION, null)
            return null
        }
        return parsed.takeIf { it.userId == userId }
    }

    fun saveSession(session: CachedSession) {
        runCatching { storage.putString(KEY_SESSION, json.encodeToString(session)) }
    }

    // --- drafts -------------------------------------------------------------

    /** Every saved draft, keyed by chat id. */
    fun drafts(): Map<Long, String> {
        val raw = storage.getString(KEY_DRAFTS) ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<Long, String>>(raw) }.getOrDefault(emptyMap())
    }

    /** Replaces the whole draft set (blank drafts are dropped rather than stored). */
    fun saveDrafts(drafts: Map<Long, String>) {
        val kept = drafts.filterValues { it.isNotBlank() }
        runCatching {
            storage.putString(KEY_DRAFTS, if (kept.isEmpty()) null else json.encodeToString(kept))
        }
    }

    // --- downloaded files ---------------------------------------------------

    /**
     * Where previously-downloaded file attachments ended up, as fileId -> platform
     * reference. Lets a message offer "open" instead of "download" across restarts.
     */
    fun downloadedFiles(): Map<Long, String> {
        val raw = storage.getString(KEY_DOWNLOADS) ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<Long, String>>(raw) }.getOrDefault(emptyMap())
    }

    fun saveDownloadedFiles(refs: Map<Long, String>) {
        runCatching {
            storage.putString(KEY_DOWNLOADS, if (refs.isEmpty()) null else json.encodeToString(refs))
        }
    }

    // --- clearing -----------------------------------------------------------

    /** Drops the cached conversation snapshot. Keeps drafts (unsent user content). */
    fun clearCache() = storage.putString(KEY_SESSION, null)

    /** Drops everything this cache owns, drafts included. Used on logout. */
    fun clearAll() {
        storage.putString(KEY_SESSION, null)
        storage.putString(KEY_DRAFTS, null)
        storage.putString(KEY_DOWNLOADS, null)
    }

    /**
     * Approximate size of the cached data, in bytes — the encoded length of what
     * this cache stores. The underlying store's own on-disk overhead isn't
     * included, so treat it as an indication rather than a disk measurement.
     */
    fun sizeBytes(): Long =
        listOf(KEY_SESSION, KEY_DRAFTS, KEY_DOWNLOADS)
            .sumOf { key ->
                storage
                    .getString(key)
                    ?.encodeToByteArray()
                    ?.size
                    ?.toLong() ?: 0L
            }

    private companion object {
        const val KEY_SESSION = "cache_session"
        const val KEY_DRAFTS = "chat_drafts"
        const val KEY_DOWNLOADS = "downloaded_files"
    }
}
