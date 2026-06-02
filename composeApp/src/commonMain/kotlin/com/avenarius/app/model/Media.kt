package com.avenarius.app.model

/**
 * A photo/video the user picked (or shared from another app), read into memory and
 * staged for upload. Not a data class — it holds raw [bytes] and is transient.
 */
class PickedMedia(
    val bytes: ByteArray,
    val mime: String,
    val fileName: String,
    val isVideo: Boolean,
)

/** An uploaded attachment, ready to be referenced in a sent message's `attaches`. */
sealed interface OutAttach {
    data class Photo(
        val token: String,
    ) : OutAttach

    data class Video(
        val videoId: Long,
        val token: String,
    ) : OutAttach
}
