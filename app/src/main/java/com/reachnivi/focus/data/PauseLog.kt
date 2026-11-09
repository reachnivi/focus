package com.reachnivi.focus.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Records what people choose on the mindful-pause screen. Writes outlive the activity that asked. */
class PauseLog(private val dao: FocusDao, private val scope: CoroutineScope) {

    fun opened(packageName: String, minutes: Int) = record(packageName, PauseEventEntity.OPENED, minutes)

    fun skipped(packageName: String) = record(packageName, PauseEventEntity.SKIPPED, 0)

    fun since(since: Long): Flow<List<PauseEventEntity>> = dao.observePauseEventsSince(since)

    private fun record(packageName: String, outcome: String, minutes: Int) {
        scope.launch {
            dao.insertPauseEvent(PauseEventEntity(packageName = packageName, at = System.currentTimeMillis(), outcome = outcome, minutes = minutes))
        }
    }
}
