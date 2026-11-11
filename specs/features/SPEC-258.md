# SPEC-258：液态玻璃默认关 + AI 设置三步接入向导

| 项目 | 内容 |
| --- | --- |
| Spec ID | SPEC-258 |
| 状态 | Implemented（待装机） |
| 负责人 | AO |
| 需求依据 | 2026-11-09 确认：默认 Filled、不做 liquid→filled 迁移；AI 页直接三步向导合并供应商/模型；打 debug |
| 对应 Plan | [`../plans/PLAN-258.md`](../plans/PLAN-258.md) |

## 范围

1. `ChromeSurfaceStylePolicy.defaultStyle` 一律 `Filled`（不迁移已选手动开液态的用户）。
2. AI 设置页：预设供应商 → API Key → 选/填模型 → 保存并设为当前；取代「新建供应商 / 当前模型」双入口主路径。
3. `assembleAlohaDebug`。

## 验收

- [ ] AC-01：新安装/无存储偏好时默认非液态玻璃。（装机验）
- [ ] AC-02：AI 设置页可 DeepSeek 预设 + Key + 模型名完成接入并成为当前模型。（装机验）
- [x] AC-03：debug 包产出 — `巡页2.5.4-beta.apk`
