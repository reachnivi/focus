package com.reachnivi.focus.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.reachnivi.focus.core.ScheduleRule
import java.time.DayOfWeek

@Entity(tableName = "block_targets", indices = [Index(value = ["type", "value"], unique = true)])
data class BlockTargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [TYPE_APP], [TYPE_DOMAIN] or [TYPE_PAUSE] */
    val type: String,
    /** package name or bare domain */
    val value: String,
    val addedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val TYPE_APP = "app"
        const val TYPE_DOMAIN = "domain"
        const val TYPE_PAUSE = "pause"
    }
}

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** bit (dayOfWeek.value - 1) set when the schedule runs on that day */
    val daysMask: Int,
    val startMinute: Int,
    val endMinute: Int,
    val enabled: Boolean = true,
) {
    fun toRule() = ScheduleRule(
        id = id,
        name = name,
        days = DayOfWeek.entries.filter { daysMask and (1 shl (it.value - 1)) != 0 }.toSet(),
        startMinute = startMinute,
        endMinute = endMinute,
        enabled = enabled,
    )

    companion object {
        fun from(rule: ScheduleRule) = ScheduleEntity(
            id = rule.id,
            name = rule.name,
            daysMask = rule.days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) },
            startMinute = rule.startMinute,
            endMinute = rule.endMinute,
            enabled = rule.enabled,
        )
    }
}

@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey val packageName: String,
    val minutesPerDay: Int,
)

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val plannedEndAt: Long,
    val endedAt: Long? = null,
    val strict: Boolean,
) {
    val completed: Boolean get() = endedAt != null && endedAt >= plannedEndAt
    fun focusedMs(now: Long): Long = (endedAt ?: minOf(now, plannedEndAt)) - startedAt
}

/** Daily snapshots kept beyond Android's ~7–10 day usage-event retention. */
@Entity(tableName = "daily_app_usage", primaryKeys = ["date", "packageName"])
data class DailyAppUsageEntity(
    /** ISO date, yyyy-MM-dd */
    val date: String,
    val packageName: String,
    val foregroundMs: Long,
)

@Entity(tableName = "daily_summary")
data class DailySummaryEntity(
    @PrimaryKey val date: String,
    val totalMs: Long,
    val unlocks: Int,
)

/** One decision on the mindful-pause screen. */
@Entity(tableName = "pause_events")
data class PauseEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val at: Long,
    /** [OPENED] or [SKIPPED] */
    val outcome: String,
    /** Minutes granted when opened, 0 when skipped. */
    val minutes: Int,
) {
    companion object {
        const val OPENED = "OPENED"
        const val SKIPPED = "SKIPPED"
    }
}
