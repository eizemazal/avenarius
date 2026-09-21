package com.avenarius.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.avenarius.app.net.CallEngine

/** Desktop has no WebRTC video — draw nothing (calls are Android-only). */
@Composable
actual fun CallVideoRenderer(
    engine: CallEngine?,
    local: Boolean,
    modifier: Modifier,
    mirror: Boolean,
) {
    Box(modifier)
}
