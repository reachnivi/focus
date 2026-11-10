package com.reachnivi.focus.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ConnectionsTest {
    private val today = LocalDate.of(2026, 11, 11) // a Wednesday
    private val amma = CirclePerson(1, "Amma", everyDays = 3)
    private val rahul = CirclePerson(2, "Rahul", everyDays = 7)
    private val priya = CirclePerson(3, "Priya", everyDays = 14)

    @Test
    fun `never called ranks first, then most overdue relative to interval`() {
        val last = mapOf(1L to today.minusDays(6), 2L to today.minusDays(7)) // Amma 2x overdue, Rahul exactly due
        val ranked = Connections.ranked(listOf(amma, rahul, priya), last, today)
        assertEquals(listOf("Priya", "Amma", "Rahul"), ranked.map { it.person.name })
        assertNull(ranked.first().daysSince)
        assertTrue(ranked[2].isDue)
    }

    @Test
    fun `skipped people are not suggested today`() {
        val last = mapOf(1L to today.minusDays(6), 2L to today.minusDays(1), 3L to today.minusDays(1))
        assertEquals("Amma", Connections.nextDue(listOf(amma, rahul, priya), last, today)?.person?.name)
        assertEquals("Rahul", Connections.nextDue(listOf(amma, rahul, priya), last, today, skippedToday = setOf(1L))?.person?.name)
        assertNull(Connections.nextDue(listOf(amma), last, today, skippedToday = setOf(1L)))
    }

    @Test
    fun `week streak counts consecutive weeks and tolerates an empty current week`() {
        val calls = listOf(
            LocalDate.of(2026, 11, 3), // last week (Mon 2 Nov)
            LocalDate.of(2026, 10, 29), // week of 26 Oct
            LocalDate.of(2026, 10, 20), // week of 19 Oct
            LocalDate.of(2026, 10, 5), // gap: week of 12 Oct missing
        )
        assertEquals(3, Connections.weekStreak(calls, today))
        assertEquals(4, Connections.weekStreak(calls + today, today))
        assertEquals(0, Connections.weekStreak(listOf(LocalDate.of(2026, 10, 20)), today))
        assertEquals(0, Connections.weekStreak(emptyList(), today))
    }

    @Test
    fun `talk time accumulates only while in call and caps gaps`() {
        var t = 0
        repeat(36) { t = Connections.accumulate(t, inCall = true, secondsSinceLastSample = 5) }
        assertEquals(180, t)
        assertEquals(180, Connections.accumulate(t, inCall = false, secondsSinceLastSample = 5))
        assertEquals(185, Connections.accumulate(t, inCall = true, secondsSinceLastSample = 600))
    }

    @Test
    fun `outcome and xp`() {
        assertEquals(CallOutcome.COMPLETED, Connections.outcome(180))
        assertEquals(CallOutcome.SHORT, Connections.outcome(60))
        assertEquals(CallOutcome.NO_CALL, Connections.outcome(0))
        assertEquals(30, Connections.xpFor(CallOutcome.COMPLETED, insteadOfScrolling = true))
        assertEquals(20, Connections.xpFor(CallOutcome.COMPLETED, insteadOfScrolling = false))
        assertEquals(0, Connections.xpFor(CallOutcome.SHORT, insteadOfScrolling = true))
    }
}
