package com.avenarius.app.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PollTest {
    private fun poll(
        settings: Int,
        mine: Set<Int> = emptySet(),
    ) = Poll(
        pollId = 1,
        title = "Обед?",
        settings = settings,
        answers = listOf(PollAnswer(1, "Да", votes = 2, mine = 1 in mine), PollAnswer(2, "Нет", votes = 1, mine = 2 in mine)),
        total = 3,
    )

    @Test
    fun aTapVotesOnceAndOnlyRevotingLetsItChangeOrRetract() {
        assertEquals(setOf(2), poll(settings = 0).votesAfterTapping(2))
        assertNull(poll(settings = 0, mine = setOf(1)).votesAfterTapping(2), "no revote: the vote stands")
        assertEquals(emptySet(), poll(settings = 4, mine = setOf(1)).votesAfterTapping(1), "our answer again retracts")
        assertEquals(setOf(2), poll(settings = 4, mine = setOf(1)).votesAfterTapping(2))
        assertEquals(setOf(1, 2), poll(settings = 2 or 4, mine = setOf(1)).votesAfterTapping(2), "multiple choice adds")
        assertNull(poll(settings = 8).votesAfterTapping(1), "closed")
    }

    @Test
    fun ourVotesMoveTheCounts() {
        val voted = poll(settings = 4).withMyVotes(setOf(2))
        assertEquals(listOf(2, 2), voted.answers.map { it.votes })
        assertEquals(4, voted.total)

        val retracted = voted.withMyVotes(emptySet())
        assertEquals(listOf(2, 1), retracted.answers.map { it.votes })
        assertEquals(3, retracted.total)
    }
}
