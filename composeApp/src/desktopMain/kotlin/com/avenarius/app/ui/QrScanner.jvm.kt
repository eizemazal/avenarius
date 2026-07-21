package com.avenarius.app.ui

import androidx.compose.runtime.Composable

// Desktop QR scanning isn't implemented yet (Android-only feature for now).
actual val qrScanSupported: Boolean = false

@Composable
actual fun rememberQrScanLauncher(onResult: (String) -> Unit): () -> Unit = {}
