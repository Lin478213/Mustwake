# MustWake Phase 2 — 功能扩展规格（deep-interview）

## Metadata

- 阶段：Phase 2（功能扩展），基线 `db2e80f`，当前 HEAD `1e3d270`
- 基线状态：T0–T7 完成；122 tests；APK 16,963,136 bytes；T8 仅模拟器预验收，真机未做
- 阈值：0.20（source: default）
- 最终歧义：0.16（16%）
- 轮次：4
- 组件：P0 发布硬化、P1 差异化、P2 可见性/生态、P3 Wear、T8 真机、T9 root、工程质量
- 输入：`HANDOFF-PHASE2.md` + 用户 2026-10-19 的功能清单

## Goal

在不破坏 T0–T7 基线的前提下，把 MustWake 从“可运行的原型”推进到“可发布、可信任、可差异化”的产品：

1. **M1 发布硬化 + T8**：试响、跳过下一次、备份/恢复、无障碍 + 英文、Baseline Profile、截图测试、真机验收。
2. **M2 差异化**：唤醒统计 + streak、自适应目标、防作弊、渐进音量 + 每闹钟声音/振动、唤醒后小结。
3. **M3 可见性/生态**：桌面小组件 + QS 磁贴、自动化接口/智能家居、节假日/调休（最后、可选）。
4. **M4 Wear OS**：手表伴侣，独立模块，后置。
5. **T9 Root 辅助**：可选，标准路径优先，不做 daemon。

## Users and situations

- 主要用户：需要“必须起身走动才能关闹钟”的重度赖床者。
- 设备：小米/红米 Android 14+ 为主，minSdk 26。
- 典型场景：睡前设闹钟 → 响铃 → 锁屏/后台计步 → 达标停止；周末临时跳过；换机备份；英语用户；低端机玻璃性能。
- 非目标用户：需要账号/云同步、社交排名、睡眠周期智能唤醒、Wear OS 强依赖的用户。

## Decisions locked in this interview

| # | 决策 | 说明 |
|---|---|---|
| 1 | 里程碑顺序 | **M1=P0+T8 → M2=P1 → M3=P2 → M4=Wear**；Wear 后置 |
| 2 | T8 门禁 | **T8 是 M1 的硬门禁**；无真机不关闭 M1、不宣称发布完成 |
| 3 | P0 暂停范围 | 设备到位前**只暂停响铃相关 P0**（试响、跳过、T8）；允许设备无关的 P0 准备（备份、Roborazzi、Baseline Profile、a11y、文档、可观测性） |
| 4 | 试响时长 | **10 秒自动结束**；debug 构建可配 30 秒，不暴露给普通用户 |
| 5 | 唤醒后小结 | **纯本地**：用时/步数/snooze/streak/自定义一句话；**不含天气**，不新增网络/定位权限 |
| 6 | 不做 | 账号/云同步、广告/核心功能付费墙、社交排行榜、睡眠周期智能唤醒 |
| 7 | Root | 可选辅助（pm grant/appops/deviceidle），**不做 daemon/轮询/root socket** |

## Topology

1. **P0 发布硬化**：试响、跳过下一次、备份/恢复、无障碍 + 英文。
2. **P1 差异化**：统计 + streak、自适应目标、防作弊、渐进音量 + 每闹钟声音/振动、唤醒小结。
3. **P2 可见性/生态**：小组件 + QS、自动化接口、节假日/调休。
4. **P3 Wear**：手表伴侣（后置）。
5. **T8 真机**：A–F 验收门禁。
6. **T9 Root**：可选辅助。
7. **工程质量**：Baseline Profile、Roborazzi、玻璃帧率、崩溃/ANR 可观测性。

## Functional requirements

### P0 — 发布硬化

#### P0-1 试响 / 预览（S）

- 设置页“试响 10 秒”，走完整链路：通知 → 全屏页 → 步数/摇晃门禁 → 停止。
- **不写** `alarm_sessions` / `alarm_events`；不影响正式闹钟；不取消/重排真实闹钟。
- 预览与真实闹钟同时触发时，**真实闹钟优先**；预览用独立 `previewSessionId` 或被停止，避免 `currentSessionId` 冲突。
- 10 秒自动结束；debug 构建可配 30 秒。
- 预览通知/全屏页标注“试响”，停止后直接 `finish`，不跳历史。
- 验收：无 session/event 行；正式闹钟不受影响；30 秒 debug 可配；Robolectric 覆盖“预览不写库”。

#### P0-2 跳过下一次（S–M）

- 只跳过下一次触发，之后自动恢复；列表显示“已跳过 · 下次 X”。
- Room v3：`alarms.skip_until INTEGER`（被跳过的触发时间 epoch millis）。
- `NextTriggerCalculator` 支持 `skipUntil`：计算结果等于 skip 时取下一次；跨天/重复正确；过期自动失效（读取时判断，不必写库）。
- 编辑页提供“恢复下一次”。
- 迁移 `2→3` + `MigrationTestHelper`；旧数据不丢。
- 验收：单次/重复各一条；重启后有效；过期失效；迁移测试通过。

#### P0-3 备份 / 恢复（S–M）

- `:data` `BackupCodec`（kotlinx.serialization），`schemaVersion`；导出 `alarms` + `AppSettings`；默认不导出 session/history。
- SAF：`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`，不申请存储权限。
- 导入校验：版本、步数/分钟范围、重复掩码、时间；非法项跳过并报告；替换/合并二选一，替换二次确认。
- 解析失败不崩溃，返回可读错误。
- 验收：导出→清空→导入一致；坏 JSON/越界/未知版本有错误；round-trip + 坏数据单测。

#### P0-4 无障碍 + 英文（M）

- a11y：图标按钮 `contentDescription`；步数/进度状态描述；长按停止操作提示；对比度检查；200% 字体不截断。
- 字符串：硬编码中文逐步迁到 `strings.xml`；`values-en` 英文；日期/星期用 `DateTimeFormatter` 本地化。
- 顺序：先做 a11y 基础，功能稳定后做英文抽取，避免 P0/P1 功能反复改字符串。
- 验收：TalkBack 完成“建闹钟→响铃→摇晃/长按停止”；200% 字体无遮挡；英文环境无中文残留（用户输入除外）。

#### P0-5 Baseline Profile（S–M）

- 新 `:baselineprofile` 模块 + `androidx.benchmark.macro`。
- 覆盖 `MainActivity` 冷启动、`RingingActivity` 冷启动；profile 放 `androidApp/src/main/baselineProfiles`。
- 记录 before/after P50/P90；CI 不做设备基准，保留脚本。
- 验收：profile 随 APK 打包；Macrobenchmark 有 before/after；响铃页冷启动无回归。

#### P0-6 截图/黄金图测试（S–M）

- Roborazzi + Robolectric；覆盖：列表空态/有数据、编辑页、响铃页（正常/降级）、历史页、设置页、4 种底栏/玻璃组合。
- 固定字体缩放、深/浅色、时间/时区；图片入 `androidApp/src/test/screenshots`。
- 只对关键区域做像素对比，避免阴影/模糊导致全图脆弱。
- 验收：`.\gradlew.bat :androidApp:testDebugUnitTest` 含截图测试；4 组合有基线图；UI 改动有更新流程。

#### P0-7 崩溃/ANR 可观测性（S–M，工程质量）

- 隐私友好的本地环形日志（响铃/服务/步数关键路径）；可选导出文本；默认不上传。
- 崩溃/ANR 捕获：全局 handler + 本地记录；不引入第三方分析 SDK。
- 验收：模拟崩溃/ANR 有本地记录；导出可读；不影响响铃。

### P1 — 差异化

#### P1-1 唤醒统计 + streak（M）

- `:data` `WakeStatsRepository`：按周聚合 `alarm_events` + `alarm_sessions`：成功率、平均停止用时、平均步数、snooze 次数、连续达成天数。
- `ZoneId` 处理时区/DST；跨周测试；空数据空态。
- UI 放历史页顶部卡片或“统计”标签；**不新增数据库表**。
- 验收：固定事件序列统计精确；跨周/时区测试；历史清理后不崩。

#### P1-2 自适应目标（M）

- 最近 7 次 `COMPLETED` 实际步数中位数 +10%，限制 10–500，取整到 10。
- 只在编辑页显示“建议 80 步，采用？”；**不自动修改**。
- 数据 <3 次不显示；验收纯函数单测（0/1/3/7 条、异常值）；采用后写 `goalSteps`。

#### P1-3 防作弊（M–L）

- 正常模式继续用 `TYPE_STEP_COUNTER`。
- 降级模式 `ShakeCounter`：30 次必须分布在 ≥8 秒，单次幅度超阈值；等间隔注入被拒。
- 充电 + 静止检测可选，作为提高音量启发式，**不作为停止条件**。
- 阈值放常量/设置；策略纯函数可单测。
- 验收：摇晃过快/过慢/等间隔被拒；正常摇晃通过；边界单测。

#### P1-4 渐进音量 + 每闹钟铃声/振动（S–M）

- Room v3/v4：`sound_uri TEXT`、`vibration_pattern TEXT`、`volume_ramp_seconds INTEGER`（默认 0=关）。
- 通知渠道不可变：保留单一 channel；声音在 `AlarmService` 播放器按闹钟设置；通知静音避免双声。
- 渐进：30 秒从 30% 到用户媒体音量 100%，不超过系统音量；DND 不变。
- 编辑页：铃声选择（`RingtoneManager`）、振动模式、渐进开关。
- 验收：每闹钟独立声音；渐进曲线单测；迁移测试；预览用相同设置。

#### P1-5 唤醒后小结（S–M）

- 停止后展示：本次用时、步数、snooze 次数、连续天数 + 用户自定义“今日第一件事”。
- **纯本地**：不引入天气/定位/网络；可关闭；不阻塞返回。
- 完成/降级/紧急三种结束都有小结。
- 验收：三种路径都有；关闭后不显示；无网络权限。

#### P1-6 玻璃帧率基准（S–M，工程质量）

- Macrobenchmark 量化 T6 玻璃（blur/backdrop）在低端机的帧率/掉帧。
- 记录 before/after；给 T6 性能降级阈值提供数据。
- 验收：有基准报告；低端机不崩；不达标时有降级策略。

### P2 — 可见性/生态

#### P2-1 桌面小组件 + QS 磁贴（M）

- Glance；小组件显示下次闹钟、目标步数、开关；点击进编辑。
- QS Tile：下次闹钟；短按进应用，长按跳过下一次（复用 P0-2）。
- Room/DataStore `Flow` 更新；**不轮询**。
- 验收：改闹钟 1 秒内刷新；重启正确；4×2/2×2 两种尺寸。

#### P2-2 自动化接口 / 智能家居（M）

- 签名权限 `com.mustwake.alarm.permission.CONTROL`；只导出受保护 receiver：`SKIP_NEXT`、`DISABLE_NEXT`、`TEST_RING`、`SNOOZE`。
- 文档给 adb/Tasker/快捷指令示例；不导出 service，不开任意 action。
- 智能家居：响铃/停止本地广播或 HTTP webhook（用户配置 URL，失败静默重试一次）；超时不阻塞响铃。
- 验收：未授权调用被拒；文档示例可跑；webhook 超时不阻塞。

#### P2-3 节假日/调休（M，最后、可选）

- 先做手动“跳过下一次”；自动调休依赖数据源，放最后。
- 可选：系统日历全天事件（`READ_CALENDAR`）或内置年度 CN 节假日表；默认关闭、显式开启。
- 验收：节假日不响、次日恢复；跨年表更新有文档。

### P3 — Wear OS（L，后置）

- 新 `:wear` 模块；手机侧 `DataClient` 推送“响铃中/步数/目标/剩余”；手表显示进度与停止按钮。
- 停止仍由手机 `StopPolicy` 判定；手表只发请求。
- 验收：手表安装、消息延迟、断连重连、低电模式；不早于 M2。

### T9 — Root 辅助（可选）

- allowlist：`pm grant`、`appops set`、`deviceidle whitelist`；逐条显示结果。
- 可撤销；失败回退标准路径；不做 daemon/轮询/root socket。
- 验收：拒绝 su 时 A–F 仍通过；开启后增强生效。

## Constraints

- Android 单平台；minSdk 26；targetSdk 36；compileSdk 37；工具链固定在 `E:\MustWake\toolchain`。
- 每任务一个 git 提交；汇报只写实际跑过的命令。
- Miuix import 只允许在 `shared/.../ui/design/MwComponents.kt`；页面只用 `Mw*`。
- 数据层放 `:data`，不要塞进 KMP `shared`。
- 写入 `require`、读取 `sanitized()`；Room 迁移必须有测试。
- 不降 compileSdk、不移除 Miuix、不用 daemon/轮询保活。
- 不引入账号/云同步/广告/社交/睡眠周期。
- 唤醒小结不引入网络/定位/天气。
- 通知渠道不可变：每闹钟声音在播放器层做，不建大量 channel。
- 无真机时 P0 响铃链路暂停；设备无关准备可做；M1 不关闭。

## Acceptance criteria

### M1（P0 + T8）

- [ ] 试响 10 秒链路完整；不写 session/event；正式闹钟不受影响；debug 30 秒可配。
- [ ] 跳过下一次单次/重复正确；重启有效；过期失效；v2→v3 迁移测试通过。
- [ ] 备份/恢复 round-trip；坏数据有可读错误；SAF 无存储权限。
- [ ] a11y：TalkBack 完成建闹钟→响铃→停止；200% 字体不截断；英文无中文残留。
- [ ] Baseline Profile 随 APK 打包；before/after 数据；响铃页冷启动无回归。
- [ ] Roborazzi 覆盖关键页面与 4 组合；CI/本地可跑。
- [ ] 崩溃/ANR 本地记录可导出。
- [ ] 真机 T8 A–F 全部 PASS，记录证据；无真机则 M1 不关闭。

### M2（P1）

- [ ] 统计/streak 对固定事件序列精确；跨周/时区测试。
- [ ] 自适应目标纯函数测试；不自动改。
- [ ] 防作弊：过快/过慢/等间隔被拒；正常摇晃通过。
- [ ] 每闹钟声音/振动/渐进；迁移测试；预览同样生效。
- [ ] 小结完成/降级/紧急三条路径；无网络权限；可关闭。
- [ ] 玻璃帧率基准有报告；低端机不崩。

### M3（P2）

- [ ] 小组件/QS 1 秒内刷新；重启正确；4×2/2×2。
- [ ] 自动化 receiver 受签名权限保护；未授权被拒；adb/Tasker 示例可跑。
- [ ] 节假日（如做）默认关闭；不响、次日恢复；跨年文档。
- [ ] T9 root 可选完成（若做）。

### M4（Wear）

- [ ] 手表显示进度/停止；断连重连/低电通过；手机仍是唯一判定方。

## Assumptions / open questions

| # | 假设/问题 | 当前处理 |
|---|---|---|
| 1 | Room v3 是否一次加 skip+sound+vibration+ramp | **建议 v3 一次加齐**，减少迁移；P0 只用 skip，P1 用其余 |
| 2 | 英文抽取时点 | M1 先 a11y/字符串基础设施；P0 功能稳定后做英文；P1 新字符串同步 `values-en` |
| 3 | 节假日数据源 | 先手动跳过；自动调休最后做，默认关闭 |
| 4 | 小组件/QS 放 `:widget` 还是 `androidApp` | plan 阶段按 Glance 依赖决定，优先独立 `:widget` |
| 5 | T9 root 是否本轮做 | 可选；不阻塞 M1–M3 |
| 6 | Wear 是否本轮做 | 后置 M4，不早于 M2 |
| 7 | 试响 debug 30 秒如何配置 | `BuildConfig.DEBUG` 常量/设置，不暴露普通用户 |
| 8 | 统计是否导出 | 备份默认不含 session/history；统计不导出 |

## Decision log

| Round | 问题 | 回答 | 影响 |
|---|---|---|---|
| 0 | Phase 2 范围/顺序 | M1=P0+T8 → M2=P1 → M3=P2；Wear 后置 | 里程碑锁定 |
| 1 | 试响时长 | 10 秒自动结束；debug 可配 30 秒 | P0-1 明确 |
| 2 | 唤醒小结 | 纯本地，不含天气 | 零网络/定位权限 |
| 3 | T8 门禁 | T8 硬门禁；无真机不关闭 M1 | 发布可信度优先 |
| 4 | P0 暂停范围 | 设备到位前只暂停响铃相关 P0；允许设备无关准备 | M1 可先做备份/Roborazzi/Baseline/a11y/文档 |

## Handoff

- 计划：`.omc/plans/phase2.md`
- 交接版：`HANDOFF-PHASE2-PLAN.md`
- 先做 M1-A（设备无关准备），G0 真机到位后做 M1-B（试响/跳过/T8），再做 M2/M3；Wear 后置。
