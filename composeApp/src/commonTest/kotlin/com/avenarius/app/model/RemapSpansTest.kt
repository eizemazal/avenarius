package com.avenarius.app.model

import kotlin.test.Test
import kotlin.test.assertEquals

class RemapSpansTest {
    private val bold = TextSpan("STRONG", from = 0, length = 5)

    @Test
    fun runsBeforeAndAfterTheEditSurviveIt() {
        // "Hello big world" -> "Hello small world": the bold head stays, the italic tail shifts by 2.
        val tail = TextSpan("EMPHASIZED", from = 10, length = 5)

        assertEquals(
            listOf(bold, tail.copy(from = 12)),
            remapSpans("Hello big world", "Hello small world", listOf(bold, tail)),
        )
    }

    @Test
    fun aRunWrappingTheWholeEditStretches() {
        val all = TextSpan("STRONG", from = 0, length = 10)

        assertEquals(listOf(all.copy(length = 11)), remapSpans("hello wrld", "hello world", listOf(all)))
        // Text added right after a run is not assumed to belong to it.
        assertEquals(listOf(all), remapSpans("hello wrld", "hello wrld!", listOf(all)))
    }

    @Test
    fun aRunCutByTheEditIsDroppedNotGuessed() {
        // The edit replaces "llo w" with "y": bold "hello" is cut, so it goes.
        assertEquals(emptyList(), remapSpans("hello world", "heyorld", listOf(bold)))
    }

    @Test
    fun mentionsAndUrlLessLinksAreNotSentBack() {
        val spans = listOf(TextSpan("USER_MENTION", 0, 5), TextSpan("LINK", 0, 5, url = null), TextSpan("LINK", 0, 5, url = "https://a.b"))

        assertEquals(listOf(spans[2]), remapSpans("hello", "hello!", spans))
    }
}
