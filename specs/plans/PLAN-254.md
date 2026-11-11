# PLAN-254

1. **FGS**：`BrowserVideoPlaybackService` — `onStartCommand` 首行 promote；`ACTION_START_AUDIO` 先通知后 Exo；`stop()` 改发 `ACTION_STOP`（`startForegroundService`），handler 内 promote→停。
2. **入口**：`pageNeedsTranslate` 状态；`maybeDetectTranslateNeed` 只更新状态/自动译；删 prompt bar；平板 `TabletFunctionBar` 条件显示翻译键（需可重组状态）。
3. **Bergamot**：`FoxletTranslateClient` — `TranslationConfig(nonbreakingPrefixes=true, miniBatchWords↑, cacheSize↑)`；显式 `TextFormat.Plain`；pivot 批处理；改进 `guessSourceLang`；`WebPageTranslatePacker` 短节点打包。
4. **Google**：新 `GoogleTranslateClient`（form POST、长文分块、并发、重试）；`WebPageInPlaceTranslator` / `TranslateChapterUseCase` 改用；失败不整批静默原文。
5. LOG + 交接；`compileAlohaDebugKotlin` 验证。
