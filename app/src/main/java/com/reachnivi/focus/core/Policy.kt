package com.reachnivi.focus.core

import java.time.DayOfWeek
import java.time.LocalDateTime

/** When the block list (apps + websites) is enforced. Daily limits always apply. */
enum class BlockMode { ALWAYS, SCHEDULED, FOCUS_ONLY }

enum class BlockReason { FOCUS_SESSION, SCHEDULE, ALWAYS_ON, DAILY_LIMIT }

sealed interface BlockDecision {
    data object Allow : BlockDecision

    /**
     * @param untilMillis when the block lifts on its own, or null when it is open-ended.
     * @param strict a strict block can't be bypassed with a temporary pass.
     */
    data class Block(
        val reason: BlockReason,
        val untilMillis: Long?,
        val strict: Boolean,
    ) : BlockDecision
}

data class ActiveSession(
    val id: Long,
    val startedAt: Long,
    val endsAt: Long,
    val strict: Boolean,
) {
    fun isRunning(now: Long): Boolean = now in startedAt until endsAt
}

/**
 * A recurring time window, e.g. weekdays 09:00–18:00.
 * Minutes are minutes since midnight. A window where start > end crosses midnight
 * and belongs to the day it starts on. start == end means the whole day.
 */
data class ScheduleRule(
    val id: Long = 0,
    val name: String,
    val days: Set<DayOfWeek>,
    val startMinute: Int,
    val endMinute: Int,
    val enabled: Boolean = true,
) {
    val isAllDay: Boolean get() = startMinute == endMinute
    val crossesMidnight: Boolean get() = startMinute > endMinute

    fun isActiveAt(t: LocalDateTime): Boolean {
        if (!enabled || days.isEmpty()) return false
        val minute = t.hour * 60 + t.minute
        val today = t.dayOfWeek
        return when {
            isAllDay -> today in days
            !crossesMidnight -> today in days && minute >= startMinute && minute < endMinute
            else -> (today in days && minute >= startMinute) ||
                (today.minus(1) in days && minute < endMinute)
        }
    }

    /** When the occurrence that is active at [t] ends. Only meaningful if [isActiveAt]. */
    fun currentWindowEnd(t: LocalDateTime): LocalDateTime {
        val date = t.toLocalDate()
        val minute = t.hour * 60 + t.minute
        return when {
            isAllDay -> date.plusDays(1).atStartOfDay()
            !crossesMidnight -> date.atStartOfDay().plusMinutes(endMinute.toLong())
            minute >= startMinute -> date.plusDays(1).atStartOfDay().plusMinutes(endMinute.toLong())
            else -> date.atStartOfDay().plusMinutes(endMinute.toLong())
        }
    }
}

/** Everything the blocker needs to make a decision, captured as an immutable value. */
data class PolicySnapshot(
    val blockedApps: Set<String> = emptySet(),
    val blockedDomains: Set<String> = emptySet(),
    val mode: BlockMode = BlockMode.ALWAYS,
    val schedules: List<ScheduleRule> = emptyList(),
    val session: ActiveSession? = null,
    val dailyLimitsMs: Map<String, Long> = emptyMap(),
)
