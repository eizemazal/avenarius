package com.avenarius.app.ui

import java.awt.Desktop
import java.net.URI

/**
 * Desktop has no bundled audio player (same as [VideoPlayer]), so a voice message is
 * handed to the system. There is no playback of our own to control, so everything
 * else is a no-op and the bubble returns to its idle state right away.
 */
actual object VoiceAudio : VoicePlayback {
    override fun play(
        url: String,
        durationHintMs: Long,
        onStarted: () -> Unit,
        onProgress: (Float) -> Unit,
        onFinished: () -> Unit,
    ) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }
        onFinished()
    }

    override fun pause() = Unit

    override fun resume() = Unit

    override fun seekTo(fraction: Float) = Unit

    override fun stop() = Unit
}
