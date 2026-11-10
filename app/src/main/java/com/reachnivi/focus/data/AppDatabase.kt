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
        CirclePersonEntity::class,
        ConnectionEventEntity::class,
        DailyStepsEntity::class,
        PhotoQuestEntity::class,
        PhoneStackEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun focusDao(): FocusDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "focus.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()

        /** v6: phone stacks. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `phone_stacks` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`code` TEXT NOT NULL, " +
                        "`groupName` TEXT NOT NULL, " +
                        "`role` TEXT NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, " +
                        "`minutes` INTEGER NOT NULL, " +
                        "`endedAt` INTEGER NOT NULL, " +
                        "`outcome` TEXT NOT NULL)",
                )
            }
        }

        /** v5: photo quests (label only, never the image). */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `photo_quests` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`questId` TEXT NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`completedAt` INTEGER NOT NULL, " +
                        "`label` TEXT NOT NULL, " +
                        "`confidence` REAL NOT NULL)",
                )
            }
        }

        /** v4: walk quests (steps per day). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `daily_steps` (`date` TEXT NOT NULL, `steps` INTEGER NOT NULL, PRIMARY KEY(`date`))")
            }
        }

        /** v3: Connection challenge (inner circle and call log). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `circle_people` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`phone` TEXT NOT NULL, " +
                        "`everyDays` INTEGER NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `connection_events` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`personId` INTEGER NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, " +
                        "`talkedSec` INTEGER NOT NULL, " +
                        "`verified` INTEGER NOT NULL, " +
                        "`outcome` TEXT NOT NULL, " +
                        "`mood` INTEGER, " +
                        "`note` TEXT, " +
                        "`source` TEXT NOT NULL, " +
                        "`xp` INTEGER NOT NULL)",
                )
            }
        }

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
