package com.avenarius.app.net

import kotlin.test.Test
import kotlin.test.assertEquals

class MaxClientTest {
    private val client = MaxClient() // constructible without a connection

    @Test
    fun dialogChatIdIsXorAndSymmetric() {
        val me = 293679916L
        val other = 286642699L
        val expected = me xor other
        assertEquals(expected, client.dialogChatId(me, other))
        // The same dialog id regardless of argument order.
        assertEquals(client.dialogChatId(me, other), client.dialogChatId(other, me))
    }

    @Test
    fun savedMessagesSelfChatIsZero() {
        val me = 123456789L
        assertEquals(0L, client.dialogChatId(me, me))
    }
}
