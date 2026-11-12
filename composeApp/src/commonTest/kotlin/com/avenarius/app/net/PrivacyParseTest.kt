package com.avenarius.app.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class PrivacyParseTest {
    private fun parse(json: String) = Json.parseToJsonElement(json).jsonObject.toPrivacy()

    @Test
    fun readsServerUserConfig() {
        // Shape seen in a real login reply's config.user (other keys omitted).
        val p =
            parse(
                """{"SHOW_READ_MARK":true,"SEARCH_BY_PHONE":"ALL","INCOMING_CALL":"CONTACTS","HIDDEN":false,"CHATS_INVITE":"CONTACTS","VIBR":true}""",
            )
        assertEquals(Privacy(Privacy.ALL, Privacy.CONTACTS, Privacy.CONTACTS, hideOnline = false, showReadMarks = true), p)
    }

    @Test
    fun nobodyIsReadInBothSpellings() {
        val p = parse("""{"INCOMING_CALL":"NOBODY","CHATS_INVITE":"_NONE_"}""")

        assertEquals(Privacy.NOBODY, p.incomingCall)
        assertEquals(Privacy.NOBODY, p.chatsInvite)
    }

    @Test
    fun missingKeysFallBackToOpenDefaults() {
        assertEquals(Privacy(), JsonObject(emptyMap()).toPrivacy())
        assertEquals(true, parse("""{"HIDDEN":1}""").hideOnline)
    }
}
