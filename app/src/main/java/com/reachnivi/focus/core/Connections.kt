package com.reachnivi.focus.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Someone in the user's inner circle and how often they want to talk. */
data class CirclePerson(val id: Long, val name: String, val everyDays: Int)

/** A circle member ranked by how overdue a conversation is. */
data class DuePerson(
    val person: CirclePerson,
    /** Days since the last completed call, or null if none was ever logged. */
    val daysSince: Int?,
) {
    /** 1.0 = exactly due; above 1 = overdue. Never-called people rank first. */
    val overdueRatio: Double get() = daysSince?.let { it.toDouble() / person.everyDays } ?: Double.MAX_VALUE
    val isDue: Boolean get() = daysSince == null || daysSince >= person.everyDays
}

enum class CallOutcome { COMPLETED, SHORT, NO_CALL }

object Connections {

    const val REQUIRED_TALK_SEC = 180
    const val SAMPLE_INTERVAL_SEC = 5
    const val XP_COMPLETED = 20
    const val XP_INSTEAD_OF_SCROLLING = 10

    /** Circle members, most overdue first; people skipped today go last. */
    fun ranked(
        circle: List<CirclePerson>,
        lastTalked: Map<Long, LocalDate>,
        today: LocalDate,
        skippedToday: Set<Long> = emptySet(),
    ): List<DuePerson> = circle
        .map { p -> DuePerson(p, lastTalked[p.id]?.let { ChronoUnit.DAYS.between(it, today).toInt().coerceAtLeast(0) }) }
        .sortedWith(
            compareBy<DuePerson> { it.person.id in skippedToday }
                .thenByDescending { it.overdueRatio }
                .thenBy { it.person.id },
        )

    /** Today's suggestion: the most overdue person not skipped today, if anyone. */
    fun nextDue(
        circle: List<CirclePerson>,
        lastTalked: Map<Long, LocalDate>,
        today: LocalDate,
        skippedToday: Set<Long> = emptySet(),
    ): DuePerson? = ranked(circle, lastTalked, today, skippedToday).firstOrNull { it.person.id !in skippedToday }

    /**
     * Consecutive weeks (Mon–Sun) with at least one completed call. The current week counts once
     * it has a call; until then the streak ending last week is still alive.
     */
    fun weekStreak(completedDays: Collection<LocalDate>, today: LocalDate): Int {
        val weeks = completedDays.map { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.toSet()
        var week = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (week !in weeks) week = week.minusWeeks(1)
        var streak = 0
        while (week in weeks) {
            streak++
            week = week.minusWeeks(1)
        }
        return streak
    }

    /**
     * Adds one audio-mode sample to the talk time. Gaps longer than two intervals (the service was
     * paused or killed) only count one interval, so a missed stretch is never credited as talk.
     */
    fun accumulate(talkedSec: Int, inCall: Boolean, secondsSinceLastSample: Long): Int {
        if (!inCall || secondsSinceLastSample <= 0) return talkedSec
        val credited = if (secondsSinceLastSample > SAMPLE_INTERVAL_SEC * 2) SAMPLE_INTERVAL_SEC.toLong() else secondsSinceLastSample
        return talkedSec + credited.toInt()
    }

    fun outcome(talkedSec: Int): CallOutcome = when {
        talkedSec >= REQUIRED_TALK_SEC -> CallOutcome.COMPLETED
        talkedSec > 0 -> CallOutcome.SHORT
        else -> CallOutcome.NO_CALL
    }

    fun xpFor(outcome: CallOutcome, insteadOfScrolling: Boolean): Int =
        if (outcome != CallOutcome.COMPLETED) 0 else XP_COMPLETED + if (insteadOfScrolling) XP_INSTEAD_OF_SCROLLING else 0
}
