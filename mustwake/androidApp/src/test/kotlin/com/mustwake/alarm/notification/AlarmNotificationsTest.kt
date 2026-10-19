package com.mustwake.alarm.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmNotificationsTest {

    private lateinit var context: Context
    private lateinit var notifications: AlarmNotifications

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notifications = AlarmNotifications(context)
        notifications.ensureChannels()
    }

    @Test
    fun `ringing channel is high importance and silent`() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(AlarmNotifications.CHANNEL_RINGING)

        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
        assertEquals(Notification.VISIBILITY_PUBLIC, channel.lockscreenVisibility)
    }

    @Test
    fun `ringing notification is ongoing with a snooze action`() {
        val notification = notifications.buildRinging(
            alarmId = 1L,
            label = "Wake up",
            hour = 7,
            minute = 30,
            goalSteps = 50,
            steps = 12,
            snoozeCount = 0,
            maxSnoozes = 3,
            snoozeMinutes = 5,
            showFullScreen = true,
        )

        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(Notification.CATEGORY_ALARM, notification.category)
        assertEquals(1, notification.actions.size)
        assertNotNull(notification.fullScreenIntent)
        assertNotNull(notification.contentIntent)

        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(text.contains("12"))
        assertTrue(text.contains("50"))
    }

    @Test
    fun `no snooze action once the limit is reached`() {
        val notification = notifications.buildRinging(
            alarmId = 1L,
            label = "Wake up",
            hour = 7,
            minute = 30,
            goalSteps = 50,
            steps = 20,
            snoozeCount = 3,
            maxSnoozes = 3,
            showFullScreen = false,
        )

        assertEquals(0, notification.actions?.size ?: 0)
    }

    @Test
    fun `degraded mode shows shake progress`() {
        val notification = notifications.buildRinging(
            alarmId = 1L,
            label = "Wake up",
            hour = 7,
            minute = 30,
            goalSteps = 50,
            degradedMode = true,
            shakeCount = 12,
            shakeTarget = 30,
            showFullScreen = false,
        )

        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(text.contains("12"))
        assertTrue(text.contains("30"))
        assertTrue(text.contains("摇晃"))
    }

    @Test
    fun `full screen intent is omitted when not allowed`() {
        val notification = notifications.buildRinging(
            alarmId = 1L,
            label = "Wake up",
            hour = 7,
            minute = 30,
            goalSteps = 50,
            showFullScreen = false,
        )

        assertNull(notification.fullScreenIntent)
        // Heads-up path: the content intent must still open the ringing screen.
        assertNotNull(notification.contentIntent)
    }

    @Test
    fun `blank label falls back to the default label`() {
        val notification = notifications.buildRinging(
            alarmId = 2L,
            label = "  ",
            hour = 6,
            minute = 0,
            goalSteps = 30,
            showFullScreen = false,
        )

        val subText = notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString()
        assertTrue(subText.contains("闹钟"))
    }
}
