package io.legado.app.domain.model

/**
 * SPEC-264: DeepSeek chat uses Responses; official `web_search` only on Pro.
 * Flash ignores `web_search` per DeepSeek Responses API docs.
 */
object DeepSeekChatSupport {

    fun isDeepSeekProvider(provider: AiProviderConfig): Boolean {
        val host = provider.baseUrl.lowercase()
        return host.contains("deepseek.com") ||
            provider.name.contains("deepseek", ignoreCase = true)
    }

    fun withResponsesProtocol(model: AiModelConfig): AiModelConfig {
        if (!isDeepSeekProvider(model.provider)) return model
        if (model.provider.protocol == AiProtocol.OPENAI_RESPONSES) return model
        if (model.provider.protocol == AiProtocol.OPENAI_CHAT_COMPLETIONS) {
            return model.copy(
                provider = model.provider.copy(protocol = AiProtocol.OPENAI_RESPONSES)
            )
        }
        return model
    }

    /** Official server web_search is supported on deepseek-v4-pro; ignored on flash. */
    fun supportsOfficialWebSearch(model: AiModelConfig): Boolean {
        if (!isDeepSeekProvider(model.provider)) return false
        val id = model.modelId.lowercase()
        if (id.contains("flash")) return false
        return id.contains("v4-pro") ||
            id == "deepseek-v4-pro" ||
            (id.contains("pro") && !id.contains("flash"))
    }

    fun webSearchTool(): AiToolDefinition = AiToolDefinition(
        name = "web_search",
        description = "Official DeepSeek server-side web search",
        inputSchema = emptyMap(),
        serverToolType = "web_search",
    )
}
