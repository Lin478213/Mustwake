package com.mustwake.alarm.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database, schema version 1.
 *
 * Migrations must be added for every future schema change; destructive migration is
 * intentionally not configured so user alarms and history are never silently dropped.
 */
@Database(
    entities = [AlarmEntity::class, AlarmEventEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MustWakeDatabase : RoomDatabase() {

    abstract fun alarmDao(): AlarmDao

    abstract fun alarmEventDao(): AlarmEventDao

    companion object {
        const val DATABASE_NAME = "mustwake.db"

        @Volatile
        private var instance: MustWakeDatabase? = null

        fun get(context: Context): MustWakeDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): MustWakeDatabase =
            Room.databaseBuilder(context, MustWakeDatabase::class.java, DATABASE_NAME).build()
    }
}
