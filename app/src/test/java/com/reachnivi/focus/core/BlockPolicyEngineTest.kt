package com.reachnivi.focus.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class BlockPolicyEngineTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val weekdays = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )

    // 2026-11-09 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val work = ScheduleRule(1, "Work", weekdays, 9 * 60, 18 * 60)
    private val night = ScheduleRule(2, "Night", setOf(DayOfWeek.FRIDAY), 22 * 60, 6 * 60)

    @Test
    fun `always mode blocks listed apps only`() {
        val p = PolicySnapshot(blockedApps = setOf("insta"), mode = BlockMode.ALWAYS)
        val d = BlockPolicyEngine.decideApp(p, "insta", at(9, 12), zone)
        assertEquals(BlockReason.ALWAYS_ON, (d as BlockDecision.Block).reason)
        assertEquals(BlockDecision.Allow, BlockPolicyEngine.decideApp(p, "maps", at(9, 12), zone))
    }

    @Test
    fun `focus only mode allows outside sessions`() {
        val p = PolicySnapshot(blockedApps = setOf("insta"), mode = BlockMode.FOCUS_ONLY)
        assertEquals(BlockDecision.Allow, BlockPolicyEngine.decideApp(p, "insta", at(9, 12), zone))
    }

    @Test
    fun `scheduled mode blocks inside window and reports end`() {
        val p = PolicySnapshot(blockedApps = setOf("insta"), mode = BlockMode.SCHEDULED, schedules = listOf(work))
        val d = BlockPolicyEngine.decideApp(p, "insta", at(9, 10), zone) as BlockDecision.Block
        assertEquals(BlockReason.SCHEDULE, d.reason)
        assertEquals(at(9, 18), d.untilMillis)
        assertEquals(BlockDecision.Allow, BlockPolicyEngine.decideApp(p, "insta", at(9, 18), zone))
        assertEquals(BlockDecision.Allow, BlockPolicyEngine.decideApp(p, "insta", at(8, 10), zone)) // Sunday
    }

    @Test
    fun `overnight schedule spans into next morning`() {
        // Friday 13th 22:00 -> Saturday 14th 06:00
        assertTrue(night.isActiveAt(LocalDateTime.of(2026, 11, 13, 23, 0)))
        assertTrue(night.isActiveAt(LocalDateTime.of(2026, 11, 14, 5, 59)))
        assertTrue(!night.isActiveAt(LocalDateTime.of(2026, 11, 14, 6, 0)))
        assertTrue(!night.isActiveAt(LocalDateTime.of(2026, 11, 13, 5, 0))) // Thu night not scheduled
        assertEquals(
            LocalDateTime.of(2026, 11, 14, 6, 0),
            night.currentWindowEnd(LocalDateTime.of(2026, 11, 13, 23, 0)),
        )
        assertEquals(
            LocalDateTime.of(2026, 11, 14, 6, 0),
            night.currentWindowEnd(LocalDateTime.of(2026, 11, 14, 1, 0)),
        )
    }

    @Test
    fun `disabled schedule never applies`() {
        assertTrue(!work.copy(enabled = false).isActiveAt(LocalDateTime.of(2026, 11, 9, 10, 0)))
    }

    @Test
    fun `focus session overrides mode and carries strictness`() {
        val session = ActiveSession(1, at(9, 10), at(9, 11), strict = true)
        val p = PolicySnapshot(blockedApps = setOf("insta"), mode = BlockMode.FOCUS_ONLY, session = session)
        val d = BlockPolicyEngine.decideApp(p, "insta", at(9, 10, 30), zone) as BlockDecision.Block
        assertEquals(BlockReason.FOCUS_SESSION, d.reason)
        assertTrue(d.strict)
        assertEquals(at(9, 11), d.untilMillis)
        assertEquals(BlockDecision.Allow, BlockPolicyEngine.decideApp(p, "insta", at(9, 11), zone))
    }

    @Test
    fun `daily limit blocks once used up until midnight`() {
        val p = PolicySnapshot(mode = BlockMode.FOCUS_ONLY, dailyLimitsMs = mapOf("yt" to 30 * 60_000L))
        assertEquals(BlockDecision.Allow, BlockPolicyEngine.decideApp(p, "yt", at(9, 12), zone, 29 * 60_000L))
        val d = BlockPolicyEngine.decideApp(p, "yt", at(9, 12), zone, 30 * 60_000L) as BlockDecision.Block
        assertEquals(BlockReason.DAILY_LIMIT, d.reason)
        assertEquals(at(10, 0), d.untilMillis)
    }

    @Test
    fun `domains follow the block list`() {
        val p = PolicySnapshot(blockedDomains = setOf("youtube.com"), mode = BlockMode.ALWAYS)
        assertTrue(BlockPolicyEngine.decideDomain(p, "m.youtube.com", at(9, 12), zone) is BlockDecision.Block)
        assertEquals(BlockDecision.Allow, BlockPolicyEngine.decideDomain(p, "notyoutube.com", at(9, 12), zone))
    }
}
