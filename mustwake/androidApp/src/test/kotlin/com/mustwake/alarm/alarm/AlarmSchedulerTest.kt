package com.mustwake.alarm.alarm

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mustwake.alarm.data.alarm.AlarmRepeatDays
import com.mustwake.alarm.data.db.AlarmEntity
import com.mustwake.alarm.data.db.AlarmEventDao
import com.mustwake.alarm.data.db.AlarmEventEntity
import com.mustwake.alarm.data.db.AlarmEventType
import com.mustwake.alarm.data.history.HistoryRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmSchedulerTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var eventDao: RecordingEventDao
    private lateinit var history: HistoryRepository
    private lateinit var scheduler: AlarmScheduler
    private var now: Instant = LocalDateTime.of(2026, 10, 19, 6, 0).atZone(zone).toInstant()

    private fun localMillis(hour: Int, minute: Int, day: Int = 19): Long =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        eventDao = RecordingEventDao()
        history = HistoryRepository(eventDao) { 0L }
        scheduler = AlarmScheduler(
            context = context,
            historyRepository = history,
            alarmManager = alarmManager,
            clock = { now },
            zone = { zone },
        )
    }

    private fun alarm(
        id: Long = 1L,
        hour: Int = 7,
        minute: Int = 0,
        mask: Int = AlarmRepeatDays.EVERY_DAY,
        enabled: Boolean = true,
    ) = AlarmEntity(
        id = id,
        hour = hour,
        minute = minute,
        repeatDaysMask = mask,
        goalSteps = 50,
        enabled = enabled,
    )

    private fun scheduledTriggerTimes(): List<Long> =
        shadowOf(alarmManager).scheduledAlarms.map { it.triggerAtTime }

    @Test
    fun `schedules the next occurrence of a repeating alarm`() = runTest {
        val outcome = scheduler.schedule(alarm(hour = 7, minute = 0))

        assertTrue(outcome is ScheduleOutcome.Scheduled)
        assertEquals(listOf(localMillis(7, 0)), scheduledTriggerTimes())
    }

    @Test
    fun `rescheduling the same alarm replaces the existing registration`() = runTest {
        scheduler.schedule(alarm(hour = 7, minute = 0))
        now = LocalDateTime.of(2026, 10, 19, 7, 30).atZone(zone).toInstant()

        scheduler.schedule(alarm(hour = 7, minute = 0))

        assertEquals(1, scheduledTriggerTimes().size)
        assertEquals(localMillis(7, 0, day = 20), scheduledTriggerTimes().single())
    }

    @Test
    fun `cancel removes the registration`() = runTest {
        scheduler.schedule(alarm())
        assertTrue(scheduledTriggerTimes().isNotEmpty())

        scheduler.cancel(1L)

        assertTrue(scheduledTriggerTimes().isEmpty())
    }

    @Test
    fun `disabled alarm is skipped and nothing is registered`() = runTest {
        val outcome = scheduler.schedule(alarm(enabled = false))

        assertTrue(outcome is ScheduleOutcome.Skipped)
        assertTrue(scheduledTriggerTimes().isEmpty())
    }

    @Test
    fun `one shot alarm in the past is scheduled for tomorrow`() = runTest {
        scheduler.schedule(alarm(mask = AlarmRepeatDays.NONE, hour = 5, minute = 30))

        assertEquals(localMillis(5, 30, day = 20), scheduledTriggerTimes().single())
    }

    @Test
    fun `snooze registers the provided trigger instant`() = runTest {
        val trigger = now.plusSeconds(300)

        val outcome = scheduler.scheduleSnooze(alarmId = 3L, triggerAt = trigger, steps = 12, snoozeCount = 1)

        assertTrue(outcome is ScheduleOutcome.Scheduled)
        assertEquals(listOf(trigger.toEpochMilli()), scheduledTriggerTimes())
    }

    @Test
    fun `snooze does not replace the regular registration`() = runTest {
        scheduler.schedule(alarm(hour = 7, minute = 0))
        val snoozeAt = now.plusSeconds(300)

        scheduler.scheduleSnooze(alarmId = 1L, triggerAt = snoozeAt, steps = 10, snoozeCount = 1)

        assertEquals(2, scheduledTriggerTimes().size)
        assertTrue(scheduledTriggerTimes().contains(snoozeAt.toEpochMilli()))
        assertTrue(scheduledTriggerTimes().contains(localMillis(7, 0)))
    }

    @Test
    fun `cancel removes both the alarm and its snooze`() = runTest {
        scheduler.schedule(alarm())
        scheduler.scheduleSnooze(alarmId = 1L, triggerAt = now.plusSeconds(300), steps = 1, snoozeCount = 1)

        scheduler.cancel(1L)

        assertTrue(scheduledTriggerTimes().isEmpty())
    }

    @Test
    fun `cancelSnooze keeps the regular registration`() = runTest {
        scheduler.schedule(alarm())
        scheduler.scheduleSnooze(alarmId = 1L, triggerAt = now.plusSeconds(300), steps = 1, snoozeCount = 1)

        scheduler.cancelSnooze(1L)

        assertEquals(listOf(localMillis(7, 0)), scheduledTriggerTimes())
    }

    @Test
    fun `exactness matches the platform capability`() = runTest {
        val outcome = scheduler.schedule(alarm())

        outcome as ScheduleOutcome.Scheduled
        assertEquals(scheduler.canScheduleExactAlarms(), outcome.exact)
    }

    @Test
    fun `scheduling writes a scheduled history event`() = runTest {
        scheduler.schedule(alarm())

        val event = eventDao.stored.single()
        assertEquals(AlarmEventType.SCHEDULED, event.type)
        assertEquals(1L, event.alarmId)
        assertTrue(event.reason!!.contains("exact") || event.reason!!.contains("inexact"))
    }

    @Test
    fun `snooze history captures steps and count`() = runTest {
        scheduler.scheduleSnooze(alarmId = 2L, triggerAt = now.plusSeconds(300), steps = 8, snoozeCount = 2)

        val reason = eventDao.stored.single().reason!!
        assertTrue(reason.contains("snooze#2"))
        assertTrue(reason.contains("8steps"))
    }

    private class RecordingEventDao : AlarmEventDao {
        private val state = MutableStateFlow<List<AlarmEventEntity>>(emptyList())
        private var nextId = 1L

        val stored: List<AlarmEventEntity> get() = state.value

        override suspend fun insert(event: AlarmEventEntity): Long {
            val id = nextId++
            state.value = state.value + event.copy(id = id)
            return id
        }

        override fun observeRecent(limit: Int): Flow<List<AlarmEventEntity>> = state

        override suspend fun getRecent(limit: Int): List<AlarmEventEntity> = state.value

        override fun observeForAlarm(alarmId: Long, limit: Int): Flow<List<AlarmEventEntity>> =
            state.map { events -> events.filter { it.alarmId == alarmId } }

        override suspend fun getForAlarm(alarmId: Long, limit: Int): List<AlarmEventEntity> =
            state.value.filter { it.alarmId == alarmId }

        override suspend fun deleteOlderThan(cutoff: Long): Int = 0

        override suspend fun trimTo(keep: Int): Int = 0

        override suspend fun deleteForAlarm(alarmId: Long): Int = 0

        override suspend fun count(): Int = state.value.size
    }
}
