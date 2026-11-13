# PLAN-264

## A. DeepSeek 联网

1. 向导预设 `deepseek`：`protocol=openai_responses`（`baseUrl` 仍 `https://api.deepseek.com`）。
2. 请求构建：识别 DeepSeek（host/`modelId`）；若存的是 Chat Completions，运行时按 Responses 发。
3. `deepseek-v4-pro`：tools 前置 `{"type":"web_search"}`；Flash 不前置。
4. `OpenAiResponsesHandler.toOpenAiResponsesTools` 支持内置 `web_search` 与 function 并存。

## B. 省 token

1. 改写 `buildSystemPrompt`：仅用户问书架/章节/书签等才用本地工具；通识/实时用联网（若有）；禁止反复猜 URL/同内容多读。
2. `MAX_TOOL_OUTPUT_CHARS` 下调；工具结果截断加强；`search_books` 等限制条数与 intro 长度。
3. 历史回传时工具大段结果再截断。

## C. 滚动

1. 贴底改为「距列表底部 ≤阈值」；`NestedScrollConnection` 用户上滑立刻 `shouldStickToBottom=false`。
2. 仅 `shouldStickToBottom==true` 时流式 `scrollToItem`。

## D. 平板 / 气泡

1. 宽屏/平板消息 `LazyColumn` 水平 padding ≥44dp。
2. 用户 `GlassCard`：`wrapContentWidth` + `widthIn(max=86%)`，去掉强制 `fillMaxWidth(0.86f)`。

## 验证

单测（DeepSeek web_search 工具组装 / strip 无关）；compile；assembleAlohaDebug → 2.5.9-beta。
