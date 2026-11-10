package com.reachnivi.focus.core

import java.time.LocalDate

/** Today's step count, built from the phone's step counter (which counts since boot). */
data class StepState(val day: LocalDate, val stepsToday: Int, val lastReading: Long)

object Steps {
    const val STEPS_PER_REWARD = 1_000
    const val MINUTES_PER_REWARD = 5
    const val MAX_EARNED_MINUTES = 60

    /** Readings that jump by more than this are sensor glitches, not walking. */
    private const val MAX_PLAUSIBLE_DELTA = 50_000L

    /**
     * Folds a new counter reading into the day's count. The first reading only sets a baseline; a
     * reading lower than the last means the phone rebooted (the counter restarted from zero); a new
     * day starts again from zero, carrying only the steps since the last reading.
     */
    fun update(state: StepState?, reading: Long, today: LocalDate): StepState {
        if (state == null) return StepState(today, 0, reading)
        val raw = if (reading >= state.lastReading) reading - state.lastReading else reading
        val delta = if (raw > MAX_PLAUSIBLE_DELTA) 0 else raw.toInt()
        return if (state.day == today) {
            StepState(today, state.stepsToday + delta, reading)
        } else {
            StepState(today, delta, reading)
        }
    }

    fun earnedMinutes(steps: Int): Int =
        minOf(steps / STEPS_PER_REWARD * MINUTES_PER_REWARD, MAX_EARNED_MINUTES)

    /** Steps until the next 5 minutes, or null once today's cap is reached. */
    fun stepsToNextReward(steps: Int): Int? =
        if (earnedMinutes(steps) >= MAX_EARNED_MINUTES) null else STEPS_PER_REWARD - steps % STEPS_PER_REWARD

    fun available(steps: Int, spentMinutes: Int): Int = (earnedMinutes(steps) - spentMinutes).coerceAtLeast(0)
}
