package com.avenarius.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Plays a video from [url]. Android embeds an ExoPlayer; desktop (which lacks a
 * simple bundled player) opens it in the system browser/player.
 *
 * [crop] fills the given bounds and clips the overflow instead of fitting the whole
 * frame inside them. A round video message needs it — fitting a 9:16 clip into a
 * circle leaves black wedges on either side — while the full-screen viewer must not
 * crop, since there the whole frame is the point.
 */
@Composable
expect fun VideoPlayer(
    url: String,
    modifier: Modifier,
    crop: Boolean = false,
)
