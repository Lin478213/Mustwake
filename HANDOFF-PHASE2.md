# MustWake 第二阶段交接：功能扩展

> 交接时间：2026-10-19 19:20（UTC+8）
> 基线提交：`db2e80f`（工作区干净）
> 状态：**T0–T7 完成，T8 模拟器预验收通过，真机未做；本文件中的扩展功能尚未实现**

本轮决定：**除“不建议”清单外，全部推进**。上一轮在开始实现前被中止，因此没有任何功能代码落地；
这份文件是给下一位接手者的实施计划与验收标准。

---

## 1. 当前基线（不要重复建设）

- 工程：`E:\MustWake\mustwake`，模块 `:shared`（Compose Miuix）、`:data`（Room v2 + DataStore）、`:androidApp`。
- 已实现：T0 工具链、T1 数据层、T2 调度与权限、T3 响铃服务、T4 步数/停止策略、T5 Miuix 界面、
  T6 悬浮底栏 + 液态玻璃、T7 权限引导/HyperOS 引导/下次响铃/30 分钟 missed 窗口。
- 测试：`.\gradlew.bat test` → **122 tests, 0 failures**（data 98 + app 24）。
- APK：16,963,136 bytes，SHA256 `a678277167d5ed29fb148680599e469439eabf4ded65a2f3b09f90a1cd6ac078`。
- 模拟器预验收 14 项通过，见 `mustwake/docs/testing.md`；AVD 在
  `E:\MustWake\toolchain\android-home\avd\mustwake36`。
- 必读：`HANDOFF.md` → `mustwake/docs/toolchain.md` → `data-layer.md` → `scheduling.md` →
  `ringing.md` → `steps.md` → `ui.md` → `appearance.md` → `permissions.md` → `testing.md` →
  `decisions.md` → spec → plan。

### 硬约束（沿用）

- 每任务一个 git 提交；汇报只写实际跑过的命令。
- Miuix import 只允许出现在 `shared/.../ui/design/MwComponents.kt`；页面只用 `Mw*`。
- 数据层放 `:data`，不要塞进 KMP `shared`。
- 写入 `require`、读取 `sanitized()`；Room 迁移必须有测试。
- 不降 compileSdk、不移除 Miuix、不用 daemon/轮询保活。
- `targetSdk 36`，`minSdk 26`；工具链固定在 `E:\MustWake\toolchain`。

---

## 2. 本轮决策

### 推进（全部）

1. 发布前：试响、跳过下一次、备份/恢复、无障碍 + 英文。
2. 差异化：唤醒统计 + streak、自适应目标、防作弊、渐进音量 + 每闹钟铃声/振动、唤醒后小结。
3. 可见性/生态：桌面小组件 + 快捷设置磁贴、自动化接口/智能家居、节假日/调休。
4. 工程质量：Baseline Profile、截图/黄金图测试、玻璃帧率基准、崩溃/ANR 可观测性。
5. 原有：T8 真机验收、T9 Root 辅助（可选）。

### 明确不做（不要偷偷加回来）

- 账号 + 云同步：本地闹钟不应依赖网络和登录。
- 广告 / 把核心停止功能放进付费墙：闹钟场景伤害体验；最多一次性 Pro 主题/音效。
- 社交排行榜/好友：没有用户基数，先做个人 streak。
- 睡眠周期智能唤醒：依赖穿戴，误差与期望管理成本高。

---

## 3. 分期总表

| 阶段 | 功能 | 规模 | 依赖 | 主要落点 |
|---|---|---|---|---|
| P0 | 试响（10 秒预览） | S | 无 | `AlarmService`、`SettingsScreen` |
| P0 | 跳过下一次 | S–M | Room v3 | `AlarmEntity`、`AlarmScheduler`、列表 |
| P0 | 备份/恢复 JSON | S–M | 无 | `:data` 导出器、设置页 SAF |
| P0 | 无障碍 + 英文 | M | 功能稳定后做 | `shared/.../ui/**`、`strings.xml` |
| P0 | Baseline Profile | S–M | 无 | 新 `:baselineprofile` 模块 |
| P0 | 截图/黄金图测试 | S–M | 无 | `androidApp/src/test`（Roborazzi） |
| P1 | 唤醒统计 + streak | M | P0 数据不变 | `:data` 聚合、历史页 |
| P1 | 自适应目标 | S–M | 统计 | 编辑页建议值 |
| P1 | 防作弊 | M–L | 步数/摇晃 | `StopPolicy`、`ShakeCounter` |
| P1 | 渐进音量 + 每闹钟声音/振动 | M | Room v3/v4 | `AlarmNotifications`、`AlarmService` |
| P1 | 唤醒后小结 | S–M | 无 | `RingingScreen` |
| P2 | 小组件 + QS 磁贴 | M | Glance | 新 `:widget` 或 `:androidApp` |
| P2 | 自动化接口/智能家居 | M | 无 | manifest + 受保护 receiver |
| P2 | 节假日/调休 | M | 跳过下一次 | `:data` 日历规则 |
| P3 | Wear OS 伴侣 | L | P1 统计 | 新 `:wear` 模块 |

执行顺序建议：**M1 = P0 + T8；M2 = P1；M3 = P2；M4 = P3**。
P0 内先做“试响”，它同时也是 T8 的调试工具。

---

## 4. P0 发布硬化

### 4.1 试响 / 预览

**目标**：设置页“试响 10 秒”走完整链路（通知 → 全屏页 → 摇晃/步数门禁 → 停止），
不写 session/history，不影响正式闹钟。

实现要点：

- `AlarmService` 增加 `EXTRA_PREVIEW=true`；预览模式不调用 `SessionRepository.startSession`、
  不写 `alarm_events`、不取消/重排真实闹钟；30 秒自动结束；通知标题标注“试响”。
- 预览期间如果真实闹钟触发，真实闹钟优先：先 `stopSelf()` 预览实例或用独立
  `previewSessionId`，避免 `currentSessionId` 冲突。
- `RingingActivity` 增加 `EXTRA_PREVIEW`，停止后直接 finish，不导航到历史。
- 设置页增加入口，文案说明“不会记录历史”。

验收：

- [ ] 试响不产生 `alarm_sessions` / `alarm_events` 行；
- [ ] 试响期间正式闹钟仍按时触发；
- [ ] 30 秒自动停止，服务不残留；
- [ ] Robolectric：预览启动不写库。

### 4.2 跳过下一次

**目标**：只跳过下一次触发，之后自动恢复；列表显示“已跳过”。

实现要点：

- Room v3：`alarms` 增加 `skip_until INTEGER`（被跳过的那次触发时间，epoch millis）。
- `NextTriggerCalculator` 增加可选 `skipUntil`：若计算结果等于 `skip_until`，取下一次；
  跨天/重复规则要正确。
- `AlarmScheduler.schedule` 使用计算结果；跳过状态在触发时间过去后自动清除（读取时判断，
  不必写库）。
- 列表行显示“已跳过 · 下次 X”；编辑页提供“恢复下一次”。
- 迁移：`Migration(2,3)` + `MigrationTestHelper`；导出 schema。

验收：

- [ ] 单次/重复闹钟各一条：跳过当天，次日恢复；
- [ ] 跳过状态重启后仍有效，过期后自动失效；
- [ ] 迁移测试 v2→v3 通过，旧数据不丢。

### 4.3 备份 / 恢复

**目标**：闹钟 + 设置导出 JSON，换机导入；不引入账号/网络。

实现要点：

- `:data` 增加 `BackupCodec`（kotlinx.serialization），字段带 `schemaVersion`；
  导出 `alarms`、`AppSettings`；不导出 session/history（可选开关，默认不导出）。
- 使用 SAF：`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`，不申请存储权限。
- 导入前校验：版本、范围（步数/分钟）、重复掩码、时间；非法项跳过并报告；
  提供“替换/合并”二选一，默认替换需二次确认。
- 解析失败不得崩溃，返回可读错误。

验收：

- [ ] 导出→清空→导入后闹钟/设置一致；
- [ ] 非法 JSON、越界值、未知版本有明确错误；
- [ ] 单元测试覆盖 round-trip 与坏数据。

### 4.4 无障碍 + 英文

**目标**：TalkBack 可读、字体 200% 不截断、支持 English。

实现要点：

- 先做 a11y：所有图标按钮有 `contentDescription`；步数/进度有状态描述；
  长按停止有操作提示；颜色对比度检查。
- 再抽字符串：当前页面大量硬编码中文，按页面逐步迁到 `strings.xml`；
  英文翻译放 `values-en`；日期/星期用 `DateTimeFormatter` 本地化。
- 字体缩放：避免固定高度容器；NumberPicker 在 200% 下实测。
- 这是 P0 里最容易拖长的项，建议放在功能稳定后单独提交，不要和功能混在一起。

验收：

- [ ] TalkBack 能完成“建闹钟→响铃→摇晃/长按停止”；
- [ ] 200% 字体无遮挡/截断（截图）；
- [ ] 英文环境下无中文残留（除用户输入）。

### 4.5 Baseline Profile

**目标**：冷启动和响铃页冷启动可量化优化。

实现要点：

- 新 `:baselineprofile` 模块 + `androidx.benchmark.macro`；
- 覆盖 `MainActivity` 启动、`RingingActivity` 冷启动；生成 profile 放入 `androidApp/src/main/baselineProfiles`。
- 记录 before/after P50/P90；CI 不做设备基准，只保留脚本。

验收：

- [ ] profile 随 APK 打包；
- [ ] Macrobenchmark 报告有 before/after 数据；
- [ ] 响铃页冷启动无回归。

### 4.6 截图/黄金图测试

**目标**：UI 回归自动化，覆盖这次长按按钮类 bug 的可视状态。

实现要点：

- Roborazzi + Robolectric（无需设备）：列表空态/有数据、编辑页、响铃页（正常/降级）、
  历史页、设置页、四组合底栏。
- 固定字体缩放、深/浅色、时间/时区；图片入 `androidApp/src/test/screenshots`。
- 只对关键区域做像素对比，避免阴影/模糊导致全图脆弱。

验收：

- [ ] `.\gradlew.bat :androidApp:testDebugUnitTest` 含截图测试；
- [ ] 4 种开关组合都有基线图；
- [ ]  intentional UI 改动有更新流程说明。

---

## 5. P1 差异化

### 5.1 唤醒统计 + streak

- `:data` 新增 `WakeStatsRepository`：按周聚合 `alarm_events` + `alarm_sessions`：
  成功率、平均停止用时（started→finished）、平均步数、snooze 次数、连续达成天数。
- 时区/DST 用 `ZoneId` 计算，跨周边界有测试；空数据有空态。
- UI 放历史页顶部卡片或独立“统计”标签；不新增数据库表。
- 验收：给定固定事件序列，统计值精确；跨周/跨时区测试；历史清理后统计不崩。

### 5.2 自适应目标

- 基于最近 7 次 `COMPLETED` 的实际步数中位数 +10%，限制在 10–500，取整到 10。
- 只在编辑页显示“建议 80 步，采用？”；**不自动修改**用户设置。
- 数据不足（<3 次）不显示建议。
- 验收：纯函数单测（0/1/3/7 条数据、异常值）；采用后写入 goalSteps。

### 5.3 防作弊

- 正常模式：继续用 `TYPE_STEP_COUNTER`（系统计步，难伪造）。
- 降级模式：`ShakeCounter` 增加“有效摇晃窗口”——30 次必须分布在 ≥8 秒内，
  且单次幅度超过阈值；连续等间隔注入应被拒绝（模拟器测试要改）。
- 充电 + 静止检测：可选，作为提高音量的启发式，不作为停止条件。
- 所有阈值放常量/设置，便于真机调参；策略保持纯函数、可单测。
- 验收：摇晃过快/过慢/等间隔被拒；正常摇晃通过；单测覆盖边界。

### 5.4 渐进音量 + 每闹钟铃声/振动

- Room v3/v4：`alarms` 增加 `sound_uri TEXT`、`vibration_pattern TEXT`、
  `volume_ramp_seconds INTEGER`（默认 0=关闭）。
- 通知渠道不可变：保留单一 channel，声音在 `AlarmService` 的音频播放器上按闹钟设置；
  通知静音，避免双声。
- 渐进：30 秒从 30% 到用户媒体音量的 100%，不超过系统音量；DND 行为不变。
- 编辑页提供铃声选择（`RingtoneManager`）、振动模式、渐进开关。
- 验收：每闹钟独立声音；渐进曲线单测；迁移测试；预览使用相同设置。

### 5.5 唤醒后小结

- 停止后展示：本次用时、步数、snooze 次数、连续天数 + 用户自定义一句“今日第一件事”。
- 不引入网络/定位/天气（避免权限与隐私成本）；纯本地。
- 验收：完成/降级/紧急三种结束都有小结；可关闭；不阻塞返回。

---

## 6. P2 可见性与生态

### 6.1 桌面小组件 + 快捷设置磁贴

- Glance 依赖；小组件显示下次闹钟、目标步数、开关；点击进编辑。
- QS Tile：显示下次闹钟，短按进应用，长按跳过下一次（复用 4.2）。
- 数据通过 Room/DataStore Flow 更新；勿用轮询。
- 验收：改闹钟后 1 秒内刷新；重启后正确；4×2 和 2×2 两种尺寸。

### 6.2 自动化接口 / 智能家居

- 定义签名权限 `com.mustwake.alarm.permission.CONTROL`，只导出受保护的 receiver：
  `SKIP_NEXT`、`DISABLE_NEXT`、`TEST_RING`、`SNOOZE`。
- 文档给出 `adb`/Tasker/快捷指令示例；不导出 service，不开任意 action。
- 智能家居：响铃/停止时发本地广播或 HTTP webhook（用户配置 URL，失败静默重试一次）。
- 验收：未授权应用调用被拒；文档示例可跑通；webhook 超时不阻塞响铃。

### 6.3 节假日/调休

- 先做手动“跳过下一次”（4.2）；自动调休依赖数据源，放后。
- 可选：读取系统日历全天事件（需要 `READ_CALENDAR`）或内置年度 CN 节假日表；
  默认关闭，用户显式开启。
- 验收：节假日不响、次日恢复；跨年表更新有文档。

---

## 7. P3 大模块

### Wear OS 伴侣

- 新 `:wear` 模块；手机侧 `DataClient` 推送“响铃中/步数/目标/剩余”，手表显示进度与停止按钮。
- 停止仍由手机 `StopPolicy` 判定；手表只发请求，避免双端状态分叉。
- 独立验收：手表安装、消息延迟、断连重连、低电模式。
- 依赖 P1 统计与稳定协议，不建议早于 M2。

---

## 8. 数据与迁移

| 版本 | 变更 | 迁移测试 |
|---|---|---|
| v2（现状） | sessions/events | 已有 |
| v3 | `alarms.skip_until`、`sound_uri`、`vibration_pattern`、`volume_ramp_seconds` | 新增 2→3 |
| v4（如需） | 统计缓存/索引 | 按需 |

- 所有新列给默认值，旧行可读；导出 JSON 带 `schemaVersion`，导入做兼容。
- `AlarmConstraints` 增加新字段校验；读取 `sanitized()` 回退默认。

---

## 9. 里程碑

| 里程碑 | 内容 | 出口标准 |
|---|---|---|
| M1 | P0 全部 + T8 真机 | 122+ 测试通过；真机 A–F 记录；Baseline Profile 有数据 |
| M2 | P1 全部 | 统计/自适应/防作弊/声音/小结 单测齐全 |
| M3 | P2 全部 | 小组件/磁贴/自动化/节假日 可用 |
| M4 | Wear OS | 独立模块通过断连/低电测试 |

每完成一个功能：更新 `HANDOFF.md` 状态、对应 `docs/*.md`、一个提交。

---

## 10. 风险

- **Room 迁移**：v2→v3 是硬门槛，迁移测试先写；失败会丢闹钟。
- **英文抽取**：硬编码中文遍布页面，改动面大，放功能稳定后单独做。
- **防作弊误判**：阈值过严会让真实用户停不掉；必须可调、可观测。
- **通知渠道不可变**：每闹钟声音不能在 channel 上做，避免建大量渠道。
- **小组件/磁贴刷新**：不要轮询；用 Flow；注意进程被杀后的首次刷新。
- **FSI/HyperOS**：真机行为仍未验证，任何“响铃相关”改动都要回归 T8。
- **Wear OS**：独立模块与协议成本高，不要在 M1/M2 前启动。

---

## 11. 下一个 PR 建议

从 **试响** 开始（4.1）：不碰数据库、不依赖真机、能直接用于 T8 调试。

1. `AlarmService` 加 `EXTRA_PREVIEW`，跳过 session/history；
2. `RingingActivity` 支持预览，停止直接 finish；
3. 设置页入口 + 文案；
4. Robolectric：预览不写 `alarm_sessions`/`alarm_events`；
5. 模拟器手工验证：试响 10 秒、正式闹钟不受影响；
6. 提交 `feat(ringing): add 10-second test ring`。

之后做 **跳过下一次**（4.2），先写 v2→v3 迁移测试，再动调度器。

---

## 12. 交接 checklist

- [ ] 读 `HANDOFF.md` 第 1、5G、6 节和本文件；
- [ ] `cd E:\MustWake\mustwake && .\gradlew.bat :androidApp:assembleDebug test` 确认 122 测试；
- [ ] 有真机：`adb devices`，按 `docs/testing.md` 补 T8 剩余项；无真机：用 AVD
      `mustwake36`（E 盘）做回归；
- [ ] 确认没有把“明确不做”的功能加回来；
- [ ] 从 4.1 开始，按 M1→M4 顺序推进。
