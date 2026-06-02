package com.avenarius.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avenarius.app.model.UserInfo
import com.avenarius.app.ui.components.Avatar
import com.avenarius.app.ui.components.CenteredSpinner
import com.avenarius.app.ui.components.LinkedText
import com.avenarius.app.ui.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UserScreen(
    user: UserInfo?,
    isMe: Boolean,
    onBack: () -> Unit,
    onWrite: (UserInfo) -> Unit,
    onAvatarClick: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профиль") },
                navigationIcon = { IconButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineMedium) } },
            )
        },
    ) { padding ->
        if (user == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CenteredSpinner() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Tapping the avatar opens it full-screen (reusing the image viewer).
            Avatar(user.name, user.avatarUrl, 96.dp, onClick = user.avatarUrl?.let { url -> { onAvatarClick(url) } })
            Text(user.name, style = MaterialTheme.typography.headlineSmall)
            // Bio — render URLs as clickable links.
            user.description?.takeIf { it.isNotBlank() }?.let { bio ->
                LinkedText(
                    bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isMe) {
                Button(onClick = { onWrite(user) }) { Text("Написать") }
            }
            HorizontalDivider()
            InfoRow("Телефон", user.phone?.let { "+$it" })
            InfoRow(
                "Пол",
                when (user.gender) {
                    "MALE" -> "Мужской"
                    "FEMALE" -> "Женский"
                    else -> user.gender
                },
            )
            InfoRow("Страна", user.country)
            InfoRow("Ссылка", user.link)
            InfoRow("Регистрация", user.registrationTime?.takeIf { it > 0 }?.let { formatDate(it) })
            InfoRow("ID", user.id.toString())
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String?,
) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
