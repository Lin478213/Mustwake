package com.mustwake.alarm

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke test: the Miuix UI shell must compose without crashing, including the database-backed
 * screens (empty state) and the theme.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

    @Test
    fun `main activity composes the alarm list`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()

        assertNotNull(controller.get())
        controller.pause().stop().destroy()
    }

    @Test
    fun `ringing activity composes with alarm extras`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = RingingActivity.intent(context, alarmId = 1L, label = "晨跑", hour = 7, minute = 30, goalSteps = 50)
        val controller = Robolectric.buildActivity(RingingActivity::class.java, intent).setup()

        assertNotNull(controller.get())
        controller.pause().stop().destroy()
    }
}
