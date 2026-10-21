package com.mustwake.alarm.ui

import android.app.Application
import android.content.ComponentName
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyTaskType
import com.mustwake.alarm.App
import com.mustwake.alarm.MustWakeApp
import com.mustwake.alarm.data.alarm.AlarmRepeatDays
import com.mustwake.alarm.data.db.AlarmEntity
import com.mustwake.alarm.data.db.AlarmEventEntity
import com.mustwake.alarm.data.db.AlarmEventType
import com.mustwake.alarm.ui.alarms.AlarmEditScreen
import com.mustwake.alarm.ui.alarms.AlarmListScreen
import com.mustwake.alarm.ui.design.LocalMwGlassEnabled
import com.mustwake.alarm.ui.design.MwSquircleFallback
import com.mustwake.alarm.ui.design.MwTheme
import com.mustwake.alarm.ui.design.MwThemeBackground
import com.mustwake.alarm.ui.history.HistoryScreen
import com.mustwake.alarm.ui.ringing.RingingScreen
import com.mustwake.alarm.ui.ringing.RingingUiState
import com.mustwake.alarm.ui.settings.SettingsScreen
import java.time.Instant
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi screenshot baselines for the Miuix UI.
 *
 * Determinism knobs:
 * - device: Pixel 5 qualifiers (fixed size + density);
 * - fontScale: pinned to 1f via [LocalDensity] (and the Robolectric font scale);
 * - timezone: `Asia/Shanghai`;
 * - "now": [FIXED_NOW] through [AlarmListScreen]'s injectable clock;
 * - history timestamps: inserted directly with fixed epochs (not `System.currentTimeMillis`).
 *
 * Light/dark follow [MwTheme] (`isSystemInDarkTheme`), driven by an overridden
 * [LocalConfiguration] `uiMode` so the two groups never depend on host settings.
 *
 * Glass/blur shots use `changeThreshold = 0.01f` because the backdrop blur is not bit-stable
 * across graphics stacks. Squircle is on Miuix's rounded-rect fallback under Robolectric
 * (see [MwSquircleFallback]); layout/text/colors — what M1-A4 regression needs — are real.
 *
 * Each test is a no-op unless a Roborazzi record/verify/compare task set its flags, so a plain
 * `gradlew test` stays fast; composition smoke lives in [com.mustwake.alarm.MainActivityTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTests {

    private val composeRule = createComposeRule()

    /**
     * Robolectric only resolves activities declared in the manifest; `createComposeRule()`'s host
     * [ComponentActivity] is not. Register it on the shadow PackageManager before the compose rule
     * launches, instead of polluting the production manifest.
     */
    private val hostActivityRegistration = TestRule { base, _: Description ->
        object : Statement() {
            override fun evaluate() {
                val app = ApplicationProvider.getApplicationContext<Application>()
                Shadows.shadowOf(app.packageManager)
                    .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
                base.evaluate()
            }
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivityRegistration).around(composeRule)

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
        RuntimeEnvironment.setFontScale(1f)
        // Room's process-wide singleton and the DataStore singleton outlive each test method;
        // without this, "empty" baselines would see the previous test's rows/toggles.
        runBlocking {
            val container = container()
            withContext(Dispatchers.IO) { container.database.clearAllTables() }
            val settings = container.settingsRepository
            settings.setOnboardingCompleted(true)
            settings.setLiquidGlassEnabled(false)
        }
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(null)
    }

    // ---------- alarms list ----------

    @Test
    fun list_empty_light() {
        if (!roborazziActive()) return
        captureScreen("list_empty_light", waitText = "还没有闹钟") {
            AlarmListScreen(
                container = container(),
                actions = NOOP_ACTIONS,
                onEdit = {},
                clock = { FIXED_NOW },
            )
        }
    }

    @Test
    fun list_populated_light() {
        if (!roborazziActive()) return
        seedAlarms()
        captureScreen("list_populated_light", waitText = "晨跑") {
            AlarmListScreen(
                container = container(),
                actions = NOOP_ACTIONS,
                onEdit = {},
                clock = { FIXED_NOW },
            )
        }
    }

    @Test
    fun list_populated_dark() {
        if (!roborazziActive()) return
        seedAlarms()
        captureScreen("list_populated_dark", dark = true, waitText = "晨跑") {
            AlarmListScreen(
                container = container(),
                actions = NOOP_ACTIONS,
                onEdit = {},
                clock = { FIXED_NOW },
            )
        }
    }

    // ---------- editor ----------

    @Test
    fun edit_light() {
        if (!roborazziActive()) return
        captureScreen("edit_light", waitText = "新建闹钟") {
            AlarmEditScreen(
                container = container(),
                actions = NOOP_ACTIONS,
                alarmId = null,
                onClose = {},
            )
        }
    }

    // ---------- settings ----------

    @Test
    fun settings_light() {
        if (!roborazziActive()) return
        captureScreen("settings_light", waitText = "液态玻璃") {
            SettingsScreen(container = container())
        }
    }

    @Test
    fun settings_dark() {
        if (!roborazziActive()) return
        captureScreen("settings_dark", dark = true, waitText = "液态玻璃") {
            SettingsScreen(container = container())
        }
    }

    // ---------- history ----------

    @Test
    fun history_empty_light() {
        if (!roborazziActive()) return
        captureScreen("history_empty_light", waitText = "还没有记录") {
            HistoryScreen(container = container())
        }
    }

    @Test
    fun history_light() {
        if (!roborazziActive()) return
        seedHistory()
        captureScreen("history_light", waitText = "完成") {
            HistoryScreen(container = container())
        }
    }

    // ---------- ringing ----------

    @Test
    fun ringing_normal_light() {
        if (!roborazziActive()) return
        captureScreen("ringing_normal_light", waitText = "已走") {
            RingingScreen(state = NORMAL_RINGING, onSnooze = {}, onEmergency = {}, onShortPress = {})
        }
    }

    @Test
    fun ringing_degraded_light() {
        if (!roborazziActive()) return
        captureScreen("ringing_degraded_light", waitText = "摇晃") {
            RingingScreen(
                state = DEGRADED_RINGING,
                onSnooze = {},
                onEmergency = {},
                onShortPress = {},
            )
        }
    }

    @Test
    fun ringing_normal_dark() {
        if (!roborazziActive()) return
        captureScreen("ringing_normal_dark", dark = true, waitText = "已走") {
            RingingScreen(state = NORMAL_RINGING, onSnooze = {}, onEmergency = {}, onShortPress = {})
        }
    }

    // ---------- shell: liquid glass toggle ----------

    @Test
    fun shell_solid_light() {
        if (!roborazziActive()) return
        prepareShell(glass = false)
        captureScreen("shell_solid_light", waitText = "闹钟") {
            App(container = container(), actions = NOOP_ACTIONS)
        }
    }

    @Test
    fun shell_liquid_light() {
        if (!roborazziActive()) return
        prepareShell(glass = true)
        captureScreen("shell_liquid_light", changeThreshold = GLASS_CHANGE_THRESHOLD, waitText = "闹钟") {
            App(container = container(), actions = NOOP_ACTIONS)
        }
    }

    // ---------- liquid components (switch / slider row / buttons) ----------

    @Test
    fun settings_liquid_light() {
        if (!roborazziActive()) return
        runBlocking { container().settingsRepository.setOnboardingCompleted(true) }
        captureScreen(
            "settings_liquid_light",
            changeThreshold = GLASS_CHANGE_THRESHOLD,
            waitText = "液态玻璃",
        ) {
            CompositionLocalProvider(LocalMwGlassEnabled provides true) {
                SettingsScreen(container = container())
            }
        }
    }

    /**
     * Glass dialog baseline through the real [App] hosting path: the dialog is only reachable as
     * the REPLACE-import confirmation on the settings tab, which is exactly where `MwDialogHost`
     * renders it outside the captured layer (so the panel refracts the screen beneath).
     *
     * Waits on "确认替换" (dialog-only) rather than the "替换导入" title/button, which exists
     * both behind the overlay and in the button that opens it. No extra test dependencies: the
     * Backdrop API never crosses the test's compile surface (route (a) in HANDOFF-LIQUID-GLASS).
     */
    @Test
    fun dialog_liquid_light() {
        if (!roborazziActive()) return
        prepareShell(glass = true)
        captureScreen(
            "dialog_liquid_light",
            changeThreshold = GLASS_CHANGE_THRESHOLD,
            waitText = "闹钟",
            interact = {
                composeRule.onNodeWithText("设置").performClick()
                composeRule.waitUntil(WAIT_TIMEOUT_MS) {
                    composeRule.onAllNodesWithText("替换导入").fetchSemanticsNodes().isNotEmpty()
                }
                // Scrolled into view, the button lands on the bottom edge — under the floating
                // glass bar, so a synthetic touch would hit the bar instead. Invoke the click
                // semantics action directly; hit-testing is emulator-verification territory.
                composeRule.onNodeWithText("替换导入")
                    .performScrollTo()
                    .performSemanticsAction(SemanticsActions.OnClick) { it.invoke() }
                composeRule.waitUntil(WAIT_TIMEOUT_MS) {
                    composeRule.onAllNodesWithText("确认替换").fetchSemanticsNodes().isNotEmpty()
                }
            },
        ) {
            App(container = container(), actions = NOOP_ACTIONS)
        }
    }

    // ---------- helpers ----------

    private fun container() = ApplicationProvider.getApplicationContext<MustWakeApp>().container

    private fun seedAlarms() {
        runBlocking {
            val repo = container().alarmRepository
            repo.save(
                AlarmEntity(
                    hour = 7,
                    minute = 30,
                    label = "晨跑",
                    goalSteps = 80,
                    repeatDaysMask = AlarmRepeatDays.EVERY_DAY,
                    enabled = true,
                ),
            )
            repo.save(
                AlarmEntity(
                    hour = 12,
                    minute = 30,
                    label = "午休散步",
                    goalSteps = 60,
                    repeatDaysMask = AlarmRepeatDays.MONDAY or
                        AlarmRepeatDays.TUESDAY or
                        AlarmRepeatDays.WEDNESDAY or
                        AlarmRepeatDays.THURSDAY or
                        AlarmRepeatDays.FRIDAY,
                    enabled = true,
                ),
            )
            repo.save(
                AlarmEntity(
                    hour = 22,
                    minute = 0,
                    label = "",
                    goalSteps = 100,
                    repeatDaysMask = AlarmRepeatDays.NONE,
                    enabled = false,
                ),
            )
        }
    }

    /**
     * History rows are written with fixed epochs so the `MM-dd HH:mm` caption cannot drift
     * between the record and a later verify run.
     */
    private fun seedHistory() {
        runBlocking {
            val alarmId = container().alarmRepository.save(
                AlarmEntity(
                    hour = 7,
                    minute = 30,
                    label = "晨跑",
                    goalSteps = 80,
                    repeatDaysMask = AlarmRepeatDays.EVERY_DAY,
                    enabled = true,
                ),
            )
            val dao = container().database.alarmEventDao()
            dao.insert(
                AlarmEventEntity(
                    alarmId = alarmId,
                    type = AlarmEventType.COMPLETED,
                    steps = 80,
                    timestamp = Instant.parse("2026-10-17T23:35:00Z").toEpochMilli(),
                ),
            )
            dao.insert(
                AlarmEventEntity(
                    alarmId = alarmId,
                    type = AlarmEventType.COMPLETED,
                    steps = 80,
                    timestamp = Instant.parse("2026-10-16T23:42:00Z").toEpochMilli(),
                ),
            )
            dao.insert(
                AlarmEventEntity(
                    alarmId = alarmId,
                    type = AlarmEventType.DEGRADED,
                    steps = 0,
                    timestamp = Instant.parse("2026-10-15T23:38:00Z").toEpochMilli(),
                    reason = "计步传感器不可用，摇晃 30 次停止",
                ),
            )
            dao.insert(
                AlarmEventEntity(
                    alarmId = alarmId,
                    type = AlarmEventType.MISSED,
                    steps = 0,
                    timestamp = Instant.parse("2026-10-14T23:30:00Z").toEpochMilli(),
                ),
            )
        }
    }

    /**
     * Shell shots go through [App], whose [AlarmListScreen] keeps the production `Instant.now()`
     * clock. Only disabled alarms are seeded so the "下次 …" line — the one wall-clock-dependent
     * string — never appears in these baselines.
     */
    private fun prepareShell(glass: Boolean) {
        runBlocking {
            val settings = container().settingsRepository
            settings.setOnboardingCompleted(true)
            settings.setLiquidGlassEnabled(glass)
            container().alarmRepository.save(
                AlarmEntity(
                    hour = 7,
                    minute = 30,
                    label = "晨跑",
                    goalSteps = 80,
                    repeatDaysMask = AlarmRepeatDays.EVERY_DAY,
                    enabled = false,
                ),
            )
            container().alarmRepository.save(
                AlarmEntity(
                    hour = 22,
                    minute = 0,
                    label = "睡前拉伸",
                    goalSteps = 60,
                    repeatDaysMask = AlarmRepeatDays.NONE,
                    enabled = false,
                ),
            )
        }
    }

    /**
     * Sets [content] into the compose tree, waits for [waitText] (so Room/DataStore emissions have
     * landed), runs [interact] (e.g. opening a dialog) if given, then writes the golden PNG under
     * `src/test/screenshots/`.
     */
    private fun captureScreen(
        name: String,
        dark: Boolean = false,
        changeThreshold: Float = 0f,
        waitText: String,
        interact: (() -> Unit)? = null,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            FixedEnvironment(dark = dark) {
                // Robolectric NATIVE cannot compile Miuix's squircle AGSL; use the lib fallback.
                MwSquircleFallback {
                    MwTheme {
                        MwThemeBackground(content = content)
                    }
                }
            }
        }
        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeRule
                .onAllNodesWithText(waitText, substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        interact?.invoke()
        composeRule.onRoot().captureRoboImage(
            filePath = "$SCREENSHOT_DIR/$name.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = changeThreshold),
            ),
        )
    }

    companion object {
        /** 2026-10-19 07:20 Asia/Shanghai (Monday), so "下次 今天 …" is stable. */
        val FIXED_NOW: Instant = Instant.parse("2026-10-18T23:20:00Z")

        const val SCREENSHOT_DIR = "src/test/screenshots"

        /** Blur is not bit-stable; allow 1% pixel drift on glass shots before failing. */
        const val GLASS_CHANGE_THRESHOLD = 0.01f

        private const val WAIT_TIMEOUT_MS = 15_000L

        private val NOOP_ACTIONS = AlarmUiActions(schedule = {}, cancel = {})

        private val NORMAL_RINGING = RingingUiState(
            label = "晨跑",
            hour = 7,
            minute = 30,
            steps = 32,
            goalSteps = 80,
            snoozeCount = 1,
            maxSnoozes = 3,
            snoozeMinutes = 5,
            degradedMode = false,
        )

        private val DEGRADED_RINGING = RingingUiState(
            label = "晨跑",
            hour = 7,
            minute = 30,
            steps = 0,
            goalSteps = 80,
            snoozeCount = 0,
            maxSnoozes = 3,
            snoozeMinutes = 5,
            degradedMode = true,
            shakeCount = 12,
            shakeTarget = 30,
        )

        /** Mirrors the flags the Roborazzi Gradle plugin puts on record/verify/compare runs. */
        @OptIn(ExperimentalRoborazziApi::class)
        private fun roborazziActive(): Boolean = roborazziSystemPropertyTaskType().isEnabled()
    }
}

/**
 * Pins fontScale to 1f and optionally forces the dark theme the way [MwTheme] reads it
 * (`isSystemInDarkTheme` → `LocalConfiguration.uiMode`).
 */
@Composable
private fun FixedEnvironment(dark: Boolean, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val themedConfiguration = Configuration(configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    }
    CompositionLocalProvider(
        LocalDensity provides Density(density = density.density, fontScale = 1f),
        LocalConfiguration provides themedConfiguration,
    ) {
        val nightYes =
            themedConfiguration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        check(nightYes == dark) { "theme override did not take effect" }
        content()
    }
}
