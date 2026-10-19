package com.mustwake.alarm

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.mustwake.alarm.service.AlarmService
import java.util.Locale

/**
 * Full-screen ringing screen (T3: functional, not yet MIUIX — T5 re-skins it).
 *
 * Shows the alarm time, label, step goal and a stop button. The step progress ring / snooze /
 * long-press emergency stop arrive with T4/T5. Stops when the service broadcasts that the session
 * ended (e.g. stop from the notification action).
 */
class RingingActivity : Activity() {

    private var ringingStoppedReceiver: BroadcastReceiver? = null

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

        val alarmId = intent.getLongExtra(AlarmService.EXTRA_ALARM_ID, 0L)
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val hour = intent.getIntExtra(EXTRA_HOUR, 0)
        val minute = intent.getIntExtra(EXTRA_MINUTE, 0)
        val goalSteps = intent.getIntExtra(EXTRA_GOAL_STEPS, 0)

        setContentView(buildContent(alarmId, label, hour, minute, goalSteps))
    }

    override fun onStart() {
        super.onStart()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                finish()
            }
        }
        ringingStoppedReceiver = receiver
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(AlarmService.ACTION_RINGING_STOPPED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStop() {
        ringingStoppedReceiver?.let { runCatching { unregisterReceiver(it) } }
        ringingStoppedReceiver = null
        super.onStop()
    }

    private fun buildContent(
        alarmId: Long,
        label: String,
        hour: Int,
        minute: Int,
        goalSteps: Int,
    ): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(dp(32), dp(48), dp(32), dp(48))
        }

        val timeView = TextView(this).apply {
            text = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 72f)
            gravity = Gravity.CENTER
        }
        val statusView = TextView(this).apply {
            text = getString(R.string.ringing_title)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            gravity = Gravity.CENTER
        }
        val labelView = TextView(this).apply {
            text = label.ifBlank { getString(R.string.ringing_default_label) }
            setTextColor(Color.LTGRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
        }
        val goalView = TextView(this).apply {
            text = getString(R.string.ringing_goal, goalSteps)
            setTextColor(Color.LTGRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
        }
        val hintView = TextView(this).apply {
            text = getString(R.string.ringing_hint)
            setTextColor(Color.GRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
        }
        val stopButton = Button(this).apply {
            text = getString(R.string.ringing_stop)
            setOnClickListener {
                AlarmService.stop(this@RingingActivity, alarmId)
                finish()
            }
        }

        listOf(timeView, statusView, labelView, goalView, hintView).forEach { view ->
            root.addView(
                view,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(8) },
            )
        }
        root.addView(
            stopButton,
            LinearLayout.LayoutParams(dp(220), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(40)
                gravity = Gravity.CENTER_HORIZONTAL
            },
        )
        return root
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

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
