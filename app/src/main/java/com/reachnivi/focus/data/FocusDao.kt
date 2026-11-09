package com.reachnivi.focus.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusDao {
    // Block list
    @Query("SELECT * FROM block_targets ORDER BY addedAt DESC")
    fun observeTargets(): Flow<List<BlockTargetEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTarget(target: BlockTargetEntity)

    @Query("DELETE FROM block_targets WHERE type = :type AND value = :value")
    suspend fun deleteTarget(type: String, value: String)

    // Schedules
    @Query("SELECT * FROM schedules ORDER BY startMinute")
    fun observeSchedules(): Flow<List<ScheduleEntity>>

    @Upsert
    suspend fun upsertSchedule(schedule: ScheduleEntity)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun deleteSchedule(id: Long)

    // Daily limits
    @Query("SELECT * FROM app_limits ORDER BY minutesPerDay")
    fun observeLimits(): Flow<List<AppLimitEntity>>

    @Upsert
    suspend fun upsertLimit(limit: AppLimitEntity)

    @Query("DELETE FROM app_limits WHERE packageName = :packageName")
    suspend fun deleteLimit(packageName: String)

    // Focus sessions
    @Insert
    suspend fun insertSession(session: FocusSessionEntity): Long

    @Query("UPDATE focus_sessions SET endedAt = :endedAt WHERE id = :id")
    suspend fun endSession(id: Long, endedAt: Long)

    @Query("SELECT * FROM focus_sessions WHERE startedAt >= :since ORDER BY startedAt DESC")
    fun observeSessionsSince(since: Long): Flow<List<FocusSessionEntity>>

    // Usage snapshots
    @Upsert
    suspend fun upsertAppUsage(rows: List<DailyAppUsageEntity>)

    @Upsert
    suspend fun upsertSummary(summary: DailySummaryEntity)

    @Query("SELECT * FROM daily_summary WHERE date >= :fromDate ORDER BY date")
    suspend fun summariesFrom(fromDate: String): List<DailySummaryEntity>

    // Mindful pause
    @Insert
    suspend fun insertPauseEvent(event: PauseEventEntity)

    @Query("SELECT * FROM pause_events WHERE at >= :since ORDER BY at DESC")
    fun observePauseEventsSince(since: Long): Flow<List<PauseEventEntity>>
}
