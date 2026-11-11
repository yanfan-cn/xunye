# PLAN-259

## 方案

### 1. 网页背景跟随系统

- Aloha / systemWv：`WEB_DARK_AUTO` 按 `Configuration.UI_MODE_NIGHT_MASK` 解析为 ON/OFF 再调 Force Dark。
- 传入 Context（或 `isNight`）到 `applyWebContentDarkMode`。
- `BrowserSessionScreen` / 平板 chrome：配置变更或主题变化时对当前 host 重应用偏好。

### 2. 净读滚动首屏图

- `ReaderCanvasSurface.ScrollPageStack`：图片 `loadImage` 完成后 bump 与 `textBackgroundRevision` 同类的失效信号，强制 Canvas 重绘（根因：位图进 LRU 不触发重组，需滑一下才 draw）。
- 可选：抽取 JS 补常见 lazy 属性名。

### 3. 翻译

- `WebPageTranslatePacker`：对含汉字+拉丁的节点拆成「汉字保留 / 英文送译」片段（段内），打包时 skip 纯汉字。
- `BrowserSessionScreen.runInPageTranslate`：`translating` 状态 + 可取消 Job；UI 复用 `ReaderLoadingCapsule`，文案参数化「翻译中」；取消 cancel job + 不写半成品标记（尽量 restore 或停在已应用批）。
- 去掉 toast「翻译中…」；完成/失败可保留短 toast 或仅胶囊消失。

### 4. 收藏切换

- `BrowseStore` 已有 `removeBookmark`；增 `isBookmarked(url)`（或现查）。
- 工具箱 `add_bookmark` 按当前 URL 态切换 label/icon；动作 toggle。
- 平板地址栏同态；`Symbols.BookmarkRemove` = `bookmark_remove`。

## 验证

- 单测：`WebPageTranslatePacker` 混排拆分；可选 force-dark 解析纯函数。
- `:app:testAlohaDebugUnitTest` 相关类；不 assemble。

## TC

| ID | 对应 AC | 方式 |
| --- | --- | --- |
| TC-01 | AC-04 | 单测 |
| TC-02 | AC-07 | compile/test |
| TC-03–06 | AC-01/02/03/05/06 | 装机（本轮不做） |
