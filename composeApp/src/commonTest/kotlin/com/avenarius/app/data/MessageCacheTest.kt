package com.avenarius.app.data

import com.avenarius.app.model.MediaAttach
import com.avenarius.app.model.MediaType
import com.avenarius.app.model.Message
import com.avenarius.app.model.MessageStatus
import com.avenarius.app.model.PendingAttach
import com.avenarius.app.model.PickedKind
import com.avenarius.app.model.Reaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessageCacheTest {
    private fun message(
        id: String?,
        time: Long,
        text: String = "текст",
    ) = Message(id = id, cid = null, chatId = 1, senderId = 2, text = text, time = time)

    @Test
    fun messagesRoundTripWithTheirAttachments() {
        val cache = MessageCache(InMemoryStorage())
        val saved =
            listOf(
                message("1", 10).copy(
                    media = listOf(MediaAttach(MediaType.PHOTO, "https://cdn/p.jpg", 800, 600)),
                    reactions = listOf(Reaction("👍", 2, mine = true)),
                    status = MessageStatus.READ,
                ),
            )

        cache.save(chatId = 1, messages = saved)

        assertEquals(saved, cache.load(1))
    }

    @Test
    fun eachChatIsCachedSeparately() {
        val cache = MessageCache(InMemoryStorage())
        cache.save(1, listOf(message("1", 10, "для чата 1")))
        cache.save(2, listOf(message("2", 20, "для чата 2")))

        assertEquals("для чата 1", cache.load(1).single().text)
        assertEquals("для чата 2", cache.load(2).single().text)
        assertTrue(cache.load(3).isEmpty(), "an uncached chat has no messages")
    }

    @Test
    fun unsentMessagesAreNotCached() {
        val cache = MessageCache(InMemoryStorage())
        val pending =
            message(null, 30).copy(
                pending = listOf(PendingAttach(preview = "handle", kind = PickedKind.PHOTO)),
            )

        cache.save(1, listOf(message("1", 10), pending))

        // A message still in the send queue isn't history.
        assertEquals(listOf("1"), cache.load(1).map { it.id })
    }

    @Test
    fun onlyTheNewestMessagesAreKept() {
        val cache = MessageCache(InMemoryStorage())
        val many = (1..200).map { message(it.toString(), it.toLong()) }

        cache.save(1, many)

        val loaded = cache.load(1)
        assertEquals(60, loaded.size, "the cache holds a screenful, not an archive")
        assertEquals("200", loaded.last().id, "the newest must be the ones kept")
        assertEquals("141", loaded.first().id)
    }

    @Test
    fun savingAnEmptyListDropsTheEntry() {
        val storage = InMemoryStorage()
        val cache = MessageCache(storage)
        cache.save(1, listOf(message("1", 10)))

        cache.save(1, emptyList())

        assertTrue(cache.load(1).isEmpty())
        assertNull(storage.blobs["messages_1"])
    }

    @Test
    fun corruptCacheIsDroppedRatherThanCrashing() {
        val storage = InMemoryStorage()
        val cache = MessageCache(storage)
        cache.save(1, listOf(message("1", 10)))
        storage.blobs["messages_1"] = "{not json"

        assertTrue(cache.load(1).isEmpty())
        assertNull(storage.blobs["messages_1"], "an unreadable cache should be discarded")
    }

    @Test
    fun clearRemovesEveryChat() {
        val cache = MessageCache(InMemoryStorage())
        cache.save(1, listOf(message("1", 10)))
        cache.save(2, listOf(message("2", 20)))

        cache.clear()

        assertTrue(cache.load(1).isEmpty() && cache.load(2).isEmpty())
        assertEquals(0L, cache.sizeBytes())
    }
}
