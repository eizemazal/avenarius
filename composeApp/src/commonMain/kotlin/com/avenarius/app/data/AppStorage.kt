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
}

/** Convenience accessors layered on top of the raw key/value store. */
class Prefs(
    private val storage: AppStorage,
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
    }

    private companion object {
        const val KEY_TOKEN = "login_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_MT_INSTANCE = "mt_instance"
        const val KEY_THEME = "theme"
    }
}

/** Generates a random UUID-v4-formatted string (8-4-4-4-12 hex). */
private fun randomUuid(): String {
    val hex = "0123456789abcdef"

    fun block(n: Int) = buildString { repeat(n) { append(hex[Random.nextInt(16)]) } }
    return "${block(8)}-${block(4)}-4${block(3)}-${hex[8 + Random.nextInt(4)]}${block(3)}-${block(12)}"
}
