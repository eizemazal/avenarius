package com.avenarius.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.avenarius.app.ui.screens.ChatScreen
import com.avenarius.app.ui.screens.CodeScreen
import com.avenarius.app.ui.screens.LoginScreen
import com.avenarius.app.ui.screens.MainScreen
import com.avenarius.app.ui.screens.MediaViewerOverlay
import com.avenarius.app.ui.screens.PasswordScreen
import com.avenarius.app.ui.screens.RegisterScreen
import com.avenarius.app.ui.screens.SharePickScreen
import com.avenarius.app.ui.screens.UserScreen
import com.avenarius.app.ui.theme.AvenariusColors

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
                                    replyingTo = state.replyingTo,
                                    sendingAttachment = state.sendingAttachment,
                                    stagedMedia = state.stagedMedia,
                                    onLoadOlder = viewModel::loadOlder,
                                    onBack = viewModel::backToChats,
                                    onSend = viewModel::sendMessage,
                                    onSendMedia = viewModel::sendMedia,
                                    onStagedConsumed = viewModel::consumeStagedMedia,
                                    onMediaClick = viewModel::openMedia,
                                    onOpenUser = viewModel::openUser,
                                    onReact = viewModel::toggleReaction,
                                    onReply = viewModel::startReply,
                                    onCancelReply = viewModel::cancelReply,
                                    onDeleteChat = viewModel::deleteCurrentChat,
                                    onLeaveGroup = viewModel::leaveCurrentGroup,
                                )
                            Screen.SHARE_PICK ->
                                SharePickScreen(
                                    chats = state.chats,
                                    contacts = state.contactsList,
                                    myId = state.account?.userId ?: -1L,
                                    count = state.sharePending.size,
                                    onPick = viewModel::pickShareTarget,
                                    onCancel = viewModel::cancelShare,
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
