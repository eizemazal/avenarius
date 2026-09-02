package com.avenarius.app.model

import io.ktor.utils.io.ByteReadChannel

/** What kind of attachment a [PickedMedia] is (drives upload + preview). */
enum class PickedKind { PHOTO, VIDEO, FILE }

/**
 * The content behind a [PickedMedia], as a *handle* rather than bytes: the file
 * stays where the platform put it and is streamed straight to the server when the
 * message is sent.
 *
 * Holding the bytes instead meant every staged item sat in the heap at the same
 * time, which ran out of memory on a handful of videos or a burst of large photos
 * (`readBytes` → `OutOfMemoryError`).
 *
 * [ByteReadChannel] is Ktor's type simply because that is what both the platform
 * readers and the uploader already speak.
 */
interface MediaContent {
    /** Size in bytes, or -1 when the platform cannot tell. */
    val size: Long

    /**
     * A value Coil can render directly (on Android, the content URI), so previews
     * are decoded at display size instead of at full resolution.
     */
    val previewModel: Any?

    /**
     * Opens a fresh channel over the content. Called once per upload attempt, so
     * a retry re-reads from the source rather than reusing a spent channel.
     */
    fun openChannel(): ByteReadChannel
}

/**
 * A photo/video/file the user picked (or shared from another app), staged for
 * upload. Not a data class — [content] is a live handle, not a value.
 */
class PickedMedia(
    val content: MediaContent,
    val mime: String,
    val fileName: String,
    val kind: PickedKind,
) {
    /** Size in bytes, or -1 when unknown. */
    val size: Long get() = content.size
}

/**
 * A finished recording, ready to upload.
 *
 * [content] is a handle to the file on disk (never its bytes — see [MediaContent]),
 * so a long recording costs nothing in memory while it waits to be sent.
 */
class RecordedVoice(
    val content: MediaContent,
    val durationSeconds: Int,
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

    /** A voice message: the upload token plus how long it runs. */
    data class Voice(
        val token: String,
        val durationSeconds: Int,
    ) : OutAttach

    /** A round video message (a "video note"), as opposed to a plain video. */
    data class VideoNote(
        val token: String,
        val durationSeconds: Int,
    ) : OutAttach
}
