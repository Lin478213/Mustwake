package com.mustwake.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.mustwake.alarm.service.AlarmService
import com.mustwake.alarm.ui.design.MwTheme
import com.mustwake.alarm.ui.ringing.RingingScreen
import com.mustwake.alarm.ui.ringing.RingingUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Full-screen Miuix ringing screen.
 *
 * State is fed by the service's broadcasts; the stop button requires a 10 second continuous press
 * (emergency stop). The activity closes when the service reports the session ended.
 */
class RingingActivity : ComponentActivity() {

    private var alarmId = 0L
    private var ringingReceiver: BroadcastReceiver? = null
    private var state by mutableStateOf(RingingUiState())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )

        alarmId = intent.getLongExtra(AlarmService.EXTRA_ALARM_ID, 0L)
        state = state.copy(
            label = intent.getStringExtra(EXTRA_LABEL).orEmpty(),
            hour = intent.getIntExtra(EXTRA_HOUR, 0),
            minute = intent.getIntExtra(EXTRA_MINUTE, 0),
            goalSteps = intent.getIntExtra(EXTRA_GOAL_STEPS, 50),
        )

        if (alarmId > 0L) {
            scope.launch {
                val alarm = (application as? MustWakeApp)?.container?.alarmRepository?.getAlarm(alarmId)
                    ?: return@launch
                state = state.copy(
                    label = alarm.label,
                    hour = alarm.hour,
                    minute = alarm.minute,
                    goalSteps = alarm.goalSteps,
                )
            }
        }

        setContent {
            MwTheme {
                RingingScreen(
                    state = state,
                    onSnooze = { AlarmService.snooze(this, alarmId) },
                    onEmergency = { AlarmService.emergency(this, alarmId) },
                    onShortPress = {
                        Toast.makeText(this, R.string.ringing_emergency_short, Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    AlarmService.ACTION_RINGING_UPDATE -> applyState(intent)
                    AlarmService.ACTION_RINGING_STOPPED -> {
                        if (intent.getBooleanExtra(AlarmService.EXTRA_SNOOZED, false)) {
                            Toast.makeText(
                                this@RingingActivity,
                                R.string.ringing_snoozed,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        finish()
                    }
                }
            }
        }
        ringingReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(AlarmService.ACTION_RINGING_UPDATE)
            addAction(AlarmService.ACTION_RINGING_STOPPED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        AlarmService.requestState(this)
    }

    override fun onStop() {
        ringingReceiver?.let { runCatching { unregisterReceiver(it) } }
        ringingReceiver = null
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun applyState(intent: Intent) {
        state = state.copy(
            steps = intent.getIntExtra(AlarmService.EXTRA_STEPS, state.steps),
            goalSteps = intent.getIntExtra(AlarmService.EXTRA_GOAL, state.goalSteps),
            snoozeCount = intent.getIntExtra(AlarmService.EXTRA_SNOOZE_COUNT, state.snoozeCount),
            maxSnoozes = intent.getIntExtra(AlarmService.EXTRA_MAX_SNOOZES, state.maxSnoozes),
            snoozeMinutes = intent.getIntExtra(AlarmService.EXTRA_SNOOZE_MINUTES, state.snoozeMinutes),
            degradedMode = intent.getBooleanExtra(AlarmService.EXTRA_DEGRADED, state.degradedMode),
            shakeCount = intent.getIntExtra(AlarmService.EXTRA_SHAKE_COUNT, state.shakeCount),
            shakeTarget = intent.getIntExtra(AlarmService.EXTRA_SHAKE_TARGET, state.shakeTarget),
        )
    }

    companion object {
        private const val EXTRA_LABEL = "com.mustwake.alarm.extra.LABEL"
        private const val EXTRA_HOUR = "com.mustwake.alarm.extra.HOUR"
        private const val EXTRA_MINUTE = "com.mustwake.alarm.extra.MINUTE"
        private const val EXTRA_GOAL_STEPS = "com.mustwake.alarm.extra.GOAL_STEPS"

        fun intent(
            context: Context,
            alarmId: Long,
            label: String,
            hour: Int,
            minute: Int,
            goalSteps: Int,
        ): Intent = Intent(context, RingingActivity::class.java)
            .putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_LABEL, label)
            .putExtra(EXTRA_HOUR, hour)
            .putExtra(EXTRA_MINUTE, minute)
            .putExtra(EXTRA_GOAL_STEPS, goalSteps)
    }
}
