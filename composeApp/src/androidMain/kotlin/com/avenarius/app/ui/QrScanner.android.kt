package com.avenarius.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import com.avenarius.app.QrScanActivity

actual val qrScanSupported: Boolean = true

/** Launches [QrScanActivity] and returns its decoded string (or null if cancelled). */
private class QrScanContract : ActivityResultContract<Unit, String?>() {
    override fun createIntent(
        context: Context,
        input: Unit,
    ): Intent = Intent(context, QrScanActivity::class.java)

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): String? = if (resultCode == Activity.RESULT_OK) intent?.getStringExtra(QrScanActivity.EXTRA_RESULT) else null
}

@Composable
actual fun rememberQrScanLauncher(onResult: (String) -> Unit): () -> Unit {
    val launcher =
        rememberLauncherForActivityResult(QrScanContract()) { result ->
            result?.let(onResult)
        }
    return { launcher.launch(Unit) }
}
