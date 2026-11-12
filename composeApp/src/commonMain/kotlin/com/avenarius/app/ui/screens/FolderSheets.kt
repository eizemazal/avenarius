package com.avenarius.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avenarius.app.model.Chat
import com.avenarius.app.model.ChatFolder
import com.avenarius.app.ui.AppIcons
import com.avenarius.app.ui.components.AppSheet
import com.avenarius.app.ui.components.PillTextField
import com.avenarius.app.ui.components.SheetAction
import com.avenarius.app.ui.components.clickableRow

/** Longest folder name offered: the server took 15 characters and refused 21. */
private const val FOLDER_TITLE_MAX = 20

/** «Удалить папку?», asked before a folder goes. */
@Composable
private fun DeleteFolderDialog(
    folder: ChatFolder,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить папку «${folder.title}»?") },
        text = { Text("Чаты из неё останутся в списке.") },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** A long press on a folder chip: «Настроить папку», «Удалить папку». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderActionsSheet(
    folder: ChatFolder,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    if (confirm) DeleteFolderDialog(folder, onDelete) { confirm = false }
    AppSheet(onDismiss) {
        Text(folder.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
        SheetAction(AppIcons.Edit, "Настроить папку", onClick = onEdit)
        SheetAction(AppIcons.Delete, "Удалить папку", danger = true) { confirm = true }
        Spacer(Modifier.height(12.dp))
    }
}

/** «Добавить в папку»: every folder, ticked where [chat] was put in by hand; a tap puts it in or takes it out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderPickSheet(
    chat: Chat,
    folders: List<ChatFolder>,
    onToggle: (ChatFolder) -> Unit,
    onDismiss: () -> Unit,
) {
    AppSheet(onDismiss) {
        Text("Добавить в папку", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
        folders.forEach { f -> FolderTickRow(f.title, chat.id in f.include) { onToggle(f) } }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun FolderTickRow(
    title: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = { onToggle() })
            .padding(horizontal = 24.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Folder, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(18.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .then(if (checked) Modifier.background(cs.primary) else Modifier.border(1.5.dp, cs.outline, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(AppIcons.Delivered, contentDescription = null, tint = cs.onPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

/** Настройки → Папки: «Все» stays first; the rest move with ↑/↓, a tap opens the editor; «Создать папку». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FoldersSheet(
    folders: List<ChatFolder>,
    onMove: (id: String, by: Int) -> Unit,
    onOpen: (ChatFolder) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    AppSheet(onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Text("Папки", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
            Text(
                "Вкладки над списком чатов. Они общие для всех ваших устройств.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
            )
            val movable = folders.filterNot { it.isAll }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Chats, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(18.dp))
                Text("Все", style = MaterialTheme.typography.bodyLarge)
            }
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(movable, key = { it.id }) { f ->
                    val i = movable.indexOf(f)
                    Row(
                        Modifier.fillMaxWidth().clickableRow { onOpen(f) }.padding(start = 24.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(AppIcons.Folder, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(18.dp))
                        Text(
                            f.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onMove(f.id, -1) }, enabled = i > 0) {
                            Icon(AppIcons.ArrowUp, contentDescription = "Выше")
                        }
                        IconButton(onClick = { onMove(f.id, 1) }, enabled = i < movable.lastIndex) {
                            Icon(AppIcons.ArrowDown, contentDescription = "Ниже")
                        }
                    }
                }
            }
            SheetAction(AppIcons.Add, "Создать папку", onClick = onCreate)
        }
    }
}

/**
 * Creates or changes a folder: its name and the chats put in by hand. A [folder] with a blank
 * id is a new one; [onDelete] is null for it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderEditorSheet(
    folder: ChatFolder,
    chats: List<Chat>,
    avatarOf: (Chat) -> String?,
    onSave: (title: String, chatIds: List<Long>) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(folder.title) }
    var picked by remember { mutableStateOf(folder.include) }
    var filter by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete && onDelete != null) DeleteFolderDialog(folder, onDelete) { confirmDelete = false }
    val shown = chats.filter { filter.isBlank() || it.title.contains(filter.trim(), ignoreCase = true) }
    AppSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (onDelete == null) "Новая папка" else "Папка", style = MaterialTheme.typography.headlineSmall)
            PillTextField(
                title,
                { title = it.take(FOLDER_TITLE_MAX) },
                "Название папки",
                Modifier.fillMaxWidth(),
                leading = AppIcons.Folder,
                capitalize = true,
            )
            if (folder.filterIds.isNotEmpty()) {
                Text(
                    "Кроме отмеченных, в папку попадают чаты по её правилам.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PillTextField(filter, { filter = it }, "Чаты: ${picked.size} · поиск", Modifier.fillMaxWidth())
            LazyColumn(Modifier.heightIn(max = 300.dp).clip(RoundedCornerShape(16.dp))) {
                items(shown, key = { it.id }) { c ->
                    PickPersonRow(c.title, avatarOf(c), null, c.id in picked) { on -> picked = if (on) picked + c.id else picked - c.id }
                }
            }
            Button(
                onClick = { onSave(title.trim(), picked) },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (onDelete == null) "Создать папку" else "Сохранить") }
            if (onDelete != null) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Удалить папку", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
