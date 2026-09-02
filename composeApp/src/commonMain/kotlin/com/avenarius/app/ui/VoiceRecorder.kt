package com.avenarius.app.ui

import androidx.compose.runtime.Composable
import com.avenarius.app.model.RecordedVoice

/**
 * Records a voice message.
 *
 * Returned by [rememberVoiceRecorder] so the platform can hold whatever it needs
 * (a permission launcher, the encoder) alive across recompositions.
 */
interface VoiceRecording {
    /**
     * Starts recording, asking for microphone permission first if needed. Nothing
     * happens if permission is refused.
     */
    fun start()

    /** Stops and hands the recording to [onRecorded]; a failed take is dropped. */
    fun stop()

    /** Stops and discards the recording. */
    fun cancel()
}

/**
 * A recorder wired to [onRecorded], which fires once a take is finished.
 *
 * Desktop has no recorder yet, so its controls do nothing — see
 * [voiceRecordingSupported].
 */
@Composable
expect fun rememberVoiceRecorder(onRecorded: (RecordedVoice) -> Unit): VoiceRecording

/** True on platforms that can record (Android). */
expect val voiceRecordingSupported: Boolean
