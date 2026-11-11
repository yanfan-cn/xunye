# PLAN-261：页内译加速 + AI 助手 UI

| 项目 | 内容 |
| --- | --- |
| Spec | SPEC-261 |
| 策略 | 先改译路径 batch；再改 AI UI；单测 Packer；assembleAlohaDebug |

## 实施步骤

### A. 翻译

1. `WebPageInPlaceTranslator.translatePacked`：收集待译 pack → `prepare`（Han mask）后走 `FoxletTranslateClient.translateBatch` / `GoogleTranslateClient.translateBatch` / AI 逐条或小批；结果 `unpack`。
2. 丢标记 / 失败：收集节点 → 再次 batch，禁止串行 `translateOnePack` 主路径。
3. `rescanLeftoverLatin`：命中节点 batch 补译。
4. 单测：必要时补 batch 路径相关 Packer 断言（现有 `WebPageTranslatePackerTest` 保持绿）。

### B. AI UI

1. `AiChatScreen` 顶栏：左 `Menu`；右 `Add` + `Close`（`onBackClick`）。
2. 横屏：`Row` 左 `ConversationPane`（搜索常显、无顶栏）+ 右对话；竖屏保留 Drawer。
3. `AiGeneratedMessageContent`：`showHeader=false`（或按角色隐藏）；隐藏 tool 展示；`reasoningAutoExpandWhileStreaming=false`；合并 BookResult；浏览器 origin →「相关网页」。
4. 发送：`Symbols.ArrowUpward`（新增 symbol）。
5. `AiChatGateway.createConversation` 默认标题「新对话」；ViewModel/调用处一致；字符串资源同步。
6. `MarkdownBlock` / AI 调用处：固定 `lineHeight` + 段底间距统一。

### C. 验证

1. `:app:testAlohaDebugUnitTest --tests …WebPageTranslatePackerTest`
2. `assembleAlohaDebug`（`GRADLE_USER_HOME=D:\cursor\.gradle-home`）
3. LOG-261 记证据与包名。

## TC

| ID | 前提 | 步骤 | 期望 |
| --- | --- | --- | --- |
| TC-01 | Bergamot | 译外文长页 | 进度连续前进；主路径无逐 pack 串行阻塞感 |
| TC-02 | AI 竖屏 | 点顶栏 | 左开侧栏；右新建；Close 退出 |
| TC-03 | AI 横屏 | 旋转 | 左栏搜索常显、无侧栏顶栏 |
| TC-04 | 新建对话 | NewConversation | 标题「新对话」 |
| TC-05 | 助手回复含书籍 | 多 BookResult / 浏览器 origin | 单块列表；浏览器标「相关网页」 |
