package com.avenarius.app.ui

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import com.avenarius.app.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL

private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Fetches [url] and writes it into the public Downloads folder, then toasts the
 * result. We do this ourselves (plain HTTP GET + MediaStore) rather than via
 * DownloadManager, which silently defers/drops jobs on some OEM ROMs.
 */
actual fun downloadToDevice(
    url: String,
    fileName: String,
    mime: String,
) {
    val context = Session.appContext
    val safeName = fileName.replace('/', '_').replace('\\', '_').ifBlank { "file" }
    val ext = safeName.substringAfterLast('.', "")
    val resolvedMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase()) ?: mime
    mainToast(context, "Загрузка: $safeName")
    downloadScope.launch {
        runCatching {
            val bytes = URL(url).openStream().use { it.readBytes() }
            saveToDownloads(context, safeName, resolvedMime, bytes)
        }.onSuccess {
            mainToast(context, "Сохранено в «Загрузки»: $safeName")
        }.onFailure {
            mainToast(context, "Не удалось сохранить файл")
        }
    }
}

/** Writes [bytes] to the public Downloads collection (MediaStore on Q+, file pre-Q). */
private fun saveToDownloads(
    context: Context,
    fileName: String,
    mime: String,
    bytes: ByteArray,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        val uri =
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert failed")
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("openOutputStream failed")
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    } else {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        File(dir, fileName).writeBytes(bytes)
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
