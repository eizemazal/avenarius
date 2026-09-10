package com.avenarius.app.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Known-answer tests for the SMS-login `mode` attestation, pinned against a value captured
 * from the genuine Max client. If Max rotates the app (new [MaxClient.APP_VERSION]) the
 * integrity constants in [AuthAttest] must be refreshed and this vector re-captured.
 */
class AuthAttestTest {
    private fun ByteArray.hex() = joinToString("") { ((it.toInt() and 0xff) + 0x100).toString(16).substring(1) }

    @Test
    fun modeMatchesGenuineClientVector() {
        // callsSeed = 10^18, deviceId = "994d1bc966a3b2ce" -> captured from the real app.
        val mode = AuthAttest.mode(callsSeed = 1_000_000_000_000_000_000L, deviceId = "994d1bc966a3b2ce")
        assertEquals(96, mode.size)
        assertEquals(
            "5fa6a4ddcd9a30025747fcdf628b56cb48fd29f4d7761347366906da3e0ea83a" +
                "7c94bb26d5abc7ab4335df1860f0d2e0d32f4cd987fa9f77757cdf6a32570cf3" +
                "a2c21cf28ee050a680272ab01e2d47b967004f5fec966f0ecbb8ce48c41d3f65",
            mode.hex(),
        )
    }

    @Test
    fun startAuthPayloadIsFixmap3WithBinModeAndPrefix() {
        val mode = ByteArray(96) { it.toByte() }
        val p = AuthAttest.startAuthPayload(mode, "+79995551234")
        // f0 7c prefix, then fixmap(3), then "mode" -> bin8(96).
        assertEquals(0xf0.toByte(), p[0])
        assertEquals(0x7c.toByte(), p[1])
        assertEquals(0x83.toByte(), p[2]) // fixmap, 3 entries
        assertEquals(0xa4.toByte(), p[3]) // fixstr, len 4 ("mode")
        assertEquals("mode", p.copyOfRange(4, 8).decodeToString())
        assertEquals(0xc4.toByte(), p[8]) // bin8
        assertEquals(96.toByte(), p[9])
        assertTrue(p.copyOfRange(10, 106).contentEquals(mode))
        // phone survives at the tail.
        assertTrue(p.decodeToString().endsWith("+79995551234"))
    }
}
