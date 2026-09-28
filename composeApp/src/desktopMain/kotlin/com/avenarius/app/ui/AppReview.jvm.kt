package com.avenarius.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import com.avenarius.app.ui.components.openUriSafely

@Composable
actual fun rememberAppReviewLauncher(): () -> Unit {
    val uriHandler = LocalUriHandler.current
    return { uriHandler.openUriSafely(AppLinks.PLAY_STORE) }
}
