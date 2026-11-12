package com.avenarius.app.net

import com.avenarius.app.model.OutAttach
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DemoMaxApiTest {
    private val api = DemoMaxApi(now = 1_800_000_000_000L)

    private suspend fun messages(chatId: Long) = api.fetchHistory(chatId, Long.MAX_VALUE, 500)

    @Test
    fun everyChatHasMessages() =
        runTest {
            val chats = api.sync("demo").chats
            assertTrue(chats.size >= 12)
            chats.forEach { assertTrue(messages(it.id).isNotEmpty(), "${it.title} is empty") }
            // The list shows the newest first, and each row previews its last message.
            assertEquals(chats.sortedByDescending { it.lastEventTime }, chats)
            assertTrue(chats.all { it.lastMessageText != null })
        }

    @Test
    fun mediaRequestReturnsEveryKind() =
        runTest {
            val chat = DemoFixtures.dialog(DemoFixtures.ALICE)
            val anchor = messages(chat).last().id!!
            for (type in listOf("PHOTO", "FILE", "SHARE", "AUDIO")) {
                assertTrue(api.fetchChatMedia(chat, listOf(type), anchor, 50, withAnchor = true).isNotEmpty(), type)
            }
        }

    @Test
    fun aVoteChangesTheTallyAndStays() =
        runTest {
            val chat = DemoFixtures.dialog(DemoFixtures.ALICE)
            val msg = messages(chat).first { it.poll != null }
            val poll = msg.poll!!
            val counted = assertNotNull(api.votePoll(chat, msg.id!!, poll, listOf(0)))
            assertEquals(poll.total + 1, counted.total)
            assertEquals(1, counted.answers.first { it.id == 0 }.votes)
            assertTrue(
                messages(chat)
                    .first { it.id == msg.id }
                    .poll!!
                    .answers
                    .first { it.id == 0 }
                    .mine,
            )
        }

    @Test
    fun sentStickerAndPollAreKeptInTheChat() =
        runTest {
            val chat = DemoFixtures.WORK
            val sticker = api.sendMessage(chat, "", 1, attaches = listOf(OutAttach.Sticker(DemoFixtures.STICKER_OK)))
            val poll = api.sendMessage(chat, "", 2, attaches = listOf(OutAttach.Poll("Обед?", listOf("Да", "Нет"), 0)))
            assertNotNull(sticker.stickerUrl)
            assertEquals(listOf("Да", "Нет"), poll.poll?.answers?.map { it.text })
            assertEquals(listOf(sticker.id, poll.id), messages(chat).takeLast(2).map { it.id })
        }

    @Test
    fun searchFindsDemoMessages() =
        runTest {
            assertTrue(api.searchMessages(null, "палатку").isNotEmpty())
            assertTrue(api.searchMessages(DemoFixtures.WORK, "палатку").isEmpty())
        }

    @Test
    fun settingsHaveContent() =
        runTest {
            assertEquals(3, api.getSessions().size)
            assertEquals(1, api.getBlockedPage(0).users.size)
            assertEquals(4, api.getFolders().folders.size)
        }
}
