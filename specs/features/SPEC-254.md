# SPEC-254：净视 FGS 崩溃 + 翻译入口/弹窗 + Bergamot/Google 深度效果

| 项目 | 内容 |
| --- | --- |
| Spec ID | SPEC-254 |
| 状态 | Done（待装机） |
| 负责人 | AO |
| 需求依据 | 2026-11-09 崩溃 log×2；平板翻译入口/取消询问；Bergamot/Google 深度效果；确认「都做」 |
| 对应 Plan | [`../plans/PLAN-254.md`](../plans/PLAN-254.md) |
| 关联 | SPEC-188/251/249 |

## 确认

- 任务下达者确认：崩溃修复 + 平板按需显示翻译键 + 取消底栏询问 + Bergamot/Google **深度效果优化**。

## 范围

1. **崩溃**：`BrowserVideoPlaybackService` 在 `startForegroundService` 后须立即 `startForeground`；禁止未 promote 就 `stopSelf`/`stopService` 竞态。
2. **平板**：仅当语种检测判定「需要翻译」时显示地址栏翻译键。
3. **统一取消**「翻译此网页？」底栏询问（手机+平板）；自动译仅在设置开启时静默执行。
4. **Bergamot**：更好源语言、短节点打包上下文、跳过无意义串、配置与 pivot 批处理、失败可感知。
5. **Google**：POST/分块、并发限流、重试、页内译勿静默整段留原文；失败计数提示。

明确不做：套壳网页译；新引擎；本轮不强制 assemble（验证以 compile 为准，操作者未要求出包）。

## 验收

- [x] AC-01：Service `onStartCommand` 入口即 promote；`stop` 经 ACTION_STOP 且先 promote。（代码）
- [x] AC-02：平板翻译键仅在 `shouldPrompt` 为真时可见；中文页隐藏。（代码；真机待验）
- [x] AC-03：无 `TranslatePromptBar` / 「翻译此网页？」UI。（代码）
- [x] AC-04：Bergamot 页内译走打包+改进检测；缺模型/失败有提示。（代码；真机待验）
- [x] AC-05：Google 页内译走专用客户端（POST/分块/重试）；部分失败有提示。（代码；真机待验）
- [x] AC-06：`compileAlohaDebugKotlin` PASS；Packer 单测 PASS。
