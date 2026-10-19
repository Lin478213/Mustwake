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
    fun `ringing notification is ongoing with a stop action`() {
        val notification = notifications.buildRinging(
            alarmId = 1L,
            label = "Wake up",
            hour = 7,
            minute = 30,
            goalSteps = 50,
            showFullScreen = true,
        )

        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(Notification.CATEGORY_ALARM, notification.category)
        assertEquals(1, notification.actions.size)
        assertNotNull(notification.fullScreenIntent)
        assertNotNull(notification.contentIntent)
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

        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(text.contains("闹钟"))
    }
}
