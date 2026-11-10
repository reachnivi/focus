package com.reachnivi.focus.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RepsTest {

    @Test
    fun `joint angle maths`() {
        assertEquals(90.0, Reps.angle(Point(0f, 1f), Point(0f, 0f), Point(1f, 0f)), 0.001)
        assertEquals(180.0, Reps.angle(Point(-1f, 0f), Point(0f, 0f), Point(1f, 0f)), 0.001)
        assertEquals(45.0, Reps.angle(Point(1f, 1f), Point(0f, 0f), Point(1f, 0f)), 0.001)
    }

    @Test
    fun `a full squat counts once, jitter does not`() {
        val c = RepCounter(Exercise.SQUAT)
        listOf(170.0, 150.0, 120.0, 99.0, 101.0, 98.0, 130.0, 159.0, 161.0, 165.0).forEach { c.onAngle(it) }
        assertEquals(1, c.reps)
    }

    @Test
    fun `half reps and missing frames do not count`() {
        val c = RepCounter(Exercise.SQUAT)
        listOf(170.0, 120.0, 110.0, 170.0).forEach { c.onAngle(it) } // never below 100
        assertEquals(0, c.reps)
        listOf(95.0, null, null, 170.0).forEach { c.onAngle(it) }
        assertEquals(1, c.reps)
    }

    @Test
    fun `pushups use their own thresholds`() {
        val c = RepCounter(Exercise.PUSHUP)
        repeat(3) { listOf(160.0, 85.0, 155.0).forEach { c.onAngle(it) } }
        assertEquals(3, c.reps)
        listOf(95.0, 160.0).forEach { c.onAngle(it) } // 95 isn't deep enough for a pushup
        assertEquals(3, c.reps)
    }

    @Test
    fun `rewards are capped per day`() {
        assertEquals(5, Reps.minutesFor(10, alreadyEarnedToday = 0))
        assertEquals(10, Reps.minutesFor(25, 0))
        assertEquals(5, Reps.minutesFor(20, alreadyEarnedToday = 25))
        assertEquals(0, Reps.minutesFor(40, alreadyEarnedToday = 30))
        assertEquals(30, Reps.xpForDay(80))
        assertEquals(12, Reps.xpForDay(12))
    }
}
