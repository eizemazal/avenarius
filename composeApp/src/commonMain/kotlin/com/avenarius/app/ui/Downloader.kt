package com.avenarius.app.ui

/**
 * Saves [url] into the device's Downloads area as [fileName].
 *
 * Returns a platform reference to the stored copy — a content URI on Android —
 * which [openDownloadedFile] can hand to a viewer app, or null when the file was
 * saved but can't be referenced (Android 9 and older). Desktop is a stub for now.
 *
 * [onProgress] is called with 0..1 as the body arrives. Supplying it also says the
 * caller shows its own progress, so the platform stays quiet: no toasts (they would
 * cover the very indicator they duplicate) and a failure is thrown for the caller to
 * report. Without it, the platform announces start, finish and failure itself and
 * returns null if the download failed.
 */
expect suspend fun downloadToDevice(
    url: String,
    fileName: String,
    mime: String,
    onProgress: ((Float) -> Unit)? = null,
): String?

/**
 * Opens an already-downloaded file in whichever app handles it. [reference] is a
 * value previously returned by [downloadToDevice]; [fileName] supplies the type.
 * Returns false if nothing could open it — including when the file is no longer
 * there, so the caller can offer to download it again.
 */
expect suspend fun openDownloadedFile(
    reference: String,
    fileName: String,
): Boolean
