package com.mustwake.alarm.data.stop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopPolicyTest {

    private fun input(
        steps: Int = 0,
        goal: Int = 50,
        emergency: Boolean = false,
        degraded: Boolean = false,
        shakes: Int = 0,
        shakeTarget: Int = 30,
        snoozes: Int = 0,
        maxSnoozes: Int = 3,
    ) = StopPolicyInput(
        currentSteps = steps,
        goalSteps = goal,
        emergencyRequested = emergency,
        degradedMode = degraded,
        shakeCount = shakes,
        shakeTarget = shakeTarget,
        snoozeCount = snoozes,
        maxSnoozes = maxSnoozes,
    )

    private fun reasonOf(decision: StopDecision): StopReason? =
        (decision as? StopDecision.Stop)?.reason

    @Test
    fun `below the goal keeps ringing`() {
        assertEquals(StopDecision.Continue, StopPolicy.evaluate(input(steps = 49)))
    }

    @Test
    fun `reaching the goal stops as completed`() {
        assertEquals(StopReason.STEP_GOAL, reasonOf(StopPolicy.evaluate(input(steps = 50))))
    }

    @Test
    fun `overshooting the goal still stops as completed`() {
        assertEquals(StopReason.STEP_GOAL, reasonOf(StopPolicy.evaluate(input(steps = 73))))
    }

    @Test
    fun `goal boundary is inclusive`() {
        assertEquals(
            StopDecision.Continue,
            StopPolicy.evaluate(input(steps = 9, goal = 10)),
        )
        assertEquals(StopReason.STEP_GOAL, reasonOf(StopPolicy.evaluate(input(steps = 10, goal = 10))))
    }

    @Test
    fun `emergency long press stops in normal mode`() {
        assertEquals(
            StopReason.EMERGENCY,
            reasonOf(StopPolicy.evaluate(input(steps = 3, emergency = true))),
        )
    }

    @Test
    fun `goal completion wins over emergency`() {
        assertEquals(
            StopReason.STEP_GOAL,
            reasonOf(StopPolicy.evaluate(input(steps = 50, emergency = true))),
        )
    }

    @Test
    fun `degraded mode ignores emergency before enough shakes`() {
        val decision = StopPolicy.evaluate(
            input(steps = 0, emergency = true, degraded = true, shakes = 29),
        )
        assertEquals(StopDecision.Continue, decision)
    }

    @Test
    fun `degraded mode stops as degraded after enough shakes`() {
        assertEquals(
            StopReason.DEGRADED,
            reasonOf(
                StopPolicy.evaluate(input(emergency = true, degraded = true, shakes = 30)),
            ),
        )
    }

    @Test
    fun `degraded shakes alone do not stop without confirmation`() {
        assertEquals(
            StopDecision.Continue,
            StopPolicy.evaluate(input(steps = 4, degraded = true, shakes = 40)),
        )
    }

    @Test
    fun `degraded mode still completes on steps if a sensor recovers`() {
        assertEquals(
            StopReason.STEP_GOAL,
            reasonOf(StopPolicy.evaluate(input(steps = 50, degraded = true, shakes = 0))),
        )
    }

    @Test
    fun `remaining steps never goes negative`() {
        assertEquals(30, StopPolicy.remainingSteps(20, 50))
        assertEquals(0, StopPolicy.remainingSteps(60, 50))
    }

    @Test
    fun `snooze allowed while below the limit`() {
        assertTrue(StopPolicy.canSnooze(snoozeCount = 0, maxSnoozes = 3))
        assertTrue(StopPolicy.canSnooze(snoozeCount = 2, maxSnoozes = 3))
        assertFalse(StopPolicy.canSnooze(snoozeCount = 3, maxSnoozes = 3))
    }

    @Test
    fun `zero max snoozes disables snooze`() {
        assertFalse(StopPolicy.canSnooze(snoozeCount = 0, maxSnoozes = 0))
    }

    @Test
    fun `invalid input is rejected`() {
        val failures = listOf<() -> Unit>(
            { StopPolicy.evaluate(input(goal = 0)) },
            { StopPolicy.evaluate(input(steps = -1)) },
            { StopPolicy.evaluate(input(shakes = -1)) },
            { StopPolicy.evaluate(input(shakeTarget = 0)) },
            { StopPolicy.evaluate(input(snoozes = -1)) },
            { StopPolicy.evaluate(input(maxSnoozes = -1)) },
        )

        failures.forEach { call ->
            val error = runCatching(call).exceptionOrNull()
            assertTrue("expected IllegalArgumentException but was $error", error is IllegalArgumentException)
        }
    }
}
