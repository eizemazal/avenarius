package com.avenarius.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.avenarius.app.net.CallEngine

/**
 * Renders a call's local self-view or the remote peer's video. Android draws the
 * WebRTC track into a SurfaceViewRenderer; desktop shows a placeholder (no calls).
 *
 * [local] = our camera preview; otherwise the remote track. [mirror] flips the local
 * front-camera preview so it looks like a mirror.
 */
@Composable
expect fun CallVideoRenderer(
    engine: CallEngine?,
    local: Boolean,
    modifier: Modifier,
    mirror: Boolean = false,
)
