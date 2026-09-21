package com.avenarius.app.net

import com.avenarius.app.model.IceServer
import kotlinx.coroutines.flow.SharedFlow

/**
 * The WebRTC media engine (STAGE 2 media half). Abstracts the platform PeerConnection
 * so [CallSession] can orchestrate a call in commonMain. The real implementation lives
 * in androidMain (backed by the standard Google WebRTC library); desktop is a no-op.
 *
 * Created via [createCallEngine]. The session drives it: create an offer/answer, feed
 * remote SDP + ICE, toggle mic/camera; local ICE candidates and connection state come
 * back through [events].
 */
interface CallEngine {
    val events: SharedFlow<CallEngineEvent>

    /** Creates the local SDP offer (sets it as local description, starts ICE gathering). */
    suspend fun createOffer(): String

    /** Applies the remote SDP (offer or answer). */
    suspend fun setRemoteDescription(
        type: String,
        sdp: String,
    )

    /** Creates the local SDP answer (only valid after a remote offer was set). */
    suspend fun createAnswer(): String

    /** Adds a remote trickle ICE candidate. */
    fun addRemoteCandidate(
        candidate: String,
        sdpMid: String?,
        sdpMLineIndex: Int?,
    )

    fun setMicEnabled(enabled: Boolean)

    fun setCameraEnabled(enabled: Boolean)

    fun switchCamera()

    fun close()
}

/** Things the [CallEngine] tells the session about. */
sealed interface CallEngineEvent {
    /** A locally-gathered ICE candidate to trickle to the SFU. */
    data class LocalCandidate(
        val candidate: String,
        val sdpMid: String?,
        val sdpMLineIndex: Int?,
    ) : CallEngineEvent

    /** ICE/DTLS connected — media is flowing. */
    data object Connected : CallEngineEvent

    /** The remote peer's video track appeared or went away. */
    data class RemoteVideo(
        val active: Boolean,
    ) : CallEngineEvent

    /** The connection failed or was lost. */
    data object Failed : CallEngineEvent
}

/**
 * Platform factory for a [CallEngine]. Android returns a WebRTC-backed engine; desktop
 * returns a no-op (the desktop build carries the call types but never places a call).
 */
expect fun createCallEngine(
    iceServers: List<IceServer>,
    video: Boolean,
): CallEngine
