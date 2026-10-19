package com.mustwake.alarm

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.mustwake.alarm.data.stop.StopPolicy
import com.mustwake.alarm.service.AlarmService
import java.util.Locale

/**
 * Full-screen ringing screen.
 *
 * T4 behaviour: live step progress (or shake progress in degraded mode), snooze button enabled
 * while snoozes remain, and a stop button that only fires after a 10 second continuous press
 * (emergency stop). MIUIX styling and the progress ring are T5.
 */
class RingingActivity : Activity() {

    private var alarmId = 0L
    private var ringingReceiver: BroadcastReceiver? = null
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var statusView: TextView
    private lateinit var labelView: TextView
    private lateinit var stepsView: TextView
    private lateinit var remainingView: TextView
    private lateinit var hintView: TextView
    private lateinit var snoozeButton: Button
    private lateinit var stopButton: Button

    private var emergencyTriggered = false
    private var emergencyRunnable: Runnable? = null
    private var pressStartedAt = 0L

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
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val hour = intent.getIntExtra(EXTRA_HOUR, 0)
        val minute = intent.getIntExtra(EXTRA_MINUTE, 0)
        val goalSteps = intent.getIntExtra(EXTRA_GOAL_STEPS, 0)

        setContentView(buildContent(label, hour, minute, goalSteps))
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
        emergencyRunnable?.let(handler::removeCallbacks)
        super.onDestroy()
    }

    private fun buildContent(label: String, hour: Int, minute: Int, goalSteps: Int): View {
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
        statusView = TextView(this).apply {
            text = getString(R.string.ringing_title)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
        }
        labelView = TextView(this).apply {
            text = label.ifBlank { getString(R.string.ringing_default_label) }
            setTextColor(Color.LTGRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
        }
        stepsView = TextView(this).apply {
            text = getString(R.string.ringing_steps, 0, goalSteps)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            gravity = Gravity.CENTER
        }
        remainingView = TextView(this).apply {
            text = getString(R.string.ringing_remaining, goalSteps)
            setTextColor(Color.LTGRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
        }
        hintView = TextView(this).apply {
            text = getString(R.string.ringing_emergency_hint)
            setTextColor(Color.GRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
        }

        snoozeButton = Button(this).apply {
            text = getString(R.string.ringing_snooze, 5)
            isEnabled = false
            setOnClickListener {
                AlarmService.snooze(this@RingingActivity, alarmId)
            }
        }
        stopButton = Button(this).apply {
            text = getString(R.string.ringing_stop)
            setOnTouchListener { view, event -> handleStopTouch(view, event) }
        }

        listOf(timeView, statusView, labelView, stepsView, remainingView, hintView).forEach { view ->
            root.addView(
                view,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(8) },
            )
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        buttonRow.addView(
            snoozeButton,
            LinearLayout.LayoutParams(dp(150), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(12)
            },
        )
        buttonRow.addView(
            stopButton,
            LinearLayout.LayoutParams(dp(150), LinearLayout.LayoutParams.WRAP_CONTENT),
        )
        root.addView(
            buttonRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(40) },
        )
        return root
    }

    private fun handleStopTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (emergencyTriggered) return true
                pressStartedAt = SystemClock.elapsedRealtime()
                stopButton.text = getString(R.string.ringing_emergency_hint)
                val runnable = Runnable {
                    if (emergencyTriggered) return@Runnable
                    emergencyTriggered = true
                    stopButton.isEnabled = false
                    stopButton.text = getString(R.string.ringing_emergency_hint)
                    view.performHapticFeedback(
                        android.view.HapticFeedbackConstants.LONG_PRESS,
                    )
                    AlarmService.emergency(this@RingingActivity, alarmId)
                }
                emergencyRunnable = runnable
                handler.postDelayed(runnable, EMERGENCY_HOLD_MS)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                emergencyRunnable?.let(handler::removeCallbacks)
                emergencyRunnable = null
                if (!emergencyTriggered) {
                    stopButton.text = getString(R.string.ringing_stop)
                    if (SystemClock.elapsedRealtime() - pressStartedAt < EMERGENCY_HOLD_MS) {
                        Toast.makeText(
                            this,
                            R.string.ringing_emergency_short,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                return true
            }
        }
        return false
    }

    private fun applyState(intent: Intent) {
        val steps = intent.getIntExtra(AlarmService.EXTRA_STEPS, 0)
        val goal = intent.getIntExtra(AlarmService.EXTRA_GOAL, 0)
        val snoozeCount = intent.getIntExtra(AlarmService.EXTRA_SNOOZE_COUNT, 0)
        val maxSnoozes = intent.getIntExtra(AlarmService.EXTRA_MAX_SNOOZES, 0)
        val snoozeMinutes = intent.getIntExtra(AlarmService.EXTRA_SNOOZE_MINUTES, 5)
        val degraded = intent.getBooleanExtra(AlarmService.EXTRA_DEGRADED, false)
        val shakeCount = intent.getIntExtra(AlarmService.EXTRA_SHAKE_COUNT, 0)
        val shakeTarget = intent.getIntExtra(AlarmService.EXTRA_SHAKE_TARGET, 30)

        if (degraded) {
            stepsView.text = getString(R.string.ringing_degraded, shakeCount, shakeTarget)
            remainingView.visibility = View.GONE
        } else {
            stepsView.text = getString(R.string.ringing_steps, steps, goal)
            remainingView.text = getString(R.string.ringing_remaining, StopPolicy.remainingSteps(steps, goal))
            remainingView.visibility = View.VISIBLE
        }

        val canSnooze = StopPolicy.canSnooze(snoozeCount, maxSnoozes)
        snoozeButton.isEnabled = canSnooze && !emergencyTriggered
        snoozeButton.text = if (canSnooze) {
            getString(R.string.ringing_snooze, snoozeMinutes)
        } else {
            getString(R.string.ringing_snooze_disabled)
        }
        snoozeButton.contentDescription = snoozeButton.text
        statusView.contentDescription = getString(R.string.ringing_title)
        labelView.contentDescription = labelView.text
        stepsView.contentDescription = stepsView.text
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_LABEL = "com.mustwake.alarm.extra.LABEL"
        private const val EXTRA_HOUR = "com.mustwake.alarm.extra.HOUR"
        private const val EXTRA_MINUTE = "com.mustwake.alarm.extra.MINUTE"
        private const val EXTRA_GOAL_STEPS = "com.mustwake.alarm.extra.GOAL_STEPS"

        /** Long-press duration for the emergency stop. */
        const val EMERGENCY_HOLD_MS = 10_000L

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
