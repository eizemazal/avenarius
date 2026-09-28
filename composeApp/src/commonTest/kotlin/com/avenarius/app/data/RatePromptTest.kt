package com.avenarius.app.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RatePromptTest {
    private val day = 24L * 60 * 60 * 1000
    private val start = 1_700_000_000_000L

    /** A user who installed at [start] and used the app on [days] consecutive days. */
    private fun regularUser(
        days: Int = 14,
        sent: Int = 25,
    ): Pair<RatePrompt, Long> {
        val p = RatePrompt(InMemoryStorage())
        var now = start
        repeat(days) {
            p.recordUse(now)
            now += day
        }
        repeat(sent) { p.recordMessageSent() }
        return p to now
    }

    @Test
    fun regularUserQualifies() {
        val (p, now) = regularUser()
        assertTrue(p.isEligible(now))
    }

    @Test
    fun tooNewOrTooLittleUseDoesNotQualify() {
        assertFalse(regularUser(days = 5).let { (p, now) -> p.isEligible(now) }) // few active days, too recent
        assertFalse(regularUser(sent = 3).let { (p, now) -> p.isEligible(now) }) // barely writes
        // Many opens on the same day count as one active day.
        val p = RatePrompt(InMemoryStorage())
        repeat(30) { p.recordUse(start + it * 60_000L) }
        repeat(30) { p.recordMessageSent() }
        assertFalse(p.isEligible(start + 20 * day))
    }

    @Test
    fun recentProblemSuppressesThenExpires() {
        val (p, now) = regularUser()
        p.recordProblem(now - day)
        assertFalse(p.isEligible(now))
        assertTrue(p.isEligible(now + 3 * day))
    }

    @Test
    fun laterHidesForAMonthAndTheSecondLaterIsFinal() {
        val (p, now) = regularUser()
        p.later(now)
        assertFalse(p.isEligible(now + 10 * day))
        assertTrue(p.isEligible(now + 31 * day))
        p.later(now + 31 * day)
        assertFalse(p.isEligible(now + 400 * day))
    }

    @Test
    fun neverAndRatedAreFinal() {
        regularUser().let { (p, now) ->
            p.never()
            assertFalse(p.isEligible(now + 400 * day))
        }
        regularUser().let { (p, now) ->
            p.rated()
            assertFalse(p.isEligible(now + 400 * day))
        }
    }
}
