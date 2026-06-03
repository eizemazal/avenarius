package com.avenarius.app.ui

import androidx.compose.runtime.Composable
import com.avenarius.app.model.PickedMedia

/**
 * Returns a launcher lambda that opens the platform photo/video picker. [onPicked]
 * fires with the chosen item (already read into memory), or not at all if the user
 * cancels. Android uses the system Photo Picker; desktop is a stub for now.
 */
@Composable
expect fun rememberPhotoPickLauncher(onPicked: (PickedMedia) -> Unit): () -> Unit

/** Launcher that captures a photo with the system camera. Desktop is a stub. */
@Composable
expect fun rememberCameraPhotoLauncher(onPicked: (PickedMedia) -> Unit): () -> Unit

/** Launcher that records a video with the system camera. Desktop is a stub. */
@Composable
expect fun rememberCameraVideoLauncher(onPicked: (PickedMedia) -> Unit): () -> Unit

/** Launcher that picks an arbitrary file (any MIME type). Desktop is a stub. */
@Composable
expect fun rememberFilePickLauncher(onPicked: (PickedMedia) -> Unit): () -> Unit

/** Launcher that picks a single image (for an avatar). Desktop is a stub. */
@Composable
expect fun rememberSingleImagePickLauncher(onPicked: (PickedMedia) -> Unit): () -> Unit

/** True on platforms where camera capture is available (Android). */
expect val cameraCaptureSupported: Boolean
