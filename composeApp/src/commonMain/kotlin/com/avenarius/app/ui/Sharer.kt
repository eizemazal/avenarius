package com.avenarius.app.ui

/**
 * Fetches [url] and hands it to the OS share sheet as [fileName] of [mime] so the
 * user can send it to another app. On Android this downloads to the cache and
 * shares a FileProvider URI via ACTION_SEND; desktop is a stub for now.
 */
expect fun shareMediaToOtherApps(
    url: String,
    fileName: String,
    mime: String,
)
