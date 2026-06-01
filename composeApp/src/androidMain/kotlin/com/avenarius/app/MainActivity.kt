package com.avenarius.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avenarius.app.data.Prefs
import com.avenarius.app.ui.App
import com.avenarius.app.ui.AppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = Prefs(AndroidStorage(applicationContext))

        setContent {
            val vm: AppViewModel = viewModel { AppViewModel(prefs) }
            App(vm)
        }
    }
}
