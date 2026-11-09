package com.reachnivi.focus.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BlockTargetEntity::class,
        ScheduleEntity::class,
        AppLimitEntity::class,
        FocusSessionEntity::class,
        DailyAppUsageEntity::class,
        DailySummaryEntity::class,
        PauseEventEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun focusDao(): FocusDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "focus.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v2: mindful-pause decisions. Paused apps reuse block_targets (type = 'pause'). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pause_events` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`packageName` TEXT NOT NULL, " +
                        "`at` INTEGER NOT NULL, " +
                        "`outcome` TEXT NOT NULL, " +
                        "`minutes` INTEGER NOT NULL)",
                )
            }
        }
    }
}
