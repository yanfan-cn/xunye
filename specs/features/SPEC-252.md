# SPEC-252：巡页 App 图标统一为指定方图

| 项目 | 内容 |
| --- | --- |
| Spec ID | SPEC-252 |
| 状态 | Done |
| 负责人 | AO |
| 需求依据 | 2026-11-08「补充：巡页 app 图标统一改为 Documents\browsesth-logo-512-square.png」；随 debug 构建 |
| 对应 Plan | [`../plans/PLAN-252.md`](../plans/PLAN-252.md) |
| 关联 | SPEC-052/115 |

## 确认

- 任务下达者指令即确认（补充当前 debug 构建任务）。
- 构建规则补充：无要求不打 release；打 release 则不打 debug。

## 范围

- 做：用给定 512 方图重制 `ic_launcher` / round / adaptive foreground（含 launcher0–6/w 共用层）/ monochrome / `drawable/ic_launcher` / splash logo。
- 不做：改 NTP 二字 Logo（`ic_ntp_logo`）；不打 release。

## 验收

- [ ] AC-01：桌面/关于/启动闪屏可见新方图（非旧图）。
- [ ] AC-02：可选皮肤 launcher0–6/w 与主图标同一 foreground。
- [ ] AC-03：产出 debug 包（含本图标）；未打 release。
