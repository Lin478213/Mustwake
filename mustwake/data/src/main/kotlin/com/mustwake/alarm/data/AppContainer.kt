package com.mustwake.alarm.data

import android.content.Context
import com.mustwake.alarm.data.alarm.AlarmRepository
import com.mustwake.alarm.data.backup.BackupRepository
import com.mustwake.alarm.data.backup.RoomBackupTransactionRunner
import com.mustwake.alarm.data.db.MustWakeDatabase
import com.mustwake.alarm.data.history.HistoryRepository
import com.mustwake.alarm.data.session.SessionRepository
import com.mustwake.alarm.data.settings.SettingsRepository
import com.mustwake.alarm.data.settings.mustWakeSettingsDataStore

/**
 * Minimal manual dependency container.
 *
 * Kept deliberately small (no DI framework) so the data layer can be constructed from an
 * Application class in T2 without introducing another dependency.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val database: MustWakeDatabase by lazy { MustWakeDatabase.get(appContext) }

    val alarmRepository: AlarmRepository by lazy { AlarmRepository(database.alarmDao()) }

    val historyRepository: HistoryRepository by lazy { HistoryRepository(database.alarmEventDao()) }

    val sessionRepository: SessionRepository by lazy {
        SessionRepository(database.alarmSessionDao())
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(appContext.mustWakeSettingsDataStore)
    }

    val backupRepository: BackupRepository by lazy {
        BackupRepository(
            alarmDao = database.alarmDao(),
            settingsRepository = settingsRepository,
            transactions = RoomBackupTransactionRunner(database),
        )
    }
}
