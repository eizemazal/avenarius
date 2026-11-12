package com.avenarius.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avenarius.app.model.Chat
import com.avenarius.app.ui.AppIcons
import com.avenarius.app.ui.AppState
import com.avenarius.app.ui.AppViewModel
import com.avenarius.app.ui.components.CenteredSpinner
import com.avenarius.app.ui.components.CircleIconButton
import com.avenarius.app.ui.formatDate
import com.avenarius.app.ui.lastSeenText
import com.avenarius.app.ui.nowMillis

/** A user's info page; for our own profile its tabs show «Избранное» (chat 0). */
@Composable
internal fun UserScreen(
    state: AppState,
    vm: AppViewModel,
) {
    val user = state.viewingUser
    if (user == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            CenteredSpinner()
            CircleIconButton(AppIcons.Back, "Назад", vm::closeUser, Modifier.statusBarsPadding().padding(12.dp))
        }
        return
    }
    val myId = state.account?.userId ?: -1L
    val isMe = user.id == myId
    // Renaming or removing only makes sense for someone on our contact list.
    val isContact = !isMe && state.contactsList.any { it.id == user.id }
    // The dialog with them (myId xor theirs); for ourselves that is 0, the saved messages.
    val dialogId = myId xor user.id
    val dialog = state.chats.firstOrNull { it.id == dialogId } ?: Chat(dialogId, user.name, null, 0, isDialog = true)
    val online = !isMe && user.id in state.onlineUsers
    var confirmBlock by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    if (renaming) {
        var first by remember { mutableStateOf(user.firstName ?: user.name.substringBefore(' ')) }
        // A known first name with no last name means none: the full name must not be split again.
        var last by remember { mutableStateOf(user.lastName ?: if (user.firstName != null) "" else user.name.substringAfter(' ', "")) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Изменить контакт") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(first, { first = it }, label = { Text("Имя") }, singleLine = true)
                    OutlinedTextField(last, { last = it }, label = { Text("Фамилия") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    renaming = false
                    vm.renameContact(user, first, last)
                }, enabled = first.isNotBlank()) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Отмена") } },
        )
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Удалить контакт?") },
            text = { Text("${user.name} пропадёт из ваших контактов. Переписка останется.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    vm.removeContact(user)
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Отмена") } },
        )
    }
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) ClearHistoryDialog({ vm.clearHistory(dialog) }) { confirmClear = false }
    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            // The name stands alone, so it needs no declension («Заблокировать Алиса…» reads wrong).
            title = { Text(if (user.blocked) "Разблокировать пользователя?" else "Заблокировать пользователя?") },
            text = {
                Text(
                    if (user.blocked) {
                        "${user.name} снова сможет писать вам, звонить и добавлять в чаты."
                    } else {
                        "${user.name} не сможет писать вам, звонить и добавлять в чаты."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmBlock = false
                    vm.setBlocked(user, !user.blocked)
                }) {
                    Text(
                        if (user.blocked) "Разблокировать" else "Заблокировать",
                        color = if (user.blocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("Отмена") } },
        )
    }
    val actions =
        if (isMe) {
            listOf(
                InfoAction(AppIcons.Edit, "Изменить", onClick = vm::openEditProfile),
                InfoAction(AppIcons.Bookmark, "Избранное", onClick = vm::openSavedMessages),
            )
        } else {
            listOfNotNull(
                InfoAction(AppIcons.ChatsSelected, "Написать") { vm.openDialogWith(user) },
                InfoAction(if (dialog.muted) AppIcons.Muted else AppIcons.Unmuted, if (dialog.muted) "Без звука" else "Звук") {
                    vm.toggleMute(dialog)
                },
                InfoAction(AppIcons.Call, "Позвонить") { vm.startCall(user.id, false) }.takeIf { !state.demoMode },
                InfoAction(AppIcons.Video, "Видео") { vm.startCall(user.id, true) }.takeIf { !state.demoMode },
            )
        }
    InfoPage(
        state = state,
        vm = vm,
        chatId = dialogId,
        title = user.name,
        avatarUrl = user.avatarUrl,
        status =
            when {
                isMe -> "это вы"
                online -> "в сети"
                else -> state.presence[user.id]?.let { lastSeenText(it, nowMillis()) }
            },
        online = online,
        onBack = vm::closeUser,
        actions = actions,
        menu =
            listOfNotNull(
                InfoAction(AppIcons.Edit, "Изменить контакт") { renaming = true }.takeIf { isContact },
                InfoAction(
                    AppIcons.Delete,
                    "Удалить контакт",
                    danger = true,
                ) { confirmRemove = true }.takeIf { isContact },
                // Only for a dialog we know: clearing needs its last event time.
                InfoAction(AppIcons.Clean, "Стереть переписку", danger = true) { confirmClear = true }
                    .takeIf { !isMe && state.chats.any { it.id == dialogId && it.lastEventTime > 0 } },
                InfoAction(AppIcons.Block, if (user.blocked) "Разблокировать" else "Заблокировать", danger = !user.blocked) {
                    confirmBlock = true
                }.takeIf { !isMe },
            ),
        facts =
            listOfNotNull(
                user.phone?.let { InfoFact("+$it", "Телефон") },
                user.link?.let { InfoFact(it, "Ссылка") },
                user.description?.takeIf { it.isNotBlank() }?.let { InfoFact(it, "О себе", rich = true) },
                user.registrationTime?.takeIf { it > 0 }?.let { InfoFact(formatDate(it), "В MAX с") },
                InfoFact(user.id.toString(), "ID"),
            ),
    )
}
