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
