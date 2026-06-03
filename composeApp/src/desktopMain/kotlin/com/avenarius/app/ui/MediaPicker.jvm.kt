package com.avenarius.app.ui

import androidx.compose.runtime.Composable
import com.avenarius.app.model.PickedMedia

// Desktop media attachments aren't supported yet (Android-only feature for now);
// the launchers are no-ops so shared UI still compiles and runs on desktop.
actual val cameraCaptureSupported: Boolean = false

@Composable
actual fun rememberPhotoPickLauncher(onPicked: (PickedMedia) -> Unit): () -> Unit = {}

@Composable
actual fun rememberCameraPhotoLauncher(onPicked: (PickedMedia) -> Unit): () -> Unit = {}

@Composable
actual fun rememberCameraVideoLauncher(onPicked: (PickedMedia) -> Unit): () -> Unit = {}

@Composable
actual fun rememberFilePickLauncher(onPicked: (PickedMedia) -> Unit): () -> Unit = {}

@Composable
actual fun rememberSingleImagePickLauncher(onPicked: (PickedMedia) -> Unit): () -> Unit = {}
