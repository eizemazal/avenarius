package com.avenarius.app.net

import android.content.Context
import android.media.AudioManager
import com.avenarius.app.Session
import com.avenarius.app.model.IceServer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android WebRTC media engine, backed by the standard Google WebRTC library
 * (io.getstream:stream-webrtc-android — BSD, not VK's binaries). Owns a single
 * PeerConnection with our mic (+ camera) tracks, and surfaces the remote tracks
 * for [CallVideoRenderer].
 */
internal class AndroidCallEngine(
    iceServers: List<IceServer>,
    private val video: Boolean,
) : CallEngine {
    private val _events = MutableSharedFlow<CallEngineEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<CallEngineEvent> = _events

    private val factory = sharedFactory()
    private var pc: PeerConnection? = null

    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var capturer: CameraVideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null

    /** Exposed for [CallVideoRenderer] to bind SurfaceViewRenderers. */
    var localVideoTrack: VideoTrack? = null
        private set
    var remoteVideoTrack: VideoTrack? = null
        private set

    val eglBaseContext: EglBase.Context get() = sharedEgl.eglBaseContext

    private val observer =
        object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) {
                _events.tryEmit(CallEngineEvent.LocalCandidate(c.sdp, c.sdpMid, c.sdpMLineIndex))
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                clog("ICE state: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED,
                    -> _events.tryEmit(CallEngineEvent.Connected)
                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.CLOSED,
                    -> _events.tryEmit(CallEngineEvent.Failed)
                    else -> Unit
                }
            }

            override fun onTrack(transceiver: RtpTransceiver) {
                (transceiver.receiver?.track() as? VideoTrack)?.let {
                    remoteVideoTrack = it
                    _events.tryEmit(CallEngineEvent.RemoteVideo(true))
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit

            override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit

            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                clog("ICE gathering: $state")
            }

            override fun onAddStream(stream: org.webrtc.MediaStream?) = Unit

            override fun onRemoveStream(stream: org.webrtc.MediaStream?) = Unit

            override fun onDataChannel(dc: org.webrtc.DataChannel?) = Unit

            override fun onRenegotiationNeeded() = Unit
        }

    init {
        val rtc =
            PeerConnection
                .RTCConfiguration(
                    iceServers.map { s ->
                        val b = PeerConnection.IceServer.builder(s.urls)
                        if (s.username != null) b.setUsername(s.username)
                        if (s.credential != null) b.setPassword(s.credential)
                        b.createIceServer()
                    },
                ).apply {
                    sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                    continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                    bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                    rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                }
        pc = factory.createPeerConnection(rtc, observer)
        addLocalTracks()
        setupAudioRouting()
        startStatsLogging()
    }

    private val audioManager by lazy { Session.appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var prevAudioMode = AudioManager.MODE_NORMAL

    /** Route call audio: voice-comm mode + speaker so the remote audio is audible. */
    private fun setupAudioRouting() {
        runCatching {
            prevAudioMode = audioManager.mode
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                // isSpeakerphoneOn is deprecated on 12+; select the speaker device explicitly.
                val speaker =
                    audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                if (speaker != null) audioManager.setCommunicationDevice(speaker)
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
            }
        }
    }

    private var statsThread: Thread? = null

    /** Logs RTP byte counters every 3s so we can tell whether media is actually flowing. */
    private fun startStatsLogging() {
        statsThread =
            Thread {
                try {
                    while (!Thread.currentThread().isInterrupted) {
                        Thread.sleep(3000)
                        val p = pc ?: continue
                        p.getStats { report ->
                            var aIn = 0.0
                            var aOut = 0.0
                            var vIn = 0.0
                            var vOut = 0.0
                            for (s in report.statsMap.values) {
                                val m = s.members
                                val kind = m["kind"]?.toString()
                                val br = (m["bytesReceived"] as? Number)?.toDouble() ?: 0.0
                                val bs = (m["bytesSent"] as? Number)?.toDouble() ?: 0.0
                                when (s.type) {
                                    "inbound-rtp" ->
                                        if (kind == "audio") {
                                            aIn += br
                                        } else if (kind == "video") {
                                            vIn += br
                                        }
                                    "outbound-rtp" ->
                                        if (kind == "audio") {
                                            aOut += bs
                                        } else if (kind == "video") {
                                            vOut += bs
                                        }
                                }
                            }
                            clog("stats: audio in=${aIn.toLong()} out=${aOut.toLong()} | video in=${vIn.toLong()} out=${vOut.toLong()}")
                        }
                    }
                } catch (_: InterruptedException) {
                }
            }.also {
                it.isDaemon = true
                it.start()
            }
    }

    private fun addLocalTracks() {
        val streamIds = listOf("ARDAMS")
        audioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack("ARDAMSa0", audioSource).also { pc?.addTrack(it, streamIds) }

        // Always create the video track + m-line, even for an "audio" call, so video can
        // be switched on mid-call without renegotiation (matching the official client).
        // The camera hardware itself is only started on demand in [startCamera].
        runCatching {
            val src = factory.createVideoSource(false)
            videoSource = src
            localVideoTrack =
                factory.createVideoTrack("ARDAMSv0", src).also {
                    it.setEnabled(video)
                    pc?.addTrack(it, streamIds)
                }
        }.onFailure { clog("video track setup failed: $it") }

        if (video) runCatching { startCamera() }.onFailure { clog("camera start failed (continuing audio-only): $it") }
    }

    /** Lazily opens the front camera and feeds [videoSource]. Safe to call repeatedly. */
    private fun startCamera() {
        val src = videoSource ?: return
        capturer?.let {
            runCatching { it.startCapture(1280, 720, 30) }
            return
        }
        val ctx = Session.appContext
        val enumerator = Camera2Enumerator(ctx)
        val front =
            enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: enumerator.deviceNames.firstOrNull() ?: return
        val cap = enumerator.createCapturer(front, null)
        capturer = cap
        val helper = SurfaceTextureHelper.create("CaptureThread", sharedEgl.eglBaseContext)
        surfaceHelper = helper
        cap.initialize(helper, ctx, src.capturerObserver)
        cap.startCapture(1280, 720, 30)
    }

    override suspend fun createOffer(): String {
        val pc = pc ?: return ""
        val sdp = pc.awaitSdp { obs -> pc.createOffer(obs, mediaConstraints()) }
        pc.awaitSetLocal(sdp)
        return sdp.description
    }

    override suspend fun createAnswer(): String {
        val pc = pc ?: return ""
        val sdp = pc.awaitSdp { obs -> pc.createAnswer(obs, mediaConstraints()) }
        pc.awaitSetLocal(sdp)
        return sdp.description
    }

    override suspend fun setRemoteDescription(
        type: String,
        sdp: String,
    ) {
        val pc = pc ?: return
        val t = if (type.equals("offer", true)) SessionDescription.Type.OFFER else SessionDescription.Type.ANSWER
        pc.awaitSetRemote(SessionDescription(t, sdp))
    }

    override fun addRemoteCandidate(
        candidate: String,
        sdpMid: String?,
        sdpMLineIndex: Int?,
    ) {
        pc?.addIceCandidate(IceCandidate(sdpMid ?: "", sdpMLineIndex ?: 0, candidate))
    }

    override fun setMicEnabled(enabled: Boolean) {
        audioTrack?.setEnabled(enabled)
    }

    override fun setCameraEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
        // Start the camera lazily the first time video is turned on (e.g. switching an
        // audio call to video); stop it when turned off.
        runCatching { if (enabled) startCamera() else capturer?.stopCapture() }
    }

    override fun switchCamera() {
        capturer?.switchCamera(null)
    }

    override fun close() {
        runCatching { statsThread?.interrupt() }
        runCatching {
            audioManager.mode = prevAudioMode
            audioManager.isSpeakerphoneOn = false
        }
        runCatching { capturer?.stopCapture() }
        runCatching { capturer?.dispose() }
        runCatching { surfaceHelper?.dispose() }
        runCatching { videoSource?.dispose() }
        runCatching { audioSource?.dispose() }
        runCatching { pc?.dispose() }
        pc = null
        localVideoTrack = null
        remoteVideoTrack = null
    }

    private fun mediaConstraints() =
        MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (video) "true" else "false"))
        }

    private companion object {
        private var factoryRef: PeerConnectionFactory? = null
        private val sharedEgl: EglBase by lazy { EglBase.create() }

        fun sharedFactory(): PeerConnectionFactory =
            factoryRef ?: run {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions
                        .builder(Session.appContext)
                        .createInitializationOptions(),
                )
                // Route native WebRTC logs (ICE/DTLS) to logcat for call debugging.
                runCatching {
                    org.webrtc.Logging.enableLogToDebugOutput(org.webrtc.Logging.Severity.LS_INFO)
                }
                PeerConnectionFactory
                    .builder()
                    .setVideoEncoderFactory(DefaultVideoEncoderFactory(sharedEgl.eglBaseContext, true, true))
                    .setVideoDecoderFactory(DefaultVideoDecoderFactory(sharedEgl.eglBaseContext))
                    .createPeerConnectionFactory()
                    .also { factoryRef = it }
            }
    }
}

// --- SdpObserver / setter coroutine bridges ---

private suspend fun PeerConnection.awaitSdp(create: (SdpObserver) -> Unit): SessionDescription =
    suspendCancellableCoroutine { cont ->
        create(
            object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription) = cont.resume(desc)

                override fun onCreateFailure(error: String?) = cont.resumeWithException(IllegalStateException(error))

                override fun onSetSuccess() = Unit

                override fun onSetFailure(error: String?) = Unit
            },
        )
    }

private suspend fun PeerConnection.awaitSetLocal(desc: SessionDescription) =
    suspendCancellableCoroutine { cont ->
        setLocalDescription(setObserver(cont), desc)
    }

private suspend fun PeerConnection.awaitSetRemote(desc: SessionDescription) =
    suspendCancellableCoroutine { cont ->
        setRemoteDescription(setObserver(cont), desc)
    }

private fun setObserver(cont: kotlinx.coroutines.CancellableContinuation<Unit>) =
    object : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) = Unit

        override fun onCreateFailure(error: String?) = Unit

        override fun onSetSuccess() {
            if (cont.isActive) cont.resume(Unit)
        }

        override fun onSetFailure(error: String?) {
            if (cont.isActive) cont.resumeWithException(IllegalStateException(error))
        }
    }

@Suppress("unused")
private fun MediaStreamTrack.isVideoKind() = kind() == MediaStreamTrack.VIDEO_TRACK_KIND

actual fun createCallEngine(
    iceServers: List<IceServer>,
    video: Boolean,
): CallEngine = AndroidCallEngine(iceServers, video)
