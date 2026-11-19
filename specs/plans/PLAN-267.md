# PLAN-267：手势切回闪退 / 平板标签串页 / 恢复丢历史 / 分享入口

| 项目 | 内容 |
| --- | --- |
| Spec | [`../features/SPEC-267.md`](../features/SPEC-267.md) |
| 验证 | 单测 + `assembleAlohaRelease`；AC 真机（操作者） |

## 现状

- 活跃 AwContents 仅在 park/attach 时 `onPause`/`onResume`，**Activity 进后台未桥接**，手势切回时内核 surface 易崩；NTP 无活跃宿主故不闪。
- `updateActiveMeta` / `onPageMeta` 只写「当前活跃标签」，切标签竞态可把 A 的 URL 写到 B；`onReady` 见 `initialUrl != client.lastUrl` 会 `loadUrl`，把已 park 的 B 页冲成 A。
- `BrowserTabPersist` 只存 url/title，冷启 `queueEagerLoad`+`loadUrl`，无 `AwContents.saveState`/`restoreState`；`tabDisplayTitle` 在 `showHome` 仍优先网页 title。
- release 主包 `receiving_shared_label` 仍为「阅读 · 搜索」；仅 debug overlay 为「巡页·搜索」；无收藏分享入口。

## 方案

### A. 前后台内核生命周期（AC-01/02）

1. `BrowserSessionHolder` 增加 `pauseActiveForAppBackground()` / `resumeActiveForAppForeground()`（仅活跃槽；已 park 的保持 pause）。
2. `BrowserSessionScreen`（或 `AlohaBrowserComposeInner`）用 `LifecycleEventEffect`：`ON_PAUSE`→pause，`ON_RESUME`→resume；并核对 holder.sessionKey 与本屏一致。

### B. 标签串页门闩（AC-03）

1. `BrowserTabManager.updateActiveMeta(url, title, sessionKey)`：仅当 `sessionKey == activeTabId` 且（若 holder 有活跃键）`sessionKey == BrowserSessionHolder.sessionKey` 时写入。
2. `BrowserSessionScreen` 的 `onPageMeta` / client 回调传入 `resolvedSessionKey`。
3. `onReady` 恢复 park 会话：若 `navEpoch` 未前进且 `client.lastUrl` 非空，**信任内核页**，用 `client.lastUrl/title` 回写 meta，禁止用脏 `initialUrl` 强行 `loadUrl`。

### C. 导航状态持久化 + 主页标签名（AC-04/05）

1. `AwContentsHostView`（aloha + systemWv）封装 `saveNavigationState(): Bundle?` / `restoreNavigationState(Bundle): Boolean`。
2. `BrowserTabWebStateStore`：按 tabId 落盘 Bundle（文件或 Base64）；`parkActive`/`persist`/后台 pause 时保存活跃键；`destroyKey`/`closeAll` 清理。
3. 冷启：`init` 对活跃非主页标签若有 web state 则 `queueRestoreState`；`attachTo` 创建宿主后先 `restoreState`，成功则跳过 eager `loadUrl`。
4. `tabDisplayTitle`：`showHome == true` →「新标签页」。

### D. 分享（AC-06）

1. 主包 `values` / `zh-rCN` / `zh-rTW` / `zh-rHK`：`receiving_shared_label` → 巡页·搜索（英文 `Xunye·Search`）；debug overlay 对齐。
2. 新增 `receiving_shared_favorite_label`；`activity-alias` 指向 `SharedReceiverActivity`，label 为「巡页·收藏」。
3. `SharedReceiverActivity`：按 component 区分收藏模式；抽取首个 `http(s)` URL → `BrowseStore.addBookmark`；无链接 Toast「无法收藏」；搜索模式保持现逻辑。

### E. Release（AC-07）

`$env:GRADLE_USER_HOME="D:\cursor\.gradle-home"`；`assembleAlohaRelease --no-configuration-cache`；apksigner；只留 `巡页1.0.3.apk`。

## 测试

| TC | AC | 步骤 |
| --- | --- | --- |
| TC-01 | AC-03 | 单测：`updateActiveMeta` 错 sessionKey 不改活跃 tab；对 key 才改 |
| TC-02 | AC-05 | 单测：`showHome` 时 `tabDisplayTitle` 为「新标签页」 |
| TC-03 | AC-06 | 单测或逻辑测：分享文本 URL 抽取；无链接失败 |
| TC-04 | AC-01/02/04/06/07 | release 装机（操作者） |

## 任务

1. Spec/Plan（本文件）
2. 生命周期 pause/resume
3. 标签 meta 门闩 + onReady 信任 parked lastUrl
4. Web state 持久化 + 主页标签名
5. 分享文案与收藏入口
6. 单测 + assembleAlohaRelease + LOG
