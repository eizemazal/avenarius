package com.avenarius.app.ui

// Desktop file download isn't implemented yet (Android-only feature for now).
actual suspend fun downloadToDevice(
    url: String,
    fileName: String,
    mime: String,
    onProgress: ((Float) -> Unit)?,
): String? = null

actual suspend fun openDownloadedFile(
    reference: String,
    fileName: String,
): Boolean = false
