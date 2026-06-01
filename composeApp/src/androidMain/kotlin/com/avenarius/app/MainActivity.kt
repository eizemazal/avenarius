package com.avenarius.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avenarius.app.ui.App
import com.avenarius.app.ui.AppViewModel
import com.avenarius.app.ui.Screen
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    // Chat id requested by a tapped notification (null = none).
    private val openChatRequests = MutableStateFlow<Long?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        openChatRequests.value = intent.chatIdExtra()

        setContent {
            val vm: AppViewModel = viewModel { AppViewModel(Session.prefs, Session.client) }
            val state by vm.state.collectAsStateWithLifecycle()

            // Mirror UI state the background service needs (to suppress notifications
            // for the open chat, and to label notifications with sender names).
            LaunchedEffect(state.currentChat?.id) { Session.openChatId = state.currentChat?.id }
            LaunchedEffect(state.contacts) { Session.contacts = state.contacts }
            LaunchedEffect(state.chats) {
                Session.chatInfo = state.chats.associate { it.id to ChatBrief(it.title, it.isDialog) }
            }

            // Run the foreground service while logged in; stop it on logout.
            LaunchedEffect(state.screen) {
                when (state.screen) {
                    Screen.CHATS, Screen.CHAT -> ConnectionService.start(this@MainActivity)
                    Screen.LOGIN -> ConnectionService.stop(this@MainActivity)
                    else -> Unit
                }
            }

            // Open a chat when launched/resumed from a message notification.
            val pendingChat by openChatRequests.collectAsStateWithLifecycle()
            LaunchedEffect(pendingChat) {
                pendingChat?.let { vm.openChatById(it); openChatRequests.value = null }
            }

            App(vm)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openChatRequests.value = intent.chatIdExtra()
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

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val EXTRA_CHAT_ID = "chatId"
    }
}
