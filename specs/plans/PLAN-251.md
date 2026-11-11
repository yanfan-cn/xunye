# PLAN-251：翻译工程落地

对应 Spec：[SPEC-251](../features/SPEC-251.md)

## 1. 现状证据

- `WebPageTranslate.WEB_PAGE_TRANSLATE_UI_ENABLED=false`；`pageTranslateUrl` → Google/百度套壳。
- `TranslationConstants` 提供方含百度/ML Kit；默认百度。
- 净读：`TranslateChapterUseCase` + `TranslationManager`；`BrowserReaderPrefs.autoTranslateOnOpen` 已存在（默认关）。
- 宿主：`AwContentsHostView.evaluateJavaScript`；工具箱 `BrowserToolboxPrefs.DEFAULT_ORDER`；平板收藏键在 `TabletBrowserChrome` omnibox 行内。
- Foxlet 0.5.0：`implementation(files("libs/foxlet-v0.5.0.aar"))`；minSdk 28；HTML 译实验性 → 本轮用文本节点批替换更稳。

## 2. 方案

1. **依赖**：下载 AAR → `app/libs/`；`minSdk=28`；显式协程已有则不重复。
2. **Foxlet 封装**：应用级 `FoxletTranslateClient`（create/shutdown、prepare+pivot、`zh`→`zh-Hans`）。
3. **章节译**：`TranslateChapterUseCase` 增 Bergamot 分支；删/隐藏百度与 ML Kit UI；旧 prefs 迁移 Bergamot。
4. **页内译**：`WebPageInPlaceTranslate` JS 采集文本节点 → 批译 → 回写/`data-xy-orig` 还原；`WebPageTranslate` 废套壳入口，闸门改为可用。
5. **入口**：工具箱 `translate`（点按 toggle / 长按设置）；平板 omnibox 收藏左侧翻译键（仅点按）。
6. **设置**：`TranslationSettings` 增 `autoTranslateWebpage` / `autoTranslateJingdu`（默认 false）；净读自动与 `BrowserReaderPrefs.autoTranslateOnOpen` 对齐或改读设置。
7. **自动网页译**：`pageFinished` 且开关开、非目标语言、非已译 → 触发页内译。

## 3. 主要文件

- `app/libs/foxlet-v0.5.0.aar`、`app/build.gradle.kts`
- `browser/reader/WebPageTranslate.kt`、新建页内译控制器/JS
- `domain/.../TranslationConstants.kt`、`TranslationSettings.kt`、FeatureSettings 映射
- `TranslateChapterUseCase.kt`、Foxlet 客户端
- `BrowserToolboxSheet.kt`、`BrowserToolboxPrefs.kt`、`TabletBrowserChrome.kt`、`BrowserSessionScreen.kt`
- `TranslationConfigScreen.kt` / Contract / ViewModel
- 测试：settings mapping；可选页内 JS 单测

## 4. 验证

- 单元：provider 列表与默认值、prefs 迁移、自动译默认 false。
- 编译：`compileAlohaDebugKotlin`（需同盘 GRADLE_USER_HOME）。
- 真机：外文页工具箱译/还原；平板键；长按设置；自动开关；缺模型提示（装机另验）。

## 5. 回退

去掉 AAR 依赖与入口；恢复闸门 false；提供方列表可回滚。
