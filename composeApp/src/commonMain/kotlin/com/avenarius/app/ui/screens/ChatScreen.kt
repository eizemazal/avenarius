package com.avenarius.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.avenarius.app.model.Chat
import com.avenarius.app.model.FileAttach
import com.avenarius.app.model.LinkPreview
import com.avenarius.app.model.MediaAttach
import com.avenarius.app.model.MediaType
import com.avenarius.app.model.Message
import com.avenarius.app.model.MessageStatus
import com.avenarius.app.model.PendingAttach
import com.avenarius.app.model.PickedKind
import com.avenarius.app.model.PickedMedia
import com.avenarius.app.model.RecordedVoice
import com.avenarius.app.model.ServiceEvent
import com.avenarius.app.model.UploadState
import com.avenarius.app.model.VoiceAttach
import com.avenarius.app.ui.AppIcons
import com.avenarius.app.ui.MediaViewer
import com.avenarius.app.ui.PlatformBackHandler
import com.avenarius.app.ui.PlayingVideoNote
import com.avenarius.app.ui.PlayingVoice
import com.avenarius.app.ui.VideoPlayer
import com.avenarius.app.ui.cameraCaptureSupported
import com.avenarius.app.ui.components.Avatar
import com.avenarius.app.ui.components.CenteredSpinner
import com.avenarius.app.ui.components.LinkedText
import com.avenarius.app.ui.components.openUriSafely
import com.avenarius.app.ui.formatClock
import com.avenarius.app.ui.formatDay
import com.avenarius.app.ui.rememberCameraPhotoLauncher
import com.avenarius.app.ui.rememberCameraVideoLauncher
import com.avenarius.app.ui.rememberFilePickLauncher
import com.avenarius.app.ui.rememberPhotoPickLauncher
import com.avenarius.app.ui.rememberVoiceRecorder
import com.avenarius.app.ui.voiceRecordingSupported
import kotlinx.coroutines.delay
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
    sendingAttachment: Boolean,
    stagedMedia: List<PickedMedia>,
    /** The saved unsent text for this chat, restored into the input. */
    initialDraft: String,
    onLoadOlder: () -> Unit,
    onBack: () -> Unit,
    /** Starts a call with the dialog partner ([userId], [isVideo]). */
    onStartCall: (userId: Long, isVideo: Boolean) -> Unit,
    /** Toggles muting notifications for this chat. */
    onToggleMute: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onSendMedia: (List<PickedMedia>, String) -> Unit,
    onStagedConsumed: () -> Unit,
    onMediaClick: (MediaAttach, String?) -> Unit,
    onOpenUser: (Long) -> Unit,
    onReact: (Message, String) -> Unit,
    onReply: (Message) -> Unit,
    onForward: (Message) -> Unit,
    onEditMessage: (Message, String) -> Unit,
    onDeleteMessage: (Message, Boolean) -> Unit,
    onRetrySend: (Message) -> Unit,
    onDiscardSend: (Message) -> Unit,
    /** Handles a tapped link in-app (Max links); false means "open externally". */
    onLinkClick: (String) -> Boolean,
    /** Sends a finished recording. */
    onSendVoice: (RecordedVoice) -> Unit,
    /** Sends a captured clip as a round video message. */
    onSendVideoNote: (PickedMedia) -> Unit,
    /** Starts/stops the voice message in a bubble. */
    onVoiceClick: (Message) -> Unit,
    /** Scrubs the playing voice message to a 0..1 position. */
    onVoiceSeek: (Float) -> Unit,
    /** The voice message currently loaded, if any. */
    playingVoice: PlayingVoice?,
    /** Starts/stops the round video message in a bubble. */
    onVideoNoteClick: (Message) -> Unit,
    /** The round video message playing right now, if any. */
    playingVideoNote: PlayingVideoNote?,
    onFileClick: (Message, FileAttach) -> Unit,
    /** fileIds already saved to the device (shown as "open" rather than "download"). */
    downloadedFiles: Set<Long>,
    /** fileIds being fetched right now, with how far along each is (0..1). */
    downloadingFiles: Map<Long, Float>,
    onCancelReply: () -> Unit,
    onDeleteChat: () -> Unit,
    onLeaveGroup: () -> Unit,
    onOpenGroup: () -> Unit,
) {
    var draft by remember(chat?.id) { mutableStateOf(initialDraft) }
    // What was typed before an edit took over the input, so cancelling an edit puts
    // the draft back instead of clearing it.
    var draftBeforeEdit by remember(chat?.id) { mutableStateOf("") }
    // The message currently being edited (input shows its text + an "editing" banner).
    var editing by remember(chat?.id) { mutableStateOf<Message?>(null) }
    // The message pending a delete confirmation.
    var deleteTarget by remember(chat?.id) { mutableStateOf<Message?>(null) }
    // Photos/videos/files staged for sending, with an optional caption (the draft).
    var pending by remember(chat?.id) { mutableStateOf<List<PickedMedia>>(emptyList()) }
    val pickFromGallery = rememberPhotoPickLauncher { pending = pending + it }
    val takePhoto = rememberCameraPhotoLauncher { pending = pending + it }
    val takeVideo = rememberCameraVideoLauncher { pending = pending + it }
    val recordVideoNote = rememberCameraVideoLauncher { onSendVideoNote(it) }
    val pickFile = rememberFilePickLauncher { pending = pending + it }
    var attachMenu by remember { mutableStateOf(false) }
    // Voice recording: tap the mic to start, then send or discard. Deliberately not
    // hold-to-record — a press-and-hold inside a scrolling list is the same gesture
    // fight that the voice bubble's scrubbing lost.
    var recording by remember(chat?.id) { mutableStateOf(false) }
    var recordedSeconds by remember(chat?.id) { mutableIntStateOf(0) }
    val recorder =
        rememberVoiceRecorder { recorded ->
            recording = false
            onSendVoice(recorded)
        }
    LaunchedEffect(recording) {
        recordedSeconds = 0
        while (recording) {
            delay(1000)
            recordedSeconds++
        }
    }
    // Media shared in from another app (Screen.SHARE_PICK) lands here once the chat opens.
    LaunchedEffect(stagedMedia) {
        if (stagedMedia.isNotEmpty()) {
            pending = pending + stagedMedia
            onStagedConsumed()
        }
    }
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
                    // 1:1 → open the contact's profile; group/channel → open the group page.
                    val onHeaderClick: (() -> Unit)? =
                        when {
                            !isDialog && chat != null -> onOpenGroup
                            otherUserId != null -> {
                                { onOpenUser(otherUserId) }
                            }
                            else -> null
                        }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = if (onHeaderClick != null) Modifier.clickable(onClick = onHeaderClick) else Modifier,
                    ) {
                        Avatar(chat?.title ?: "Чат", headingAvatar, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(chat?.title ?: "Чат", maxLines = 1, style = MaterialTheme.typography.titleMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "Назад") }
                },
                actions = {
                    if (isDialog && otherUserId != null && otherUserId >= 0) {
                        IconButton(onClick = { onStartCall(otherUserId, false) }) {
                            Icon(AppIcons.Call, contentDescription = "Аудиозвонок")
                        }
                        IconButton(onClick = { onStartCall(otherUserId, true) }) {
                            Icon(AppIcons.VideoCall, contentDescription = "Видеозвонок")
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(AppIcons.More, contentDescription = "Меню")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (chat?.muted == true) "Включить уведомления" else "Отключить уведомления") },
                            onClick = {
                                menuOpen = false
                                onToggleMute()
                            },
                        )
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
            // With enableEdgeToEdge + adjustResize (see AndroidManifest) the keyboard
            // arrives purely as the `ime` inset (no legacy window resize, which once
            // double-counted into a huge gap). On this device the `ime` inset is
            // measured to the top of the nav bar, i.e. it EXCLUDES the nav-bar strip —
            // so we sum the two paddings: ime + navigationBars = full keyboard height
            // while typing, and just the nav bar when the keyboard is hidden (ime = 0).
            Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
                // Read-only chats (channels we don't run): no composer, just a note.
                if (chat != null && !chat.canWrite) {
                    Text(
                        "У вас нет прав писать в этот чат",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    )
                    return@Column
                }
                if (editing != null) {
                    EditBanner(editing!!.text) {
                        editing = null
                        draft = draftBeforeEdit
                    }
                } else if (replyingTo != null) {
                    ReplyBanner(replyingTo, contacts, myId, onCancelReply)
                }
                if (pending.isNotEmpty()) {
                    StagedAttachments(pending, onRemove = { item -> pending = pending - item })
                }
                // Telegram-style rounded input pill: attach + text field + send button.
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                ) {
                    val canSend = recording || ((draft.isNotBlank() || pending.isNotEmpty()) && !sendingAttachment)
                    Row(
                        Modifier.padding(start = 4.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (recording) {
                            IconButton(
                                onClick = {
                                    recording = false
                                    recorder.cancel()
                                },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    AppIcons.Delete,
                                    contentDescription = "Отменить запись",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Box {
                            IconButton(
                                onClick = { attachMenu = true },
                                enabled = !recording,
                                modifier = Modifier.size(if (recording) 0.dp else 40.dp),
                            ) {
                                if (!recording) {
                                    Icon(
                                        AppIcons.Attach,
                                        contentDescription = "Прикрепить",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                            DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Галерея") },
                                    onClick = {
                                        attachMenu = false
                                        pickFromGallery()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Файл") },
                                    onClick = {
                                        attachMenu = false
                                        pickFile()
                                    },
                                )
                                if (cameraCaptureSupported) {
                                    DropdownMenuItem(
                                        text = { Text("Сделать фото") },
                                        onClick = {
                                            attachMenu = false
                                            takePhoto()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Снять видео") },
                                        onClick = {
                                            attachMenu = false
                                            takeVideo()
                                        },
                                    )
                                }
                            }
                        }
                        if (recording) {
                            Row(
                                Modifier.weight(1f).padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error),
                                )
                                Text(
                                    "Запись… ${recordedSeconds / 60}:${(recordedSeconds % 60).toString().padStart(2, '0')}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        if (!recording) {
                            BasicTextField(
                                value = draft,
                                onValueChange = {
                                    draft = it
                                    // While editing, the input holds the message being
                                    // edited — that must not overwrite the chat's draft.
                                    if (editing == null) onDraftChange(it)
                                },
                                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                                textStyle =
                                    MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                // Auto-capitalize sentences, like other messengers.
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                maxLines = 5,
                                decorationBox = { inner ->
                                    if (draft.isEmpty()) {
                                        Text(
                                            if (pending.isNotEmpty()) "Подпись…" else "Сообщение",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    inner()
                                },
                            )
                        }
                        // Recording buttons instead of send when there is nothing typed.
                        if (voiceRecordingSupported && !recording && !canSend) {
                            IconButton(
                                onClick = { recordVideoNote() },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    AppIcons.VideoNote,
                                    contentDescription = "Записать видеосообщение",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    recording = true
                                    recorder.start()
                                },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    AppIcons.Mic,
                                    contentDescription = "Записать голосовое сообщение",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Box(
                            Modifier
                                .size(if (voiceRecordingSupported && !recording && !canSend) 0.dp else 40.dp)
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
                                            if (recording) {
                                                // Hands the take to onRecorded, which sends it.
                                                recorder.stop()
                                                return@clickable
                                            }
                                            val target = editing
                                            when {
                                                target != null -> {
                                                    onEditMessage(target, draft)
                                                    editing = null
                                                    draft = draftBeforeEdit
                                                }
                                                pending.isNotEmpty() -> {
                                                    onSendMedia(pending, draft)
                                                    pending = emptyList()
                                                    draft = ""
                                                    onDraftChange("")
                                                }
                                                else -> {
                                                    onSend(draft)
                                                    draft = ""
                                                    onDraftChange("")
                                                }
                                            }
                                        }
                                    } else {
                                        Modifier
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (sendingAttachment) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
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
                    // Group service events ("X joined", "X added Y") render as a centered chip.
                    val service = msg.service
                    if (service != null) {
                        ServiceMessageChip(serviceMessageText(service, contacts, myId))
                        return@itemsIndexed
                    }
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
                        forwardAuthor =
                            msg.forwardedFrom?.let { if (it == myId) "Вы" else contacts[it] ?: "—" },
                        onForwardClick = msg.forwardedFrom?.let { id -> { onOpenUser(id) } },
                        onMediaClick = onMediaClick,
                        onAvatarClick = { onOpenUser(msg.senderId) },
                        onClick = { menuTarget = msg },
                        onSwipeReply = { onReply(msg) },
                        onReactionClick = { emoji -> onReact(msg, emoji) },
                        onDownloadFile = { file -> onFileClick(msg, file) },
                        onLinkClick = onLinkClick,
                        onVoiceClick = { onVoiceClick(msg) },
                        onVoiceSeek = onVoiceSeek,
                        voiceState = playingVoice?.takeIf { it.messageId == msg.id },
                        onVideoNoteClick = { onVideoNoteClick(msg) },
                        videoNoteState = playingVideoNote?.takeIf { it.messageId == msg.id },
                        downloadedFiles = downloadedFiles,
                        downloadingFiles = downloadingFiles,
                        onRetry = { onRetrySend(msg) },
                        onDiscard = { onDiscardSend(msg) },
                    )
                }
            }

            // Floating date chip (top-center): the day of the top-most visible message,
            // shown only while scrolling and fading out when the list settles.
            val topTime by remember { derivedStateOf { messages.getOrNull(listState.firstVisibleItemIndex)?.time } }
            androidx.compose.animation.AnimatedVisibility(
                visible = listState.isScrollInProgress && topTime != null,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    tonalElevation = 3.dp,
                ) {
                    Text(
                        topTime?.let { formatDay(it) } ?: "",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            // Jump-to-latest button (bottom-right), shown only when scrolled up off the newest.
            val scrollScope = rememberCoroutineScope()
            val notAtBottom by remember { derivedStateOf { listState.canScrollForward } }
            androidx.compose.animation.AnimatedVisibility(
                visible = notAtBottom,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp),
            ) {
                Surface(
                    onClick = {
                        scrollScope.launch { listState.animateScrollToItem(messages.lastIndex.coerceAtLeast(0)) }
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 4.dp,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            AppIcons.ScrollDown,
                            contentDescription = "К последним сообщениям",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }

    // Tap-to-open context menu, floating above the conversation.
    menuTarget?.let { target ->
        MessageContextMenu(
            message = target,
            // Editing is for our own text messages (not media/service messages).
            canEdit = target.senderId == myId && target.service == null && target.media.isEmpty() && target.files.isEmpty(),
            isMine = target.senderId == myId,
            onDismiss = { menuTarget = null },
            onReact = { emoji ->
                onReact(target, emoji)
                menuTarget = null
            },
            onReply = {
                onReply(target)
                menuTarget = null
            },
            onForward = {
                onForward(target)
                menuTarget = null
            },
            onEdit = {
                draftBeforeEdit = draft
                editing = target
                draft = target.text
                menuTarget = null
            },
            onDelete = {
                deleteTarget = target
                menuTarget = null
            },
        )
    }

    // Delete confirmation: "for everyone" is offered only for our own messages.
    deleteTarget?.let { target ->
        val mine = target.senderId == myId
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить сообщение?") },
            text = { Text(if (mine) "Сообщение будет удалено." else "Сообщение будет удалено у вас.") },
            confirmButton = {
                Column {
                    if (mine) {
                        TextButton(onClick = {
                            onDeleteMessage(target, true)
                            deleteTarget = null
                        }) { Text("Удалить у всех") }
                    }
                    TextButton(onClick = {
                        onDeleteMessage(target, false)
                        deleteTarget = null
                    }) { Text("Удалить у меня") }
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Отмена") } },
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
    forwardAuthor: String?,
    onForwardClick: (() -> Unit)?,
    onMediaClick: (MediaAttach, String?) -> Unit,
    onAvatarClick: () -> Unit,
    onClick: () -> Unit,
    onSwipeReply: () -> Unit,
    onReactionClick: (String) -> Unit,
    onDownloadFile: (FileAttach) -> Unit,
    onLinkClick: (String) -> Boolean,
    onVoiceClick: () -> Unit,
    onVoiceSeek: (Float) -> Unit,
    /** Non-null when this message is the one loaded in the player. */
    voiceState: PlayingVoice?,
    onVideoNoteClick: () -> Unit,
    /** Non-null when this message's circle is the one playing. */
    videoNoteState: PlayingVideoNote?,
    downloadedFiles: Set<Long>,
    downloadingFiles: Map<Long, Float>,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
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
        val swipeToReply =
            Modifier.pointerInput(rowKey) {
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
            }
        Row(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .then(swipeToReply),
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        ) {
            // Avatars are only meaningful in group chats; in a 1:1 dialog the only
            // other participant is obvious, so no avatar gutter is shown.
            if (!isMine && isGroup) {
                if (startsRun) Avatar(senderName, senderAvatar, 32.dp, onClick = onAvatarClick) else Spacer(Modifier.size(32.dp))
                Spacer(Modifier.width(6.dp))
            }
            // A message that is nothing but a round video gets no bubble: the circle
            // floats on the conversation, the way other messengers draw them.
            val bareVideoNote =
                msg.text.isBlank() &&
                    msg.voice == null &&
                    msg.files.isEmpty() &&
                    msg.linkPreview == null &&
                    msg.replyTo == null &&
                    msg.pending.isEmpty() &&
                    msg.media.size == 1 &&
                    msg.media.single().isVideoNote
            val bg =
                when {
                    bareVideoNote -> Color.Transparent
                    isMine -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            // Without a bubble behind it, the footer sits on the chat background, so it
            // can't use the on-bubble colour.
            val fg =
                when {
                    bareVideoNote -> MaterialTheme.colorScheme.onSurfaceVariant
                    isMine -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                Box(
                    Modifier
                        .widthIn(max = 280.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(bg)
                        .clickable(onClick = onClick)
                        .padding(
                            horizontal = if (bareVideoNote) 0.dp else 12.dp,
                            vertical = if (bareVideoNote) 0.dp else 6.dp,
                        ),
                ) {
                    Column {
                        if (isGroup && !isMine && startsRun) {
                            Text(senderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        if (forwardAuthor != null) {
                            Text(
                                "Переслано от $forwardAuthor",
                                style = MaterialTheme.typography.labelSmall,
                                color = fg.copy(alpha = if (onForwardClick != null) 0.95f else 0.75f),
                                modifier =
                                    if (onForwardClick != null) {
                                        Modifier.clickable(onClick = onForwardClick)
                                    } else {
                                        Modifier
                                    },
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        msg.replyTo?.let { reply ->
                            ReplyQuote(replyAuthor ?: "—", reply.text, fg)
                            Spacer(Modifier.height(4.dp))
                        }
                        msg.pending.forEach { item ->
                            PendingThumbnail(item)
                            Spacer(Modifier.height(4.dp))
                        }
                        if (msg.pending.isNotEmpty()) {
                            val failed = msg.pending.any { it.state == UploadState.FAILED }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (failed) {
                                    PendingAction(AppIcons.Retry, "Повторить отправку", fg, onRetry)
                                    Spacer(Modifier.width(12.dp))
                                }
                                // Always available: an upload in flight can be abandoned,
                                // and a failed one has to be dismissable.
                                PendingAction(AppIcons.Close, "Отменить", fg, onDiscard)
                            }
                            Spacer(Modifier.height(2.dp))
                        }
                        msg.media.forEach { media ->
                            if (media.isVideoNote) {
                                // Played in place, inside its circle — not in the
                                // full-screen rectangular viewer.
                                VideoNoteView(media, videoNoteState, onVideoNoteClick)
                            } else {
                                MediaThumbnail(media, onClick = { onMediaClick(media, msg.id) })
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        msg.voice?.let { voice ->
                            VoiceMessageView(
                                voice = voice,
                                fg = fg,
                                state = voiceState,
                                onClick = onVoiceClick,
                                onSeek = onVoiceSeek,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        msg.files.forEach { file ->
                            FileAttachView(
                                file = file,
                                fg = fg,
                                downloaded = file.fileId in downloadedFiles,
                                progress = downloadingFiles[file.fileId],
                                onClick = { onDownloadFile(file) },
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        if (msg.text.isNotEmpty()) {
                            LinkedText(msg.text, MaterialTheme.typography.bodyLarge, fg, onLinkClick)
                        }
                        msg.linkPreview?.let { preview -> LinkPreviewCard(preview, fg) }
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

/** Horizontal strip of picked photos/videos/files staged above the input. */
@Composable
private fun StagedAttachments(
    items: List<PickedMedia>,
    onRemove: (PickedMedia) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items) { media ->
                Box(Modifier.size(56.dp)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (media.kind) {
                            PickedKind.PHOTO ->
                                AsyncImage(
                                    // The URI/handle, not bytes: Coil decodes at thumbnail size.
                                    model = media.content.previewModel,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            PickedKind.VIDEO ->
                                Icon(AppIcons.Play, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            PickedKind.FILE ->
                                Icon(AppIcons.Attach, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { onRemove(media) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.Close, contentDescription = "Убрать", modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

/** A link-preview card (server SHARE attach): image + title + description, opens the URL. */
@Composable
private fun LinkPreviewCard(
    preview: LinkPreview,
    fg: Color,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier
            .padding(top = 4.dp)
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(fg.copy(alpha = 0.10f))
            .clickable { uriHandler.openUriSafely(preview.url) },
    ) {
        preview.imageUrl?.let { img ->
            AsyncImage(
                model = img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
        }
        Column(Modifier.padding(8.dp)) {
            (preview.title ?: preview.url).let {
                Text(it, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            preview.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = fg.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A voice message: play/pause, the server's waveform (bars already played are
 * filled), and the length. Tap along the bar to jump within the clip.
 */
@Composable
private fun VoiceMessageView(
    voice: VoiceAttach,
    fg: Color,
    state: PlayingVoice?,
    onClick: () -> Unit,
    onSeek: (Float) -> Unit,
) {
    // Loaded in the player (playing or paused) — the clip has a position to keep.
    val loaded = state != null
    val progress = state?.progress
    val shown = progress ?: 0f
    Row(
        Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(fg.copy(alpha = 0.15f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            when {
                // Fetching the clip: nothing to show but a spinner.
                loaded && progress == null ->
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = fg)
                loaded && state?.paused == false ->
                    Icon(AppIcons.Pause, contentDescription = "Пауза", tint = fg, modifier = Modifier.size(20.dp))
                else ->
                    Icon(AppIcons.Play, contentDescription = "Воспроизвести", tint = fg, modifier = Modifier.size(20.dp))
            }
        }
        // Tap to jump; no drag handler here on purpose. A horizontal drag detector on
        // this bar competed with the chat's vertical scroll and with the row's
        // swipe-to-reply, which made both unreliable. A tap-only detector consumes
        // nothing until the finger lifts in place, so scrolling and swiping still
        // belong to the list and the row.
        //
        // The touch area is taller than the drawn bar: a 28dp strip is a hard target,
        // and missing it hit the bubble instead.
        val seekByTap =
            if (!loaded) {
                Modifier
            } else {
                Modifier.pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onSeek((offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f))
                    }
                }
            }
        Box(
            Modifier.width(140.dp).height(40.dp).then(seekByTap),
            contentAlignment = Alignment.Center,
        ) {
            Waveform(voice.waveform, shown, fg, Modifier.fillMaxWidth().height(28.dp))
        }
        Text(
            // Counts up while playing, like every other player.
            formatVoiceDuration(
                if (loaded && voice.durationSeconds > 0) {
                    (voice.durationSeconds * shown).toInt()
                } else {
                    voice.durationSeconds
                },
            ),
            style = MaterialTheme.typography.labelSmall,
            color = fg.copy(alpha = 0.7f),
        )
    }
}

/** The amplitude sketch, with the played part filled in. A flat bar if we have none. */
@Composable
private fun Waveform(
    samples: List<Float>,
    progress: Float,
    fg: Color,
    modifier: Modifier,
) {
    val bars =
        remember(samples) {
            if (samples.isEmpty()) List(WAVEFORM_BARS) { 0.35f } else samples.resample(WAVEFORM_BARS)
        }
    Canvas(modifier) {
        val slot = size.width / bars.size
        val barWidth = (slot * 0.55f).coerceAtLeast(1f)
        bars.forEachIndexed { index, level ->
            val height = (size.height * level).coerceAtLeast(2f)
            val played = (index + 1f) / bars.size <= progress
            drawRect(
                color = if (played) fg else fg.copy(alpha = 0.4f),
                topLeft = Offset(x = index * slot, y = (size.height - height) / 2f),
                size = Size(barWidth, height),
            )
        }
    }
}

/** Averages [this] down (or stretches it up) to exactly [count] bars. */
private fun List<Float>.resample(count: Int): List<Float> {
    if (isEmpty()) return List(count) { 0f }
    return List(count) { i ->
        val start = i * size / count
        val end = ((i + 1) * size / count).coerceAtLeast(start + 1).coerceAtMost(size)
        subList(start, end).average().toFloat()
    }
}

/** "0:07" / "1:23". */
private fun formatVoiceDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "${safe / 60}:${(safe % 60).toString().padStart(2, '0')}"
}

private const val WAVEFORM_BARS = 28

/** A downloadable file attachment row inside a message bubble (tap to download/open). */
@Composable
private fun FileAttachView(
    file: FileAttach,
    fg: Color,
    downloaded: Boolean,
    /** Non-null while downloading: the fraction fetched so far. */
    progress: Float?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(fg.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            if (progress != null) {
                // Determinate as soon as bytes are counted; the size isn't always known.
                if (progress > 0f) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = fg,
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = fg)
                }
            } else {
                Icon(
                    // A saved file offers to open; an unsaved one, to fetch.
                    if (downloaded) AppIcons.Open else AppIcons.Attach,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column {
            Text(file.name, style = MaterialTheme.typography.bodyMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val action =
                when {
                    progress != null && progress > 0f -> "Загрузка… ${(progress * 100).toInt()}%"
                    progress != null -> "Загрузка…"
                    downloaded -> "Открыть"
                    else -> "Скачать"
                }
            Text(
                "${formatFileSize(file.size)} · $action",
                style = MaterialTheme.typography.labelSmall,
                color = fg.copy(alpha = 0.7f),
            )
        }
    }
}

/** Human-readable byte size, e.g. "2.4 МБ". */
private fun formatFileSize(bytes: Long): String =
    when {
        bytes >= 1_000_000 -> "${(bytes / 100_000) / 10.0} МБ"
        bytes >= 1_000 -> "${bytes / 1_000} КБ"
        else -> "$bytes Б"
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

/** Banner shown above the input while editing a message. */
@Composable
private fun EditBanner(
    text: String,
    onCancel: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Редактирование",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text.ifBlank { "Сообщение" },
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
    canEdit: Boolean,
    isMine: Boolean,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var expanded by remember { mutableStateOf(false) }
    // Drop input focus so the soft keyboard closes — otherwise the menu (centered
    // over the whole screen) would sit behind the keyboard.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focusManager.clearFocus() }
    PlatformBackHandler(enabled = true, onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(onClick = onDismiss)
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
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
                    ContextMenuItem(AppIcons.Forward, "Переслать", onClick = onForward)
                    if (canEdit) {
                        ContextMenuItem(AppIcons.Edit, "Изменить", onClick = onEdit)
                    }
                    if (message.text.isNotBlank()) {
                        ContextMenuItem(AppIcons.Copy, "Копировать") {
                            clipboard.setText(AnnotatedString(message.text))
                            onDismiss()
                        }
                    }
                    if (isMine || message.service == null) {
                        ContextMenuItem(AppIcons.Delete, "Удалить", onClick = onDelete)
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

/** A small icon + label action on a still-sending bubble (retry / cancel). */
@Composable
private fun PendingAction(
    icon: Painter,
    label: String,
    fg: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

/**
 * An attachment that is still going up: its local thumbnail, dimmed, with a
 * progress ring over it — or a retry-less failure marker if the upload broke.
 */
@Composable
private fun PendingThumbnail(item: PendingAttach) {
    val shape = RoundedCornerShape(10.dp)
    val failed = item.state == UploadState.FAILED
    Box(
        Modifier.width(240.dp).heightIn(min = 120.dp, max = 320.dp).clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        if (item.kind == PickedKind.FILE) {
            // Files have no image to show; the icon stands in for the thumbnail.
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant))
            Icon(
                AppIcons.Attach,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
        } else {
            AsyncImage(
                model = item.preview,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        // Scrim, so a light photo doesn't swallow the indicator.
        Box(Modifier.matchParentSize().background(Color(0x66000000)))
        when {
            failed ->
                Text("Не отправлено", style = MaterialTheme.typography.labelMedium, color = Color.White)
            // A closed ring with a tick: this one is up, and the message is only
            // waiting on its siblings.
            item.state == UploadState.DONE ->
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        color = Color.White,
                    )
                    Icon(
                        AppIcons.Delivered,
                        contentDescription = "Загружено",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            item.state == UploadState.UPLOADING && item.progress > 0f ->
                CircularProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                    color = Color.White,
                )
            else ->
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                    color = Color.White,
                )
        }
    }
}

/**
 * A round video message: its thumbnail until tapped, then the video playing inside
 * the same circle.
 */
@Composable
private fun VideoNoteView(
    media: MediaAttach,
    state: PlayingVideoNote?,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(VIDEO_NOTE_SIZE).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val url = state?.url
        if (url != null) {
            VideoPlayer(url, Modifier.matchParentSize(), crop = true)
        } else {
            SubcomposeAsyncImage(
                model = media.url,
                contentDescription = "Видеосообщение",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                loading = { MediaTilePlaceholder(loading = true) },
                error = { MediaTilePlaceholder(loading = false) },
            )
            if (state != null) {
                // Tapped, waiting for the stream.
                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp, color = Color.White)
            } else {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color(0x88000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        AppIcons.Play,
                        contentDescription = "Воспроизвести",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

private val VIDEO_NOTE_SIZE = 200.dp

/**
 * Fills a media tile while its image is being fetched (or if the fetch failed), so
 * a message with several photos shows its layout and progress straight away rather
 * than an empty bubble.
 */
@Composable
private fun MediaTilePlaceholder(loading: Boolean) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "Не удалось загрузить",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MediaThumbnail(
    media: MediaAttach,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    // The server reports the dimensions, so the tile can take its final shape before
    // the bytes arrive — no blank bubble, and no reflow once the image lands. Without
    // them, fall back to a minimum height so the placeholder still has a box to fill.
    var mod =
        if (media.width > 0 && media.height > 0) {
            Modifier.width(240.dp).aspectRatio(media.width.toFloat() / media.height)
        } else {
            Modifier.width(240.dp).heightIn(min = 140.dp, max = 320.dp)
        }
    mod = mod.clip(shape)
    Box(modifier = Modifier.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        SubcomposeAsyncImage(
            model = media.url,
            contentDescription = if (media.type == MediaType.VIDEO) "Видео" else "Фото",
            contentScale = ContentScale.Crop,
            modifier = mod,
            loading = { MediaTilePlaceholder(loading = true) },
            error = { MediaTilePlaceholder(loading = false) },
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
    canForward: Boolean,
    onDownload: () -> Unit,
    onForward: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    PlatformBackHandler(enabled = true, onBack = onClose)
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().background(Color(0xF2000000)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        when (viewer) {
            is MediaViewer.Loading -> CircularProgressIndicator(color = Color.White)
            is MediaViewer.Image -> ZoomableImage(viewer.url)
            is MediaViewer.Video -> VideoPlayer(viewer.url, Modifier.fillMaxWidth().heightIn(max = 480.dp))
        }
        Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 4.dp)) {
            // The action menu needs a loaded URL (download/share/forward target).
            if (viewer.url != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(AppIcons.More, contentDescription = "Действия", tint = Color.White)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Сохранить") },
                            onClick = {
                                menuOpen = false
                                onDownload()
                            },
                        )
                        if (canForward) {
                            DropdownMenuItem(
                                text = { Text("Переслать") },
                                onClick = {
                                    menuOpen = false
                                    onForward()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Поделиться") },
                            onClick = {
                                menuOpen = false
                                onShare()
                            },
                        )
                    }
                }
            }
            IconButton(onClick = onClose) {
                Icon(AppIcons.Close, contentDescription = "Закрыть", tint = Color.White)
            }
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

/** A centered, muted chip for a group service/system event. */
@Composable
private fun ServiceMessageChip(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/** Builds a human-readable Russian label for a group service event. */
private fun serviceMessageText(
    service: ServiceEvent,
    contacts: Map<Long, String>,
    myId: Long,
): String {
    fun name(id: Long) = if (id == myId) "Вы" else contacts[id] ?: "Пользователь"
    val actor = name(service.actorId)
    val targets = service.userIds.joinToString(", ") { name(it) }
    return when (service.event) {
        "new" -> "$actor создал(а) группу"
        "add" -> if (targets.isNotBlank()) "$actor добавил(а): $targets" else "$actor добавил(а) участника"
        "remove" -> if (targets.isNotBlank()) "$actor исключил(а): $targets" else "$actor исключил(а) участника"
        "join" -> "$actor присоединил(ся/ась) к группе"
        "leave" -> "$actor покинул(а) группу"
        "title" -> if (service.title != null) "$actor изменил(а) название на «${service.title}»" else "$actor изменил(а) название"
        "icon" -> "$actor изменил(а) фото группы"
        "pin" -> "$actor закрепил(а) сообщение"
        "unpin" -> "$actor открепил(а) сообщение"
        "hello" -> "$actor теперь в MAX"
        "joinByLink" -> "$actor присоединил(ся/ась) по ссылке"
        "call" -> service.message ?: "Звонок"
        // "system" (e.g. the "Теперь в MAX!" greeting) and any unmapped event carry
        // ready-made server text — prefer it over a generic fallback.
        else -> service.message ?: "$actor обновил(а) чат"
    }
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
