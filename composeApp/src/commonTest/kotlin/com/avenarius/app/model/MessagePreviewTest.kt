package com.avenarius.app.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** The chat-list preview line for a message. */
class MessagePreviewTest {
    private fun message(
        text: String = "",
        media: List<MediaAttach> = emptyList(),
        files: List<FileAttach> = emptyList(),
        service: ServiceEvent? = null,
    ) = Message(
        id = "1",
        cid = null,
        chatId = 1,
        senderId = 2,
        text = text,
        time = 1,
        media = media,
        files = files,
        service = service,
    )

    private fun photo() = MediaAttach(MediaType.PHOTO, "https://cdn/p.jpg", 100, 100)

    private fun video() = MediaAttach(MediaType.VIDEO, "https://cdn/v.jpg", 100, 100, videoId = 7)

    @Test
    fun textWinsWhenThereIsAny() {
        assertEquals("привет", message(text = "привет", media = listOf(photo())).previewLabel())
    }

    @Test
    fun attachmentOnlyMessagesGetALabel() {
        assertEquals("📷 Фото", message(media = listOf(photo())).previewLabel())
        assertEquals("🎥 Видео", message(media = listOf(video())).previewLabel())
        // A mixed album leads with the video.
        assertEquals("🎥 Видео", message(media = listOf(photo(), video())).previewLabel())
    }

    @Test
    fun fileMessagesNameTheFile() {
        assertEquals(
            "📎 отчёт.pdf",
            message(files = listOf(FileAttach(fileId = 1, name = "отчёт.pdf", size = 10))).previewLabel(),
        )
    }

    @Test
    fun serviceMessagesUseTheirServerText() {
        assertEquals(
            "Аня добавила Бориса",
            message(service = ServiceEvent(event = "ADD", actorId = 2, message = "Аня добавила Бориса")).previewLabel(),
        )
    }

    @Test
    fun anEmptyMessageHasNoPreview() {
        assertEquals("", message().previewLabel())
    }
}
