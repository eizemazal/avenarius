package com.avenarius.app.ui

import com.avenarius.app.data.blobFilesSize
import com.avenarius.app.data.deleteAllBlobFiles
import java.io.File

actual fun imageCacheDirectory(): String? =
    runCatching {
        File(System.getProperty("user.home"), ".avenarius/image_cache").absolutePath
    }.getOrNull()

actual fun imageCacheSizeOnDisk(): Long =
    runCatching {
        blobFilesSize(File(System.getProperty("user.home"), ".avenarius/image_cache"))
    }.getOrDefault(0L)

actual fun clearImageCacheOnDisk() {
    runCatching { deleteAllBlobFiles(File(System.getProperty("user.home"), ".avenarius/image_cache")) }
}
