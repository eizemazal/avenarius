package com.avenarius.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class PluralTest {
    private fun files(n: Int) = plural(n, "вложение", "вложения", "вложений")

    @Test
    fun picksOneFewOrMany() {
        assertEquals("вложение", files(1))
        assertEquals("вложения", files(2))
        assertEquals("вложения", files(4))
        assertEquals("вложений", files(5))
        assertEquals("вложений", files(0))
        assertEquals("вложений", files(11))
        assertEquals("вложений", files(12))
        assertEquals("вложений", files(14))
        assertEquals("вложение", files(21))
        assertEquals("вложения", files(22))
        assertEquals("вложений", files(111))
    }
}
