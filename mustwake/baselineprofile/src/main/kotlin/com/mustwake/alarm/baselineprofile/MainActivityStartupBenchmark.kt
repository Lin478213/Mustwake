package com.mustwake.alarm.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M1-A3: cold-start timing of MainActivity, before (no profile) and after
 * (baseline profile required) — the before/after pair for the emulator pre-check.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityStartupBenchmark {

    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartWithoutProfile() = measureColdStart(CompilationMode.None())

    @Test
    fun coldStartWithBaselineProfile() =
        measureColdStart(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    private fun measureColdStart(compilationMode: CompilationMode) =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 5,
        ) {
            pressHome()
            startActivityAndWait()
        }
}
