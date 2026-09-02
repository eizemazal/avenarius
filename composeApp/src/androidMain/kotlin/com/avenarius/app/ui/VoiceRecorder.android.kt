package com.avenarius.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.avenarius.app.model.MediaContent
import com.avenarius.app.model.RecordedVoice
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import java.io.File

actual val voiceRecordingSupported: Boolean = true

/**
 * Records AAC audio into an MP4 container (".m4a").
 *
 * Not Opus, even though the official client records that: `MediaRecorder`'s Opus
 * encoder only exists from API 29 and this app supports API 24. The server hands back
 * opus/m4a/mp3 variants of whatever it receives, so it transcodes anyway.
 */
@Composable
actual fun rememberVoiceRecorder(onRecorded: (RecordedVoice) -> Unit): VoiceRecording {
    val context = LocalContext.current
    val recorder = remember { AndroidVoiceRecording(context, onRecorded) }
    // Held here rather than inside the recorder: a launcher has to be registered
    // during composition.
    val requestPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) recorder.beginRecording()
        }
    return remember(recorder) {
        object : VoiceRecording {
            override fun start() {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    recorder.beginRecording()
                } else {
                    requestPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            override fun stop() = recorder.finish(keep = true)

            override fun cancel() = recorder.finish(keep = false)
        }
    }
}

/** Holds the encoder and the file being written. */
private class AndroidVoiceRecording(
    private val context: Context,
    private val onRecorded: (RecordedVoice) -> Unit,
) {
    private var recorder: MediaRecorder? = null
    private var target: File? = null
    private var startedAt = 0L

    fun beginRecording() {
        finish(keep = false) // never leave a previous take running
        val dir = File(context.cacheDir, "voice_out").apply { mkdirs() }
        val file = File(dir, "rec_${System.currentTimeMillis()}.m4a")
        val mr =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
        val started =
            runCatching {
                mr.setAudioSource(MediaRecorder.AudioSource.MIC)
                mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                // Speech: mono at a modest bitrate keeps a minute well under a megabyte.
                mr.setAudioChannels(1)
                mr.setAudioSamplingRate(44_100)
                mr.setAudioEncodingBitRate(64_000)
                mr.setOutputFile(file.absolutePath)
                mr.prepare()
                mr.start()
            }.isSuccess
        if (!started) {
            runCatching { mr.release() }
            file.delete()
            return
        }
        recorder = mr
        target = file
        startedAt = System.currentTimeMillis()
    }

    fun finish(keep: Boolean) {
        val mr = recorder ?: return
        val file = target
        recorder = null
        target = null
        // stop() throws if it never got any audio (a tap-length take), so a failed
        // stop means "no recording", not a crash.
        val stopped = runCatching { mr.stop() }.isSuccess
        runCatching { mr.release() }
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        if (!keep || !stopped || file == null || !file.isFile || file.length() == 0L) {
            file?.delete()
            return
        }
        onRecorded(RecordedVoice(content = RecordedFileContent(file), durationSeconds = seconds))
    }
}

/** [MediaContent] over a file we recorded ourselves. */
private class RecordedFileContent(
    private val file: File,
) : MediaContent {
    override val size: Long get() = file.length()
    override val previewModel: Any? get() = null

    override fun openChannel(): ByteReadChannel = file.inputStream().toByteReadChannel()
}
