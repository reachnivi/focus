package com.reachnivi.focus.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StreaksTest {
    private val today = LocalDate.of(2026, 11, 15)
    private val h = 3_600_000L
    private fun day(n: Long) = today.minusDays(n)
    private fun usage(vararg apps: Pair<String, Long>) = DayUsage(apps.toMap(), List(24) { 0L }, 0)

    @Test
    fun `under-goal streak ends yesterday and stops at a miss or a gap`() {
        val totals = mapOf(
            today to 9 * h, // today in progress, ignored
            day(1) to 2 * h,
            day(2) to 3 * h,
            day(3) to 1 * h,
            day(4) to 5 * h, // over goal
            day(5) to 1 * h,
        )
        assertEquals(3, Streaks.underGoalStreak(totals, goalMs = 3 * h, today = today))
        assertEquals(0, Streaks.underGoalStreak(totals - day(1), goalMs = 3 * h, today = today))
    }

    @Test
    fun `focus streak counts today when done, else from yesterday`() {
        val days = setOf(day(1), day(2), day(4))
        assertEquals(2, Streaks.focusStreak(days, today))
        assertEquals(3, Streaks.focusStreak(days + today, today))
        assertEquals(0, Streaks.focusStreak(setOf(day(3)), today))
    }

    @Test
    fun `weekly recap compares weeks and finds the biggest drop`() {
        val thisWeek = mapOf(day(2) to usage("insta" to 1 * h, "yt" to 2 * h), day(1) to usage("insta" to 1 * h))
        val lastWeek = mapOf(day(9) to usage("insta" to 4 * h, "yt" to 1 * h))
        val r = Streaks.weeklyRecap(thisWeek, lastWeek)
        assertEquals(5 * h, r.lastWeekMs)
        assertEquals(4 * h, r.thisWeekMs)
        assertEquals(-20, r.changePercent)
        assertEquals(day(1), r.bestDay)
        assertEquals(AppChange("insta", -2 * h), r.biggestDrop)
        assertNull(Streaks.weeklyRecap(thisWeek, emptyMap()).changePercent)
    }
}
