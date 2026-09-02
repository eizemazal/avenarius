package com.avenarius.app.ui

/**
 * Plays a single voice message at a time.
 *
 * One clip at once is the whole contract: starting a new one stops whatever was
 * playing, which is what a chat needs and keeps the platform side small.
 *
 * An interface rather than a bare platform object so tests can substitute one —
 * reaching the real desktop player from a test opened the clip in the developer's
 * browser.
 */
interface VoicePlayback {
    /**
     * Starts playing [url].
     *
     * [durationHintMs] is the length from the message's attach, used when the player
     * can't work it out for itself. Pass 0 if unknown.
     *
     * [onStarted] fires once audio is actually running (so the bubble can stop
     * showing a loading spinner), [onProgress] reports a 0..1 position, and
     * [onFinished] fires when the clip ends or fails. All arrive on the main thread.
     */
    fun play(
        url: String,
        durationHintMs: Long,
        onStarted: () -> Unit,
        onProgress: (Float) -> Unit,
        onFinished: () -> Unit,
    )

    /** Pauses without giving up the position, so [resume] continues where it stopped. */
    fun pause()

    /** Continues a [pause]d clip. */
    fun resume()

    /** Jumps to [fraction] (0..1) of the clip. Ignored if nothing is loaded. */
    fun seekTo(fraction: Float)

    /** Stops playback and forgets the position. Safe to call when nothing is playing. */
    fun stop()
}

/** The platform's player. */
expect object VoiceAudio : VoicePlayback
