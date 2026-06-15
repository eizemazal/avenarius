package com.avenarius.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avenarius.app.model.Chat
import com.avenarius.app.model.UserInfo
import com.avenarius.app.ui.AppIcons
import com.avenarius.app.ui.components.Avatar
import com.avenarius.app.ui.components.clickableRow

/** "Send to…" chat picker shown when media is shared into the app from elsewhere. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharePickScreen(
    chats: List<Chat>,
    contacts: List<UserInfo>,
    myId: Long,
    count: Int,
    onPick: (Chat) -> Unit,
    onCancel: () -> Unit,
) {
    // Confirm the destination by name before actually sending.
    var confirmTarget by remember { mutableStateOf<Chat?>(null) }
    confirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmTarget = null },
            title = { Text("Отправить?") },
            text = {
                Text(
                    if (count > 1) {
                        "Отправить $count вложений в «${target.title}»?"
                    } else {
                        "Отправить в «${target.title}»?"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmTarget = null
                    onPick(target)
                }) { Text("Отправить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmTarget = null }) { Text("Отмена") }
            },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (count > 1) "Отправить ($count) в…" else "Отправить в…") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(AppIcons.Close, contentDescription = "Отмена") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(chats, key = { it.id }) { chat ->
                val otherId = if (chat.isDialog) chat.id xor myId else null
                val avatarUrl =
                    if (chat.isDialog) {
                        otherId?.let { id -> contacts.firstOrNull { it.id == id }?.avatarUrl }
                    } else {
                        chat.avatarUrl
                    }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickableRow { confirmTarget = chat }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(chat.title, avatarUrl, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(chat.title, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                        chat.lastMessageText?.let {
                            Text(
                                it,
                                maxLines = 1,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
