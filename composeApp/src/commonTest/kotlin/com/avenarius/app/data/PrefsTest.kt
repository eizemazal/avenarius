package com.avenarius.app.data

import com.avenarius.app.ui.theme.Accent
import com.avenarius.app.ui.theme.accentOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

class PrefsTest {
    @Test
    fun tokenAndUserIdRoundTrip() {
        val prefs = Prefs(InMemoryStorage())
        assertNull(prefs.token)
        assertNull(prefs.userId)

        prefs.token = "abc123"
        prefs.userId = 293679916L
        assertEquals("abc123", prefs.token)
        assertEquals(293679916L, prefs.userId)
    }

    @Test
    fun clearRemovesTokenButKeepsDeviceIds() {
        val prefs = Prefs(InMemoryStorage())
        prefs.token = "tok"
        prefs.userId = 5L
        val device = prefs.deviceId
        val mt = prefs.mtInstance

        prefs.clear()

        assertNull(prefs.token)
        assertNull(prefs.userId)
        assertEquals(device, prefs.deviceId, "device id must survive logout")
        assertEquals(mt, prefs.mtInstance, "mt instance must survive logout")
    }

    @Test
    fun deviceIdIsStableUuidAndPersisted() {
        val storage = InMemoryStorage()
        val prefs = Prefs(storage)
        val first = prefs.deviceId
        assertTrue(UUID.matches(first), "deviceId should be a UUID: $first")
        assertEquals(first, prefs.deviceId, "deviceId must be stable across calls")
        // A fresh Prefs over the same storage must read the same persisted value.
        assertEquals(first, Prefs(storage).deviceId)
    }

    @Test
    fun deviceAndMtInstanceDiffer() {
        val prefs = Prefs(InMemoryStorage())
        assertNotEquals(prefs.deviceId, prefs.mtInstance)
        assertTrue(UUID.matches(prefs.mtInstance))
    }

    @Test
    fun scalesDefaultRoundTripAndClampBadValues() {
        val storage = InMemoryStorage()
        val prefs = Prefs(storage)
        assertEquals(1f, prefs.uiScale)
        assertEquals(1f, prefs.fontScale)

        prefs.uiScale = 1.2f
        prefs.fontScale = 1.4f
        assertEquals(1.2f, prefs.uiScale)
        assertEquals(1.4f, prefs.fontScale)

        storage.putString("ui_scale", "9")
        storage.putString("font_scale", "garbage")
        assertEquals(Prefs.UI_SCALE_RANGE.endInclusive, prefs.uiScale)
        assertEquals(1f, prefs.fontScale)
    }

    @Test
    fun unknownAccentFallsBackToBlue() {
        assertEquals(Accent.BLUE, accentOf(null))
        assertEquals(Accent.BLUE, accentOf("NEON"))
        assertEquals(Accent.GREEN, accentOf("GREEN"))
    }

    @Test
    fun pinnedChatsRoundTripAndClearOnLogout() {
        val storage = InMemoryStorage()
        val prefs = Prefs(storage)
        assertEquals(emptySet(), prefs.pinnedChats)
        prefs.pinnedChats = setOf(5L, -12L)
        assertEquals(setOf(5L, -12L), prefs.pinnedChats)
        storage.putString("pinned_chats", "7,junk,,9")
        assertEquals(setOf(7L, 9L), prefs.pinnedChats)
        // Old bare ids read as time 0 next to timed ones; setting the set keeps known times.
        storage.putString("pinned_chats", "7,9:1500")
        assertEquals(mapOf(7L to 0L, 9L to 1500L), prefs.pinnedAt)
        prefs.pinnedChats = setOf(9L, 4L)
        assertEquals(mapOf(9L to 1500L, 4L to 0L), prefs.pinnedAt)
        prefs.clear()
        assertEquals(emptySet(), prefs.pinnedChats)
    }

    @Test
    fun sendOnEnterDefaultsToFalseAndRoundTrips() {
        val storage = InMemoryStorage()
        val prefs = Prefs(storage)
        assertFalse(prefs.sendOnEnter)

        prefs.sendOnEnter = true
        assertTrue(Prefs(storage).sendOnEnter)

        prefs.sendOnEnter = false
        assertFalse(Prefs(storage).sendOnEnter)
    }

    @Test
    fun quickReactionDefaultsToNullAndRoundTrips() {
        val storage = InMemoryStorage()
        val prefs = Prefs(storage)
        assertNull(prefs.quickReaction)

        prefs.quickReaction = "👍"
        assertEquals("👍", Prefs(storage).quickReaction)

        prefs.quickReaction = null
        assertNull(Prefs(storage).quickReaction)
    }

    @Test
    fun clearKeepsInputPreferences() {
        // They are device preferences, like the theme, not account data.
        val prefs = Prefs(InMemoryStorage())
        prefs.sendOnEnter = true
        prefs.quickReaction = "❤"

        prefs.clear()

        assertTrue(prefs.sendOnEnter)
        assertEquals("❤", prefs.quickReaction)
    }
}
