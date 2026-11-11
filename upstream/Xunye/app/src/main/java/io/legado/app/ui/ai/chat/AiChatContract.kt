package io.legado.app.ui.ai.chat

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiReasoningLevel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class AiChatUiState(
    val conversations: ImmutableList<AiChatConversationUi> = persistentListOf(),
    val messages: ImmutableList<AiChatMessageUi> = persistentListOf(),
    val currentConversationId: String? = null,
    val reasoningLevel: AiReasoningLevel = AiReasoningLevel.AUTO,
    val isSending: Boolean = false,
    val streamingMessage: AiChatMessageUi? = null,
    val pendingToolConfirmation: AiToolConfirmationUi? = null,
    /** SPEC-250: 是否已配置可用于对话的默认模型。 */
    val isModelConfigured: Boolean = true,
    /** SPEC-250: 未配置模型时的引导对话框。 */
    val showMissingModelPrompt: Boolean = false,
    /** SPEC-262: model picker sheet. */
    val showModelPicker: Boolean = false,
    val availableModels: ImmutableList<AiChatModelOptionUi> = persistentListOf(),
    val currentModelProfileId: String? = null,
)

@Stable
data class AiChatConversationUi(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val isSelected: Boolean,
    val providerName: String = "",
    val modelName: String = ""
)

@Stable
data class AiChatMessageUi(
    val id: String,
    val role: String,
    val parts: ImmutableList<AiMessagePart> = persistentListOf(),
    val content: String,
    val reasoning: String?,
    val toolTrace: String?,
    val createdAt: Long,
    val thinkingDuration: Int = 0,
    val bookResults: ImmutableList<AiChatBookResultUi> = persistentListOf(),
    val branchIndex: Int = 0,
    val totalBranches: Int = 1,
    val parentMessageId: String? = null,
    /** SPEC-262 */
    val tokenUsage: AiChatTokenUsageUi? = null,
)

@Stable
data class AiChatBookResultUi(
    val bookUrl: String,
    val name: String,
    val author: String,
    val origin: String?,
    val coverPath: String?,
    val latestChapterTitle: String?,
    val currentChapterTitle: String?,
    val intro: String?
)

@Stable
data class AiChatModelOptionUi(
    val modelProfileId: String,
    val providerName: String,
    val modelName: String,
    val isSelected: Boolean,
)

@Stable
data class AiChatTokenUsageUi(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val estimated: Boolean,
)

@Stable
data class AiToolConfirmationUi(
    val title: String,
    val description: String
)

sealed interface AiChatIntent {
    data object NewConversation : AiChatIntent
    data class SelectConversation(val id: String) : AiChatIntent
    data class SendMessage(val content: String) : AiChatIntent
    data object StopGenerating : AiChatIntent
    data object ConfirmPendingTool : AiChatIntent
    data object RejectPendingTool : AiChatIntent
    data class UpdateReasoningLevel(val level: AiReasoningLevel) : AiChatIntent
    data class RegenerateMessage(val messageId: String) : AiChatIntent
    data class SwitchBranch(val messageId: String) : AiChatIntent
    data class DeleteConversation(val id: String) : AiChatIntent
    data class RenameConversation(val id: String, val title: String) : AiChatIntent
    /** SPEC-250: 关闭未配置模型提示。 */
    data object DismissMissingModelPrompt : AiChatIntent
    /** SPEC-250: 从提示跳转 AI 设置。 */
    data object OpenAiSettings : AiChatIntent
    /** SPEC-262 */
    data object OpenModelPicker : AiChatIntent
    data object DismissModelPicker : AiChatIntent
    data class SelectModel(val modelProfileId: String) : AiChatIntent
    data object AddModel : AiChatIntent
}

sealed interface AiChatEffect {
    data class ShowMessage(val message: String) : AiChatEffect
    /** SPEC-250: 打开设置族 AI 设置页。 */
    data object OpenAiSettings : AiChatEffect
}
