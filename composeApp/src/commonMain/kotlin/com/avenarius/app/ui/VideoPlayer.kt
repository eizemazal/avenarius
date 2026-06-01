package com.avenarius.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Plays a video from [url]. Android embeds an ExoPlayer; desktop (which lacks a
 * simple bundled player) opens it in the system browser/player.
 */
@Composable
expect fun VideoPlayer(url: String, modifier: Modifier)
