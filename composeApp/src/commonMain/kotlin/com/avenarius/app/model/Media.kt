package com.avenarius.app.model

/** What kind of attachment a [PickedMedia] is (drives upload + preview). */
enum class PickedKind { PHOTO, VIDEO, FILE }

/**
 * A photo/video/file the user picked (or shared from another app), read into memory
 * and staged for upload. Not a data class — it holds raw [bytes] and is transient.
 */
class PickedMedia(
    val bytes: ByteArray,
    val mime: String,
    val fileName: String,
    val kind: PickedKind,
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

    data class File(
        val fileId: Long,
    ) : OutAttach
}
