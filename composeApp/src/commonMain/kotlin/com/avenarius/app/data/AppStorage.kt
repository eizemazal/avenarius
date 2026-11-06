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

    /** Locally pinned chat ids (comma-separated); belong to the account, so [clear] drops them. */
    var pinnedChats: Set<Long>
        get() =
            storage
                .getString(KEY_PINNED)
                ?.split(',')
                ?.mapNotNull { it.toLongOrNull() }
                ?.toSet() ?: emptySet()
        set(value) = storage.putString(KEY_PINNED, value.joinToString(",").ifEmpty { null })

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
