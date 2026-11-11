# SPEC-260：Material Motion 硬性要求 + AI 设置四厂商向导

| 项目 | 内容 |
| --- | --- |
| Spec ID | SPEC-260 |
| 状态 | Implemented（待装机） |
| 负责人 | AO |
| 需求依据 | 2026-11-10 确认：允许自定义接入；对话思考只留自动；assembleAlohaDebug |
| 对应 Plan | [`../plans/PLAN-260.md`](../plans/PLAN-260.md) |
| 关联 | SPEC-258 |

## 范围

1. `docs/UI设计文档.md`：过渡动画必须遵循 Material Motion。
2. AI 向导供应商默认 DeepSeek；可选小米 MiMo、智谱 GLM、千问 Qwen；向导取消其余；高级/「新增模型接入」仍可自定义 OpenAI 兼容。
3. 预置各厂最新公开模型；主路径「常用模型」；显示名称隐藏（回退 ID）；仅自定义时填模型 ID。
4. 顶栏：「模型列表」`format_list_bulleted`、「新增模型接入」`add`。
5. 「保存为当前」上方「测试连接」。
6. 对话 UI 思考强度只留自动（去掉多档选择）；朗读等任务底层仍可 OFF。
7. `assembleAlohaDebug`。

## 验收

- [ ] AC-01：UI 文档含 Material Motion 硬性条款。
- [ ] AC-02：向导仅 DeepSeek/MiMo/GLM/Qwen，默认 DeepSeek。
- [ ] AC-03：DeepSeek 预置含 Flash + Pro（`deepseek-flash` / `deepseek-v4-pro`）。
- [ ] AC-04：顶栏两键分别打开模型列表 / 新增接入。
- [ ] AC-05：测试连接可用；保存为当前仍在其下。
- [ ] AC-06：对话页无多档思考滑条，请求固定 AUTO。
- [x] AC-07：debug 包 — `巡页2.5.5-beta.apk`
