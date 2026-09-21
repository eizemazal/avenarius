package com.avenarius.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avenarius.app.model.CallKind
import com.avenarius.app.model.CallState
import com.avenarius.app.ui.AppIcons

/** Full-screen prompt for an inbound call: caller identity + accept / decline. */
@Composable
internal fun IncomingCallOverlay(
    call: CallState,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = Color(0xFF0E0E12)) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
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
                Text(
                    call.peerName ?: "Неизвестный",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    if (call.kind == CallKind.VIDEO) "Входящий видеозвонок" else "Входящий звонок",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 64.dp),
                horizontalArrangement = Arrangement.spacedBy(64.dp, Alignment.CenterHorizontally),
            ) {
                LabeledCircleButton("Отклонить", AppIcons.CallEnd, Color(0xFFE0384A), onDecline)
                LabeledCircleButton("Принять", AppIcons.Call, Color(0xFF2FB563), onAccept)
            }
        }
    }
}

@Composable
private fun LabeledCircleButton(
    label: String,
    icon: androidx.compose.ui.graphics.painter.Painter,
    bg: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(onClick = onClick, shape = CircleShape, color = bg, modifier = Modifier.size(68.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
    }
}
