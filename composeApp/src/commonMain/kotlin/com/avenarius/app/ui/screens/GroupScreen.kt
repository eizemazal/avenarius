package com.avenarius.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.avenarius.app.model.Chat
import com.avenarius.app.model.UserInfo
import com.avenarius.app.ui.AppIcons
import com.avenarius.app.ui.components.Avatar
import com.avenarius.app.ui.components.CenteredSpinner
import com.avenarius.app.ui.components.clickableRow
import com.avenarius.app.ui.rememberDeviceContacts
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupScreen(
    group: Chat?,
    members: List<UserInfo>,
    loading: Boolean,
    myId: Long,
    contacts: List<UserInfo>,
    notice: String?,
    onNoticeShown: () -> Unit,
    onBack: () -> Unit,
    onOpenUser: (Long) -> Unit,
    onAddMembers: (userIds: List<Long>, phones: List<String>) -> Unit,
    onRemoveMember: (Long) -> Unit,
    onSetAdmin: (Long, Boolean) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    // Surface transient notices (e.g. "some contacts aren't on MAX") on this screen.
    LaunchedEffect(notice) {
        notice?.let {
            snackbarHostState.showSnackbar(it)
            onNoticeShown()
        }
    }

    if (showAddDialog && group != null) {
        AddMembersDialog(
            contacts = contacts.filter { it.id !in group.memberIds && it.id != myId },
            onDismiss = { showAddDialog = false },
            onConfirm = { ids, phones ->
                showAddDialog = false
                onAddMembers(ids, phones)
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (group?.isChannel == true) "Канал" else "Группа") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "Назад") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (group == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CenteredSpinner() }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            // Header: avatar, title, participant count.
            item {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Avatar(group.title, group.avatarUrl, 96.dp)
                    Text(group.title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        participantsLabel(group.participantsCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }

            // Invite via link (when the group has a public link).
            group.link?.let { link ->
                item {
                    ActionRow(AppIcons.Attach, "Пригласить по ссылке") {
                        clipboard.setText(AnnotatedString(link))
                        scope.launch { snackbarHostState.showSnackbar("Ссылка скопирована") }
                    }
                }
            }
            // Add members (when entitled).
            if (group.canAddMembers) {
                item {
                    ActionRow(AppIcons.Edit, "Добавить участников") { showAddDialog = true }
                    HorizontalDivider()
                }
            }

            // Member list.
            if (loading && members.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CenteredSpinner() } }
            } else {
                item {
                    Text(
                        "Участники",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                val amManager = myId == group.ownerId || myId in group.adminIds
                items(members, key = { it.id }) { member ->
                    // Managers can act on others (not the owner, not themselves).
                    val manageable = amManager && member.id != myId && member.id != group.ownerId
                    MemberRow(
                        member = member,
                        role = roleLabel(member.id, group),
                        isAdmin = member.id in group.adminIds,
                        manageable = manageable,
                        onOpenProfile = { onOpenUser(member.id) },
                        onRemove = { onRemoveMember(member.id) },
                        onSetAdmin = { admin -> onSetAdmin(member.id, admin) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickableRow(onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MemberRow(
    member: UserInfo,
    role: String?,
    isAdmin: Boolean,
    manageable: Boolean,
    onOpenProfile: () -> Unit,
    onRemove: () -> Unit,
    onSetAdmin: (Boolean) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Исключить участника?") },
            text = { Text("${member.name} будет удалён(а) из группы.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove()
                }) { Text("Исключить") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Отмена") } },
        )
    }

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clickableRow { if (manageable) menuOpen = true else onOpenProfile() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(member.name, member.avatarUrl, 44.dp)
            Spacer(Modifier.width(12.dp))
            Text(member.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (role != null) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        role,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Открыть профиль") },
                onClick = {
                    menuOpen = false
                    onOpenProfile()
                },
            )
            DropdownMenuItem(
                text = { Text(if (isAdmin) "Снять роль администратора" else "Назначить администратором") },
                onClick = {
                    menuOpen = false
                    onSetAdmin(!isAdmin)
                },
            )
            DropdownMenuItem(
                text = { Text("Исключить из группы") },
                onClick = {
                    menuOpen = false
                    confirmRemove = true
                },
            )
        }
    }
}

@Composable
private fun AddMembersDialog(
    contacts: List<UserInfo>,
    onDismiss: () -> Unit,
    onConfirm: (userIds: List<Long>, phones: List<String>) -> Unit,
) {
    // Address book, so we can add people not in our Max contacts (resolved by phone
    // on confirm — only the few selected, so no bulk lookup).
    val device = rememberDeviceContacts()
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var selectedPhones by remember { mutableStateOf(setOf<String>()) }
    var filter by remember { mutableStateOf("") }

    val serverNames = remember(contacts) { contacts.map { it.name.lowercase() }.toSet() }
    val book = remember(device, serverNames) { device.filter { it.name.lowercase() !in serverNames }.distinctBy { it.phone } }
    val q = filter.trim()
    val shownServer = contacts.filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }
    val shownBook = book.filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить участников") },
        text = {
            Column {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    singleLine = true,
                    leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                    placeholder = { Text("Поиск по имени") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (shownServer.isEmpty() && shownBook.isEmpty()) {
                    Text("Ничего не найдено", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
                } else {
                    LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 320.dp)) {
                        items(shownServer, key = { "u${it.id}" }) { c ->
                            PickRow(c.name, c.avatarUrl, null, c.id in selectedIds) { on ->
                                selectedIds = if (on) selectedIds + c.id else selectedIds - c.id
                            }
                        }
                        items(shownBook, key = { "p${it.phone}" }) { c ->
                            PickRow(c.name, null, c.phone, c.phone in selectedPhones) { on ->
                                selectedPhones = if (on) selectedPhones + c.phone else selectedPhones - c.phone
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedIds.toList(), selectedPhones.toList()) },
                enabled = selectedIds.isNotEmpty() || selectedPhones.isNotEmpty(),
            ) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** A checkable person row (server contact or address-book entry with a phone subtitle). */
@Composable
private fun PickRow(
    name: String,
    avatarUrl: String?,
    subtitle: String?,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, onValueChange = onToggle).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Avatar(name, avatarUrl, 36.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun roleLabel(
    userId: Long,
    group: Chat,
): String? =
    when {
        userId == group.ownerId -> "Владелец"
        userId in group.adminIds -> "Админ"
        else -> null
    }

private fun participantsLabel(count: Int): String {
    val n = count % 100
    val d = count % 10
    val word =
        when {
            n in 11..14 -> "участников"
            d == 1 -> "участник"
            d in 2..4 -> "участника"
            else -> "участников"
        }
    return "$count $word"
}
