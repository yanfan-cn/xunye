# SPEC-256：桌面图标内缩改为 15%

| 项目 | 内容 |
| --- | --- |
| Spec ID | SPEC-256 |
| 状态 | Done（待装机） |
| 负责人 | AO |
| 需求依据 | 2026-11-09：确认桌面内缩由 18% 改为 15% |
| 对应 Plan | [`../plans/PLAN-256.md`](../plans/PLAN-256.md) |
| 关联 | SPEC-253 |

## 范围

- 做：`SAFE_PAD=0.15` 重生成 launcher foreground/mipmap/mono/about；不改 splash。
- 不做：assemble（随下次出包）。

## 验收

- [x] AC-01：foreground 不透明边距 15.0%。
- [x] AC-02：splash 仍匹配 HEAD（SPEC-253 旧版）。
