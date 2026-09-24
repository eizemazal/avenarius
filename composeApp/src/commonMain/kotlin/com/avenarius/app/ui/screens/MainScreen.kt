package com.avenarius.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.avenarius.app.model.Account
import com.avenarius.app.model.Chat
import com.avenarius.app.model.DeviceContact
import com.avenarius.app.model.PickedMedia
import com.avenarius.app.model.SearchResult
import com.avenarius.app.model.UserInfo
import com.avenarius.app.ui.AppIcons
import com.avenarius.app.ui.AppState
import com.avenarius.app.ui.AppViewModel
import com.avenarius.app.ui.Tab
import com.avenarius.app.ui.components.Avatar
import com.avenarius.app.ui.components.clickableRow
import com.avenarius.app.ui.formatListTime
import com.avenarius.app.ui.qrScanSupported
import com.avenarius.app.ui.rememberDeviceContacts
import com.avenarius.app.ui.rememberQrScanLauncher
import com.avenarius.app.ui.rememberSingleImagePickLauncher
import com.avenarius.app.ui.theme.ThemeMode
import com.avenarius.app.ui.typingText
import androidx.compose.material3.Tab as MdTab

@Composable
private fun NewChatDialog(
    searchResults: List<SearchResult>,
    searching: Boolean,
    contacts: List<UserInfo>,
    myId: Long,
    onSearch: (String) -> Unit,
    onPickResult: (SearchResult) -> Unit,
    onPhone: (String) -> Unit,
    onCreateGroup: (name: String, memberIds: List<Long>, phones: List<String>, avatar: PickedMedia?) -> Unit,
    onLoadDeviceContacts: (List<DeviceContact>) -> Unit,
    onDismiss: () -> Unit,
) {
    // Load the device address book (requests READ_CONTACTS) and feed it to the VM
    // so it can rank book contacts into the search results.
    val deviceContacts = rememberDeviceContacts()
    LaunchedEffect(deviceContacts) { onLoadDeviceContacts(deviceContacts) }
    var tab by remember { mutableStateOf(0) } // 0 = new chat, 1 = new group
    // --- New chat state ---
    var query by remember { mutableStateOf("") }
    val looksLikePhone = query.isNotBlank() && query.all { it.isDigit() || it == '+' || it == ' ' }
    LaunchedEffect(query, tab) {
        if (tab == 0 && !looksLikePhone && query.trim().length >= 2) onSearch(query) else onSearch("")
    }
    // --- New group state ---
    var groupName by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf<PickedMedia?>(null) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var selectedPhones by remember { mutableStateOf(setOf<String>()) }
    val pickAvatar = rememberSingleImagePickLauncher { avatar = it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tab == 0) "Новый чат" else "Новая группа") },
        text = {
            Column {
                TabRow(selectedTabIndex = tab) {
                    MdTab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Чат") })
                    MdTab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Группа") })
                }
                Spacer(Modifier.height(12.dp))
                if (tab == 0) {
                    NewChatTab(query, searching, searchResults, looksLikePhone, { query = it }, onPickResult)
                } else {
                    NewGroupTab(
                        groupName = groupName,
                        onName = { groupName = it },
                        avatarPicked = avatar != null,
                        onPickAvatar = pickAvatar,
                        contacts = contacts.filter { it.id != myId },
                        deviceContacts = deviceContacts,
                        selectedIds = selected,
                        selectedPhones = selectedPhones,
                        onToggleId = { id, on -> selected = if (on) selected + id else selected - id },
                        onTogglePhone = { p, on -> selectedPhones = if (on) selectedPhones + p else selectedPhones - p },
                    )
                }
            }
        },
        confirmButton = {
            if (tab == 0) {
                if (looksLikePhone) {
                    TextButton(onClick = { onPhone(query) }) { Text("Открыть по номеру") }
                }
            } else {
                TextButton(
                    onClick = { onCreateGroup(groupName, selected.toList(), selectedPhones.toList(), avatar) },
                    enabled = groupName.isNotBlank() && (selected.isNotEmpty() || selectedPhones.isNotEmpty()),
                ) { Text("Создать") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun NewChatTab(
    query: String,
    searching: Boolean,
    searchResults: List<SearchResult>,
    looksLikePhone: Boolean,
    onQuery: (String) -> Unit,
    onPickResult: (SearchResult) -> Unit,
) {
    Column {
        Text("Имя пользователя или номер телефона", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text("Например: Алиса или +7…") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        if (!looksLikePhone) {
            if (searching) {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                }
            }
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 280.dp)) {
                items(searchResults, key = { it.chatId }) { result ->
                    Row(
                        Modifier.fillMaxWidth().clickableRow { onPickResult(result) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(result.title, result.avatarUrl, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(result.title, style = MaterialTheme.typography.bodyLarge)
                            result.subtitle?.let {
                                Text(
                                    it,
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
}

@Composable
private fun NewGroupTab(
    groupName: String,
    onName: (String) -> Unit,
    avatarPicked: Boolean,
    onPickAvatar: () -> Unit,
    contacts: List<UserInfo>,
    deviceContacts: List<DeviceContact>,
    selectedIds: Set<Long>,
    selectedPhones: Set<String>,
    onToggleId: (Long, Boolean) -> Unit,
    onTogglePhone: (String, Boolean) -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    val serverNames = remember(contacts) { contacts.map { it.name.lowercase() }.toSet() }
    val book =
        remember(deviceContacts, serverNames) { deviceContacts.filter { it.name.lowercase() !in serverNames }.distinctBy { it.phone } }
    val q = filter.trim()
    val shownServer = contacts.filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }
    val shownBook = book.filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }
    Column {
        OutlinedTextField(
            value = groupName,
            onValueChange = onName,
            singleLine = true,
            label = { Text("Название группы") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onPickAvatar) {
            Text(if (avatarPicked) "Фото выбрано ✓" else "Добавить фото")
        }
        Spacer(Modifier.height(4.dp))
        Text("Участники (${selectedIds.size + selectedPhones.size})", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            singleLine = true,
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
            placeholder = { Text("Поиск по имени") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 240.dp)) {
            items(shownServer, key = { "u${it.id}" }) { c ->
                PickPersonRow(c.name, c.avatarUrl, null, c.id in selectedIds) { on -> onToggleId(c.id, on) }
            }
            items(shownBook, key = { "p${it.phone}" }) { c ->
                PickPersonRow(c.name, null, c.phone, c.phone in selectedPhones) { on -> onTogglePhone(c.phone, on) }
            }
        }
    }
}

/** A checkable person row (server contact or address-book entry with a phone subtitle). */
@Composable
private fun PickPersonRow(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreen(
    state: AppState,
    vm: AppViewModel,
) {
    var showNewChat by remember { mutableStateOf(false) }
    // Telegram-style search: a top-bar icon expands an inline search field; the
    // typed query filters the chat list (handled in ChatsTab).
    var searchActive by remember { mutableStateOf(false) }
    var chatQuery by remember { mutableStateOf("") }
    // Transient feedback (e.g. QR web-login result).
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearNotice()
        }
    }
    if (showNewChat) {
        NewChatDialog(
            searchResults = state.searchResults,
            searching = state.searching,
            contacts = state.contactsList,
            myId = state.account?.userId ?: -1L,
            onSearch = vm::searchUsers,
            onPickResult = { r ->
                showNewChat = false
                vm.clearSearch()
                // Address-book entries have no chat yet — open by phone lookup.
                val phone = r.phone
                if (phone != null) vm.startChatByPhone(phone) else vm.openSearchResult(r)
            },
            onLoadDeviceContacts = vm::setDeviceContacts,
            onPhone = { phone ->
                showNewChat = false
                vm.clearSearch()
                vm.startChatByPhone(phone)
            },
            onCreateGroup = { name, memberIds, phones, avatar ->
                showNewChat = false
                vm.clearSearch()
                vm.createGroup(name, memberIds, phones, avatar)
            },
            onDismiss = {
                showNewChat = false
                vm.clearSearch()
            },
        )
    }
    Scaffold(
        topBar = {
            if (state.tab == Tab.CHATS && searchActive) {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            searchActive = false
                            chatQuery = ""
                        }) {
                            Icon(AppIcons.Back, contentDescription = "Закрыть поиск")
                        }
                    },
                    title = {
                        TextField(
                            value = chatQuery,
                            onValueChange = { chatQuery = it },
                            singleLine = true,
                            placeholder = { Text("Поиск") },
                            colors =
                                TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        )
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            when (state.tab) {
                                Tab.CHATS -> "Чаты"
                                Tab.CONTACTS -> "Контакты"
                                Tab.SETTINGS -> "Настройки"
                            },
                        )
                    },
                    actions = {
                        if (state.tab == Tab.CHATS) {
                            IconButton(onClick = { searchActive = true }) {
                                Icon(AppIcons.Search, contentDescription = "Поиск")
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = state.tab == Tab.CHATS,
                    onClick = { vm.selectTab(Tab.CHATS) },
                    icon = { Icon(AppIcons.Chats, contentDescription = null) },
                    label = { Text("Чаты") },
                )
                NavigationBarItem(
                    selected = state.tab == Tab.CONTACTS,
                    onClick = { vm.selectTab(Tab.CONTACTS) },
                    icon = { Icon(AppIcons.Contacts, contentDescription = null) },
                    label = { Text("Контакты") },
                )
                NavigationBarItem(
                    selected = state.tab == Tab.SETTINGS,
                    onClick = { vm.selectTab(Tab.SETTINGS) },
                    icon = { Icon(AppIcons.Settings, contentDescription = null) },
                    label = { Text("Настройки") },
                )
            }
        },
        floatingActionButton = {
            if (state.tab == Tab.CHATS) {
                FloatingActionButton(onClick = { showNewChat = true }) {
                    Icon(AppIcons.Edit, contentDescription = "Новый чат")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.tab) {
                Tab.CHATS ->
                    ChatsTab(
                        chats = state.chats,
                        myId = state.account?.userId ?: -1L,
                        contacts = state.contactsList,
                        peers = state.groupMembers,
                        online = state.onlineUsers,
                        drafts = state.drafts,
                        typing =
                            state.typing.keys
                                .mapNotNull { id -> state.typingText(id, state.contacts)?.let { id to it } }
                                .toMap(),
                        query = if (searchActive) chatQuery else "",
                        isRefreshing = state.refreshing,
                        onRefresh = vm::refresh,
                        onOpenChat = vm::openChat,
                        onOpenUser = vm::openUser,
                    )
                Tab.CONTACTS -> ContactsTab(state.contactsList, state.onlineUsers, vm::openUser)
                Tab.SETTINGS ->
                    SettingsTab(
                        account = state.account,
                        theme = state.theme,
                        demoMode = state.demoMode,
                        dataCacheBytes = state.dataCacheBytes,
                        imageCacheBytes = state.imageCacheBytes,
                        onSetTheme = vm::setTheme,
                        onConfirmWebLogin = vm::confirmWebLogin,
                        onOpenProfile = { state.account?.let { vm.openUser(it.userId) } },
                        onOpenAbout = vm::openAbout,
                        twoFaEnabled = state.account?.twoFaEnabled,
                        onOpenTwoFa = vm::openTwoFa,
                        onClearCache = vm::clearCache,
                        onRefreshCacheSize = vm::refreshCacheSize,
                        onLogout = vm::logout,
                    )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatsTab(
    chats: List<Chat>,
    myId: Long,
    contacts: List<UserInfo>,
    peers: Map<Long, UserInfo>,
    online: Set<Long>,
    /** Unsent text per chat, previewed instead of the last message. */
    drafts: Map<Long, String>,
    /** "печатает…" per chat where someone is typing right now; shown over the preview. */
    typing: Map<Long, String> = emptyMap(),
    query: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenChat: (Chat) -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    // Filter the already-loaded chat list by title (= the other party's name for
    // dialogs); the search box that drives [query] lives in the top app bar.
    val visibleChats =
        remember(chats, query) {
            val q = query.trim()
            if (q.isBlank()) chats else chats.filter { it.title.contains(q, ignoreCase = true) }
        }
    val listState = rememberLazyListState()
    // When a chat jumps to the top (new message, or a deleted dialog revived by an
    // incoming message), a keyed LazyColumn anchors to the previously-visible row,
    // leaving the new top item just above the fold. If the user is already at/near
    // the top, scroll up to reveal it; if they've scrolled down, leave them be.
    val topChatId = chats.firstOrNull()?.id
    LaunchedEffect(topChatId) {
        if (topChatId != null && listState.firstVisibleItemIndex <= 2) {
            listState.animateScrollToItem(0)
        }
    }
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        if (visibleChats.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (query.isBlank()) {
                        "Чатов пока нет (потяните вниз для обновления)"
                    } else {
                        "Ничего не найдено"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(visibleChats, key = { it.id }) { chat ->
                    val otherId = if (chat.isDialog) chat.id xor myId else null
                    // Dialogs take their avatar from the contact (or a resolved
                    // non-contact peer); groups carry their own.
                    val avatarUrl =
                        if (chat.isDialog) {
                            otherId?.let { id ->
                                contacts.firstOrNull { it.id == id }?.avatarUrl ?: peers[id]?.avatarUrl
                            }
                        } else {
                            chat.avatarUrl
                        }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickableRow { onOpenChat(chat) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(
                            chat.title,
                            avatarUrl,
                            44.dp,
                            onClick = otherId?.let { id -> { onOpenUser(id) } },
                            online = otherId != null && otherId in online,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    chat.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (chat.muted) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "🔕",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            val draft = drafts[chat.id]?.takeIf { it.isNotBlank() }
                            val typingHere = typing[chat.id]
                            if (typingHere != null) {
                                Text(
                                    typingHere,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else if (draft != null) {
                                Text(
                                    buildAnnotatedString {
                                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) {
                                            append("Черновик: ")
                                        }
                                        // Newlines would make the row read oddly on one line.
                                        append(draft.replace('\n', ' ').trim())
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                chat.lastMessageText?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        // Trailing column: last-activity time over the unread badge (as the
                        // official list does), so a glance shows both recency and backlog.
                        Column(horizontalAlignment = Alignment.End) {
                            if (chat.lastEventTime > 0) {
                                Text(
                                    formatListTime(chat.lastEventTime),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            if (chat.unreadCount > 0) {
                                Spacer(Modifier.height(4.dp))
                                UnreadBadge(chat.unreadCount, muted = chat.muted)
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ContactsTab(
    contacts: List<UserInfo>,
    online: Set<Long>,
    onOpenUser: (Long) -> Unit,
) {
    if (contacts.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Контактов нет", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(contacts, key = { it.id }) { c ->
            Row(
                Modifier.fillMaxWidth().clickableRow { onOpenUser(c.id) }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(c.name, c.avatarUrl, 44.dp, online = c.id in online)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(c.name, style = MaterialTheme.typography.titleMedium)
                    c.description?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun SettingsTab(
    account: Account?,
    theme: ThemeMode,
    demoMode: Boolean,
    dataCacheBytes: Long,
    imageCacheBytes: Long,
    onSetTheme: (ThemeMode) -> Unit,
    onConfirmWebLogin: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAbout: () -> Unit,
    onClearCache: () -> Unit,
    onRefreshCacheSize: () -> Unit,
    onLogout: () -> Unit,
    /** Login password (2FA) state for the "Пароль для входа" row; null = unknown yet. */
    twoFaEnabled: Boolean? = null,
    onOpenTwoFa: () -> Unit = {},
) {
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmClearCache by remember { mutableStateOf(false) }
    // The size is read from storage, so refresh it whenever this tab is shown.
    LaunchedEffect(Unit) { onRefreshCacheSize() }
    if (confirmClearCache) {
        AlertDialog(
            onDismissRequest = { confirmClearCache = false },
            title = { Text("Очистить кэш?") },
            text = {
                Text(
                    "Загруженные изображения, сохранённые списки чатов и история сообщений " +
                        "будут удалены с устройства и загружены заново при необходимости. " +
                        "Сами сообщения и черновики не пострадают.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearCache = false
                    onClearCache()
                }) { Text("Очистить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearCache = false }) { Text("Отмена") }
            },
        )
    }
    // QR scanner launcher for authorizing the web/desktop version.
    val scanWebLogin = rememberQrScanLauncher(onConfirmWebLogin)
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Выйти из аккаунта?") },
            text = { Text("Вы выйдете из аккаунта на этом устройстве.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    onLogout()
                }) { Text("Выйти") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Отмена") }
            },
        )
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickableRow(onOpenProfile).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(account?.firstName ?: "Я", account?.avatarUrl, 64.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    listOfNotNull(account?.firstName, account?.lastName).joinToString(" ").ifBlank { "Профиль" },
                    style = MaterialTheme.typography.titleLarge,
                )
                Text("Открыть профиль", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { confirmLogout = true }) { Text("Выйти из аккаунта") }
        HorizontalDivider()

        // Authorize the web/desktop version by scanning its login QR code.
        if (qrScanSupported && !demoMode) {
            Row(
                Modifier.fillMaxWidth().clickableRow { scanWebLogin() }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Войти в веб-версию по QR-коду",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            HorizontalDivider()
        }

        // Security: the login password (2FA), as in the official Settings → Безопасность.
        if (!demoMode) {
            Row(
                Modifier.fillMaxWidth().clickableRow(onOpenTwoFa).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Пароль для входа", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        when (twoFaEnabled) {
                            true -> "Включён"
                            false -> "Отключён"
                            null -> "Дополнительная защита профиля"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
        }

        // Settings block.
        Text("Тема оформления", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            ThemeOption("Системная", ThemeMode.SYSTEM, theme, onSetTheme)
            ThemeOption("Тёмная", ThemeMode.DARK, theme, onSetTheme)
            ThemeOption("Светлая", ThemeMode.LIGHT, theme, onSetTheme)
        }
        HorizontalDivider()

        // Cached chat list / contacts (the warm-start snapshot).
        Row(
            Modifier.fillMaxWidth().clickableRow { confirmClearCache = true }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Очистить кэш", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                // Split out, because the thumbnails are almost always the bulk of it
                // and a single total made the number look implausibly small.
                val summary =
                    when {
                        imageCacheBytes <= 0 && dataCacheBytes <= 0 -> "Кэш пуст"
                        imageCacheBytes <= 0 -> "Данные ${formatBytes(dataCacheBytes)}"
                        else ->
                            "Изображения ${formatBytes(imageCacheBytes)} · " +
                                "данные ${formatBytes(dataCacheBytes)}"
                    }
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()
        Text(
            "О программе",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().clickableRow(onOpenAbout).padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun ThemeOption(
    label: String,
    mode: ThemeMode,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = mode == current, onClick = { onSelect(mode) }, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = mode == current, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Human-readable byte size for the cache row ("42 КБ", "1,3 МБ"). */
private fun formatBytes(bytes: Long): String =
    when {
        bytes < 1024 -> "$bytes Б"
        bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
        else -> {
            val mb = bytes * 10 / (1024 * 1024)
            "${mb / 10},${mb % 10} МБ"
        }
    }

@Composable
private fun UnreadBadge(
    count: Int,
    muted: Boolean = false,
) {
    // Muted chats get a grey badge so they don't compete with the ones that ring.
    val bg = if (muted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
    val fg = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
    Box(
        Modifier.clip(CircleShape).background(bg).padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(if (count > 99) "99+" else "$count", color = fg, style = MaterialTheme.typography.labelSmall)
    }
}
