package com.avenarius.app.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Validates the STAGE-2 `transmitted-data` extractors against the real messages
 * captured from a genuine call (see example_code/max_call_stage2_messages.json).
 */
class CallSignalingParseTest {
    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test
    fun parsesRealOfferSdp() {
        val msg =
            obj(
                """
                {"data":{"sdp":{"type":"offer","sdp":"v=0\r\no=- 1 2 IN IP4 127.0.0.1\r\na=group:BUNDLE 0 1\r\n"},"capabilities":"1"},
                 "notification":"transmitted-data","participantType":"USER","participantId":910186980129,"type":"notification"}
                """.trimIndent(),
            )
        val sdp = parseTransmittedSdp(msg)!!
        assertEquals("offer", sdp.type)
        assertEquals(910186980129L, sdp.participantId)
        assertTrue(sdp.sdp.startsWith("v=0"))
        assertTrue(sdp.sdp.contains("a=group:BUNDLE 0 1"))
        // A candidate-only message must not be mistaken for SDP.
        assertNull(parseTransmittedCandidate(msg))
    }

    @Test
    fun parsesRealIceCandidate() {
        val msg =
            obj(
                """
                {"data":{"candidate":{"candidate":"candidate:468136283 1 udp 658217562 155.212.198.75 43210 typ host generation 0 ufrag QkY7cvEnWvc34HO network-id 1 network-cost 10","sdpMid":"0","usernameFragment":"QkY7cvEnWvc34HO","sdpMLineIndex":0}},
                 "notification":"transmitted-data","participantId":910186980129,"type":"notification"}
                """.trimIndent(),
            )
        val c = parseTransmittedCandidate(msg)!!
        assertEquals("0", c.sdpMid)
        assertEquals(0, c.sdpMLineIndex)
        assertEquals(910186980129L, c.participantId)
        assertTrue(c.candidate.contains("155.212.198.75 43210 typ host"))
        assertNull(parseTransmittedSdp(msg))
    }
}
