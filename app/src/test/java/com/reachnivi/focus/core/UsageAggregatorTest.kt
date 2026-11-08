package com.reachnivi.focus.core

import com.reachnivi.focus.core.UsageEvent.Type.PAUSED
import com.reachnivi.focus.core.UsageEvent.Type.RESUMED
import com.reachnivi.focus.core.UsageEvent.Type.SCREEN_OFF
import com.reachnivi.focus.core.UsageEvent.Type.UNLOCK
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class UsageAggregatorTest {
    private val zone = ZoneOffset.UTC
    private fun t(h: Int, m: Int = 0) =
        LocalDateTime.of(2026, 11, 9, h, m).toInstant(ZoneOffset.UTC).toEpochMilli()

    private val dayStart = t(0)
    private val dayEnd = dayStart + 24 * 3_600_000L
    private val min = 60_000L

    @Test
    fun `pairs resume and pause per app`() {
        val events = listOf(
            UsageEvent("a", t(10, 0), RESUMED),
            UsageEvent("a", t(10, 20), PAUSED),
            UsageEvent("b", t(10, 20), RESUMED),
            UsageEvent("b", t(10, 30), PAUSED),
        )
        val u = UsageAggregator.aggregate(events, dayStart, dayEnd, zone)
        assertEquals(20 * min, u.perAppMs["a"])
        assertEquals(10 * min, u.perAppMs["b"])
        assertEquals(30 * min, u.totalMs)
    }

    @Test
    fun `next resume closes previous app even without pause`() {
        val events = listOf(
            UsageEvent("a", t(10, 0), RESUMED),
            UsageEvent("b", t(10, 5), RESUMED),
            UsageEvent("a", t(10, 6), PAUSED), // late pause for a must not close b
            UsageEvent("b", t(10, 15), SCREEN_OFF),
        )
        val u = UsageAggregator.aggregate(events, dayStart, dayEnd, zone)
        assertEquals(5 * min, u.perAppMs["a"])
        assertEquals(10 * min, u.perAppMs["b"])
    }

    @Test
    fun `launcher is ignored and ends foreground app`() {
        val events = listOf(
            UsageEvent("a", t(9, 0), RESUMED),
            UsageEvent("launcher", t(9, 10), RESUMED),
            UsageEvent("launcher", t(9, 50), PAUSED),
        )
        val u = UsageAggregator.aggregate(events, dayStart, dayEnd, zone, setOf("launcher"))
        assertEquals(mapOf("a" to 10 * min), u.perAppMs)
    }

    @Test
    fun `clips to window and splits hours`() {
        val events = listOf(
            UsageEvent("a", dayStart - 30 * min, RESUMED), // started yesterday 23:30
            UsageEvent("a", t(0, 15), PAUSED),
            UsageEvent("b", t(13, 50), RESUMED),
            UsageEvent("b", t(14, 20), PAUSED),
        )
        val u = UsageAggregator.aggregate(events, dayStart, dayEnd, zone)
        assertEquals(15 * min, u.perAppMs["a"])
        assertEquals(15 * min, u.hourlyMs[0])
        assertEquals(10 * min, u.hourlyMs[13])
        assertEquals(20 * min, u.hourlyMs[14])
    }

    @Test
    fun `open app counts until now and unlocks are counted`() {
        val events = listOf(
            UsageEvent("android", t(8, 0), UNLOCK),
            UsageEvent("android", t(12, 0), UNLOCK),
            UsageEvent("a", t(12, 0), RESUMED),
        )
        val u = UsageAggregator.aggregate(events, dayStart, dayEnd, zone, now = t(12, 7))
        assertEquals(7 * min, u.perAppMs["a"])
        assertEquals(2, u.unlocks)
    }

    @Test
    fun `formats durations`() {
        assertEquals("<1m", Format.duration(20_000))
        assertEquals("45m", Format.duration(45 * min))
        assertEquals("2h", Format.duration(120 * min))
        assertEquals("2h 14m", Format.duration(134 * min))
        assertEquals("24:59", Format.clock(24 * min + 59_000))
    }
}
