package com.avenarius.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.awt.Desktop
import java.net.URI

@Composable
actual fun VideoPlayer(
    url: String,
    modifier: Modifier,
) {
    // Desktop has no bundled player here — hand the video to the system browser/player.
    LaunchedEffect(url) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Text("Видео открыто во внешнем плеере", style = MaterialTheme.typography.bodyMedium)
    }
}
