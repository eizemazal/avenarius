package com.avenarius.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BubbleToneTest {
    private fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun deepensSkyBlueUntilWhiteTextReads() {
        val sky = Color(0xFF3E9BE9)
        val bubble = bubbleTone(sky, Color.White)
        assertTrue(bubble.luminance() < sky.luminance())
        assertTrue(contrast(bubble, Color.White) >= 4.5f, "$bubble")
    }

    @Test
    fun lightensWhenTextIsDark() {
        val bubble = bubbleTone(Color(0xFF3E6E9B), Color.Black)
        assertTrue(contrast(bubble, Color.Black) >= 4.5f, "$bubble")
    }

    @Test
    fun keepsAnAlreadyReadableAccent() {
        val deep = Color(0xFF1A4B8C)
        assertEquals(deep, bubbleTone(deep, Color.White))
    }
}
