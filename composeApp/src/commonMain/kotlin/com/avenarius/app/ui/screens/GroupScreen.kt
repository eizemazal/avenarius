package com.avenarius.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avenarius.app.model.Chat
import com.avenarius.app.model.UserInfo
import com.avenarius.app.ui.AppIcons
import com.avenarius.app.ui.AppState
import com.avenarius.app.ui.AppViewModel
import com.avenarius.app.ui.components.Avatar
import com.avenarius.app.ui.components.CenteredSpinner
import com.avenarius.app.ui.components.PillTextField
import com.avenarius.app.ui.components.clickableRow
import com.avenarius.app.ui.plural
import com.avenarius.app.ui.rememberDeviceContacts
import com.avenarius.app.ui.shareText
import com.avenarius.app.ui.theme.Spacing

/** A group's or channel's info page: the shared [InfoPage] with the members as a first tab. */
@Composable
internal fun GroupScreen(
    state: AppState,
    vm: AppViewModel,
) {
    val group = state.viewingGroup
    if (group == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { CenteredSpinner() }
        return
    }
    val myId = state.account?.userId ?: -1L
    val clipboard = LocalClipboardManager.current
    var showAddDialog by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) ClearHistoryDialog({ vm.clearHistory(group) }) { confirmClear = false }
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        EditChatSheet(
            chat = group,
            onSave = { opened, title, description, avatar, options ->
                editing = false
                vm.editChat(group, title, description, avatar, options, opened)
            },
            onRevokeLink = { vm.revokeChatLink(group) },
        ) { editing = false }
    }
    val amManager = myId == group.ownerId || myId in group.adminIds
    if (showAddDialog) {
        AddMembersDialog(
            contacts = state.contactsList.filter { it.id !in group.memberIds && it.id != myId },
            onDismiss = { showAddDialog = false },
            onConfirm = { ids, phones ->
                showAddDialog = false
                vm.addMembersToGroup(ids, phones)
            },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(if (group.isChannel) "Отписаться от канала?" else "Выйти из группы?") },
            text = { Text("«${group.title}» пропадёт из списка чатов.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    vm.leaveGroup(group)
                }) { Text(if (group.isChannel) "Отписаться" else "Выйти", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Отмена") } },
        )
    }
    val link = group.link
    val menu =
        listOfNotNull(
            InfoAction(AppIcons.Edit, "Изменить") { editing = true }.takeIf { amManager },
            link?.let {
                InfoAction(AppIcons.Copy, "Скопировать ссылку") {
                    clipboard.setText(AnnotatedString(it))
                    vm.showNotice("Ссылка скопирована")
                }
            },
            link?.let { InfoAction(AppIcons.Share, "Поделиться ссылкой") { shareText(it) } },
            InfoAction(AppIcons.Clean, "Стереть переписку", danger = true) { confirmClear = true }.takeIf { !group.isChannel },
            InfoAction(AppIcons.Logout, if (group.isChannel) "Отписаться" else "Выйти из группы", danger = true) { confirmLeave = true },
        )
    val mute =
        InfoAction(if (group.muted) AppIcons.Muted else AppIcons.Unmuted, if (group.muted) "Без звука" else "Звук") { vm.toggleMute(group) }
    val find = InfoAction(AppIcons.Search, "Найти") { vm.searchInChat(group) }
    val actions =
        if (group.isChannel) {
            listOfNotNull(mute, find, link?.let { InfoAction(AppIcons.Share, "Поделиться") { shareText(it) } })
        } else {
            listOfNotNull(mute, find, InfoAction(AppIcons.PersonAdd, "Добавить") { showAddDialog = true }.takeIf { group.canAddMembers })
        }
    InfoPage(
        state = state,
        vm = vm,
        chatId = group.id,
        title = group.title,
        avatarUrl = group.avatarUrl,
        official = group.official,
        status = participantsLabel(group.participantsCount, group.isChannel),
        onBack = vm::closeGroup,
        actions = actions,
        menu = menu,
        moreTile = true,
        facts =
            listOfNotNull(
                link?.let { InfoFact(it.removePrefix("https://"), "Ссылка") },
                group.description?.let { InfoFact(it, "Описание", rich = true) },
            ),
        // A channel's subscriber list only matters to whoever runs it.
        extraTab =
            InfoTab(if (group.isChannel) "Подписчики" else "Участники") {
                if (state.groupMembersLoading && state.groupMemberList.isEmpty()) {
                    item(
                        key = "members-loading",
                    ) { Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { CenteredSpinner() } }
                }
                items(state.groupMemberList, key = { "u${it.id}" }) { member ->
                    // Managers can act on others (not the owner, not themselves).
                    MemberRow(
                        member = member,
                        role = roleLabel(member.id, group),
                        isAdmin = member.id in group.adminIds,
                        online = member.id in state.onlineUsers,
                        manageable = amManager && member.id != myId && member.id != group.ownerId,
                        onOpenProfile = { vm.openUser(member.id) },
                        onRemove = { vm.removeGroupMember(member.id) },
                        onSetAdmin = { admin -> vm.setGroupAdmin(member.id, admin) },
                    )
                }
            }.takeIf { !group.isChannel || amManager },
    )
}

@Composable
private fun MemberRow(
    member: UserInfo,
    role: String?,
    isAdmin: Boolean,
    online: Boolean,
    manageable: Boolean,
    onOpenProfile: () -> Unit,
    onRemove: () -> Unit,
    onSetAdmin: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
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
                }) { Text("Исключить", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Отмена") } },
        )
    }

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clickableRow { if (manageable) menuOpen = true else onOpenProfile() }
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(member.name, member.avatarUrl, 44.dp, online = online)
            Spacer(Modifier.width(Spacing.m))
            Text(
                member.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (role != null) {
                Spacer(Modifier.width(Spacing.s))
                Text(
                    role,
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.primary,
                    maxLines = 1,
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Открыть профиль") },
                leadingIcon = { Icon(AppIcons.Person, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onOpenProfile()
                },
            )
            DropdownMenuItem(
                text = { Text(if (isAdmin) "Снять роль администратора" else "Назначить администратором") },
                leadingIcon = { Icon(AppIcons.Shield, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onSetAdmin(!isAdmin)
                },
            )
            DropdownMenuItem(
                text = { Text("Исключить из группы", color = cs.error) },
                leadingIcon = { Icon(AppIcons.Delete, contentDescription = null, tint = cs.error) },
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
                PillTextField(filter, { filter = it }, "Поиск по имени", Modifier.fillMaxWidth())
                Spacer(Modifier.height(Spacing.s))
                if (shownServer.isEmpty() && shownBook.isEmpty()) {
                    Text("Ничего не найдено", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(Spacing.s))
                } else {
                    LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 320.dp)) {
                        items(shownServer, key = { "u${it.id}" }) { c ->
                            PickPersonRow(c.name, c.avatarUrl, null, c.id in selectedIds) { on ->
                                selectedIds = if (on) selectedIds + c.id else selectedIds - c.id
                            }
                        }
                        items(shownBook, key = { "p${it.phone}" }) { c ->
                            PickPersonRow(c.name, null, c.phone, c.phone in selectedPhones) { on ->
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

private fun roleLabel(
    userId: Long,
    group: Chat,
): String? =
    when {
        userId == group.ownerId -> "Владелец"
        userId in group.adminIds -> "Админ"
        else -> null
    }

/** "243 783 участника" (or "подписчика" for a [channel]), digits grouped by thousands. */
internal fun participantsLabel(
    count: Int,
    channel: Boolean = false,
): String {
    val word =
        if (channel) {
            plural(
                count,
                "подписчик",
                "подписчика",
                "подписчиков",
            )
        } else {
            plural(count, "участник", "участника", "участников")
        }
    // Non-breaking spaces, so the number never wraps mid-way.
    val digits =
        count
            .toString()
            .reversed()
            .chunked(3)
            .joinToString(" ")
            .reversed()
    return "$digits $word"
}
