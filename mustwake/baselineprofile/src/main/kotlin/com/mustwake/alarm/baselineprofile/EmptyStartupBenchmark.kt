package com.mustwake.alarm.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M1-A3 spike: minimal empty-scenario macrobenchmark, only to prove the
 * `:baselineprofile` + `androidx.benchmark.macro` pipeline works with AGP 9.3.2.
 * Real scenarios (MainActivityStartupBenchmark / RingingActivityStartupBenchmark)
 * come in a later step.
 */
@RunWith(AndroidJUnit4::class)
class EmptyStartupBenchmark {

    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStart() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.DEFAULT,
            startupMode = StartupMode.COLD,
            iterations = 1,
        ) {
            pressHome()
            startActivityAndWait()
        }
}
