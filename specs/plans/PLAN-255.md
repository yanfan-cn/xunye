# PLAN-255

1. `WebPageInPlaceTranslateJs.COLLECT`：为每文本节点记 blockId（最近块级祖先）。
2. `batchSlice` 返回 texts + blockIds；`WebPageTranslatePacker` 按 block 打包，占位 `⟦n⟧`。
3. 占位丢失 → 该块逐节点重译；Bergamot `translateBatch` 失败整块重试一次。
4. 整页后对仍像源语言的节点补译一轮。
5. 单测 + compile。
