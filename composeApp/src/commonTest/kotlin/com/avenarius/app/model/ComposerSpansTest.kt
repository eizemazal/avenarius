package com.avenarius.app.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** The composer's formatting: the bar's toggle, and the runs following later typing. */
class ComposerSpansTest {
    @Test
    fun theBarPutsARunOnAndTheSameTapTakesItOff() {
        val bold = listOf<TextSpan>().toggled("STRONG", 0, 5)
        assertEquals(listOf(TextSpan("STRONG", 0, 5)), bold)
        assertEquals(listOf(TextSpan("STRONG", 0, 5), TextSpan("EMPHASIZED", 0, 5)), bold.toggled("EMPHASIZED", 0, 5))
        assertEquals(emptyList(), bold.toggled("STRONG", 0, 5))
        assertEquals(bold, bold.toggled("STRONG", 3, 0), "nothing selected: nothing changes")
    }

    @Test
    fun runsFollowTypingBeforeInsideAndAfterThem() {
        // "hello world", "world" in bold.
        var text = "hello world"
        var spans = listOf(TextSpan("STRONG", 6, 5))

        fun type(next: String) {
            spans = remapSpans(text, next, spans)
            text = next
        }
        type("oh, hello world") // typed before: the run shifts
        assertEquals(listOf(TextSpan("STRONG", 10, 5)), spans)
        type("oh, hello woorld") // typed inside: it grows
        assertEquals(listOf(TextSpan("STRONG", 10, 6)), spans)
        type("oh, hello woorld!") // typed right after: not part of it
        assertEquals(listOf(TextSpan("STRONG", 10, 6)), spans)
        type("oh, hello !") // the run's text deleted: the run goes
        assertEquals(emptyList(), spans)
    }
}
