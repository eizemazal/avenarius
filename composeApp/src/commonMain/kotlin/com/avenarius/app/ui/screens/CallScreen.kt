package com.avenarius.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avenarius.app.model.CallState
import com.avenarius.app.model.CallStatus
import com.avenarius.app.net.CallEngine
import com.avenarius.app.ui.AppIcons
import com.avenarius.app.ui.CallVideoRenderer
import com.avenarius.app.ui.nowMillis
import kotlinx.coroutines.delay

/**
 * The full-screen in-call UI: remote video (or an avatar/gradient for audio calls),
 * a local self-view PiP, the peer's name + call status/timer, and the control bar
 * (mic, camera, switch-camera, hang-up). When the call has ended it shows the reason
 * and a close button.
 */
@Composable
internal fun CallScreen(
    call: CallState,
    engine: CallEngine?,
    onHangup: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Once the call ends, show the reason briefly then dismiss the screen automatically.
    if (call.status == CallStatus.ENDED) {
        LaunchedEffect(call.conversationId, call.status) {
            delay(1500)
            onDismiss()
        }
    }
    Surface(Modifier.fillMaxSize(), color = Color(0xFF0E0E12)) {
        Box(Modifier.fillMaxSize()) {
            // Any call can carry video (you can switch it on mid-call), so drive the UI
            // off the live media state, not the call's initial kind.
            val showRemoteVideo = call.media.remoteVideoActive && call.status == CallStatus.ACTIVE

            if (showRemoteVideo) {
                CallVideoRenderer(engine = engine, local = false, modifier = Modifier.fillMaxSize())
            } else {
                // Audio call, or video not yet flowing: centered avatar over a dark backdrop.
                Column(
                    Modifier.fillMaxSize().padding(top = 96.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(128.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            (call.peerName ?: "?").take(1).uppercase(),
                            color = Color.White,
                            style = MaterialTheme.typography.displaySmall,
                        )
                    }
                }
            }

            // Header: name + status.
            Column(
                Modifier.fillMaxWidth().padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    call.peerName ?: "Звонок",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    statusText(call),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Local self-view PiP, shown whenever our camera is on.
            if (call.media.cameraEnabled && call.status != CallStatus.ENDED) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 120.dp)
                        .width(108.dp)
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                ) {
                    CallVideoRenderer(engine = engine, local = true, modifier = Modifier.fillMaxSize(), mirror = true)
                }
            }

            // Controls.
            if (call.status == CallStatus.ENDED) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    RoundCallButton(AppIcons.Close, Color(0xFF3A3A40), onDismiss)
                }
            } else {
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundCallButton(
                        icon = if (call.media.micEnabled) AppIcons.Mic else AppIcons.MicOff,
                        bg = if (call.media.micEnabled) Color(0xFF3A3A40) else Color.White,
                        tint = if (call.media.micEnabled) Color.White else Color.Black,
                        onClick = onToggleMic,
                    )
                    run {
                        RoundCallButton(
                            icon = if (call.media.cameraEnabled) AppIcons.Video else AppIcons.VideoOff,
                            bg = if (call.media.cameraEnabled) Color(0xFF3A3A40) else Color.White,
                            tint = if (call.media.cameraEnabled) Color.White else Color.Black,
                            onClick = onToggleCamera,
                        )
                        if (call.media.cameraEnabled) RoundCallButton(AppIcons.SwitchCamera, Color(0xFF3A3A40), onSwitchCamera)
                    }
                    RoundCallButton(AppIcons.CallEnd, Color(0xFFE0384A), onHangup)
                }
            }
        }
    }
}

@Composable
private fun statusText(call: CallState): String =
    when (call.status) {
        CallStatus.DIALING -> "Вызов…"
        CallStatus.RINGING -> "Входящий звонок"
        CallStatus.CONNECTING -> "Соединение…"
        CallStatus.ACTIVE -> {
            var now by remember { mutableStateOf(nowMillis()) }
            LaunchedEffect(call.connectedAtMs) {
                while (true) {
                    now = nowMillis()
                    delay(1000)
                }
            }
            val secs = ((now - (call.connectedAtMs ?: now)) / 1000).coerceAtLeast(0)
            val m = secs / 60
            val s = secs % 60
            (if (m < 10) "0$m" else "$m") + ":" + (if (s < 10) "0$s" else "$s")
        }
        CallStatus.ENDED -> call.endReason ?: "Звонок завершён"
    }

@Composable
private fun RoundCallButton(
    icon: androidx.compose.ui.graphics.painter.Painter,
    bg: Color,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bg,
        modifier = Modifier.size(60.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        }
    }
}
