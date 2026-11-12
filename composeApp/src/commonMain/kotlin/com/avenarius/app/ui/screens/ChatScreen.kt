package com.avenarius.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.avenarius.app.model.BotButton
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
import com.avenarius.app.model.Sticker
import com.avenarius.app.model.StickerShelf
import com.avenarius.app.model.TextSpan
import com.avenarius.app.model.UploadState
import com.avenarius.app.model.UserInfo
import com.avenarius.app.model.VoiceAttach
import com.avenarius.app.model.canPinMessages
import com.avenarius.app.model.isSavedMessages
import com.avenarius.app.model.previewLabel
import com.avenarius.app.model.remapSpans
import com.avenarius.app.model.toggled
import com.avenarius.app.net.Presence
import com.avenarius.app.ui.AppIcons
import com.avenarius.app.ui.MediaViewer
import com.avenarius.app.ui.PlatformBackHandler
import com.avenarius.app.ui.PlayingVideoNote
import com.avenarius.app.ui.PlayingVoice
import com.avenarius.app.ui.VideoPlayer
import com.avenarius.app.ui.cameraCaptureSupported
import com.avenarius.app.ui.components.AppSheet
import com.avenarius.app.ui.components.Avatar
import com.avenarius.app.ui.components.CenteredSpinner
import com.avenarius.app.ui.components.LinkedText
import com.avenarius.app.ui.components.PillTextField
import com.avenarius.app.ui.components.SheetAction
import com.avenarius.app.ui.components.openUriSafely
import com.avenarius.app.ui.components.senderNameColor
import com.avenarius.app.ui.components.spanStyleOf
import com.avenarius.app.ui.components.tileColor
import com.avenarius.app.ui.formatClock
import com.avenarius.app.ui.formatDay
import com.avenarius.app.ui.lastSeenText
import com.avenarius.app.ui.nowMillis
import com.avenarius.app.ui.plural
import com.avenarius.app.ui.rememberCameraPhotoLauncher
import com.avenarius.app.ui.rememberCameraVideoLauncher
import com.avenarius.app.ui.rememberFilePickLauncher
import com.avenarius.app.ui.rememberPhotoPickLauncher
import com.avenarius.app.ui.rememberVoiceRecorder
import com.avenarius.app.ui.sameDay
import com.avenarius.app.ui.theme.Radius
import com.avenarius.app.ui.theme.Spacing
import com.avenarius.app.ui.theme.chatWallpaper
import com.avenarius.app.ui.theme.isLightTheme
import com.avenarius.app.ui.theme.onScrim
import com.avenarius.app.ui.theme.outgoingBubbleColor
import com.avenarius.app.ui.voiceRecordingSupported
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.roundToLong

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
    /** "печатает…" line for the header, or null when nobody is typing. */
    typingText: String? = null,
    /** Who is online now; a dialog's header says "в сети" when the partner is. */
    onlineUsers: Set<Long> = emptySet(),
    /** Last-seen info, for "был(а) в сети …" when the partner is offline. */
    presence: Map<Long, Presence> = emptyMap(),
    /** "Подключение…" while the connection is being (re)established; shown in place of the status line. */
    connecting: String? = null,
    onLoadOlder: () -> Unit,
    onBack: () -> Unit,
    /** Starts a call with the dialog partner ([userId], [isVideo]). */
    onStartCall: (userId: Long, isVideo: Boolean) -> Unit,
    /** Toggles muting notifications for this chat. */
    onToggleMute: () -> Unit,
    onDraftChange: (String) -> Unit,
    /** Sends the text with its formatting runs. */
    onSend: (String, List<TextSpan>) -> Unit,
    onSendMedia: (List<PickedMedia>, String) -> Unit,
    onStagedConsumed: () -> Unit,
    onMediaClick: (MediaAttach, String?) -> Unit,
    onOpenUser: (Long) -> Unit,
    onReact: (Message, String) -> Unit,
    onReply: (Message) -> Unit,
    onForward: (Message) -> Unit,
    onEditMessage: (Message, String) -> Unit,
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
    /** Read marks of this chat's members (userId → ms), for the "who read" list. */
    readMarks: Map<Long, Long> = emptyMap(),
    /** The message whose read-by sheet is open, if any. */
    readingMessageId: String? = null,
    /** True while the read-by sheet is refreshing its data. */
    readMarksLoading: Boolean = false,
    /** Opens the read-by sheet for a message. */
    onShowReadBy: (Message) -> Unit = {},
    /** Closes the read-by sheet. */
    onCloseReadBy: () -> Unit = {},
    /** Resolves a reader's name + avatar for the sheet. */
    readerInfo: (Long) -> UserInfo = { UserInfo(it, "Пользователь $it") },
    /** The emoji a double tap reacts with; null leaves the gesture off. */
    quickReaction: String? = null,
    /** Enter sends the message instead of breaking the line. */
    sendOnEnter: Boolean = false,
    /** The server's reaction picker; the built-in set is used while it is empty. */
    reactionsMenu: List<String> = emptyList(),
    /** Multi-select actions. */
    onForwardMany: (List<Message>) -> Unit = {},
    /** Deletes these messages, for everyone or just for us. */
    onDeleteMany: (List<Message>, Boolean) -> Unit = { _, _ -> },
    /** Pins (true, notifying the members or not) or unpins (false) a message. */
    onPin: (Message, Boolean, Boolean) -> Unit = { _, _, _ -> },
    /** Open with the in-chat search showing (the info page's «Найти»); [onSearchStarted] acknowledges it. */
    startSearch: Boolean = false,
    onSearchStarted: () -> Unit = {},
    /** Asks the server for in-chat [query] hits ("" clears them); they come back as [serverHits]. */
    onSearch: (String) -> Unit = {},
    serverHits: List<Message> = emptyList(),
    /** Loads history back to a hit that isn't loaded yet. */
    onLoadUntil: (Message) -> Unit = {},
    /** A message to scroll to once loaded; [onJumped] acknowledges it. */
    jumpTo: String? = null,
    onJumped: () -> Unit = {},
    /** «Стереть переписку» (asked to confirm first). */
    onClearHistory: () -> Unit = {},
    /** Bot callback buttons in flight, and pressing one. */
    pressingButtons: Set<BotButton> = emptySet(),
    onBotButton: (BotButton) -> Unit = {},
    /** The «Реакции» sheet: which message, who reacted with what (null while loading), open/close. */
    reactionsMessageId: String? = null,
    reactedBy: Map<String, List<Long>>? = null,
    onShowReactions: (Message) -> Unit = {},
    onCloseReactions: () -> Unit = {},
    /** Our answers in a poll set to these (empty retracts). */
    onVote: (Message, Set<Int>) -> Unit = { _, _ -> },
    /** «Написать» on a shared contact. */
    onWriteTo: (UserInfo) -> Unit = {},
    /** The sticker panel: its sets (null while loading), the stickers fetched so far, and the calls behind it. */
    stickerShelf: StickerShelf? = null,
    stickers: Map<Long, Sticker> = emptyMap(),
    onOpenStickers: () -> Unit = {},
    onLoadStickers: (List<Long>) -> Unit = {},
    onSendSticker: (Sticker) -> Unit = {},
    /** Polls may be sent in 1:1 dialogs (server config). */
    pollsInDialogs: Boolean = false,
    /** Sends a new poll: question, answers, settings bits. */
    onSendPoll: (String, List<String>, Int) -> Unit = { _, _, _ -> },
    /** The chat background (Настройки → Оформление → Фон чата). */
    wallpaper: Int = 0,
    /** The chat was opened by an info page's «Найти»: back goes to that page even while the search shows. */
    searchLeavesChat: Boolean = false,
) {
    // The whole field value (selection and IME composition included), not just its text: rebuilding
    // it from the text on every change would break the keyboard's composing state.
    var field by remember(chat?.id) { mutableStateOf(TextFieldValue(initialDraft, TextRange(initialDraft.length))) }
    // Formatting picked in the composer, kept in step with later typing by remapSpans.
    var draftSpans by remember(chat?.id) { mutableStateOf(emptyList<TextSpan>()) }

    fun setDraftText(
        text: String,
        spans: List<TextSpan> = emptyList(),
    ) {
        field = TextFieldValue(text, TextRange(text.length))
        draftSpans = spans
    }
    // What was typed (and its formatting) before an edit took over the input, so ending the
    // edit puts the draft back instead of clearing it.
    var draftBeforeEdit by remember(chat?.id) { mutableStateOf("") }
    var spansBeforeEdit by remember(chat?.id) { mutableStateOf(emptyList<TextSpan>()) }
    // The message currently being edited (input shows its text + an "editing" banner).
    var editing by remember(chat?.id) { mutableStateOf<Message?>(null) }
    // The messages pending a delete confirmation (one from its menu, or the multi-select).
    var deleteTargets by remember(chat?.id) { mutableStateOf(emptyList<Message>()) }
    // Photos/videos/files staged for sending, with an optional caption (the draft).
    var pending by remember(chat?.id) { mutableStateOf<List<PickedMedia>>(emptyList()) }
    val pickFromGallery = rememberPhotoPickLauncher { pending = pending + it }
    val takePhoto = rememberCameraPhotoLauncher { pending = pending + it }
    val takeVideo = rememberCameraVideoLauncher { pending = pending + it }
    val recordVideoNote = rememberCameraVideoLauncher { onSendVideoNote(it) }
    val pickFile = rememberFilePickLauncher { pending = pending + it }
    var attachMenu by remember { mutableStateOf(false) }
    var stickerPanel by remember { mutableStateOf(false) }
    var pollForm by remember(chat?.id) { mutableStateOf(false) }
    if (pollForm) {
        PollFormSheet(onCreate = { q, a, settings ->
            pollForm = false
            onSendPoll(q, a, settings)
        }) { pollForm = false }
    }
    if (stickerPanel) {
        StickerSheet(stickerShelf, stickers, onLoadStickers, onSend = {
            stickerPanel = false
            onSendSticker(it)
        }) { stickerPanel = false }
    }
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
    val jumpScope = rememberCoroutineScope()
    // Replying or editing means typing next: focus the composer and raise the keyboard.
    val composerFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(replyingTo?.id, editing?.id) {
        if (replyingTo != null || editing != null) {
            runCatching { composerFocus.requestFocus() }
            keyboard?.show()
        }
    }
    val clipboard = LocalClipboardManager.current
    // Multi-select: ids of the picked messages; a long press on a bubble starts it.
    var selected by remember(chat?.id) { mutableStateOf(setOf<String>()) }
    val selecting = selected.isNotEmpty()
    PlatformBackHandler(enabled = selecting) { selected = emptySet() }
    // The message briefly tinted after jumping to it (from a reply quote or the pinned bar).
    var flashId by remember(chat?.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(flashId) {
        if (flashId != null) {
            delay(1600)
            flashId = null
        }
    }
    var showPinned by remember(chat?.id) { mutableStateOf(false) }
    val isDialog = chat?.isDialog ?: true
    // Service notices and bots: nobody to call, and their "last seen" means nothing.
    val isService = chat?.isServiceChat == true
    // For a 1:1 dialog the other user's id is chatId XOR myId.
    val otherUserId = if (isDialog && chat != null && myId >= 0) chat.id xor myId else null
    // The message whose context menu is open (overlay shown above the conversation).
    var menuTarget by remember(chat?.id) { mutableStateOf<Message?>(null) }
    // The message waiting for the pin confirmation.
    var pinTarget by remember(chat?.id) { mutableStateOf<Message?>(null) }
    pinTarget?.let { target ->
        var notify by remember(target.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { pinTarget = null },
            title = { Text("Закрепить сообщение?") },
            text = {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.small))
                        .toggleable(value = notify, onValueChange = { notify = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = notify, onCheckedChange = null)
                    Spacer(Modifier.width(Spacing.s))
                    Text(if (chat?.isChannel == true) "Уведомить подписчиков" else "Уведомить участников")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onPin(target, true, notify)
                    pinTarget = null
                }) { Text("Закрепить") }
            },
            dismissButton = { TextButton(onClick = { pinTarget = null }) { Text("Отмена") } },
        )
    }

    // Index of the first unread message (the divider sits just before it).
    // More unread than loaded: the oldest loaded one (reaching the top then pages back the rest).
    val firstUnread = if (unreadAtOpen > 0 && messages.isNotEmpty()) (messages.size - unreadAtOpen).coerceAtLeast(0) else -1

    // Saved (with the list state), so coming back from the info page keeps the place instead of re-running the open jump.
    var positioned by rememberSaveable(chat?.id) { mutableStateOf(false) }
    var prevSize by rememberSaveable(chat?.id) { mutableStateOf(0) }
    var prevLastKey by remember(chat?.id) { mutableStateOf<Any?>(null) }
    // When loading older messages we remember the top item so we can stay on it
    // after the prepend (otherwise the list visibly jumps).
    var restoreAnchor by remember(chat?.id) { mutableStateOf<Pair<Any, Int>?>(null) }

    LaunchedEffect(messages.size, chat?.id) {
        if (messages.isEmpty()) return@LaunchedEffect
        when {
            !positioned -> {
                // Jump INSTANTLY to the first unread (or the very end) on open.
                if (firstUnread >= 0) listState.scrollToItem(firstUnread) else listState.scrollToEnd(messages.lastIndex)
                positioned = true
            }
            olderPrepended(messages.first().listKey(), restoreAnchor?.first) -> {
                // Older page prepended: scroll back to the previously-top message.
                val (key, offset) = restoreAnchor!!
                val idx = messages.indexOfFirst { (it.id ?: it.cid ?: it.time) == key }
                if (idx >= 0) listState.scrollToItem(idx, offset)
                restoreAnchor = null
            }
            // New message appended (not history prepended): ours always, others only if already near the bottom.
            messages.size > prevSize && messages.last().listKey() != prevLastKey -> {
                // An anchor still on the first message is stale (no older page came: the start of the
                // history); restoring it here would throw the list back to the top.
                restoreAnchor = null
                val lastVisible =
                    listState.layoutInfo.visibleItemsInfo
                        .lastOrNull()
                        ?.index ?: 0
                if (followAppended(messages.last().senderId == myId, lastVisible, prevSize)) {
                    listState.animateScrollToItem(messages.lastIndex, END_OFFSET)
                }
            }
        }
        prevSize = messages.size
        prevLastKey = messages.last().listKey()
    }

    // The keyboard shrinks the list from below: if we were at the end, stay pinned to it while it slides up.
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    LaunchedEffect(listState, ime) {
        var prev = 0
        snapshotFlow { ime.getBottom(density) }.collect { bottom ->
            val lastVisible =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            if (bottom > prev && total > 0 && lastVisible >= total - 2) listState.scrollToEnd(total - 1)
            prev = bottom
        }
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

    // In-chat search: the loaded messages at once, the server's hits (older history too) a moment later.
    var searchOpen by remember(chat?.id) { mutableStateOf(false) }
    LaunchedEffect(startSearch) {
        if (startSearch) {
            searchOpen = true
            onSearchStarted()
        }
    }
    var searchQuery by remember(chat?.id) { mutableStateOf("") }
    val query = searchQuery.trim().takeIf { searchOpen && it.length >= 2 }
    LaunchedEffect(query) {
        delay(400) // let typing settle before asking the server
        onSearch(query ?: "")
    }
    val matches =
        remember(messages, query, serverHits) {
            if (query == null) {
                emptyList()
            } else {
                (messages.filter { it.id != null && it.text.contains(query, ignoreCase = true) } + serverHits)
                    .distinctBy { it.id }
                    .sortedBy { it.time }
            }
        }
    // Position within [matches], counted from the newest hit.
    var searchPos by remember(chat?.id, query) { mutableIntStateOf(0) }
    // History is paged back to a hit only once the user has stepped (↑/↓), never as results land.
    var stepped by remember(chat?.id, query) { mutableStateOf(false) }
    val hit = matches.getOrNull(matches.lastIndex - searchPos)
    val matchIndex = hit?.let { h -> messages.indexOfFirst { it.id == h.id } }?.takeIf { it >= 0 }
    LaunchedEffect(hit?.id, matchIndex, stepped) {
        when {
            matchIndex != null -> listState.animateScrollToItem(matchIndex)
            // Older than what is loaded: load back to it; the list then scrolls here.
            hit != null && stepped -> onLoadUntil(hit)
        }
    }
    // A search hit opened from the chat list: scroll to it once its history is in.
    LaunchedEffect(jumpTo, messages.size, positioned) {
        val idx = jumpTo?.let { id -> messages.indexOfFirst { it.id == id } } ?: -1
        if (positioned && idx >= 0) {
            listState.scrollToItem(idx)
            // A third of the way down rather than flush under the top bar, where the tint goes unseen.
            listState.scrollBy(-listState.layoutInfo.viewportSize.height / 3f)
            flashId = jumpTo
            onJumped()
        }
    }
    // Opened by the info page's «Найти»: back leaves to that page straight away (App's handler).
    PlatformBackHandler(enabled = searchOpen && !searchLeavesChat) {
        searchOpen = false
        searchQuery = ""
    }

    /** Scrolls to a loaded message and flashes it; false when it isn't in the loaded history. */
    val jumpTo: (String) -> Boolean = { id ->
        val idx = messages.indexOfFirst { it.id == id }
        if (idx >= 0) {
            flashId = id
            jumpScope.launch { listState.animateScrollToItem(idx) }
        }
        idx >= 0
    }

    var menuOpen by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) ClearHistoryDialog(onClearHistory) { confirmClear = false }
    // Heading avatar: a 1:1 dialog uses the contact's photo; a group uses its own.
    val headingAvatar = if (isDialog) otherUserId?.let { senderAvatars[it] } else chat?.avatarUrl
    val cs = MaterialTheme.colorScheme
    val isSaved = chat?.isSavedMessages == true
    if (attachMenu) {
        AttachSheet(
            onDismiss = { attachMenu = false },
            onGallery = pickFromGallery,
            onFile = pickFile,
            onPhoto = takePhoto.takeIf { cameraCaptureSupported },
            onVideo = takeVideo.takeIf { cameraCaptureSupported },
            // Where we can write; a 1:1 dialog only when the server allows polls there.
            onPoll = { pollForm = true }.takeIf { chat?.canWrite == true && (!chat.isDialog || pollsInDialogs) },
        )
    }
    Scaffold(
        modifier = Modifier.chatWallpaper(wallpaper),
        containerColor = Color.Transparent,
        contentColor = cs.onSurface,
        topBar = {
            // 1:1 → open the contact's profile; group/channel → open the group page.
            val onHeaderClick: (() -> Unit)? =
                when {
                    isSaved -> null
                    !isDialog && chat != null -> onOpenGroup
                    otherUserId != null -> {
                        { onOpenUser(otherUserId) }
                    }
                    else -> null
                }
            Surface(color = cs.surfaceContainer) {
                Column {
                    if (selecting) {
                        val picked = messages.filter { it.id in selected }
                        SelectionBar(
                            count = selected.size,
                            onClose = { selected = emptySet() },
                            onCopy =
                                {
                                    clipboard.setText(AnnotatedString(picked.joinToString("\n\n") { it.text }.trim()))
                                    selected = emptySet()
                                }.takeIf { picked.any { it.text.isNotBlank() } },
                            onForward = {
                                onForwardMany(picked)
                                selected = emptySet()
                            },
                            onDelete = { deleteTargets = picked },
                        )
                    } else if (searchOpen) {
                        ChatSearchBar(
                            query = searchQuery,
                            onQuery = { searchQuery = it },
                            position = if (matches.isEmpty()) null else "${searchPos + 1} из ${matches.size}",
                            nothingFound = matches.isEmpty() && searchQuery.trim().length >= 2,
                            loading = loadingOlder,
                            onOlder = {
                                if (searchPos < matches.lastIndex) searchPos++
                                stepped = true
                            },
                            // At the newest hit, ↓ fetches that hit when it isn't loaded yet.
                            onNewer = {
                                if (searchPos > 0) searchPos--
                                stepped = true
                            },
                            onClose = {
                                searchOpen = false
                                searchQuery = ""
                            },
                        )
                    } else {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .height(64.dp)
                                .padding(horizontal = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "Назад") }
                            Row(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(Radius.medium))
                                    .then(if (onHeaderClick != null) Modifier.clickable(onClick = onHeaderClick) else Modifier)
                                    .padding(Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (isSaved) {
                                    SavedMessagesAvatar(40.dp)
                                } else {
                                    Avatar(
                                        chat?.title ?: "Чат",
                                        headingAvatar,
                                        40.dp,
                                        online = otherUserId != null && otherUserId in onlineUsers,
                                    )
                                }
                                Spacer(Modifier.width(Spacing.m))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            chat?.title ?: "Чат",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        if (chat?.official == true) {
                                            Spacer(Modifier.width(Spacing.xs))
                                            Icon(
                                                AppIcons.Verified,
                                                contentDescription = "Официальный",
                                                tint = cs.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                    val partnerOnline = !isSaved && otherUserId != null && otherUserId in onlineUsers
                                    val active = connecting == null && (typingText != null || partnerOnline)
                                    val lastSeen =
                                        otherUserId?.takeIf { !isSaved && !isService }?.let { presence[it] }?.let {
                                            lastSeenText(
                                                it,
                                                nowMillis(),
                                            )
                                        }
                                    val members =
                                        chat?.takeIf { !isDialog && it.participantsCount > 0 }?.let {
                                            participantsLabel(it.participantsCount, it.isChannel)
                                        }
                                    val status =
                                        connecting?.lowercase()
                                            ?: typingText
                                            ?: when {
                                                isSaved -> "заметки для себя"
                                                partnerOnline -> "в сети"
                                                else -> lastSeen ?: members
                                            }
                                    if (status != null) {
                                        Text(
                                            status,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (active) cs.primary else cs.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            if (isDialog && !isSaved && !isService && otherUserId != null && otherUserId >= 0) {
                                IconButton(onClick = { onStartCall(otherUserId, false) }) {
                                    Icon(AppIcons.Call, contentDescription = "Аудиозвонок")
                                }
                                IconButton(onClick = { onStartCall(otherUserId, true) }) {
                                    Icon(AppIcons.Video, contentDescription = "Видеозвонок")
                                }
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(AppIcons.More, contentDescription = "Меню")
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Поиск") },
                                        leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                                        onClick = {
                                            menuOpen = false
                                            searchOpen = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (chat?.muted == true) "Включить уведомления" else "Отключить уведомления") },
                                        leadingIcon = {
                                            Icon(
                                                if (chat?.muted ==
                                                    true
                                                ) {
                                                    AppIcons.Unmuted
                                                } else {
                                                    AppIcons.Muted
                                                },
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            menuOpen = false
                                            onToggleMute()
                                        },
                                    )
                                    if (chat?.isChannel != true) {
                                        DropdownMenuItem(
                                            text = { Text("Стереть переписку", color = cs.error) },
                                            leadingIcon = { Icon(AppIcons.Clean, contentDescription = null, tint = cs.error) },
                                            onClick = {
                                                menuOpen = false
                                                confirmClear = true
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(if (isDialog) "Удалить чат" else "Выйти из группы", color = cs.error) },
                                        leadingIcon = {
                                            Icon(
                                                if (isDialog) AppIcons.Delete else AppIcons.Logout,
                                                contentDescription = null,
                                                tint = cs.error,
                                            )
                                        },
                                        onClick = {
                                            menuOpen = false
                                            confirmAction = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                    chat?.pinnedMessage?.takeIf { !selecting && !searchOpen }?.let { pinned ->
                        PinnedBar(pinned) { if (pinned.id?.let(jumpTo) != true) showPinned = true }
                    }
                }
            }
        },
        bottomBar = {
            // With enableEdgeToEdge + adjustResize (see AndroidManifest) the keyboard
            // arrives purely as the `ime` inset (no legacy window resize, which once
            // double-counted into a huge gap). On this device the `ime` inset is
            // measured to the top of the nav bar, i.e. it EXCLUDES the nav-bar strip —
            // so we sum the two paddings: ime + navigationBars = full keyboard height
            // while typing, and just the nav bar when the keyboard is hidden (ime = 0).
            Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
                // A read-only service chat: nothing to write and nothing to say about it.
                if (chat != null && !chat.canWrite && chat.isDialog) return@Column
                // Read-only chats (channels we don't run): no composer, just a note.
                if (chat != null && !chat.canWrite) {
                    // A channel we only read gets the one action that makes sense there.
                    Text(
                        when {
                            !chat.isChannel -> "Писать в этот чат могут только администраторы"
                            chat.muted -> "Включить уведомления"
                            else -> "Отключить уведомления"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (chat.isChannel) cs.primary else cs.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(Spacing.s)
                                .clip(CircleShape)
                                .background(incomingBubbleColor())
                                .then(if (chat.isChannel) Modifier.clickable(onClick = onToggleMute) else Modifier)
                                .padding(horizontal = Spacing.l, vertical = Spacing.l),
                    )
                    return@Column
                }
                // Sends whatever the composer holds: an edit, staged attachments or plain text.
                val submit = {
                    val target = editing
                    when {
                        target != null -> {
                            onEditMessage(target, field.text)
                            editing = null
                            setDraftText(draftBeforeEdit, spansBeforeEdit)
                        }
                        pending.isNotEmpty() -> {
                            onSendMedia(pending, field.text)
                            pending = emptyList()
                            setDraftText("")
                            onDraftChange("")
                        }
                        else -> {
                            onSend(field.text, draftSpans)
                            setDraftText("")
                            onDraftChange("")
                        }
                    }
                }
                val canSend = recording || ((field.text.isNotBlank() || pending.isNotEmpty()) && !sendingAttachment)
                // Nothing to send yet: the round button records a voice message instead.
                val micMode = voiceRecordingSupported && !recording && !canSend && !sendingAttachment
                Row(
                    Modifier.fillMaxWidth().padding(start = Spacing.s, end = Spacing.s, top = Spacing.xs, bottom = Spacing.s),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    // The input pill: reply/edit strip and staged attachments ride on top of the field.
                    Surface(
                        // Half the single-line height (44 + 2 × 4), so one line is a full pill.
                        shape = RoundedCornerShape(26.dp),
                        color = incomingBubbleColor(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column {
                            if (editing != null) {
                                EditBanner(editing!!.text) {
                                    editing = null
                                    setDraftText(draftBeforeEdit, spansBeforeEdit)
                                }
                            } else if (replyingTo != null) {
                                ReplyBanner(replyingTo, contacts, myId, onCancelReply)
                            }
                            if (pending.isNotEmpty()) {
                                StagedAttachments(pending, onRemove = { item -> pending = pending - item })
                            }
                            Row(
                                Modifier.padding(Spacing.xs).heightIn(min = 44.dp),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                if (recording) {
                                    IconButton(
                                        onClick = {
                                            recording = false
                                            recorder.cancel()
                                        },
                                        modifier = Modifier.size(44.dp),
                                    ) {
                                        Icon(
                                            AppIcons.Delete,
                                            contentDescription = "Отменить запись",
                                            tint = cs.error,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                    Row(
                                        Modifier.weight(1f).height(44.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                                    ) {
                                        RecordingDot()
                                        Text(
                                            "${recordedSeconds / 60}:${(recordedSeconds % 60).toString().padStart(2, '0')}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = cs.onSurface,
                                        )
                                        Text(
                                            "Идёт запись…",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = cs.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    if (editing == null) {
                                        IconButton(
                                            onClick = {
                                                onOpenStickers()
                                                stickerPanel = true
                                            },
                                            modifier = Modifier.size(44.dp),
                                        ) {
                                            Icon(
                                                AppIcons.Emoji,
                                                contentDescription = "Стикеры",
                                                tint = cs.onSurfaceVariant,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                    }
                                    IconButton(onClick = { attachMenu = true }, modifier = Modifier.size(44.dp)) {
                                        Icon(
                                            AppIcons.Attach,
                                            contentDescription = "Прикрепить",
                                            tint = cs.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                    BasicTextField(
                                        value = field,
                                        onValueChange = { v ->
                                            val old = field.text
                                            // "Отправка по вводу": a line break typed at the end sends.
                                            if (sendOnEnter &&
                                                v.text.length == old.length + 1 &&
                                                v.text.endsWith('\n') &&
                                                old.isNotBlank()
                                            ) {
                                                submit()
                                                return@BasicTextField
                                            }
                                            if (v.text != old) draftSpans = remapSpans(old, v.text, draftSpans)
                                            field = v
                                            // While editing, the input holds the message being
                                            // edited — that must not overwrite the chat's draft.
                                            if (editing == null && v.text != old) onDraftChange(v.text)
                                        },
                                        visualTransformation = spansShown(draftSpans),
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .padding(horizontal = 2.dp)
                                                .focusRequester(composerFocus),
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                                        cursorBrush = SolidColor(cs.primary),
                                        // Auto-capitalize sentences, like other messengers.
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                        maxLines = 6,
                                        decorationBox = { inner ->
                                            // As tall as the icon buttons, so a single line sits on their centre line.
                                            Box(
                                                Modifier.heightIn(min = 44.dp).padding(vertical = Spacing.s),
                                                contentAlignment = Alignment.CenterStart,
                                            ) {
                                                if (field.text.isEmpty()) {
                                                    Text(
                                                        if (pending.isNotEmpty()) "Подпись…" else "Сообщение",
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color = cs.onSurfaceVariant,
                                                        maxLines = 1,
                                                    )
                                                }
                                                inner()
                                            }
                                        },
                                    )
                                    // Round video messages share the "nothing typed yet" state with the mic.
                                    if (micMode) {
                                        IconButton(onClick = { recordVideoNote() }, modifier = Modifier.size(44.dp)) {
                                            Icon(
                                                AppIcons.VideoNote,
                                                contentDescription = "Записать видеосообщение",
                                                tint = cs.onSurfaceVariant,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                    } else {
                                        Spacer(Modifier.width(Spacing.m))
                                    }
                                }
                            }
                            // Text selected in a new message: a small bar to format it, under the field, where
                            // the system's own selection toolbar (drawn above the selection) does not go.
                            if (!field.selection.collapsed && editing == null && pending.isEmpty() && !recording) {
                                FormatBar { type ->
                                    draftSpans = draftSpans.toggled(type, field.selection.min, field.selection.length)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(Spacing.s))
                    // The round action button: mic when idle, otherwise send.
                    val enabled = micMode || canSend
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (enabled) cs.primary else incomingBubbleColor())
                            .clickable(enabled = enabled) {
                                if (micMode) {
                                    recording = true
                                    recorder.start()
                                    return@clickable
                                }
                                if (recording) {
                                    // Hands the take to onRecorded, which sends it.
                                    recorder.stop()
                                    return@clickable
                                }
                                submit()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (sendingAttachment) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = cs.onSurfaceVariant)
                        } else {
                            AnimatedContent(
                                targetState =
                                    if (micMode) {
                                        0
                                    } else if (editing != null) {
                                        2
                                    } else {
                                        1
                                    },
                                transitionSpec = {
                                    (androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn()) togetherWith
                                        (androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut())
                                },
                                label = "action",
                            ) { mode ->
                                Icon(
                                    when (mode) {
                                        0 -> AppIcons.Mic
                                        2 -> AppIcons.Delivered
                                        else -> AppIcons.Send
                                    },
                                    contentDescription =
                                        when (mode) {
                                            0 -> "Записать голосовое сообщение"
                                            2 -> "Сохранить"
                                            else -> "Отправить"
                                        },
                                    tint = if (enabled) cs.onPrimary else cs.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            // Bubbles may span most of the screen, as messengers do; a fixed cap left a wide
            // empty margin on large or scaled-down screens and wrapped text early.
            val bubbleMax = maxWidth * 0.82f
            if (busy && messages.isEmpty()) {
                CenteredSpinner()
            }
            if (loadingOlder) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.TopCenter).padding(Spacing.s).size(24.dp),
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
                            .padding(Spacing.s),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(vertical = Spacing.s),
            ) {
                itemsIndexed(messages, key = { _, m -> m.id ?: m.cid ?: m.time }) { index, msg ->
                    // New bubbles fade in; placement stays instant so prepending history never slides the view.
                    Column(Modifier.animateItem(placementSpec = null)) {
                        // A day chip at every date change, as the official client does; the
                        // floating chip above still tracks the day while scrolling.
                        val prevAny = messages.getOrNull(index - 1)
                        if (prevAny == null || !sameDay(prevAny.time, msg.time)) ServiceMessageChip(formatDay(msg.time))
                        if (index == firstUnread) NewMessagesDivider()
                        // Group service events ("X joined", "X added Y") render as a centered chip.
                        val service = msg.service
                        if (service != null) {
                            ServiceMessageChip(serviceMessageText(service, contacts, myId))
                            return@Column
                        }
                        val isMine = msg.senderId == myId
                        val prev = messages.getOrNull(index - 1)
                        // Show the avatar only on the first message of a run from one sender.
                        val startsRun = prev == null || prev.senderId != msg.senderId
                        MessageRow(
                            msg = msg,
                            isMine = isMine,
                            maxWidth = bubbleMax,
                            senderName = contacts[msg.senderId] ?: "—",
                            senderAvatar = senderAvatars[msg.senderId],
                            isGroup = !isDialog,
                            startsRun = startsRun,
                            highlighted = index == matchIndex || (msg.id != null && msg.id == flashId),
                            selecting = selecting,
                            selected = msg.id != null && msg.id in selected,
                            searchQuery = searchQuery.trim().takeIf { searchOpen && it.length >= 2 },
                            onLongClick = { msg.id?.let { id -> selected = if (id in selected) selected - id else selected + id } },
                            onDoubleClick =
                                quickReaction?.takeIf { !selecting && msg.id != null }?.let { emoji -> { onReact(msg, emoji) } },
                            onReplyClick = msg.replyTo?.messageId?.let { id -> { jumpTo(id) } },
                            replyAuthor =
                                msg.replyTo?.let { if (it.senderId == myId) "Вы" else contacts[it.senderId] ?: "—" },
                            // Read as «Переслано от …», so ours is «вас», not «Вы».
                            forwardAuthor =
                                msg.forwardedFrom?.let { if (it == myId) "вас" else contacts[it] ?: "—" },
                            onForwardClick = msg.forwardedFrom?.let { id -> { onOpenUser(id) } },
                            onMediaClick = onMediaClick,
                            onAvatarClick = { onOpenUser(msg.senderId) },
                            onClick = {
                                val id = msg.id
                                if (selecting &&
                                    id != null
                                ) {
                                    selected = if (id in selected) selected - id else selected + id
                                } else {
                                    menuTarget = msg
                                }
                            },
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
                            pressingButtons = pressingButtons,
                            onBotButton = onBotButton,
                            onVote = { onVote(msg, it) },
                            onWriteTo = onWriteTo,
                        )
                    }
                }
            }

            // Floating date chip (top-center): the day of the top-most visible message,
            // shown only while scrolling and fading out when the list settles.
            val topTime by remember { derivedStateOf { messages.getOrNull(listState.firstVisibleItemIndex)?.time } }
            androidx.compose.animation.AnimatedVisibility(
                visible = listState.isScrollInProgress && topTime != null,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = Spacing.s),
            ) {
                Surface(shape = CircleShape, color = incomingBubbleColor()) {
                    Text(
                        topTime?.let { formatDay(it) } ?: "",
                        modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }

            // Jump-to-latest button (bottom-right), shown only when scrolled up off the newest.
            val scrollScope = rememberCoroutineScope()
            // Only while the newest bubble is out of sight, so the button never covers its time and ticks.
            val notAtBottom by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    (info.visibleItemsInfo.lastOrNull()?.index ?: 0) < info.totalItemsCount - 1
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = notAtBottom,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = Spacing.m, bottom = Spacing.m),
            ) {
                Surface(
                    onClick = {
                        scrollScope.launch { listState.animateScrollToItem(messages.lastIndex.coerceAtLeast(0), END_OFFSET) }
                    },
                    shape = CircleShape,
                    color = incomingBubbleColor(),
                    // Just enough to lift it off a bubble of the same tone passing underneath.
                    shadowElevation = 2.dp,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            AppIcons.ArrowDown,
                            contentDescription = "К последним сообщениям",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp),
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
            // Only plain text: what MSG_EDIT does to attachments it isn't sent was never measured.
            canEdit =
                target.senderId == myId &&
                    target.service == null &&
                    target.media.isEmpty() &&
                    target.files.isEmpty() &&
                    target.poll == null &&
                    target.voice == null &&
                    target.stickerUrl == null &&
                    target.location == null &&
                    target.contact == null &&
                    target.buttons.isEmpty() &&
                    target.pending.isEmpty(),
            isMine = target.senderId == myId,
            // Read receipts are only meaningful where several people can read one
            // message: groups and channels, never a 1:1 dialog.
            canShowReadBy = target.senderId == myId && !isDialog,
            pinned = target.id != null && target.id == chat?.pinnedMessage?.id,
            onPin =
                { pin: Boolean ->
                    if (pin) pinTarget = target else onPin(target, false, false)
                    menuTarget = null
                }.takeIf { target.id != null && target.service == null && chat?.canPinMessages(myId) == true },
            readByCount = if (readMarks.isEmpty()) null else readMarks.count { it.value >= target.time },
            quick = reactionsMenu.ifEmpty { QuickReactions },
            onSelect =
                target.id?.let { id ->
                    {
                        selected = selected + id
                        menuTarget = null
                    }
                },
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
                draftBeforeEdit = field.text
                spansBeforeEdit = draftSpans
                editing = target
                // With its formatting, which the save keeps (the server's runs, moved along with the edits).
                setDraftText(target.text, target.spans)
                menuTarget = null
            },
            onDelete = {
                deleteTargets = listOf(target)
                menuTarget = null
            },
            onShowReadBy = {
                onShowReadBy(target)
                menuTarget = null
            },
            onShowReactions =
                {
                    onShowReactions(target)
                    menuTarget = null
                }.takeIf { target.id != null && target.reactions.isNotEmpty() },
        )
    }

    if (reactionsMessageId != null) {
        ReactionsSheet(reactedBy, myId, readerInfo, onDismiss = onCloseReactions) {
            onCloseReactions()
            onOpenUser(it)
        }
    }

    // "Who read this message" sheet (groups/channels).
    val readingMessage = messages.firstOrNull { it.id != null && it.id == readingMessageId }
    if (readingMessage != null) {
        // Readers are those whose mark reached this message; newest reader first.
        val readers =
            readMarks
                .filterValues { it >= readingMessage.time }
                .entries
                .sortedByDescending { it.value }
                .map { readerInfo(it.key) }
        ReadBySheet(
            readers = readers,
            totalMembers = chat?.participantsCount ?: 0,
            loading = readMarksLoading,
            onDismiss = onCloseReadBy,
            onOpenUser = {
                onCloseReadBy()
                onOpenUser(it)
            },
        )
    }

    // The pinned message in full, when it is too old to jump to.
    chat?.pinnedMessage?.takeIf { showPinned }?.let { pinned ->
        AlertDialog(
            onDismissRequest = { showPinned = false },
            title = { Text("Закреплённое сообщение") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LinkedText(
                        pinned.text.ifBlank { pinned.previewLabel() },
                        MaterialTheme.typography.bodyLarge,
                        MaterialTheme.colorScheme.onSurface,
                        onLinkClick,
                        spans = pinned.spans,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showPinned = false }) { Text("Закрыть") } },
        )
    }

    // Delete confirmation, the same for one message and a selection: "for everyone" only when all are ours.
    if (deleteTargets.isNotEmpty()) {
        val targets = deleteTargets
        val delete = { forAll: Boolean ->
            onDeleteMany(targets, forAll)
            deleteTargets = emptyList()
            selected = emptySet()
        }
        AppSheet(onDismiss = { deleteTargets = emptyList() }) {
            Text(
                if (targets.size == 1) {
                    "Удалить сообщение?"
                } else {
                    "Удалить ${targets.size} ${plural(targets.size, "сообщение", "сообщения", "сообщений")}?"
                },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.s),
            )
            if (targets.all { it.senderId == myId }) {
                SheetAction(AppIcons.Delete, "Удалить у всех", danger = true) { delete(true) }
            }
            SheetAction(AppIcons.Delete, "Удалить у меня", danger = true) { delete(false) }
            SheetAction(AppIcons.Close, "Отмена") { deleteTargets = emptyList() }
            Spacer(Modifier.height(Spacing.m))
        }
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    msg: Message,
    isMine: Boolean,
    /** Widest a bubble may grow (a share of the chat's width). */
    maxWidth: Dp,
    senderName: String,
    senderAvatar: String?,
    isGroup: Boolean,
    startsRun: Boolean,
    /** The current in-chat search hit: the row is tinted so it stands out. */
    highlighted: Boolean = false,
    /** Multi-select is on: every row shows its pick mark. */
    selecting: Boolean = false,
    selected: Boolean = false,
    /** The in-chat search query, marked inside the text. */
    searchQuery: String? = null,
    onLongClick: () -> Unit = {},
    /** Double tap (quick reaction); null keeps single taps instant. */
    onDoubleClick: (() -> Unit)? = null,
    /** Jumps to the quoted message; null when there is nothing to jump to. */
    onReplyClick: (() -> Unit)? = null,
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
    pressingButtons: Set<BotButton> = emptySet(),
    onBotButton: (BotButton) -> Unit = {},
    onVote: (Set<Int>) -> Unit = {},
    onWriteTo: (UserInfo) -> Unit = {},
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

    val tint by animateColorAsState(
        if (highlighted || selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
        label = "hit",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = if (startsRun) Spacing.s else 0.dp)
            .clip(RoundedCornerShape(Radius.small))
            .background(tint),
    ) {
        if (selecting) {
            // The pick mark sits on the side the bubble doesn't.
            Box(
                Modifier
                    .align(if (isMine) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = Spacing.m)
                    .size(22.dp)
                    .clip(CircleShape)
                    .then(
                        if (selected) {
                            Modifier.background(MaterialTheme.colorScheme.primary)
                        } else {
                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        AppIcons.Delivered,
                        contentDescription = "Выбрано",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        val progress = (-offsetX.value / triggerPx).coerceIn(0f, 1f)
        Icon(
            AppIcons.Reply,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = Spacing.l)
                    .size(24.dp)
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
                Spacer(Modifier.width(Spacing.s))
            }
            // A message that is nothing but a round video gets no bubble: the circle
            // floats on the conversation, the way other messengers draw them.
            val bare =
                msg.text.isBlank() &&
                    msg.voice == null &&
                    msg.files.isEmpty() &&
                    msg.linkPreview == null &&
                    msg.replyTo == null &&
                    msg.pending.isEmpty() &&
                    msg.media.size == 1 &&
                    msg.media.single().isVideoNote ||
                    // …and so does a sticker.
                    (msg.stickerUrl != null && msg.text.isBlank() && msg.media.isEmpty() && msg.replyTo == null)
            // Own bubbles are a flat accent; incoming ones sit on a neutral raised tone.
            val bubbleColor =
                when {
                    bare -> Color.Transparent
                    isMine -> outgoingBubbleColor()
                    else -> incomingBubbleColor()
                }
            // Without a bubble behind it, the footer sits on the chat background, so it
            // can't use the on-bubble colour.
            val fg =
                when {
                    bare -> MaterialTheme.colorScheme.onSurfaceVariant
                    isMine -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            // Messages in a run hug each other: the sender-side corners are tight, the first
            // bubble of a run keeps a round top on that side.
            val big = 18.dp
            val tight = 6.dp
            val bubbleShape =
                if (isMine) {
                    RoundedCornerShape(topStart = big, topEnd = if (startsRun) big else tight, bottomEnd = tight, bottomStart = big)
                } else {
                    RoundedCornerShape(topStart = if (startsRun) big else tight, topEnd = big, bottomEnd = big, bottomStart = tight)
                }
            // Photos and videos run almost edge to edge; text keeps a comfortable inset.
            val mediaBubble = !bare && (msg.media.isNotEmpty() || msg.pending.isNotEmpty())
            val inset = if (mediaBubble) Modifier.padding(horizontal = Spacing.s) else Modifier
            // A bare sticker or circle has no bubble padding: its name and «Переслано от» lines get the text
            // bubble's inset, or the clipped corner cuts them.
            val headerInset = if (bare) Modifier.padding(horizontal = Spacing.m) else inset
            Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                Box(
                    Modifier
                        .widthIn(max = maxWidth)
                        .clip(bubbleShape)
                        .background(bubbleColor)
                        .combinedClickable(onClick = onClick, onLongClick = onLongClick, onDoubleClick = onDoubleClick)
                        .padding(
                            horizontal =
                                if (bare) {
                                    0.dp
                                } else if (mediaBubble) {
                                    Spacing.xs
                                } else {
                                    Spacing.m
                                },
                            vertical =
                                if (bare) {
                                    0.dp
                                } else if (mediaBubble) {
                                    Spacing.xs
                                } else {
                                    Spacing.s
                                },
                        ),
                ) {
                    // A media bubble wraps its 260dp media: captions, buttons and the footer wrap to it too.
                    Column(if (mediaBubble) Modifier.widthIn(max = 260.dp) else Modifier) {
                        if (isGroup && !isMine && startsRun) {
                            Text(
                                senderName,
                                style = MaterialTheme.typography.labelLarge,
                                color = senderNameColor(senderName),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = headerInset,
                            )
                        }
                        if (forwardAuthor != null) {
                            Text(
                                "Переслано от $forwardAuthor",
                                style = MaterialTheme.typography.labelMedium,
                                color = fg.copy(alpha = if (onForwardClick != null) 0.95f else 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier =
                                    if (onForwardClick != null) {
                                        headerInset.clickable(onClick = onForwardClick)
                                    } else {
                                        headerInset
                                    },
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        msg.replyTo?.let { reply ->
                            Box(
                                inset.then(
                                    if (onReplyClick !=
                                        null
                                    ) {
                                        Modifier.clip(RoundedCornerShape(Radius.small)).clickable(onClick = onReplyClick)
                                    } else {
                                        Modifier
                                    },
                                ),
                            ) { ReplyQuote(replyAuthor ?: "—", reply.text, fg) }
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
                                    Spacer(Modifier.width(Spacing.m))
                                }
                                // Always available: an upload in flight can be abandoned,
                                // and a failed one has to be dismissable.
                                PendingAction(AppIcons.Close, "Отменить", fg, onDiscard)
                            }
                            Spacer(Modifier.height(2.dp))
                        }
                        msg.stickerUrl?.let { sticker ->
                            AsyncImage(model = sticker, contentDescription = "Стикер", modifier = Modifier.size(STICKER_SIZE))
                            Spacer(Modifier.height(4.dp))
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
                            Box(inset) {
                                VoiceMessageView(
                                    voice = voice,
                                    fg = fg,
                                    state = voiceState,
                                    onClick = onVoiceClick,
                                    onSeek = onVoiceSeek,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        msg.files.forEach { file ->
                            Box(inset) {
                                FileAttachView(
                                    file = file,
                                    fg = fg,
                                    downloaded = file.fileId in downloadedFiles,
                                    progress = downloadingFiles[file.fileId],
                                    onClick = { onDownloadFile(file) },
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        if (msg.text.isNotEmpty()) {
                            // Own bubbles are accent-tinted, so an accent link could vanish (issue #49):
                            // draw it in the bubble's text color, underlined.
                            Box(inset) {
                                LinkedText(
                                    msg.text,
                                    MessageTextStyle(),
                                    fg,
                                    onLinkClick,
                                    linkColor = if (isMine) fg else MaterialTheme.colorScheme.primary,
                                    spans = msg.spans,
                                    highlight = searchQuery,
                                )
                            }
                        }
                        msg.linkPreview?.let { preview -> Box(inset) { LinkPreviewCard(preview, fg) } }
                        msg.poll?.let { poll -> Box(inset) { PollView(poll, fg, onVote) } }
                        msg.location?.let { place -> Box(inset) { LocationCard(place, fg) } }
                        msg.contact?.let { card ->
                            Box(inset) { ContactCardView(card, fg, if (isMine) fg else MaterialTheme.colorScheme.primary, onWriteTo) }
                        }
                        // A bot's buttons: full width, one per line, as the official client lays them out.
                        msg.buttons.flatten().forEach { button ->
                            BotButtonView(
                                button,
                                fg,
                                busy = button in pressingButtons,
                                onPress = { onBotButton(button) },
                                modifier = inset.padding(top = Spacing.xs).fillMaxWidth(),
                            )
                        }
                        // Footer line inside the bubble: reactions then time + delivery
                        // receipt. The row is sized to its content (not the full bubble
                        // width) so a short message keeps a snug bubble instead of being
                        // stretched out by the meta row.
                        Spacer(Modifier.height(2.dp))
                        // Wraps instead of overflowing the bubble when there are many reactions;
                        // each line is end-aligned, so the time stays at the trailing edge.
                        FlowRow(
                            modifier = Modifier.align(Alignment.End).then(inset),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            msg.reactions.forEach { r -> ReactionChip(r, fg) { onReactionClick(r.emoji) } }
                            MessageMeta(msg, isMine, fg, Modifier.align(Alignment.CenterVertically))
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
    Box {
        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            items(items) { media ->
                Box(Modifier.size(56.dp)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(Radius.small))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
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
                            .size(20.dp)
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
            .padding(top = Spacing.xs)
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(Radius.small))
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
        Column(Modifier.padding(Spacing.s)) {
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
        Modifier.padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Box(
            Modifier
                .size(40.dp)
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
            style = MaterialTheme.typography.bodySmall,
            color = fg.copy(alpha = 0.7f),
            maxLines = 1,
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
internal fun formatVoiceDuration(seconds: Int): String {
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
            .clip(RoundedCornerShape(Radius.small))
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(fg.copy(alpha = 0.15f)),
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
                style = MaterialTheme.typography.bodySmall,
                color = fg.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }
    }
}

/** Human-readable byte size, e.g. "2.4 МБ". */
internal fun formatFileSize(bytes: Long): String =
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
            .clip(RoundedCornerShape(Radius.small))
            // Light text (own bubbles, dark theme) gets a darker well, not a grey veil it can't stand out on.
            .background(if (fg.luminance() > 0.5f) Color.Black.copy(alpha = 0.22f) else fg.copy(alpha = 0.12f))
            .height(IntrinsicSize.Min)
            .padding(end = Spacing.s),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(fg.copy(alpha = 0.7f)))
        Column(Modifier.padding(start = Spacing.s, top = Spacing.xs, bottom = Spacing.xs)) {
            Text(author, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = fg.copy(alpha = 0.9f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A poll in its bubble: question, answers (with a bar and share once we voted or it closed),
 * and the voter count. Tapping an answer votes; tapping ours again retracts where revoting is on.
 */
@Composable
private fun PollView(
    poll: com.avenarius.app.model.Poll,
    fg: Color,
    onVote: (Set<Int>) -> Unit,
) {
    val mine =
        poll.answers
            .filter { it.mine }
            .map { it.id }
            .toSet()
    // A multiple-choice poll collects ticks here and sends them once, with «Проголосовать».
    var picks by remember(poll) { mutableStateOf(mine) }
    val choosing = poll.multiple && poll.canChoose
    val results = (poll.voted || poll.closed) && !(choosing && picks != mine)
    Column(Modifier.widthIn(min = 220.dp).padding(vertical = 2.dp)) {
        Text(poll.title, style = MaterialTheme.typography.titleSmall.copy(lineBreak = LineBreak.Paragraph), color = fg)
        Text(
            when {
                poll.closed -> "Опрос завершён"
                else ->
                    listOfNotNull(
                        if (poll.anonymous) "Анонимный опрос" else "Опрос",
                        "несколько ответов".takeIf { poll.multiple },
                    ).joinToString(" · ")
            },
            style = MaterialTheme.typography.bodySmall,
            color = fg.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(Spacing.xs))
        poll.answers.forEach { a ->
            val share = if (poll.total > 0) a.votes * 100 / poll.total else 0
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.small))
                    .clickable(enabled = if (choosing) true else poll.votesAfterTapping(a.id) != null) {
                        if (choosing) {
                            picks = if (a.id in picks) picks - a.id else picks + a.id
                        } else {
                            poll.votesAfterTapping(a.id)?.let(onVote)
                        }
                    }.padding(vertical = Spacing.s),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (results) {
                        Text(
                            "$share%",
                            style = MaterialTheme.typography.labelLarge,
                            color = fg,
                            maxLines = 1,
                            modifier = Modifier.width(40.dp),
                        )
                    } else {
                        val shape = if (poll.multiple) RoundedCornerShape(4.dp) else CircleShape
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(shape)
                                .then(
                                    if (a.id in picks &&
                                        choosing
                                    ) {
                                        Modifier.background(fg)
                                    } else {
                                        Modifier.border(1.5.dp, fg.copy(alpha = 0.6f), shape)
                                    },
                                ),
                        )
                        Spacer(Modifier.width(Spacing.m))
                    }
                    Text(a.text, style = MaterialTheme.typography.bodyMedium, color = fg, modifier = Modifier.weight(1f))
                    if (a.mine) Icon(AppIcons.CheckCircle, contentDescription = "Ваш голос", tint = fg, modifier = Modifier.size(16.dp))
                }
                if (results) {
                    Box(
                        Modifier
                            .padding(top = Spacing.xs)
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(fg.copy(alpha = 0.15f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(
                                    share / 100f,
                                ).fillMaxHeight()
                                .clip(CircleShape)
                                .background(fg.copy(alpha = if (a.mine) 1f else 0.55f)),
                        )
                    }
                }
            }
        }
        if (choosing && picks != mine && (picks.isNotEmpty() || poll.voted)) {
            TextButton(onClick = { onVote(picks) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (picks.isEmpty()) "Отменить голос" else "Проголосовать", color = fg, maxLines = 1)
            }
        }
        Text(votesLabel(poll.total), style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.7f))
    }
}

/** A shared place: its coordinates; a tap opens it on a map (OpenStreetMap, which needs no app or account). */
@Composable
private fun LocationCard(
    place: com.avenarius.app.model.GeoPoint,
    fg: Color,
) {
    val uriHandler = LocalUriHandler.current

    fun short(v: Double) = ((v * 100_000).roundToLong() / 100_000.0).toString()
    val lat = short(place.latitude)
    val lon = short(place.longitude)
    Row(
        Modifier
            .clip(RoundedCornerShape(Radius.small))
            .background(fg.copy(alpha = 0.10f))
            .clickable { uriHandler.openUriSafely("https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=16/$lat/$lon") }
            .padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Location, contentDescription = null, tint = fg, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(Spacing.m))
        Column {
            Text("Местоположение", style = MaterialTheme.typography.titleSmall, color = fg)
            Text("$lat, $lon", style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.75f))
        }
        Spacer(Modifier.width(Spacing.s))
        Icon(AppIcons.Open, contentDescription = "Открыть на карте", tint = fg.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
    }
}

/** A shared contact: name and phone, «Написать» for a MAX user and «Скопировать номер». */
@Composable
private fun ContactCardView(
    card: com.avenarius.app.model.ContactCard,
    fg: Color,
    accent: Color,
    onWriteTo: (UserInfo) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier
            .widthIn(min = 200.dp)
            .clip(RoundedCornerShape(Radius.small))
            .background(fg.copy(alpha = 0.10f)),
    ) {
        Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Avatar(card.name, null, 40.dp)
            Spacer(Modifier.width(Spacing.m))
            Column {
                Text(card.name, style = MaterialTheme.typography.titleSmall, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                card.phone?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.75f)) }
            }
        }
        if (card.userId != 0L || card.phone != null) {
            HorizontalDivider(color = fg.copy(alpha = 0.12f))
            Row(Modifier.padding(horizontal = Spacing.xs)) {
                val pad = PaddingValues(horizontal = Spacing.s)
                if (card.userId != 0L) {
                    TextButton(onClick = { onWriteTo(UserInfo(card.userId, card.name)) }, contentPadding = pad) {
                        Text("Написать", color = accent, maxLines = 1)
                    }
                }
                card.phone?.let { phone ->
                    TextButton(onClick = { clipboard.setText(AnnotatedString(phone)) }, contentPadding = pad) {
                        Text("Скопировать номер", color = accent, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** "Нет голосов" / "1 голос" / "3 голоса" / "5 голосов". */
private fun votesLabel(n: Int): String {
    if (n == 0) return "Нет голосов"
    return "$n ${plural(n, "голос", "голоса", "голосов")}"
}

/** A view count the way the official client prints it: 999, 1,3K, 12K, 1,2M. */
internal fun formatViews(n: Int): String {
    fun short(
        value: Int,
        unit: Int,
        suffix: String,
    ): String {
        val tenths = value / (unit / 10)
        return if (tenths >= 100 || tenths % 10 == 0) "${tenths / 10}$suffix" else "${tenths / 10},${tenths % 10}$suffix"
    }
    return when {
        n < 1_000 -> n.toString()
        n < 1_000_000 -> short(n, 1_000, "K")
        else -> short(n, 1_000_000, "M")
    }
}

/** Time + delivery receipt, shown at the trailing edge of a message bubble. */
@Composable
private fun MessageMeta(
    msg: Message,
    isMine: Boolean,
    fg: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (msg.views > 0) {
            Icon(AppIcons.Visibility, contentDescription = "Просмотры", tint = fg.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
            Text(formatViews(msg.views), style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.7f), maxLines = 1)
        }
        if (msg.edited) Text("изм.", style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.7f), maxLines = 1)
        Text(formatClock(msg.time), style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.7f), maxLines = 1)
        if (isMine) {
            val read = msg.status == MessageStatus.READ
            Icon(
                if (read) AppIcons.Read else AppIcons.Delivered,
                contentDescription = if (read) "Прочитано" else "Доставлено",
                // Own bubbles are accent-filled, so "read" is the full-strength glyph, not an accent one.
                tint =
                    if (read && !isMine) {
                        MaterialTheme.colorScheme.primary
                    } else if (read) {
                        fg
                    } else {
                        fg.copy(alpha = 0.6f)
                    },
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** One tappable reaction chip; its background derives from the bubble's [fg], denser for our own. */
@Composable
private fun ReactionChip(
    r: com.avenarius.app.model.Reaction,
    fg: Color,
    onClick: () -> Unit,
) {
    val chipBg = if (r.mine) fg.copy(alpha = 0.30f) else fg.copy(alpha = 0.12f)
    Row(
        Modifier
            .clip(CircleShape)
            .background(chipBg)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.s, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(r.emoji, style = MaterialTheme.typography.bodyMedium)
        if (r.count > 1) Text("${r.count}", style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

/** Strip at the top of the input pill showing which message is being replied to. */
@Composable
private fun ReplyBanner(
    msg: Message,
    contacts: Map<Long, String>,
    myId: Long,
    onCancel: () -> Unit,
) {
    ComposerBanner(
        AppIcons.Reply,
        if (msg.senderId == myId) "Вы" else contacts[msg.senderId] ?: "Сообщение",
        msg.previewLabel().ifBlank { "Вложение" },
        onCancel,
    )
}

/** Strip at the top of the input pill while editing a message. */
@Composable
private fun EditBanner(
    text: String,
    onCancel: () -> Unit,
) {
    ComposerBanner(AppIcons.Edit, "Редактирование", text.ifBlank { "Сообщение" }, onCancel)
}

@Composable
private fun ComposerBanner(
    icon: Painter,
    title: String,
    text: String,
    onCancel: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.xs, top = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.m))
        Box(
            Modifier
                .width(3.dp)
                .height(32.dp)
                .clip(CircleShape)
                .background(cs.primary),
        )
        Spacer(Modifier.width(Spacing.s))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = cs.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onCancel, modifier = Modifier.size(40.dp)) {
            Icon(AppIcons.Close, contentDescription = "Отменить", modifier = Modifier.size(20.dp))
        }
    }
}

private val QuickReactions = listOf("❤️", "👍", "🤣", "😱", "🥰", "🔥", "😢")

private val MoreReactions = listOf("👏", "😄", "😘", "🙏", "💯", "🎉", "😡", "🤔", "👎", "🤝", "😍", "🤯", "😭", "👀", "✅", "💔")

/**
 * Floating context menu shown when a message is tapped: an (expandable) reactions
 * bar on top, then the actions. It springs in over a dimmed conversation; tapping
 * the backdrop dismisses it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageContextMenu(
    message: Message,
    canEdit: Boolean,
    isMine: Boolean,
    /** Whether this message can show a "who read" entry (our message in a group). */
    canShowReadBy: Boolean = false,
    /** How many members have read it; null when unknown (no read data yet). */
    readByCount: Int? = null,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShowReadBy: () -> Unit = {},
    /** The reactions offered first (the server's menu, or the built-in set). */
    quick: List<String> = QuickReactions,
    /** Starts multi-select with this message; null for a message that can't be picked. */
    onSelect: (() -> Unit)? = null,
    /** This message is the chat's pinned one (the item then unpins). */
    pinned: Boolean = false,
    /** Pins (true) / unpins (false); null where we may not pin. */
    onPin: ((Boolean) -> Unit)? = null,
    /** «Реакции»: who reacted; null when nobody has. */
    onShowReactions: (() -> Unit)? = null,
) {
    val clipboard = LocalClipboardManager.current
    var expanded by remember { mutableStateOf(false) }
    // Drop input focus so the soft keyboard closes — otherwise the menu (centered
    // over the whole screen) would sit behind the keyboard.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focusManager.clearFocus() }
    PlatformBackHandler(enabled = true, onBack = onDismiss)
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) }
    val cs = MaterialTheme.colorScheme
    val mineEmoji = message.reactions.firstOrNull { it.mine }?.emoji
    Box(
        Modifier
            .fillMaxSize()
            .background(cs.scrim.copy(alpha = 0.55f * appear.value.coerceIn(0f, 1f)))
            .clickable(interactionSource = null, indication = null, onClick = onDismiss)
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .graphicsLayer {
                    val v = appear.value
                    alpha = v.coerceIn(0f, 1f)
                    scaleX = 0.86f + 0.14f * v
                    scaleY = 0.86f + 0.14f * v
                }.verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l, vertical = Spacing.xl),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
        ) {
            // Reactions bar (expandable): a full pill while it is one row.
            Surface(shape = RoundedCornerShape(REACTION_PICK / 2 + Spacing.xs), color = cs.surfaceContainerHigh) {
                Column(Modifier.padding(Spacing.xs).widthIn(max = REACTION_PICK * 8)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        quick.take(7).forEach { emoji -> ReactionPick(emoji, emoji == mineEmoji) { onReact(emoji) } }
                        Box(
                            Modifier
                                .size(
                                    REACTION_PICK,
                                ).clip(CircleShape)
                                .background(cs.surfaceContainerHighest)
                                .clickable { expanded = !expanded },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                AppIcons.ArrowDown,
                                contentDescription = if (expanded) "Свернуть" else "Больше реакций",
                                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                            )
                        }
                    }
                    AnimatedVisibility(expanded) {
                        FlowRow {
                            (quick.drop(7) + MoreReactions.filter { it !in quick }).forEach { emoji ->
                                ReactionPick(emoji, emoji == mineEmoji) { onReact(emoji) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.m))
            // What the menu is about: a short quote of the message, in its bubble's colours.
            Surface(
                shape = RoundedCornerShape(Radius.medium),
                color = if (isMine) outgoingBubbleColor() else incomingBubbleColor(),
                contentColor = if (isMine) cs.onPrimary else cs.onSurface,
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Text(
                    message.previewLabel().ifBlank { "Сообщение" },
                    style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Paragraph),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
                )
            }
            Spacer(Modifier.height(Spacing.m))
            // Action items.
            Surface(shape = RoundedCornerShape(Radius.medium), color = cs.surfaceContainerHigh) {
                Column(Modifier.width(250.dp).padding(vertical = Spacing.xs)) {
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
                    onPin?.let { pin ->
                        if (pinned) {
                            ContextMenuItem(AppIcons.Unpin, "Открепить") { pin(false) }
                        } else {
                            ContextMenuItem(AppIcons.Pin, "Закрепить") { pin(true) }
                        }
                    }
                    onSelect?.let { ContextMenuItem(AppIcons.CheckCircle, "Выбрать", onClick = it) }
                    onShowReactions?.let { ContextMenuItem(AppIcons.Heart, "Реакции", onClick = it) }
                    if (canShowReadBy) {
                        val label = if (readByCount == null || readByCount == 0) "Кто прочитал" else "Кто прочитал ($readByCount)"
                        ContextMenuItem(AppIcons.Read, label, onClick = onShowReadBy)
                    }
                    if (isMine || message.service == null) {
                        ContextMenuItem(AppIcons.Delete, "Удалить", danger = true, onClick = onDelete)
                    }
                }
            }
        }
    }
}

// Eight of these (seven emoji and the chevron) fit the bar on a 360dp-wide screen.
private val REACTION_PICK = 38.dp

/** «Реакции»: everyone who reacted to a message, grouped by emoji; rows open the profile. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReactionsSheet(
    byEmoji: Map<String, List<Long>>?,
    myId: Long,
    userInfo: (Long) -> UserInfo,
    onDismiss: () -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    AppSheet(onDismiss) {
        Text("Реакции", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = Spacing.xl, bottom = Spacing.s))
        if (byEmoji == null) {
            Box(
                Modifier.fillMaxWidth().padding(Spacing.xl),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(Modifier.size(28.dp)) }
        } else {
            LazyColumn(Modifier.heightIn(max = 480.dp), contentPadding = PaddingValues(bottom = Spacing.l)) {
                byEmoji.entries.sortedByDescending { it.value.size }.forEach { (emoji, users) ->
                    item(key = "e$emoji") {
                        Text(
                            "$emoji  ${users.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.primary,
                            modifier = Modifier.padding(start = Spacing.xl, top = Spacing.m, bottom = Spacing.xs),
                        )
                    }
                    items(users, key = { "e$emoji:$it" }) { id ->
                        val info = userInfo(id)
                        val name = if (id == myId) "Вы" else info.name
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenUser(id) }.padding(horizontal = Spacing.xl, vertical = Spacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(name, info.avatarUrl, 40.dp)
                            Spacer(Modifier.width(Spacing.l))
                            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** One emoji in the reaction picker; ours is ringed. */
@Composable
private fun ReactionPick(
    emoji: String,
    mine: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(REACTION_PICK)
            .clip(CircleShape)
            .then(if (mine) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
    }
}

/**
 * "Who read this message" list, shown over the conversation like the context menu
 * (same scrim + back handling). Rows open the reader's profile.
 */
@Composable
private fun ReadBySheet(
    readers: List<UserInfo>,
    totalMembers: Int,
    loading: Boolean,
    onDismiss: () -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focusManager.clearFocus() }
    PlatformBackHandler(enabled = true, onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            // Swallow taps so a tap inside the card doesn't dismiss it.
            modifier = Modifier.fillMaxWidth().padding(Spacing.xxl).clickable(enabled = false) {},
            shape = RoundedCornerShape(Radius.medium),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(vertical = Spacing.m)) {
                Text(
                    "Кто прочитал",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xs),
                )
                Text(
                    if (totalMembers > 0) "Прочитали ${readers.size} из $totalMembers" else "Прочитали ${readers.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.l),
                )
                Spacer(Modifier.height(Spacing.s))
                when {
                    loading && readers.isEmpty() ->
                        Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp))
                        }
                    readers.isEmpty() ->
                        Text(
                            "Пока никто не прочитал",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
                        )
                    else -> {
                        HorizontalDivider()
                        LazyColumn(Modifier.heightIn(max = 360.dp)) {
                            items(readers, key = { it.id }) { reader ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenUser(reader.id) }
                                        .padding(horizontal = Spacing.l, vertical = Spacing.s),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Avatar(reader.name, reader.avatarUrl, 40.dp)
                                    Spacer(Modifier.width(Spacing.m))
                                    Text(
                                        reader.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
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

@Composable
private fun ContextMenuItem(
    icon: Painter,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (danger) color else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The header while searching inside a chat: the field, the hit counter and older/newer steps. */
@Composable
private fun ChatSearchBar(
    query: String,
    onQuery: (String) -> Unit,
    position: String?,
    nothingFound: Boolean,
    /** History is being paged back to a hit. */
    loading: Boolean = false,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
    onClose: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Opened from the ⋮ popup: focus taken while it is still closing is lost, and the
        // first typed text with it.
        delay(250)
        focus.requestFocus()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(AppIcons.Back, contentDescription = "Закрыть поиск") }
        PillTextField(query, onQuery, "Поиск в чате", Modifier.weight(1f), focusRequester = focus)
        if (loading) {
            Spacer(Modifier.width(Spacing.s))
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        Text(
            position ?: if (nothingFound) "нет" else "",
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = Spacing.s),
        )
        IconButton(onClick = onOlder, enabled = position != null, modifier = Modifier.size(40.dp)) {
            Icon(AppIcons.ArrowDown, contentDescription = "Раньше", modifier = Modifier.graphicsLayer { rotationZ = 180f })
        }
        IconButton(onClick = onNewer, enabled = position != null, modifier = Modifier.size(40.dp)) {
            Icon(AppIcons.ArrowDown, contentDescription = "Позже")
        }
    }
}

/** The composer's formatting bar: «Ж», «К», «З̶», «моно» for the selected text, under the field. */
@Composable
private fun FormatBar(onFormat: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.padding(start = Spacing.m, end = Spacing.m, bottom = Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        // Each label drawn the way its run looks: Ж bold, К italic, З struck through, моно monospace.
        listOf("STRONG" to "Ж", "EMPHASIZED" to "К", "STRIKETHROUGH" to "З", "MONOSPACED" to "моно").forEach { (type, label) ->
            Text(
                AnnotatedString(label, spanStyleOf(type) ?: SpanStyle()),
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurface,
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .background(cs.surfaceContainerHighest)
                        .clickable { onFormat(type) }
                        .padding(horizontal = Spacing.m, vertical = Spacing.xs),
            )
        }
    }
}

/** Shows the composer's formatting runs in the field itself (the text is unchanged). */
private fun spansShown(spans: List<TextSpan>): VisualTransformation =
    if (spans.isEmpty()) {
        VisualTransformation.None
    } else {
        VisualTransformation { text ->
            val ranges =
                spans.mapNotNull { s ->
                    val start = s.from.coerceIn(0, text.length)
                    val end = (s.from + s.length).coerceIn(start, text.length)
                    spanStyleOf(s.type)?.takeIf { end > start }?.let { AnnotatedString.Range(it, start, end) }
                }
            TransformedText(AnnotatedString(text.text, spanStyles = ranges), OffsetMapping.Identity)
        }
    }

/** The pulsing red dot of an ongoing recording. */
@Composable
private fun RecordingDot() {
    val pulse = rememberInfiniteTransition(label = "rec")
    val alpha by pulse.animateFloat(1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "recAlpha")
    Box(
        Modifier
            .size(10.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error),
    )
}

/** The attach picker: a sheet of big round shortcuts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachSheet(
    onDismiss: () -> Unit,
    onGallery: () -> Unit,
    onFile: () -> Unit,
    /** Null where the platform has no camera capture. */
    onPhoto: (() -> Unit)?,
    onVideo: (() -> Unit)?,
    /** Null where a poll can't be sent. */
    onPoll: (() -> Unit)? = null,
) {
    AppSheet(onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.m).padding(top = Spacing.xs, bottom = Spacing.xl),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            AttachOption(AppIcons.Image, "Галерея", tileColor(0f)) {
                onDismiss()
                onGallery()
            }
            onPhoto?.let {
                AttachOption(AppIcons.Camera, "Камера", tileColor(-70f)) {
                    onDismiss()
                    it()
                }
            }
            onVideo?.let {
                AttachOption(AppIcons.Video, "Видео", tileColor(70f)) {
                    onDismiss()
                    it()
                }
            }
            AttachOption(AppIcons.Document, "Файл", tileColor(150f)) {
                onDismiss()
                onFile()
            }
            onPoll?.let {
                AttachOption(AppIcons.Poll, "Опрос", tileColor(220f)) {
                    onDismiss()
                    it()
                }
            }
        }
    }
}

@Composable
private fun AttachOption(
    icon: Painter,
    label: String,
    tile: Color,
    onClick: () -> Unit,
) {
    Column(
        Modifier.clip(RoundedCornerShape(Radius.medium)).clickable(onClick = onClick).padding(Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(tile), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
        Modifier.clip(RoundedCornerShape(Radius.small)).clickable(onClick = onClick).padding(vertical = Spacing.xs, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

/**
 * An attachment that is still going up: its local thumbnail, dimmed, with a
 * progress ring over it — or a retry-less failure marker if the upload broke.
 */
@Composable
private fun PendingThumbnail(item: PendingAttach) {
    val shape = RoundedCornerShape(Radius.medium)
    val failed = item.state == UploadState.FAILED
    Box(
        Modifier.width(260.dp).heightIn(min = 120.dp, max = 320.dp).clip(shape),
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
        Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)))
        when {
            failed ->
                Text("Не отправлено", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onScrim)
            // A closed ring with a tick: this one is up, and the message is only
            // waiting on its siblings.
            item.state == UploadState.DONE ->
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.onScrim,
                    )
                    Icon(
                        AppIcons.Delivered,
                        contentDescription = "Загружено",
                        tint = MaterialTheme.colorScheme.onScrim,
                        modifier = Modifier.size(16.dp),
                    )
                }
            item.state == UploadState.UPLOADING && item.progress > 0f ->
                CircularProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.onScrim,
                )
            else ->
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.onScrim,
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
                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.onScrim)
            } else {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.53f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        AppIcons.Play,
                        contentDescription = "Воспроизвести",
                        tint = MaterialTheme.colorScheme.onScrim,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

private val VIDEO_NOTE_SIZE = 200.dp

// Past any bubble's height: scrolling to the last item with it lands at the list's very end
// (the list clamps it), rather than at the top of a post taller than the screen.
private const val END_OFFSET = 100_000

private suspend fun androidx.compose.foundation.lazy.LazyListState.scrollToEnd(lastIndex: Int) = scrollToItem(lastIndex, END_OFFSET)

private fun Message.listKey(): Any = id ?: cid ?: time

/** Whether the list follows a message appended at the end: our own send always, others only near the bottom. */
internal fun followAppended(
    mine: Boolean,
    lastVisible: Int,
    prevSize: Int,
): Boolean = mine || lastVisible >= prevSize - 2

/** Whether older history came in before the message the list was anchored on ([anchorKey], set when paging back). */
internal fun olderPrepended(
    firstKey: Any,
    anchorKey: Any?,
): Boolean = anchorKey != null && firstKey != anchorKey

private val STICKER_SIZE = 160.dp

/**
 * One button under a bot's message, full width as the official client draws them. Link
 * buttons open their URL (↗ in the corner), callback buttons tell the bot ([busy] while
 * that is on its way); other kinds need more than this client does and stay inert.
 */
@Composable
private fun BotButtonView(
    button: BotButton,
    fg: Color,
    busy: Boolean,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val url = button.url
    val active = url != null || button.isCallback
    Box(
        modifier
            .clip(RoundedCornerShape(Radius.small))
            .background(fg.copy(alpha = 0.12f))
            .clickable(enabled = active && !busy) { if (url != null) uriHandler.openUriSafely(url) else onPress() }
            .padding(horizontal = Spacing.xl, vertical = Spacing.s),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = fg)
        } else {
            Text(
                button.text,
                style = MaterialTheme.typography.labelLarge,
                color = fg.copy(alpha = if (active) 1f else 0.55f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        if (url != null) {
            Icon(
                AppIcons.Open,
                contentDescription = "Ссылка",
                tint = fg.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 18.dp, y = (-4).dp).size(12.dp),
            )
        }
    }
}

/** The bar under the header that carries the chat's pinned message. */
@Composable
private fun PinnedBar(
    message: Message,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val divider = cs.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(divider, size = Size(size.width, 1.dp.toPx())) }
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(32.dp)
                .clip(CircleShape)
                .background(cs.primary),
        )
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(
                "Закреплённое сообщение",
                style = MaterialTheme.typography.labelLarge,
                color = cs.primary,
                maxLines = 1,
            )
            Text(
                message.previewLabel().replace('\n', ' '),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Spacing.m))
        Icon(AppIcons.Pin, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/** The header while messages are being picked: the count and what can be done with them. */
@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    /** Null when none of the picked messages has text. */
    onCopy: (() -> Unit)?,
    onForward: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(AppIcons.Close, contentDescription = "Отменить выбор") }
        Text(
            "Выбрано: $count",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = Spacing.xs),
        )
        if (onCopy != null) IconButton(onClick = onCopy) { Icon(AppIcons.Copy, contentDescription = "Копировать") }
        IconButton(onClick = onForward) { Icon(AppIcons.Forward, contentDescription = "Переслать") }
        IconButton(onClick = onDelete) {
            Icon(AppIcons.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
        }
    }
}

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
    val shape = RoundedCornerShape(Radius.medium)
    // The server reports the dimensions, so the tile can take its final shape before
    // the bytes arrive — no blank bubble, and no reflow once the image lands. Without
    // them, fall back to a minimum height so the placeholder still has a box to fill.
    var mod =
        if (media.width > 0 && media.height > 0) {
            Modifier.width(260.dp).aspectRatio(media.width.toFloat() / media.height)
        } else {
            Modifier.width(260.dp).heightIn(min = 140.dp, max = 320.dp)
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
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.53f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    AppIcons.Play,
                    contentDescription = "Воспроизвести",
                    tint = MaterialTheme.colorScheme.onScrim,
                    modifier = Modifier.size(28.dp),
                )
            }
            if (media.durationSec > 0) {
                Text(
                    formatDuration(media.durationSec),
                    color = MaterialTheme.colorScheme.onScrim,
                    style = MaterialTheme.typography.labelSmall,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(Spacing.s)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.53f))
                            .padding(horizontal = Spacing.s, vertical = 2.dp),
                )
            }
        }
    }
}

/** "m:ss" (or "h:mm:ss") for a video-length badge. */
internal fun formatDuration(totalSec: Int): String {
    val h = totalSec / 3600
    val m = totalSec % 3600 / 60
    val s = totalSec % 60
    val ss = s.toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
}

@Composable
internal fun MediaViewerOverlay(
    viewer: MediaViewer,
    canForward: Boolean,
    onDownload: () -> Unit,
    onForward: () -> Unit,
    onShare: () -> Unit,
    onGalleryPage: (Int) -> Unit,
    onClose: () -> Unit,
) {
    PlatformBackHandler(enabled = true, onBack = onClose)
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.95f)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        when (viewer) {
            is MediaViewer.Loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.onScrim)
            is MediaViewer.Image ->
                if (viewer.gallery.size > 1) {
                    val gallery = viewer.gallery
                    val pager =
                        rememberPagerState(initialPage = gallery.indexOf(viewer.copy(gallery = emptyList())).coerceAtLeast(0)) {
                            gallery.size
                        }
                    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect(onGalleryPage) }
                    HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { ZoomableImage(gallery[it].url) }
                } else {
                    ZoomableImage(viewer.url)
                }
            is MediaViewer.Video -> VideoPlayer(viewer.url, Modifier.fillMaxWidth().heightIn(max = 480.dp))
        }
        Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 4.dp)) {
            // The action menu needs a loaded URL (download/share/forward target).
            if (viewer.url != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(AppIcons.More, contentDescription = "Действия", tint = MaterialTheme.colorScheme.onScrim)
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
                Icon(AppIcons.Close, contentDescription = "Закрыть", tint = MaterialTheme.colorScheme.onScrim)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class) // transformable(canPan)
@Composable
private fun ZoomableImage(url: String) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform =
        rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 5f)
            offset = if (scale == 1f) Offset.Zero else offset + pan
        }
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                // One-finger drags pan only a zoomed photo; otherwise they're left to the pager's swipe.
                .transformable(transform, canPan = { scale > 1f }),
    )
}

/** A small, flat, muted pill for a day label, a group service/system event or «Новые сообщения». */
@Composable
private fun ServiceMessageChip(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s, horizontal = Spacing.xl), horizontalArrangement = Arrangement.Center) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(lineBreak = LineBreak.Paragraph),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radius.medium))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = Spacing.m, vertical = Spacing.xs),
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
    ServiceMessageChip("Новые сообщения")
}

/** Incoming bubbles (and the input pill): a raised neutral tone that reads on the plain backdrop. */
@Composable
internal fun incomingBubbleColor(): Color =
    // Light pages are grey, so incoming bubbles are white there; dark ones sit a step above the page.
    MaterialTheme.colorScheme.run { if (isLightTheme()) surfaceContainer else surfaceContainerHigh }

/** Message text: paragraph-balanced breaks and hyphenation, so wrapped lines end evenly. */
@Composable
private fun MessageTextStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Paragraph, hyphens = Hyphens.Auto)
