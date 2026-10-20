plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

// Emulator/low-battery runs are only accepted with an explicit opt-in
// (-PbenchmarkAllowEmulator=true). The default stays strict so G0 real-device
// acceptance can never silently pick up emulator numbers.
val benchmarkAllowEmulator = providers.gradleProperty("benchmarkAllowEmulator").isPresent

android {
    namespace = "com.mustwake.alarm.baselineprofile"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        // 28, not the app's 26: baseline-profile collection needs API 28+.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (benchmarkAllowEmulator) {
            // Pipeline checks on the emulator only; real perf numbers need a physical device.
            testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] =
                "EMULATOR,LOW-BATTERY"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":androidApp"
}

baselineProfile {
    skipBenchmarksOnEmulator = !benchmarkAllowEmulator
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.test.junit)
}
