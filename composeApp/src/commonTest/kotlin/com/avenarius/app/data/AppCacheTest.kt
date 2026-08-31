package com.avenarius.app.data

import com.avenarius.app.model.Account
import com.avenarius.app.model.Chat
import com.avenarius.app.model.UserInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun session(userId: Long = 100L) =
    CachedSession(
        userId = userId,
        account = Account(userId = userId, firstName = "Я"),
        chats = listOf(Chat(id = 5, title = "Аня", lastMessageText = "привет", lastEventTime = 9, unreadCount = 2)),
        contacts = mapOf(7L to "Аня"),
        contactsList = listOf(UserInfo(id = 7, name = "Аня", avatarUrl = "https://x/a.jpg")),
        peers = mapOf(8L to UserInfo(id = 8, name = "Борис")),
    )

class AppCacheTest {
    @Test
    fun sessionRoundTrips() {
        val cache = AppCache(InMemoryStorage())
        val saved = session()
        cache.saveSession(saved)

        val loaded = cache.loadSession(100L)
        assertEquals(saved.chats, loaded?.chats)
        assertEquals(saved.contacts, loaded?.contacts)
        assertEquals(saved.contactsList, loaded?.contactsList)
        assertEquals(saved.peers, loaded?.peers)
        assertEquals(saved.account, loaded?.account)
    }

    @Test
    fun sessionOfAnotherAccountIsNotReturned() {
        val cache = AppCache(InMemoryStorage())
        cache.saveSession(session(userId = 100L))
        assertNull(cache.loadSession(999L), "another account's cache must never be shown")
    }

    @Test
    fun cacheSurvivesANewCacheInstanceOverTheSameStorage() {
        val storage = InMemoryStorage()
        AppCache(storage).saveSession(session())
        assertEquals(
            "Аня",
            AppCache(storage)
                .loadSession(100L)
                ?.chats
                ?.single()
                ?.title,
        )
    }

    @Test
    fun corruptSnapshotIsDroppedRatherThanCrashing() {
        val storage = InMemoryStorage()
        val cache = AppCache(storage)
        cache.saveSession(session())
        storage.map["cache_session"] = "{not json at all"

        assertNull(cache.loadSession(100L))
        assertNull(storage.map["cache_session"], "an unreadable snapshot should be discarded")
    }

    @Test
    fun draftsRoundTripAndDropBlanks() {
        val cache = AppCache(InMemoryStorage())
        cache.saveDrafts(mapOf(1L to "недописанное", 2L to "   ", 3L to ""))

        val drafts = cache.drafts()
        assertEquals("недописанное", drafts[1L])
        assertEquals(setOf(1L), drafts.keys, "blank drafts are not worth storing")
    }

    @Test
    fun clearCacheKeepsDrafts() {
        val cache = AppCache(InMemoryStorage())
        cache.saveSession(session())
        cache.saveDrafts(mapOf(1L to "черновик"))

        cache.clearCache()

        assertNull(cache.loadSession(100L))
        assertEquals("черновик", cache.drafts()[1L], "clearing the cache must not delete unsent text")
    }

    @Test
    fun clearAllDropsSnapshotAndDrafts() {
        val cache = AppCache(InMemoryStorage())
        cache.saveSession(session())
        cache.saveDrafts(mapOf(1L to "черновик"))

        cache.clearAll()

        assertNull(cache.loadSession(100L))
        assertTrue(cache.drafts().isEmpty())
    }

    @Test
    fun sizeReflectsStoredDataAndFallsToZeroWhenCleared() {
        val cache = AppCache(InMemoryStorage())
        assertEquals(0L, cache.sizeBytes())

        cache.saveSession(session())
        val withSession = cache.sizeBytes()
        assertTrue(withSession > 0, "a stored snapshot should have a non-zero size")

        cache.clearAll()
        assertEquals(0L, cache.sizeBytes())
    }
}
