# PLAN-266

对应 [`../features/SPEC-266.md`](../features/SPEC-266.md)。

## 现状证据

- Release `app_name` 在 `values` / `values-zh-rCN/TW/HK` 为「巡页Xunye」；`src/debug` 已为「巡页」。
- 引导功能文：`app/src/main/assets/onboardingFeatures.md`；缺翻译/AI。
- AI 顶栏：`AiChatScreen` 用 `currentConversation?.modelName`，空则「选择模型」；`observeAvailableModels` 只更新 `availableModels`/`currentModelProfileId`，未回填会话 `modelName`（向导刚保存后常见）。
- 看图：`ReaderImageGalleryDialog` 无 zoom；顶右为分享|保存|关闭；漫画侧已用 telephoto `ZoomableImage`。
- NTP：`NtpWallpaperIds.DEFAULT = MIST`；`BrowserChromePrefs.ntpWallpaperId` 缺省即薄雾，无「深色→霓虹」分支，无「是否已由用户/系统写入」标记。
- 闪退：无 log；历史高危见 `docs/液态玻璃与动画安全.md`；印象为快切/连点。

## 方案

### A. 桌面名

主资源四处 `app_name` →「巡页」/「巡頁」。

### B. 引导文案

`onboardingFeatures.md` 追加「翻译」「AI 助手」两节；AI 按四块压缩。

### C. AI 顶栏

1. `observeAvailableModels`：有 `currentId` 时，把当前会话（及必要时列表项）的 `modelName`/`providerName` 同步为选中模型。
2. `AiChatScreen` 展示优先：会话 `modelName` → `availableModels` 中选中项 →「选择模型」。

### D. 净读看图

1. 单页图用 telephoto `ZoomableImage`（或等价）支持 pinch；缩放到 1x 时仍允许下滑关闭。
2. `Symbols` 增加 `PhotoLibrary`（`photo_library`）。
3. 顶右序：分享 | Photo Library | 保存 | 关闭。
4. Photo Library：网格缩略图 overlay；点击 `pagerState.scrollToPage` 并关闭网格。

### E. NTP 默认壁纸

1. Prefs 增加「壁纸已初始化」或检测 key 是否存在（`contains`），与「值为 mist」区分。
2. 首次读取且未初始化：按当前深/浅（跟随系统时用 Configuration `uiMode`）写 NEON 或 MIST，并标记已初始化。
3. 用户切换壁纸只改 id；删除自定义回退时若需默认，仍用浅 mist / 已选逻辑，不重跑「首次」。

### F. 闪退静态审计（快切/连点）

检查并按需加固：

1. 主壳/标签/NTP↔网页切换：是否仍违反「采样 AwContents / fade+scale 标签 / 立即 destroy」。
2. Compose：未附着节点、空列表下标、Dialog/Pager 竞态。
3. AI/浏览器近期改动中的 force unwrap、并发 cancel。
4. 能安全加的：空安全、index coerce、快速重复点击防重入（最小改动）。
5. 无证据不大改架构；结论写 LOG-266。

### G. Release

```powershell
$env:GRADLE_USER_HOME = "D:\cursor\.gradle-home"
cd D:\cursor\Xunye\upstream\Xunye
.\gradlew.bat assembleAlohaRelease --no-configuration-cache
```

签 debug.keystore → `巡页1.0.2.apk`；删 unsigned/`.idsig`。

## 验证

- 单测：壁纸首次解析（深→neon / 浅→mist；已有 key 不覆盖）；AI 顶栏回落名（可选）。
- 编译/打包 AC-07。
- AC-01～04、06 真机；AC-05 以 LOG 为准。

## 回退

各点独立；壁纸用新 key，误伤可删 key 恢复旧 DEFAULT=mist 行为。
