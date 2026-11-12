package com.avenarius.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avenarius.app.model.Chat
import com.avenarius.app.model.UserInfo
import com.avenarius.app.model.isSavedMessages
import com.avenarius.app.ui.AppIcons
import com.avenarius.app.ui.components.Avatar
import com.avenarius.app.ui.components.CircleIconButton
import com.avenarius.app.ui.components.PillTextField
import com.avenarius.app.ui.components.clickableRow
import com.avenarius.app.ui.plural

/** "Send to…" chat picker shown when media is shared into the app from elsewhere. */
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
                        "Отправить $count ${plural(count, "вложение", "вложения", "вложений")} в «${target.title}»?"
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
    var query by remember { mutableStateOf("") }
    val shown = remember(chats, query) { chats.filter { it.title.contains(query.trim(), ignoreCase = true) } }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(AppIcons.Close, "Отмена", onCancel)
            Spacer(Modifier.width(12.dp))
            Text(
                if (count > 1) "Отправить ($count) в…" else "Отправить в…",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PillTextField(query, { query = it }, "Поиск", Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = 4.dp,
                    bottom =
                        16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
        ) {
            items(shown, key = { it.id }) { chat ->
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
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (chat.isSavedMessages) SavedMessagesAvatar(48.dp) else Avatar(chat.title, avatarUrl, 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        chat.lastMessageText?.let {
                            Text(
                                it.replace('\n', ' '),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
