package com.avenarius.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.avenarius.app.model.Account
import com.avenarius.app.model.Chat
import com.avenarius.app.model.MediaAttach
import com.avenarius.app.model.MediaType
import com.avenarius.app.model.Message
import com.avenarius.app.model.MessageStatus
import com.avenarius.app.model.SearchResult
import com.avenarius.app.model.UserInfo

private val AvenariusColors = darkColorScheme()

@Composable
fun App(viewModel: AppViewModel) {
    // Register Coil's Ktor-based network fetcher so AsyncImage can load CDN URLs.
    setSingletonImageLoaderFactory { context ->
        ImageLoader
            .Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .build()
    }
    MaterialTheme(colorScheme = AvenariusColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val state by viewModel.state.collectAsStateWithLifecycle()

            // Intercept the Android system back so leaving a chat returns to the
            // list (and code/password screens return to login) instead of exiting.
            PlatformBackHandler(enabled = viewModel.canGoBack(state.screen, state.tab)) {
                viewModel.onBack()
            }

            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    // Thin "reconnecting" strip while we transparently re-establish the
                    // connection — shown over the chat list / open chat, never a bounce.
                    if (state.reconnecting && (state.screen == Screen.CHATS || state.screen == Screen.CHAT)) {
                        ReconnectingBar()
                    }
                    Box(Modifier.weight(1f)) {
                        when (state.screen) {
                            Screen.LOADING -> LoadingScreen(reconnecting = state.reconnecting)
                            Screen.LOGIN ->
                                LoginScreen(
                                    busy = state.busy,
                                    error = state.error,
                                    onSubmit = viewModel::requestCode,
                                )
                            Screen.CODE ->
                                CodeScreen(
                                    busy = state.busy,
                                    error = state.error,
                                    codeLength = state.codeLength,
                                    onSubmit = viewModel::submitCode,
                                )
                            Screen.PASSWORD ->
                                PasswordScreen(
                                    busy = state.busy,
                                    error = state.error,
                                    hint = state.passwordHint,
                                    onSubmit = viewModel::submitPassword,
                                )
                            Screen.REGISTER ->
                                RegisterScreen(
                                    busy = state.busy,
                                    error = state.error,
                                    onSubmit = viewModel::submitRegister,
                                )
                            Screen.CHATS -> MainScreen(state, viewModel)
                            Screen.USER ->
                                UserScreen(
                                    user = state.viewingUser,
                                    isMe = state.viewingUser?.id == state.account?.userId,
                                    onBack = viewModel::closeUser,
                                    onWrite = viewModel::openDialogWith,
                                    onAvatarClick = viewModel::openImage,
                                )
                            Screen.CHAT ->
                                ChatScreen(
                                    chat = state.currentChat,
                                    messages = state.messages,
                                    myId = state.account?.userId ?: -1L,
                                    contacts = state.contacts,
                                    senderAvatars = state.contactsList.associate { it.id to it.avatarUrl },
                                    unreadAtOpen = state.openUnreadCount,
                                    busy = state.busy,
                                    error = state.error,
                                    loadingOlder = state.loadingOlder,
                                    onLoadOlder = viewModel::loadOlder,
                                    onBack = viewModel::backToChats,
                                    onSend = viewModel::sendMessage,
                                    onMediaClick = viewModel::openMedia,
                                    onOpenUser = viewModel::openUser,
                                )
                        }
                    }
                }
                // Full-screen image/video viewer, layered above everything.
                state.mediaViewer?.let { MediaViewerOverlay(it, onClose = viewModel::closeMedia) }
            }
        }
    }
}

@Composable
private fun LoadingScreen(reconnecting: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            if (reconnecting) {
                Text("Переподключение…", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ReconnectingBar() {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            "Переподключение…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun CenteredSpinner() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenteredForm(content: @Composable ColumnScopeAlias.() -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

// Alias so the lambda above reads clearly; Column's scope is ColumnScope.
private typealias ColumnScopeAlias = androidx.compose.foundation.layout.ColumnScope

@Composable
private fun LoginScreen(
    busy: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
) {
    var phone by remember { mutableStateOf("+7") }
    CenteredForm {
        Text("Авенариус", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Введите номер телефона, привязанный к Max",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Номер телефона") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorText(error)
        Button(
            onClick = { onSubmit(phone) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) SmallSpinner() else Text("Получить код")
        }
    }
}

@Composable
private fun CodeScreen(
    busy: Boolean,
    error: String?,
    codeLength: Int,
    onSubmit: (String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    CenteredForm {
        Text("Подтверждение", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Введите код из SMS ($codeLength цифр)",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter { ch -> ch.isDigit() } },
            label = { Text("Код") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorText(error)
        Button(
            onClick = { onSubmit(code) },
            enabled = !busy && code.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) SmallSpinner() else Text("Войти")
        }
    }
}

@Composable
private fun PasswordScreen(
    busy: Boolean,
    error: String?,
    hint: String?,
    onSubmit: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    CenteredForm {
        Text("Пароль для входа", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Для входа на новом устройстве введите пароль, заданный в настройках Max (Безопасность → Пароль для входа).",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!hint.isNullOrBlank()) {
            Text("Подсказка: $hint", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorText(error)
        Button(
            onClick = { onSubmit(password) },
            enabled = !busy && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) SmallSpinner() else Text("Войти")
        }
    }
}

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

@Composable
private fun RegisterScreen(
    busy: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    CenteredForm {
        Text("Регистрация", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Этот номер ещё не зарегистрирован в Max. Введите имя, чтобы создать аккаунт.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Имя") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorText(error)
        Button(
            onClick = { onSubmit(name) },
            enabled = !busy && name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) SmallSpinner() else Text("Создать аккаунт")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
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
                    icon = { Text("💬") },
                    label = { Text("Чаты") },
                )
                NavigationBarItem(
                    selected = state.tab == Tab.CONTACTS,
                    onClick = { vm.selectTab(Tab.CONTACTS) },
                    icon = { Text("👥") },
                    label = { Text("Контакты") },
                )
                NavigationBarItem(
                    selected = state.tab == Tab.SETTINGS,
                    onClick = { vm.selectTab(Tab.SETTINGS) },
                    icon = { Text("⚙") },
                    label = { Text("Настройки") },
                )
            }
        },
        floatingActionButton = {
            if (state.tab == Tab.CHATS) {
                FloatingActionButton(onClick = { showNewChat = true }) {
                    Text("✎", style = MaterialTheme.typography.headlineSmall)
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
                        isRefreshing = state.refreshing,
                        onRefresh = vm::refresh,
                        onOpenChat = vm::openChat,
                        onOpenUser = vm::openUser,
                    )
                Tab.CONTACTS -> ContactsTab(state.contactsList, vm::openUser)
                Tab.SETTINGS ->
                    SettingsTab(
                        account = state.account,
                        onOpenProfile = { state.account?.let { vm.openUser(it.userId) } },
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
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenChat: (Chat) -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        if (chats.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Чатов пока нет (потяните вниз для обновления)", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(chats, key = { it.id }) { chat ->
                    val otherId = if (chat.isDialog) chat.id xor myId else null
                    val avatarUrl = otherId?.let { id -> contacts.firstOrNull { it.id == id }?.avatarUrl }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickableRow { onOpenChat(chat) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(chat.title, avatarUrl, 44.dp, onClick = otherId?.let { id -> { onOpenUser(id) } })
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(chat.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f))
                                if (chat.unreadCount > 0) UnreadBadge(chat.unreadCount)
                            }
                            chat.lastMessageText?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodyMedium,
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
    }
}

@Composable
private fun ContactsTab(
    contacts: List<UserInfo>,
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
                Avatar(c.name, c.avatarUrl, 44.dp)
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
    onOpenProfile: () -> Unit,
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
        HorizontalDivider()
        TextButton(onClick = onLogout) { Text("Выйти из аккаунта") }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserScreen(
    user: UserInfo?,
    isMe: Boolean,
    onBack: () -> Unit,
    onWrite: (UserInfo) -> Unit,
    onAvatarClick: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профиль") },
                navigationIcon = { IconButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineMedium) } },
            )
        },
    ) { padding ->
        if (user == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CenteredSpinner() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Tapping the avatar opens it full-screen (reusing the image viewer).
            Avatar(user.name, user.avatarUrl, 96.dp, onClick = user.avatarUrl?.let { url -> { onAvatarClick(url) } })
            Text(user.name, style = MaterialTheme.typography.headlineSmall)
            // Bio — render URLs as clickable links.
            user.description?.takeIf { it.isNotBlank() }?.let { bio ->
                LinkedText(
                    bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isMe) {
                Button(onClick = { onWrite(user) }) { Text("Написать") }
            }
            HorizontalDivider()
            InfoRow("Телефон", user.phone?.let { "+$it" })
            InfoRow(
                "Пол",
                when (user.gender) {
                    "MALE" -> "Мужской"
                    "FEMALE" -> "Женский"
                    else -> user.gender
                },
            )
            InfoRow("Страна", user.country)
            InfoRow("Ссылка", user.link)
            InfoRow("Регистрация", user.registrationTime?.takeIf { it > 0 }?.let { formatDate(it) })
            InfoRow("ID", user.id.toString())
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String?,
) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private val UrlRegex =
    Regex(
        """(https?://[^\s]+)|([\w.-]+\.(?:ru|com|org|net|me|io|info|app|tv|dev)(?:/[^\s]*)?)""",
        RegexOption.IGNORE_CASE,
    )

/** Renders [text] with embedded URLs as tappable links (opens the system browser). */
@Composable
private fun LinkedText(
    text: String,
    style: TextStyle,
    color: Color,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated =
        remember(text, linkColor) {
            buildAnnotatedString {
                var last = 0
                for (m in UrlRegex.findAll(text)) {
                    if (m.range.first > last) append(text.substring(last, m.range.first))
                    val raw = m.value
                    val url = if (raw.startsWith("http", ignoreCase = true)) raw else "https://$raw"
                    withLink(
                        LinkAnnotation.Url(
                            url,
                            TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                        ),
                    ) { append(raw) }
                    last = m.range.last + 1
                }
                if (last < text.length) append(text.substring(last))
            }
        }
    Text(annotated, style = style, color = color)
}

@Composable
private fun Avatar(
    name: String,
    url: String?,
    size: Dp,
    onClick: (() -> Unit)? = null,
) {
    var mod = Modifier.size(size).clip(CircleShape).background(avatarColor(name))
    if (onClick != null) mod = mod.clickable(onClick = onClick)
    if (!url.isNullOrBlank()) {
        AsyncImage(model = url, contentDescription = name, contentScale = ContentScale.Crop, modifier = mod)
    } else {
        Box(mod, contentAlignment = Alignment.Center) {
            Text(
                name
                    .trim()
                    .firstOrNull()
                    ?.uppercaseChar()
                    ?.toString() ?: "?",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    chat: Chat?,
    messages: List<Message>,
    myId: Long,
    contacts: Map<Long, String>,
    senderAvatars: Map<Long, String?>,
    unreadAtOpen: Int,
    busy: Boolean,
    error: String?,
    loadingOlder: Boolean,
    onLoadOlder: () -> Unit,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onMediaClick: (MediaAttach, String?) -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isDialog = chat?.isDialog ?: true
    // For a 1:1 dialog the other user's id is chatId XOR myId.
    val otherUserId = if (isDialog && chat != null && myId >= 0) chat.id xor myId else null

    // Index of the first unread message (the divider sits just before it).
    val firstUnread = if (unreadAtOpen in 1..messages.size) messages.size - unreadAtOpen else -1

    var positioned by remember(chat?.id) { mutableStateOf(false) }
    var prevSize by remember(chat?.id) { mutableStateOf(0) }
    // When loading older messages we remember the top item so we can stay on it
    // after the prepend (otherwise the list visibly jumps).
    var restoreAnchor by remember(chat?.id) { mutableStateOf<Pair<Any, Int>?>(null) }

    LaunchedEffect(messages.size, chat?.id) {
        if (messages.isEmpty()) return@LaunchedEffect
        when {
            !positioned -> {
                // Jump INSTANTLY to the first unread (or bottom) on open.
                listState.scrollToItem(if (firstUnread > 0) firstUnread else messages.lastIndex)
                positioned = true
            }
            restoreAnchor != null -> {
                // Older page prepended: scroll back to the previously-top message.
                val (key, offset) = restoreAnchor!!
                val idx = messages.indexOfFirst { (it.id ?: it.cid ?: it.time) == key }
                if (idx >= 0) listState.scrollToItem(idx, offset)
                restoreAnchor = null
            }
            messages.size > prevSize -> {
                // New message appended: follow it only if already near the bottom.
                val lastVisible =
                    listState.layoutInfo.visibleItemsInfo
                        .lastOrNull()
                        ?.index ?: 0
                if (lastVisible >= prevSize - 2) listState.animateScrollToItem(messages.lastIndex)
            }
        }
        prevSize = messages.size
    }

    // Trigger loading older messages when the user reaches the very top.
    LaunchedEffect(listState, messages.size) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { idx ->
                if (positioned && idx == 0 && messages.isNotEmpty() && restoreAnchor == null) {
                    val first = messages.first()
                    restoreAnchor = (first.id ?: first.cid ?: first.time) to listState.firstVisibleItemScrollOffset
                    onLoadOlder()
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleMod = if (otherUserId != null) Modifier.clickable { onOpenUser(otherUserId) } else Modifier
                    Text(chat?.title ?: "Чат", modifier = titleMod)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
                },
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Сообщение") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                )
                Button(
                    onClick = {
                        onSend(draft)
                        draft = ""
                    },
                    enabled = draft.isNotBlank(),
                ) { Text("➤") }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (busy && messages.isEmpty()) {
                CenteredSpinner()
            }
            if (loadingOlder) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp).size(24.dp),
                )
            }
            if (error != null) {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(8.dp),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(messages, key = { _, m -> m.id ?: m.cid ?: m.time }) { index, msg ->
                    if (index == firstUnread && firstUnread > 0) NewMessagesDivider()
                    val isMine = msg.senderId == myId
                    val prev = messages.getOrNull(index - 1)
                    // Show the avatar only on the first message of a run from one sender.
                    val startsRun = prev == null || prev.senderId != msg.senderId
                    MessageRow(
                        msg = msg,
                        isMine = isMine,
                        senderName = contacts[msg.senderId] ?: "—",
                        senderAvatar = senderAvatars[msg.senderId],
                        isGroup = !isDialog,
                        startsRun = startsRun,
                        onMediaClick = onMediaClick,
                        onAvatarClick = { onOpenUser(msg.senderId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageRow(
    msg: Message,
    isMine: Boolean,
    senderName: String,
    senderAvatar: String?,
    isGroup: Boolean,
    startsRun: Boolean,
    onMediaClick: (MediaAttach, String?) -> Unit,
    onAvatarClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        // Avatars are only meaningful in group chats; in a 1:1 dialog the only
        // other participant is obvious, so no avatar gutter is shown.
        if (!isMine && isGroup) {
            if (startsRun) Avatar(senderName, senderAvatar, 32.dp, onClick = onAvatarClick) else Spacer(Modifier.size(32.dp))
            Spacer(Modifier.width(6.dp))
        }
        val bg = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        val fg = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        Box(
            Modifier
                .widthIn(max = 280.dp)
                .background(bg, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Column {
                if (isGroup && !isMine && startsRun) {
                    Text(senderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                msg.media.forEach { media ->
                    MediaThumbnail(media, onClick = { onMediaClick(media, msg.id) })
                    Spacer(Modifier.height(4.dp))
                }
                if (msg.text.isNotEmpty()) {
                    Text(msg.text, color = fg, style = MaterialTheme.typography.bodyLarge)
                }
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(formatClock(msg.time), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f))
                    if (isMine) {
                        val read = msg.status == MessageStatus.READ
                        Text(
                            if (read) "✓✓" else "✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (read) Color(0xFF8AB4F8) else fg.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaThumbnail(
    media: MediaAttach,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    var mod = Modifier.widthIn(max = 240.dp).heightIn(max = 320.dp).clip(shape)
    if (media.width > 0 && media.height > 0) {
        mod = Modifier.width(240.dp).aspectRatio(media.width.toFloat() / media.height).clip(shape)
    }
    Box(modifier = Modifier.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = media.url,
            contentDescription = if (media.type == MediaType.VIDEO) "Видео" else "Фото",
            contentScale = ContentScale.Crop,
            modifier = mod,
        )
        if (media.type == MediaType.VIDEO) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color(0x88000000)),
                contentAlignment = Alignment.Center,
            ) {
                Text("▶", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun MediaViewerOverlay(
    viewer: MediaViewer,
    onClose: () -> Unit,
) {
    PlatformBackHandler(enabled = true, onBack = onClose)
    Box(
        Modifier.fillMaxSize().background(Color(0xF2000000)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        when (viewer) {
            is MediaViewer.Loading -> CircularProgressIndicator(color = Color.White)
            is MediaViewer.Image -> ZoomableImage(viewer.url)
            is MediaViewer.Video -> VideoPlayer(viewer.url, Modifier.fillMaxWidth().heightIn(max = 480.dp))
        }
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            Text("✕", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun ZoomableImage(url: String) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset += pan
                    }
                },
    )
}

private val AvatarColors =
    listOf(
        Color(0xFFE57373),
        Color(0xFF64B5F6),
        Color(0xFF81C784),
        Color(0xFFFFB74D),
        Color(0xFFBA68C8),
        Color(0xFF4DB6AC),
        Color(0xFFF06292),
        Color(0xFF9575CD),
    )

private fun avatarColor(key: String): Color = AvatarColors[(key.hashCode() and 0x7fffffff) % AvatarColors.size]

@Composable
private fun NewMessagesDivider() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            "новые сообщения",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        HorizontalDivider(Modifier.weight(1f))
    }
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) {
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SmallSpinner() {
    CircularProgressIndicator(
        modifier = Modifier.height(20.dp),
        color = MaterialTheme.colorScheme.onPrimary,
    )
}

/** Small wrapper for a clickable row that works across platforms. */
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
