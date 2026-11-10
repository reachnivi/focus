package com.reachnivi.focus.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.reachnivi.focus.core.StepState
import com.reachnivi.focus.core.Steps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

data class WalkStatus(
    val available: Boolean,
    val permitted: Boolean,
    val stepsToday: Int,
    val earnedMinutes: Int,
    val spentMinutes: Int,
    val stepsToNext: Int?,
    /** Minutes earned today from other quests (e.g. photo quests), on top of walking. */
    val bonusMinutes: Int = 0,
) {
    val minutesLeft: Int get() = (earnedMinutes + bonusMinutes - spentMinutes).coerceAtLeast(0)
}

/** Walk quests: today's steps, minutes earned and spent. Day history goes to Room for the garden. */
class StepStore(private val context: Context, private val dao: FocusDao) {
    private val prefs = context.getSharedPreferences("steps", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<StepState?> = _state.asStateFlow()
    private val _spent = MutableStateFlow(readSpent())
    private val _bonus = MutableStateFlow(readBonus())
    private var lastPersistAt = 0L

    val hasSensor: Boolean
        get() = context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Steps per day for the garden: stored history plus today's live count. */
    val stepsByDay: Flow<Map<LocalDate, Int>> = combine(dao.observeSteps(), _state) { rows, live ->
        val stored = rows.mapNotNull { r -> runCatching { LocalDate.parse(r.date) }.getOrNull()?.let { it to r.steps } }.toMap()
        if (live != null) stored + (live.day to live.stepsToday) else stored
    }

    val status: Flow<WalkStatus> = combine(_state, _spent, _bonus) { s, spent, bonus ->
        val steps = s?.takeIf { it.day == LocalDate.now() }?.stepsToday ?: 0
        WalkStatus(
            available = hasSensor,
            permitted = hasPermission(),
            stepsToday = steps,
            earnedMinutes = Steps.earnedMinutes(steps),
            spentMinutes = if (prefs.getString(K_SPENT_DAY, null) == LocalDate.now().toString()) spent else 0,
            stepsToNext = Steps.stepsToNextReward(steps),
            bonusMinutes = bonus,
        )
    }

    /** Called by the step sensor listener. Persists today's total at most once a minute. */
    suspend fun onReading(reading: Long) {
        val today = LocalDate.now()
        val previous = _state.value
        val next = Steps.update(previous, reading, today)
        if (previous != null && previous.day != today) {
            dao.upsertSteps(DailyStepsEntity(previous.day.toString(), previous.stepsToday))
        }
        prefs.edit()
            .putString(K_DAY, next.day.toString())
            .putInt(K_STEPS, next.stepsToday)
            .putLong(K_READING, next.lastReading)
            .apply()
        _state.value = next
        val now = System.currentTimeMillis()
        if (now - lastPersistAt > 60_000) {
            dao.upsertSteps(DailyStepsEntity(next.day.toString(), next.stepsToday))
            lastPersistAt = now
        }
    }

    fun minutesLeftToday(): Int {
        val steps = _state.value?.takeIf { it.day == LocalDate.now() }?.stepsToday ?: 0
        return (Steps.earnedMinutes(steps) + readBonus() - readSpent()).coerceAtLeast(0)
    }

    /** Adds minutes earned by a non-walking quest to today's wallet. */
    fun addBonus(minutes: Int) {
        val total = readBonus() + minutes
        prefs.edit().putString(K_BONUS_DAY, LocalDate.now().toString()).putInt(K_BONUS, total).apply()
        _bonus.value = total
    }

    /** @return true if [minutes] were available and are now spent. */
    fun spend(minutes: Int): Boolean {
        if (minutes <= 0 || minutesLeftToday() < minutes) return false
        val spent = readSpent() + minutes
        prefs.edit().putString(K_SPENT_DAY, LocalDate.now().toString()).putInt(K_SPENT, spent).apply()
        _spent.value = spent
        return true
    }

    fun refresh() {
        _spent.value = readSpent()
        _bonus.value = readBonus()
    }

    private fun read(): StepState? {
        val day = prefs.getString(K_DAY, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return StepState(day, prefs.getInt(K_STEPS, 0), prefs.getLong(K_READING, 0))
    }

    private fun readBonus(): Int =
        if (prefs.getString(K_BONUS_DAY, null) == LocalDate.now().toString()) prefs.getInt(K_BONUS, 0) else 0

    private fun readSpent(): Int =
        if (prefs.getString(K_SPENT_DAY, null) == LocalDate.now().toString()) prefs.getInt(K_SPENT, 0) else 0

    private companion object {
        const val K_DAY = "day"
        const val K_STEPS = "steps_today"
        const val K_READING = "last_reading"
        const val K_SPENT_DAY = "spent_day"
        const val K_SPENT = "spent_minutes"
        const val K_BONUS_DAY = "bonus_day"
        const val K_BONUS = "bonus_minutes"
    }
}
