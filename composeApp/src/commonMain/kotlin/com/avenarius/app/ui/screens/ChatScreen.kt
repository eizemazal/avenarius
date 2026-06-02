package com.avenarius.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.avenarius.app.model.Chat
import com.avenarius.app.model.MediaAttach
import com.avenarius.app.model.MediaType
import com.avenarius.app.model.Message
import com.avenarius.app.model.MessageStatus
import com.avenarius.app.ui.AppIcons
import com.avenarius.app.ui.MediaViewer
import com.avenarius.app.ui.PlatformBackHandler
import com.avenarius.app.ui.VideoPlayer
import com.avenarius.app.ui.components.Avatar
import com.avenarius.app.ui.components.CenteredSpinner
import com.avenarius.app.ui.components.LinkedText
import com.avenarius.app.ui.formatClock
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatScreen(
    chat: Chat?,
    messages: List<Message>,
    myId: Long,
    contacts: Map<Long, String>,
    senderAvatars: Map<Long, String?>,
    unreadAtOpen: Int,
    busy: Boolean,
    error: String?,
    loadingOlder: Boolean,
    replyingTo: Message?,
    onLoadOlder: () -> Unit,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onMediaClick: (MediaAttach, String?) -> Unit,
    onOpenUser: (Long) -> Unit,
    onReact: (Message, String) -> Unit,
    onReply: (Message) -> Unit,
    onCancelReply: () -> Unit,
    onDeleteChat: () -> Unit,
    onLeaveGroup: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isDialog = chat?.isDialog ?: true
    // For a 1:1 dialog the other user's id is chatId XOR myId.
    val otherUserId = if (isDialog && chat != null && myId >= 0) chat.id xor myId else null
    // The message whose context menu is open (overlay shown above the conversation).
    var menuTarget by remember(chat?.id) { mutableStateOf<Message?>(null) }

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

    var menuOpen by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf(false) }
    // Heading avatar: a 1:1 dialog uses the contact's photo; a group uses its own.
    val headingAvatar = if (isDialog) otherUserId?.let { senderAvatars[it] } else chat?.avatarUrl
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(
                            chat?.title ?: "Чат",
                            headingAvatar,
                            36.dp,
                            // 1:1 → open the contact's profile; group details come later.
                            onClick = otherUserId?.let { id -> { onOpenUser(id) } },
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(chat?.title ?: "Чат", maxLines = 1, style = MaterialTheme.typography.titleMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "Назад") }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(AppIcons.More, contentDescription = "Меню")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (isDialog) "Удалить чат" else "Выйти из группы") },
                            onClick = {
                                menuOpen = false
                                confirmAction = true
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth()) {
                if (replyingTo != null) ReplyBanner(replyingTo, contacts, myId, onCancelReply)
                // Telegram-style rounded input pill with the send button tucked inside.
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                ) {
                    val canSend = draft.isNotBlank()
                    Row(
                        Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                            textStyle =
                                MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            maxLines = 5,
                            decorationBox = { inner ->
                                if (draft.isEmpty()) {
                                    Text(
                                        "Сообщение",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                inner()
                            },
                        )
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (canSend) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                    },
                                ).then(
                                    if (canSend) {
                                        Modifier.clickable {
                                            onSend(draft)
                                            draft = ""
                                        }
                                    } else {
                                        Modifier
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                AppIcons.Send,
                                contentDescription = "Отправить",
                                tint =
                                    if (canSend) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
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
                        replyAuthor =
                            msg.replyTo?.let { if (it.senderId == myId) "Вы" else contacts[it.senderId] ?: "—" },
                        onMediaClick = onMediaClick,
                        onAvatarClick = { onOpenUser(msg.senderId) },
                        onClick = { menuTarget = msg },
                        onSwipeReply = { onReply(msg) },
                        onReactionClick = { emoji -> onReact(msg, emoji) },
                    )
                }
            }
        }
    }

    // Tap-to-open context menu, floating above the conversation.
    menuTarget?.let { target ->
        MessageContextMenu(
            message = target,
            onDismiss = { menuTarget = null },
            onReact = { emoji ->
                onReact(target, emoji)
                menuTarget = null
            },
            onReply = {
                onReply(target)
                menuTarget = null
            },
        )
    }

    // Confirmation for the destructive top-right menu action.
    if (confirmAction) {
        val isGroup = !isDialog
        AlertDialog(
            onDismissRequest = { confirmAction = false },
            title = { Text(if (isGroup) "Выйти из группы?" else "Удалить чат?") },
            text = { Text(if (isGroup) "Вы покинете группу «${chat?.title ?: ""}»." else "Чат будет удалён без возможности отмены.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmAction = false
                    if (isGroup) onLeaveGroup() else onDeleteChat()
                }) { Text(if (isGroup) "Выйти" else "Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmAction = false }) { Text("Отмена") } },
        )
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
    replyAuthor: String?,
    onMediaClick: (MediaAttach, String?) -> Unit,
    onAvatarClick: () -> Unit,
    onClick: () -> Unit,
    onSwipeReply: () -> Unit,
    onReactionClick: (String) -> Unit,
) {
    // Swipe-to-reply: drag the row left; past the threshold a reply icon is revealed
    // (with a haptic tick) and releasing there starts a reply to this message.
    val scope = rememberCoroutineScope()
    val rowKey = msg.id ?: msg.cid ?: msg.time
    val offsetX = remember(rowKey) { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val triggerPx = with(density) { 56.dp.toPx() }
    val maxPx = with(density) { 88.dp.toPx() }
    var triggered by remember(rowKey) { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        val progress = (-offsetX.value / triggerPx).coerceIn(0f, 1f)
        Icon(
            AppIcons.Reply,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 20.dp)
                    .size(22.dp)
                    .graphicsLayer {
                        alpha = progress
                        scaleX = progress
                        scaleY = progress
                    },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(rowKey) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX.value <= -triggerPx) onSwipeReply()
                            triggered = false
                            scope.launch { offsetX.animateTo(0f) }
                        },
                        onDragCancel = {
                            triggered = false
                            scope.launch { offsetX.animateTo(0f) }
                        },
                    ) { _, dragAmount ->
                        val target = (offsetX.value + dragAmount).coerceIn(-maxPx, 0f)
                        if (!triggered && target <= -triggerPx) {
                            triggered = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (triggered && target > -triggerPx) {
                            triggered = false
                        }
                        scope.launch { offsetX.snapTo(target) }
                    }
                },
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
            Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                Box(
                    Modifier
                        .widthIn(max = 280.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(bg)
                        .clickable(onClick = onClick)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Column {
                        if (isGroup && !isMine && startsRun) {
                            Text(senderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        msg.replyTo?.let { reply ->
                            ReplyQuote(replyAuthor ?: "—", reply.text, fg)
                            Spacer(Modifier.height(4.dp))
                        }
                        msg.media.forEach { media ->
                            MediaThumbnail(media, onClick = { onMediaClick(media, msg.id) })
                            Spacer(Modifier.height(4.dp))
                        }
                        if (msg.text.isNotEmpty()) {
                            LinkedText(msg.text, MaterialTheme.typography.bodyLarge, fg)
                        }
                        // Footer line inside the bubble: reactions then time + delivery
                        // receipt. The row is sized to its content (not the full bubble
                        // width) so a short message keeps a snug bubble instead of being
                        // stretched out by the meta row.
                        Spacer(Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (msg.reactions.isNotEmpty()) {
                                ReactionChips(msg.reactions, fg, onReactionClick)
                            }
                            MessageMeta(msg, isMine, fg)
                        }
                    }
                }
            }
        }
    }
}

/** Quoted-message block shown at the top of a reply bubble (author + preview). */
@Composable
private fun ReplyQuote(
    author: String,
    text: String,
    fg: Color,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(fg.copy(alpha = 0.12f))
            .height(IntrinsicSize.Min)
            .padding(end = 8.dp),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(fg.copy(alpha = 0.6f)))
        Column(Modifier.padding(start = 6.dp, top = 3.dp, bottom = 3.dp)) {
            Text(author, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = fg.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Time + delivery receipt, shown at the trailing edge of a message bubble. */
@Composable
private fun MessageMeta(
    msg: Message,
    isMine: Boolean,
    fg: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(formatClock(msg.time), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f))
        if (isMine) {
            val read = msg.status == MessageStatus.READ
            Icon(
                if (read) AppIcons.Read else AppIcons.Delivered,
                contentDescription = if (read) "Прочитано" else "Доставлено",
                tint = if (read) Color(0xFF8AB4F8) else fg.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * Row of tappable reaction chips, shown inside the message bubble. Chip
 * backgrounds are derived from the bubble's foreground color [fg] so they read
 * on either bubble color; our own reaction is denser.
 */
@Composable
private fun ReactionChips(
    reactions: List<com.avenarius.app.model.Reaction>,
    fg: Color,
    onClick: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        reactions.forEach { r ->
            val chipBg = if (r.mine) fg.copy(alpha = 0.30f) else fg.copy(alpha = 0.12f)
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(chipBg)
                    .clickable { onClick(r.emoji) }
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(r.emoji, style = MaterialTheme.typography.labelMedium)
                if (r.count > 1) Text("${r.count}", style = MaterialTheme.typography.labelMedium, color = fg)
            }
        }
    }
}

/** Banner above the input showing which message is being replied to. */

@Composable
private fun ReplyBanner(
    msg: Message,
    contacts: Map<Long, String>,
    myId: Long,
    onCancel: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(3.dp).height(34.dp).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (msg.senderId == myId) "Вы" else contacts[msg.senderId] ?: "Сообщение",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    msg.text.ifBlank { "Вложение" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onCancel) { Icon(AppIcons.Close, contentDescription = "Отменить") }
        }
    }
}

private val QuickReactions = listOf("❤️", "🥰", "😱", "🤣", "😄", "👍", "😘")

private val MoreReactions = listOf("🔥", "👏", "😢", "🙏", "💯", "🎉", "😡", "🤔")

/**
 * Floating context menu shown when a message is tapped: a (expandable) reactions
 * bar on top, then Reply / Copy. Tapping the dimmed backdrop dismisses it.
 */

@Composable
private fun MessageContextMenu(
    message: Message,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var expanded by remember { mutableStateOf(false) }
    PlatformBackHandler(enabled = true, onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Reactions bar (expandable).
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        QuickReactions.forEach { emoji ->
                            Text(emoji, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clickable { onReact(emoji) })
                        }
                        IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (expanded) AppIcons.Collapse else AppIcons.Expand,
                                contentDescription = if (expanded) "Свернуть" else "Больше реакций",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (expanded) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MoreReactions.forEach { emoji ->
                                Text(
                                    emoji,
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.clickable { onReact(emoji) },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // Action items.
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.widthIn(min = 220.dp)) {
                    ContextMenuItem(AppIcons.Reply, "Ответить", onClick = onReply)
                    if (message.text.isNotBlank()) {
                        ContextMenuItem(AppIcons.Copy, "Копировать") {
                            clipboard.setText(AnnotatedString(message.text))
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextMenuItem(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
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
                Icon(AppIcons.Play, contentDescription = "Воспроизвести", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
internal fun MediaViewerOverlay(
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
            Icon(AppIcons.Close, contentDescription = "Закрыть", tint = Color.White)
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
