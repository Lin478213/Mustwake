package com.mustwake.alarm.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M1-A3 spike: minimal empty-scenario profile collector, only so
 * `generateBaselineProfile` has a `BaselineProfileRule` journey to collect.
 * Real journeys come in a later step.
 */
@RunWith(AndroidJUnit4::class)
class EmptyBaselineProfile {

    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun coldStart() =
        rule.collect(
            packageName = TARGET_PACKAGE,
            maxIterations = 3,
            stableIterations = 2,
        ) {
            pressHome()
            startActivityAndWait()
        }
}
