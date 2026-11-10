package com.reachnivi.focus.core

import kotlin.math.acos
import kotlin.math.sqrt

enum class Exercise(val label: String, val downBelow: Double, val upAbove: Double, val tip: String) {
    SQUAT("Squats", downBelow = 100.0, upAbove = 160.0, tip = "Stand back so your whole body is in view, facing the phone."),
    PUSHUP("Pushups", downBelow = 90.0, upAbove = 150.0, tip = "Put the phone on the floor, side-on to you, so your arms are in view."),
}

data class Point(val x: Float, val y: Float)

/** Counts reps from a stream of joint angles. Hysteresis means jitter near a threshold can't double-count. */
class RepCounter(val exercise: Exercise) {
    var reps: Int = 0
        private set
    private var down = false

    /** Feed one frame's joint angle (degrees), or null when the body isn't confidently visible. */
    fun onAngle(angle: Double?): Int {
        if (angle == null) return reps
        if (!down && angle < exercise.downBelow) {
            down = true
        } else if (down && angle > exercise.upAbove) {
            down = false
            reps++
        }
        return reps
    }

    val isDown: Boolean get() = down
}

object Reps {
    const val REPS_PER_REWARD = 10
    const val MINUTES_PER_REWARD = 5
    const val MAX_MINUTES_PER_DAY = 30
    const val MAX_XP_PER_DAY = 30
    const val MIN_LANDMARK_CONFIDENCE = 0.5f

    /** Angle at [b] between rays b→a and b→c, in degrees (0..180). */
    fun angle(a: Point, b: Point, c: Point): Double {
        val v1x = (a.x - b.x).toDouble()
        val v1y = (a.y - b.y).toDouble()
        val v2x = (c.x - b.x).toDouble()
        val v2y = (c.y - b.y).toDouble()
        val n = sqrt(v1x * v1x + v1y * v1y) * sqrt(v2x * v2x + v2y * v2y)
        if (n == 0.0) return 180.0
        val cos = ((v1x * v2x + v1y * v2y) / n).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cos))
    }

    /** Minutes a workout earns, given what was already earned from moving today. */
    fun minutesFor(reps: Int, alreadyEarnedToday: Int): Int {
        val raw = reps / REPS_PER_REWARD * MINUTES_PER_REWARD
        return raw.coerceAtMost((MAX_MINUTES_PER_DAY - alreadyEarnedToday).coerceAtLeast(0))
    }

    /** Garden XP for all of a day's reps. */
    fun xpForDay(repsToday: Int): Int = repsToday.coerceAtMost(MAX_XP_PER_DAY)

}
