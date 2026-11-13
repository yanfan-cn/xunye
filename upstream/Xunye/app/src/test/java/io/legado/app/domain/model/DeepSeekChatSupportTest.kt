package io.legado.app.domain.model

import io.legado.app.data.repository.ai.toOpenAiResponsesTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekChatSupportTest {

    private fun provider(
        protocol: String = AiProtocol.OPENAI_CHAT_COMPLETIONS,
        baseUrl: String = "https://api.deepseek.com",
    ) = AiProviderConfig(
        id = "ds",
        name = "DeepSeek",
        protocol = protocol,
        baseUrl = baseUrl,
        apiKey = "sk-test",
    )

    private fun model(
        modelId: String,
        protocol: String = AiProtocol.OPENAI_CHAT_COMPLETIONS,
    ) = AiModelConfig(
        id = "m1",
        provider = provider(protocol),
        displayName = modelId,
        modelId = modelId,
    )

    @Test
    fun withResponsesProtocol_upgradesDeepSeekChatCompletions() {
        val upgraded = DeepSeekChatSupport.withResponsesProtocol(model("deepseek-flash"))
        assertEquals(AiProtocol.OPENAI_RESPONSES, upgraded.provider.protocol)
    }

    @Test
    fun supportsOfficialWebSearch_onlyPro() {
        assertTrue(DeepSeekChatSupport.supportsOfficialWebSearch(model("deepseek-v4-pro")))
        assertFalse(DeepSeekChatSupport.supportsOfficialWebSearch(model("deepseek-flash")))
        assertFalse(
            DeepSeekChatSupport.supportsOfficialWebSearch(
                model(
                    "gpt-4.1-mini",
                    protocol = AiProtocol.OPENAI_RESPONSES,
                ).copy(
                    provider = provider(
                        protocol = AiProtocol.OPENAI_RESPONSES,
                        baseUrl = "https://api.openai.com/v1",
                    )
                )
            )
        )
    }

    @Test
    fun webSearchTool_serializesAsBuiltInType() {
        val tools = listOf(
            DeepSeekChatSupport.webSearchTool(),
            AiToolDefinition(
                name = "search_books",
                description = "Search books",
                inputSchema = mapOf("type" to "object"),
            ),
        ).toOpenAiResponsesTools()

        assertEquals("web_search", tools[0]["type"])
        assertEquals(null, tools[0]["name"])
        assertEquals("function", tools[1]["type"])
        assertEquals("search_books", tools[1]["name"])
    }
}
