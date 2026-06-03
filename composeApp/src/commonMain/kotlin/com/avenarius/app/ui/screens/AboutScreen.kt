package com.avenarius.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.avenarius.app.ui.AppIcons
import com.avenarius.app.ui.components.openUriSafely

private const val GITHUB_URL = "https://github.com/eizemazal/avenarius"
private const val APP_VERSION = "1.0.0"

/** "About" screen: app name, version, and a link to the project's GitHub page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("О программе") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "Назад") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Авенариус", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Версия $APP_VERSION",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            val uriHandler = LocalUriHandler.current
            Text(
                "Открыть на GitHub",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { uriHandler.openUriSafely(GITHUB_URL) },
            )
            Text(
                GITHUB_URL,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
