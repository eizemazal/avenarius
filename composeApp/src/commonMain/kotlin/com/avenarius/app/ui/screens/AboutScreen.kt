package com.avenarius.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.avenarius.app.ui.AppLinks
import com.avenarius.app.ui.components.GroupCard
import com.avenarius.app.ui.components.groupRowDivider
import com.avenarius.app.ui.components.openUriSafely
import com.avenarius.app.ui.rememberAppReviewLauncher
import com.avenarius.app.ui.theme.Spacing

private const val APP_VERSION = "1.0.12"

/** "About" screen: what the app is, its version, and how to support it (rating, GitHub). */
@Composable
internal fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { SubPageBar("О программе", onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l, vertical = Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text("Авенариус", style = MaterialTheme.typography.headlineMedium, maxLines = 1)
            Text(
                "Версия $APP_VERSION",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Неофициальный клиент Max с открытым исходным кодом. Без рекламы и трекеров. " +
                    "Если приложение вам полезно, поддержите проект:",
                style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Paragraph),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.s),
            )
            val uriHandler = LocalUriHandler.current
            val launchReview = rememberAppReviewLauncher()
            GroupCard(modifier = Modifier.fillMaxWidth()) {
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
            }
            Text(
                AppLinks.GITHUB,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
        Modifier
            .fillMaxWidth()
            .groupRowDivider(Spacing.l)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
