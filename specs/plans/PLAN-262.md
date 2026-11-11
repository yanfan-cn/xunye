# PLAN-262

## A. 滚动与流畅
1. `AiChatScreen`：去掉流式开始强制 `shouldStickToBottom=true`；仅用户近底才跟底；流式用 `scrollToItem` 减动画抢焦点。
2. 切会话：先清空 `messages` 再加载；Markdown 已后台解析，避免切会话时多余重组（`key`/`remember`）。
3. 进页默认 `NewConversation`（见 F）。

## B. 模型切换
1. 标题可点 → BottomSheet：当前可用模型列表；「添加模型」→ `OpenAiSettings`。
2. 选模型：写 CHAT task preset（复用 AiProfileGateway）。

## C. Token / 用量
1. `AiStreamEvent.Usage`；OpenAI stream 尽量 `include_usage`；解析则真实，否则 `AiTokenEstimator`。
2. `AiMessagePart.Usage` 随 parts 落库；气泡底展示。
3. `AiUsageLedger`（PreferKey）累计；`AiUsageScreen` + settings 顶栏入口 + MainRoute。

## D. 侧栏 / 横屏
1. `BrowserListSearchField` 替换侧栏搜索。
2. 标题恢复双行：对话名 + 模型名。
3. 横屏：`Menu` 切换 `sidebarPinned`；钉住=分栏，取消=全屏+抽屉。

## E. 启动选项
1. PreferKey `aiRestoreLastChat` 默认 false；AI 设置 Switch。
2. ViewModel init：false→`createConversation()`；true→选最近一条。

## F. 翻译入口
1. `pageTranslated` 状态（toggle 结果 / IS_ACTIVE）；URL 变重置。
2. 地址栏显示：`pageNeedsTranslate || pageTranslated`。
3. Toolbox：`pageTranslated` → 文案「恢复原文」。

## 验证
compile + Packer 单测 + assembleAlohaDebug。
