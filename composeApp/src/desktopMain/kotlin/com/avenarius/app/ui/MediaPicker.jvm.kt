package com.avenarius.app.ui

import androidx.compose.runtime.Composable
import com.avenarius.app.model.PickedMedia

// Desktop media attachments aren't supported yet (Android-only feature for now);
// the launcher is a no-op so shared UI still compiles and runs on desktop.
@Composable
actual fun rememberPhotoPickLauncher(onPicked: (PickedMedia) -> Unit): () -> Unit = {}
