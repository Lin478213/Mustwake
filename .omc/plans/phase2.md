# MustWake Phase 2 实施计划

## Goal

在 T0–T7 基线上完成 P0 发布硬化、P1 差异化、P2 可见性/生态与工程质量；T8 真机是 M1 硬门禁；Wear OS 后置 M4；T9 root 可选。

- spec：`.omc/specs/deep-interview-phase2.md`
- 输入交接：`HANDOFF-PHASE2.md`
- 基线：`db2e80f`（HEAD `1e3d270`），122 tests，APK 16,963,136 bytes，T8 仅模拟器预验收
- 当前无真机：P0 响铃链路暂停；设备无关准备可先做

## Milestones

| 里程碑 | 内容 | 出口 |
|---|---|---|
| **M1-A** | 设备无关 P0 准备：备份/恢复、Roborazzi、Baseline Profile、a11y/字符串基础、可观测性 | 构建 + 单测 + 截图/基准脚本通过；不宣称 M1 |
| **G0** | 真机到位：T8 基线 + 环境确认 | `adb devices` 有设备；T8 基线记录 |
| **M1-B** | 试响、跳过下一次、英文完成、T8 A–F | P0 全项 + 真机 PASS；M1 关闭 |
| **M2** | P1 差异化：统计/streak、自适应、防作弊、声音/渐进、小结、玻璃基准 | P1 全项单测/基准；迁移测试 |
| **M3** | P2：小组件/QS、自动化、节假日（可选）、T9 root（可选） | P2 验收；权限安全 |
| **M4** | Wear OS 伴侣 | 独立模块验收；断连/低电 |

顺序：**M1-A → G0 → M1-B → M2 → M3 → M4**。M2 不早于 M1 关闭；Wear 不早于 M2。

## Task graph

| ID | 任务 | 规模 | 依赖 | 主要落点 | 验证 |
|---|---|---|---|---|---|
| M1-A1 | 备份/恢复 JSON | S–M | — | `:data` BackupCodec、设置页 SAF | round-trip/坏数据单测 |
| M1-A2 | Roborazzi 截图测试 | S–M | — | `androidApp/src/test`、baselines | `testDebugUnitTest` |
| M1-A3 | Baseline Profile | S–M | — | 新 `:baselineprofile`、Macrobenchmark | before/after P50/P90 |
| M1-A4 | a11y + 字符串基础设施 | M | — | `shared/.../ui/**`、`strings.xml` | TalkBack/200% 字体 |
| M1-A5 | 本地崩溃/ANR 可观测性 | S–M | — | app 启动、环形日志 | 模拟崩溃/ANR |
| **G0** | 真机门禁 | S | M1-A 可先做 | `docs/testing.md` | `adb devices` + T8 基线 |
| M1-B1 | 试响 10 秒 | S | G0 | `AlarmService`、`RingingActivity`、设置页 | 不写库；正式闹钟不受影响 |
| M1-B2 | 跳过下一次（Room v3） | S–M | G0 | `:data`、`NextTriggerCalculator`、列表/编辑 | 迁移 2→3；单次/重复 |
| M1-B3 | 英文完成 | M | M1-B1/B2 | `values-en`、日期/星期 | 英文无中文残留 |
| M1-B4 | T8 真机 A–F | M | M1-B1/B2/B3 | 真机、`docs/testing.md` | 全项 PASS + 证据 |
| M1-B5 | M1 关闭 | S | M1-A/B4 | 回归、APK、文档、提交 | 122+ tests；APK |
| M2-1 | 唤醒统计 + streak | M | M1 | `:data` 聚合、历史页 | 固定事件序列/跨周/时区 |
| M2-2 | 自适应目标 | S–M | M2-1 | 纯函数、编辑页建议 | 0/1/3/7 条边界 |
| M2-3 | 防作弊 | M–L | M1 | `StopPolicy`、`ShakeCounter` | 过快/过慢/等间隔被拒 |
| M2-4 | 渐进音量 + 每闹钟声音/振动 | S–M | M1-B2(v3) | `AlarmNotifications`、`AlarmService` | 每闹钟独立；曲线单测 |
| M2-5 | 唤醒后小结（纯本地） | S–M | M2-1 | `RingingScreen` | 完成/降级/紧急；可关 |
| M2-6 | 玻璃帧率基准 | S–M | M1 | Macrobenchmark、T6 | before/after；低端机 |
| M2-7 | P1 新字符串英文 | S | M2-1..5 | `values-en` | 新页面无中文残留 |
| M2-8 | M2 关闭 | S | M2-1..7 | 回归、文档、提交 | 测试/基准 |
| M3-1 | 小组件 + QS 磁贴 | M | M1-B2 | 新 `:widget` 或 app、Glance | 1 秒刷新；4×2/2×2 |
| M3-2 | 自动化接口/智能家居 | M | M1-B1/B2 | manifest 受保护 receiver、webhook | 未授权被拒；adb/Tasker |
| M3-3 | 节假日/调休（可选最后） | M | M1-B2 | `:data` 日历规则 | 默认关；次日恢复 |
| M3-4 | T9 root 辅助（可选） | S–M | M1 | RootHelper/allowlist | 拒绝 su 回退；可撤销 |
| M3-5 | M3 关闭 | S | M3-1..4 | 回归、文档、提交 | P2 验收 |
| M4-1 | Wear OS 伴侣 | L | M2-1 | 新 `:wear`、DataClient | 断连/低电/延迟 |

### Room v3 决策

**建议 v3 一次加齐**以下列，减少后续迁移：

- `alarms.skip_until INTEGER DEFAULT NULL`
- `alarms.sound_uri TEXT DEFAULT NULL`
- `alarms.vibration_pattern TEXT DEFAULT NULL`
- `alarms.volume_ramp_seconds INTEGER NOT NULL DEFAULT 0`

理由：P0-2 需要 `skip_until`；P1-4 需要其余三列。一次 `2→3` + `MigrationTestHelper`，旧行可读；后续只有统计索引才需要 v4。

## M1-A（设备无关，现在可开始）

### M1-A1 备份/恢复（建议第一个 PR）

- `:data`：`BackupCodec`（kotlinx.serialization），`schemaVersion=1`；导出 `alarms` + `AppSettings`；不导出 session/history。
- 设置页：`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`；文件名 `mustwake-backup-YYYYMMDD.json`。
- 导入：版本/范围/掩码/时间校验；非法项跳过并报告；替换需二次确认（单事务提交，失败全量回滚）；合并按 `(hour, minute, repeatDaysMask, label)` 去重（同键保留已有，报告逐项明细）——以 `phase2-execution.md` M1-A1 步骤 5 为准。
- 测试：round-trip、空备份、坏 JSON、未知版本、越界值、重复项。
- 验证：`.\gradlew.bat test`；设置页手工导入/导出。

### M1-A2 Roborazzi

- 加 Roborazzi + Robolectric；固定字体缩放/深色/时间/时区。
- 覆盖：空列表、列表有数据、编辑页、响铃页（正常/降级）、历史页、设置页、4 种底栏/玻璃组合。
- 只比较关键区域，避免模糊/阴影导致脆弱。
- 验证：`:androidApp:testDebugUnitTest`；4 组合基线图。

### M1-A3 Baseline Profile

- 新 `:baselineprofile` 模块；`androidx.benchmark.macro`。
- 覆盖 `MainActivity` 与 `RingingActivity` 冷启动；profile 放 `androidApp/src/main/baselineProfiles`。
- 记录 before/after P50/P90；CI 不跑设备基准，保留脚本。
- 验证：APK 含 profile；Macrobenchmark 报告。

### M1-A4 a11y + 字符串基础设施

- 所有图标按钮 `contentDescription`；步数/进度状态描述；长按提示；对比度。
- 建 `strings.xml`，先把**现有** P0 页面硬编码中文迁入；不急于一次性翻译所有 P1 文案。
- 200% 字体检查；避免固定高度。
- 验证：TalkBack 手工路径；截图。

### M1-A5 可观测性

- 本地环形日志（响铃/服务/步数关键路径）；全局 crash/ANR handler；可选导出文本；默认不上传。
- 验证：模拟崩溃/ANR；日志可读；不影响响铃。

## G0 + M1-B（设备到位后）

### G0

- `adb devices` 确认；安装当前 APK；跑 `docs/testing.md` 模拟器 14 项 + 真机基线。
- 重点：FSI、精确闹钟、ACTIVITY_RECOGNITION、HyperOS 电池/自启动、DND。
- 记录设备型号/系统/权限状态；没有设备则 M1-B 全部阻塞，M1 不关闭。

### M1-B1 试响

- `AlarmService` 加 `EXTRA_PREVIEW=true`；预览不写 session/event、不取消/重排真实闹钟；10 秒自动结束（debug 30 秒）。
- `RingingActivity` 支持预览，停止直接 finish；通知/页面标“试响”。
- 真实闹钟优先：预览用独立 `previewSessionId` 或先 stopSelf。
- 测试：Robolectric 预览不写库；模拟器手工验证正式闹钟不受影响。

### M1-B2 跳过下一次

- Room v3 迁移（见上）；`NextTriggerCalculator` 支持 `skipUntil`；列表显示“已跳过 · 下次 X”；编辑页“恢复下一次”。
- 过期自动失效（读取时判断）；重启保持。
- 测试：单次/重复、跨天、过期、迁移 2→3。

### M1-B3 英文

- 所有 P0 页面字符串迁入 `values-en`；日期/星期 `DateTimeFormatter`；用户输入不翻译。
- 验证：英文环境全流程无中文残留；截图。

### M1-B4 T8

- 真机按 spec A–F 逐项；记录 PASS/FAIL、截图、logcat、`dumpsys alarm`、历史。
- 重点：FSI 撤销、ACTIVITY_RECOGNITION 撤销、snooze 杀进程、重启、missed、HyperOS。
- 失败项修复后重跑；不通过不关闭 M1。

## M2 — 差异化

### M2-1 统计 + streak

- `:data` `WakeStatsRepository`：成功率、平均停止用时、平均步数、snooze、连续天数。
- `ZoneId` 处理时区/DST；跨周测试；空态。
- UI 历史页顶部卡片或统计标签；不新增表。

### M2-2 自适应目标

- 最近 7 次 `COMPLETED` 中位数 +10%，10–500，取整 10；<3 次不显示；只建议不自动改。
- 纯函数单测。

### M2-3 防作弊

- `ShakeCounter`：30 次分布 ≥8 秒，单次幅度阈值；等间隔注入拒绝。
- 充电+静止只作音量启发式，不作停止条件。
- 阈值常量/设置；纯函数单测。

### M2-4 声音/振动/渐进

- 使用 v3 列；通知 channel 不变，声音在 `AlarmService` 播放器层；通知静音避免双声。
- 30 秒 30%→100%，不超系统音量；DND 不变。
- 编辑页铃声/振动/渐进；预览同样生效。
- 迁移测试；曲线单测。

### M2-5 唤醒后小结

- 完成/降级/紧急三种路径：用时、步数、snooze、streak、自定义一句话。
- 纯本地，无网络/定位/天气；可关闭；不阻塞返回。

### M2-6 玻璃帧率基准

- Macrobenchmark 量化 T6 玻璃；低端机/模拟器；before/after。
- 不达标时给 T6 降级阈值/回退策略提供数据。

## M3 — 可见性/生态

### M3-1 小组件 + QS

- Glance；下次闹钟/目标/开关；点击编辑；QS 短按进应用、长按跳过。
- Flow 更新，不轮询；4×2/2×2；重启/进程被杀后首次刷新。

### M3-2 自动化接口

- 签名权限 `com.mustwake.alarm.permission.CONTROL`；受保护 receiver：`SKIP_NEXT`、`DISABLE_NEXT`、`TEST_RING`、`SNOOZE`。
- 文档 adb/Tasker/快捷指令；webhook 可选，超时不阻塞响铃。

### M3-3 节假日/调休

- 先手动跳过；自动调休最后做，默认关闭；系统日历或内置年度表；跨年更新文档。

### M3-4 T9 root（可选）

- allowlist 命令；逐条结果；可撤销；拒绝 su 回退；不做 daemon/轮询/root socket。

## 优先级矩阵（按用户清单）

### 发布前（P0）

| 功能 | 成本 | 价值 | 计划位置 |
|---|---|---|---|
| 试响 / 预览 | S | 用户信任 + T8 调试工具 | M1-B1 |
| 跳过下一次 | S–M | 真实周末场景 | M1-B2 |
| 备份/恢复 | S–M | 换机不丢闹钟 | M1-A1 |
| 无障碍 + 英文 | M | 可访问性/国际化 | M1-A4/M1-B3 |
| Baseline Profile | S–M | 响铃页冷启动可信度 | M1-A3 |
| 截图/黄金图测试 | S–M | UI 回归兜底 | M1-A2 |
| 崩溃/ANR 可观测性 | S–M | 发布后可诊断 | M1-A5 |

### 差异化（P1）

| 功能 | 成本 | 计划位置 |
|---|---|---|
| 统计 + streak | M | M2-1 |
| 自适应目标 | M | M2-2 |
| 防作弊 | M–L | M2-3（核心信誉） |
| 渐进音量 + 每闹钟声音/振动 | S–M | M2-4 |
| 唤醒后小结（纯本地） | S–M | M2-5 |
| 玻璃帧率基准 | S–M | M2-6 |

### 可见性/生态（P2）

| 功能 | 成本 | 计划位置 |
|---|---|---|
| 小组件 + QS | M | M3-1 |
| 自动化/智能家居 | M | M3-2 |
| 节假日/调休 | M | M3-3（最后、可选） |
| Wear OS | L | M4（后置） |

## Risks and mitigations

| 风险 | 影响 | 缓解 |
|---|---|---|
| Room v2→v3 迁移 | 丢闹钟 | 先写迁移测试；一次加齐列；旧行默认值 |
| 无真机 | M1 无法关闭 | G0 门禁；M1-A 先做；不宣称发布 |
| 试响与真实闹钟冲突 | 预览抢 session | 独立 `previewSessionId`；真实优先 |
| 英文抽取面大 | 进度拖长 | a11y/字符串基础先做；P1 新文案同步 `values-en` |
| 防作弊误判 | 真实用户停不掉 | 阈值可调/可观测；纯函数测试；真机调参 |
| 通知渠道不可变 | 双声/改不了 | 单一 channel，播放器层做声音 |
| FSI/HyperOS | 响铃不显示 | 每次响铃改动回归 T8；真机证据 |
| 小组件刷新 | 数据不更新 | Flow，不轮询；进程重启首次刷新测试 |
| Glance/Wear 依赖 | 构建复杂 | 独立模块；先 M2 稳定再 M3/M4 |
| 基准测试设备依赖 | CI 不可跑 | 本地脚本；CI 只跑单测/截图 |

## Verification

- 单测：`.\gradlew.bat test`（当前 122；每任务新增）。
- 截图：`.\gradlew.bat :androidApp:testDebugUnitTest`。
- 构建：`.\gradlew.bat :androidApp:assembleDebug`。
- 基准：`:baselineprofile:connectedBenchmarkAndroidTest`（设备/模拟器）。
- 真机：`adb install -r ...`；`adb logcat -d -s MustWake:* AndroidRuntime:E`；`adb shell dumpsys alarm`。
- 每个任务：一个 git 提交；更新 `HANDOFF.md`/对应 `docs/*.md`/本计划状态。

## First PRs (recommended)

1. **M1-A1 备份/恢复**：纯数据/设置，不碰响铃，设备无关，用户可见。
2. **M1-A2 Roborazzi**：建立 UI 安全网，后续 P0/P1 页面改动有回归。
3. **M1-A3 Baseline Profile**：响铃页冷启动可信度。
4. **M1-A4 a11y/字符串基础**。
5. **M1-A5 可观测性**。
6. G0 真机 → **M1-B1 试响** → **M1-B2 跳过** → **M1-B3 英文** → **M1-B4 T8**。

## Definition of done (Phase 2)

- M1：P0 全项 + T8 真机 A–F；122+ tests；截图/基准/可观测性；APK。
- M2：P1 全项；统计/防作弊/声音/小结单测；玻璃基准。
- M3：P2 全项（节假日可选）；自动化权限安全；T9 可选。
- M4：Wear 独立验收。
- 每个功能有文档、测试、提交；没有“明确不做”的功能被加回来。
