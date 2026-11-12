package com.avenarius.app.data

import kotlin.random.Random

/**
 * Tiny key/value persistence abstraction. Each platform provides a concrete
 * implementation (SharedPreferences on Android, a properties file on desktop)
 * and passes it into the app at startup.
 */
interface AppStorage {
    fun getString(key: String): String?

    fun putString(
        key: String,
        value: String?,
    )

    // --- blobs ---------------------------------------------------------------
    //
    // The key/value side above is one document that each platform loads whole, so
    // anything sizeable (cached message history) would be parsed at startup whether
    // it was needed or not. Blobs are stored one file per name instead, so a chat's
    // history costs a read only when that chat is opened.

    /** The named blob's contents, or null if it isn't there (or can't be read). */
    fun readBlob(name: String): String?

    fun writeBlob(
        name: String,
        value: String,
    )

    fun deleteBlob(name: String)

    /** Drops every blob. The key/value entries above are untouched. */
    fun deleteAllBlobs()

    /** Total size of all blobs, in bytes. */
    fun blobsSizeBytes(): Long
}

/** Convenience accessors layered on top of the raw key/value store. */
class Prefs(
    // Internal (not private) so [AppCache] can layer its own keys over the same
    // store without every call site having to pass the storage around twice.
    internal val storage: AppStorage,
) {
    var token: String?
        get() = storage.getString(KEY_TOKEN)
        set(value) = storage.putString(KEY_TOKEN, value)

    var userId: Long?
        get() = storage.getString(KEY_USER_ID)?.toLongOrNull()
        set(value) = storage.putString(KEY_USER_ID, value?.toString())

    /** Theme preference: "system" (default), "dark" or "light". Survives logout. */
    var theme: String
        get() = storage.getString(KEY_THEME) ?: "system"
        set(value) = storage.putString(KEY_THEME, value)

    /**
     * Chats pinned on this device → when (epoch ms; 0 for pins saved before times were kept). The server
     * accepts a pin but may not keep it, so these hold the pins whatever it reports; [clear] drops them.
     * Stored as `id:time` pairs, comma-separated; older entries are a bare id.
     */
    var pinnedAt: Map<Long, Long>
        get() =
            storage
                .getString(KEY_PINNED)
                ?.split(',')
                ?.mapNotNull { entry ->
                    val parts = entry.split(':')
                    parts[0].toLongOrNull()?.let { it to (parts.getOrNull(1)?.toLongOrNull() ?: 0L) }
                }?.toMap() ?: emptyMap()
        set(value) = storage.putString(KEY_PINNED, value.entries.joinToString(",") { "${it.key}:${it.value}" }.ifEmpty { null })

    /** The chats in [pinnedAt]; setting it keeps the known pin times. */
    var pinnedChats: Set<Long>
        get() = pinnedAt.keys
        set(value) {
            val at = pinnedAt
            pinnedAt = value.associateWith { at[it] ?: 0L }
        }

    /** Accent color name (see Accent); like the theme, survives logout. */
    var accent: String?
        get() = storage.getString(KEY_ACCENT)
        set(value) = storage.putString(KEY_ACCENT, value)

    /** Whole-UI scale factor (1 = system size). */
    var uiScale: Float
        get() = scaleOf(KEY_UI_SCALE, UI_SCALE_RANGE)
        set(value) = storage.putString(KEY_UI_SCALE, value.toString())

    /** Text-only scale factor, on top of the system font size. */
    var fontScale: Float
        get() = scaleOf(KEY_FONT_SCALE, FONT_SCALE_RANGE)
        set(value) = storage.putString(KEY_FONT_SCALE, value.toString())

    /** "Отправка по вводу": Enter sends the message instead of breaking the line. */
    var sendOnEnter: Boolean
        get() = storage.getString(KEY_SEND_ON_ENTER) == "1"
        set(value) = storage.putString(KEY_SEND_ON_ENTER, if (value) "1" else null)

    /** The chat background preset (see WALLPAPERS); per device, like the theme. */
    var chatWallpaper: Int
        get() = storage.getString(KEY_WALLPAPER)?.toIntOrNull() ?: 1
        set(value) = storage.putString(KEY_WALLPAPER, value.takeIf { it != 1 }?.toString())

    /** The emoji a double tap on a message reacts with; null = double tap does nothing. */
    var quickReaction: String?
        get() = storage.getString(KEY_QUICK_REACTION)
        set(value) = storage.putString(KEY_QUICK_REACTION, value)

    private fun scaleOf(
        key: String,
        range: ClosedFloatingPointRange<Float>,
    ): Float = storage.getString(key)?.toFloatOrNull()?.coerceIn(range) ?: 1f

    /** A stable per-install device id (UUID format). Generated once, then persisted. */
    val deviceId: String
        get() = getOrCreate(KEY_DEVICE_ID)

    /** A stable per-install mt instance id (UUID format), required by the handshake. */
    val mtInstance: String
        get() = getOrCreate(KEY_MT_INSTANCE)

    private fun getOrCreate(key: String): String {
        storage.getString(key)?.let { return it }
        val generated = randomUuid()
        storage.putString(key, generated)
        return generated
    }

    fun clear() {
        token = null
        userId = null
        pinnedChats = emptySet()
    }

    companion object {
        val UI_SCALE_RANGE = 0.8f..1.3f
        val FONT_SCALE_RANGE = 0.8f..1.5f
        private const val KEY_ACCENT = "accent"
        private const val KEY_PINNED = "pinned_chats"
        private const val KEY_UI_SCALE = "ui_scale"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_SEND_ON_ENTER = "send_on_enter"
        private const val KEY_QUICK_REACTION = "quick_reaction"
        private const val KEY_WALLPAPER = "chat_wallpaper"
        private const val KEY_TOKEN = "login_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_MT_INSTANCE = "mt_instance"
        private const val KEY_THEME = "theme"
    }
}

/** Generates a random UUID-v4-formatted string (8-4-4-4-12 hex). */
private fun randomUuid(): String {
    val hex = "0123456789abcdef"

    fun block(n: Int) = buildString { repeat(n) { append(hex[Random.nextInt(16)]) } }
    return "${block(8)}-${block(4)}-4${block(3)}-${hex[8 + Random.nextInt(4)]}${block(3)}-${block(12)}"
}
