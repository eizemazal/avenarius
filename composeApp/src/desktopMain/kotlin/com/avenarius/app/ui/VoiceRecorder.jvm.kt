package com.avenarius.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.avenarius.app.model.RecordedVoice

// Desktop voice recording isn't implemented yet (Android-only feature for now); the
// controls are hidden by voiceRecordingSupported rather than being no-op buttons.
actual val voiceRecordingSupported: Boolean = false

@Composable
actual fun rememberVoiceRecorder(onRecorded: (RecordedVoice) -> Unit): VoiceRecording =
    remember {
        object : VoiceRecording {
            override fun start() = Unit

            override fun stop() = Unit

            override fun cancel() = Unit
        }
    }
