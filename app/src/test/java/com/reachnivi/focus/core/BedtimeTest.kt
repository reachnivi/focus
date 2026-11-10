package com.reachnivi.focus.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class BedtimeTest {
    private val zone = ZoneOffset.UTC
    private val night = ScheduleRule(name = "Bedtime", days = DayOfWeek.entries.toSet(), startMinute = 22 * 60 + 30, endMinute = 6 * 60 + 30)
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, day, h, m)
    private fun ms(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `bedtime enforces block list in any mode and ends at window end`() {
        val p = PolicySnapshot(blockedApps = setOf("insta"), mode = BlockMode.FOCUS_ONLY, bedtime = night)
        val d = BlockPolicyEngine.decideApp(p, "insta", ms(at(9, 23)), zone) as BlockDecision.Block
        assertEquals(BlockReason.BEDTIME, d.reason)
        assertEquals(ms(at(10, 6, 30)), d.untilMillis)
        assertEquals(BlockDecision.Allow, BlockPolicyEngine.decideApp(p, "insta", ms(at(9, 12)), zone))
        assertEquals(BlockDecision.Allow, BlockPolicyEngine.decideApp(p, "maps", ms(at(9, 23)), zone))
    }

    @Test
    fun `session outranks bedtime, bedtime outranks mode`() {
        val session = ActiveSession(1, ms(at(9, 22)), ms(at(9, 23, 30)), strict = true)
        val p = PolicySnapshot(blockedApps = setOf("insta"), mode = BlockMode.ALWAYS, bedtime = night, session = session)
        assertEquals(BlockReason.FOCUS_SESSION, (BlockPolicyEngine.decideApp(p, "insta", ms(at(9, 23)), zone) as BlockDecision.Block).reason)
        val noSession = p.copy(session = null)
        assertEquals(BlockReason.BEDTIME, (BlockPolicyEngine.decideApp(noSession, "insta", ms(at(9, 23)), zone) as BlockDecision.Block).reason)
        assertEquals(BlockReason.ALWAYS_ON, (BlockPolicyEngine.decideApp(noSession, "insta", ms(at(9, 12)), zone) as BlockDecision.Block).reason)
    }

    @Test
    fun `next start and wind-down once per night`() {
        assertEquals(at(9, 22, 30), Bedtime.nextStart(night, at(9, 21)))
        assertEquals(at(10, 22, 30), Bedtime.nextStart(night, at(9, 23)))
        assertFalse(Bedtime.windDownDue(night, at(9, 21, 59), 30, null))
        assertTrue(Bedtime.windDownDue(night, at(9, 22, 0), 30, null))
        assertFalse(Bedtime.windDownDue(night, at(9, 22, 10), 30, LocalDate.of(2026, 11, 9)))
        assertFalse(Bedtime.windDownDue(night, at(9, 22, 40), 30, null)) // already in bedtime
        assertFalse(Bedtime.windDownDue(night, at(9, 22, 10), 0, null)) // reminder off
    }

    @Test
    fun `dnd only undoes what Focus did`() {
        assertEquals(DndAction.TURN_ON, Bedtime.dndAction(bedtimeActive = true, dndEnabled = true, currentFilterIsAll = true, setByUs = false))
        assertEquals(DndAction.NONE, Bedtime.dndAction(true, true, currentFilterIsAll = false, setByUs = false)) // user already in DND
        assertEquals(DndAction.NONE, Bedtime.dndAction(true, dndEnabled = false, currentFilterIsAll = true, setByUs = false))
        assertEquals(DndAction.RESTORE, Bedtime.dndAction(bedtimeActive = false, dndEnabled = true, currentFilterIsAll = false, setByUs = true))
        assertEquals(DndAction.NONE, Bedtime.dndAction(false, true, true, setByUs = false))
    }
}
