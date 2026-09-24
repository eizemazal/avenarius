package com.avenarius.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avenarius.app.ui.App
import com.avenarius.app.ui.AppViewModel
import com.avenarius.app.ui.Screen
import com.avenarius.app.ui.readSharedMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    // Chat id requested by a tapped notification (null = none).
    private val openChatRequests = MutableStateFlow<Long?>(null)

    // URIs shared in from another app via ACTION_SEND(_MULTIPLE), awaiting handling.
    private val shareRequests = MutableStateFlow<List<Uri>?>(null)

    // A max.ru link opened from outside the app (ACTION_VIEW), awaiting handling.
    private val linkRequests = MutableStateFlow<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    // Mic + camera for calls, requested when a call starts. The call foreground service
    // is started from here (once the dialog is answered) rather than alongside the
    // request: its FGS type must match permissions that are granted at that moment.
    private val callPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _: Map<String, Boolean> ->
            if (callInProgress) CallService.start(this)
        }

    private var callInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        openChatRequests.value = intent.chatIdExtra()
        shareRequests.value = intent.shareUris()
        linkRequests.value = intent.viewUrl()
        handleCallExtras(intent)

        setContent {
            val vm: AppViewModel = viewModel { AppViewModel(Session.prefs, Session.client, callSession = Session.callSession) }
            val state by vm.state.collectAsStateWithLifecycle()

            // Mirror UI state the background service needs (to suppress notifications
            // for the open chat, and to label notifications with sender names).
            LaunchedEffect(state.currentChat?.id) { Session.openChatId = state.currentChat?.id }
            LaunchedEffect(state.contacts) { Session.contacts = state.contacts }
            LaunchedEffect(state.chats) {
                Session.chatInfo = state.chats.associate { it.id to ChatBrief(it.title, it.isDialog, it.muted) }
            }

            // Keep the foreground service (and thus the live connection) running
            // from the moment an auth session exists (CODE screen) through the chat
            // screens — NOT just after login. Otherwise backgrounding while waiting
            // for the SMS suspends the connection and the auth session is lost.
            LaunchedEffect(state.screen, state.demoMode) {
                // The offline demo session has no real connection to keep alive.
                if (state.demoMode) {
                    ConnectionService.stop(this@MainActivity)
                    return@LaunchedEffect
                }
                when (state.screen) {
                    Screen.CODE, Screen.PASSWORD, Screen.REGISTER, Screen.CHATS, Screen.CHAT,
                    Screen.USER, Screen.SHARE_PICK, Screen.ABOUT, Screen.EDIT_PROFILE, Screen.GROUP, Screen.TWOFA,
                    -> ConnectionService.start(this@MainActivity)
                    Screen.LOGIN, Screen.LOGIN_RESTRICTED, Screen.LOADING -> ConnectionService.stop(this@MainActivity)
                }
            }

            // While a call is live, request mic/camera and run the call foreground
            // service so capture survives backgrounding; stop it when the call ends.
            val callActive = state.call != null && state.call?.status != com.avenarius.app.model.CallStatus.ENDED
            LaunchedEffect(callActive) {
                callInProgress = callActive
                if (callActive) {
                    // Request both mic and camera for any call — video can be switched on
                    // mid-call, so the camera permission is needed even for an "audio" call.
                    val needed =
                        buildList {
                            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) !=
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                add(Manifest.permission.RECORD_AUDIO)
                            }
                            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) !=
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                add(Manifest.permission.CAMERA)
                            }
                        }
                    if (needed.isNotEmpty()) callPermissions.launch(needed.toTypedArray()) else CallService.start(this@MainActivity)
                } else {
                    CallService.stop(this@MainActivity)
                }
            }

            // Open a chat when launched/resumed from a message notification.
            val pendingChat by openChatRequests.collectAsStateWithLifecycle()
            LaunchedEffect(pendingChat) {
                pendingChat?.let {
                    vm.openChatById(it)
                    openChatRequests.value = null
                }
            }

            // Media shared in from another app: read the bytes, then show the chat picker.
            val pendingShare by shareRequests.collectAsStateWithLifecycle()
            LaunchedEffect(pendingShare) {
                val uris = pendingShare ?: return@LaunchedEffect
                // NB: don't null out shareRequests here — it's this effect's key, so
                // mutating it would cancel us mid-read. Clear it only after we're done.
                val media =
                    withContext(Dispatchers.IO) {
                        uris.mapNotNull { runCatching { readSharedMedia(this@MainActivity, it) }.getOrNull() }
                    }
                if (media.isNotEmpty()) vm.beginShare(media)
                shareRequests.value = null
            }

            // A max.ru link tapped in another app.
            val pendingLink by linkRequests.collectAsStateWithLifecycle()
            LaunchedEffect(pendingLink) {
                pendingLink?.let {
                    vm.openLink(it)
                    linkRequests.value = null
                }
            }

            App(vm)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openChatRequests.value = intent.chatIdExtra()
        intent.shareUris()?.let { shareRequests.value = it }
        intent.viewUrl()?.let { linkRequests.value = it }
        handleCallExtras(intent)
    }

    /**
     * Launched from the incoming-call notification: show over the lock screen and wake
     * the display like a phone call would, and accept straight away if the user tapped
     * "Принять" on the notification (the overlay's own button does the same).
     */
    private fun handleCallExtras(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_INCOMING_CALL, false) != true) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        if (intent.getBooleanExtra(EXTRA_ACCEPT_CALL, false)) {
            Session.callSession.accept()
            intent.removeExtra(EXTRA_ACCEPT_CALL) // don't re-accept on a config change
        }
    }

    override fun onResume() {
        super.onResume()
        Session.appInForeground = true
    }

    override fun onPause() {
        super.onPause()
        Session.appInForeground = false
    }

    private fun Intent?.chatIdExtra(): Long? {
        val id = this?.getLongExtra(EXTRA_CHAT_ID, Long.MIN_VALUE) ?: Long.MIN_VALUE
        return if (id == Long.MIN_VALUE) null else id
    }

    /** The URL of an ACTION_VIEW intent (a max.ru link opened from another app). */
    private fun Intent?.viewUrl(): String? = this?.takeIf { it.action == Intent.ACTION_VIEW }?.data?.toString()

    /** Extracts shared content URIs from an ACTION_SEND / ACTION_SEND_MULTIPLE intent. */
    private fun Intent?.shareUris(): List<Uri>? =
        when (this?.action) {
            Intent.ACTION_SEND ->
                IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)?.let { listOf(it) }
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat
                    .getParcelableArrayListExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.filterNotNull()
                    ?.takeIf { it.isNotEmpty() }
            else -> null
        }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val EXTRA_CHAT_ID = "chatId"

        /** Set when opened from the incoming-call notification (content or full-screen intent). */
        const val EXTRA_INCOMING_CALL = "incomingCall"

        /** With [EXTRA_INCOMING_CALL]: the user tapped "Принять" on the notification. */
        const val EXTRA_ACCEPT_CALL = "acceptCall"
    }
}
