package com.reachnivi.focus.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneStackTest {
    private val min = 60_000L

    @Test
    fun `invite code round-trips and rejects junk`() {
        val invite = StackInvite("ab12", 45, "Hostel 3B: dinner & chai")
        assertEquals(invite, StackCode.parse(StackCode.encode(invite)))
        assertNull(StackCode.parse("https://example.com"))
        assertNull(StackCode.parse("focus-stack:2:ab12:45:x"))
        assertNull(StackCode.parse("focus-stack:1:ab12:999:x"))
        assertNull(StackCode.parse("focus-stack:1:a-b:45:x"))
        assertNull(StackCode.parse(null))
    }

    @Test
    fun `face-down for two seconds starts the stack`() {
        var p: StackPhase = StackPhase.Waiting()
        p = StackMonitor.onOrientation(p, faceDown = true, screenOn = true, now = 0, minutes = 30)
        p = StackMonitor.onOrientation(p, true, true, 1_500, 30)
        assertTrue(p is StackPhase.Waiting)
        p = StackMonitor.onOrientation(p, false, true, 1_800, 30) // lifted: restart the hold
        p = StackMonitor.onOrientation(p, true, true, 2_000, 30)
        p = StackMonitor.onOrientation(p, true, true, 4_000, 30)
        assertEquals(StackPhase.Running(4_000, 4_000 + 30 * min), p)
    }

    @Test
    fun `unlock breaks after the grace period, not before`() {
        val running = StackPhase.Running(0, 30 * min)
        assertEquals(running, StackMonitor.onUnlock(running, 5_000))
        assertEquals(StackPhase.Broken(60_000, 60_000), StackMonitor.onUnlock(running, 60_000))
    }

    @Test
    fun `face-up with screen on breaks only if held`() {
        var p: StackPhase = StackPhase.Running(0, 30 * min)
        p = StackMonitor.onOrientation(p, faceDown = false, screenOn = true, now = 20_000, minutes = 30)
        p = StackMonitor.onOrientation(p, false, true, 21_000, 30)
        p = StackMonitor.onOrientation(p, true, true, 21_500, 30) // put back quickly: fine
        assertTrue(p is StackPhase.Running)
        p = StackMonitor.onOrientation(p, false, false, 40_000, 30) // face-up but screen off: fine
        assertTrue(p is StackPhase.Running)
        p = StackMonitor.onOrientation(p, false, true, 50_000, 30)
        p = StackMonitor.onOrientation(p, false, true, 53_000, 30)
        assertEquals(StackPhase.Broken(53_000, 53_000), p)
    }

    @Test
    fun `reaching the end completes and stays completed`() {
        val running = StackPhase.Running(0, 30 * min)
        val done = StackMonitor.tick(running, 30 * min + 1)
        assertEquals(StackPhase.Completed(30 * min), done)
        assertEquals(done, StackMonitor.onUnlock(done, 31 * min))
        assertEquals(done, StackMonitor.onOrientation(done, false, true, 31 * min, 30))
    }
}
