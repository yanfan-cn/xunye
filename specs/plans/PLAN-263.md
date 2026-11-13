# PLAN-263：AI 助手流畅 / 侧栏焦点 / 底栏透明 / 分享

## A. 流畅

1. `MarkdownBlock`：去掉首帧主线程 `parseMarkdown`；全程 `Dispatchers.Default` + `mapLatest`；未解析完用纯文本暂显，避免切会长会话时同步解析卡顿。
2. `AiChatViewModel.observeCurrentMessages`：parts 解码与 UI 映射放到 `Dispatchers.Default`，再回主线程更新 state。
3. `AiGeneratedMessageContent`：去掉整卡 `animateContentSize()`（流式/切会话时动画抢帧）。

## B. 侧栏焦点

1. 抽屉 `open` 时：`LocalFocusManager.clearFocus()` + `LocalSoftwareKeyboardController.hide()`。
2. `ConversationPane` 搜索：`focusProperties { canFocus = searchInteractive }`，仅用户点击搜索区域后才可聚焦；关闭抽屉时复位。

## C. 底栏（保留半透明渐变）

1. 保留输入区上方垂直半透明渐变模糊。
2. 导航区实色 `alpha=0.88` Spacer 改为渐变续接（半透明，非实心白块）。
3. 生成中底色 lerp 可保留，但不得形成实色占位条。

## D. 操作行 + 分享 + 去 MD

1. `AiGeneratedMessageContent`：token 与刷新/复制/分享合并同一 `Row`，`Alignment.CenterVertically`。
2. 新增 `onShare`；`AiChatScreen` 用 `ACTION_SEND` + `createChooser`。
3. 新增 `AiChatPlainText.stripMarkdown`（去 ATX `#`、加粗/斜体 `*`/`_`、代码围栏等常见标记）；复制与分享共用。
4. 单测覆盖 strip 样例。

## 验证

- 单测 strip；`compileAlohaDebugKotlin`；`assembleAlohaDebug` → `巡页2.5.8-beta.apk`。
- AC-01~05 待真机。
