package com.avenarius.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** In-memory [AppStorage] for tests. */
private class FakeStorage : AppStorage {
    val map = mutableMapOf<String, String>()

    override fun getString(key: String): String? = map[key]

    override fun putString(
        key: String,
        value: String?,
    ) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

private val UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

class PrefsTest {
    @Test
    fun tokenAndUserIdRoundTrip() {
        val prefs = Prefs(FakeStorage())
        assertNull(prefs.token)
        assertNull(prefs.userId)

        prefs.token = "abc123"
        prefs.userId = 293679916L
        assertEquals("abc123", prefs.token)
        assertEquals(293679916L, prefs.userId)
    }

    @Test
    fun clearRemovesTokenButKeepsDeviceIds() {
        val prefs = Prefs(FakeStorage())
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
        val storage = FakeStorage()
        val prefs = Prefs(storage)
        val first = prefs.deviceId
        assertTrue(UUID.matches(first), "deviceId should be a UUID: $first")
        assertEquals(first, prefs.deviceId, "deviceId must be stable across calls")
        // A fresh Prefs over the same storage must read the same persisted value.
        assertEquals(first, Prefs(storage).deviceId)
    }

    @Test
    fun deviceAndMtInstanceDiffer() {
        val prefs = Prefs(FakeStorage())
        assertNotEquals(prefs.deviceId, prefs.mtInstance)
        assertTrue(UUID.matches(prefs.mtInstance))
    }
}
