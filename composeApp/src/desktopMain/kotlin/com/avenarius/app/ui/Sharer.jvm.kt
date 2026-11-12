package com.avenarius.app.ui

// Desktop share-to-other-apps isn't implemented yet (Android-only feature for now).
actual fun shareMediaToOtherApps(
    url: String,
    fileName: String,
    mime: String,
) = Unit

// No share sheet on desktop: the text goes to the clipboard instead.
actual fun shareText(text: String) {
    runCatching {
        java.awt.Toolkit
            .getDefaultToolkit()
            .systemClipboard
            .setContents(java.awt.datatransfer.StringSelection(text), null)
    }
}
