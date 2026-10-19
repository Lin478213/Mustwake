# MustWake Phase 2 执行计划（细化版）

> 状态：**pending approval（critic REVISE 已修订，待批准；未开始任何代码改动）**
> 生成：2026-10-19 | 基线：`1e3d270`，122 tests | 评审：critic REVISE（1 Blocker + 7 Major + 8 Minor，已全部合入，见文末修订记录）
> 关系：本文是 `.omc/plans/phase2.md`（路线图）的**执行细化**，任务 ID 与之一致（M1-A1…M4-1）；范围与决策以 `.omc/specs/deep-interview-phase2.md`、`HANDOFF-PHASE2-PLAN.md` 为准，冲突时先改文档再改代码。细节冲突以本计划为准并在执行时同步 roadmap。

---

## Requirements Summary

- **M1-A（现在可做，设备无关）**：备份/恢复 JSON、Roborazzi 截图基线、Baseline Profile、a11y+字符串基础设施、崩溃/ANR 本地可观测性。
- **G0（真机门禁）**：设备到位 → T8 基线。无设备则 M1-B 全部阻塞，**M1 不关闭、不宣称发布完成**。
- **M1-B（G0 后）**：试响 10 秒、跳过下一次（Room v2→v3）、英文完成、T8 真机 A–F、M1 关闭。
- **M2**：统计/streak、自适应目标、防作弊、渐进音量+每闹钟声音/振动、唤醒后小结（纯本地）、玻璃帧率基准。
- **M3**：小组件+QS、自动化接口、节假日/调休（最后可选）、T9 root（可选）。**M4 Wear OS 后置，不早于 M2。**

## 里程碑与门禁

```
M1-A(1..5) ──→ G0(真机) ──→ M1-B(1..5, M1 关闭) ──→ M2 ──→ M3 ──→ M4
     │                        ▲
     └── 可立即串行执行         └── 无真机证据不得通过
```

| 里程碑 | 出口验收 |
|---|---|
| M1-A | `.\gradlew.bat test` 全绿（122+ 新增）；`:androidApp:assembleDebug` 成功；截图/基准/导出脚本可用；**不宣称 M1** |
| G0 | `adb devices` 有设备；`docs/testing.md` 记录设备型号/系统/权限状态 + T8 基线 |
| M1-B | P0 全项 + T8 A–F 全 PASS（清单见 M1-B4，定义在 `HANDOFF.md` §7）；122+ tests；M1 关闭 |
| M2 | P1 全项单测/基准；统计/防作弊/曲线有测试；玻璃基准有 before/after |
| M3 | P2 验收；自动化权限安全（未授权被拒）；节假日默认关 |

---

## M1-A 实施步骤（现在开始）

### M1-A1 备份/恢复 JSON（第一个 PR）

**落点**：`:data` 新增 `backup/` 包；`shared/.../ui/settings/SettingsScreen.kt` 加导出/导入入口。

现状锚点：
- `AlarmEntity` 字段：`data/src/main/kotlin/com/mustwake/alarm/data/db/AlarmEntity.kt:16-30`（id/hour/minute/repeatDaysMask/label/goalSteps/enabled/createdAt/updatedAt）
- `AppSettings` 7 字段：`data/.../settings/AppSettings.kt:9-17`
- 写入校验入口：`data/.../settings/SettingsConstraints.kt`、`alarm/AlarmConstraints.kt`
- 仓库 API：`AlarmRepository.save/observeAlarms`（`data/.../alarm/AlarmRepository.kt:18/36`）、`SettingsRepository`（`data/.../settings/SettingsRepository.kt:58-99`）
- 事务能力：`AppContainer.kt:21` 暴露 `database`，可用 `withTransaction`；`docs/data-layer.md:139-140` 记载现状 multi-statement 写尚未包事务——**本任务必须补上导入事务**
- UI 落点：`SettingsScreen.kt:31`（`SettingsScreen(container, onOpenPermissions)`，`:128` 的 `MwStepperRow` 为同页组件先例）
- **现状无任何 JSON 库**（全仓 grep 无 serialization/org.json 依赖）——需选型（见下）。

步骤：
1. **依赖 spike**（独立小提交）：给 `:data` 加 kotlinx.serialization——**两件事都要**：① 编译器插件 `org.jetbrains.kotlin.plugin.serialization` 2.4.20（对齐 `gradle/libs.versions.toml:3`）；② 运行时工件 `kotlinx-serialization-json`（插件本身不含 JSON）。**风险**：`:data` 用 AGP 9 内置 Kotlin（无 kotlin-android 插件，`docs/toolchain.md:70-72`），编译器插件可能无法挂载；挂不上则**回退 org.json**（平台自带、零依赖，`BackupCodec` 公共接口不变）。回退时注意：org.json 在 JVM 单测是 android.jar stub，`BackupCodecTest` 用 Robolectric 跑（`:data` 已有 robolectric，`data/build.gradle.kts:39`）或补 `testImplementation("org.json:json")`。
2. `data/.../backup/BackupModels.kt` + `BackupCodec.kt`：JSON 结构 `{schemaVersion:1, exportedAt, alarms[], settings{}}`；**不导出** session/history（按 spec）。字段随 Room v3 演进的扩展义务见 M1-B2/M2-4。
3. `BackupRepository`：导出取 `AlarmRepository.observeAlarms().first()` + `SettingsRepository.currentSettings()`；导入逐项过 `AlarmConstraints`/`SettingsConstraints`，非法项跳过并计入报告。
4. **导入事务性（Blocker 修复）**：导入写库必须用 `withTransaction { ... }`（经 `AppContainer.database`）**单事务提交，任一失败全量回滚**——尤其替换模式禁止"先清空后逐条插入"的非原子路径；中断后数据必须与导入前完全一致。`AlarmRepository`/`SettingsRepository` 的读快照在事务内取用。
5. **合并去重语义（统一定义）**：合并模式去重键 = `(hour, minute, repeatDaysMask, label)`；同键时**已有闹钟保留、备份项跳过**（不覆盖 goalSteps/enabled，避免静默改用户数据），计入 `skippedDuplicates`。替换模式不做去重（清空后全量导入）。`ImportReport(mode, imported, skippedInvalid, skippedDuplicates)` 返回计数 + 逐项原因列表（供 UI 摘要显示）。*（本定义覆盖 phase2.md M1-A1 的"id/time 去重"措辞，执行首提交时同步改 roadmap 一行。）*
6. `SettingsScreen.kt` 加「备份与恢复」区：`ACTION_CREATE_DOCUMENT`/`ACTION_OPEN_DOCUMENT`（SAF），文件名 `mustwake-backup-YYYYMMDD.json`；**替换模式二次确认对话框**（文案须提示"将删除现有 N 个闹钟"，复用 MwCard/按钮组件，`MwComponents.kt`）。
7. 测试 `data/src/test/.../backup/BackupCodecTest.kt` + `BackupRepositoryTest.kt`（合计 ≥12 例）：
   - **换机恢复主路径**：导出 → **删除全部 alarms** → 导入 → 快照与导出前一致（spec P0-3 原文语义，必须测）
   - replace 模式用例（清空 + 导入）
   - **中断回滚**：构造事务中途抛异常 → `AlarmRepository` 快照与导入前一致
   - 空备份、坏 JSON、未知 schemaVersion、越界值（goalSteps 501/9）、重复项跳过（同键不同 goalSteps 保留已有）、`ImportReport` 各计数、settings 缺字段回默认

**验收（可测）**：
- 换机恢复：导出→清空→导入→快照一致（测试断言）；replace 模式同。
- 原子性：中断注入后数据与导入前一致（测试断言）。
- 坏 JSON/未知版本：返回失败结果，**不抛未捕获异常**（测试断言）。
- `.\gradlew.bat :data:testDebugUnitTest` 新增 ≥12 全过；`.\gradlew.bat test` 全绿。

### M1-A2 Roborazzi 截图基线

**落点**：`androidApp/src/test/`（Robolectric+Compose 冒烟已在此可跑：`MainActivityTest.kt:16-17` `@Config(sdk=[34])`、`:42-49` RingingActivity 组合冒烟——同一文件两组测试，仅证明可组合，像素渲染待首跑验证）。

现状锚点：
- 测试依赖：`androidApp/build.gradle.kts:50-53`（junit/robolectric/androidx.test.core/coroutines-test）
- `testOptions.unitTests.isIncludeAndroidResources = true` **已开**（`androidApp/build.gradle.kts:28-30`）
- 版本目录：`gradle/libs.versions.toml [plugins]` 现无 roborazzi/benchmark 条目（新增时 pin 精确版本）

步骤：
1. `libs.versions.toml` + `androidApp/build.gradle.kts` 加 Roborazzi（`io.github.takahirom.roborazzi`）。NATIVE 渲染用测试类注解 `@GraphicsMode(GraphicsMode.Mode.NATIVE)` 或 `systemProperty("roborazzi.graphicsMode", "NATIVE")`（**不是** `robolectric { graphicsMode = ... }` DSL）。
2. 新建 `androidApp/src/test/kotlin/com/mustwake/alarm/ui/ScreenshotTests.kt`：固定 fontScale=1f、浅/深色各一组、时区 `Asia/Shanghai`、固定"当前时间"——注意 `TimeText.kt` 是纯函数 `timeText(hour,minute)`，无时间注入点；**注入点在列表页"下次 X"的时间源**（`AlarmListScreen.kt` 的下次响铃刷新，抽一个可注入时钟）。
3. 覆盖 ≥10 张基线：空列表、有数据列表、编辑页、设置页、历史页、响铃页（正常/降级）、**底栏×玻璃 4 组合**（预置 DataStore 两开关，语义见 `docs/appearance.md:12`）。
4. 基线图提交进仓库（`androidApp/src/test/screenshots/`）；玻璃/模糊区裁剪出比较区或设 `compareOptions.changeThreshold = 0.01f` 降低脆弱。**首跑只做 2 张图**（列表/设置）验证 Roborazzi + KMP `:shared` Compose + Miuix 自绘/backdrop 在 Robolectric NATIVE 下不空白不崩溃，再扩全量。

**验收（可测）**：
- `.\gradlew.bat :androidApp:recordRoborazziDebug` 生成 ≥10 张基线并提交。
- `.\gradlew.bat :androidApp:verifyRoborazziDebug` 连续两次通过（无 flake）。
- 故意改一个组件颜色 → verify 失败能报出差异（人工验证一次）。

### M1-A3 Baseline Profile（Main / Ringing 冷启动）

**落点**：新模块 `:baselineprofile`；`androidApp` 挂 profile。

现状锚点：
- 模块清单：`settings.gradle.kts:25-27`（现 `:shared/:data/:androidApp`）
- 插件目录：`gradle/libs.versions.toml [plugins]`（现无 benchmark/baselineprofile——新增时 pin 与 AGP 9.3.2 兼容的版本）
- 目标场景：`androidApp/.../MainActivity.kt`、`RingingActivity.kt`（响铃页冷启动是可信度关键）

步骤：
1. **兼容性 spike**（独立小提交）：`:baselineprofile`/`androidx.benchmark.macro` 与 AGP 9.3.2 内置 Kotlin + KMP `:shared` 的组合先跑通空场景；任务名以 `.\gradlew.bat :baselineprofile:tasks` 实测为准（AGP 9 下可能是 `generateBaselineProfile` 或变体化任务名）。不通则此任务挂起标"待工具链升级"，不阻塞其余 M1-A。
2. `libs.versions.toml` 加 benchmark/baselineprofile 插件与 `androidx.profileinstaller`（pin 版本）；`settings.gradle.kts` `include(":baselineprofile")`。
3. 新模块 `:baselineprofile`（`com.android.test`，targetProjectPath `:androidApp`）；`androidApp/build.gradle.kts` 应用 `androidx.baselineprofile` 插件 + `baselineProfile(project(":baselineprofile"))` + `implementation(profileinstaller)`。
4. 场景类：`MainActivityStartupBenchmark`、`RingingActivityStartupBenchmark`（`StartupTimingMetric`，cold start，各 ≥3 次迭代）。
5. **生成并落盘**：跑生成 profile 的任务（spike 确认的确切任务名，通常是 `:baselineprofile:generateBaselineProfile`）→ 产物拷/生成到 `androidApp/src/main/baselineProfiles/`；在 `docs/testing.md` 记录 before/after P50/P90。Ringing 基准若被 FSI/turnScreenOn 标志干扰（真机差异），G0 后校准。

**验收（可测）**：
- `.\gradlew.bat :androidApp:assembleDebug` 产物含 profile（`aapt2 dump` 或 benchmark 报告佐证）。
- `.\gradlew.bat :baselineprofile:connectedBenchmarkAndroidTest`（模拟器 mustwake36 可跑，启动命令见 `docs/testing.md`）出报告，P50/P90 记入文档；Ringing 冷启动 P50 有 before/after 对比。
- 无设备/模拟器时任务标记为"待 G0"，不阻塞其余 M1-A。

### M1-A4 a11y + 字符串基础设施

**落点**：新 `shared/src/androidMain/res/values/strings.xml`；9 个 UI 文件去硬编码。

现状锚点（已 grep 核实，模式 `"[^"]*[一-龥]`）：
- `androidApp/src/main/res/values/strings.xml` 现 **17 条**（通知/响铃文案），消费者是 `AlarmNotifications.kt` 与 `RingingActivity.kt`（app 模块）。
- **shared 无 res 目录**（`shared/src/androidMain/` 只有 AndroidManifest + kotlin），但 `shared/build.gradle.kts:16` 已开 `androidResources.enable = true`——字符串可落 shared res，屏幕用 `stringResource`。
- 硬编码中文 **94 处/9 文件**（含 `"$it 步"`、`" · ${event.steps} 步"` 等插值串，集中在 SettingsScreen/PermissionScreen/HistoryScreen/AlarmEditScreen/App.kt/AlarmListScreen/RingingScreen/TimeText/MwComponents）。

步骤：
1. 建 `shared/src/androidMain/res/values/strings.xml`，把上述 94 处迁入（P0 页面全部；通知/FGS 文案留在 androidApp——**规则：屏幕字符串在 shared res，服务/通知字符串在 androidApp res**，写进 `docs/ui.md`）。插值串带 `%1$d`/`%1$s` 占位符。
2. a11y：`MwComponents.kt` 的 `MwFab`/`MwIconAction` **已有** `contentDescription` 参数（`MwComponents.kt:296-303`、`:420-426`）——改为**逐调用点补缺与默认值**，不是加参数；`RingingScreen.kt` 步数/摇晃进度加 `semantics { contentDescription = ... }`；长按停止加状态描述（"按住 3/10 秒"）；对照 `docs/ui.md` 屏幕表逐屏补。
3. 200% 字体：排查 `MwComponents.kt` 固定高度（`Modifier.height` 硬编码行）改为 `defaultMinSize`；`RingingScreen` 核心信息区不截断。
4. 对比度：Miuix 主题色下正文/次要文本抽查（人工清单写入 `docs/ui.md`）。

**验收（可测）**：
- `grep -rn '"[^"]*[一-龥]' mustwake/shared/src --include=*.kt` → 0 处字符串字面量（注释除外；注意用全串匹配，`"[一-龥]` 开头锚定会漏插值串的假阴性）。
- 模拟器（TalkBack 可用）手工路径：创建闹钟→响铃→停止，全程可读；记录在 `docs/testing.md`。
- Roborazzi 加 fontScale=2.0 截图 2 张（列表/响铃）加入基线。

### M1-A5 崩溃/ANR 本地可观测性

**落点**：`androidApp` 新 `obs/` 包；`MustWakeApp.onCreate()` 安装。

现状锚点：
- 安装点：`androidApp/.../MustWakeApp.kt:22-31`（onCreate 在 `AppContainer` 之后已有 sweep 协程，handler 装在其前）
- 关键路径已有 `Log.*` 调用：`AlarmReceiver`/`BootReceiver`/`AlarmService`/`AlarmMaintenance`（现有日志点双写入环形缓冲）

步骤：
1. `obs/RingBuffer.kt`（纯 JVM，定长 512 条或 256 KB，线程安全，满则覆盖最旧）+ `obs/CrashLogger.kt`（`Thread.setDefaultUncaughtExceptionHandler` 链式委托，崩溃时缓冲落 `filesDir/obs/`）。
2. ANR 信号：主线程 watchdog（心跳写入 + 后台线程 5s 无心跳记 `ANR-suspect` 标记）。**ANR-suspect 一旦判定必须立即异步落盘**（真实 ANR 由系统杀进程，`UncaughtExceptionHandler` 不会执行——只存内存的标记在最需要时恰好丢失）；崩溃缓冲同理定期/事件触发落盘。**不做** SIGQUIT ptrace 类复杂方案。
3. 导出：设置页复用 M1-A1 的 SAF 通道导出 `mustwake-obs-YYYYMMDD.txt`（含缓冲 + 设备型号/系统版本/权限状态快照）。
4. **默认不上传**、无网络调用；文档声明数据仅本地（`docs/permissions.md` 或新 `docs/observability.md`）。

**验收（可测）**：
- `RingBufferTest`（JVM，≥4 例：覆盖写、并发写、格式、截断）。
- 模拟崩溃（debug 触发按钮或 `adb shell am crash`）→ 重启后导出文件含崩溃栈 + 前 512 条日志。
- **ANR 落盘**：模拟主线程 sleep 10s 触发标记 → **立即杀进程** → 重启导出仍含 `ANR-suspect` 标记（验证落盘时机，而非存活后导出）。
- 响铃链路回归：`.\gradlew.bat test` 全绿；响铃不受 handler 影响（模拟器 14 项中 #5/#9 重跑）。

---

## M1-B 实施步骤（G0 真机到位后）

### G0 真机门禁
`adb devices` 确认（小米/红米 Android 14+）→ `adb install -r` 当前 APK → `docs/testing.md` 模拟器 14 项 + 真机基线（FSI、精确闹钟、ACTIVITY_RECOGNITION、DND、HyperOS 电池/自启动）→ 记录型号/系统/权限状态/证据。**无设备：M1-B 全部阻塞。**

### M1-B1 试响 10 秒
- `AlarmService` 加 `ACTION_PREVIEW` + `EXTRA_PREVIEW`（`androidApp/.../service/AlarmService.kt`）：**不写** session/event（不调 `SessionRepository`/`HistoryRepository`）、不取消/重排真实闹钟、10 秒自动结束（`BuildConfig.DEBUG` 30 秒）。
- `RingingActivity.kt`/`RingingScreen.kt` 预览态：标"试响"，停止直接 finish；真实 `ACTION_START` 到达时预览立即让位（stopSelf 或独立 `previewSessionId`，见 phase2.md 决策）。
- 设置页加「试响」按钮。
- **验收**：Robolectric 断言预览后 DB alarms/sessions/events 行数不变；模拟器正式闹钟回归（testing.md #5/#10 重跑）。

### M1-B2 跳过下一次（Room v2→v3）
- **先写** `data/src/test/.../db/Migration2To3Test.kt`（TDD，参照 `Migration1To2Test.kt`），再实现 `MIGRATION_2_3`：`alarms` 加 `skip_until`、`sound_uri`、`vibration_pattern`、`volume_ramp_seconds`（一次加齐，决策见 phase2.md「Room v3 决策」）。
- `schemas/.../3.json` 导出提交；`MustWakeDatabase` version 3（`data/.../db/MustWakeDatabase.kt:17-21` 现 version=2、exportSchema=true）。
- **备份格式同步演进**：`BackupModels` 增补 v3 四字段、`schemaVersion` bump 为 2；`BackupCodec` 对 `schemaVersion=1` 的旧备份仍可导入（缺列按默认值/NULL），测试覆盖旧备份兼容导入。
- `NextTriggerCalculator.kt`（`data/.../alarm/`）支持 `skipUntil`：跳过 < `skipUntil` 的触发，过期自动失效（读取时判定）；`AlarmEntity.kt` 加列。
- UI：列表"已跳过 · 下次 X"（`AlarmListScreen.kt`）、编辑页"跳过下一次/恢复下一次"（`AlarmEditScreen.kt`）。
- **验收**：迁移测试（旧数据保留/默认值）；`NextTriggerCalculatorTest` 新增单次/重复/跨天/过期/恰好边界 ≥6 例；旧备份（schemaVersion=1）导入兼容测试；重启后跳过状态保持（模拟器手工）。

### M1-B3 英文完成
- `shared/src/androidMain/res/values-en/` + `androidApp/.../values-en/` 覆盖 M1-A4 全部键；`TimeText.kt` 日期/星期改 `DateTimeFormatter` + `Locale`；用户输入（label）不翻译。
- **验收**：模拟器改系统语言为 English (United States)（`setprop persist.sys.locale en-US` 需重启才生效，优先走系统设置 UI）；全流程截图无中文残留；日期格式为英文 locale。

### M1-B4 T8 真机 A–F + M1-B5 M1 关闭
- 按 **`HANDOFF.md` §7（约 :547-552）定义的 A–F** 逐项（spec 只有"A–F 验收门禁"字样，条目定义以 HANDOFF 为准）：
  - **A** 构建安装；**B** 核心闭环（真实计步）；**C** snooze/紧急/降级；**D** 双开关（底栏×玻璃）；**E** 完整版/HyperOS；**F** Root（T9，可选不阻塞 M1）。
  - 每项记录 PASS/FAIL + 截图 + `adb logcat -d -s MustWake:* AndroidRuntime:E` + `adb shell dumpsys alarm` + 历史。重点：FSI/ACTIVITY_RECOGNITION 撤销、snooze 杀进程、重启、missed、HyperOS 杀后台。失败修复后重跑。
- M1 关闭门槛：122+ tests 全绿、APK、`docs/*.md` 更新（含 `docs/testing.md` 真机表）、每任务一提交、**A–F 全 PASS 证据**。

---

## M2 / M3 摘要（M1 关闭后展开为细化计划）

| ID | 任务 | 关键验收（每项可测） |
|---|---|---|
| M2-1 | 统计 + streak | `WakeStatsRepository` 纯聚合；固定事件序列/跨周/时区(DST)单测；空态显示 |
| M2-2 | 自适应目标 | 纯函数：最近 7 次 COMPLETED 中位数 +10%、取整 10、钳 10–500；<3 次不显示；边界 0/1/3/7 条单测 |
| M2-3 | 防作弊 | `ShakeCounter`：30 次分布 ≥8 秒、等间隔注入被拒；阈值可调；纯函数单测覆盖过快/过慢/均匀 |
| M2-4 | 渐进音量 + 每闹钟声音/振动 | 用 v3 列；声音在 `AlarmService` 播放器层（通知 channel 不动，`AlarmNotifications.kt` 保持静音）；30 秒 30%→100% 曲线单测；每闹钟独立；**BackupModels 同步扩展 + schemaVersion bump + 旧备份兼容** |
| M2-5 | 唤醒后小结 | 完成/降级/紧急三路径显示用时/步数/snooze/streak；纯本地无网络；可关；不阻塞返回 |
| M2-6 | 玻璃帧率基准 | Macrobenchmark 帧率 P50/P90 before/after；低端机/模拟器数据 → 提供 T6 降级阈值 |
| M2-7/8 | 新字符串英文 + M2 关闭 | 新页面无中文残留；全量回归 |
| M3-1 | 小组件 + QS | Glance 4×2/2×2；Flow 更新无轮询；点击编辑；QS 短按进应用/长按跳过；进程重启后 1 秒内刷新 |
| M3-2 | 自动化接口 | 签名权限 `CONTROL` + 受保护 receiver（SKIP_NEXT/DISABLE_NEXT/TEST_RING/SNOOZE）；未授权被拒（adb 验证）；Tasker/快捷指令文档；webhook 为**可选项**（超时不阻塞响铃，是否实现 M3 立项时定） |
| M3-3 | 节假日/调休 | 默认关；手动跳过优先（依赖 M1-B2）；自动规则最后做；跨年更新文档 |
| M3-4 | T9 root（可选） | allowlist 命令逐条结果；拒绝 su 回退；可撤销；**不做 daemon/轮询/root socket** |
| M3-5 | M3 关闭 | P2 全项回归、文档、提交 |
| M4-1 | Wear OS（不早于 M2） | 独立 `:wear`；手机是唯一停止判定方；断连/低电/延迟验收 |

---

## Risks and Mitigations

| # | 风险 | 影响 | 缓解 | 验证信号 |
|---|---|---|---|---|
| 1 | kotlinx.serialization 插件无法挂到 AGP 9 内置 Kotlin 的 `:data` | M1-A1 阻塞 | 独立 spike 提交（插件 + `kotlinx-serialization-json` 运行时两件套）；回退 org.json（codec 接口不变；测试用 Robolectric 或 `org.json:json` 测试依赖） | spike 提交编译结果 |
| 2 | Roborazzi + KMP shared Compose + Miuix 自绘/backdrop 在 Robolectric NATIVE 下空白/崩溃/差异 | 截图脆弱/跑不起来 | 首跑 2 张图证伪再扩 10 张；模糊区裁剪/阈值；有 Compose 冒烟先例（`MainActivityTest.kt:42-49`，仅组合级） | record 首跑结果 |
| 3 | Robolectric 无 SDK 37 运行时 | 新测试环境漂移 | 全部 UI 测试维持 SDK 34（现状一致，`docs/data-layer.md:135-138`） | 测试配置 diff |
| 4 | Baseline Profile 需设备/模拟器 | M1-A3 延期 | 模拟器 mustwake36 可跑；不阻塞其余任务；无设备标"待 G0" | connectedBenchmark 是否可跑 |
| 5 | **baselineprofile/benchmark 与 AGP 9.3.2 内置 Kotlin + KMP `:shared` 兼容性** | M1-A3 做不成 | 独立 spike 小提交先跑空场景；确切任务名 `:baselineprofile:tasks` 实测；不通则挂起待工具链升级 | spike 编译+空跑结果 |
| 6 | 无真机 | M1 无法关闭 | G0 硬门禁；M1-A 先行；**不宣称发布完成** | `adb devices` |
| 7 | 试响与真实闹钟冲突 | 预览抢响铃 | 真实优先/独立 previewSessionId；不写库 | 预览中触发正式闹钟的手工用例 |
| 8 | Room v2→v3 迁移丢数据 | 用户丢闹钟 | 先写迁移测试；一次加齐 4 列；旧行默认值；`MigrationTestHelper` | Migration2To3Test |
| 9 | **导入非原子（清空后逐条插入中断）** | 用户闹钟丢失（P0 承诺违背） | `withTransaction` 单事务全量回滚（M1-A1 步骤 4）；替换模式确认文案含现有数量 | 中断注入回滚测试 |
| 10 | **合并去重键静默丢数据**（同键不同 goalSteps） | 用户配置丢失 | 键含 (hour,minute,repeatDaysMask,label) 且同键"保留已有"；报告给逐项明细；与 roadmap 措辞统一 | 重复项测试 + ImportReport 断言 |
| 11 | **ANR 标记只存内存，系统杀进程即丢** | 最需诊断时无证据 | ANR-suspect 判定即异步落盘（M1-A5 步骤 2） | "标记后杀进程重启导出"验收 |
| 12 | **备份格式不随 Room v3 演进** | 换机丢每闹钟声音/振动配置 | M1-B2/M2-4 强制扩展 BackupModels + schemaVersion bump + 旧备份兼容测试 | 旧备份导入测试 |
| 13 | 防作弊误判 | 真实用户停不掉 | 阈值可调/可观测；纯函数测试；真机调参（M2-3 硬性要求） | 真机调参记录 |
| 14 | 通知渠道不可变 | 双声 | 单一 `alarm_ringing` 静音渠道；声音只在播放器层 | 代码审查确认 channel 创建仅一处 |
| 15 | 94 处字符串迁移引入 UI 回归 | 文案丢失/错位 | M1-A2 截图基线**先于** M1-A4，迁移后 verifyRoborazzi 兜底 | verifyRoborazziDebug |

## Verification Steps（每个任务完成时）

```powershell
cd E:\MustWake\mustwake
.\gradlew.bat test                              # 全量单测（当前 122，每任务新增）
.\gradlew.bat :androidApp:assembleDebug         # 构建
.\gradlew.bat :androidApp:verifyRoborazziDebug  # M1-A2 之后每次都跑
```

- 任务专项命令见各任务"验收"栏（`--tests` 过滤单类、connectedBenchmark、adb 等）。
- 每任务一个 git 提交（conventional commits 英文），同步更新对应 `mustwake/docs/*.md` 与本计划状态。
- 汇报模板（HANDOFF-PHASE2-PLAN.md §10）：完成 / 改动（`path:line`）/ 验证（`<command>` → 结果）/ 未完成/风险。

## 交付顺序（First PRs）

1. `feat(data): backup and restore alarms/settings as JSON`（M1-A1，含依赖 spike + 导入事务）
2. `test(ui): add Roborazzi screenshot baselines`（M1-A2，先 2 张证伪再扩全量）
3. `perf(startup): add baseline profile for Main/Ringing`（M1-A3，先兼容性 spike，需模拟器）
4. `feat(a11y): content descriptions and string resources`（M1-A4，依赖 2 的基线兜底）
5. `feat(observability): local crash/ANR ring buffer`（M1-A5）
6. **G0 真机** → `feat(ringing): add 10-second test ring`（M1-B1）
7. `feat(scheduling): skip next occurrence`（M1-B2，先写 v2→v3 迁移测试 + 备份格式扩展）
8. `feat(i18n): complete English localization`（M1-B3）→ T8 A–F（M1-B4，按 `HANDOFF.md` §7）→ M1 关闭（M1-B5）

## Definition of done（本计划 M1-A 部分）

- [ ] M1-A1..A5 各有提交、测试、文档；`.\gradlew.bat test` 全绿（122 → 122+新增）
- [ ] 备份导入具备事务原子性 + 换机恢复主路径（清空→导入）测试
- [ ] 截图基线 ≥10 张入库，verify 两次通过
- [ ] Baseline Profile 有 before/after 记录（或明确标注"待 G0"/"待工具链升级"）
- [ ] shared 无硬编码中文字符串（全串 grep 0 命中）；a11y contentDescription 调用点全覆盖
- [ ] 崩溃/ANR 导出可复现（含杀进程后落盘）；默认不上传
- [ ] 全程不宣称 T8/M1 完成；G0 未过则 M1-B 保持阻塞

---

## 评审修订记录（2026-10-19，critic REVISE → 本文修订）

| 级别 | 发现 | 修订位置 |
|---|---|---|
| Blocker | 导入（替换模式）无事务性，中断即丢闹钟 | M1-A1 步骤 4/7、验收、风险 #9 |
| Major | round-trip 验收缺"清空"步骤，换机场景未被测 | M1-A1 测试/验收（导出→删除全部→导入） |
| Major | M1-B4 引用 spec "A–F" 指向错误（定义在 HANDOFF.md §7） | M1-B4 内联 A–F 清单并改引 HANDOFF |
| Major | ANR-suspect 只存内存，系统杀进程即丢 | M1-A5 步骤 2 + 杀进程后导出验收、风险 #11 |
| Major | M1-A3 缺 generateBaselineProfile、版本 pin、兼容风险 | M1-A3 步骤 1/5、风险 #5 |
| Major | M1-A4 grep 假阴性 + 清点偏低（70→94，漏插值串） | M1-A4 验收 grep 模式与清点、风险 #15 |
| Major | 合并去重键与 roadmap 冲突且不完备 | M1-A1 步骤 5 统一语义（保留已有+明细报告）、风险 #10 |
| Major | 备份格式未随 Room v3 演进 | M1-B2/M2-4 增补扩展义务、风险 #12 |
| Minor ×8 | org.json 测试 stub、缺运行时工件、graphicsMode DSL 无效、contentDescription 已有参数、TimeText 注入点、locale 命令、M3-5/webhook 遗漏、strings 17 条/测试表述 | 已分别并入 M1-A1/A2/A4、M1-B3、M2/M3 表 |

未采纳：无。Open questions（不计缺陷，执行中验证）：Roborazzi NATIVE 对 Miuix 自绘/模糊的首跑表现；Ringing 基准被 FSI/turnScreenOn 干扰程度（G0 后校准）；`generateBaselineProfile` 确切任务名（spike 实测）。
