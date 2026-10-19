package com.mustwake.alarm.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepBaselineTrackerTest {

    @Test
    fun `first reading becomes the baseline and counts zero`() {
        val tracker = StepBaselineTracker()

        assertFalse(tracker.hasBaseline)
        assertEquals(0, tracker.stepsFrom(1_000f))
        assertTrue(tracker.hasBaseline)
    }

    @Test
    fun `steps are measured relative to the baseline`() {
        val tracker = StepBaselineTracker()
        tracker.stepsFrom(1_000f)

        assertEquals(12, tracker.stepsFrom(1_012f))
        assertEquals(50, tracker.stepsFrom(1_050f))
    }

    @Test
    fun `counter rollback never returns negative steps`() {
        val tracker = StepBaselineTracker()
        tracker.stepsFrom(1_000f)

        assertEquals(0, tracker.stepsFrom(900f))
    }

    @Test
    fun `reset takes a new baseline`() {
        val tracker = StepBaselineTracker()
        tracker.stepsFrom(1_000f)
        tracker.stepsFrom(1_040f)

        tracker.reset()

        assertEquals(0, tracker.stepsFrom(5_000f))
        assertEquals(7, tracker.stepsFrom(5_007f))
    }

    @Test
    fun `fractional counters truncate`() {
        val tracker = StepBaselineTracker()
        tracker.stepsFrom(100.9f)

        assertEquals(3, tracker.stepsFrom(104.4f))
    }
}
