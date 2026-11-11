package com.avenarius.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RotateHueTest {
    @Test
    fun turnsHueBothWays() {
        val red = Color(1f, 0f, 0f)
        val green = red.rotateHue(120f)
        assertTrue(green.green > 0.99f && green.red < 0.01f && green.blue < 0.01f, "$green")
        val blue = red.rotateHue(-120f)
        assertTrue(blue.blue > 0.99f && blue.red < 0.01f && blue.green < 0.01f, "$blue")
    }

    @Test
    fun greyHasNoHueToTurn() {
        val grey = Color(0.5f, 0.5f, 0.5f)
        val turned = grey.rotateHue(90f)
        assertEquals(grey.red, turned.red, 0.01f)
        assertEquals(grey.blue, turned.blue, 0.01f)
    }
}
