package com.reachnivi.focus.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.reachnivi.focus.core.ActiveSession
import com.reachnivi.focus.core.BlockMode
import com.reachnivi.focus.core.ScheduleRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.DayOfWeek

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focus_settings")

data class AppSettings(
    val blockMode: BlockMode = BlockMode.ALWAYS,
    val dailyGoalMinutes: Int = 180,
    val allowPass: Boolean = true,
    val onboardingDone: Boolean = false,
    val lastFocusMinutes: Int = 25,
    val session: ActiveSession? = null,
    /** Favourite apps on the minimal home screen, in display order. */
    val homeApps: List<String> = emptyList(),
    val bedtimeEnabled: Boolean = false,
    val bedtimeStart: Int = 22 * 60 + 30,
    val bedtimeEnd: Int = 6 * 60 + 30,
    /** bit (dayOfWeek.value - 1) */
    val bedtimeDaysMask: Int = 0b1111111,
    val bedtimeDnd: Boolean = false,
    val windDownMinutes: Int = 30,
    val dndSetByUs: Boolean = false,
    val windDownNotifiedOn: String? = null,
) {
    val bedtime: ScheduleRule?
        get() = if (!bedtimeEnabled) {
            null
        } else {
            ScheduleRule(
                id = -1,
                name = "Bedtime",
                days = DayOfWeek.entries.filter { bedtimeDaysMask and (1 shl (it.value - 1)) != 0 }.toSet(),
                startMinute = bedtimeStart,
                endMinute = bedtimeEnd,
            )
        }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BLOCK_MODE = stringPreferencesKey("block_mode")
        val DAILY_GOAL = intPreferencesKey("daily_goal_minutes")
        val ALLOW_PASS = booleanPreferencesKey("allow_pass")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val LAST_FOCUS_MINUTES = intPreferencesKey("last_focus_minutes")
        val SESSION_ID = longPreferencesKey("session_id")
        val SESSION_START = longPreferencesKey("session_start")
        val SESSION_END = longPreferencesKey("session_end")
        val SESSION_STRICT = booleanPreferencesKey("session_strict")
        val HOME_APPS = stringPreferencesKey("home_apps")
        val BEDTIME_ENABLED = booleanPreferencesKey("bedtime_enabled")
        val BEDTIME_START = intPreferencesKey("bedtime_start")
        val BEDTIME_END = intPreferencesKey("bedtime_end")
        val BEDTIME_DAYS = intPreferencesKey("bedtime_days")
        val BEDTIME_DND = booleanPreferencesKey("bedtime_dnd")
        val WIND_DOWN_MINUTES = intPreferencesKey("wind_down_minutes")
        val DND_SET_BY_US = booleanPreferencesKey("dnd_set_by_us")
        val WIND_DOWN_NOTIFIED_ON = stringPreferencesKey("wind_down_notified_on")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            AppSettings(
                blockMode = p[Keys.BLOCK_MODE]
                    ?.let { name -> BlockMode.entries.firstOrNull { it.name == name } }
                    ?: BlockMode.ALWAYS,
                dailyGoalMinutes = p[Keys.DAILY_GOAL] ?: 180,
                allowPass = p[Keys.ALLOW_PASS] ?: true,
                onboardingDone = p[Keys.ONBOARDING_DONE] ?: false,
                lastFocusMinutes = p[Keys.LAST_FOCUS_MINUTES] ?: 25,
                homeApps = p[Keys.HOME_APPS]?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
                bedtimeEnabled = p[Keys.BEDTIME_ENABLED] ?: false,
                bedtimeStart = p[Keys.BEDTIME_START] ?: (22 * 60 + 30),
                bedtimeEnd = p[Keys.BEDTIME_END] ?: (6 * 60 + 30),
                bedtimeDaysMask = p[Keys.BEDTIME_DAYS] ?: 0b1111111,
                bedtimeDnd = p[Keys.BEDTIME_DND] ?: false,
                windDownMinutes = p[Keys.WIND_DOWN_MINUTES] ?: 30,
                dndSetByUs = p[Keys.DND_SET_BY_US] ?: false,
                windDownNotifiedOn = p[Keys.WIND_DOWN_NOTIFIED_ON],
                session = p[Keys.SESSION_END]?.let { end ->
                    ActiveSession(
                        id = p[Keys.SESSION_ID] ?: 0L,
                        startedAt = p[Keys.SESSION_START] ?: 0L,
                        endsAt = end,
                        strict = p[Keys.SESSION_STRICT] ?: false,
                    )
                },
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setBlockMode(mode: BlockMode) {
        context.dataStore.edit { it[Keys.BLOCK_MODE] = mode.name }
    }

    suspend fun setDailyGoal(minutes: Int) {
        context.dataStore.edit { it[Keys.DAILY_GOAL] = minutes }
    }

    suspend fun setAllowPass(allow: Boolean) {
        context.dataStore.edit { it[Keys.ALLOW_PASS] = allow }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun setLastFocusMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.LAST_FOCUS_MINUTES] = minutes }
    }

    suspend fun setHomeApps(packages: List<String>) {
        context.dataStore.edit { it[Keys.HOME_APPS] = packages.distinct().take(MAX_HOME_APPS).joinToString(",") }
    }

    suspend fun setBedtime(
        enabled: Boolean,
        start: Int,
        end: Int,
        daysMask: Int,
        dnd: Boolean,
        windDownMinutes: Int,
    ) {
        context.dataStore.edit {
            it[Keys.BEDTIME_ENABLED] = enabled
            it[Keys.BEDTIME_START] = start
            it[Keys.BEDTIME_END] = end
            it[Keys.BEDTIME_DAYS] = daysMask
            it[Keys.BEDTIME_DND] = dnd
            it[Keys.WIND_DOWN_MINUTES] = windDownMinutes
        }
    }

    suspend fun setDndSetByUs(value: Boolean) {
        context.dataStore.edit { it[Keys.DND_SET_BY_US] = value }
    }

    suspend fun setWindDownNotifiedOn(date: String) {
        context.dataStore.edit { it[Keys.WIND_DOWN_NOTIFIED_ON] = date }
    }

    suspend fun setSession(session: ActiveSession?) {
        context.dataStore.edit { p ->
            if (session == null) {
                p.remove(Keys.SESSION_ID)
                p.remove(Keys.SESSION_START)
                p.remove(Keys.SESSION_END)
                p.remove(Keys.SESSION_STRICT)
            } else {
                p[Keys.SESSION_ID] = session.id
                p[Keys.SESSION_START] = session.startedAt
                p[Keys.SESSION_END] = session.endsAt
                p[Keys.SESSION_STRICT] = session.strict
            }
        }
    }

    companion object {
        const val MAX_HOME_APPS = 7
    }
}
