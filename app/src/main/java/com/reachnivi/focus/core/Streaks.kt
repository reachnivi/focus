package com.reachnivi.focus.core

import java.time.LocalDate

data class AppChange(val packageName: String, val changeMs: Long)

data class WeeklyRecap(
    val thisWeekMs: Long,
    val lastWeekMs: Long,
    /** Negative = less screen time than last week. Null when last week has no data. */
    val changePercent: Int?,
    /** The lightest day of this week (least screen time), among days with data. */
    val bestDay: LocalDate?,
    val bestDayMs: Long,
    /** The app whose time fell the most, if any fell. */
    val biggestDrop: AppChange?,
)

object Streaks {

    /**
     * Consecutive finished days at or under the goal, counting back from yesterday. Today is still
     * in progress, so it never breaks the streak; a day with no data does.
     */
    fun underGoalStreak(dailyTotals: Map<LocalDate, Long>, goalMs: Long, today: LocalDate): Int {
        var day = today.minusDays(1)
        var streak = 0
        while (true) {
            val total = dailyTotals[day] ?: break
            if (total > goalMs) break
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /** Consecutive days with at least one completed focus session, ending today (or yesterday). */
    fun focusStreak(completedDays: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in completedDays) today else today.minusDays(1)
        var streak = 0
        while (day in completedDays) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    fun weeklyRecap(thisWeek: Map<LocalDate, DayUsage>, lastWeek: Map<LocalDate, DayUsage>): WeeklyRecap {
        val thisTotal = thisWeek.values.sumOf { it.totalMs }
        val lastTotal = lastWeek.values.sumOf { it.totalMs }
        val change = if (lastTotal > 0) (((thisTotal - lastTotal) * 100) / lastTotal).toInt() else null
        val best = thisWeek.entries.filter { it.value.totalMs > 0 }.minByOrNull { it.value.totalMs }
        val thisApps = DayUsage.merge(thisWeek.values).perAppMs
        val lastApps = DayUsage.merge(lastWeek.values).perAppMs
        val drop = (thisApps.keys + lastApps.keys)
            .map { AppChange(it, (thisApps[it] ?: 0L) - (lastApps[it] ?: 0L)) }
            .filter { it.changeMs < 0 }
            .minByOrNull { it.changeMs }
        return WeeklyRecap(thisTotal, lastTotal, change, best?.key, best?.value?.totalMs ?: 0L, drop)
    }
}
