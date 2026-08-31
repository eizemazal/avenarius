package com.avenarius.app.ui

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import com.avenarius.app.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URL

/**
 * Fetches [url] and writes it into the public Downloads folder, then toasts the
 * result. We do this ourselves (plain HTTP GET + MediaStore) rather than via
 * DownloadManager, which silently defers/drops jobs on some OEM ROMs.
 *
 * The body is streamed to its destination — a download big enough to matter must
 * not have to fit in the heap first.
 */
actual suspend fun downloadToDevice(
    url: String,
    fileName: String,
    mime: String,
    onProgress: ((Float) -> Unit)?,
): String? {
    val context = Session.appContext
    val safeName = sanitizeFileName(fileName)
    val resolvedMime = mimeOf(safeName, mime)
    // Quiet when the caller draws its own progress — see the expect declaration.
    val announce = onProgress == null
    if (announce) mainToast(context, "Загрузка: $safeName")
    return withContext(Dispatchers.IO) {
        val result =
            runCatching {
                val connection = URL(url).openConnection()
                val total = connection.contentLengthLong
                connection.getInputStream().use { input ->
                    saveToDownloads(context, safeName, resolvedMime, input, total, onProgress)
                }
            }
        result
            .onSuccess { if (announce) mainToast(context, "Сохранено в «Загрузки»: $safeName") }
            .onFailure {
                if (!announce) throw it
                mainToast(context, "Не удалось сохранить файл")
            }
        result.getOrNull()
    }
}

actual suspend fun openDownloadedFile(
    reference: String,
    fileName: String,
): Boolean {
    val context = Session.appContext
    val uri = runCatching { Uri.parse(reference) }.getOrNull() ?: return false
    // Gone (the user cleared their Downloads, say) — the caller re-offers the download.
    // Checked off the main thread; the activity launch below is back on it.
    val exists =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.close()
                true
            }.getOrDefault(false)
        }
    if (!exists) return false
    val intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeOf(sanitizeFileName(fileName), "application/octet-stream"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        mainToast(context, "Нет приложения для этого файла")
        false
    }
}

/**
 * Strips what mustn't reach the file system — or the extension check that decides
 * which app can open the file. Names arrive from the server and have been seen
 * wrapped in quotes, which turned ".pdf" into ".pdf\"" and left the phone with no
 * idea what the file was.
 */
private fun sanitizeFileName(fileName: String): String =
    fileName
        .trim()
        .removeSurrounding("\"")
        .replace('/', '_')
        .replace('\\', '_')
        .filter { it.code >= 0x20 }
        .trim()
        .ifBlank { "file" }

/** The MIME type for [fileName]'s extension, falling back to [fallback]. */
private fun mimeOf(
    fileName: String,
    fallback: String,
): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: fallback
}

/**
 * Streams [input] into the public Downloads collection (MediaStore on Q+, a plain
 * file before that). Returns the content URI of the saved copy, or null pre-Q where
 * a `file://` path can't be handed to another app.
 */
private fun saveToDownloads(
    context: Context,
    fileName: String,
    mime: String,
    input: InputStream,
    totalBytes: Long,
    onProgress: ((Float) -> Unit)?,
): String? {
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
        try {
            resolver.openOutputStream(uri)?.use { output -> input.copyReporting(output, totalBytes, onProgress) }
                ?: error("openOutputStream failed")
        } catch (e: Throwable) {
            // Don't leave a half-written pending entry behind.
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri.toString()
    }
    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    dir.mkdirs()
    File(dir, fileName).outputStream().use { output: OutputStream ->
        input.copyReporting(output, totalBytes, onProgress)
    }
    // A file:// URI would trip FileUriExposedException on N+, and this app supports
    // back to API 24 — so the file is saved, but there's no "open" shortcut here.
    return null
}

/**
 * Copies this stream into [output], reporting how far along it is. Falls back to
 * a plain copy when the server didn't say how big the file is, since a fraction of
 * an unknown total is meaningless.
 */
private fun InputStream.copyReporting(
    output: OutputStream,
    totalBytes: Long,
    onProgress: ((Float) -> Unit)?,
) {
    if (onProgress == null || totalBytes <= 0L) {
        copyTo(output)
        return
    }
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var copied = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        output.write(buffer, 0, read)
        copied += read
        onProgress((copied.toFloat() / totalBytes).coerceIn(0f, 1f))
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
