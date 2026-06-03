package com.avenarius.app.ui

/**
 * Downloads [url] to the device's Downloads area as [fileName]. On Android this
 * hands off to the system DownloadManager (with its own progress notification);
 * desktop is a stub for now.
 */
expect fun downloadToDevice(
    url: String,
    fileName: String,
    mime: String,
)
