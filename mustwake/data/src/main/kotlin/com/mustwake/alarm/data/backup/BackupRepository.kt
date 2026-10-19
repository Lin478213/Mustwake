package com.mustwake.alarm.data.backup

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.mustwake.alarm.data.alarm.AlarmConstraints
import com.mustwake.alarm.data.alarm.AlarmRepeatDays
import com.mustwake.alarm.data.db.AlarmDao
import com.mustwake.alarm.data.db.AlarmEntity
import com.mustwake.alarm.data.settings.AppSettings
import com.mustwake.alarm.data.settings.SettingsConstraints
import com.mustwake.alarm.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** How imported alarms combine with the ones already stored. */
enum class ImportMode {
    /** Keep existing alarms; skip backup rows whose identity key is already present. */
    MERGE,

    /** Delete every stored alarm first, then import the whole backup. */
    REPLACE,
}

/**
 * Outcome of one import.
 *
 * [details] holds one human-readable line per skipped or failed item (or one line for a
 * whole-file failure), so the UI can show why rows did not make it in.
 */
data class ImportReport(
    val mode: ImportMode,
    val imported: Int,
    val skippedInvalid: Int,
    val skippedDuplicates: Int,
    val details: List<String>,
)

/**
 * Runs [block] as one atomic database transaction: if [block] throws, every write it
 * performed is rolled back before the exception reaches the caller.
 */
interface BackupTransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}

/** [BackupTransactionRunner] backed by Room's `withTransaction`. */
class RoomBackupTransactionRunner(
    private val database: RoomDatabase,
) : BackupTransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T =
        database.withTransaction { block() }
}

/**
 * Exports alarms and user settings to JSON and restores them again.
 *
 * Import rules (P0 promises):
 * - alarm writes run inside a single transaction; any failure rolls the whole import back
 * - settings are written in one DataStore edit afterwards, and only when the alarm
 *   transaction committed; a settings write failure is reported in [ImportReport.details]
 *   (the imported alarms stay) instead of being thrown at the caller
 * - MERGE dedup key is `(hour, minute, repeatDaysMask, label)`: an existing alarm is kept,
 *   the backup row is skipped (never overwriting goalSteps/enabled)
 * - rows failing [AlarmConstraints] are skipped and counted, settings values outside
 *   [SettingsConstraints] fall back to defaults (same as [AppSettings.sanitized])
 * - `created_at`/`updated_at` are restored verbatim so export -> wipe -> import round-trips
 */
class BackupRepository(
    private val alarmDao: AlarmDao,
    private val settingsRepository: SettingsRepository,
    private val transactions: BackupTransactionRunner,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Serialises every stored alarm plus the current settings to backup JSON.
     * Storage IO errors propagate to the caller (the UI shows an error message).
     */
    suspend fun exportJson(): String {
        val alarms = alarmDao.observeAll().first()
        val settings = settingsRepository.currentSettings()
        return BackupCodec.encode(
            BackupData(
                schemaVersion = BackupCodec.SCHEMA_VERSION,
                exportedAt = clock(),
                alarms = alarms.map { it.toBackupAlarm() },
                settings = settings.toBackupSettings(),
            ),
        )
    }

    /**
     * Restores [json] according to [mode] and reports what happened.
     *
     * Never throws (short of cancellation): unreadable JSON or an unknown schema version
     * returns a failure report without touching any data, a failed alarm transaction is
     * rolled back before a failure report is returned, and a settings write failure after
     * a committed alarm import is reported in the details instead of thrown.
     */
    suspend fun importJson(json: String, mode: ImportMode): ImportReport {
        val decoded = BackupCodec.decode(json).getOrElse { error ->
            return ImportReport(
                mode = mode,
                imported = 0,
                skippedInvalid = 0,
                skippedDuplicates = 0,
                details = listOf("invalid backup file: ${error.message ?: "unreadable"}"),
            )
        }

        val alarmResult = try {
            transactions.inTransaction { importAlarms(decoded, mode) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ImportReport(
                mode = mode,
                imported = 0,
                skippedInvalid = 0,
                skippedDuplicates = 0,
                details = listOf("import failed, rolled back: ${e.message ?: e::class.simpleName}"),
            )
        }

        // Settings go in only after the alarm transaction committed; one DataStore edit
        // keeps the write atomic and leaves onboarding_completed untouched. A settings IO
        // failure is reported, never thrown: the alarm import has already committed.
        val details = try {
            settingsRepository.importSettings(decoded.settings.toAppSettings())
            alarmResult.details
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            alarmResult.details +
                "alarms imported, settings failed: ${e.message ?: e::class.simpleName}"
        }

        return ImportReport(
            mode = mode,
            imported = alarmResult.imported,
            skippedInvalid = alarmResult.skippedInvalid,
            skippedDuplicates = alarmResult.skippedDuplicates,
            details = details,
        )
    }

    private suspend fun importAlarms(decoded: BackupData, mode: ImportMode): AlarmCounts {
        val existing = if (mode == ImportMode.REPLACE) {
            alarmDao.deleteAll()
            emptyList()
        } else {
            alarmDao.getAll()
        }

        // Dedup key -> the kept alarm, so details can name the goalSteps that was kept.
        val keptByKey = LinkedHashMap<String, AlarmEntity>()
        existing.forEach { keptByKey[dedupKey(it)] = it }
        val idsInUse = existing.map { it.id }.toMutableSet()

        var imported = 0
        var skippedInvalid = 0
        var skippedDuplicates = 0
        val details = mutableListOf<String>()

        for (item in decoded.alarms) {
            val trimmedLabel = item.label.trim()
            val invalidReason = invalidReason(item, trimmedLabel)
            if (invalidReason != null) {
                skippedInvalid++
                details.add("skipped: invalid $invalidReason")
                continue
            }
            val key = dedupKey(item.hour, item.minute, item.repeatDaysMask, trimmedLabel)
            val kept = keptByKey[key]
            if (mode == ImportMode.MERGE && kept != null) {
                skippedDuplicates++
                details.add(
                    "skipped: duplicate ${item.hour}:${item.minute} '$trimmedLabel' " +
                        "goalSteps=${kept.goalSteps} kept existing",
                )
                continue
            }
            // Preserve the backup id when it is free so round-trips restore exactly;
            // colliding or unset ids are regenerated instead of aborting the import.
            val id = if (item.id > 0L && item.id !in idsInUse) item.id else 0L
            val row = AlarmEntity(
                id = id,
                hour = item.hour,
                minute = item.minute,
                repeatDaysMask = item.repeatDaysMask,
                label = trimmedLabel,
                goalSteps = item.goalSteps,
                enabled = item.enabled,
                createdAt = item.createdAt,
                updatedAt = item.updatedAt,
            )
            val newId = alarmDao.insert(row)
            idsInUse += newId
            keptByKey[key] = row.copy(id = newId)
            imported++
        }

        return AlarmCounts(imported, skippedInvalid, skippedDuplicates, details)
    }

    private data class AlarmCounts(
        val imported: Int,
        val skippedInvalid: Int,
        val skippedDuplicates: Int,
        val details: List<String>,
    )

    /** Returns a human-readable field name with the offending value, or null when valid. */
    private fun invalidReason(item: BackupAlarm, trimmedLabel: String): String? = when {
        item.hour !in AlarmConstraints.MIN_HOUR..AlarmConstraints.MAX_HOUR ->
            "hour=${item.hour}"
        item.minute !in AlarmConstraints.MIN_MINUTE..AlarmConstraints.MAX_MINUTE ->
            "minute=${item.minute}"
        !AlarmRepeatDays.isValidMask(item.repeatDaysMask) ->
            "repeat_days_mask=${item.repeatDaysMask}"
        trimmedLabel.length > AlarmConstraints.LABEL_MAX_LENGTH ->
            "label length=${trimmedLabel.length}"
        !SettingsConstraints.isValidGoalSteps(item.goalSteps) ->
            "goalSteps=${item.goalSteps}"
        item.createdAt < 0 || item.updatedAt < 0 ->
            "created_at=${item.createdAt} updated_at=${item.updatedAt}"
        else -> null
    }

    private fun dedupKey(entity: AlarmEntity): String =
        dedupKey(entity.hour, entity.minute, entity.repeatDaysMask, entity.label.trim())

    private fun dedupKey(hour: Int, minute: Int, repeatDaysMask: Int, label: String): String =
        "$hour:$minute:$repeatDaysMask:$label"

    private fun AlarmEntity.toBackupAlarm(): BackupAlarm = BackupAlarm(
        id = id,
        hour = hour,
        minute = minute,
        repeatDaysMask = repeatDaysMask,
        label = label,
        goalSteps = goalSteps,
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun AppSettings.toBackupSettings(): BackupSettings = BackupSettings(
        floatingBottomBarEnabled = floatingBottomBarEnabled,
        liquidGlassEnabled = liquidGlassEnabled,
        defaultGoalSteps = defaultGoalSteps,
        snoozeMinutes = snoozeMinutes,
        maxSnoozes = maxSnoozes,
        shakeCount = shakeCount,
    )

    private fun BackupSettings.toAppSettings(): AppSettings = AppSettings(
        floatingBottomBarEnabled = floatingBottomBarEnabled,
        liquidGlassEnabled = liquidGlassEnabled,
        defaultGoalSteps = defaultGoalSteps,
        snoozeMinutes = snoozeMinutes,
        maxSnoozes = maxSnoozes,
        shakeCount = shakeCount,
        onboardingCompleted = false,
    )
}
