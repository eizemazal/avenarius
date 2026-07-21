package com.avenarius.app.ui

import androidx.compose.runtime.Composable

/**
 * Returns a launcher lambda that opens the camera QR scanner. [onResult] fires with
 * the decoded string when a QR code is scanned, or not at all if the user cancels.
 * Android uses zxing-android-embedded; desktop is a stub for now.
 */
@Composable
expect fun rememberQrScanLauncher(onResult: (String) -> Unit): () -> Unit

/** True on platforms where QR scanning is available (Android). */
expect val qrScanSupported: Boolean
