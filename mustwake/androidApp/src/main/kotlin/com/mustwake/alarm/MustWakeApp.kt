package com.mustwake.alarm

import android.app.Application
import android.util.Log
import com.mustwake.alarm.alarm.AlarmMaintenance
import com.mustwake.alarm.data.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Holds the single [AppContainer] for receivers, services and the UI. */
class MustWakeApp : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // App-start sweep: a session that died while the process was gone must not stay
        // "ringing" forever (spec E: history must never be stuck in the ringing state).
        applicationScope.launch {
            try {
                AlarmMaintenance.sweepExpiredSessions(container, source = "app-start")
            } catch (throwable: Throwable) {
                Log.e("MustWake", "app-start session sweep failed", throwable)
            }
        }
    }
}
