package com.avenarius.app.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class InitialsTest {
    @Test
    fun takesFirstLettersOfFirstTwoWords() {
        assertEquals("АП", initials("Анна Петрова"))
        assertEquals("АП", initials("  анна   петрова иванова "))
        assertEquals("Г", initials("Гентош "))
    }

    @Test
    fun skipsPunctuationAndEmoji() {
        assertEquals("УА", initials("ул. Александра"))
        assertEquals("В", initials("🎉 (Вася)"))
        assertEquals("?", initials("  "))
    }
}
