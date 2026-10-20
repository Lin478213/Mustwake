# HANDOFF — Phase 2 M1-A 执行进展交接

> 交接时间：2026-10-19；2026-10-20 更新（M1-A3 spike 完成）。
> 计划与硬约束见 `HANDOFF-PHASE2-PLAN.md`、`.omc/plans/phase2-execution.md`、`CLAUDE.md`；本文只记录**执行状态与接手要点**，不重复它们的内容。

## 状态总览

| 任务 | 状态 | 提交 | GitHub |
|---|---|---|---|
| M1-A1 备份/恢复 JSON | ✅ 完成（代码评审 APPROVE） | `2456352` + `cd5466d` | 已推送 |
| M1-A2 Roborazzi 截图基线 | ✅ 完成（代码评审 APPROVE） | `9cce785` | 已推送 |
| M1-A3 Baseline Profile | 🔄 spike 完成（评审 APPROVE），真实场景步骤 2-5 待做 | `build(baselineprofile)` spike 提交 | 未推送 |
| M1-A4 a11y + 字符串 | ⬜ 未开始 | — | — |
| M1-A5 崩溃/ANR 可观测性 | ⬜ 未开始 | — | — |
| G0 真机门禁 | ⬜ 未开始 | — | — |
| M1-B 系列（试响/跳过/英文/T8/M1 关闭） | ⬜ 未开始（依赖 G0） | — | — |

测试基准：**158 个 JVM/Robolectric 测试全绿**（`:data` 119 + `:androidApp` 39，含 15 个截图用例）。

---

## M1-A1 备份/恢复 JSON（完成）

**提交**
- `2456352` `build(data): add kotlinx.serialization for JSON backups` — 依赖 spike：AGP 9.3.2 内置 Kotlin 挂 serialization 编译器插件验证通过，无需 org.json 回退。
- `cd5466d` `feat(backup): export and restore alarms and settings as JSON (M1-A1)` — 11 文件 +1154 行。

**产出**
- `mustwake/data/src/main/kotlin/com/mustwake/alarm/data/backup/`：`BackupModels`（snake_case，schema v1）、`BackupCodec`、`BackupRepository`。
- `SettingsScreen.kt`「备份与恢复」卡：导出 / 合并导入 / 替换导入（REPLACE 有确认框）。
- 测试 21 例（8 codec + 13 repository），在 `BackupCodecTest.kt` / `BackupRepositoryTest.kt`。

**关键语义**（权威描述在 `mustwake/docs/data-layer.md` § Backup / restore JSON）：
- `importJson` 永不外抛（取消除外）：坏 JSON / 未知 schema 零写入；闹钟写入单事务，失败整体回滚；settings 写入失败以 `alarms imported, settings failed: …` 计入 details，**已导入的闹钟保留**。
- MERGE 去重键 `(hour, minute, repeatDaysMask, label)`（label 两侧 trim）：保留已有闹钟，备份行计 `skippedDuplicates`；REPLACE 先 `deleteAll()` 不去重。
- 不导出 session / history / `onboarding_completed`；时间戳与空闲 id 原样恢复（export → wipe → import 全等往返）。
- UI 侧：2 MB 读取上限、`pendingImportMode` 用 `rememberSaveable`、两 SAF 回调 try/catch（`CancellationException` 重抛）。

**评审闭环**：首轮 REVISE（1 Major：settings 写入在 try/catch 外）→ 修复 + 补 5 测试 → 复核 APPROVE，4 条 LOW 一并修掉。

---

## M1-A2 Roborazzi 截图基线（完成，待推送）

**提交**：`9cce785` `test(ui): add Roborazzi screenshot baselines (M1-A2)` — 22 文件（含 15 张 PNG）。

**产出**
- 基线：`androidApp/src/test/screenshots/` 15 张 1080×2340（Pixel5）——空/有数据列表、编辑页、设置页、历史页（空+有数据）、响铃页（正常+降级）、悬浮底栏×液态玻璃 4 组合；浅色全量 + 深色（列表/设置/响铃）。
- 测试：`androidApp/src/test/kotlin/com/mustwake/alarm/ui/ScreenshotTests.kt`（15 用例，`@GraphicsMode(NATIVE)` + `@Config(sdk=[34], qualifiers=Pixel5)`）。
- 生产小改动（评审确认默认路径零变化）：
  - `AlarmListScreen.kt:46` 可注入时钟 `clock: () -> Instant = { Instant.now() }`（`App.kt` 调用不传参）；
  - `MwComponents.kt` 新增 `LocalMwAgslHighlightEnabled` / `MwSquircleFallback` / `MwThemeBackground`，均 `@VisibleForTesting` 且不在生产调用路径。
- 依赖：`libs.versions.toml` pin `roborazzi = "1.76.0"`（AGP 9 兼容）+ `roborazzi-compose` + `jetbrains-compose-ui-test-junit4`（随 compose 1.12.0）。

**常用命令**
```bash
./gradlew :androidApp:recordRoborazziDebug   # 重新录制基线（UI 故意改版后）
./gradlew :androidApp:verifyRoborazziDebug   # 对比基线（每次 UI 改动后跑）
```

**已知限制**（评审判定可接受；说明在 `ScreenshotTests.kt` KDoc）
1. Robolectric NATIVE 编译不了 AGSL `layout(color)` shader → 截图走圆角矩形 + `Highlight.Plain` 回退；**文字/布局/配色真实**，仅描边形状与真机有差。对 M1-A4 字符串迁移兜底足够。
2. 两张玻璃图 `compareOptions.changeThreshold = 0.01f`（blur 非位稳定），其余全部 0f。副作用：只影响玻璃区域的微小改动可能漏报（实证：故意改单色时玻璃 2 张未报、其余 12 张报差异）——改玻璃视觉时人工看 compare 图。
3. shell 4 组合只 seed **停用**闹钟（避免「下次 X」墙钟文本漂移 flake）；带「下次」文本的确定性截图在列表页用例（注入 `FIXED_NOW`）。

**验收证据**：record 15 张 → verifyRoborazziDebug 连续两次全绿（其一 `--rerun-tasks` 强制实跑）→ 故意改组件颜色 12 张报差异（compare/actual 产物生成）→ 还原恢复 → 全量 158 例 0 失败。

---

## 遗留事项（按优先序）

1. **推送 M1-A3 spike 提交到 GitHub**（`9cce785` 及之前的提交已在 origin；推送授权按会话确认）。
2. **M1-A4 前置小项**（M1-A2 评审 LOW，非阻断）：
   - `ScreenshotTests.kt:81` `createComposeRule` 已弃用 → 迁 `androidx.compose.ui.test.junit4.v2.createComposeRule`。注意 v2 是排队调度语义，迁移后确认 `waitUntil` 同步点仍充分，并重跑 verifyRoborazziDebug ×2。
   - 可选：`org.jetbrains.compose.ui:ui-test-junit4` 换 `androidx.compose.ui:ui-test-junit4`（`androidApp` 是纯 Android 模块，依赖图更直接）。
3. `exportJson` 失败仍外抛（设计使然：导出无部分提交，UI 已 catch）。若日后导出改多步写入需重新评估。
4. Robolectric AGSL 限制（上节）：若未来 Robolectric 支持 `layout(color)`，可去掉 `MwSquircleFallback` 并重录基线。
5. **生成 profile 提交策略**（M1-A3 spike 评审 Minor-2，已定）：`androidApp/src/release/generated/baselineProfiles/` 只提交真实场景 profile 正本（步骤 5 一并提交），spike 空场景 profile 不进仓库（跑完生成命令后手工清理或确认不误提交）。

---

## 下一步：M1-A3 步骤 2-5（真实场景）

spike（步骤 1）已完成并独立提交——`:baselineprofile`（`com.android.test` → `:androidApp`）+ `androidx.benchmark.macro` 1.5.0 × AGP 9.3.2 内置 Kotlin + KMP `:shared` 兼容性成立，空场景全链路实测通过（评测任务 `:baselineprofile:connectedBenchmarkReleaseAndroidTest`、生成任务 `:androidApp:generateBaselineProfile`、变体分工与兼容区间等实测结论见 `.omc/plans/phase2-execution.md` M1-A3 步骤 1）。剩余步骤（同文件 L100-108）：

- 场景类：`MainActivityStartupBenchmark`、`RingingActivityStartupBenchmark`（`StartupTimingMetric`，cold start，各 ≥3 次迭代），替换 `EmptyStartupBenchmark`；`EmptyBaselineProfile` 换真实采集旅程。
- **生成并落盘**：`./gradlew :androidApp:generateBaselineProfile -PbenchmarkAllowEmulator=true` → 产物自动落 `androidApp/src/release/generated/baselineProfiles/baseline-prof.txt`（插件自动挂进 release 源集，随最终 profile 提交；spike 实测路径，非原计划的 `src/main/`）。
- **模拟器跑必须加 `-PbenchmarkAllowEmulator=true`**（spike 评审 Minor-1 门禁：默认严格，防 G0 静默收模拟器/低电量数字）；真机/G0 不带。
- 模拟器 mustwake36（启动命令见 `mustwake/docs/testing.md`，需 `ANDROID_USER_HOME`/`ANDROID_AVD_HOME`）；无设备时标「待 G0」不阻塞。
- before/after P50/P90 记入 `mustwake/docs/testing.md`；Ringing 基准若被 FSI/turnScreenOn 干扰，G0 后校准。

---

## 工作流约定（本次执行沿用，交接后继续）

- **每任务一个 git 提交**，conventional commits 英文；实现与 plan/spec 偏离时**先改 `.omc/` 文档再改码**。
- **作者/评审分离**：executor 实现 → 独立验证（JUnit XML 计数，不信口头报告）→ code-reviewer 评审 → 修复 → 提交。评审 REVISE 时逐条修复并请复核。
- 构建自包含：`cd /home/lin/Java/MustWake/mustwake` 后 `./gradlew`（JDK 21 / SDK 37 / Gradle 9.8.1 在 `toolchain/`，不设 JAVA_HOME；2026-10-20 起为 Linux 工具链，旧 Windows 路径已失效）。镜像顺序在 `settings.gradle.kts`（Aliyun 优先）。
- 硬约束（`HANDOFF-PHASE2-PLAN.md` §8）：compileSdk 37 不降；Miuix import 只在 `MwComponents.kt`；数据层放 `:data`；Room 迁移必须测试；试响不写 session/event；T8 真机是硬门禁；不加账号/云同步/广告/排行榜/睡眠周期。
- 测试计数变化时同步 `CLAUDE.md` 与 `mustwake/docs/data-layer.md` 的数字。

## 快速验证

```bash
cd /home/lin/Java/MustWake/mustwake
./gradlew test                                # 158 例应全绿
./gradlew :androidApp:verifyRoborazziDebug    # 截图对比应 15/15
./gradlew :androidApp:assembleDebug           # 构建 APK
```
