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

    /** Blobs, kept separate from [map] exactly as the real implementations do. */
    val blobs = mutableMapOf<String, String>()

    override fun readBlob(name: String): String? = blobs[name]

    override fun writeBlob(
        name: String,
        value: String,
    ) {
        blobs[name] = value
    }

    override fun deleteBlob(name: String) {
        blobs.remove(name)
    }

    override fun deleteAllBlobs() = blobs.clear()

    override fun blobsSizeBytes(): Long = blobs.values.sumOf { it.encodeToByteArray().size.toLong() }
}
