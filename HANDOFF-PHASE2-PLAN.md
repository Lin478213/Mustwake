# MustWake Phase 2 — 执行计划（交接版）

> 生成时间：2026-10-19
> 基线：`db2e80f`（当前 HEAD `1e3d270`），T0–T7 完成，122 tests，T8 仅模拟器预验收
> 规格：`.omc/specs/deep-interview-phase2.md`
> 详细计划：`.omc/plans/phase2.md`
> 输入：`HANDOFF-PHASE2.md` + 用户 2026-10-19 功能清单

---

## 1. 结论

Phase 2 按 **M1 → M2 → M3 → M4** 推进：

- **M1 = P0 发布硬化 + T8 真机**，其中：
  - **M1-A（现在可做）**：备份/恢复、Roborazzi、Baseline Profile、a11y/字符串基础、崩溃/ANR 可观测性。
  - **G0**：真机到位，跑 T8 基线。
  - **M1-B（G0 后）**：试响、跳过下一次、英文完成、T8 A–F。
- **M2 = P1 差异化**：统计/streak、自适应目标、防作弊、渐进音量 + 每闹钟声音/振动、唤醒后小结、玻璃帧率基准。
- **M3 = P2 可见性/生态**：小组件 + QS、自动化接口/智能家居、节假日/调休（最后）、T9 root（可选）。
- **M4 = Wear OS**：后置，不早于 M2。

**T8 是硬门禁**：没有真机 A–F 证据，不关闭 M1、不宣称发布完成。

---

## 2. 本轮锁定的决策

| 决策 | 值 |
|---|---|
| 里程碑顺序 | M1 → M2 → M3 → M4；Wear 后置 |
| 试响时长 | **10 秒自动结束**；debug 可配 30 秒 |
| 唤醒后小结 | **纯本地**：用时/步数/snooze/streak/自定义一句话；**不含天气** |
| P0 暂停范围 | 设备到位前只停响铃相关 P0；设备无关准备先做 |
| Room v3 | 一次加齐 `skip_until`、`sound_uri`、`vibration_pattern`、`volume_ramp_seconds` |
| 不做 | 账号/云同步、广告/核心付费墙、社交排行榜、睡眠周期智能唤醒 |
| Root | 可选辅助（pm grant/appops/deviceidle），不做 daemon/轮询/root socket |

---

## 3. 现在就能开始的任务（M1-A）

按建议顺序：

| 顺序 | 任务 | 成本 | 产出/验收 |
|---|---|---|---|
| 1 | **备份/恢复 JSON** | S–M | `:data` BackupCodec + 设置页 SAF；round-trip/坏数据测试 |
| 2 | **Roborazzi 截图测试** | S–M | 关键页面 + 4 种底栏/玻璃组合基线 |
| 3 | **Baseline Profile** | S–M | `:baselineprofile` + Main/Ringing 冷启动 before/after |
| 4 | **a11y + 字符串基础** | M | TalkBack、200% 字体、现有页面 strings.xml |
| 5 | **崩溃/ANR 本地可观测性** | S–M | 环形日志、可导出、默认不上传 |

这些都不依赖真机；完成后跑 `.\gradlew.bat test` 和 `:androidApp:assembleDebug`。

---

## 4. 真机门禁 G0（必须）

1. `adb devices` 确认设备（小米/红米 Android 14+）。
2. 安装当前 APK。
3. 跑 `docs/testing.md` 的模拟器 14 项 + 真机基线：FSI、精确闹钟、ACTIVITY_RECOGNITION、DND、HyperOS 电池/自启动。
4. 记录设备型号、系统版本、权限状态、证据。
5. 没有设备：**M1-B 全部阻塞，M1 不关闭**。

---

## 5. G0 之后的任务（M1-B）

| 顺序 | 任务 | 成本 | 关键验收 |
|---|---|---|---|
| 1 | **试响 10 秒** | S | 不写 session/event；正式闹钟不受影响；debug 30 秒 |
| 2 | **跳过下一次** | S–M | Room v3 迁移 2→3；单次/重复/过期/重启 |
| 3 | **英文完成** | M | 英文环境全流程无中文残留；日期本地化 |
| 4 | **T8 真机 A–F** | M | 全项 PASS + 截图/logcat/dumpsys 证据 |
| 5 | **M1 关闭** | S | 122+ tests；APK；文档；提交 |

试响是 T8 的调试工具：先做它，后续真机验收会省很多时间。

---

## 6. 后续里程碑摘要

### M2（P1 差异化）

| 任务 | 成本 | 依赖 |
|---|---|---|
| 唤醒统计 + streak | M | M1 |
| 自适应目标 | S–M | 统计 |
| 防作弊（摇晃 ≥8 秒/幅度） | M–L | M1 |
| 渐进音量 + 每闹钟声音/振动 | S–M | v3 列 |
| 唤醒后小结（纯本地） | S–M | 统计 |
| 玻璃帧率基准 | S–M | T6 |

### M3（P2 可见性/生态）

| 任务 | 成本 | 依赖 |
|---|---|---|
| 小组件 + QS 磁贴 | M | 跳过下一次 |
| 自动化接口/智能家居 | M | 试响/跳过 |
| 节假日/调休（可选最后） | M | 跳过下一次 |
| T9 root 辅助（可选） | S–M | M1 |

### M4（Wear OS）

- 独立 `:wear`；手机推送响铃/步数/目标/剩余；手机仍是唯一停止判定方。
- 断连/低电/延迟验收；不早于 M2。

---

## 7. 优先级矩阵

### 发布前最值得补的（P0）

| 功能 | 成本 | 价值 |
|---|---|---|
| 试响/预览 | S | 用户信任 + T8 调试工具 |
| 跳过下一次 | S–M | 周末/临时晚起 |
| 备份/恢复 | S–M | 换机不丢闹钟 |
| 无障碍 + 英文 | M | 可访问性/国际化 |
| Baseline Profile | S–M | 响铃页冷启动可信度 |
| 截图/黄金图 | S–M | UI 回归兜底 |
| 崩溃/ANR 可观测性 | S–M | 发布后可诊断 |

### 差异化（P1）

统计/streak、 自适应目标、防作弊、渐进音量+每闹钟声音、唤醒后小结、玻璃帧率。

### 可见性/生态（P2）

小组件+QS、自动化/智能家居、节假日/调休、Wear OS（M4）。

---

## 8. 硬约束 / 坑

1. 不降 compileSdk（37）、不移除 Miuix、不用 JDK 23 替代项目 JDK 21。
2. 数据层放 `:data`，不要塞 KMP `shared`；Miuix import 只在 `MwComponents.kt`。
3. 写入 `require`、读取 `sanitized()`；Room 迁移必须有测试。
4. 每任务一个 git 提交；汇报只写实际跑过的命令。
5. 通知渠道不可变：每闹钟声音在播放器层做，不建大量 channel。
6. 试响不能写 session/event；真实闹钟优先。
7. 防作弊阈值必须可调、可观测，避免真实用户停不掉。
8. 无真机时不要宣称 T8/M1 完成。
9. 不要把“明确不做”的功能加回来。
10. 唤醒小结不要引入天气/定位/网络。

---

## 9. 第一批 PR 建议

1. `feat(data): backup and restore alarms/settings as JSON`
2. `test(ui): add Roborazzi screenshot baselines`
3. `perf(startup): add baseline profile for Main/Ringing`
4. `feat(a11y): content descriptions and string resources`
5. `feat(observability): local crash/ANR ring buffer`
6. G0 真机 → `feat(ringing): add 10-second test ring`
7. `feat(scheduling): skip next occurrence`（先写 v2→v3 迁移测试）

---

## 10. 验收与汇报

- 每个任务：`.\gradlew.bat test` + `.\gradlew.bat :androidApp:assembleDebug`。
- 截图：`:androidApp:testDebugUnitTest`。
- 真机：`adb install -r ...`、`adb logcat`、`dumpsys alarm`。
- 汇报模板：

```markdown
## 完成
- ...

## 改动
- `path:line` ...

## 验证
- `<command>` → <结果>

## 未完成/风险
- ...
```

---

## 11. 必读顺序

1. `HANDOFF.md`
2. `HANDOFF-PHASE2.md`
3. `mustwake/docs/toolchain.md`
4. `mustwake/docs/data-layer.md` → `scheduling.md` → `ringing.md` → `steps.md` → `ui.md` → `appearance.md` → `permissions.md` → `testing.md` → `decisions.md`
5. `.omc/specs/deep-interview-phase2.md`
6. `.omc/plans/phase2.md`
7. 本文

先做 **M1-A1 备份/恢复**；真机到位后立即做 **G0 → M1-B1 试响**。
