package com.avenarius.app.ui

// Desktop file download isn't implemented yet (Android-only feature for now).
actual fun downloadToDevice(
    url: String,
    fileName: String,
    mime: String,
) = Unit
