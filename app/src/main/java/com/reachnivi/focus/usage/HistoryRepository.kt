package com.reachnivi.focus.usage

import com.reachnivi.focus.core.DayUsage
import com.reachnivi.focus.core.Streaks
import com.reachnivi.focus.core.WeeklyRecap
import com.reachnivi.focus.data.FocusDao
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class HistorySummary(
    val underGoalStreak: Int,
    val focusStreak: Int,
    val recap: WeeklyRecap,
)

/**
 * Longer history than Android keeps: stored daily snapshots for older days, live usage events
 * for the last week (live wins where both exist).
 */
class HistoryRepository(private val usage: UsageRepository, private val dao: FocusDao) {

    suspend fun lastDays(count: Int = 14): Map<LocalDate, DayUsage> {
        val today = LocalDate.now()
        val from = today.minusDays(count - 1L)
        val summaries = dao.summariesFrom(from.toString()).associateBy { it.date }
        val stored = dao.appUsageFrom(from.toString())
            .groupBy { it.date }
            .mapNotNull { (date, rows) ->
                val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return@mapNotNull null
                day to DayUsage(rows.associate { it.packageName to it.foregroundMs }, List(24) { 0L }, summaries[date]?.unlocks ?: 0)
            }
            .toMap()
        val live = if (usage.hasAccess()) usage.days(today.minusDays(6), today).toMap() else emptyMap()
        return stored + live.filterValues { it.perAppMs.isNotEmpty() || it.unlocks > 0 }
    }

    suspend fun summary(goalMinutes: Int): HistorySummary {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val days = lastDays(28)
        val totals = days.mapValues { it.value.totalMs }
        val since = today.minusDays(60).atStartOfDay(zone).toInstant().toEpochMilli()
        val focusDays = dao.sessionsFrom(since)
            .filter { it.completed }
            .map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
            .toSet()
        val thisWeek = days.filterKeys { !it.isBefore(today.minusDays(6)) }
        val lastWeek = days.filterKeys { it.isBefore(today.minusDays(6)) && !it.isBefore(today.minusDays(13)) }
        return HistorySummary(
            underGoalStreak = Streaks.underGoalStreak(totals, goalMinutes * 60_000L, today),
            focusStreak = Streaks.focusStreak(focusDays, today),
            recap = Streaks.weeklyRecap(thisWeek, lastWeek),
        )
    }
}
