package com.reachnivi.focus.core

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class UsageEvent(val packageName: String, val timestamp: Long, val type: Type) {
    enum class Type { RESUMED, PAUSED, SCREEN_OFF, UNLOCK }
}

data class DayUsage(
    val perAppMs: Map<String, Long>,
    /** 24 buckets, foreground time per hour of day. */
    val hourlyMs: List<Long>,
    val unlocks: Int,
) {
    val totalMs: Long get() = perAppMs.values.sum()

    fun topApps(limit: Int): List<Pair<String, Long>> =
        perAppMs.entries.sortedByDescending { it.value }.take(limit).map { it.key to it.value }

    companion object {
        val EMPTY = DayUsage(emptyMap(), List(24) { 0L }, 0)

        fun merge(days: Collection<DayUsage>): DayUsage {
            val apps = HashMap<String, Long>()
            val hours = LongArray(24)
            days.forEach { d ->
                d.perAppMs.forEach { (k, v) -> apps[k] = (apps[k] ?: 0L) + v }
                d.hourlyMs.forEachIndexed { i, v -> hours[i] += v }
            }
            return DayUsage(apps, hours.toList(), days.sumOf { it.unlocks })
        }
    }
}

/**
 * Turns raw usage events into per-app foreground time. Only one app is in the
 * foreground at a time: a RESUMED event ends whatever was in front before it.
 */
object UsageAggregator {

    fun aggregate(
        events: List<UsageEvent>,
        windowStart: Long,
        windowEnd: Long,
        zone: ZoneId,
        ignoredPackages: Set<String> = emptySet(),
        now: Long = windowEnd,
    ): DayUsage {
        val perApp = HashMap<String, Long>()
        val hourly = LongArray(24)
        var unlocks = 0
        var current: String? = null
        var currentStart = 0L

        fun close(at: Long) {
            val pkg = current ?: return
            val start = maxOf(currentStart, windowStart)
            val end = minOf(at, windowEnd)
            if (end > start) {
                perApp[pkg] = (perApp[pkg] ?: 0L) + (end - start)
                addToHours(hourly, start, end, zone)
            }
            current = null
        }

        for (e in events.sortedBy { it.timestamp }) {
            when (e.type) {
                UsageEvent.Type.RESUMED -> {
                    if (current == e.packageName) continue
                    close(e.timestamp)
                    if (e.packageName !in ignoredPackages) {
                        current = e.packageName
                        currentStart = e.timestamp
                    }
                }
                UsageEvent.Type.PAUSED -> if (current == e.packageName) close(e.timestamp)
                UsageEvent.Type.SCREEN_OFF -> close(e.timestamp)
                UsageEvent.Type.UNLOCK -> if (e.timestamp in windowStart until windowEnd) unlocks++
            }
        }
        close(now)
        return DayUsage(perApp, hourly.toList(), unlocks)
    }

    private fun addToHours(hourly: LongArray, start: Long, end: Long, zone: ZoneId) {
        var cursor = start
        while (cursor < end) {
            val zdt = Instant.ofEpochMilli(cursor).atZone(zone)
            val nextHour = zdt.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
            val sliceEnd = minOf(end, nextHour)
            hourly[zdt.hour] += sliceEnd - cursor
            cursor = sliceEnd
        }
    }
}
