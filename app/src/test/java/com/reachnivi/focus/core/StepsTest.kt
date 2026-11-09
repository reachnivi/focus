package com.reachnivi.focus.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StepsTest {
    private val mon = LocalDate.of(2026, 11, 9)
    private val tue = mon.plusDays(1)

    @Test
    fun `first reading is a baseline, then steps accumulate`() {
        var s = Steps.update(null, 12_000, mon)
        assertEquals(0, s.stepsToday)
        s = Steps.update(s, 12_800, mon)
        s = Steps.update(s, 13_500, mon)
        assertEquals(1_500, s.stepsToday)
    }

    @Test
    fun `new day restarts and a reboot keeps counting`() {
        var s = StepState(mon, 6_000, 20_000)
        s = Steps.update(s, 20_300, tue)
        assertEquals(tue, s.day)
        assertEquals(300, s.stepsToday)
        s = Steps.update(s, 150, tue) // rebooted: counter restarted
        assertEquals(450, s.stepsToday)
        s = Steps.update(s, 900_000, tue) // glitch ignored
        assertEquals(450, s.stepsToday)
    }

    @Test
    fun `minutes are earned per thousand steps, capped, and spendable`() {
        assertEquals(0, Steps.earnedMinutes(999))
        assertEquals(15, Steps.earnedMinutes(3_240))
        assertEquals(60, Steps.earnedMinutes(40_000))
        assertEquals(760, Steps.stepsToNextReward(3_240))
        assertNull(Steps.stepsToNextReward(12_000))
        assertEquals(5, Steps.available(3_240, spentMinutes = 10))
        assertEquals(0, Steps.available(1_000, spentMinutes = 10))
    }
}
