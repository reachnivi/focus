package com.reachnivi.focus.core

import java.time.LocalDate
import java.time.LocalDateTime

enum class DndAction { TURN_ON, RESTORE, NONE }

object Bedtime {

    /** The next time this rule's window starts, strictly after [now] (within the next 8 days). */
    fun nextStart(rule: ScheduleRule, now: LocalDateTime): LocalDateTime? {
        if (!rule.enabled || rule.days.isEmpty()) return null
        for (offset in 0..7L) {
            val day = now.toLocalDate().plusDays(offset)
            if (day.dayOfWeek !in rule.days) continue
            val start = day.atStartOfDay().plusMinutes(rule.startMinute.toLong())
            if (start.isAfter(now)) return start
        }
        return null
    }

    /**
     * Whether to send tonight's wind-down reminder: within [minutesBefore] of the start, not yet in
     * bedtime, and not already sent for that night (keyed by the date bedtime starts).
     */
    fun windDownDue(rule: ScheduleRule, now: LocalDateTime, minutesBefore: Int, notifiedOn: LocalDate?): Boolean {
        if (minutesBefore <= 0 || rule.isActiveAt(now)) return false
        val start = nextStart(rule, now) ?: return false
        if (now.isBefore(start.minusMinutes(minutesBefore.toLong()))) return false
        return notifiedOn != start.toLocalDate()
    }

    /**
     * Do Not Disturb follows bedtime, but Focus only ever undoes what it did itself: it turns DND on
     * only from "all notifications", and restores only if it was the one that turned it on.
     */
    fun dndAction(bedtimeActive: Boolean, dndEnabled: Boolean, currentFilterIsAll: Boolean, setByUs: Boolean): DndAction = when {
        bedtimeActive && dndEnabled && currentFilterIsAll && !setByUs -> DndAction.TURN_ON
        !bedtimeActive && setByUs -> DndAction.RESTORE
        else -> DndAction.NONE
    }
}
