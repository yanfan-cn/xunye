# PLAN-258

1. `ChromeSurfaceStylePolicy.defaultStyle` → 恒 `Filled`。
2. `AiProviderPresets` 增 `suggestedModels`；向导只展示主预设（OpenAI / DeepSeek / Xiaomi / Anthropic 等）。
3. 重写 `AiConfigScreen` + `AiConfigViewModel`：页内向导保存 `saveProvider`+`saveModel`+`setDefaultModel`；任务入口保留；旧编辑页路由可保留但不作主入口。
4. 中英文字符串；assembleAlohaDebug。
