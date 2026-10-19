package com.mustwake.alarm.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies [MustWakeDatabase.MIGRATION_1_2] on a real SQLite database created with the v1 schema.
 *
 * Room validates the resulting schema against `2.json` when a real device upgrades; this test
 * covers the parts that can be checked without instrumentation: the SQL executes, all expected
 * columns/indices exist and the foreign key is in place.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration1To2Test {

    private lateinit var context: Context
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB_NAME)
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) = createVersion1(db)

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
    }

    @After
    fun tearDown() {
        helper.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `migration adds alarm_sessions with expected columns`() {
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO alarms (id, hour, minute, repeat_days_mask, label, goal_steps, enabled, " +
                "created_at, updated_at) VALUES (1, 7, 0, 127, 'Wake up', 50, 1, 0, 0)",
        )

        MustWakeDatabase.MIGRATION_1_2.migrate(db)

        db.query("PRAGMA table_info(`alarm_sessions`)").use { cursor ->
            val columns = buildList {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertEquals(
                listOf(
                    "id",
                    "alarm_id",
                    "state",
                    "started_at",
                    "updated_at",
                    "steps",
                    "snooze_count",
                    "deadline_at",
                ),
                columns,
            )
        }

        assertEquals(1, count(db, "SELECT COUNT(*) FROM alarms"))
    }

    @Test
    fun `migration creates indices and cascade foreign key`() {
        val db = helper.writableDatabase

        MustWakeDatabase.MIGRATION_1_2.migrate(db)

        val indices = buildList {
            db.query(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'alarm_sessions'",
            ).use { cursor ->
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        assertTrue(indices.contains("index_alarm_sessions_alarm_id"))
        assertTrue(indices.contains("index_alarm_sessions_state"))

        db.query("PRAGMA foreign_key_list(`alarm_sessions`)").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("alarms", cursor.getString(cursor.getColumnIndexOrThrow("table")))
            assertEquals("alarm_id", cursor.getString(cursor.getColumnIndexOrThrow("from")))
            assertEquals("id", cursor.getString(cursor.getColumnIndexOrThrow("to")))
            assertEquals("CASCADE", cursor.getString(cursor.getColumnIndexOrThrow("on_delete")))
        }
    }

    @Test
    fun `sessions can be inserted after migration`() {
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO alarms (id, hour, minute, repeat_days_mask, label, goal_steps, enabled, " +
                "created_at, updated_at) VALUES (1, 7, 0, 127, 'Wake up', 50, 1, 0, 0)",
        )

        MustWakeDatabase.MIGRATION_1_2.migrate(db)
        db.execSQL(
            "INSERT INTO alarm_sessions (alarm_id, state, started_at, updated_at, steps, " +
                "snooze_count, deadline_at) VALUES (1, 'SNOOZED', 100, 200, 12, 1, 500)",
        )

        assertEquals(1, count(db, "SELECT COUNT(*) FROM alarm_sessions"))
    }

    private fun count(db: SupportSQLiteDatabase, sql: String): Int =
        db.query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun createVersion1(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `alarms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, " +
                "`repeat_days_mask` INTEGER NOT NULL, `label` TEXT NOT NULL, " +
                "`goal_steps` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `alarm_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`alarm_id` INTEGER NOT NULL, `type` TEXT NOT NULL, `steps` INTEGER NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, `reason` TEXT, FOREIGN KEY(`alarm_id`) " +
                "REFERENCES `alarms`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_alarm_events_alarm_id` ON `alarm_events` (`alarm_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_alarm_events_timestamp` ON `alarm_events` (`timestamp`)",
        )
    }

    private companion object {
        const val DB_NAME = "migration-1-2-test.db"
    }
}
