package com.mustwake.alarm.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M1-A3: baseline-profile collection for RingingActivity cold start
 * (explicit component, no extras — renders the default ringing state).
 */
@RunWith(AndroidJUnit4::class)
class RingingActivityBaselineProfile {

    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun coldStart() =
        rule.collect(
            packageName = TARGET_PACKAGE,
            maxIterations = 5,
            stableIterations = 3,
        ) {
            pressHome()
            startActivityAndWait {
                it.setClassName(packageName, "$packageName.RingingActivity")
            }
        }
}
