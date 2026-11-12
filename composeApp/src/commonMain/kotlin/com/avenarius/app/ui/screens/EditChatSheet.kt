package com.avenarius.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.avenarius.app.model.Chat
import com.avenarius.app.model.PickedMedia
import com.avenarius.app.ui.AppIcons
import com.avenarius.app.ui.components.AppSheet
import com.avenarius.app.ui.components.Avatar
import com.avenarius.app.ui.components.SheetAction
import com.avenarius.app.ui.rememberSingleImagePickLauncher

/** The official «Описание» limit for a channel; groups get the same. */
internal const val CHAT_DESCRIPTION_MAX = 400

/** Group options the editor offers (confirmed flag names), with their labels. */
private val GROUP_OPTIONS =
    listOf(
        "ONLY_OWNER_CAN_CHANGE_ICON_TITLE" to "Название и фото меняет только владелец",
        "ONLY_ADMIN_CAN_ADD_MEMBER" to "Добавлять участников могут только админы",
        "ONLY_ADMIN_CAN_CALL" to "Звонить в группу могут только админы",
        "ALL_CAN_PIN_MESSAGE" to "Все могут закреплять сообщения",
        "MEMBERS_CAN_SEE_PRIVATE_LINK" to "Участники видят ссылку-приглашение",
    )

/** A round photo picker: the picked image, else the current avatar (or a camera glyph when there is none). */
@Composable
internal fun PhotoPicker(
    picked: PickedMedia?,
    currentUrl: String?,
    name: String,
    size: androidx.compose.ui.unit.Dp,
    onPick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(cs.surfaceContainerHighest)
            .clickable(onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            picked != null ->
                AsyncImage(
                    model = picked.content.previewModel,
                    contentDescription = "Фото",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            currentUrl != null -> Avatar(name, currentUrl, size)
            else -> Icon(AppIcons.Camera, contentDescription = "Добавить фото", tint = cs.primary, modifier = Modifier.size(size * 0.45f))
        }
    }
}

/** «Изменить» a group or channel: photo, name, description, the group's options, «Сбросить ссылку». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditChatSheet(
    chat: Chat,
    /** [opened] is the chat as the sheet opened on it: what the fields started from, to tell what the user changed. */
    onSave: (opened: Chat, title: String, description: String, avatar: PickedMedia?, options: Map<String, Boolean>) -> Unit,
    onRevokeLink: () -> Unit,
    onDismiss: () -> Unit,
) {
    val opened = remember { chat }
    var title by remember { mutableStateOf(chat.title) }
    var description by remember { mutableStateOf(chat.description ?: "") }
    var avatar by remember { mutableStateOf<PickedMedia?>(null) }
    var options by remember { mutableStateOf(GROUP_OPTIONS.associate { (k, _) -> k to (k in chat.options) }) }
    var confirmRevoke by remember { mutableStateOf(false) }
    val pickAvatar = rememberSingleImagePickLauncher { avatar = it }
    if (confirmRevoke) {
        AlertDialog(
            onDismissRequest = { confirmRevoke = false },
            title = { Text("Сбросить ссылку?") },
            text = { Text("Старая ссылка-приглашение перестанет работать. Вместо неё появится новая.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRevoke = false
                    onRevokeLink()
                }) { Text("Сбросить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRevoke = false }) { Text("Отмена") } },
        )
    }
    AppSheet(onDismiss) {
        Column(
            Modifier.imePadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (chat.isChannel) "Канал" else "Группа", style = MaterialTheme.typography.headlineSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PhotoPicker(avatar, chat.avatarUrl, chat.title, 72.dp, pickAvatar)
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(if (chat.isChannel) "Название канала" else "Название группы") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(CHAT_DESCRIPTION_MAX) },
                    label = { Text("Описание") },
                    supportingText = { Text("${description.length} / $CHAT_DESCRIPTION_MAX") },
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!chat.isChannel) {
                GROUP_OPTIONS.forEach { (key, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = options[key] == true, onValueChange = { on -> options = options + (key to on) })
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = options[key] == true, onCheckedChange = null)
                    }
                }
            }
            // Only a private invite link (max.ru/join/…) can be reset; a public name stays as it is.
            if (chat.link?.contains("/join/") == true) {
                SheetAction(AppIcons.Link, "Сбросить ссылку", danger = true) { confirmRevoke = true }
            }
            Button(
                onClick = { onSave(opened, title, description, avatar, if (chat.isChannel) emptyMap() else options) },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(52.dp),
            ) { Text("Сохранить") }
        }
    }
}
