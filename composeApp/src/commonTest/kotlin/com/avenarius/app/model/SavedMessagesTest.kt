package com.avenarius.app.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SavedMessagesTest {
    private fun chat(
        id: Long,
        isDialog: Boolean,
    ) = Chat(id = id, title = "t", lastMessageText = null, lastEventTime = 0, isDialog = isDialog)

    @Test
    fun onlyTheDialogWithIdZeroIsSavedMessages() {
        assertTrue(chat(0, isDialog = true).isSavedMessages)
        assertFalse(chat(0, isDialog = false).isSavedMessages)
        assertFalse(chat(5, isDialog = true).isSavedMessages)
    }

    @Test
    fun stickerMessageHasALabelWhenItHasNoText() {
        val sticker = Message(id = "1", cid = null, chatId = 1, senderId = 2, text = "", time = 1, stickerUrl = "https://cdn/s.webp")
        assertEquals("Стикер", sticker.previewLabel())
        assertEquals("привет", sticker.copy(text = "привет").previewLabel())
    }
}
