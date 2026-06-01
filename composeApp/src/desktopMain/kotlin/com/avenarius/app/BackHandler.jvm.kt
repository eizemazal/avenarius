package com.avenarius.app.ui

import androidx.compose.runtime.Composable

/** Desktop has no system back action, so nothing to intercept. */
@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    // no-op
}
