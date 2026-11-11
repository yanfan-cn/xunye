# PLAN-249：翻译引擎产品口径（仅文档）

对应 Spec：[SPEC-249](../features/SPEC-249.md)

## 1. 方案

本轮无工程改动。只固化决策到 SPEC-249，并在 `docs/会话状态交接.md` 记摘要。

后续（**不在本 Plan**）建议单独 spike / 实施：

1. **已定**：走现成 AAR（Foxlet 优先），不做自编 Bergamot JNI；spike 验证 arm64、模型按需下载、许可、英/日→简中章节译与体积。
2. 设置提供方列表裁剪：删百度/ML Kit；默认值迁 Bergamot；迁移旧 prefs。
3. 浏览器：**仅** Chrome 类页内 DOM 就地译（Bergamot 优先）；恢复闸门时须废弃/阻断 `pageTranslateUrl` 套壳。
4. 净读：翻译全文接 Bergamot；Google / 应用 AI 保留（章节文本）。不扩展其它译形态。

## 2. 验证

- SPEC-249 四条 AC 自检通过；本轮 `git diff` 不含 `upstream/Xunye` 产品源码。
