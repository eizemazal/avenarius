package com.avenarius.app.data

/** In-memory [AppStorage] shared by the tests that need persistence without a platform. */
internal class InMemoryStorage : AppStorage {
    val map = mutableMapOf<String, String>()

    override fun getString(key: String): String? = map[key]

    override fun putString(
        key: String,
        value: String?,
    ) {
        if (value == null) map.remove(key) else map[key] = value
    }
}
