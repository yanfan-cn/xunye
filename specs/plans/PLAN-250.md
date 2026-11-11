# PLAN-250：NTP「AI 助手」接入现有 AI 对话

对应 Spec：[SPEC-250](../features/SPEC-250.md)

## 1. 现状证据

- `BrowserNtpScreen`：`NtpActionChip(label = "AI 模式")` + `toastOnUi("AI 模式即将推出")`。
- `MainNavGraph` 已有 `MainRouteAiChat` → `AiChatRouteScreen`；「我的」经 `onNavigateToChat` 接入。
- `BrowserHomeTabScreen` / `BrowserNtpScreen` 尚无 `onOpenAiChat` 回调。
- 设置族经 `MainIntent.createSettingsIntent(..., ROUTE_SETTINGS_AI)` 打开 `SettingsActivity`。
- 对话发送路径 `AiChatGenerationUseCase.buildRequest` 在无预设时 `error("Please configure a default AI model first")`。

## 2. 方案

1. **穿线**：`BrowserNtpScreen` 增加 `onOpenAiChat`；`BrowserHomeTabScreen`（手机/平板 NTP）与 `MainScreen` 接到 `onNavigateToChat` / `MainRouteAiChat`。
2. **文案**：NTP 硬编码改为 string；`ai_chat` 简中「AI 助手」（英/繁同步 Assistant/助手语义）。
3. **未配置模型**：`AiChatViewModel` 观察 profile，维护 `isModelConfigured` / `showMissingModelPrompt`；进页缺模型即提示；发送前拦截再提示；确认按钮打开 `createSettingsIntent(ROUTE_SETTINGS_AI)`。
4. **文档**：`docs/UI设计文档.md` §3.1「AI 模式」→「AI 助手」；交接补 SPEC-250 一句。

## 3. 主要改动文件

- `BrowserNtpScreen.kt`、`BrowserHomeTabScreen.kt`、`MainScreen.kt`
- `AiChatContract.kt`、`AiChatViewModel.kt`、`AiChatScreen.kt`、`MainNavGraph.kt`
- `values*/strings.xml`（`ai_chat` + 提示文案）
- `docs/UI设计文档.md`、`docs/会话状态交接.md`

## 4. 验证

- 代码审查对照 AC-01～04（本轮不 assemble）。
- 可选：静态确认 NTP onClick 非 toast；对话 Effect/Intent 含打开 AI 设置路径。

## 5. 回退

还原上述穿线与文案；对话缺模型提示相关状态可单独回退。
