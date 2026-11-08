package com.reachnivi.focus.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BlockTargetEntity::class,
        ScheduleEntity::class,
        AppLimitEntity::class,
        FocusSessionEntity::class,
        DailyAppUsageEntity::class,
        DailySummaryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun focusDao(): FocusDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "focus.db").build()
    }
}
