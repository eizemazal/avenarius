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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import com.avenarius.app.model.Account
import com.avenarius.app.model.Chat
import com.avenarius.app.model.PickedMedia
import com.avenarius.app.model.SearchResult
import com.avenarius.app.model.UserInfo
import com.avenarius.app.ui.AppIcons
import com.avenarius.app.ui.AppState
import com.avenarius.app.ui.AppViewModel
import com.avenarius.app.ui.Tab
import com.avenarius.app.ui.components.Avatar
import com.avenarius.app.ui.components.clickableRow
import com.avenarius.app.ui.qrScanSupported
import com.avenarius.app.ui.rememberQrScanLauncher
import com.avenarius.app.ui.rememberSingleImagePickLauncher
import com.avenarius.app.ui.theme.ThemeMode
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
    onCreateGroup: (String, List<Long>, PickedMedia?) -> Unit,
    onDismiss: () -> Unit,
) {
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
                        selected = selected,
                        onToggle = { id, on -> selected = if (on) selected + id else selected - id },
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
                    onClick = { onCreateGroup(groupName, selected.toList(), avatar) },
                    enabled = groupName.isNotBlank() && selected.isNotEmpty(),
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
            LazyColumn(Modifier.heightIn(max = 280.dp)) {
                items(searchResults, key = { it.chatId }) { result ->
                    Row(
                        Modifier.fillMaxWidth().clickableRow { onPickResult(result) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(result.title, result.avatarUrl, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(result.title, style = MaterialTheme.typography.bodyLarge)
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
    selected: Set<Long>,
    onToggle: (Long, Boolean) -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    val shown =
        remember(contacts, filter) {
            val q = filter.trim()
            if (q.isBlank()) contacts else contacts.filter { it.name.contains(q, ignoreCase = true) }
        }
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
        Text("Участники (${selected.size})", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            singleLine = true,
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
            placeholder = { Text("Поиск по имени") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        LazyColumn(Modifier.heightIn(max = 240.dp)) {
            items(shown, key = { it.id }) { c ->
                val checked = c.id in selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = checked, onValueChange = { onToggle(c.id, it) })
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Avatar(c.name, c.avatarUrl, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(c.name, style = MaterialTheme.typography.bodyLarge)
                }
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
                vm.openSearchResult(r)
            },
            onPhone = { phone ->
                showNewChat = false
                vm.clearSearch()
                vm.startChatByPhone(phone)
            },
            onCreateGroup = { name, memberIds, avatar ->
                showNewChat = false
                vm.clearSearch()
                vm.createGroup(name, memberIds, avatar)
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
                        onSetTheme = vm::setTheme,
                        onConfirmWebLogin = vm::confirmWebLogin,
                        onOpenProfile = { state.account?.let { vm.openUser(it.userId) } },
                        onOpenAbout = vm::openAbout,
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
                            Text(chat.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            chat.lastMessageText?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                        if (chat.unreadCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            UnreadBadge(chat.unreadCount)
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
    onSetTheme: (ThemeMode) -> Unit,
    onConfirmWebLogin: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAbout: () -> Unit,
    onLogout: () -> Unit,
) {
    var confirmLogout by remember { mutableStateOf(false) }
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
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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

        // Settings block.
        Text("Тема оформления", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            ThemeOption("Системная", ThemeMode.SYSTEM, theme, onSetTheme)
            ThemeOption("Тёмная", ThemeMode.DARK, theme, onSetTheme)
            ThemeOption("Светлая", ThemeMode.LIGHT, theme, onSetTheme)
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

@Composable
private fun UnreadBadge(count: Int) {
    Box(
        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text("$count", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
    }
}
