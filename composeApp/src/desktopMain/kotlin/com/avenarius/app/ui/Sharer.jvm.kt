package com.avenarius.app.ui

// Desktop share-to-other-apps isn't implemented yet (Android-only feature for now).
actual fun shareMediaToOtherApps(
    url: String,
    fileName: String,
    mime: String,
) = Unit
