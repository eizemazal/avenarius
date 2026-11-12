package com.avenarius.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.avenarius.app.model.FileAttach
import com.avenarius.app.model.MediaAttach
import com.avenarius.app.model.MediaType
import com.avenarius.app.model.Message
import com.avenarius.app.ui.AppIcons
import com.avenarius.app.ui.AppState
import com.avenarius.app.ui.AppViewModel
import com.avenarius.app.ui.MediaKind
import com.avenarius.app.ui.components.AppSheet
import com.avenarius.app.ui.components.CircleIconButton
import com.avenarius.app.ui.components.GroupCard
import com.avenarius.app.ui.components.LinkedText
import com.avenarius.app.ui.components.QuickAction
import com.avenarius.app.ui.components.SheetAction
import com.avenarius.app.ui.components.avatarColor
import com.avenarius.app.ui.components.fadingEndScroll
import com.avenarius.app.ui.components.groupRowDivider
import com.avenarius.app.ui.components.initials
import com.avenarius.app.ui.components.openUriSafely
import com.avenarius.app.ui.formatDate
import com.avenarius.app.ui.theme.Radius
import com.avenarius.app.ui.theme.Spacing
import com.avenarius.app.ui.theme.lightStatusBarIcons
import kotlinx.coroutines.flow.distinctUntilChanged

/** «Стереть переписку?»: shared by the chat's ⋮ menu and the info pages. */
@Composable
internal fun ClearHistoryDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Стереть переписку?") },
        text = { Text("Все сообщения этого чата будут удалены у вас. Сам чат останется в списке.") },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text("Стереть", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** A tile under the info page's hero, or an entry of its ⋮ / «Ещё» sheet. */
internal class InfoAction(
    val icon: Painter,
    val label: String,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

/** A fact in the info card: [value] on top, its [label] under it; tapping copies it. [rich] text has tappable links. */
internal class InfoFact(
    val value: String,
    val label: String,
    val rich: Boolean = false,
)

/** A tab ahead of the attachment tabs (a group's members), drawn by [content]. */
internal class InfoTab(
    val label: String,
    val content: LazyListScope.() -> Unit,
)

/**
 * The info page of a user, group or channel: a full-bleed photo hero with the name over it,
 * the action tiles under it, a card of facts, then tabs (an optional [extraTab] and the
 * chat's Медиа / Файлы / Ссылки / Голосовые from [chatId]) that stick under a compact
 * bar once the hero has scrolled away.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun InfoPage(
    state: AppState,
    vm: AppViewModel,
    chatId: Long,
    title: String,
    avatarUrl: String?,
    status: String?,
    onBack: () -> Unit,
    actions: List<InfoAction>,
    menu: List<InfoAction>,
    facts: List<InfoFact>,
    official: Boolean = false,
    online: Boolean = false,
    /** Also offer the [menu] as a last «Ещё» tile. */
    moreTile: Boolean = false,
    extraTab: InfoTab? = null,
) {
    val cs = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    val labels = listOfNotNull(extraTab?.label) + MediaKind.entries.map { it.label }
    var tab by rememberSaveable(chatId) { mutableIntStateOf(0) }
    val kind = MediaKind.entries.getOrNull(if (extraTab != null) tab - 1 else tab)
    LaunchedEffect(chatId, kind) { kind?.let { vm.loadChatMedia(chatId, it) } }
    val media = state.infoMedia?.takeIf { it.chatId == chatId && it.kind == kind }
    val listState = rememberLazyListState()
    // The tab row is item 2 (after the hero and the facts card): once it is the top item, it is stuck.
    val stuck by remember { derivedStateOf { listState.firstVisibleItemIndex >= 2 } }
    // The hero's name has reached the top: a compact bar covers it, so it never runs under the status bar
    // (the tabs may never stick when the page is short).
    val density = LocalDensity.current
    val heroGoneAt = WindowInsets.statusBars.getTop(density) + with(density) { 200.dp.toPx() }
    val heroGone by remember(heroGoneAt) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.layoutInfo.visibleItemsInfo
                    .firstOrNull()
                    ?.let { it.offset + it.size < heroGoneAt } == true
        }
    }
    // The hero is a dark photo or colour under the status bar: light icons over it in every theme.
    DisposableEffect(heroGone) {
        lightStatusBarIcons.value = !heroGone
        onDispose { lightStatusBarIcons.value = false }
    }
    // The user scrolling to the end of a tab asks for its next (older) page: one page per such
    // scroll, never on its own as the list grows or a spinner comes and goes.
    LaunchedEffect(listState, chatId, kind) {
        snapshotFlow {
            val info = listState.layoutInfo
            listState.isScrollInProgress && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 4
        }.distinctUntilChanged()
            .collect { near -> if (near && kind != null) vm.loadChatMedia(chatId, kind, more = true) }
    }
    if (menuOpen) {
        AppSheet(onDismiss = { menuOpen = false }) {
            menu.forEach { item ->
                SheetAction(item.icon, item.label, danger = item.danger) {
                    menuOpen = false
                    item.onClick()
                }
            }
            Spacer(Modifier.height(Spacing.m))
        }
    }
    val openMenu = { menuOpen = true }.takeIf { menu.isNotEmpty() }
    val tiles = if (moreTile && openMenu != null) actions + InfoAction(AppIcons.More, "Ещё", onClick = openMenu) else actions
    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxSize().background(cs.background),
            state = listState,
            contentPadding = PaddingValues(bottom = Spacing.xl + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        ) {
            item(key = "hero") {
                InfoHero(title, avatarUrl, official, status, online, onBack, openMenu, vm::openImage, tiles)
            }
            item(key = "facts") { FactsCard(facts, vm::openLink) }
            stickyHeader(key = "tabs") {
                Column(Modifier.fillMaxWidth().background(cs.background)) {
                    if (stuck) CompactBar(title, onBack)
                    TabPills(labels, tab) { tab = it }
                }
            }
            when {
                kind == null -> extraTab?.content?.invoke(this)
                media == null || (media.loading && media.items.isEmpty()) -> item(key = "loading") { TabSpinner() }
                media.items.isEmpty() -> item(key = "empty") { TabEmpty() }
                else -> {
                    mediaTab(kind, media.items, state, vm)
                    if (media.loading) item(key = "more") { TabSpinner() }
                }
            }
        }
        // The same bar the stuck tabs carry, at the same place, so the hand-over is seamless.
        if (heroGone && !stuck) CompactBar(title, onBack, Modifier.background(cs.background))
    }
}

/** Back and the title, under the status bar: the top of the info page once the hero has scrolled away. */
@Composable
private fun CompactBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = SubPageBar(title, onBack, modifier, titleStyle = MaterialTheme.typography.titleMedium)

/**
 * The top of a sub-page, under the status bar: a round tonal back button at the start and the
 * [title] centred, with optional [actions] at the end.
 */
@Composable
internal fun SubPageBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.titleLarge,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Spacing.m, vertical = Spacing.s)
            .heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircleIconButton(
            AppIcons.Back,
            "Назад",
            onBack,
            Modifier.align(Alignment.CenterStart),
            size = 48.dp,
            container = MaterialTheme.colorScheme.surfaceContainer,
        )
        Text(
            title,
            style = titleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            // Clear of the back button on both sides, so the title stays centred on the screen.
            modifier = Modifier.padding(horizontal = 48.dp + Spacing.m),
        )
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
private fun InfoHero(
    title: String,
    avatarUrl: String?,
    official: Boolean,
    status: String?,
    online: Boolean,
    onBack: () -> Unit,
    onMenu: (() -> Unit)?,
    onAvatarClick: (String) -> Unit,
    actions: List<InfoAction>,
) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Фото",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clickable { onAvatarClick(avatarUrl) },
                )
            } else {
                // No photo: the avatar's own flat colour, its initials large.
                Box(Modifier.fillMaxSize().background(avatarColor(title)).padding(bottom = 56.dp), contentAlignment = Alignment.Center) {
                    Text(initials(title), style = MaterialTheme.typography.displayLarge, color = Color.White)
                }
            }
            if (avatarUrl != null) {
                // One soft scrim under the name, eased in so it has no visible start.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.45f)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.5f to Color.Black.copy(alpha = 0.25f),
                                1f to Color.Black.copy(alpha = 0.6f),
                            ),
                        ),
                )
                // And the status bar and the top buttons on a light photo.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars.add(WindowInsets(top = 72.dp)))
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent))),
                )
            }
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.m, vertical = Spacing.s)) {
                ScrimButton(AppIcons.Back, "Назад", onBack)
                Spacer(Modifier.weight(1f))
                onMenu?.let { ScrimButton(AppIcons.More, "Ещё", it) }
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(Spacing.l)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (official) {
                        Spacer(Modifier.width(Spacing.xs))
                        Icon(AppIcons.Verified, contentDescription = "Официальный", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                if (status != null) {
                    Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (online) Color.White else Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // The tiles sit under the photo, on the page background.
        if (actions.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.l, top = Spacing.m),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                actions.forEach { a -> QuickAction(a.icon, a.label, Modifier.weight(1f), a.onClick) }
            }
        }
    }
}

/** A round dark-scrim button with a white glyph, for over the photo. */
@Composable
private fun ScrimButton(
    icon: Painter,
    description: String,
    onClick: () -> Unit,
) {
    CircleIconButton(icon, description, onClick, container = Color.Black.copy(alpha = 0.35f), tint = Color.White)
}

@Composable
private fun FactsCard(
    facts: List<InfoFact>,
    onLinkClick: (String) -> Boolean,
) {
    if (facts.isEmpty()) {
        Spacer(Modifier.height(Spacing.s))
        return
    }
    val cs = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    GroupCard(modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m).fillMaxWidth()) {
        facts.forEach { f ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .groupRowDivider(Spacing.l)
                    .clickable { clipboard.setText(AnnotatedString(f.value)) }
                    .padding(horizontal = Spacing.l, vertical = Spacing.s),
            ) {
                if (f.rich) {
                    LinkedText(f.value, MaterialTheme.typography.bodyLarge, cs.onSurface, onLinkClick)
                } else {
                    Text(f.value, style = MaterialTheme.typography.bodyLarge)
                }
                Text(f.label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TabPills(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.titleSmall
    val measurer = rememberTextMeasurer()
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s).fillMaxWidth()) {
        // Equal pills when every label fits its share of the row (xs row inset, s pill padding a side);
        // otherwise the row scrolls, its end faded.
        val widest = remember(labels, style) { labels.maxOf { measurer.measure(it, style, maxLines = 1).size.width } }
        val equal = with(LocalDensity.current) { (widest.toDp() + Spacing.s * 2) * labels.size <= maxWidth - Spacing.xs * 2 }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(cs.surfaceContainer)
                .then(if (equal) Modifier else Modifier.fadingEndScroll(scroll))
                .padding(Spacing.xs),
        ) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                Text(
                    label,
                    style = style,
                    color = if (on) cs.primary else cs.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier =
                        (if (equal) Modifier.weight(1f) else Modifier)
                            .clip(CircleShape)
                            .background(if (on) cs.surfaceContainerHighest else Color.Transparent)
                            .clickable { onSelect(i) }
                            .padding(horizontal = if (equal) Spacing.s else Spacing.l, vertical = Spacing.s),
                )
            }
        }
    }
}

@Composable
private fun TabSpinner() {
    Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
    }
}

@Composable
private fun TabEmpty() {
    Text(
        "Здесь пока ничего нет",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxl),
        textAlign = TextAlign.Center,
    )
}

/** The rows of one attachment tab. */
private fun LazyListScope.mediaTab(
    kind: MediaKind,
    messages: List<Message>,
    state: AppState,
    vm: AppViewModel,
) {
    when (kind) {
        MediaKind.MEDIA -> {
            val tiles = messages.flatMap { m -> m.media.map { m to it } }
            items(tiles.chunked(3), key = { row -> "m" + row.first().first.id + row.first().second.url }) { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    row.forEach { (m, a) -> MediaTile(a, Modifier.weight(1f)) { vm.openMedia(a, m.id) } }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        MediaKind.FILES ->
            items(messages.flatMap { m -> m.files.map { m to it } }, key = { (m, f) -> "f${m.id}:${f.fileId}" }) { (m, f) ->
                FileTabRow(
                    f,
                    m.time,
                    downloaded = f.fileId in state.downloadedFiles,
                    progress = state.downloadingFiles[f.fileId],
                ) { vm.openOrDownloadFile(m, f) }
            }
        MediaKind.LINKS -> items(messages, key = { "l${it.id}" }) { m -> LinkTabRow(m, vm::openLink) }
        MediaKind.VOICE ->
            items(messages, key = { "v${it.id}" }) { m ->
                val sender =
                    if (m.senderId == state.account?.userId) {
                        "Вы"
                    } else {
                        state.contacts[m.senderId] ?: state.groupMembers[m.senderId]?.name ?: "Собеседник"
                    }
                VoiceTabRow(m, sender, playing = state.playingVoice?.let { it.messageId == m.id && !it.paused } == true) {
                    vm.toggleVoice(m)
                }
            }
    }
}

@Composable
private fun MediaTile(
    media: MediaAttach,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick)) {
        AsyncImage(model = media.url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (media.type == MediaType.VIDEO) {
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(Spacing.xs)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = Spacing.s, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Play, contentDescription = "Видео", tint = Color.White, modifier = Modifier.size(14.dp))
                if (media.durationSec > 0) {
                    Text(formatDuration(media.durationSec), style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }
    }
}

/** A tab row: a leading tile, two lines of text, the whole row tappable. */
@Composable
private fun TabRow(
    title: String,
    subtitle: String,
    subtitleAccent: Boolean = false,
    onClick: () -> Unit,
    tile: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(Radius.small)).background(cs.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            tile()
        }
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (subtitleAccent) cs.primary else cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FileTabRow(
    file: FileAttach,
    time: Long,
    downloaded: Boolean,
    progress: Float?,
    onClick: () -> Unit,
) {
    TabRow(file.name, listOf(formatFileSize(file.size), formatDate(time)).joinToString(" · "), onClick = onClick) {
        when {
            progress != null -> CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
            downloaded -> Icon(AppIcons.Open, contentDescription = "Открыть", tint = MaterialTheme.colorScheme.primary)
            else ->
                Text(
                    file.name
                        .substringAfterLast('.', "")
                        .take(4)
                        .uppercase()
                        .ifBlank { "FILE" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
        }
    }
}

@Composable
private fun LinkTabRow(
    message: Message,
    onLinkClick: (String) -> Boolean,
) {
    val preview = message.linkPreview ?: return
    val uriHandler = LocalUriHandler.current
    val host = preview.url.substringAfter("://").substringBefore('/')
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { if (!onLinkClick(preview.url)) uriHandler.openUriSafely(preview.url) }
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                preview.title ?: host,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.s))
            Text(formatDate(message.time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            preview.url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun VoiceTabRow(
    message: Message,
    sender: String,
    playing: Boolean,
    onClick: () -> Unit,
) {
    val voice = message.voice ?: return
    TabRow("$sender · ${formatVoiceDuration(voice.durationSeconds)}", formatDate(message.time), onClick = onClick) {
        Icon(
            if (playing) AppIcons.Pause else AppIcons.Play,
            contentDescription = if (playing) "Пауза" else "Слушать",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
