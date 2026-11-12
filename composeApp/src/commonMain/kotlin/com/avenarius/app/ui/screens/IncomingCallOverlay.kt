package com.avenarius.app.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avenarius.app.model.CallKind
import com.avenarius.app.model.CallState
import com.avenarius.app.ui.AppIcons
import com.avenarius.app.ui.components.Avatar
import com.avenarius.app.ui.theme.Spacing

/** Full-screen prompt for an inbound call: caller identity + accept / decline. */
@Composable
internal fun IncomingCallOverlay(
    call: CallState,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                Avatar(call.peerName ?: "?", call.peerAvatarUrl, 128.dp)
                Text(
                    call.peerName ?: "Неизвестный",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Spacing.xl),
                )
                Text(
                    if (call.kind == CallKind.VIDEO) "Входящий видеозвонок" else "Входящий звонок",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 64.dp),
                horizontalArrangement = Arrangement.spacedBy(64.dp, Alignment.CenterHorizontally),
            ) {
                LabeledCircleButton(
                    "Отклонить",
                    AppIcons.CallEnd,
                    MaterialTheme.colorScheme.error,
                    MaterialTheme.colorScheme.onError,
                    onDecline,
                )
                LabeledCircleButton(
                    "Принять",
                    AppIcons.Call,
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.onPrimary,
                    onAccept,
                )
            }
        }
    }
}

@Composable
private fun LabeledCircleButton(
    label: String,
    icon: androidx.compose.ui.graphics.painter.Painter,
    bg: Color,
    tint: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Surface(onClick = onClick, shape = CircleShape, color = bg, modifier = Modifier.size(68.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(30.dp))
            }
        }
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}
