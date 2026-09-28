package com.avenarius.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.avenarius.app.ui.AppIcons
import com.avenarius.app.ui.AppLinks
import com.avenarius.app.ui.components.openUriSafely
import com.avenarius.app.ui.rememberAppReviewLauncher

private const val APP_VERSION = "1.0.9"

/** "About" screen: what the app is, its version, and how to support it (rating, GitHub). */
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
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Авенариус", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Версия $APP_VERSION",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Неофициальный клиент Max с открытым исходным кодом. Без рекламы и трекеров. " +
                    "Если приложение вам полезно, поддержите проект:",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            val uriHandler = LocalUriHandler.current
            val launchReview = rememberAppReviewLauncher()
            SupportLink(
                title = "Оценить в Google Play",
                text = "Оценки и отзывы помогают другим людям найти приложение.",
                onClick = launchReview,
            )
            SupportLink(
                title = "Поставить звезду на GitHub",
                text =
                    "Звёзды показывают, что проект нужен, и помогают ему развиваться. " +
                        "Там же можно сообщить об ошибке или предложить идею.",
                onClick = { uriHandler.openUriSafely(AppLinks.GITHUB) },
            )
            Text(
                AppLinks.GITHUB,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SupportLink(
    title: String,
    text: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
