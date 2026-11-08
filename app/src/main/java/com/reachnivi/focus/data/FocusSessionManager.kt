package com.reachnivi.focus.data

import com.reachnivi.focus.block.SessionNotifier
import com.reachnivi.focus.core.ActiveSession
import kotlinx.coroutines.flow.Flow

class FocusSessionManager(
    private val dao: FocusDao,
    private val settings: SettingsRepository,
    private val notifier: SessionNotifier,
) {
    suspend fun start(minutes: Int, strict: Boolean) {
        val now = System.currentTimeMillis()
        val end = now + minutes * 60_000L
        val id = dao.insertSession(FocusSessionEntity(startedAt = now, plannedEndAt = end, strict = strict))
        settings.setSession(ActiveSession(id, now, end, strict))
        settings.setLastFocusMinutes(minutes)
        notifier.show(end, strict)
    }

    /** Ends the running session early. Strict sessions refuse; returns false then. */
    suspend fun stop(): Boolean {
        val s = settings.current().session ?: return true
        val now = System.currentTimeMillis()
        if (s.strict && s.isRunning(now)) return false
        dao.endSession(s.id, minOf(now, s.endsAt))
        settings.setSession(null)
        notifier.cancel()
        return true
    }

    /** Records sessions that finished on their own. Safe to call often. */
    suspend fun completeIfExpired() {
        val s = settings.current().session ?: return
        if (System.currentTimeMillis() >= s.endsAt) {
            dao.endSession(s.id, s.endsAt)
            settings.setSession(null)
            notifier.cancel()
        }
    }

    fun sessionsSince(since: Long): Flow<List<FocusSessionEntity>> = dao.observeSessionsSince(since)
}
