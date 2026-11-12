package com.avenarius.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.avenarius.app.data.Prefs
import com.avenarius.app.model.Account
import com.avenarius.app.model.Chat
import com.avenarius.app.model.ChatFolder
import com.avenarius.app.model.DeviceContact
import com.avenarius.app.model.PickedMedia
import com.avenarius.app.model.SearchResult
import com.avenarius.app.model.UserInfo
import com.avenarius.app.model.isSavedMessages
import com.avenarius.app.model.matches
import com.avenarius.app.model.previewLabel
import com.avenarius.app.net.MessageHit
import com.avenarius.app.net.Presence
import com.avenarius.app.net.Privacy
import com.avenarius.app.net.SessionInfo
import com.avenarius.app.ui.AppIcons
import com.avenarius.app.ui.AppLinks
import com.avenarius.app.ui.AppState
import com.avenarius.app.ui.AppViewModel
import com.avenarius.app.ui.PlatformBackHandler
import com.avenarius.app.ui.Tab
import com.avenarius.app.ui.components.AppSheet
import com.avenarius.app.ui.components.Avatar
import com.avenarius.app.ui.components.CircleIconButton
import com.avenarius.app.ui.components.GroupCard
import com.avenarius.app.ui.components.GroupRow
import com.avenarius.app.ui.components.PillTextField
import com.avenarius.app.ui.components.QuickAction
import com.avenarius.app.ui.components.SheetAction
import com.avenarius.app.ui.components.clickableRow
import com.avenarius.app.ui.components.fadingEndScroll
import com.avenarius.app.ui.components.tileColor
import com.avenarius.app.ui.formatClock
import com.avenarius.app.ui.formatDate
import com.avenarius.app.ui.formatListTime
import com.avenarius.app.ui.lastSeenText
import com.avenarius.app.ui.nowMillis
import com.avenarius.app.ui.qrScanSupported
import com.avenarius.app.ui.rememberAppReviewLauncher
import com.avenarius.app.ui.rememberDeviceContacts
import com.avenarius.app.ui.rememberQrScanLauncher
import com.avenarius.app.ui.rememberSingleImagePickLauncher
import com.avenarius.app.ui.shareText
import com.avenarius.app.ui.theme.Accent
import com.avenarius.app.ui.theme.ThemeMode
import com.avenarius.app.ui.theme.WALLPAPERS
import com.avenarius.app.ui.theme.accentGradient
import com.avenarius.app.ui.theme.aurora
import com.avenarius.app.ui.theme.chatWallpaper
import com.avenarius.app.ui.theme.dynamicColorScheme
import com.avenarius.app.ui.theme.isLightTheme
import com.avenarius.app.ui.theme.onAccentGradient
import com.avenarius.app.ui.typingText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val CHAT_AVATAR = 56.dp

/** Room the floating bar takes at the bottom of every tab, so the last row scrolls clear of it. */
private val NAV_SPACE = 92.dp

/** Bottom padding for a tab's scrolling content: the floating bar plus the system nav inset. */
@Composable
private fun navClearance(): PaddingValues =
    PaddingValues(bottom = NAV_SPACE + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreen(
    state: AppState,
    vm: AppViewModel,
) {
    var showNewChat by remember { mutableStateOf(false) }
    // The folder editor, from a chip's long press or Settings → Папки; a blank id = a new folder.
    var editingFolder by remember { mutableStateOf<ChatFolder?>(null) }
    editingFolder?.let { folder ->
        val live = state.folders.firstOrNull { it.id == folder.id } ?: folder
        val myId = state.account?.userId ?: -1L
        FolderEditorSheet(
            folder = live,
            chats = state.chats,
            avatarOf = { chatAvatar(it, myId, state.contactsList, state.groupMembers) },
            onSave = { title, ids ->
                editingFolder = null
                if (live.id.isEmpty()) vm.createFolder(title, ids) else vm.saveFolder(live.copy(title = title, include = ids))
            },
            onDelete =
                {
                    editingFolder = null
                    vm.deleteFolder(live.id)
                }.takeIf { live.id.isNotEmpty() },
            onDismiss = { editingFolder = null },
        )
    }
    // Search: a header icon expands an inline field; the query filters the loaded chats
    // and, past two characters, also asks the server for people and public chats.
    var searchActive by remember { mutableStateOf(false) }
    var chatQuery by remember { mutableStateOf("") }
    val searching = searchActive && state.tab == Tab.CHATS
    LaunchedEffect(chatQuery, searching) {
        if (searching && chatQuery.trim().length >= 2) {
            delay(350) // let typing settle before hitting the network
            vm.searchUsers(chatQuery)
            vm.searchAllMessages(chatQuery)
        } else if (!showNewChat) {
            vm.clearSearch()
        }
    }
    val closeSearch = {
        searchActive = false
        chatQuery = ""
    }
    PlatformBackHandler(enabled = searching, onBack = closeSearch)
    // Transient feedback (e.g. QR web-login result).
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearNotice()
        }
    }
    if (showNewChat) {
        NewChatSheet(
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
            onCreateChannel = { name, description, avatar ->
                showNewChat = false
                vm.clearSearch()
                vm.createChannel(name, description, avatar)
            },
            onDismiss = {
                showNewChat = false
                vm.clearSearch()
            },
        )
    }
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(cs.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            MainHeader(
                tab = state.tab,
                // The connection state lives in the header instead of a strip pushing the UI down.
                connecting = if (state.reconnecting) (if (state.syncedOnce) "Переподключение…" else "Подключение…") else null,
                searchActive = searching,
                query = chatQuery,
                onQuery = { chatQuery = it },
                onOpenSearch = { searchActive = true },
                onCloseSearch = closeSearch,
            )
            AnimatedContent(
                targetState = state.tab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.weight(1f),
                label = "tab",
            ) { tab ->
                when (tab) {
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
                            query = if (searching) chatQuery else "",
                            searchResults = if (searching) state.searchResults else emptyList(),
                            searchingRemote = searching && state.searching,
                            messageHits = if (searching) state.messageHits else emptyList(),
                            onOpenHit = { hit ->
                                closeSearch()
                                vm.openMessageHit(hit)
                            },
                            onShowMore = vm::searchMore.takeIf { searching && state.searchMarker != null },
                            onOpenResult = { r ->
                                closeSearch()
                                val phone = r.phone
                                if (phone != null) vm.startChatByPhone(phone) else vm.openSearchResult(r)
                            },
                            isRefreshing = state.refreshing,
                            onRefresh = vm::refresh,
                            onOpenChat = vm::openChat,
                            onOpenUser = vm::openUser,
                            onOpenGroup = vm::openGroup,
                            onTogglePin = vm::togglePin,
                            onToggleMute = vm::toggleMute,
                            onMarkUnread = vm::markUnread,
                            folders = state.folders,
                            folderOrder = state.folderOrder,
                            onEditFolder = { editingFolder = it },
                            onDeleteFolder = vm::deleteFolder,
                            onToggleFolderChat = vm::toggleChatInFolder,
                            showRatePrompt = state.showRatePrompt && state.call == null,
                            onRateNow = vm::rateNow,
                            onRateLater = vm::rateLater,
                            onRateNever = vm::rateNever,
                        )
                    Tab.CONTACTS -> ContactsTab(state.contactsList, state.onlineUsers, state.presence, vm::openUser)
                    Tab.SETTINGS ->
                        SettingsTab(
                            account = state.account,
                            theme = state.theme,
                            accent = state.accent,
                            uiScale = state.uiScale,
                            fontScale = state.fontScale,
                            onSetAccent = vm::setAccent,
                            onSetUiScale = vm::setUiScale,
                            onSetFontScale = vm::setFontScale,
                            wallpaper = state.wallpaper,
                            onSetWallpaper = vm::setWallpaper,
                            demoMode = state.demoMode,
                            privacy = state.privacy,
                            onUpdatePrivacy = vm::updatePrivacy,
                            dataCacheBytes = state.dataCacheBytes,
                            imageCacheBytes = state.imageCacheBytes,
                            onSetTheme = vm::setTheme,
                            onConfirmWebLogin = vm::confirmWebLogin,
                            onOpenProfile = { state.account?.let { vm.openUser(it.userId) } },
                            onEditProfile = vm::openEditProfile,
                            onOpenSaved = vm::openSavedMessages,
                            onOpenAbout = vm::openAbout,
                            twoFaEnabled = state.account?.twoFaEnabled,
                            onOpenTwoFa = vm::openTwoFa,
                            onClearCache = vm::clearCache,
                            onRefreshCacheSize = vm::refreshCacheSize,
                            onLogout = vm::logout,
                            sendOnEnter = state.sendOnEnter,
                            onSetSendOnEnter = vm::setSendOnEnter,
                            quickReaction = state.quickReaction,
                            onSetQuickReaction = vm::setQuickReaction,
                            reactionChoices = state.reactionsMenu.ifEmpty { listOf("❤️", "👍", "🤣", "😱", "🥰", "🔥", "😢") }.take(8),
                            sessions = state.sessions,
                            onLoadSessions = vm::loadSessions,
                            onCloseOthers = vm::closeOtherSessions,
                            blockedUsers = state.blockedUsers,
                            onLoadBlocked = vm::loadBlockedUsers,
                            onUnblock = { vm.setBlocked(it, false) },
                            folders = state.folders,
                            onMoveFolder = vm::moveFolder,
                            onEditFolder = { editingFolder = it },
                        )
                }
            }
        }
        // Rows fade out under the floating bar instead of being cut by it.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(NAV_SPACE + 48.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, cs.background.copy(alpha = 0.94f)))),
        )
        FloatingNav(
            tab = state.tab,
            unreadChats = state.chats.count { it.unreadCount > 0 && !it.muted },
            onSelect = vm::selectTab,
            onCompose = { showNewChat = true },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        SnackbarHost(
            snackbarHostState,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = NAV_SPACE),
        )
    }
}

/** Big title + actions; turns into a search field on the chats tab. */
@Composable
private fun MainHeader(
    tab: Tab,
    connecting: String?,
    searchActive: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(68.dp).padding(start = 20.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searchActive) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            CircleIconButton(AppIcons.Back, "Закрыть поиск", onCloseSearch)
            Spacer(Modifier.width(10.dp))
            PillTextField(query, onQuery, "Поиск", Modifier.weight(1f), focusRequester = focusRequester)
        } else {
            Column(Modifier.weight(1f)) {
                Text(
                    when (tab) {
                        Tab.CHATS -> "Чаты"
                        Tab.CONTACTS -> "Контакты"
                        Tab.SETTINGS -> "Настройки"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                )
                AnimatedVisibility(connecting != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(11.dp), strokeWidth = 1.5.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            connecting ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (tab == Tab.CHATS) CircleIconButton(AppIcons.Search, "Поиск", onOpenSearch)
        }
    }
}

/** The floating tab pill, with the compose button riding next to it on the chats tab (its slot kept on the others). */
@Composable
private fun FloatingNav(
    tab: Tab,
    unreadChats: Int,
    onSelect: (Tab) -> Unit,
    onCompose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = cs.surfaceContainerHigh,
            border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f)),
            shadowElevation = 10.dp,
            modifier = Modifier.weight(1f).height(64.dp),
        ) {
            Row(Modifier.padding(5.dp).selectableGroup()) {
                NavItem(AppIcons.Chats, AppIcons.ChatsSelected, "Чаты", tab == Tab.CHATS, unreadChats) { onSelect(Tab.CHATS) }
                NavItem(AppIcons.Contacts, AppIcons.ContactsSelected, "Контакты", tab == Tab.CONTACTS) { onSelect(Tab.CONTACTS) }
                NavItem(AppIcons.Settings, AppIcons.SettingsSelected, "Настройки", tab == Tab.SETTINGS) { onSelect(Tab.SETTINGS) }
            }
        }
        // The slot keeps its size on every tab (only the button fades), so the tabs never shift under a finger.
        Spacer(Modifier.width(12.dp))
        val fab by animateFloatAsState(if (tab == Tab.CHATS) 1f else 0f, label = "fab")
        Box(Modifier.size(64.dp)) {
            if (fab > 0f) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            alpha = fab
                            scaleX = fab
                            scaleY = fab
                        }.shadow(10.dp, CircleShape, ambientColor = cs.primary, spotColor = cs.primary)
                        .clip(CircleShape)
                        .background(accentGradient())
                        .clickable(enabled = tab == Tab.CHATS, onClick = onCompose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Edit, contentDescription = "Новый чат", tint = onAccentGradient(), modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    icon: Painter,
    selectedIcon: Painter,
    label: String,
    selected: Boolean,
    badge: Int = 0,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) cs.primary.copy(alpha = 0.16f) else Color.Transparent, label = "navBg")
    val fg by animateColorAsState(if (selected) cs.primary else cs.onSurfaceVariant, label = "navFg")
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(CircleShape)
            .background(bg)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Icon(if (selected) selectedIcon else icon, contentDescription = null, tint = fg, modifier = Modifier.size(24.dp))
            if (badge > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(start = 16.dp)
                        .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                        .clip(CircleShape)
                        .background(cs.error)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (badge > 99) "99+" else "$badge",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = cs.onError,
                    )
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = fg, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    /** People and public chats found beyond the loaded list, for the active search. */
    searchResults: List<SearchResult> = emptyList(),
    searchingRemote: Boolean = false,
    /** «СООБЩЕНИЯ»: messages found across the chats, one per chat. */
    messageHits: List<MessageHit> = emptyList(),
    onOpenHit: (MessageHit) -> Unit = {},
    /** «Показать больше» under the public results; null when there are no more. */
    onShowMore: (() -> Unit)? = null,
    onOpenResult: (SearchResult) -> Unit = {},
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenChat: (Chat) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenGroup: (Chat) -> Unit = {},
    onTogglePin: (Long) -> Unit = {},
    onToggleMute: (Chat) -> Unit = {},
    onMarkUnread: (Chat) -> Unit = {},
    /** Server folders («Все» included); empty keeps the built-in chips. */
    folders: List<ChatFolder> = emptyList(),
    /** The server's folder order, system folders sent as an id alone included. */
    folderOrder: List<String> = emptyList(),
    onEditFolder: (ChatFolder) -> Unit = {},
    onDeleteFolder: (String) -> Unit = {},
    onToggleFolderChat: (folderId: String, chatId: Long) -> Unit = { _, _ -> },
    showRatePrompt: Boolean = false,
    onRateNow: () -> Unit = {},
    onRateLater: () -> Unit = {},
    onRateNever: () -> Unit = {},
) {
    val launchReview = rememberAppReviewLauncher()
    var filterKey by remember { mutableStateOf(ChatFilter.ALL.name) }
    val chips = remember(folders, folderOrder, myId) { chatChips(folders, folderOrder, myId) }
    // A folder deleted while open falls back to «Все».
    val filter = chips.firstOrNull { it.key == filterKey } ?: chips.first()
    var menuFor by remember { mutableStateOf<Chat?>(null) }
    var folderMenu by remember { mutableStateOf<ChatFolder?>(null) }
    var folderPickFor by remember { mutableStateOf<Chat?>(null) }
    val haptic = LocalHapticFeedback.current
    val q = query.trim()
    folderMenu?.let { folder ->
        FolderActionsSheet(
            folder = folder,
            onDismiss = { folderMenu = null },
            onEdit = {
                folderMenu = null
                onEditFolder(folder)
            },
            onDelete = {
                folderMenu = null
                onDeleteFolder(folder.id)
            },
        )
    }
    folderPickFor?.let { chat ->
        FolderPickSheet(chat, folders.filterNot { it.isAll }, onToggle = { onToggleFolderChat(it.id, chat.id) }) { folderPickFor = null }
    }
    val visibleChats =
        remember(chats, q, filter) {
            chats
                // A search looks through every chat, whatever folder was open.
                .filter { if (q.isBlank()) filter.matches(it) else it.title.contains(q, ignoreCase = true) }
                // Pinned first, the latest pin on top; stable, so the rest keep their recency order.
                .sortedByDescending { it.favIndex }
        }
    val extraResults = remember(searchResults, visibleChats) { searchResults.filter { r -> visibleChats.none { it.id == r.chatId } } }
    val names = remember(contacts, peers) { peers.mapValues { it.value.name } + contacts.associate { it.id to it.name } }
    val listState = rememberLazyListState()
    val ptrState = rememberPullToRefreshState()
    val refreshingNow by rememberUpdatedState(isRefreshing)
    val scope = rememberCoroutineScope()
    // When a chat jumps to the top (new message, or a deleted dialog revived by an
    // incoming message), a keyed LazyColumn anchors to the previously-visible row,
    // leaving the new top item just above the fold. If the user is already at/near
    // the top, scroll up to reveal it; if they've scrolled down, leave them be.
    val topChatId = visibleChats.firstOrNull()?.id // also follows a freshly pinned chat
    LaunchedEffect(topChatId) {
        if (topChatId != null && listState.firstVisibleItemIndex <= 2) {
            listState.animateScrollToItem(0)
        }
    }
    menuFor?.let { chat ->
        // The live copy, so the sheet's labels follow a mute/pin made from it.
        val live = chats.firstOrNull { it.id == chat.id } ?: chat
        ChatActionsSheet(
            chat = live,
            avatarUrl = chatAvatar(live, myId, contacts, peers),
            isPinned = live.favIndex > 0,
            onDismiss = { menuFor = null },
            onTogglePin = {
                menuFor = null
                onTogglePin(live.id)
            },
            onToggleMute = {
                menuFor = null
                onToggleMute(live)
            },
            onMarkUnread =
                {
                    menuFor = null
                    onMarkUnread(live)
                }.takeIf { live.unreadCount == 0 && !live.isSavedMessages },
            onOpenInfo = {
                menuFor = null
                if (live.isDialog) onOpenUser(live.id xor myId) else onOpenGroup(live)
            },
            onAddToFolder =
                {
                    menuFor = null
                    folderPickFor = live
                }.takeIf { folders.any { !it.isAll } },
        )
    }
    Column(Modifier.fillMaxSize()) {
        if (q.isBlank()) {
            ChatFilterRow(chats, chips, filter, onSelect = { filterKey = it.key }) { chip ->
                chip.folder?.let {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    folderMenu = it
                }
            }
        }
        // Clipped, so the indicator slides out from under the chips instead of drawing over them.
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                onRefresh()
                // The box only hides on a change of isRefreshing; a refresh that ends before a frame
                // sees it (demo, no session) never produces one, so wait for the end and hide it here.
                scope.launch {
                    withFrameNanos { }
                    snapshotFlow { refreshingNow }.first { !it }
                    ptrState.animateToHidden()
                }
            },
            state = ptrState,
            modifier = Modifier.weight(1f).clipToBounds(),
        ) {
            if (visibleChats.isEmpty() && extraResults.isEmpty() && messageHits.isEmpty() && !searchingRemote) {
                EmptyState(
                    if (q.isBlank() && filter.key == ChatFilter.ALL.name) AppIcons.Chats else AppIcons.Search,
                    if (q.isBlank() && filter.key == ChatFilter.ALL.name) "Чатов пока нет" else "Ничего не найдено",
                    if (q.isBlank() && filter.key == ChatFilter.ALL.name) "Потяните вниз, чтобы обновить, или начните новый чат" else null,
                )
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = navClearance()) {
                    if (showRatePrompt && q.isBlank()) {
                        item(key = "rate-prompt") {
                            RatePromptCard(
                                onRate = {
                                    launchReview()
                                    onRateNow()
                                },
                                onLater = onRateLater,
                                onNever = onRateNever,
                            )
                        }
                    }
                    items(visibleChats, key = { it.id }) { chat ->
                        val otherId = if (chat.isDialog) chat.id xor myId else null
                        ChatRow(
                            chat = chat,
                            avatarUrl = chatAvatar(chat, myId, contacts, peers),
                            online = otherId != null && otherId in online,
                            draft = drafts[chat.id]?.takeIf { it.isNotBlank() },
                            typing = typing[chat.id],
                            isPinned = chat.favIndex > 0,
                            prefix =
                                when {
                                    chat.isDialog || chat.isChannel || chat.lastSenderId == 0L -> null
                                    chat.lastSenderId == myId -> "Вы"
                                    else -> names[chat.lastSenderId]?.substringBefore(' ')
                                },
                            tick =
                                if (chat.lastSenderId == myId && !chat.isChannel && !chat.isSavedMessages && chat.lastMessageTime > 0) {
                                    chat.otherReadMark >= chat.lastMessageTime
                                } else {
                                    null
                                },
                            onAvatarClick = otherId?.takeIf { it != 0L }?.let { id -> { onOpenUser(id) } },
                            modifier =
                                Modifier.animateItem().combinedClickable(
                                    onClick = { onOpenChat(chat) },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuFor = chat
                                    },
                                ),
                        )
                    }
                    if (extraResults.isNotEmpty() || searchingRemote) {
                        item(key = "global-header") {
                            Row(
                                Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "ГЛОБАЛЬНЫЙ ПОИСК",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (searchingRemote) {
                                    Spacer(Modifier.width(8.dp))
                                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                }
                            }
                        }
                        items(extraResults, key = { "r${it.chatId}" }) { r ->
                            PersonRow(r.title, r.avatarUrl, r.subtitle, Modifier.clickable { onOpenResult(r) })
                        }
                        if (onShowMore != null) {
                            item(key = "global-more") {
                                Text(
                                    "Показать больше",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                onClick = onShowMore,
                                            ).padding(horizontal = 20.dp, vertical = 12.dp),
                                )
                            }
                        }
                    }
                    if (messageHits.isNotEmpty()) {
                        item(key = "messages-header") {
                            Text(
                                "СООБЩЕНИЯ",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
                            )
                        }
                        items(messageHits, key = { "h${it.message.chatId}:${it.message.id}" }) { hit ->
                            val chat = chats.firstOrNull { it.id == hit.message.chatId } ?: hit.chat
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpenHit(hit) }.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val title = chat?.title ?: "Чат"
                                if (chat?.isSavedMessages == true) {
                                    SavedMessagesAvatar(44.dp)
                                } else {
                                    Avatar(title, chat?.let { chatAvatar(it, myId, contacts, peers) }, 44.dp)
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            formatListTime(hit.message.time),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        hit.message.previewLabel().replace('\n', ' '),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Dialogs take their avatar from the contact (or a resolved non-contact peer); groups carry their own. */
internal fun chatAvatar(
    chat: Chat,
    myId: Long,
    contacts: List<UserInfo>,
    peers: Map<Long, UserInfo>,
): String? =
    if (chat.isDialog) {
        val id = chat.id xor myId
        contacts.firstOrNull { it.id == id }?.avatarUrl ?: peers[id]?.avatarUrl
    } else {
        chat.avatarUrl
    }

@Composable
private fun ChatRow(
    chat: Chat,
    avatarUrl: String?,
    online: Boolean,
    draft: String?,
    typing: String?,
    isPinned: Boolean,
    /** Author of the last message for a group row ("Вы" for our own); null elsewhere. */
    prefix: String? = null,
    /** Delivery tick of our own last message: true = read; null = not ours. */
    tick: Boolean? = null,
    onAvatarClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val hasUnread = chat.unreadCount > 0
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chat.isSavedMessages) {
            SavedMessagesAvatar(CHAT_AVATAR)
        } else {
            Avatar(chat.title, avatarUrl, CHAT_AVATAR, onClick = onAvatarClick, online = online)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The name gives way to its marks and to the time, never the other way round.
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (chat.isChannel) {
                        Icon(AppIcons.Channel, contentDescription = "Канал", tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        chat.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (chat.official) {
                        Spacer(Modifier.width(4.dp))
                        Icon(AppIcons.Verified, contentDescription = "Официальный", tint = cs.primary, modifier = Modifier.size(16.dp))
                    }
                    if (chat.muted) {
                        Spacer(Modifier.width(4.dp))
                        Icon(AppIcons.Muted, contentDescription = "Без звука", tint = cs.outline, modifier = Modifier.size(15.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (tick != null) {
                    Icon(
                        if (tick) AppIcons.Read else AppIcons.Delivered,
                        contentDescription = if (tick) "Прочитано" else "Доставлено",
                        tint = if (tick) cs.primary else cs.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                }
                if (chat.lastEventTime > 0) {
                    Text(
                        formatListTime(chat.lastEventTime),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (hasUnread && !chat.muted) cs.primary else cs.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f).heightIn(min = 40.dp)) {
                    when {
                        typing != null ->
                            Text(
                                typing,
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        draft != null ->
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = cs.error)) { append("Черновик: ") }
                                    // Newlines would make the row read oddly.
                                    append(draft.replace('\n', ' ').trim())
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        else ->
                            Text(
                                buildAnnotatedString {
                                    // Who said it, in groups: "Имя: …" / "Вы: …".
                                    if (prefix != null) withStyle(SpanStyle(color = cs.onSurface)) { append("$prefix: ") }
                                    append(chat.lastMessageText?.replace('\n', ' ') ?: "")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                    }
                }
                if (hasUnread) {
                    Spacer(Modifier.width(8.dp))
                    UnreadBadge(chat.unreadCount, muted = chat.muted)
                } else if (isPinned) {
                    Spacer(Modifier.width(8.dp))
                    Icon(AppIcons.Pin, contentDescription = "Закреплён", tint = cs.outline, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** The bookmark tile that stands in for an avatar on the saved-messages chat. */
@Composable
internal fun SavedMessagesAvatar(size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(accentGradient()), contentAlignment = Alignment.Center) {
        Icon(AppIcons.Bookmark, contentDescription = null, tint = onAccentGradient(), modifier = Modifier.size(size * 0.48f))
    }
}

/** A plain avatar + name (+ subtitle) row: search hits, pickers. */
@Composable
private fun PersonRow(
    name: String,
    avatarUrl: String?,
    subtitle: String?,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(name, avatarUrl, 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/** Icon, headline and an optional hint, centered: what a tab shows when it has nothing to list. */
@Composable
private fun EmptyState(
    icon: Painter,
    title: String,
    hint: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    // Scrollable, so pull-to-refresh still works over an empty list.
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp)
            .padding(bottom = NAV_SPACE),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(cs.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** Long-press actions for a chat row, under a small header naming the chat. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatActionsSheet(
    chat: Chat,
    avatarUrl: String?,
    isPinned: Boolean,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit,
    onOpenInfo: () -> Unit,
    /** Null when the chat already has unread messages. */
    onMarkUnread: (() -> Unit)? = null,
    /** Null when the account has no folders to put it in. */
    onAddToFolder: (() -> Unit)? = null,
) {
    AppSheet(onDismiss) {
        Row(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (chat.isSavedMessages) SavedMessagesAvatar(48.dp) else Avatar(chat.title, avatarUrl, 48.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(chat.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        chat.isSavedMessages -> "Ваши заметки и пересланное"
                        chat.isDialog -> "Личный чат"
                        chat.participantsCount > 0 -> participantsLabel(chat.participantsCount, chat.isChannel)
                        chat.isChannel -> "Канал"
                        else -> "Группа"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SheetAction(if (isPinned) AppIcons.Unpin else AppIcons.Pin, if (isPinned) "Открепить" else "Закрепить", onClick = onTogglePin)
        SheetAction(
            if (chat.muted) AppIcons.Unmuted else AppIcons.Muted,
            if (chat.muted) "Включить уведомления" else "Отключить уведомления",
            onClick = onToggleMute,
        )
        onMarkUnread?.let { SheetAction(AppIcons.MarkUnread, "Отметить непрочитанным", onClick = it) }
        onAddToFolder?.let { SheetAction(AppIcons.Folder, "Добавить в папку", onClick = it) }
        if (!chat.isSavedMessages) {
            SheetAction(
                if (chat.isDialog) AppIcons.Person else AppIcons.Info,
                if (chat.isDialog) {
                    "Открыть профиль"
                } else if (chat.isChannel) {
                    "О канале"
                } else {
                    "О группе"
                },
                onClick = onOpenInfo,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** Client-side folders over the loaded chat list, like a messenger's chat tabs. */
private enum class ChatFilter(
    val label: String,
    val matches: (Chat) -> Boolean,
) {
    ALL("Все", { true }),
    UNREAD("Новые", { it.unreadCount > 0 }),
    PERSONAL("Личные", { it.isDialog }),
    GROUPS("Группы", { !it.isDialog && !it.isChannel }),
    CHANNELS("Каналы", { it.isChannel }),
}

/** One chip over the chat list: a built-in filter, or a server [folder]. */
private class ChatChip(
    val key: String,
    val label: String,
    val folder: ChatFolder? = null,
    val matches: (Chat) -> Boolean,
)

/**
 * The server's folders in its [order] after «Все» (and «Новые», unless a folder already is just that); the
 * built-in set without them. A system folder the order lists with no folder object shows as our own chip
 * when its id is known to mean one: only «Каналы» is.
 */
private fun chatChips(
    folders: List<ChatFolder>,
    order: List<String>,
    myId: Long,
): List<ChatChip> {
    val local = { f: ChatFilter -> ChatChip(f.name, f.label, matches = f.matches) }
    val shown = folders.filterNot { it.isAll }
    if (shown.isEmpty()) return ChatFilter.entries.map(local)
    val byId = shown.associateBy { it.id }
    val tabs =
        (order + shown.map { it.id }).distinct().mapNotNull { id ->
            byId[id]?.let { f -> ChatChip(f.id, f.title, f) { f.matches(it, myId) } }
                ?: local(ChatFilter.CHANNELS).takeIf { id == ChatFolder.CHANNELS_ID }
        }
    val unread = ChatFilter.UNREAD.takeIf { shown.none { it.filterIds == listOf(0) } }
    return listOfNotNull(ChatFilter.ALL, unread).map(local) + tabs
}

/** Folder chips: the active one is filled with the accent; a counter shows chats with unread (muted aside). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatFilterRow(
    chats: List<Chat>,
    chips: List<ChatChip>,
    selected: ChatChip,
    onSelect: (ChatChip) -> Unit,
    onLongPress: (ChatChip) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    // The picked chip scrolls fully into view with the row's edge margin, never cut by the edge.
    val edge = with(LocalDensity.current) { 16.dp.toPx() }
    Row(
        Modifier
            .fillMaxWidth()
            .fadingEndScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { f ->
            val active = f.key == selected.key
            // "Новые" is itself the unread view, so a counter there would only repeat the folder.
            val unread =
                if (f.key == ChatFilter.ALL.name ||
                    f.key == ChatFilter.UNREAD.name
                ) {
                    0
                } else {
                    chats.count { f.matches(it) && it.unreadCount > 0 && !it.muted }
                }
            val bg by animateColorAsState(if (active) cs.primary else cs.surfaceContainerHigh, label = "chipBg")
            val fg by animateColorAsState(if (active) cs.onPrimary else cs.onSurfaceVariant, label = "chipFg")
            val bring = remember { BringIntoViewRequester() }
            var chipWidth by remember { mutableIntStateOf(0) }
            LaunchedEffect(active, chipWidth) { if (active && chipWidth > 0) bring.bringIntoView(Rect(-edge, 0f, chipWidth + edge, 1f)) }
            Row(
                Modifier
                    .bringIntoViewRequester(bring)
                    .onSizeChanged { chipWidth = it.width }
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .combinedClickable(role = Role.Tab, onLongClick = { onLongPress(f) }, onClick = { onSelect(f) })
                    .semantics { this.selected = active }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(f.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = fg)
                if (unread > 0) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                            .clip(CircleShape)
                            .background(if (active) cs.onPrimary.copy(alpha = 0.24f) else cs.primary)
                            .padding(horizontal = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (unread > 99) "99+" else "$unread",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = cs.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactsTab(
    contacts: List<UserInfo>,
    online: Set<Long>,
    presence: Map<Long, Presence>,
    onOpenUser: (Long) -> Unit,
) {
    if (contacts.isEmpty()) {
        EmptyState(AppIcons.Contacts, "Контактов нет", "Здесь появятся люди, с которыми вы переписываетесь")
        return
    }
    var query by remember { mutableStateOf("") }
    val cs = MaterialTheme.colorScheme
    // Alphabetical, with a letter heading at each change of initial.
    val shown =
        remember(contacts, query) {
            contacts.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }.sortedBy { it.name.lowercase() }
        }
    val now = nowMillis()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = navClearance()) {
        item(key = "search") {
            PillTextField(
                query,
                { query = it },
                "Поиск контактов",
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
            )
        }
        shown.forEachIndexed { index, c ->
            val letter = c.name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '#'
            val prev =
                shown
                    .getOrNull(index - 1)
                    ?.name
                    ?.firstOrNull { it.isLetterOrDigit() }
                    ?.uppercaseChar() ?: '#'
            if (index == 0 || letter != prev) {
                item(key = "h$letter") {
                    Text(
                        letter.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = cs.primary,
                        modifier = Modifier.padding(start = 24.dp, top = 10.dp, bottom = 2.dp),
                    )
                }
            }
            item(key = c.id) {
                val isOnline = c.id in online
                val status =
                    if (isOnline) {
                        "в сети"
                    } else {
                        presence[c.id]?.let { lastSeenText(it, now) }
                            ?: c.description?.takeIf { it.isNotBlank() }
                    }
                Row(
                    Modifier.fillMaxWidth().clickableRow { onOpenUser(c.id) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(c.name, c.avatarUrl, 48.dp, online = isOnline)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        status?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOnline) cs.primary else cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item(key = "none") {
                Text(
                    "Никого не нашлось",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SettingsTab(
    account: Account?,
    theme: ThemeMode,
    accent: Accent,
    uiScale: Float,
    fontScale: Float,
    onSetAccent: (Accent) -> Unit,
    onSetUiScale: (Float) -> Unit,
    onSetFontScale: (Float) -> Unit,
    wallpaper: Int = 0,
    onSetWallpaper: (Int) -> Unit = {},
    demoMode: Boolean,
    privacy: Privacy?,
    onUpdatePrivacy: (Privacy) -> Unit,
    dataCacheBytes: Long,
    imageCacheBytes: Long,
    onSetTheme: (ThemeMode) -> Unit,
    onConfirmWebLogin: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenAbout: () -> Unit,
    onClearCache: () -> Unit,
    onRefreshCacheSize: () -> Unit,
    onLogout: () -> Unit,
    /** Login password (2FA) state for the "Пароль для входа" row; null = unknown yet. */
    twoFaEnabled: Boolean? = null,
    onOpenTwoFa: () -> Unit = {},
    sendOnEnter: Boolean = false,
    onSetSendOnEnter: (Boolean) -> Unit = {},
    quickReaction: String? = null,
    onSetQuickReaction: (String?) -> Unit = {},
    /** Emoji offered for the quick reaction (the server's menu when known). */
    reactionChoices: List<String> = emptyList(),
    /** Signed-in devices; null while loading. */
    sessions: List<SessionInfo>? = null,
    onLoadSessions: () -> Unit = {},
    onCloseOthers: () -> Unit = {},
    /** The black list; null while loading. */
    blockedUsers: List<UserInfo>? = null,
    onLoadBlocked: () -> Unit = {},
    onUnblock: (UserInfo) -> Unit = {},
    /** Server folders for Настройки → Папки. */
    folders: List<ChatFolder> = emptyList(),
    onMoveFolder: (id: String, by: Int) -> Unit = { _, _ -> },
    /** Opens the folder editor; a blank id makes a new folder. */
    onEditFolder: (ChatFolder) -> Unit = {},
) {
    var showFolders by remember { mutableStateOf(false) }
    if (showFolders) {
        FoldersSheet(
            folders = folders,
            onMove = onMoveFolder,
            onOpen = {
                showFolders = false
                onEditFolder(it)
            },
            onCreate = {
                showFolders = false
                onEditFolder(ChatFolder(id = "", title = ""))
            },
        ) { showFolders = false }
    }
    var showSessions by remember { mutableStateOf(false) }
    if (showSessions) SessionsSheet(sessions, onCloseOthers) { showSessions = false }
    var showBlocked by remember { mutableStateOf(false) }
    if (showBlocked) BlackListSheet(blockedUsers, onUnblock) { showBlocked = false }
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
                }) { Text("Выйти", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Отмена") }
            },
        )
    }
    val cacheSummary =
        when {
            imageCacheBytes <= 0 && dataCacheBytes <= 0 -> "Кэш пуст"
            imageCacheBytes <= 0 -> "Данные ${formatBytes(dataCacheBytes)}"
            // Split out, because the thumbnails are almost always the bulk of it
            // and a single total made the number look implausibly small.
            else -> "Изображения ${formatBytes(imageCacheBytes)} · данные ${formatBytes(dataCacheBytes)}"
        }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(navClearance()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Profile hero: the account on an aurora card, with its shortcuts right under it.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .aurora(1.4f)
                    .clickable(onClick = onOpenProfile)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(account?.firstName ?: "Я", account?.avatarUrl, 72.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        listOfNotNull(account?.firstName, account?.lastName).joinToString(" ").ifBlank { "Профиль" },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (demoMode) "Демо-режим" else "Мой профиль",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(AppIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(AppIcons.Bookmark, "Избранное", Modifier.weight(1f), onOpenSaved)
                QuickAction(AppIcons.Edit, "Изменить", Modifier.weight(1f), onEditProfile)
                if (!demoMode && qrScanSupported) QuickAction(AppIcons.QrScan, "Веб-вход", Modifier.weight(1f)) { scanWebLogin() }
            }
        }

        GroupCard("Оформление") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ThemePicker(theme, onSetTheme)
                AccentPicker(accent, onSetAccent)
                WallpaperPicker(wallpaper, onSetWallpaper)
                ScaleSlider(AppIcons.Zoom, "Масштаб интерфейса", uiScale, Prefs.UI_SCALE_RANGE, onSetUiScale)
                ScaleSlider(AppIcons.FontSize, "Размер шрифта", fontScale, Prefs.FONT_SCALE_RANGE, onSetFontScale)
            }
        }

        GroupCard("Безопасность") {
            // The login password (2FA), as in the official Settings → Безопасность; it needs the server.
            if (!demoMode) {
                GroupRow(
                    AppIcons.Lock,
                    tileColor(-60f),
                    "Пароль для входа",
                    "Дополнительная защита профиля",
                    value =
                        when (twoFaEnabled) {
                            true -> "Вкл."
                            false -> "Выкл."
                            null -> null
                        },
                    chevron = true,
                    onClick = onOpenTwoFa,
                )
            }
            GroupRow(
                AppIcons.Devices,
                tileColor(30f),
                "Устройства",
                "Где выполнен вход в аккаунт",
                chevron = true,
                onClick = {
                    onLoadSessions()
                    showSessions = true
                },
            )
            GroupRow(
                AppIcons.Block,
                tileColor(150f),
                "Чёрный список",
                "Кто не может вам писать, звонить и добавлять в чаты",
                chevron = true,
                onClick = {
                    onLoadBlocked()
                    showBlocked = true
                },
            )
        }

        GroupCard("Сообщения") {
            GroupRow(
                AppIcons.Folder,
                tileColor(240f),
                "Папки",
                "Вкладки над списком чатов",
                chevron = true,
                onClick = { showFolders = true },
            )
            GroupRow(
                AppIcons.Enter,
                tileColor(180f),
                "Отправка по вводу",
                "Enter отправляет сообщение",
                checked = sendOnEnter,
                onClick = { onSetSendOnEnter(!sendOnEnter) },
            )
            var pickReaction by remember { mutableStateOf(false) }
            Box {
                GroupRow(
                    AppIcons.Heart,
                    tileColor(-30f),
                    "Быстрая реакция",
                    "Двойное нажатие на сообщение",
                    value = quickReaction ?: "Выкл.",
                    onClick = { pickReaction = true },
                )
                // Anchored at the row's trailing edge, under the value it changes.
                Box(Modifier.align(Alignment.BottomEnd)) {
                    DropdownMenu(expanded = pickReaction, onDismissRequest = { pickReaction = false }) {
                        DropdownMenuItem(
                            text = { Text("Выключить") },
                            onClick = {
                                pickReaction = false
                                onSetQuickReaction(null)
                            },
                        )
                        // Four to a line: a short menu fits under the row instead of flipping up over the rows above.
                        reactionChoices.chunked(4).forEach { line ->
                            Row(Modifier.padding(horizontal = 8.dp)) {
                                line.forEach { emoji ->
                                    Text(
                                        emoji,
                                        style = MaterialTheme.typography.titleLarge,
                                        textAlign = TextAlign.Center,
                                        modifier =
                                            Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    pickReaction = false
                                                    onSetQuickReaction(emoji)
                                                }.wrapContentHeight(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (privacy != null) {
            GroupCard("Конфиденциальность") {
                // Finding by phone takes no "nobody" on the server.
                PrivacyChoiceRow(AppIcons.Search, 0f, "Кто может найти меня по номеру", privacy.searchByPhone, nobody = false) {
                    onUpdatePrivacy(privacy.copy(searchByPhone = it))
                }
                PrivacyChoiceRow(AppIcons.Call, 120f, "Кто может мне звонить", privacy.incomingCall) {
                    onUpdatePrivacy(privacy.copy(incomingCall = it))
                }
                PrivacyChoiceRow(AppIcons.Contacts, 240f, "Кто может приглашать в чаты", privacy.chatsInvite) {
                    onUpdatePrivacy(privacy.copy(chatsInvite = it))
                }
                GroupRow(
                    AppIcons.HideOnline,
                    tileColor(-90f),
                    "Скрывать статус «в сети»",
                    checked = privacy.hideOnline,
                    onClick = { onUpdatePrivacy(privacy.copy(hideOnline = !privacy.hideOnline)) },
                )
                GroupRow(
                    AppIcons.Read,
                    tileColor(90f),
                    "Отметки о прочтении",
                    "Собеседники видят, что вы прочитали сообщение",
                    checked = privacy.showReadMarks,
                    onClick = { onUpdatePrivacy(privacy.copy(showReadMarks = !privacy.showReadMarks)) },
                )
            }
        }

        GroupCard("Данные") {
            GroupRow(AppIcons.Storage, tileColor(150f), "Очистить кэш", cacheSummary, onClick = { confirmClearCache = true })
        }

        GroupCard {
            GroupRow(
                AppIcons.PersonAdd,
                tileColor(210f),
                "Пригласить друзей",
                "Поделиться ссылкой на Авенариус",
                onClick = { shareText("Я на связи в MAX через Авенариус — открытый клиент без слежки: ${AppLinks.GITHUB}") },
            )
            GroupRow(AppIcons.Info, tileColor(60f), "О программе", chevron = true, onClick = onOpenAbout)
            GroupRow(
                AppIcons.Logout,
                MaterialTheme.colorScheme.error,
                "Выйти из аккаунта",
                danger = true,
                onClick = { confirmLogout = true },
            )
        }
    }
}

/** Settings → Устройства: every device signed in to the account. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionsSheet(
    sessions: List<SessionInfo>?,
    onCloseOthers: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var confirm by remember { mutableStateOf(false) }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Завершить другие сеансы?") },
            text = {
                Text(
                    "На всех остальных устройствах будет выполнен выход из аккаунта, включая телефон " +
                        "с официальным приложением MAX. Войти там снова можно будет только заново, по коду. " +
                        "Этот сеанс останется.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onCloseOthers()
                }) { Text("Завершить", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
        )
    }
    AppSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text("Устройства", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Здесь выполнен вход в ваш аккаунт.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            when {
                sessions == null ->
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    }
                sessions.isEmpty() ->
                    Text("Список устройств недоступен", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                else ->
                    LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sessions) { session ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(cs.surfaceContainerHigh)
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(
                                            40.dp,
                                        ).clip(
                                            RoundedCornerShape(12.dp),
                                        ).background(if (session.current) cs.primary else cs.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        AppIcons.Devices,
                                        contentDescription = null,
                                        tint = if (session.current) cs.onPrimary else cs.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        session.client.ifBlank { "Устройство" } + if (session.current) " · это устройство" else "",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (session.current) cs.primary else cs.onSurface,
                                    )
                                    if (session.info.isNotBlank()) Text(session.info, style = MaterialTheme.typography.bodySmall)
                                    val seen = session.time.takeIf { it > 0 }?.let { "${formatDate(it)} ${formatClock(it)}" }
                                    listOfNotNull(
                                        session.location.ifBlank { null },
                                        seen,
                                    ).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
            }
            if (sessions?.any { !it.current } == true) {
                Text(
                    "Завершить все сессии, кроме текущей",
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.error,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { confirm = true }
                            .padding(vertical = 14.dp, horizontal = 4.dp),
                )
            }
        }
    }
}

/** Settings → Безопасность → Чёрный список: everyone we blocked, each with «Разблокировать». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlackListSheet(
    users: List<UserInfo>?,
    onUnblock: (UserInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    AppSheet(onDismiss) {
        Column(Modifier.padding(bottom = 20.dp)) {
            Text("Чёрный список", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
            Text(
                "Список тех, кто не может вам писать, звонить и добавлять в чаты",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
            )
            when {
                users == null ->
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    }
                users.isEmpty() ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(72.dp).clip(CircleShape).background(cs.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(AppIcons.Block, contentDescription = null, tint = cs.primary, modifier = Modifier.size(34.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Список пуст", style = MaterialTheme.typography.titleMedium)
                    }
                else ->
                    LazyColumn(Modifier.heightIn(max = 460.dp)) {
                        items(users, key = { it.id }) { u ->
                            PersonRow(u.name, u.avatarUrl, u.phone?.let { "+$it" }) {
                                TextButton(onClick = { onUnblock(u) }) { Text("Разблокировать") }
                            }
                        }
                    }
            }
        }
    }
}

/** A privacy "who can…" row; tapping opens Все / Контакты (/ Никто). */
@Composable
private fun PrivacyChoiceRow(
    icon: Painter,
    turn: Float,
    title: String,
    value: String,
    nobody: Boolean = true,
    onChange: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        GroupRow(icon, tileColor(turn), title, value = whoLabel(value), onClick = { open = true })
        // Anchored at the row's trailing edge, under the value it changes.
        Box(Modifier.align(Alignment.BottomEnd)) {
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                listOfNotNull(Privacy.ALL, Privacy.CONTACTS, Privacy.NOBODY.takeIf { nobody }).forEach { v ->
                    DropdownMenuItem(
                        text = { Text(whoLabel(v)) },
                        onClick = {
                            open = false
                            if (v != value) onChange(v)
                        },
                    )
                }
            }
        }
    }
}

private fun whoLabel(value: String): String =
    when (value) {
        Privacy.ALL -> "Все"
        Privacy.CONTACTS -> "Контакты"
        Privacy.NOBODY -> "Никто"
        else -> value
    }

/** Three cards, each a tiny preview of the conversation in that mode. */
@Composable
private fun ThemePicker(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemeCard("Авто", AppIcons.AutoMode, ThemeMode.SYSTEM, current, onSelect)
        ThemeCard("Тёмная", AppIcons.DarkMode, ThemeMode.DARK, current, onSelect)
        ThemeCard("Светлая", AppIcons.LightMode, ThemeMode.LIGHT, current, onSelect)
    }
}

private val PreviewDark = Color(0xFF14161A)
private val PreviewLight = Color(0xFFF2F3F6)

@Composable
private fun RowScope.ThemeCard(
    label: String,
    icon: Painter,
    mode: ThemeMode,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val selected = mode == current
    val border by animateColorAsState(if (selected) cs.primary else cs.outlineVariant, label = "themeBorder")
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(18.dp))
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(18.dp))
            .selectable(selected = selected, onClick = { onSelect(mode) }, role = Role.RadioButton)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val backdrop =
            when (mode) {
                ThemeMode.DARK -> Brush.linearGradient(listOf(PreviewDark, PreviewDark))
                ThemeMode.LIGHT -> Brush.linearGradient(listOf(PreviewLight, PreviewLight))
                // Split down the middle: it follows the system either way.
                ThemeMode.SYSTEM ->
                    Brush.horizontalGradient(
                        0f to PreviewLight,
                        0.5f to PreviewLight,
                        0.5f to PreviewDark,
                        1f to PreviewDark,
                    )
            }
        Column(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(backdrop)
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier
                    .width(34.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.Gray.copy(alpha = 0.55f)),
            )
            Box(
                Modifier
                    .align(Alignment.End)
                    .width(42.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(accentGradient()),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (selected) cs.primary else cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) cs.primary else cs.onSurface,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}

/** Row of accent swatches; "Системный" (Material You) only where the platform has it. */
@Composable
private fun AccentPicker(
    current: Accent,
    onSelect: (Accent) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val dark = !isLightTheme()
    val dynamic = dynamicColorScheme(dark)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Цвет акцента", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(current.label, style = MaterialTheme.typography.bodyMedium, color = cs.primary)
        }
        // Equal slots across the width: every swatch fits (and is tappable) on a 360dp screen.
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Accent.entries.filter { it != Accent.SYSTEM || dynamic != null }.forEach { a ->
                val tones = if (dark) a.dark else a.light
                val partner = if (dark) a.partner.dark else a.partner.light
                val swatch =
                    if (a == Accent.SYSTEM) {
                        Brush.linearGradient(listOf(dynamic!!.primary, dynamic.tertiary))
                    } else {
                        Brush.linearGradient(listOf(tones.primary, partner.primary))
                    }
                val selected = a == current
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .widthIn(max = 46.dp)
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .then(if (selected) Modifier.border(2.dp, cs.primary, CircleShape) else Modifier)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .selectable(selected = selected, onClick = { onSelect(a) }, role = Role.RadioButton)
                            .semantics { contentDescription = a.label },
                        contentAlignment = Alignment.Center,
                    ) {
                        // The selected swatch is the scheme's own primary, so onPrimary contrasts with it.
                        if (selected) {
                            Icon(
                                AppIcons.Delivered,
                                contentDescription = null,
                                tint = cs.onPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** «Фон чата»: a live tile per preset, a bubble each way on it, as the conversation will look. */
@Composable
private fun WallpaperPicker(
    current: Int,
    onSelect: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Фон чата", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(WALLPAPERS.getOrElse(current) { WALLPAPERS[0] }, style = MaterialTheme.typography.bodyMedium, color = cs.primary)
        }
        Row(Modifier.fadingEndScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WALLPAPERS.forEachIndexed { i, name ->
                val selected = i == current
                Column(
                    Modifier
                        .size(width = 64.dp, height = 96.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(if (selected) 2.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant, RoundedCornerShape(14.dp))
                        .chatWallpaper(i)
                        .selectable(selected = selected, onClick = { onSelect(i) }, role = Role.RadioButton)
                        .semantics { contentDescription = name }
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
                ) {
                    Box(
                        Modifier
                            .width(34.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(incomingBubbleColor()),
                    )
                    Box(
                        Modifier
                            .align(Alignment.End)
                            .width(38.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentGradient()),
                    )
                }
            }
        }
    }
}

/** A 10%-step scale slider; applied on release, since rescaling mid-drag moves the slider under the finger. */
@Composable
private fun ScaleSlider(
    icon: Painter,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${(draft * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = draft,
            onValueChange = { draft = (it * 10).roundToInt() / 10f },
            valueRange = range,
            steps = ((range.endInclusive - range.start) * 10).roundToInt() - 1,
            onValueChangeFinished = { onChange(draft) },
        )
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
    val cs = MaterialTheme.colorScheme
    // Muted chats get a grey badge so they don't compete with the ones that ring.
    val base = Modifier.defaultMinSize(minWidth = 22.dp, minHeight = 22.dp).clip(CircleShape)
    Box(
        (if (muted) base.background(cs.surfaceContainerHighest) else base.background(accentGradient())).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 999) "999+" else "$count",
            color = if (muted) cs.onSurfaceVariant else onAccentGradient(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * A quiet, dismissable offer to rate the app, shown in the list rather than as a dialog.
 * Same offer for everyone who qualifies (Play policy: no asking whether they like it first,
 * no asking for a particular rating).
 */
@Composable
private fun RatePromptCard(
    onRate: () -> Unit,
    onLater: () -> Unit,
    onNever: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 18.dp, end = 10.dp, top = 16.dp, bottom = 6.dp),
    ) {
        Text("Оцените Авенариус", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Оценка в Google Play помогает другим людям найти приложение. Это займёт пару секунд.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onNever) { Text("Не показывать") }
            TextButton(onClick = onLater) { Text("Позже") }
            TextButton(onClick = onRate) { Text("Оценить") }
        }
    }
}

/** A two-way pill switch ("Чат | Группа"). */
@Composable
private fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(cs.surfaceContainerHighest)
            .padding(4.dp)
            .selectableGroup(),
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            val bg by animateColorAsState(if (active) cs.primary else Color.Transparent, label = "segBg")
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(bg)
                    .selectable(selected = active, onClick = { onSelect(i) }, role = Role.Tab)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) cs.onPrimary else cs.onSurfaceVariant,
                )
            }
        }
    }
}

/** "New chat / new group", as a sheet rising from the compose button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewChatSheet(
    searchResults: List<SearchResult>,
    searching: Boolean,
    contacts: List<UserInfo>,
    myId: Long,
    onSearch: (String) -> Unit,
    onPickResult: (SearchResult) -> Unit,
    onPhone: (String) -> Unit,
    onCreateGroup: (name: String, memberIds: List<Long>, phones: List<String>, avatar: PickedMedia?) -> Unit,
    onCreateChannel: (name: String, description: String, avatar: PickedMedia?) -> Unit,
    onLoadDeviceContacts: (List<DeviceContact>) -> Unit,
    onDismiss: () -> Unit,
) {
    // Load the device address book (requests READ_CONTACTS) and feed it to the VM
    // so it can rank book contacts into the search results.
    val deviceContacts = rememberDeviceContacts()
    LaunchedEffect(deviceContacts) { onLoadDeviceContacts(deviceContacts) }
    var tab by remember { mutableStateOf(0) } // 0 = new chat, 1 = new group, 2 = new channel
    // --- New chat state ---
    var query by remember { mutableStateOf("") }
    val looksLikePhone = query.isNotBlank() && query.all { it.isDigit() || it == '+' || it == ' ' }
    LaunchedEffect(query, tab) {
        if (tab == 0 && !looksLikePhone && query.trim().length >= 2) {
            delay(350) // let typing settle before hitting the network
            onSearch(query)
        } else {
            onSearch("")
        }
    }
    // --- New group state ---
    var groupName by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf<PickedMedia?>(null) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var selectedPhones by remember { mutableStateOf(setOf<String>()) }
    val pickAvatar = rememberSingleImagePickLauncher { avatar = it }
    // --- New channel state (the photo is shared with the group tab) ---
    var channelName by remember { mutableStateOf("") }
    var channelAbout by remember { mutableStateOf("") }

    AppSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                when (tab) {
                    0 -> "Новый чат"
                    1 -> "Новая группа"
                    else -> "Каким будет новый канал?"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Segmented(listOf("Чат", "Группа", "Канал"), tab) { tab = it }
            if (tab == 0) {
                NewChatTab(query, searching, searchResults, looksLikePhone, { query = it }, onPickResult)
                if (looksLikePhone) {
                    Button(onClick = { onPhone(query) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Открыть по номеру") }
                }
            } else if (tab == 2) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhotoPicker(avatar, null, channelName, 64.dp, pickAvatar)
                    Text(
                        "Добавьте фото, чтобы канал узнавали с первого взгляда",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PillTextField(
                    channelName,
                    { channelName = it },
                    "Название канала",
                    Modifier.fillMaxWidth(),
                    leading = null,
                    capitalize = true,
                )
                OutlinedTextField(
                    value = channelAbout,
                    onValueChange = { channelAbout = it.take(CHAT_DESCRIPTION_MAX) },
                    label = { Text("Описание") },
                    supportingText = { Text("${channelAbout.length} / $CHAT_DESCRIPTION_MAX") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onCreateChannel(channelName, channelAbout, avatar) },
                    enabled = channelName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Создать канал") }
            } else {
                NewGroupTab(
                    groupName = groupName,
                    onName = { groupName = it },
                    avatar = avatar,
                    onPickAvatar = pickAvatar,
                    contacts = contacts.filter { it.id != myId },
                    deviceContacts = deviceContacts,
                    selectedIds = selected,
                    selectedPhones = selectedPhones,
                    onToggleId = { id, on -> selected = if (on) selected + id else selected - id },
                    onTogglePhone = { p, on -> selectedPhones = if (on) selectedPhones + p else selectedPhones - p },
                )
                Button(
                    onClick = { onCreateGroup(groupName, selected.toList(), selectedPhones.toList(), avatar) },
                    enabled = groupName.isNotBlank() && (selected.isNotEmpty() || selectedPhones.isNotEmpty()),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Создать группу") }
            }
        }
    }
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
        PillTextField(query, onQuery, "Имя или номер телефона", Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        if (!looksLikePhone) {
            if (searching) {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
            if (searchResults.isEmpty() && !searching) {
                Text(
                    if (query.trim().length <
                        2
                    ) {
                        "Введите имя, чтобы найти человека или канал, либо номер телефона"
                    } else {
                        "Никого не нашлось"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                )
            }
            LazyColumn(Modifier.heightIn(max = 340.dp)) {
                items(searchResults, key = { it.chatId }) { result ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(16.dp),
                            ).clickableRow { onPickResult(result) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(result.title, result.avatarUrl, 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(result.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
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
    avatar: PickedMedia?,
    onPickAvatar: () -> Unit,
    contacts: List<UserInfo>,
    deviceContacts: List<DeviceContact>,
    selectedIds: Set<Long>,
    selectedPhones: Set<String>,
    onToggleId: (Long, Boolean) -> Unit,
    onTogglePhone: (String, Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var filter by remember { mutableStateOf("") }
    val serverNames = remember(contacts) { contacts.map { it.name.lowercase() }.toSet() }
    val book =
        remember(deviceContacts, serverNames) { deviceContacts.filter { it.name.lowercase() !in serverNames }.distinctBy { it.phone } }
    val q = filter.trim()
    val shownServer = contacts.filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }
    val shownBook = book.filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(cs.surfaceContainerHighest)
                    .clickable(onClick = onPickAvatar),
                contentAlignment = Alignment.Center,
            ) {
                if (avatar != null) {
                    AsyncImage(
                        model = avatar.content.previewModel,
                        contentDescription = "Фото группы",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(AppIcons.Camera, contentDescription = "Добавить фото", tint = cs.primary, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            PillTextField(groupName, onName, "Название группы", Modifier.weight(1f), leading = null, capitalize = true)
        }
        PillTextField(filter, { filter = it }, "Участники: ${selectedIds.size + selectedPhones.size} · поиск", Modifier.fillMaxWidth())
        LazyColumn(Modifier.heightIn(max = 260.dp)) {
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
internal fun PickPersonRow(
    name: String,
    avatarUrl: String?,
    subtitle: String?,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(16.dp),
            ).toggleable(value = checked, onValueChange = onToggle)
            .padding(vertical = 7.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name, avatarUrl, 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        // A round tick, like the selection marks in messengers' pickers.
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
