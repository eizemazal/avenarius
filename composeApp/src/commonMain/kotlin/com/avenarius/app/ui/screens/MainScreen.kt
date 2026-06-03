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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.avenarius.app.model.Account
import com.avenarius.app.model.Chat
import com.avenarius.app.model.SearchResult
import com.avenarius.app.model.UserInfo
import com.avenarius.app.ui.AppIcons
import com.avenarius.app.ui.AppState
import com.avenarius.app.ui.AppViewModel
import com.avenarius.app.ui.Tab
import com.avenarius.app.ui.components.Avatar
import com.avenarius.app.ui.components.clickableRow
import com.avenarius.app.ui.theme.ThemeMode

@Composable
private fun NewChatDialog(
    searchResults: List<SearchResult>,
    searching: Boolean,
    onSearch: (String) -> Unit,
    onPickResult: (SearchResult) -> Unit,
    onPhone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // A query that's basically a phone number is opened by number; otherwise we
    // search users by name.
    val looksLikePhone = query.isNotBlank() && query.all { it.isDigit() || it == '+' || it == ' ' }

    LaunchedEffect(query) {
        if (!looksLikePhone && query.trim().length >= 2) onSearch(query) else onSearch("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый чат") },
        text = {
            Column {
                Text("Имя пользователя или номер телефона", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
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
        },
        confirmButton = {
            if (looksLikePhone) {
                TextButton(onClick = { onPhone(query) }) { Text("Открыть по номеру") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreen(
    state: AppState,
    vm: AppViewModel,
) {
    var showNewChat by remember { mutableStateOf(false) }
    if (showNewChat) {
        NewChatDialog(
            searchResults = state.searchResults,
            searching = state.searching,
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
            onDismiss = {
                showNewChat = false
                vm.clearSearch()
            },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    when (state.tab) {
                        Tab.CHATS -> "Чаты"
                        Tab.CONTACTS -> "Контакты"
                        Tab.SETTINGS -> "Настройки"
                    },
                )
            })
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
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.tab) {
                Tab.CHATS ->
                    ChatsTab(
                        chats = state.chats,
                        myId = state.account?.userId ?: -1L,
                        contacts = state.contactsList,
                        online = state.onlineUsers,
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
                        onSetTheme = vm::setTheme,
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
    online: Set<Long>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenChat: (Chat) -> Unit,
    onOpenUser: (Long) -> Unit,
) {
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
        if (chats.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Чатов пока нет (потяните вниз для обновления)", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(chats, key = { it.id }) { chat ->
                    val otherId = if (chat.isDialog) chat.id xor myId else null
                    // Dialogs take their avatar from the contact; groups carry their own.
                    val avatarUrl =
                        if (chat.isDialog) {
                            otherId?.let { id -> contacts.firstOrNull { it.id == id }?.avatarUrl }
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
    onSetTheme: (ThemeMode) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAbout: () -> Unit,
    onLogout: () -> Unit,
) {
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
        TextButton(onClick = onLogout) { Text("Выйти из аккаунта") }
        HorizontalDivider()

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
