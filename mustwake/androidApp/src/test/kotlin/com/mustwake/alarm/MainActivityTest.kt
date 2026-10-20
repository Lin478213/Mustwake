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
 * Smoke tests: the Miuix shell must compose through onboarding and both states of the single
 * liquid-glass toggle (plain shell / liquid shell).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

    @Test
    fun `main activity composes the alarm list`() {
        prepare(onboardingCompleted = true)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()

        assertNotNull(controller.get())
        controller.pause().stop().destroy()
    }

    @Test
    fun `first run shows the permission guide`() {
        prepare(onboardingCompleted = false)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()

        assertNotNull(controller.get())
        controller.pause().stop().destroy()
        prepare(onboardingCompleted = true)
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
    fun `plain shell composes with glass off`() {
        prepare(onboardingCompleted = true, glass = false)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()

        assertNotNull(controller.get())
        controller.pause().stop().destroy()
        prepare(onboardingCompleted = true)
    }

    @Test
    fun `liquid shell composes with glass on`() {
        prepare(onboardingCompleted = true, glass = true)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()

        assertNotNull(controller.get())
        controller.pause().stop().destroy()
        prepare(onboardingCompleted = true)
    }

    private fun prepare(
        onboardingCompleted: Boolean,
        glass: Boolean = false,
    ) {
        val app = ApplicationProvider.getApplicationContext<MustWakeApp>()
        runBlocking {
            app.container.settingsRepository.setOnboardingCompleted(onboardingCompleted)
            app.container.settingsRepository.setLiquidGlassEnabled(glass)
        }
    }
}
