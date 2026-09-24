package com.avenarius.app.ui

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import com.avenarius.app.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL

private val shareScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Downloads [url] into the app's cache (under a `shared/` dir exposed by the
 * existing FileProvider) and launches a chooser to send it to another app.
 */
actual fun shareMediaToOtherApps(
    url: String,
    fileName: String,
    mime: String,
) {
    val context = Session.appContext
    val safeName = fileName.replace('/', '_').replace('\\', '_').ifBlank { "file" }
    shareScope.launch {
        runCatching {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, safeName)
            // Stream to disk: a shared video can be hundreds of MB, far beyond what
            // readBytes() could hold in the heap without an OOM.
            val conn =
                URL(url).openConnection().apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
            conn.getInputStream().use { input -> file.outputStream().use { out -> input.copyTo(out) } }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            val chooser =
                Intent.createChooser(send, "Поделиться").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(chooser)
        }.onFailure {
            mainToast(context, "Не удалось поделиться файлом")
        }
    }
}

private fun mainToast(
    context: Context,
    message: String,
) {
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
