package com.reachnivi.focus.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/** The call in progress, if any. Kept in SharedPreferences so it survives the process being killed mid-call. */
data class ActiveChallenge(
    val personId: Long,
    val personName: String,
    val phone: String,
    val startedAt: Long,
    val source: String,
    val talkedSec: Int,
    val lastSampleAt: Long,
    /** True once the phone has been sampled at least once (the blocker service was running). */
    val sampled: Boolean,
    val inCallNow: Boolean,
)

class ChallengeStore(context: Context) {
    private val prefs = context.getSharedPreferences("connection_challenge", Context.MODE_PRIVATE)
    private val _active = MutableStateFlow(read())
    val active: StateFlow<ActiveChallenge?> = _active.asStateFlow()

    private val _skipped = MutableStateFlow(readSkipped())
    /** Circle members the user skipped today. */
    val skippedToday: StateFlow<Set<Long>> = _skipped.asStateFlow()

    fun start(personId: Long, name: String, phone: String, source: String) {
        val now = System.currentTimeMillis()
        prefs.edit()
            .putLong(K_PERSON, personId).putString(K_NAME, name).putString(K_PHONE, phone)
            .putLong(K_STARTED, now).putString(K_SOURCE, source)
            .putInt(K_TALKED, 0).putLong(K_LAST_SAMPLE, now).putBoolean(K_SAMPLED, false).putBoolean(K_IN_CALL, false)
            .apply()
        _active.value = read()
    }

    fun recordSample(talkedSec: Int, at: Long, inCall: Boolean) {
        prefs.edit().putInt(K_TALKED, talkedSec).putLong(K_LAST_SAMPLE, at).putBoolean(K_SAMPLED, true).putBoolean(K_IN_CALL, inCall).apply()
        _active.value = read()
    }

    fun clear() {
        prefs.edit().remove(K_PERSON).remove(K_NAME).remove(K_PHONE).remove(K_STARTED).remove(K_SOURCE)
            .remove(K_TALKED).remove(K_LAST_SAMPLE).remove(K_SAMPLED).remove(K_IN_CALL).apply()
        _active.value = null
    }

    fun skipToday(personId: Long) {
        val today = LocalDate.now().toString()
        val current = if (prefs.getString(K_SKIP_DATE, null) == today) readSkipped() else emptySet()
        prefs.edit().putString(K_SKIP_DATE, today).putString(K_SKIP_IDS, (current + personId).joinToString(",")).apply()
        _skipped.value = readSkipped()
    }

    /** Re-reads date-dependent state; call on resume so yesterday's skips expire. */
    fun refresh() {
        _skipped.value = readSkipped()
        _active.value = read()
    }

    private fun read(): ActiveChallenge? {
        if (!prefs.contains(K_PERSON)) return null
        return ActiveChallenge(
            personId = prefs.getLong(K_PERSON, 0),
            personName = prefs.getString(K_NAME, "") ?: "",
            phone = prefs.getString(K_PHONE, "") ?: "",
            startedAt = prefs.getLong(K_STARTED, 0),
            source = prefs.getString(K_SOURCE, SOURCE_TODAY) ?: SOURCE_TODAY,
            talkedSec = prefs.getInt(K_TALKED, 0),
            lastSampleAt = prefs.getLong(K_LAST_SAMPLE, 0),
            sampled = prefs.getBoolean(K_SAMPLED, false),
            inCallNow = prefs.getBoolean(K_IN_CALL, false),
        )
    }

    private fun readSkipped(): Set<Long> {
        if (prefs.getString(K_SKIP_DATE, null) != LocalDate.now().toString()) return emptySet()
        return prefs.getString(K_SKIP_IDS, "")?.split(',')?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    }

    companion object {
        const val SOURCE_TODAY = "TODAY"
        const val SOURCE_PAUSE = "PAUSE"
        const val SOURCE_BLOCK = "BLOCK"
        const val SOURCE_CIRCLE = "CIRCLE"

        /** A challenge older than this is closed out with whatever was measured. */
        const val MAX_AGE_MS = 2 * 60 * 60 * 1000L

        private const val K_PERSON = "person_id"
        private const val K_NAME = "person_name"
        private const val K_PHONE = "phone"
        private const val K_STARTED = "started_at"
        private const val K_SOURCE = "source"
        private const val K_TALKED = "talked_sec"
        private const val K_LAST_SAMPLE = "last_sample_at"
        private const val K_SAMPLED = "sampled"
        private const val K_IN_CALL = "in_call"
        private const val K_SKIP_DATE = "skip_date"
        private const val K_SKIP_IDS = "skip_ids"
    }
}
