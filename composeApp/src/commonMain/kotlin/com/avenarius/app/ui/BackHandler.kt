package com.avenarius.app.ui

import androidx.compose.runtime.Composable

/**
 * Intercepts the platform "back" action (the Android system back button / swipe
 * gesture) while [enabled] is true, invoking [onBack] instead of the default.
 *
 * Android delegates to androidx.activity.compose.BackHandler; desktop has no
 * system back, so its actual is a no-op.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
