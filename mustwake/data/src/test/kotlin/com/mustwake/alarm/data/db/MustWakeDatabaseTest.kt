package com.mustwake.alarm.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mustwake.alarm.data.alarm.AlarmRepeatDays
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs the real Room DAOs against an in-memory SQLite database.
 *
 * Robolectric is pinned to SDK 34 because it does not ship a runtime for compileSdk 37 yet;
 * the SQL under test is version independent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MustWakeDatabaseTest {

    private lateinit var database: MustWakeDatabase
    private lateinit var alarmDao: AlarmDao
    private lateinit var eventDao: AlarmEventDao

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MustWakeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        alarmDao = database.alarmDao()
        eventDao = database.alarmEventDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun alarm(
        hour: Int = 7,
        minute: Int = 0,
        label: String = "Wake up",
        goalSteps: Int = 50,
        enabled: Boolean = true,
    ) = AlarmEntity(
        hour = hour,
        minute = minute,
        repeatDaysMask = AlarmRepeatDays.EVERY_DAY,
        label = label,
        goalSteps = goalSteps,
        enabled = enabled,
    )

    @Test
    fun `insert and read back an alarm`() = runTest {
        val id = alarmDao.insert(alarm(hour = 6, minute = 45, label = "Run", goalSteps = 80))

        val loaded = alarmDao.getById(id)

        assertNotNull(loaded)
        assertEquals(6, loaded!!.hour)
        assertEquals(45, loaded.minute)
        assertEquals("Run", loaded.label)
        assertEquals(80, loaded.goalSteps)
        assertEquals(AlarmRepeatDays.EVERY_DAY, loaded.repeatDaysMask)
        assertTrue(loaded.enabled)
    }

    @Test
    fun `observeAll orders by time and observeEnabled filters`() = runTest {
        alarmDao.insert(alarm(hour = 9, minute = 30, label = "Late", enabled = false))
        alarmDao.insert(alarm(hour = 6, minute = 15, label = "Early"))
        alarmDao.insert(alarm(hour = 6, minute = 5, label = "Earliest"))

        val all = alarmDao.observeAll().first()
        val enabled = alarmDao.observeEnabled().first()

        assertEquals(listOf("Earliest", "Early", "Late"), all.map { it.label })
        assertEquals(listOf("Earliest", "Early"), enabled.map { it.label })
    }

    @Test
    fun `update replaces fields`() = runTest {
        val id = alarmDao.insert(alarm())
        val loaded = alarmDao.getById(id)!!

        alarmDao.update(loaded.copy(hour = 8, goalSteps = 150, label = "Updated"))

        val updated = alarmDao.getById(id)!!
        assertEquals(8, updated.hour)
        assertEquals(150, updated.goalSteps)
        assertEquals("Updated", updated.label)
    }

    @Test
    fun `setEnabled updates only the flag and timestamp`() = runTest {
        val id = alarmDao.insert(alarm())

        val rows = alarmDao.setEnabled(id, false, updatedAt = 12_345L)

        assertEquals(1, rows)
        val updated = alarmDao.getById(id)!!
        assertFalse(updated.enabled)
        assertEquals(12_345L, updated.updatedAt)
    }

    @Test
    fun `setEnabled on missing alarm affects no rows`() = runTest {
        assertEquals(0, alarmDao.setEnabled(999L, false, 1L))
    }

    @Test
    fun `deleting an alarm cascades to its events`() = runTest {
        val keep = alarmDao.insert(alarm(label = "Keep"))
        val remove = alarmDao.insert(alarm(label = "Remove"))
        eventDao.insert(
            AlarmEventEntity(alarmId = keep, type = AlarmEventType.COMPLETED, steps = 50, timestamp = 1L),
        )
        eventDao.insert(
            AlarmEventEntity(alarmId = remove, type = AlarmEventType.COMPLETED, steps = 60, timestamp = 2L),
        )

        assertEquals(1, alarmDao.deleteById(remove))

        assertEquals(1, eventDao.count())
        assertEquals(keep, eventDao.getRecent(10).single().alarmId)
        assertNull(alarmDao.getById(remove))
    }

    @Test
    fun `recent events are newest first and limited`() = runTest {
        val alarmId = alarmDao.insert(alarm())
        repeat(5) { index ->
            eventDao.insert(
                AlarmEventEntity(
                    alarmId = alarmId,
                    type = AlarmEventType.RINGING,
                    timestamp = 1_000L + index,
                ),
            )
        }

        val recent = eventDao.getRecent(limit = 3)

        assertEquals(3, recent.size)
        assertEquals(1_004L, recent[0].timestamp)
        assertEquals(1_002L, recent[2].timestamp)
    }

    @Test
    fun `events can be queried per alarm`() = runTest {
        val first = alarmDao.insert(alarm(label = "First"))
        val second = alarmDao.insert(alarm(label = "Second"))
        eventDao.insert(AlarmEventEntity(alarmId = first, type = AlarmEventType.MISSED, timestamp = 1L))
        eventDao.insert(AlarmEventEntity(alarmId = second, type = AlarmEventType.COMPLETED, timestamp = 2L))

        val events = eventDao.getForAlarm(second, limit = 10)

        assertEquals(1, events.size)
        assertEquals(AlarmEventType.COMPLETED, events.single().type)
    }

    @Test
    fun `event type round trips as string`() = runTest {
        val alarmId = alarmDao.insert(alarm())
        eventDao.insert(
            AlarmEventEntity(
                alarmId = alarmId,
                type = AlarmEventType.DEGRADED,
                steps = 30,
                timestamp = 5L,
                reason = "no step sensor",
            ),
        )

        val loaded = eventDao.getRecent(1).single()

        assertEquals(AlarmEventType.DEGRADED, loaded.type)
        assertEquals(30, loaded.steps)
        assertEquals("no step sensor", loaded.reason)
    }

    @Test
    fun `trimTo keeps the newest events`() = runTest {
        val alarmId = alarmDao.insert(alarm())
        repeat(5) { index ->
            eventDao.insert(
                AlarmEventEntity(alarmId = alarmId, type = AlarmEventType.RINGING, timestamp = index.toLong()),
            )
        }

        val removed = eventDao.trimTo(keep = 2)

        assertEquals(3, removed)
        assertEquals(2, eventDao.count())
        assertEquals(listOf(4L, 3L), eventDao.getRecent(10).map { it.timestamp })
    }

    @Test
    fun `deleteOlderThan removes expired events`() = runTest {
        val alarmId = alarmDao.insert(alarm())
        eventDao.insert(AlarmEventEntity(alarmId = alarmId, type = AlarmEventType.COMPLETED, timestamp = 100L))
        eventDao.insert(AlarmEventEntity(alarmId = alarmId, type = AlarmEventType.COMPLETED, timestamp = 500L))

        val removed = eventDao.deleteOlderThan(cutoff = 200L)

        assertEquals(1, removed)
        assertEquals(500L, eventDao.getRecent(10).single().timestamp)
    }
}
