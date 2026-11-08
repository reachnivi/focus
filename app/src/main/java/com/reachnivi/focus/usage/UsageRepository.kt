package com.reachnivi.focus.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.reachnivi.focus.core.DayUsage
import com.reachnivi.focus.core.UsageAggregator
import com.reachnivi.focus.core.UsageEvent
import com.reachnivi.focus.data.AppCatalog
import com.reachnivi.focus.data.DailyAppUsageEntity
import com.reachnivi.focus.data.DailySummaryEntity
import com.reachnivi.focus.data.FocusDao
import com.reachnivi.focus.permissions.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

class UsageRepository(
    private val context: Context,
    private val dao: FocusDao,
    private val catalog: AppCatalog,
) {
    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /** Launcher and system UI time isn't "phone usage" in the sense people care about. */
    private val ignored: Set<String> by lazy { catalog.homePackages() + "com.android.systemui" }

    fun hasAccess(): Boolean = Permissions.hasUsageAccess(context)

    suspend fun day(date: LocalDate): DayUsage = days(date, date).firstOrNull()?.second ?: DayUsage.EMPTY

    /** Usage for each day in [from]..[to], from a single event query. */
    suspend fun days(from: LocalDate, to: LocalDate): List<Pair<LocalDate, DayUsage>> =
        withContext(Dispatchers.Default) {
            if (!hasAccess()) return@withContext emptyList()
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = minOf(now, to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
            val events = readEvents(start - LOOKBACK_MS, end)
            generateSequence(from) { it.plusDays(1) }
                .takeWhile { !it.isAfter(to) }
                .map { date ->
                    val ds = date.atStartOfDay(zone).toInstant().toEpochMilli()
                    val de = minOf(now, date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
                    date to UsageAggregator.aggregate(events, ds, de, zone, ignored, now)
                }
                .toList()
        }

    /** Persists daily totals so history survives Android's short event retention. */
    suspend fun snapshot(date: LocalDate) {
        val usage = day(date)
        if (usage.perAppMs.isEmpty()) return
        val iso = date.toString()
        dao.upsertAppUsage(usage.perAppMs.map { (pkg, ms) -> DailyAppUsageEntity(iso, pkg, ms) })
        dao.upsertSummary(DailySummaryEntity(iso, usage.totalMs, usage.unlocks))
    }

    private fun readEvents(from: Long, to: Long): List<UsageEvent> {
        val result = usm.queryEvents(from, to) ?: return emptyList()
        val event = UsageEvents.Event()
        val out = ArrayList<UsageEvent>(4096)
        while (result.hasNextEvent()) {
            result.getNextEvent(event)
            val type = when (event.eventType) {
                EVENT_RESUMED -> UsageEvent.Type.RESUMED
                EVENT_PAUSED -> UsageEvent.Type.PAUSED
                EVENT_SCREEN_NON_INTERACTIVE, EVENT_KEYGUARD_SHOWN -> UsageEvent.Type.SCREEN_OFF
                EVENT_KEYGUARD_HIDDEN -> UsageEvent.Type.UNLOCK
                else -> null
            } ?: continue
            out += UsageEvent(event.packageName ?: continue, event.timeStamp, type)
        }
        return out
    }

    private companion object {
        // Raw values so the same code runs on API 26+ (named constants arrived in 28/29).
        const val EVENT_RESUMED = 1
        const val EVENT_PAUSED = 2
        const val EVENT_SCREEN_NON_INTERACTIVE = 16
        const val EVENT_KEYGUARD_SHOWN = 17
        const val EVENT_KEYGUARD_HIDDEN = 18

        /** Catch apps that were already open when the day started. */
        const val LOOKBACK_MS = 3 * 60 * 60 * 1000L
    }
}
