package com.avenarius.app.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * AUDIO_PLAY answers with several links whose key names we can't know, so they are
 * recognised by shape.
 */
class AudioLinkTest {
    private fun reply(json: String) = Json.parseToJsonElement(json) as JsonObject

    @Test
    fun m4aIsPreferredOverTheOtherFormats() {
        val payload =
            reply(
                """{"opusUrl":"https://cdn/a.opus","m4aUrl":"https://cdn/a.m4a","mp3Url":"https://cdn/a.mp3"}""",
            )
        assertEquals("https://cdn/a.m4a", payload.audioLink())
    }

    @Test
    fun mp3IsTakenWhenThereIsNoM4a() {
        assertEquals(
            "https://cdn/a.mp3",
            reply("""{"opus":"https://cdn/a.opus","mp3":"https://cdn/a.mp3"}""").audioLink(),
        )
    }

    @Test
    fun anyLinkIsBetterThanNone() {
        assertEquals(
            "https://cdn/a.opus",
            reply("""{"whateverTheyCallIt":"https://cdn/a.opus"}""").audioLink(),
        )
    }

    @Test
    fun linksNestedOneLevelDownAreFound() {
        assertEquals(
            "https://cdn/a.m4a",
            reply("""{"audio":{"m4a":"https://cdn/a.m4a"}}""").audioLink(),
        )
    }

    @Test
    fun nonLinkFieldsAreIgnored() {
        assertNull(reply("""{"audioId":42,"status":"ok"}""").audioLink())
    }
}
