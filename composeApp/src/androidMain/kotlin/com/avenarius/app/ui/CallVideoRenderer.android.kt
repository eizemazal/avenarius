package com.avenarius.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.avenarius.app.net.AndroidCallEngine
import com.avenarius.app.net.CallEngine
import com.avenarius.app.net.CallEngineEvent
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/**
 * Draws the local or remote WebRTC video track into a SurfaceViewRenderer. The remote
 * track can appear after the view is composed, so we watch the engine's events and
 * (re)attach the sink via a keyed [DisposableEffect] when it does.
 */
@Composable
actual fun CallVideoRenderer(
    engine: CallEngine?,
    local: Boolean,
    modifier: Modifier,
    mirror: Boolean,
) {
    val android = engine as? AndroidCallEngine
    if (android == null) {
        Box(modifier)
        return
    }

    var view by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    // The remote track can arrive after we compose (and the RemoteVideo event may fire
    // before this composable subscribes), so re-read the engine's current track both
    // initially and whenever a RemoteVideo event lands — never rely on the event alone.
    val track: VideoTrack? by produceState<VideoTrack?>(
        if (local) android.localVideoTrack else android.remoteVideoTrack,
        android,
        local,
    ) {
        value = if (local) android.localVideoTrack else android.remoteVideoTrack
        if (!local) {
            android.events.collect {
                if (it is CallEngineEvent.RemoteVideo) value = android.remoteVideoTrack
            }
        }
    }

    DisposableEffect(view, track) {
        val v = view
        val t = track
        if (v != null && t != null) runCatching { t.addSink(v) }
        onDispose { if (v != null && t != null) runCatching { t.removeSink(v) } }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                // The local self-view is a PiP overlapping the full-screen remote surface;
                // without this it renders BEHIND the remote SurfaceView and stays invisible.
                if (local) setZOrderMediaOverlay(true)
                init(android.eglBaseContext, null)
                setEnableHardwareScaler(true)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setMirror(mirror)
                view = this
            }
        },
        update = { it.setMirror(mirror) },
        onRelease = { runCatching { it.release() } },
    )
}
