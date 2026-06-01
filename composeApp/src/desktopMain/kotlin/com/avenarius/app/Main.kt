package com.avenarius.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.avenarius.app.data.Prefs
import com.avenarius.app.net.MaxClient
import com.avenarius.app.ui.App
import com.avenarius.app.ui.AppViewModel

/**
 * Desktop entry point. Reuses the same shared [App] composable and [AppViewModel]
 * as Android — only the storage backend and window host differ.
 */
fun main() =
    application {
        val prefs = Prefs(DesktopStorage())
        val viewModel = AppViewModel(prefs, MaxClient())

        Window(
            onCloseRequest = ::exitApplication,
            title = "Авенариус",
            state = rememberWindowState(width = 420.dp, height = 720.dp),
        ) {
            App(viewModel)
        }
    }
