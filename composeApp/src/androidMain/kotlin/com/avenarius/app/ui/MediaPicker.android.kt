package com.avenarius.app.ui

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.avenarius.app.model.MediaContent
import com.avenarius.app.model.PickedKind
import com.avenarius.app.model.PickedMedia
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

actual val cameraCaptureSupported: Boolean = true

@Composable
actual fun rememberPhotoPickLauncher(onPicked: (PickedMedia) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            if (uris.isNotEmpty()) {
                scope.launch {
                    val media = withContext(Dispatchers.IO) { uris.mapNotNull { readGalleryMedia(context, it) } }
                    media.forEach { onPicked(it) }
                }
            }
        }
    return {
        launcher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
        )
    }
}

@Composable
actual fun rememberSingleImagePickLauncher(onPicked: (PickedMedia) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                scope.launch {
                    val media = withContext(Dispatchers.IO) { readGalleryMedia(context, uri) }
                    if (media != null) onPicked(media)
                }
            }
        }
    return {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}

@Composable
actual fun rememberFilePickLauncher(onPicked: (PickedMedia) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                scope.launch {
                    val media = withContext(Dispatchers.IO) { readFile(context, uri) }
                    if (media != null) onPicked(media)
                }
            }
        }
    return { launcher.launch("*/*") }
}

@Composable
actual fun rememberCameraPhotoLauncher(onPicked: (PickedMedia) -> Unit): () -> Unit =
    rememberCameraCapture(isVideo = false, onPicked = onPicked)

/**
 * Shared photo/video capture launcher. Because the app declares the CAMERA permission
 * (for the QR scanner), the system's ACTION_IMAGE_CAPTURE / ACTION_VIDEO_CAPTURE now
 * *require* that permission to be granted at runtime — otherwise they throw a
 * SecurityException. So we request CAMERA first and only launch once it's granted.
 */
@Composable
private fun rememberCameraCapture(
    isVideo: Boolean,
    onPicked: (PickedMedia) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<Uri?>(null) }
    val onResult: (Boolean) -> Unit = { success ->
        val uri = target
        if (success && uri != null) {
            if (isVideo) {
                deliver(scope, context, uri, "video/mp4", "camera.mp4", PickedKind.VIDEO, onPicked)
            } else {
                deliver(scope, context, uri, "image/jpeg", "camera.jpg", PickedKind.PHOTO, onPicked)
            }
        }
    }
    val contract: ActivityResultContract<Uri, Boolean> =
        if (isVideo) ActivityResultContracts.CaptureVideo() else ActivityResultContracts.TakePicture()
    val capture = rememberLauncherForActivityResult(contract, onResult)
    val doLaunch: () -> Unit = {
        val uri = newMediaUri(context, if (isVideo) ".mp4" else ".jpg")
        target = uri
        capture.launch(uri)
    }
    val requestCamera =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) doLaunch()
        }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            doLaunch()
        } else {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }
}

@Composable
actual fun rememberCameraVideoLauncher(onPicked: (PickedMedia) -> Unit): () -> Unit =
    rememberCameraCapture(isVideo = true, onPicked = onPicked)

/** Hands back a [PickedMedia] for [uri]; its content is read later, on upload. */
private fun deliver(
    scope: CoroutineScope,
    context: Context,
    uri: Uri,
    mime: String,
    name: String,
    kind: PickedKind,
    onPicked: (PickedMedia) -> Unit,
) {
    scope.launch {
        // Only metadata is touched here (a cursor query), so this stays cheap even
        // for a large capture.
        val size = withContext(Dispatchers.IO) { querySize(context.contentResolver, uri) }
        onPicked(PickedMedia(UriContent(context, uri, size), mime, name, kind))
    }
}

/** Creates a fresh temp file in the cache and returns a shareable FileProvider URI. */
private fun newMediaUri(
    context: Context,
    suffix: String,
): Uri {
    val file = File.createTempFile("capture_", suffix, context.cacheDir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * [MediaContent] backed by a content URI. Nothing is read until upload time, and
 * each attempt opens its own stream.
 */
private class UriContent(
    private val context: Context,
    private val uri: Uri,
    override val size: Long,
) : MediaContent {
    override val previewModel: Any = uri

    override fun openChannel(): ByteReadChannel =
        (
            context.contentResolver.openInputStream(uri)
                ?: error("Не удалось открыть выбранный файл")
        ).toByteReadChannel()
}

/** [MediaContent] backed by a file we own — a copy of shared-in content. */
private class FileContent(
    private val file: File,
) : MediaContent {
    override val size: Long get() = file.length()
    override val previewModel: Any = Uri.fromFile(file)

    override fun openChannel(): ByteReadChannel = file.inputStream().toByteReadChannel()
}

/**
 * Streams [uri] into a file of our own, in constant memory.
 *
 * Used for shared-in content: the grant from the sending app lasts only as long
 * as the receiving activity's intent, and the user may spend a while choosing a
 * chat, so that path takes a copy rather than keeping a handle to someone else's
 * URI. Returns null if the content could not be read.
 */
private fun copyToCache(
    context: Context,
    uri: Uri,
    name: String,
): File? {
    val dir = File(context.cacheDir, SHARED_MEDIA_DIR).apply { mkdirs() }
    val target = File.createTempFile("share_", "_" + name.takeLast(40), dir)
    return runCatching {
        val stream = context.contentResolver.openInputStream(uri) ?: error("Нет доступа к файлу")
        stream.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        target
    }.getOrElse {
        target.delete()
        null
    }
}

/**
 * Deletes temp files left behind by shared-in content and camera captures. Safe to
 * call at startup only, when nothing can still be staged for sending.
 */
internal fun clearMediaTempFiles(context: Context) {
    runCatching {
        File(context.cacheDir, SHARED_MEDIA_DIR).deleteRecursively()
        context.cacheDir.listFiles { f -> f.isFile && f.name.startsWith("capture_") }?.forEach { it.delete() }
    }
}

private const val SHARED_MEDIA_DIR = "shared_media"

/** A gallery pick: photo or video, decided by MIME type. */
internal fun readGalleryMedia(
    context: Context,
    uri: Uri,
): PickedMedia? {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    val isVideo = mime.startsWith("video")
    val name = queryDisplayName(resolver, uri) ?: if (isVideo) "video" else "image"
    return PickedMedia(
        UriContent(context, uri, querySize(resolver, uri)),
        mime,
        name,
        if (isVideo) PickedKind.VIDEO else PickedKind.PHOTO,
    )
}

/** An arbitrary file pick — always [PickedKind.FILE]. */
internal fun readFile(
    context: Context,
    uri: Uri,
): PickedMedia? {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    val name = queryDisplayName(resolver, uri) ?: "file"
    return PickedMedia(UriContent(context, uri, querySize(resolver, uri)), mime, name, PickedKind.FILE)
}

/** An incoming share: kind is inferred from the MIME type (image/video/else). */
internal fun readSharedMedia(
    context: Context,
    uri: Uri,
): PickedMedia? {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    val kind =
        when {
            mime.startsWith("image") -> PickedKind.PHOTO
            mime.startsWith("video") -> PickedKind.VIDEO
            else -> PickedKind.FILE
        }
    val name =
        queryDisplayName(resolver, uri) ?: when (kind) {
            PickedKind.PHOTO -> "image"
            PickedKind.VIDEO -> "video"
            PickedKind.FILE -> "file"
        }
    // Copied rather than handed over as a URI — see [copyToCache].
    val copy = copyToCache(context, uri, name) ?: return null
    return PickedMedia(FileContent(copy), mime, name, kind)
}

/** The content's size in bytes, or -1 when the provider doesn't report one. */
private fun querySize(
    resolver: ContentResolver,
    uri: Uri,
): Long =
    resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
        val column = c.getColumnIndex(OpenableColumns.SIZE)
        if (c.moveToFirst() && column >= 0 && !c.isNull(column)) c.getLong(column) else -1L
    } ?: -1L

private fun queryDisplayName(
    resolver: ContentResolver,
    uri: Uri,
): String? =
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
    }
