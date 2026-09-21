package com.avenarius.app.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Validates [parseInternalCallerParams] against the real `internalCallerParams` blob
 * captured from a genuine VIDEO_CHAT_START_ACTIVE reply (see the "max-calls-feasibility"
 * note and example_code/max_call_stage2_messages.json).
 */
class CallSetupParserTest {
    // Verbatim from a captured 1:1 video call's op-78 reply.
    private val realParams =
        """
        {"id":{"internal":1125899988598613,"external":"293679916"},"isConcurrent":false,
        "endpoint":"wss://videowebrtc.okcdn.ru/ws2?userId=1125899988598613&entityType=USER&conversationId=45aff6c1-b83f-4206-99c7-613e46c45aef&token=N7gi6j_tpX4cxVOm2bxWmE68CzBTwVh78lAh6Yzn3zk=",
        "wsIpAddresses":["155.212.204.11","155.212.204.196","155.212.205.229"],
        "wtEndpoint":"https://videowebrtc.okcdn.ru:23456/wt?userId=1125899988598613&conversationId=45aff6c1-b83f-4206-99c7-613e46c45aef",
        "wtIpAddresses":["155.212.204.196"],"clientType":"ONE_ME",
        "turn":{"urls":["turn:155.212.207.8:19302","turn:155.212.199.157:19302"],"username":"1789856220:1125899988598613","credential":"yfc2PeqNtfoUR09KffE6foVc2fg="},
        "stun":{"urls":["stun:155.212.207.8:19302"]},"deviceIdx":0}
        """.trimIndent().replace("\n", "")

    @Test
    fun parsesRealCallerParams() {
        val setup = parseInternalCallerParams("45aff6c1-b83f-4206-99c7-613e46c45aef", realParams)

        assertEquals("45aff6c1-b83f-4206-99c7-613e46c45aef", setup.conversationId)
        assertEquals(1125899988598613L, setup.internalId)
        assertEquals("293679916", setup.externalId)
        assertTrue(setup.wsEndpoint.startsWith("wss://videowebrtc.okcdn.ru/ws2?"))
        assertTrue(setup.wsEndpoint.contains("token="))
        assertEquals(
            "https://videowebrtc.okcdn.ru:23456/wt?userId=1125899988598613&conversationId=45aff6c1-b83f-4206-99c7-613e46c45aef",
            setup.wtEndpoint,
        )

        // TURN first (with creds), then STUN.
        assertEquals(2, setup.iceServers.size)
        val turn = setup.iceServers[0]
        assertEquals(listOf("turn:155.212.207.8:19302", "turn:155.212.199.157:19302"), turn.urls)
        assertEquals("1789856220:1125899988598613", turn.username)
        assertEquals("yfc2PeqNtfoUR09KffE6foVc2fg=", turn.credential)
        val stun = setup.iceServers[1]
        assertEquals(listOf("stun:155.212.207.8:19302"), stun.urls)
        assertEquals(null, stun.username)
    }

    @Test
    fun parsesRealVcpFromIncomingPush() {
        // Verbatim `vcp` from a real NOTIF_CALL_START (len:base64(LZ4(JSON))).
        val vcp =
            "591:8Ux7InRrbiI6InZDdzhDanFHMFhIWU9KS0c3bVRJVEU2OEN6QlR3Vmg3OGxBaDZZem4zems9Iiwid3NlIjoid3NzOi8vdmlkZW93ZWJydGMub2tjZG4ucnUvd3MyJwD3CGlwIjpbIjE1NS4yMTIuMjA0LjE5NiIsEgBZNS4yMjkSAKA0LjExIl0sInd0ZQBPaHR0cGcABZA6MjM0NTYvd3STAB90bAAAHDFZAA99AAJoXSwidmNhbQBVY2FsbHPOABEiIAAMXwAAuQAdNWAADRIAGjPvAEA0LjEy3QDwFHNyY3AiOiJvbmVfbWUiLCJldCI6MTc5MDAyNTQxOSwic3RukQBUc3R1bjo/AcAxOTkuMTYyOjE5MzBfASF0ciQAP3R1ciQABBksGwBKMjA3Lj4AQXUiOiJ1APIHNTQwMzk6MTEyNTg5OTk4ODU5ODYxM2MAAKcA8AxaTHFxaWtKWGMyaTYxc1hvRWpkMXdSWDZla2MPApBpdiI6dHJ1ZX0="

        val setup = parseVcp("conv-1", vcp)

        assertEquals("conv-1", setup.conversationId)
        assertEquals(1125899988598613L, setup.internalId) // from the TURN username "<epoch>:<internalId>"
        assertTrue(setup.wsEndpoint.startsWith("wss://videowebrtc.okcdn.ru/ws2?"), setup.wsEndpoint)
        assertTrue(setup.wsEndpoint.contains("token=vCw8CjqG"), setup.wsEndpoint)
        assertTrue(setup.wsEndpoint.contains("conversationId=conv-1"))
        // TURN (2 urls, creds) + STUN.
        assertEquals(2, setup.iceServers.size)
        assertEquals(2, setup.iceServers[0].urls.size)
        assertEquals("1790054039:1125899988598613", setup.iceServers[0].username)
        assertTrue(setup.iceServers[1].urls[0].startsWith("stun:"))
    }

    @Test
    fun fallsBackToPassedConversationIdWhenBlobOmitsIt() {
        val setup = parseInternalCallerParams("fallback-id", """{"id":{"internal":7,"external":"9"}}""")
        assertEquals("fallback-id", setup.conversationId)
        assertEquals(7L, setup.internalId)
        assertTrue(setup.iceServers.isEmpty())
    }
}
