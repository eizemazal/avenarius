package com.avenarius.app.data

import java.io.File

/*
 * File-backed blob helpers behind [AppStorage]'s blob methods.
 *
 * Both of this project's targets run on the JVM, so this file is compiled into each
 * of them (see the jvmShared srcDir in build.gradle.kts) instead of being copied
 * into AndroidStorage's and DesktopStorage's source sets by hand. The two differ
 * only in which directory they hand over.
 */

/** Blob names come from us, but a stray separator must never escape the directory. */
private fun blobFile(
    dir: File,
    name: String,
): File = File(dir, name.replace(Regex("[^A-Za-z0-9_.-]"), "_"))

internal fun readBlobFile(
    dir: File,
    name: String,
): String? = runCatching { blobFile(dir, name).takeIf { it.isFile }?.readText() }.getOrNull()

/**
 * Writes through a temp file and a rename, so an interrupted write leaves the
 * previous copy in place rather than a truncated one.
 */
internal fun writeBlobFile(
    dir: File,
    name: String,
    value: String,
) {
    runCatching {
        dir.mkdirs()
        val target = blobFile(dir, name)
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(value)
        if (!temp.renameTo(target)) {
            // Windows/Android won't rename onto an existing file.
            target.delete()
            temp.renameTo(target)
        }
    }
}

internal fun deleteBlobFile(
    dir: File,
    name: String,
) {
    runCatching { blobFile(dir, name).delete() }
}

internal fun deleteAllBlobFiles(dir: File) {
    runCatching { dir.deleteRecursively() }
}

internal fun blobFilesSize(dir: File): Long = runCatching { dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)
