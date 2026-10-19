package com.mustwake.alarm.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeCounterTest {

    @Test
    fun `spikes below the threshold are ignored`() {
        val counter = ShakeCounter(target = 3)

        assertEquals(0, counter.onMagnitude(magnitude = 9.8f, nowMs = 1_000L))
        assertFalse(counter.isArmed)
    }

    @Test
    fun `spikes above the threshold count`() {
        val counter = ShakeCounter(target = 3)

        assertEquals(1, counter.onMagnitude(magnitude = 15f, nowMs = 1_000L))
        assertEquals(2, counter.onMagnitude(magnitude = 20f, nowMs = 1_200L))
        assertEquals(3, counter.onMagnitude(magnitude = 14f, nowMs = 1_400L))
        assertTrue(counter.isArmed)
    }

    @Test
    fun `rapid repeats inside the debounce window are ignored`() {
        val counter = ShakeCounter(target = 5, minIntervalMs = 150L)

        assertEquals(1, counter.onMagnitude(magnitude = 15f, nowMs = 1_000L))
        assertEquals(1, counter.onMagnitude(magnitude = 15f, nowMs = 1_100L))
        assertEquals(2, counter.onMagnitude(magnitude = 15f, nowMs = 1_150L))
    }

    @Test
    fun `count saturates at the target`() {
        val counter = ShakeCounter(target = 2)

        counter.onMagnitude(15f, 1_000L)
        counter.onMagnitude(15f, 1_500L)
        assertEquals(2, counter.onMagnitude(15f, 2_000L))
        assertEquals(2, counter.onMagnitude(15f, 2_500L))
    }

    @Test
    fun `reset clears the count`() {
        val counter = ShakeCounter(target = 2)
        counter.onMagnitude(15f, 1_000L)

        counter.reset()

        assertEquals(0, counter.count)
        assertFalse(counter.isArmed)
    }
}
