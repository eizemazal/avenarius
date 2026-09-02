package com.avenarius.app.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.avenarius.app.Session
import java.io.File
import java.net.URL
import kotlin.concurrent.thread

/**
 * Android voice playback on [MediaPlayer] — adequate for short speech clips, and far
 * less machinery than a full ExoPlayer instance per bubble.
 *
 * The clip is fetched to a cache file before playing rather than streamed. Seeking
 * inside a stream depends on the server honouring range requests, and when it does
 * not, `seekTo` fails through the *asynchronous* error callback — indistinguishable
 * from the clip dying. A voice note is a few tens of kilobytes, so a local copy is
 * cheap, seeks reliably, and replays instantly.
 */
actual object VoiceAudio : VoicePlayback {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null

    /** Clip length in ms: the player's own figure once known, else the attach's. */
    private var totalMs: Long = 0

    private var onProgressRef: ((Float) -> Unit)? = null
    private var onFinishedRef: (() -> Unit)? = null

    /** Identifies the load in flight, so a stale download can't start playing. */
    private var generation = 0

    override fun play(
        url: String,
        durationHintMs: Long,
        onStarted: () -> Unit,
        onProgress: (Float) -> Unit,
        onFinished: () -> Unit,
    ) {
        stop()
        totalMs = durationHintMs
        onProgressRef = onProgress
        onFinishedRef = onFinished
        val mine = ++generation
        thread(name = "voice-fetch") {
            val file = runCatching { cachedCopy(url) }.getOrNull()
            handler.post {
                // Superseded (another clip started, or playback was stopped) — drop it.
                if (mine != generation) return@post
                if (file == null) {
                    onFinished()
                    return@post
                }
                start(file, onStarted, onProgress, onFinished)
            }
        }
    }

    override fun pause() {
        stopTicking()
        runCatching { player?.pause() }
    }

    override fun resume() {
        val mp = player ?: return
        runCatching { mp.start() }
        onProgressRef?.let { startTicking(mp, it) }
    }

    override fun seekTo(fraction: Float) {
        val mp = player ?: return
        val total = totalMs
        if (total <= 0) return
        runCatching { mp.seekTo((fraction.coerceIn(0f, 1f) * total).toInt()) }
    }

    override fun stop() {
        generation++
        stopTicking()
        totalMs = 0
        onProgressRef = null
        onFinishedRef = null
        player?.let { mp ->
            runCatching { mp.stop() }
            runCatching { mp.release() }
        }
        player = null
    }

    private fun start(
        file: File,
        onStarted: () -> Unit,
        onProgress: (Float) -> Unit,
        onFinished: () -> Unit,
    ) {
        val mp =
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
            }
        player = mp
        val fail = {
            stop()
            onFinished()
        }
        mp.setOnErrorListener { _, _, _ ->
            fail()
            true
        }
        mp.setOnCompletionListener {
            onProgress(1f)
            stop()
            onFinished()
        }
        runCatching {
            mp.setDataSource(file.absolutePath)
            // A local file prepares immediately, so there is no waiting to report.
            mp.prepare()
            val reported = runCatching { mp.duration }.getOrDefault(0)
            if (reported > 0) totalMs = reported.toLong()
            mp.start()
            onStarted()
            startTicking(mp, onProgress)
        }.onFailure { fail() }
    }

    /** Downloads [url] into the cache once and reuses it afterwards. */
    private fun cachedCopy(url: String): File {
        val dir = File(Session.appContext.cacheDir, "voice").apply { mkdirs() }
        val target = File(dir, "${url.hashCode().toUInt()}.audio")
        if (target.isFile && target.length() > 0) return target
        val temp = File(dir, "${target.name}.tmp")
        URL(url).openStream().use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
        return target
    }

    /** Polls the position: MediaPlayer has no progress callback of its own. */
    private fun startTicking(
        mp: MediaPlayer,
        onProgress: (Float) -> Unit,
    ) {
        stopTicking()
        val tick =
            object : Runnable {
                override fun run() {
                    val current = player ?: return
                    if (current !== mp) return
                    val total = totalMs
                    if (total > 0) {
                        val position = runCatching { mp.currentPosition }.getOrDefault(0)
                        onProgress((position.toFloat() / total).coerceIn(0f, 1f))
                    }
                    handler.postDelayed(this, PROGRESS_INTERVAL_MS)
                }
            }
        ticker = tick
        handler.post(tick)
    }

    private fun stopTicking() {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
    }

    private const val PROGRESS_INTERVAL_MS = 100L
}
