package com.reachnivi.focus.data

import com.reachnivi.focus.core.Garden
import com.reachnivi.focus.core.GardenInputs
import com.reachnivi.focus.core.GardenLevel
import com.reachnivi.focus.core.GardenSource
import com.reachnivi.focus.core.SessionInput
import com.reachnivi.focus.usage.UsageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class GardenState(
    val xp: Int,
    val level: GardenLevel,
    /** 0.25..1: how lush the garden looks today. */
    val health: Float,
    val healthReason: String?,
    val today: Map<GardenSource, Int>,
    val total: Map<GardenSource, Int>,
)

private data class Activity(
    val connections: List<ConnectionEventEntity>,
    val pauses: List<PauseEventEntity>,
    val sessions: List<FocusSessionEntity>,
    val summaries: List<DailySummaryEntity>,
)

/** XP is derived from what already happened, so it can't drift and past activity counts from day one. */
class GardenRepository(
    dao: FocusDao,
    settings: SettingsRepository,
    private val usage: UsageRepository,
    /** Steps per day, oldest first; empty until step counting exists. */
    steps: Flow<Map<LocalDate, Int>> = flowOf(emptyMap()),
) {
    private val questDates = combine(dao.observePhotoQuests(), dao.observeStacks()) { q, st -> q to st }
    private val todayUsageMs = MutableStateFlow(0L)

    private val activity = combine(
        dao.observeConnectionEvents(),
        dao.observePauseEventsSince(0),
        dao.observeSessionsSince(0),
        dao.observeSummaries(),
    ) { c, p, s, d -> Activity(c, p, s, d) }

    val state: Flow<GardenState> = combine(activity, settings.settings, steps, todayUsageMs, questDates) { a, s, stepsByDay, todayMs, questsAndStacks ->
        val (quests, stacks) = questsAndStacks
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val goalMs = s.dailyGoalMinutes * 60_000L
        val dayOf = { ms: Long -> Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() }
        val now = System.currentTimeMillis()

        fun inputs(onlyToday: Boolean): GardenInputs {
            val keep = { ms: Long -> !onlyToday || dayOf(ms) == today }
            return GardenInputs(
                connectionXp = a.connections.filter { keep(it.startedAt) }.sumOf { it.xp },
                letGos = a.pauses.count { it.outcome == PauseEventEntity.SKIPPED && keep(it.at) },
                sessions = a.sessions.filter { keep(it.startedAt) }
                    .map { SessionInput(it.completed, (it.focusedMs(now) / 60_000).toInt()) },
                underGoalDays = if (onlyToday) 0 else a.summaries.count { row ->
                    val d = runCatching { LocalDate.parse(row.date) }.getOrNull()
                    d != null && d.isBefore(today) && row.totalMs in 1..goalMs
                },
                stepsByDay = stepsByDay.filterKeys { !onlyToday || it == today }.values.toList(),
                photoQuests = quests.count { !onlyToday || it.date == today.toString() },
                stacksCompleted = stacks.count { it.outcome == "COMPLETED" && keep(it.endedAt) },
            )
        }

        val total = Garden.breakdown(inputs(onlyToday = false))
        val todayXp = Garden.breakdown(inputs(onlyToday = true))
        val opensToday = a.pauses.count { it.outcome == PauseEventEntity.OPENED && dayOf(it.at) == today }
        val letGosToday = a.pauses.count { it.outcome == PauseEventEntity.SKIPPED && dayOf(it.at) == today }
        val health = Garden.health(todayMs, goalMs, opensToday, letGosToday)
        val xp = total.values.sum()
        GardenState(
            xp = xp,
            level = Garden.level(xp),
            health = health,
            healthReason = when {
                health >= 0.95f -> null
                todayMs > goalMs && opensToday > letGosToday -> "Over your goal today, and $opensToday quick opens"
                todayMs > goalMs -> "A little over your screen-time goal today"
                else -> "$opensToday apps opened past a mindful pause today"
            },
            today = todayXp,
            total = total,
        )
    }

    /** Re-reads today's screen time (call on resume); the rest updates by itself. */
    suspend fun refreshToday() {
        if (usage.hasAccess()) {
            todayUsageMs.value = runCatching { usage.day(LocalDate.now()).totalMs }.getOrDefault(todayUsageMs.value)
        }
    }

}
