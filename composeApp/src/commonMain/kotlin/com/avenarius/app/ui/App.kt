package com.avenarius.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.avenarius.app.model.CallDirection
import com.avenarius.app.model.CallStatus
import com.avenarius.app.ui.components.openUriSafely
import com.avenarius.app.ui.screens.AboutScreen
import com.avenarius.app.ui.screens.CallScreen
import com.avenarius.app.ui.screens.ChatScreen
import com.avenarius.app.ui.screens.CodeScreen
import com.avenarius.app.ui.screens.EditProfileScreen
import com.avenarius.app.ui.screens.GroupScreen
import com.avenarius.app.ui.screens.IncomingCallOverlay
import com.avenarius.app.ui.screens.LoginScreen
import com.avenarius.app.ui.screens.MainScreen
import com.avenarius.app.ui.screens.MediaViewerOverlay
import com.avenarius.app.ui.screens.PasswordScreen
import com.avenarius.app.ui.screens.RegisterScreen
import com.avenarius.app.ui.screens.RestorePasswordScreen
import com.avenarius.app.ui.screens.RestrictedLoginScreen
import com.avenarius.app.ui.screens.SharePickScreen
import com.avenarius.app.ui.screens.TwoFaScreen
import com.avenarius.app.ui.screens.UserScreen
import com.avenarius.app.ui.theme.AvenariusTheme
import com.avenarius.app.ui.theme.aurora
import com.avenarius.app.ui.theme.isDark
import okio.Path.Companion.toPath

/** Ceiling for the on-disk thumbnail cache. */
private const val IMAGE_CACHE_MAX_BYTES = 256L * 1024 * 1024

@Composable
fun App(viewModel: AppViewModel) {
    // Register Coil's Ktor-based network fetcher so AsyncImage can load CDN URLs,
    // and give it a disk cache: a custom ImageLoader has none by default, so every
    // thumbnail was re-downloaded each time its chat was opened.
    setSingletonImageLoaderFactory { context ->
        ImageLoader
            .Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .apply {
                imageCacheDirectory()?.let { path ->
                    diskCache {
                        DiskCache
                            .Builder()
                            .directory(path.toPath())
                            .maxSizeBytes(IMAGE_CACHE_MAX_BYTES)
                            .build()
                            // Published so settings can report and clear it.
                            .also { ImageDiskCache.instance = it }
                    }
                }
            }.build()
    }
    val liveState by viewModel.state.collectAsStateWithLifecycle()
    val state = liveState
    // A link the app couldn't resolve itself goes to the browser, so a tap never ends
    // in silence.
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(state.openExternally) {
        state.openExternally?.let { url ->
            uriHandler.openUriSafely(url)
            viewModel.consumedExternalLink()
        }
    }
    val dark = state.theme.isDark()
    AvenariusTheme(dark, state.accent, state.uiScale, state.fontScale) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // Intercept the Android system back so leaving a chat returns to the
            // list (and code/password screens return to login) instead of exiting.
            PlatformBackHandler(enabled = viewModel.canGoBack(state.screen, state.tab)) {
                viewModel.onBack()
            }

            Box(Modifier.fillMaxSize()) {
                // The connection state is shown inside each screen's header (see
                // MainScreen / ChatScreen), not as a strip that pushes the UI down.
                val mainState = rememberSaveableStateHolder()
                // A chat keeps its scroll position while an info page is on top of it; leaving it
                // (or opening another chat) forgets it, so the next open lands on the unread again.
                val openChatId = liveState.currentChat?.id
                val lastChatId = remember { arrayOf<Long?>(null) }
                LaunchedEffect(openChatId) {
                    lastChatId[0]?.takeIf { it != openChatId }?.let { mainState.removeState("chat-$it") }
                    lastChatId[0] = openChatId
                }
                val connecting = if (state.reconnecting) (if (state.syncedOnce) "Переподключение…" else "Подключение…") else null
                AnimatedContent(
                    targetState = state.screen,
                    transitionSpec = {
                        // Going deeper slides in from the right, going back slides out to it.
                        val dir = if (targetState.depth() >= initialState.depth()) 1 else -1
                        (slideInHorizontally(tween(280)) { it / 5 * dir } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(280)) { -it / 5 * dir } + fadeOut(tween(160)))
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "screen",
                ) { screen ->
                    // A screen that is animating out keeps the state it was last shown with, so it
                    // doesn't blank out (leaving a chat clears the open chat) while it slides away.
                    val shown = remember { arrayOf(liveState) }
                    if (liveState.screen == screen) shown[0] = liveState
                    val state = shown[0]
                    Box(Modifier.fillMaxSize()) {
                        when (screen) {
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
                                    email = state.passwordEmail,
                                    onSubmit = viewModel::submitPassword,
                                    onForgotPassword = viewModel::startPasswordRestore,
                                )
                            Screen.PASSWORD_RESTORE ->
                                state.restore?.let { restore ->
                                    RestorePasswordScreen(
                                        restore = restore,
                                        busy = state.busy,
                                        error = state.error,
                                        onSubmitCode = viewModel::restoreSubmitCode,
                                        onResendCode = viewModel::restoreResendCode,
                                        onSubmitPassword = viewModel::restoreSubmitPassword,
                                        onSubmitHint = viewModel::restoreSubmitHint,
                                    )
                                }
                            Screen.LOGIN_RESTRICTED ->
                                RestrictedLoginScreen(
                                    detail = state.loginRestrictedDetail,
                                    recoveryUrl = state.recoveryUrl,
                                    onRetry = viewModel::restartLogin,
                                )
                            Screen.TWOFA ->
                                state.twoFa?.let { flow ->
                                    TwoFaScreen(
                                        flow = flow,
                                        busy = state.busy,
                                        error = flow.notice,
                                        onBack = viewModel::closeTwoFa,
                                        onStartSetup = viewModel::twoFaStartSetup,
                                        onSubmitPassword = viewModel::twoFaSubmitPassword,
                                        onSubmitHint = viewModel::twoFaSubmitHint,
                                        onSubmitEmail = viewModel::twoFaSubmitEmail,
                                        onSkipEmail = viewModel::twoFaSkipEmail,
                                        onSubmitEmailCode = viewModel::twoFaSubmitEmailCode,
                                        onCheckPassword = viewModel::twoFaCheckPassword,
                                        onChangePassword = viewModel::twoFaChangePassword,
                                        onDisable = viewModel::twoFaDisable,
                                    )
                                }
                            Screen.REGISTER ->
                                RegisterScreen(
                                    busy = state.busy,
                                    error = state.error,
                                    onSubmit = viewModel::submitRegister,
                                )
                            // Kept across visits (settings scroll, list position) while a chat or a page is open on top.
                            Screen.CHATS -> mainState.SaveableStateProvider("main") { MainScreen(state, viewModel) }
                            Screen.USER -> UserScreen(state, viewModel)
                            Screen.EDIT_PROFILE ->
                                EditProfileScreen(
                                    initialFirstName = state.account?.firstName ?: "",
                                    initialLastName = state.account?.lastName ?: "",
                                    initialDescription = state.viewingUser?.description ?: "",
                                    // Our profile (and so the bio) is in: until then saving could wipe the bio.
                                    profileLoaded = state.viewingUser?.id != null && state.viewingUser?.id == state.account?.userId,
                                    currentAvatarUrl = state.account?.avatarUrl,
                                    busy = state.busy,
                                    onBack = viewModel::onBack,
                                    onSave = viewModel::saveProfile,
                                )
                            Screen.CHAT ->
                                mainState.SaveableStateProvider("chat-${state.currentChat?.id}") {
                                    ChatScreen(
                                        chat = state.currentChat,
                                        // Includes bubbles whose attachments are still
                                        // uploading, so they survive leaving the chat.
                                        messages = state.visibleMessages,
                                        myId = state.account?.userId ?: -1L,
                                        // Merge resolved non-contact group members so their
                                        // name + avatar show instead of a "—" placeholder.
                                        contacts = state.contacts + state.groupMembers.mapValues { it.value.name },
                                        senderAvatars =
                                            state.contactsList.associate { it.id to it.avatarUrl } +
                                                state.groupMembers.mapValues { it.value.avatarUrl },
                                        unreadAtOpen = state.openUnreadCount,
                                        busy = state.busy,
                                        error = state.error,
                                        loadingOlder = state.loadingOlder,
                                        replyingTo = state.replyingTo,
                                        sendingAttachment = state.sendingAttachment,
                                        stagedMedia = state.stagedMedia,
                                        initialDraft = state.draft,
                                        typingText =
                                            state.typingText(
                                                state.currentChat?.id,
                                                state.contacts + state.groupMembers.mapValues { it.value.name },
                                            ),
                                        onlineUsers = state.onlineUsers,
                                        presence = state.presence,
                                        connecting = connecting,
                                        onLoadOlder = viewModel::loadOlder,
                                        onBack = viewModel::onBack,
                                        onStartCall = viewModel::startCall,
                                        onToggleMute = viewModel::toggleMuteCurrentChat,
                                        onDraftChange = viewModel::setDraft,
                                        onSend = viewModel::sendMessage,
                                        onSendMedia = viewModel::sendMedia,
                                        onStagedConsumed = viewModel::consumeStagedMedia,
                                        onMediaClick = viewModel::openMedia,
                                        onOpenUser = viewModel::openUser,
                                        onReact = viewModel::toggleReaction,
                                        onReply = viewModel::startReply,
                                        onForward = viewModel::startForward,
                                        onEditMessage = viewModel::editMessage,
                                        onRetrySend = viewModel::retrySend,
                                        onDiscardSend = viewModel::discardPendingSend,
                                        onLinkClick = viewModel::openLink,
                                        onSendVoice = viewModel::sendVoice,
                                        onSendVideoNote = viewModel::sendVideoNote,
                                        onVoiceClick = viewModel::toggleVoice,
                                        onVoiceSeek = viewModel::seekVoice,
                                        playingVoice = state.playingVoice,
                                        onVideoNoteClick = viewModel::toggleVideoNote,
                                        playingVideoNote = state.playingVideoNote,
                                        onFileClick = viewModel::openOrDownloadFile,
                                        downloadedFiles = state.downloadedFiles.keys,
                                        downloadingFiles = state.downloadingFiles,
                                        onCancelReply = viewModel::cancelReply,
                                        onDeleteChat = viewModel::deleteCurrentChat,
                                        onLeaveGroup = viewModel::leaveCurrentGroup,
                                        onOpenGroup = { state.currentChat?.let { viewModel.openGroup(it) } },
                                        readMarks = state.readMarks,
                                        readingMessageId = state.readingMessageId,
                                        readMarksLoading = state.readMarksLoading,
                                        onShowReadBy = viewModel::openReadBy,
                                        onCloseReadBy = viewModel::closeReadBy,
                                        readerInfo = viewModel::readerInfo,
                                        quickReaction = state.quickReaction,
                                        sendOnEnter = state.sendOnEnter,
                                        reactionsMenu = state.reactionsMenu,
                                        onForwardMany = viewModel::startForward,
                                        onDeleteMany = viewModel::deleteMessages,
                                        onPin = viewModel::setPinned,
                                        startSearch = state.searchOnOpen,
                                        onSearchStarted = viewModel::searchOpened,
                                        onSearch = viewModel::searchOpenChat,
                                        serverHits = state.chatSearchHits,
                                        onLoadUntil = viewModel::loadUntil,
                                        jumpTo = state.jumpTo,
                                        onJumped = viewModel::jumped,
                                        onClearHistory = { state.currentChat?.let(viewModel::clearHistory) },
                                        pressingButtons = state.pressingButtons,
                                        onBotButton = viewModel::pressBotButton,
                                        reactionsMessageId = state.reactionsMessageId,
                                        reactedBy = state.reactedBy,
                                        onShowReactions = viewModel::openReactions,
                                        onCloseReactions = viewModel::closeReactions,
                                        onVote = viewModel::vote,
                                        onWriteTo = viewModel::openDialogWith,
                                        stickerShelf = state.stickerShelf,
                                        stickers = state.stickers,
                                        onOpenStickers = viewModel::loadStickerShelf,
                                        onLoadStickers = viewModel::loadStickers,
                                        onSendSticker = viewModel::sendSticker,
                                        pollsInDialogs = state.pollsInDialogs,
                                        onSendPoll = viewModel::sendPoll,
                                        wallpaper = state.wallpaper,
                                        searchLeavesChat = state.searchOpenedChat,
                                    )
                                }
                            Screen.GROUP -> GroupScreen(state, viewModel)
                            Screen.SHARE_PICK ->
                                SharePickScreen(
                                    chats = state.chats,
                                    contacts = state.contactsList,
                                    myId = state.account?.userId ?: -1L,
                                    count = state.sharePending.size + state.forwarding.size,
                                    onPick = viewModel::pickShareTarget,
                                    onCancel = viewModel::cancelShare,
                                )
                            Screen.ABOUT -> AboutScreen(onBack = viewModel::onBack)
                        }
                    }
                }
                // Notices (server refusals, "copied") on every screen but the main one, which has its own host.
                val snackbar = remember { SnackbarHostState() }
                val ownHost = state.screen == Screen.CHATS
                LaunchedEffect(state.notice, ownHost) {
                    if (!ownHost) {
                        state.notice?.let {
                            snackbar.showSnackbar(it)
                            viewModel.clearNotice()
                        }
                    }
                }
                SnackbarHost(
                    snackbar,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(bottom = 72.dp),
                )
                // Full-screen image/video viewer, layered above everything.
                state.mediaViewer?.let {
                    MediaViewerOverlay(
                        viewer = it,
                        canForward = it.source != null,
                        onDownload = viewModel::downloadCurrentMedia,
                        onForward = viewModel::forwardCurrentMedia,
                        onShare = viewModel::shareCurrentMedia,
                        onClose = viewModel::closeMedia,
                    )
                }
                // Call UI, layered above everything: incoming-call prompt or the in-call screen.
                state.call?.let { call ->
                    if (call.status == CallStatus.RINGING && call.direction == CallDirection.INCOMING) {
                        IncomingCallOverlay(
                            call = call,
                            onAccept = viewModel::acceptCall,
                            onDecline = viewModel::declineCall,
                        )
                    } else {
                        CallScreen(
                            call = call,
                            engine = viewModel.currentCallEngine(),
                            onHangup = viewModel::hangupCall,
                            onToggleMic = viewModel::toggleCallMic,
                            onToggleCamera = viewModel::toggleCallCamera,
                            onSwitchCamera = viewModel::switchCallCamera,
                            onDismiss = viewModel::dismissCall,
                        )
                    }
                }
            }
        }
    }
}

/** How deep a screen sits in the navigation, to pick the direction of the transition. */
private fun Screen.depth(): Int =
    when (this) {
        Screen.LOADING -> 0
        Screen.LOGIN -> 1
        Screen.CODE, Screen.LOGIN_RESTRICTED, Screen.REGISTER -> 2
        Screen.PASSWORD -> 3
        Screen.PASSWORD_RESTORE -> 4
        Screen.CHATS -> 5
        Screen.CHAT, Screen.SHARE_PICK, Screen.ABOUT, Screen.TWOFA -> 6
        Screen.USER, Screen.GROUP -> 7
        Screen.EDIT_PROFILE -> 8
    }

@Composable
private fun LoadingScreen(reconnecting: Boolean) {
    Box(Modifier.fillMaxSize().aurora(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(strokeWidth = 3.dp)
            if (reconnecting) {
                Text("Переподключение…", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
