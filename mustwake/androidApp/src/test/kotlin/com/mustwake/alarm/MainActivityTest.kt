package com.mustwake.alarm

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke tests: the Miuix shell must compose with every combination of the two independent
 * appearance toggles (floating bottom bar / liquid glass).
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

    @Test
    fun `floating bar composes without glass`() {
        setToggles(floating = true, glass = false)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()

        assertNotNull(controller.get())
        controller.pause().stop().destroy()
        setToggles(floating = false, glass = false)
    }

    @Test
    fun `glass bar composes while floating is off`() {
        setToggles(floating = false, glass = true)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()

        assertNotNull(controller.get())
        controller.pause().stop().destroy()
        setToggles(floating = false, glass = false)
    }

    @Test
    fun `both toggles compose together`() {
        setToggles(floating = true, glass = true)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()

        assertNotNull(controller.get())
        controller.pause().stop().destroy()
        setToggles(floating = false, glass = false)
    }

    private fun setToggles(floating: Boolean, glass: Boolean) {
        val app = ApplicationProvider.getApplicationContext<MustWakeApp>()
        runBlocking {
            app.container.settingsRepository.setFloatingBottomBarEnabled(floating)
            app.container.settingsRepository.setLiquidGlassEnabled(glass)
        }
    }
}
