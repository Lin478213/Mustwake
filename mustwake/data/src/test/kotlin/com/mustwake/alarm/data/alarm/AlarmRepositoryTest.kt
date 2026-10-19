package com.mustwake.alarm.data.alarm

import com.mustwake.alarm.data.db.AlarmEntity
import com.mustwake.alarm.data.db.FakeAlarmDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmRepositoryTest {

    private val dao = FakeAlarmDao()
    private var now = 1_000L
    private val repository = AlarmRepository(dao) { now }

    private fun alarm(
        id: Long = 0L,
        hour: Int = 7,
        minute: Int = 30,
        repeatDaysMask: Int = AlarmRepeatDays.EVERY_DAY,
        label: String = "Wake up",
        goalSteps: Int = 50,
        enabled: Boolean = true,
    ) = AlarmEntity(
        id = id,
        hour = hour,
        minute = minute,
        repeatDaysMask = repeatDaysMask,
        label = label,
        goalSteps = goalSteps,
        enabled = enabled,
    )

    @Test
    fun `insert assigns id and timestamps`() = runTest {
        val id = repository.save(alarm())

        val saved = repository.getAlarm(id)
        assertEquals(id, saved?.id)
        assertEquals(1_000L, saved?.createdAt)
        assertEquals(1_000L, saved?.updatedAt)
        assertEquals(1, dao.stored.size)
    }

    @Test
    fun `update preserves createdAt and refreshes updatedAt`() = runTest {
        val id = repository.save(alarm(label = "Old"))
        now = 5_000L

        repository.save(alarm(id = id, label = "New", goalSteps = 120))

        val saved = repository.getAlarm(id)
        assertEquals("New", saved?.label)
        assertEquals(120, saved?.goalSteps)
        assertEquals(1_000L, saved?.createdAt)
        assertEquals(5_000L, saved?.updatedAt)
    }

    @Test
    fun `label is trimmed on save`() = runTest {
        val id = repository.save(alarm(label = "  Morning walk  "))
        assertEquals("Morning walk", repository.getAlarm(id)?.label)
    }

    @Test
    fun `invalid hour is rejected`() = runTest {
        assertRejected { repository.save(alarm(hour = 24)) }
        assertTrue(dao.stored.isEmpty())
    }

    @Test
    fun `invalid minute is rejected`() = runTest {
        assertRejected { repository.save(alarm(minute = 60)) }
    }

    @Test
    fun `invalid goal steps are rejected`() = runTest {
        assertRejected { repository.save(alarm(goalSteps = 9)) }
        assertRejected { repository.save(alarm(goalSteps = 501)) }
        assertTrue(dao.stored.isEmpty())
    }

    @Test
    fun `invalid repeat mask is rejected`() = runTest {
        assertRejected { repository.save(alarm(repeatDaysMask = 128)) }
    }

    @Test
    fun `label longer than 40 characters is rejected`() = runTest {
        assertRejected { repository.save(alarm(label = "x".repeat(41))) }
    }

    @Test
    fun `updating a missing alarm is rejected`() = runTest {
        assertRejected { repository.save(alarm(id = 999L)) }
    }

    @Test
    fun `setEnabled returns false for unknown alarm`() = runTest {
        assertFalse(repository.setEnabled(42L, false))
    }

    @Test
    fun `setEnabled toggles and stamps updatedAt`() = runTest {
        val id = repository.save(alarm())
        now = 9_000L

        assertTrue(repository.setEnabled(id, false))

        val saved = repository.getAlarm(id)
        assertFalse(saved!!.enabled)
        assertEquals(9_000L, saved.updatedAt)
    }

    @Test
    fun `delete removes the alarm`() = runTest {
        val id = repository.save(alarm())
        assertTrue(repository.delete(id))
        assertNull(repository.getAlarm(id))
        assertFalse(repository.delete(id))
    }

    @Test
    fun `repeat mask helpers map weekdays`() {
        assertEquals(1, AlarmRepeatDays.bitFor(1))
        assertEquals(64, AlarmRepeatDays.bitFor(7))
        assertTrue(AlarmRepeatDays.isDayEnabled(AlarmRepeatDays.EVERY_DAY, 3))
        assertFalse(AlarmRepeatDays.isDayEnabled(AlarmRepeatDays.MONDAY, 2))
    }

    private suspend fun assertRejected(block: suspend () -> Unit) {
        val error = runCatching { block() }.exceptionOrNull()
        assertTrue("expected IllegalArgumentException but was $error", error is IllegalArgumentException)
    }
}
