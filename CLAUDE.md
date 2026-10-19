# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 仓库布局

Git 根是 `E:\MustWake`，真正的工程在 `mustwake/` 子目录（Gradle 多模块 Android 项目）：

- `mustwake/` — MustWake：步数解锁闹钟 App（到点响铃，走到目标步数才能停止，Miuix UI）
- `mustwake/docs/` — 各任务的权威实现文档（见下方"文档导航"）
- `toolchain/` — （gitignore）自包含工具链：JDK 21、Android SDK 37、Gradle 9.8.1、模拟器 AVD
- `oh-my-claudecode*/`、`.workbuddy/` — （gitignore）上游参考 / 本地 agent 元数据，非本项目源码
- `HANDOFF.md`、`HANDOFF-PHASE2.md`、`HANDOFF-PHASE2-PLAN.md` — 交接与 Phase 2 计划
- `.omc/specs/`、`.omc/plans/` — spec 与 plan，**实现偏离时先改文档再改代码**

## 常用命令

在 `mustwake/` 目录下执行（PowerShell）。构建完全自包含，不需要设置 JAVA_HOME/ANDROID_HOME：

```powershell
cd E:\MustWake\mustwake

# 构建
.\gradlew.bat :androidApp:assembleDebug
# APK: androidApp\build\outputs\apk\debug\androidApp-debug.apk

# 测试（当前 158 个 JVM/Robolectric 测试）
.\gradlew.bat test                          # 全部模块
.\gradlew.bat :data:testDebugUnitTest       # 仅数据层
.\gradlew.bat :androidApp:testDebugUnitTest # 仅 app 模块

# 截图基线（androidApp/src/test/screenshots/）
.\gradlew.bat :androidApp:recordRoborazziDebug   # 重新录制基线
.\gradlew.bat :androidApp:verifyRoborazziDebug   # 对比基线（UI 改动后跑）

# 跑单个测试类
.\gradlew.bat :data:testDebugUnitTest --tests "*StopPolicyTest*"

# 安装到已连接设备
adb install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk
```

模拟器启动与验收命令见 `mustwake/docs/testing.md`（AVD 在 `toolchain/android-home/avd/mustwake36`，需设 `ANDROID_USER_HOME`/`ANDROID_AVD_HOME`）。

注意事项：

- 首次 Robolectric 运行会下载 android-all（本网络约 5 分钟），之后很快。
- Robolectric 只有 SDK 34 运行时，DAO 测试固定跑 SDK 34；行为是 SQL 层的，与版本无关。
- `settings.gradle.kts` 把 Aliyun 镜像放在 Google/Maven Central 之前（本网络直连很慢）；镜像不可用时可移除或调序。

## 架构

### 模块划分与依赖方向

```
:androidApp  (com.android.application)  平台壳：调度、接收器、前台服务、传感器、通知
    ├─→ :shared  (KMP，实际只用 androidMain)  Compose/Miuix UI 屏幕
    └─→ :data    (com.android.library + KSP)  Room/DataStore/仓库 + 纯 Kotlin 逻辑
:shared (androidMain) ──→ :data
```

- **`:data`** — `AppContainer`（手动 DI，无 Hilt）、`AlarmRepository`/`HistoryRepository`/`SessionRepository`/`SettingsRepository`、Room（`mustwake.db` v2：`alarms`、`alarm_events`、`alarm_sessions`）、DataStore 设置。纯逻辑也放这里以便 JVM 测试：`StopPolicy`、`StepBaselineTracker`、`ShakeCounter`、`NextTriggerCalculator`。**数据层放 `:data`，不要塞进 KMP `shared`**（KSP 在 KMP 模块里需要额外接线）。
- **`:shared`** — UI 屏幕（列表/编辑/历史/设置/响铃/权限引导）+ `App.kt` 导航壳（简单 `AppTab` 状态，无导航库）。UI 直接读仓库，调度通过 `AlarmUiActions` 回调由 `:androidApp` 实现。
- **`:androidApp`** — `AlarmScheduler`（`setAlarmClock` + 不精确回退）、`AlarmReceiver`/`BootReceiver`、`AlarmService`（前台服务）、`StepSensorRepository`/`ShakeDetector`、`AlarmNotifications`、`MainActivity`/`RingingActivity`/`MustWakeApp`。

### 响铃主流程

```
AlarmManager.setAlarmClock
  → AlarmReceiver：重复闹钟立即注册下一次；单次闹钟触发后自动禁用
  → AlarmService（FGS）：5 秒内 startForeground（health → specialUse 类型逐个回退）
      铃声循环 + 振动 + wake lock + 计步（STEP_COUNTER 基线 → STEP_DETECTOR → 摇晃降级）
  → StopPolicy 判定：步数达标 COMPLETED / 长按 10 秒 EMERGENCY / 降级模式摇晃+长按 DEGRADED
  → snooze：会话转 SNOOZED 并持久化 deadline，服务停止；再触发时恢复同一会话（步数累计）
  → 广播 ACTION_RINGING_STOPPED 关闭 RingingActivity
```

### 关键不变量（改动时必须保持）

- **Miuix import 只允许出现在 `shared/.../ui/design/MwComponents.kt`**；页面只用 `Mw*` 语义组件。换 UI 实现只改适配层（选型依据：`docs/decisions.md`，不迁移 Flutter）。
- **校验策略**：写入 `require(...)` 直接抛异常；读取 `AppSettings.sanitized()` 用默认值兜底损坏数据。
- **Room 迁移**：`exportSchema = true`，schema JSON 提交进 `data/schemas/`；每次改表必须新增 `Migration` + 导出 schema + 对应迁移测试（现为 v2，`MIGRATION_1_2`）。无破坏性迁移。
- **snooze 用独立 PendingIntent**（request code 带 `0x40000000` 标志），绝不覆盖重复闹钟的下次注册；会话结束时 `cancelSnooze`。
- **通知渠道 `alarm_ringing` 故意静音无振动**——音频归服务所有以便确定性停止；渠道不可变，每闹钟声音在播放器层做，不要建大量 channel。通知不提供普通停止按钮，只有进度 + 剩余 snooze 时的 snooze 动作。
- **RINGING 会话有 30 分钟 deadline**：进程被杀后由 boot/app 启动时 `sweepExpired` 记 `MISSED`。
- **试响（Phase 2）不能写 session/event**；真实闹钟优先于一切调试路径。

### 硬约束

1. 不降 `compileSdk`（37，Miuix/backdrop AAR 要求）、不移除 Miuix、不用系统 JDK 替换项目内 JDK 21（`gradle.properties` 已固定）。
2. 平台目标：小米/红米 Android 14+；minSdk 26，targetSdk 36；包名 `com.mustwake.alarm`。
3. **T8 真机验收是硬门禁**：没有真机证据（计步、音频/振动、HyperOS 后台、权限撤销）不得宣称 T8/M1 完成；模拟器预检（14 项）不能替代。
4. 明确不做的功能不要加回来：账号/云同步、广告/核心付费墙、社交排行榜、睡眠周期智能唤醒；唤醒小结不引入天气/定位/网络。
5. Root 只做可选辅助（pm grant/appops/deviceidle），不做 daemon/轮询/root socket。

### 工作规则

- 每个任务一个 git 提交；conventional commits 英文（如 `fix(ringing): ...`）。
- 汇报只写实际跑过的命令与结果，不编造验证。
- 实现与 plan/spec 有偏离时：先更新 `.omc/` 文档并说明原因，不要静默改。
- 当前 UI 文案为中文；英文 i18n 在 Phase 2 计划中（`HANDOFF-PHASE2-PLAN.md`）。

## 文档导航

深度内容全部在文档里，不要重复抄进代码注释；按需阅读：

- `mustwake/README.md` — 项目状态与快速命令
- `mustwake/docs/toolchain.md` — 版本、路径、自包含构建证明
- `mustwake/docs/data-layer.md` — 表结构、设置字段/默认值、仓库 API
- `mustwake/docs/scheduling.md` — 注册规则、fallback、boot/timezone 恢复
- `mustwake/docs/ringing.md` — 服务/通知/FSI 细节
- `mustwake/docs/steps.md` — 计步降级链、StopPolicy、snooze 语义
- `mustwake/docs/ui.md`、`appearance.md`、`permissions.md` — 界面/外观/权限引导
- `mustwake/docs/testing.md` — 模拟器 14 项预检结果与设备缺口
- `mustwake/docs/decisions.md` — 技术选型记录（UI 为何不用 Flutter）
- `HANDOFF.md` / `HANDOFF-PHASE2.md` / `HANDOFF-PHASE2-PLAN.md` — 交接、Phase 2 范围与优先级
