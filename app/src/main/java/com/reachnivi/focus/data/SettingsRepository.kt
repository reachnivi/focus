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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focus_settings")

data class AppSettings(
    val blockMode: BlockMode = BlockMode.ALWAYS,
    val dailyGoalMinutes: Int = 180,
    val allowPass: Boolean = true,
    val onboardingDone: Boolean = false,
    val lastFocusMinutes: Int = 25,
    val session: ActiveSession? = null,
)

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
}
