package com.avenarius.app.ui

import com.avenarius.app.net.Presence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LastSeenTextTest {
    private val now = 1_793_945_801_000L

    @Test
    fun statusBucketsWinOverSeen() {
        assertEquals("в сети", lastSeenText(Presence(1, online = true, seen = 1, status = 1), now))
        assertEquals("был(а) недавно", lastSeenText(Presence(1, online = false, seen = now / 1000, status = 2), now))
        assertEquals("был(а) давно", lastSeenText(Presence(1, online = false, seen = now / 1000, status = 3), now))
    }

    @Test
    fun exactSeenIsTodayYesterdayOrDate() {
        val todayText = lastSeenText(Presence(1, online = false, seen = now / 1000 - 60), now)!!
        assertTrue(todayText.startsWith("был(а) в сети в "), todayText)
        val yesterday = lastSeenText(Presence(1, online = false, seen = now / 1000 - 86_400), now)!!
        assertTrue(yesterday.startsWith("был(а) в сети вчера в "), yesterday)
        val old = lastSeenText(Presence(1, online = false, seen = now / 1000 - 30 * 86_400), now)!!
        assertEquals("был(а) в сети ${formatDate(now - 30 * 86_400_000L)}", old)
    }

    @Test
    fun nothingKnownGivesNull() {
        assertNull(lastSeenText(Presence(1, online = false), now))
    }
}

class ParticipantsLabelTest {
    @Test
    fun pluralFormsAndThousands() {
        assertEquals(
            "1 участник",
            com.avenarius.app.ui.screens
                .participantsLabel(1),
        )
        assertEquals(
            "3 участника",
            com.avenarius.app.ui.screens
                .participantsLabel(3),
        )
        assertEquals(
            "12 участников",
            com.avenarius.app.ui.screens
                .participantsLabel(12),
        )
        assertEquals(
            "243 783 подписчика",
            com.avenarius.app.ui.screens
                .participantsLabel(243_783, channel = true),
        )
        assertEquals(
            "1 000 000 подписчиков",
            com.avenarius.app.ui.screens
                .participantsLabel(1_000_000, channel = true),
        )
    }
}
