# PLAN-265

## A. 工具箱去边缘高亮

1. `browserControlLiquidGlass` 增加 `showHighlight: Boolean = true`。
2. `toolboxPanelSurface` / `toolboxCellSurface` / `toolboxWebBgSurface` 传 `showHighlight = false`（`highlight = null`）。

## B. 赛博/霓虹搜索区

1. `BrowserNtpScreen`：`darkPresetVisible` 时胶囊 `textColor`/`secondary` 用浅色（如 White / White@0.85），驱动 AI 助手、无痕、扫码、搜索 tint；占位与引擎旁文字一并可读。

## C. Release

1. `$env:GRADLE_USER_HOME=D:\cursor\.gradle-home`
2. `.\gradlew.bat assembleAlohaRelease --no-configuration-cache`（仅 Aloha，不同时 debug）
3. 确认产物 `巡页1.0.1.apk`；按既有任务签名只留一份。

## 验证

装机 AC-01/02；LOG 记录产物路径与版号。
