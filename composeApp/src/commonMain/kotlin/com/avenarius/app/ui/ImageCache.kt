package com.avenarius.app.ui

import coil3.disk.DiskCache

/**
 * Directory for Coil's on-disk image cache, or null where there isn't one.
 *
 * Without a disk cache every thumbnail is re-fetched from the CDN each time a chat
 * is opened, which is both slow and needless traffic — the app builds its own
 * [coil3.ImageLoader], and one is not configured by default.
 */
expect fun imageCacheDirectory(): String?

/**
 * Size of whatever is in [imageCacheDirectory] on disk. Used only before Coil has
 * been asked for an image (it builds its loader lazily, so there is no cache object
 * to ask yet) — reporting 0 then would understate the cache badly.
 */
expect fun imageCacheSizeOnDisk(): Long

/** Deletes [imageCacheDirectory]. Counterpart of [imageCacheSizeOnDisk]. */
expect fun clearImageCacheOnDisk()

/**
 * The disk cache handed to Coil, published so the settings screen can report its
 * size and empty it.
 *
 * Coil keeps its own index of what is on disk, so measuring and clearing go through
 * it rather than walking the directory behind its back. Held here because the cache
 * is created where the singleton [coil3.ImageLoader] is built, far from the
 * ViewModel that needs it.
 */
object ImageDiskCache {
    var instance: DiskCache? = null

    /** Bytes currently held on disk, or 0 when there is no cache. */
    val sizeBytes: Long
        get() =
            instance?.let { cache -> runCatching { cache.size }.getOrNull() }
                ?: runCatching { imageCacheSizeOnDisk() }.getOrDefault(0L)

    /**
     * Empties the cache. Deletes files, so keep it off the main thread.
     *
     * Goes through Coil when it has a cache object (it keeps its own index of what
     * is on disk) and falls back to deleting the directory when it doesn't.
     */
    fun clear() {
        val cache = instance
        if (cache != null) {
            runCatching { cache.clear() }
        } else {
            runCatching { clearImageCacheOnDisk() }
        }
    }
}
