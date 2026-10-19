package com.mustwake.alarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mustwake.alarm.alarm.AlarmScheduler
import com.mustwake.alarm.ui.AlarmUiActions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MustWakeApp).container
        val scheduler = AlarmScheduler(this, container.historyRepository)

        val actions = AlarmUiActions(
            schedule = { alarm -> scheduler.schedule(alarm, source = "ui") },
            cancel = { alarmId -> scheduler.cancel(alarmId) },
        )

        setContent {
            App(container = container, actions = actions)
        }
    }
}
