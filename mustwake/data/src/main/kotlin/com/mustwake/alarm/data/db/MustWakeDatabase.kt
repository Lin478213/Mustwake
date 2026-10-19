package com.mustwake.alarm.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database, schema version 2.
 *
 * Version 2 adds [AlarmSessionEntity] (ringing sessions, snooze deadlines, missed sweep).
 * Destructive migration is intentionally not configured: user alarms and history must survive
 * upgrades, so every version bump needs an explicit [Migration].
 */
@Database(
    entities = [AlarmEntity::class, AlarmEventEntity::class, AlarmSessionEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class MustWakeDatabase : RoomDatabase() {

    abstract fun alarmDao(): AlarmDao

    abstract fun alarmEventDao(): AlarmEventDao

    abstract fun alarmSessionDao(): AlarmSessionDao

    companion object {
        const val DATABASE_NAME = "mustwake.db"

        /** v1 -> v2: add the `alarm_sessions` table and its indices. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `alarm_sessions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`alarm_id` INTEGER NOT NULL, " +
                        "`state` TEXT NOT NULL, " +
                        "`started_at` INTEGER NOT NULL, " +
                        "`updated_at` INTEGER NOT NULL, " +
                        "`steps` INTEGER NOT NULL, " +
                        "`snooze_count` INTEGER NOT NULL, " +
                        "`deadline_at` INTEGER, " +
                        "FOREIGN KEY(`alarm_id`) REFERENCES `alarms`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_alarm_sessions_alarm_id` " +
                        "ON `alarm_sessions` (`alarm_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_alarm_sessions_state` " +
                        "ON `alarm_sessions` (`state`)",
                )
            }
        }

        @Volatile
        private var instance: MustWakeDatabase? = null

        fun get(context: Context): MustWakeDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): MustWakeDatabase =
            Room.databaseBuilder(context, MustWakeDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
