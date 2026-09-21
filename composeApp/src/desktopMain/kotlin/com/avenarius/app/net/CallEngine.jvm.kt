package com.avenarius.app.net

import com.avenarius.app.model.IceServer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Desktop has no WebRTC media stack — calls are Android-only. This no-op engine lets
 * commonMain compile and keeps desktop calls inert (any call attempt simply does nothing).
 */
private class NoopCallEngine : CallEngine {
    override val events: SharedFlow<CallEngineEvent> = MutableSharedFlow()

    override suspend fun createOffer(): String = ""

    override suspend fun setRemoteDescription(
        type: String,
        sdp: String,
    ) = Unit

    override suspend fun createAnswer(): String = ""

    override fun addRemoteCandidate(
        candidate: String,
        sdpMid: String?,
        sdpMLineIndex: Int?,
    ) = Unit

    override fun setMicEnabled(enabled: Boolean) = Unit

    override fun setCameraEnabled(enabled: Boolean) = Unit

    override fun switchCamera() = Unit

    override fun close() = Unit
}

actual fun createCallEngine(
    iceServers: List<IceServer>,
    video: Boolean,
): CallEngine = NoopCallEngine()
