package com.avenarius.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@UnstableApi
@Composable
actual fun VideoPlayer(
    url: String,
    modifier: Modifier,
    crop: Boolean,
) {
    val context = LocalContext.current
    var failed by remember(url) { mutableStateOf(false) }
    val player =
        remember(url) {
            ExoPlayer.Builder(context).build().apply {
                // A stream ExoPlayer can't handle must end in a message, not take the whole
                // screen down: setMediaItem throws synchronously for an unsupported source.
                runCatching {
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                    playWhenReady = true
                }.onFailure { failed = true }
            }
        }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    failed = true
                }
            }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    if (failed) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Не удалось воспроизвести видео", color = Color.White)
        }
        return
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                // A cropped player is the round message: its own controls would be
                // clipped by the circle, and a tap already stops playback.
                useController = !crop
                resizeMode =
                    if (crop) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
            }
        },
    )
}
