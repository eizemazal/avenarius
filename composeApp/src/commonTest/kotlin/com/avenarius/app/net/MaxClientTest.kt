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

    // --- file names ---

    @Test
    fun quotedServerFileNameIsUnwrapped() {
        // Files uploaded with a quoted `filename=` come back with the quotes stored
        // as part of the name, which hid the extension from the phone.
        assertEquals("document.pdf", "\"document.pdf\"".cleanFileName())
        assertEquals("pdf", "\"document.pdf\"".cleanFileName().substringAfterLast('.'))
    }

    @Test
    fun ordinaryFileNamesAreLeftAlone() {
        assertEquals("отчёт 2026.xlsx", "отчёт 2026.xlsx".cleanFileName())
        // A quote in the middle of a name isn't decoration — keep it.
        assertEquals("say\"hi\".txt", "say\"hi\".txt".cleanFileName())
    }

    @Test
    fun uploadHeaderNameIsUnquotedAndSingleLine() {
        assertEquals("document.pdf", headerFileName("\"document.pdf\""))
        // A line break in a name would otherwise inject a header.
        assertEquals("evil.pdf", headerFileName("evil.pdf\r\nX-Injected: 1"))
        assertEquals("file", headerFileName("   "))
    }
}
