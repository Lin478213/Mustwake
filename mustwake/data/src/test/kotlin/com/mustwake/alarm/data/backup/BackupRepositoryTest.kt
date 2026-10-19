package com.mustwake.alarm.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.mustwake.alarm.data.alarm.AlarmRepository
import com.mustwake.alarm.data.db.AlarmDao
import com.mustwake.alarm.data.db.AlarmEntity
import com.mustwake.alarm.data.db.FakeAlarmDao
import com.mustwake.alarm.data.settings.InMemoryPreferencesDataStore
import com.mustwake.alarm.data.settings.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackupRepositoryTest {

    private lateinit var dao: FakeAlarmDao
    private lateinit var alarmRepository: AlarmRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var transactions: FakeTransactionRunner
    private lateinit var repository: BackupRepository

    @Before
    fun setUp() {
        dao = FakeAlarmDao()
        alarmRepository = AlarmRepository(dao)
        settingsRepository = SettingsRepository(InMemoryPreferencesDataStore())
        transactions = FakeTransactionRunner(dao)
        repository = BackupRepository(dao, settingsRepository, transactions) { 42L }
    }

    private suspend fun seed(
        hour: Int = 7,
        minute: Int = 0,
        repeatDaysMask: Int = 127,
        label: String = "Work",
        goalSteps: Int = 50,
        enabled: Boolean = true,
        createdAt: Long = 0L,
        updatedAt: Long = 0L,
    ): Long = dao.insert(
        AlarmEntity(
            hour = hour,
            minute = minute,
            repeatDaysMask = repeatDaysMask,
            label = label,
            goalSteps = goalSteps,
            enabled = enabled,
            createdAt = createdAt,
            updatedAt = updatedAt,
        ),
    )

    private fun backupOf(vararg alarms: BackupAlarm, settings: BackupSettings = BackupSettings()): String =
        BackupCodec.encode(
            BackupData(
                schemaVersion = BackupCodec.SCHEMA_VERSION,
                exportedAt = 1L,
                alarms = alarms.toList(),
                settings = settings,
            ),
        )

    @Test
    fun `device migration round trip restores alarms and settings exactly`() = runTest {
        settingsRepository.setFloatingBottomBarEnabled(true)
        settingsRepository.setLiquidGlassEnabled(true)
        settingsRepository.setDefaultGoalSteps(80)
        settingsRepository.setSnoozeMinutes(10)
        settingsRepository.setMaxSnoozes(1)
        settingsRepository.setShakeCount(40)
        settingsRepository.setOnboardingCompleted(true)
        seed(hour = 7, minute = 0, label = "Work", goalSteps = 30, createdAt = 100L, updatedAt = 200L)
        seed(hour = 22, minute = 30, repeatDaysMask = 0, label = "Sleep", goalSteps = 200, enabled = false, createdAt = 300L, updatedAt = 400L)

        val exported = repository.exportJson()
        val alarmsBefore = alarmRepository.getAllAlarms()
        val settingsBefore = settingsRepository.currentSettings()
        dao.deleteAll()

        val report = repository.importJson(exported, ImportMode.MERGE)

        assertEquals(2, report.imported)
        assertEquals(0, report.skippedInvalid)
        assertEquals(0, report.skippedDuplicates)
        // Full entity equality covers ids plus created_at/updated_at restored verbatim.
        assertEquals(alarmsBefore, alarmRepository.getAllAlarms())
        assertEquals(settingsBefore, settingsRepository.currentSettings())
    }

    @Test
    fun `replace mode clears existing alarms and imports the backup`() = runTest {
        seed(hour = 5, label = "Stale")
        seed(hour = 6, label = "Also stale")
        val id = seed(hour = 8, label = "Kept", goalSteps = 90, createdAt = 10L, updatedAt = 20L)

        val json = backupOf(
            BackupAlarm(
                id = id,
                hour = 8,
                minute = 0,
                repeatDaysMask = 0,
                label = "Kept",
                goalSteps = 90,
                enabled = true,
                createdAt = 10L,
                updatedAt = 20L,
            ),
        )
        val report = repository.importJson(json, ImportMode.REPLACE)

        assertEquals(1, report.imported)
        assertEquals(listOf("Kept"), alarmRepository.getAllAlarms().map { it.label })
    }

    @Test
    fun `failed insert rolls back the whole import`() = runTest {
        seed(hour = 5, label = "Existing", createdAt = 1L, updatedAt = 2L)
        val alarmsBefore = alarmRepository.getAllAlarms()
        val settingsBefore = settingsRepository.currentSettings()

        // Two rows import fine, the third insert blows up mid-transaction.
        val throwingDao = ThrowingAlarmDao(dao, failAfterInserts = 2)
        val failing = BackupRepository(throwingDao, settingsRepository, FakeTransactionRunner(dao)) { 42L }
        val json = backupOf(
            BackupAlarm(hour = 7, minute = 0, label = "A", createdAt = 11L, updatedAt = 12L),
            BackupAlarm(hour = 8, minute = 0, label = "B", createdAt = 21L, updatedAt = 22L),
            BackupAlarm(hour = 9, minute = 0, label = "C", createdAt = 31L, updatedAt = 32L),
        )

        val report = failing.importJson(json, ImportMode.MERGE)

        assertEquals(0, report.imported)
        assertTrue(report.details.single().startsWith("import failed"))
        // Blocker acceptance: a mid-import failure leaves data exactly as before the import.
        assertEquals(alarmsBefore, alarmRepository.getAllAlarms())
        assertEquals(settingsBefore, settingsRepository.currentSettings())
    }

    @Test
    fun `merge keeps existing alarm on duplicate key and reports the kept goalSteps`() = runTest {
        seed(hour = 7, minute = 0, repeatDaysMask = 127, label = "Work", goalSteps = 30, enabled = true)
        val json = backupOf(
            BackupAlarm(hour = 7, minute = 0, repeatDaysMask = 127, label = "Work", goalSteps = 120, enabled = false),
            BackupAlarm(hour = 9, minute = 15, repeatDaysMask = 0, label = "New", goalSteps = 60),
        )

        val report = repository.importJson(json, ImportMode.MERGE)

        assertEquals(1, report.imported)
        assertEquals(1, report.skippedDuplicates)
        assertTrue(report.details.single().contains("goalSteps=30"))
        assertTrue(report.details.single().contains("kept existing"))
        val kept = alarmRepository.getAllAlarms().first { it.label == "Work" }
        // goalSteps/enabled of the existing alarm are never overwritten.
        assertEquals(30, kept.goalSteps)
        assertTrue(kept.enabled)
        assertEquals(2, alarmRepository.getAllAlarms().size)
    }

    @Test
    fun `invalid rows are skipped and counted`() = runTest {
        val json = backupOf(
            BackupAlarm(hour = 25, minute = 0, label = "bad hour"),
            BackupAlarm(hour = 7, minute = 60, label = "bad minute"),
            BackupAlarm(hour = 7, minute = 0, repeatDaysMask = 128, label = "bad mask"),
            BackupAlarm(hour = 7, minute = 0, label = "x".repeat(41)),
            BackupAlarm(hour = 7, minute = 0, label = "goal too high", goalSteps = 501),
            BackupAlarm(hour = 7, minute = 0, label = "goal too low", goalSteps = 9),
            BackupAlarm(hour = 7, minute = 30, label = "Good", createdAt = 5L, updatedAt = 6L),
        )

        val report = repository.importJson(json, ImportMode.MERGE)

        assertEquals(1, report.imported)
        assertEquals(6, report.skippedInvalid)
        assertEquals(0, report.skippedDuplicates)
        assertEquals(6, report.details.count { it.startsWith("skipped: invalid") })
        assertEquals(listOf("Good"), alarmRepository.getAllAlarms().map { it.label })
    }

    @Test
    fun `import never touches onboarding_completed`() = runTest {
        settingsRepository.setOnboardingCompleted(true)

        val json = backupOf(
            settings = BackupSettings(
                floatingBottomBarEnabled = true,
                liquidGlassEnabled = true,
                defaultGoalSteps = 120,
                snoozeMinutes = 15,
                maxSnoozes = 0,
                shakeCount = 100,
            ),
        )
        repository.importJson(json, ImportMode.REPLACE)

        val settings = settingsRepository.currentSettings()
        assertTrue(settings.onboardingCompleted)
        assertTrue(settings.floatingBottomBarEnabled)
        assertEquals(120, settings.defaultGoalSteps)
    }

    @Test
    fun `malformed or unknown backup leaves data unchanged and reports failure`() = runTest {
        seed(hour = 7, label = "Existing")
        val alarmsBefore = alarmRepository.getAllAlarms()

        val malformed = repository.importJson("not json", ImportMode.REPLACE)
        val unknown = repository.importJson(
            """{"schemaVersion":99,"exportedAt":0,"alarms":[],"settings":{}}""",
            ImportMode.MERGE,
        )

        assertEquals(0, malformed.imported)
        assertTrue(malformed.details.single().startsWith("invalid backup file"))
        assertEquals(0, unknown.imported)
        assertTrue(unknown.details.single().startsWith("invalid backup file"))
        assertEquals(alarmsBefore, alarmRepository.getAllAlarms())
    }

    @Test
    fun `out of range settings values fall back to defaults on import`() = runTest {
        val json = backupOf(
            settings = BackupSettings(
                defaultGoalSteps = 9_999,
                snoozeMinutes = 0,
                maxSnoozes = 42,
                shakeCount = -5,
            ),
        )

        repository.importJson(json, ImportMode.REPLACE)

        val settings = settingsRepository.currentSettings()
        assertEquals(50, settings.defaultGoalSteps)
        assertEquals(5, settings.snoozeMinutes)
        assertEquals(3, settings.maxSnoozes)
        assertEquals(30, settings.shakeCount)
    }

    @Test
    fun `empty backup clears existing alarms in replace mode and leaves alarms untouched in merge`() = runTest {
        val empty = backupOf()

        seed(hour = 7, label = "A")
        val mergeReport = repository.importJson(empty, ImportMode.MERGE)
        assertEquals(0, mergeReport.imported)
        assertEquals(1, alarmRepository.getAllAlarms().size)

        val replaceReport = repository.importJson(empty, ImportMode.REPLACE)
        assertEquals(0, replaceReport.imported)
        assertTrue(alarmRepository.getAllAlarms().isEmpty())
    }

    @Test
    fun `replace mode imports duplicate keys inside the backup without dedup`() = runTest {
        val json = backupOf(
            BackupAlarm(hour = 7, minute = 0, label = "Same"),
            BackupAlarm(hour = 7, minute = 0, label = "Same"),
        )

        val report = repository.importJson(json, ImportMode.REPLACE)

        assertEquals(2, report.imported)
        assertEquals(0, report.skippedDuplicates)
        assertEquals(2, alarmRepository.getAllAlarms().size)
    }

    @Test
    fun `one report counts imported invalid and duplicate rows together`() = runTest {
        seed(hour = 7, minute = 0, repeatDaysMask = 127, label = "Work", goalSteps = 30)
        val json = backupOf(
            BackupAlarm(hour = 7, minute = 0, repeatDaysMask = 127, label = "Work", goalSteps = 120),
            BackupAlarm(hour = 25, minute = 0, label = "Bad"),
            BackupAlarm(hour = 9, minute = 15, label = "New"),
        )

        val report = repository.importJson(json, ImportMode.MERGE)

        assertEquals(1, report.imported)
        assertEquals(1, report.skippedInvalid)
        assertEquals(1, report.skippedDuplicates)
    }

    @Test
    fun `negative timestamps are rejected as invalid rows`() = runTest {
        val json = backupOf(
            BackupAlarm(hour = 7, minute = 0, label = "Bad created", createdAt = -1L),
            BackupAlarm(hour = 8, minute = 0, label = "Bad updated", updatedAt = -5L),
        )

        val report = repository.importJson(json, ImportMode.MERGE)

        assertEquals(0, report.imported)
        assertEquals(2, report.skippedInvalid)
        assertTrue(alarmRepository.getAllAlarms().isEmpty())
    }

    @Test
    fun `settings write failure is reported and never throws after alarms commit`() = runTest {
        val failingSettings = SettingsRepository(FailingPreferencesDataStore())
        val repo = BackupRepository(dao, failingSettings, FakeTransactionRunner(dao)) { 42L }
        val json = backupOf(
            BackupAlarm(hour = 7, minute = 0, label = "New", createdAt = 1L, updatedAt = 2L),
        )

        val report = repo.importJson(json, ImportMode.MERGE)

        // "Never throws" contract: alarms stay imported, the settings IO failure is reported.
        assertEquals(1, report.imported)
        assertTrue(report.details.single().startsWith("alarms imported, settings failed"))
        assertEquals(1, alarmRepository.getAllAlarms().size)
    }

    /** Runs [block] like a transaction: restores the [FakeAlarmDao] table if it throws. */
    private class FakeTransactionRunner(
        private val dao: FakeAlarmDao,
    ) : BackupTransactionRunner {
        override suspend fun <T> inTransaction(block: suspend () -> T): T {
            val snapshot = dao.stored
            try {
                return block()
            } catch (t: Throwable) {
                dao.restore(snapshot)
                throw t
            }
        }
    }

    /** Delegates to [delegate] but fails inserts after [failAfterInserts] succeeded ones. */
    private class ThrowingAlarmDao(
        private val delegate: FakeAlarmDao,
        private val failAfterInserts: Int,
    ) : AlarmDao by delegate {
        private var inserts = 0

        override suspend fun insert(alarm: AlarmEntity): Long {
            if (inserts >= failAfterInserts) throw IllegalStateException("disk full")
            inserts++
            return delegate.insert(alarm)
        }
    }

    /** DataStore whose writes always fail, to pin the "import never throws" contract. */
    private class FailingPreferencesDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = emptyFlow()
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException("disk full")
    }
}
