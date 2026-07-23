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
import com.avenarius.app.model.PickedKind
import com.avenarius.app.model.PickedMedia
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

/** Reads [uri]'s bytes off the main thread and hands back a [PickedMedia]. */
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
        val bytes = withContext(Dispatchers.IO) { readBytes(context, uri) } ?: return@launch
        onPicked(PickedMedia(bytes, mime, name, kind))
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

internal fun readBytes(
    context: Context,
    uri: Uri,
): ByteArray? = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }

/** A gallery pick: photo or video, decided by MIME type. */
internal fun readGalleryMedia(
    context: Context,
    uri: Uri,
): PickedMedia? {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    val bytes = readBytes(context, uri) ?: return null
    val isVideo = mime.startsWith("video")
    val name = queryDisplayName(resolver, uri) ?: if (isVideo) "video" else "image"
    return PickedMedia(bytes, mime, name, if (isVideo) PickedKind.VIDEO else PickedKind.PHOTO)
}

/** An arbitrary file pick — always [PickedKind.FILE]. */
internal fun readFile(
    context: Context,
    uri: Uri,
): PickedMedia? {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    val bytes = readBytes(context, uri) ?: return null
    val name = queryDisplayName(resolver, uri) ?: "file"
    return PickedMedia(bytes, mime, name, PickedKind.FILE)
}

/** An incoming share: kind is inferred from the MIME type (image/video/else). */
internal fun readSharedMedia(
    context: Context,
    uri: Uri,
): PickedMedia? {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    val bytes = readBytes(context, uri) ?: return null
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
    return PickedMedia(bytes, mime, name, kind)
}

private fun queryDisplayName(
    resolver: ContentResolver,
    uri: Uri,
): String? =
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
    }
