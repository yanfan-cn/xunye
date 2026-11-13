package io.legado.app.ui.ai.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import io.legado.app.domain.gateway.AiChatGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiMessagePartJson
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AiToolApprovalState
import io.legado.app.domain.model.reasoningContent
import io.legado.app.domain.model.textContent
import io.legado.app.domain.model.toolParts
import io.legado.app.domain.model.usagePart
import io.legado.app.domain.usecase.AiChatGenerationUseCase
import io.legado.app.domain.usecase.PendingToolRun
import io.legado.app.domain.usecase.ToolTraceBuilder
import io.legado.app.model.ai.AiTokenEstimator
import io.legado.app.model.ai.AiTokenUsage
import io.legado.app.model.ai.AiUsageLedger
import io.legado.app.utils.GSON
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModel(
    private val aiChatGateway: AiChatGateway,
    private val aiProfileGateway: AiProfileGateway,
    private val generationUseCase: AiChatGenerationUseCase
) : ViewModel() {

    private val currentConversationId = MutableStateFlow<String?>(null)
    private var lastMessages: List<AiChatMessageUi> = emptyList()
    private var streamingJob: Job? = null
    private var pendingToolRun: PendingToolRun? = null
    private var thinkingStartTime: Long = 0L
    /** SPEC-250: 用户关闭未配置提示后，进页不再反复弹出，直至再次发送或配置状态变化。 */
    private var missingModelPromptDismissed = false

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiChatEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var lastReportedUsage: AiTokenUsage? = null
    private var lastStreamRequest: io.legado.app.domain.model.AiGenerateRequest? = null
    private var bootstrapDone = false

    init {
        observeConversations()
        observeCurrentMessages()
        observeModelConfigured()
        observeAvailableModels()
    }

    fun onIntent(intent: AiChatIntent) {
        when (intent) {
            AiChatIntent.NewConversation -> createConversation()
            is AiChatIntent.SelectConversation -> selectConversation(intent.id)
            is AiChatIntent.SendMessage -> sendMessage(intent.content)
            AiChatIntent.StopGenerating -> stopGenerating()
            AiChatIntent.ConfirmPendingTool -> confirmPendingTool()
            AiChatIntent.RejectPendingTool -> rejectPendingTool()
            is AiChatIntent.UpdateReasoningLevel -> updateReasoningLevel(intent.level)
            is AiChatIntent.RegenerateMessage -> regenerateMessage(intent.messageId)
            is AiChatIntent.SwitchBranch -> switchBranch(intent.messageId)
            is AiChatIntent.DeleteConversation -> deleteConversation(intent.id)
            is AiChatIntent.RenameConversation -> renameConversation(intent.id, intent.title)
            AiChatIntent.DismissMissingModelPrompt -> dismissMissingModelPrompt()
            AiChatIntent.OpenAiSettings -> {
                dismissMissingModelPrompt()
                _effects.tryEmit(AiChatEffect.OpenAiSettings)
            }
            AiChatIntent.OpenModelPicker -> {
                _uiState.update { it.copy(showModelPicker = true) }
            }
            AiChatIntent.DismissModelPicker -> {
                _uiState.update { it.copy(showModelPicker = false) }
            }
            is AiChatIntent.SelectModel -> selectModel(intent.modelProfileId)
            AiChatIntent.AddModel -> {
                _uiState.update { it.copy(showModelPicker = false) }
                _effects.tryEmit(AiChatEffect.OpenAiSettings)
            }
        }
    }

    private fun observeModelConfigured() {
        viewModelScope.launch {
            combine(
                aiProfileGateway.observePresets(),
                aiProfileGateway.observeModels(),
                aiProfileGateway.observeProviders(),
            ) { _, _, _ ->
                isDefaultModelConfigured()
            }.collect { configured ->
                if (configured) {
                    missingModelPromptDismissed = false
                }
                _uiState.update { current ->
                    current.copy(
                        isModelConfigured = configured,
                        showMissingModelPrompt = !configured && !missingModelPromptDismissed,
                    )
                }
            }
        }
    }

    private suspend fun isDefaultModelConfigured(): Boolean {
        return aiProfileGateway.getTaskPreset(AiTaskType.CHAT) != null ||
            aiProfileGateway.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER) != null
    }

    private fun dismissMissingModelPrompt() {
        missingModelPromptDismissed = true
        _uiState.update { it.copy(showMissingModelPrompt = false) }
    }

    private fun promptMissingModel() {
        missingModelPromptDismissed = false
        _uiState.update { it.copy(showMissingModelPrompt = true) }
    }

    // ---- Conversation lifecycle ----

    private fun observeConversations() {
        viewModelScope.launch {
            aiChatGateway.observeConversations().collect { conversations ->
                val selectedId = currentConversationId.value
                _uiState.update { current ->
                    val previousConversations = current.conversations.associateBy { it.id }
                    current.copy(
                        conversations = conversations.map {
                            val previous = previousConversations[it.id]
                            AiChatConversationUi(
                                id = it.id,
                                title = it.title,
                                updatedAt = it.updatedAt,
                                isSelected = it.id == selectedId,
                                providerName = previous?.providerName.orEmpty(),
                                modelName = previous?.modelName.orEmpty()
                            )
                        }.toImmutableList()
                    )
                }
                // SPEC-262: bootstrap once — default new chat; optional restore last.
                if (selectedId == null && !bootstrapDone) {
                    bootstrapDone = true
                    if (AiUsageLedger.restoreLastChatEnabled()) {
                        conversations.firstOrNull()?.let { selectConversation(it.id) }
                            ?: createConversation()
                    } else {
                        createConversation()
                    }
                }
            }
        }
    }

    private fun observeAvailableModels() {
        viewModelScope.launch {
            combine(
                aiProfileGateway.observeModels(),
                aiProfileGateway.observeProviders(),
                aiProfileGateway.observePresets(),
            ) { models, providers, presets ->
                Triple(models, providers, presets)
            }.collect { (models, providers, presets) ->
                val providerNames = providers.associate { it.id to it.name }
                val currentId = presets.firstOrNull {
                    it.taskType == AiTaskType.CHAT && it.isDefault
                }?.modelProfileId
                    ?: presets.firstOrNull {
                        it.taskType == AiTaskType.TRANSLATE_CHAPTER && it.isDefault
                    }?.modelProfileId
                val options = models.map { model ->
                    AiChatModelOptionUi(
                        modelProfileId = model.id,
                        providerName = providerNames[model.providerId].orEmpty(),
                        modelName = model.displayName.ifBlank { model.modelId },
                        isSelected = model.id == currentId,
                    )
                }
                val selected = options.firstOrNull { it.isSelected } ?: options.firstOrNull()
                _uiState.update { state ->
                    val syncedConversations = if (selected != null && selected.modelName.isNotBlank()) {
                        state.conversations.map { conv ->
                            if (conv.id == state.currentConversationId) {
                                conv.copy(
                                    providerName = selected.providerName,
                                    modelName = selected.modelName,
                                )
                            } else {
                                conv
                            }
                        }.toImmutableList()
                    } else {
                        state.conversations
                    }
                    state.copy(
                        availableModels = options.toImmutableList(),
                        currentModelProfileId = currentId,
                        conversations = syncedConversations,
                    )
                }
            }
        }
    }

    private fun selectModel(modelProfileId: String) {
        viewModelScope.launch {
            runCatching {
                aiProfileGateway.setDefaultModel(modelProfileId)
                val model = aiProfileGateway.getModel(modelProfileId)
                val provider = model?.let { aiProfileGateway.getProvider(it.providerId) }
                Triple(
                    model?.displayName.orEmpty().ifBlank { model?.modelId.orEmpty() },
                    provider?.name.orEmpty(),
                    modelProfileId,
                )
            }.onSuccess { (modelName, providerName, id) ->
                _uiState.update { state ->
                    state.copy(
                        showModelPicker = false,
                        currentModelProfileId = id,
                        conversations = state.conversations.map { conv ->
                            if (conv.id == state.currentConversationId) {
                                conv.copy(providerName = providerName, modelName = modelName)
                            } else conv
                        }.toImmutableList(),
                        availableModels = state.availableModels.map {
                            it.copy(isSelected = it.modelProfileId == id)
                        }.toImmutableList(),
                    )
                }
            }.onFailure { error ->
                _effects.tryEmit(AiChatEffect.ShowMessage(error.message ?: "切换模型失败"))
            }
        }
    }

    private fun observeCurrentMessages() {
        viewModelScope.launch {
            currentConversationId
                .filterNotNull()
                .flatMapLatest { aiChatGateway.observeSelectedMessages(it) }
                .collect { messages ->
                    // SPEC-263: decode/map off main to cut switch-jank on long chats.
                    val mapped = withContext(Dispatchers.Default) {
                        messages.map { msg ->
                            val parts = AiMessagePartJson.decode(msg.partsJson)
                            val usage = parts.usagePart()
                            AiChatMessageUi(
                                id = msg.id,
                                role = msg.role,
                                parts = parts.toImmutableList(),
                                content = parts.textContent(),
                                reasoning = parts.reasoningContent(),
                                toolTrace = parts.toolTraceText(),
                                createdAt = msg.createdAt,
                                thinkingDuration = msg.thinkingDuration,
                                bookResults = parts.bookResults().toImmutableList(),
                                branchIndex = msg.branchIndex,
                                totalBranches = 1, // will be updated below
                                parentMessageId = msg.parentMessageId,
                                tokenUsage = usage?.let {
                                    AiChatTokenUsageUi(
                                        promptTokens = it.promptTokens,
                                        completionTokens = it.completionTokens,
                                        totalTokens = it.totalTokens,
                                        estimated = it.estimated,
                                    )
                                },
                            )
                        }
                    }
                    val conversationId = currentConversationId.value
                    val branchCounts = if (conversationId != null) {
                        runCatching { aiChatGateway.getBranchCounts(conversationId) }.getOrNull()
                    } else null
                    lastMessages = mapped.map { msg ->
                        if (msg.role == AiMessageRole.ASSISTANT && msg.parentMessageId != null) {
                            val count = branchCounts?.get(msg.parentMessageId) ?: 1
                            msg.copy(totalBranches = count)
                        } else msg
                    }
                    _uiState.update { it.copy(messages = lastMessages.toImmutableList()) }
                }
        }
    }

    private fun createConversation() {
        viewModelScope.launch {
            runCatching {
                aiChatGateway.createConversation()
            }.onSuccess {
                selectConversation(it.id)
            }.onFailure { error ->
                _effects.tryEmit(AiChatEffect.ShowMessage(error.message ?: "Failed to create chat"))
            }
        }
    }

    private fun selectConversation(id: String) {
        streamingJob?.cancel()
        pendingToolRun = null
        currentConversationId.value = id
        // SPEC-262: clear immediately to avoid painting previous chat during switch.
        _uiState.update {
            it.copy(
                currentConversationId = id,
                messages = persistentListOf(),
                streamingMessage = null,
                pendingToolConfirmation = null,
                isSending = false,
            )
        }
        viewModelScope.launch {
            val conversation = aiChatGateway.getConversation(id)
            val preset = aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
                ?: aiProfileGateway.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)
            val providerName = preset?.model?.provider?.name.orEmpty()
            val modelName = preset?.model?.displayName.orEmpty()

            _uiState.update { current ->
                current.copy(
                    currentConversationId = id,
                    // SPEC-260: chat always AUTO regardless of stored conversation value.
                    reasoningLevel = AiReasoningLevel.AUTO,
                    streamingMessage = null,
                    pendingToolConfirmation = null,
                    isSending = false,
                    conversations = current.conversations.map {
                        if (it.id == id) {
                            it.copy(
                                isSelected = true,
                                providerName = providerName,
                                modelName = modelName
                            )
                        } else {
                            it.copy(isSelected = false)
                        }
                    }.toImmutableList()
                )
            }
        }
    }

    // ---- Generation ----

    private fun stopGenerating() {
        streamingJob?.cancel()
    }

    private fun confirmPendingTool() {
        val pending = pendingToolRun ?: return
        pendingToolRun = null
        streamingJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isSending = true, pendingToolConfirmation = null)
            }
            continueAfterToolApproval(pending)
        }
    }

    private fun rejectPendingTool() {
        val pending = pendingToolRun ?: return
        pendingToolRun = null
        val rejectionText = "工具调用已被你拒绝，我不会执行这些操作。还需要我继续帮你做什么？"
        val assistantText = buildString {
            pending.fullText.trim().takeIf { it.isNotBlank() }?.let {
                append(it)
                append("\n\n")
            }
            append(rejectionText)
        }
        val rejectedToolParts = pending.deniedToolParts()
        val reasoning = pending.fullReasoning.takeIf { it.isNotBlank() }
        streamingJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSending = false,
                    pendingToolConfirmation = null,
                    streamingMessage = AiChatMessageUi(
                        id = "tool_rejected_temp",
                        role = AiMessageRole.ASSISTANT,
                        content = assistantText,
                        reasoning = reasoning,
                        toolTrace = null,
                        createdAt = System.currentTimeMillis()
                    ).withDisplayParts(rejectedToolParts)
                )
            }
            val saveResult = runCatching {
                pending.conversationId?.let { conversationId ->
                    aiChatGateway.saveMessage(
                        conversationId = conversationId,
                        role = AiMessageRole.ASSISTANT,
                        parts = buildList {
                            reasoning?.let { add(AiMessagePart.Reasoning(it)) }
                            add(AiMessagePart.Text(assistantText))
                            addAll(rejectedToolParts)
                        },
                        parentMessageId = pending.parentMessageId
                    )
                }
            }
            saveResult.onSuccess {
                _uiState.update { it.copy(streamingMessage = null) }
            }.onFailure { error ->
                _effects.tryEmit(AiChatEffect.ShowMessage(error.message ?: "Failed to save rejected tool response"))
            }
            if (streamingJob == currentCoroutineContext()[Job]) {
                streamingJob = null
            }
        }
    }

    /** SPEC-260: chat UI locks to AUTO; ignore multi-level changes. */
    private fun updateReasoningLevel(@Suppress("UNUSED_PARAMETER") level: AiReasoningLevel) {
        _uiState.update { it.copy(reasoningLevel = AiReasoningLevel.AUTO) }
        val conversationId = currentConversationId.value ?: return
        viewModelScope.launch {
            aiChatGateway.updateReasoningLevel(conversationId, AiReasoningLevel.AUTO.name.lowercase())
        }
    }

    private fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            aiChatGateway.deleteConversation(conversationId)
            if (currentConversationId.value == conversationId) {
                currentConversationId.value = null
                _uiState.update { it.copy(messages = persistentListOf(), currentConversationId = null) }
                createConversation()
            }
        }
    }

    private fun renameConversation(conversationId: String, title: String) {
        viewModelScope.launch {
            aiChatGateway.updateConversationTitle(conversationId, title)
        }
    }

    private fun generateConversationTitle(conversationId: String, userContent: String, assistantContent: String) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val shortTitle = generationUseCase.generateTitle(
                    userContent = userContent,
                    assistantContent = assistantContent,
                    reasoningLevel = AiReasoningLevel.AUTO
                )
                if (shortTitle.isNotBlank()) {
                    aiChatGateway.updateConversationTitle(conversationId, shortTitle)
                }
            } catch (_: Exception) {
                // Fallback to truncated user message
                aiChatGateway.updateConversationTitle(conversationId, userContent.take(24))
            }
        }
    }

    private fun regenerateMessage(messageId: String) {
        val message = lastMessages.find { it.id == messageId }
            ?: return
        if (message.role != AiMessageRole.ASSISTANT || message.parentMessageId == null) return
        if (!_uiState.value.isModelConfigured) {
            promptMissingModel()
            return
        }
        val parentUserMsg = lastMessages.find { it.id == message.parentMessageId }
            ?: return
        val historyBeforeParent = lastMessages.filter { it.createdAt < parentUserMsg.createdAt }
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            val conversationId = currentConversationId.value ?: return@launch
            val fullText = StringBuilder()
            val fullReasoning = StringBuilder()
            val toolTrace = ToolTraceBuilder()
            var failureMessage: String? = null
            var wasCancelled = false
            var waitingForToolConfirmation = false
            try {
                val state = _uiState.value
                val request = generationUseCase.buildRequest(
                    userContent = parentUserMsg.content,
                    history = historyBeforeParent,
                    reasoningLevel = AiReasoningLevel.AUTO,
                    conversationId = conversationId
                )
                setStreamingPlaceholder()
                try {
                    collectStream(request, fullText, fullReasoning, toolTrace)
                    waitingForToolConfirmation = continueToolRounds(
                        conversationId = conversationId,
                        request = request,
                        fullText = fullText,
                        fullReasoning = fullReasoning,
                        toolTrace = toolTrace,
                        startRound = 0,
                        assistantTextStart = 0,
                        parentMessageId = message.parentMessageId
                    )
                } catch (e: CancellationException) {
                    wasCancelled = true
                    throw e
                } catch (e: Exception) {
                    failureMessage = "Stream interrupted: ${e.message ?: "unknown error"}"
                    _effects.tryEmit(AiChatEffect.ShowMessage(failureMessage))
                }
            } catch (e: CancellationException) {
                wasCancelled = true
                throw e
            } catch (e: Exception) {
                failureMessage = e.message ?: "AI chat failed"
                _effects.tryEmit(AiChatEffect.ShowMessage(failureMessage))
            } finally {
                val assistantContent = when {
                    waitingForToolConfirmation -> null
                    fullText.isNotEmpty() -> fullText.toString()
                    !wasCancelled && !failureMessage.isNullOrBlank() -> "请求失败：$failureMessage"
                    else -> null
                }
                if (assistantContent != null) {
                    val duration = _uiState.value.streamingMessage?.thinkingDuration ?: 0
                    runCatching {
                        aiChatGateway.saveRegeneratedMessage(
                            conversationId = conversationId,
                            role = AiMessageRole.ASSISTANT,
                            parts = generationUseCase.buildAssistantParts(
                                text = assistantContent,
                                reasoning = fullReasoning.toString(),
                                toolTrace = toolTrace,
                                usage = usagePartForSave(assistantContent),
                            ),
                            parentMessageId = message.parentMessageId,
                            thinkingDuration = duration
                        )
                    }
                }
                _uiState.update {
                    if (waitingForToolConfirmation) it
                    else it.copy(isSending = false, streamingMessage = null)
                }
                if (streamingJob == currentCoroutineContext()[Job]) {
                    streamingJob = null
                }
            }
        }
    }

    private fun switchBranch(messageId: String) {
        viewModelScope.launch {
            runCatching {
                aiChatGateway.selectBranch(messageId)
            }.onFailure { error ->
                _effects.tryEmit(AiChatEffect.ShowMessage(error.message ?: "Failed to switch branch"))
            }
        }
    }

    private fun sendMessage(rawContent: String) {
        val content = rawContent.trim()
        if (content.isBlank() || _uiState.value.isSending) return
        if (!_uiState.value.isModelConfigured) {
            promptMissingModel()
            return
        }
        streamingJob?.cancel()
        val historySnapshot = lastMessages
        streamingJob = viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            var conversationIdForMsg: String? = null
            var parentMessageId: String? = null
            val fullText = StringBuilder()
            val fullReasoning = StringBuilder()
            val toolTrace = ToolTraceBuilder()
            var failureMessage: String? = null
            var wasCancelled = false
            var waitingForToolConfirmation = false
            try {
                val conversationId = currentConversationId.value
                    ?: aiChatGateway.createConversation().id.also {
                        currentConversationId.value = it
                    }
                val userMessage = aiChatGateway.saveMessage(
                    conversationId = conversationId,
                    role = AiMessageRole.USER,
                    parts = listOf(AiMessagePart.Text(content))
                )
                conversationIdForMsg = userMessage.conversationId
                parentMessageId = userMessage.id
                val state = _uiState.value
                val request = generationUseCase.buildRequest(
                    userContent = content,
                    history = historySnapshot,
                    reasoningLevel = AiReasoningLevel.AUTO,
                    conversationId = conversationIdForMsg
                )
                setStreamingPlaceholder()
                try {
                    collectStream(request, fullText, fullReasoning, toolTrace)
                    waitingForToolConfirmation = continueToolRounds(
                        conversationId = conversationIdForMsg,
                        request = request,
                        fullText = fullText,
                        fullReasoning = fullReasoning,
                        toolTrace = toolTrace,
                        startRound = 0,
                        assistantTextStart = 0,
                        parentMessageId = parentMessageId
                    )
                } catch (e: CancellationException) {
                    wasCancelled = true
                    throw e
                } catch (e: Exception) {
                    failureMessage = "Stream interrupted: ${e.message ?: "unknown error"}"
                    _effects.tryEmit(AiChatEffect.ShowMessage(failureMessage))
                }
            } catch (e: CancellationException) {
                wasCancelled = true
                throw e
            } catch (e: Exception) {
                failureMessage = e.message ?: "AI chat failed"
                _effects.tryEmit(AiChatEffect.ShowMessage(failureMessage))
            } finally {
                val assistantContent = when {
                    waitingForToolConfirmation -> null
                    fullText.isNotEmpty() -> fullText.toString()
                    !wasCancelled && !failureMessage.isNullOrBlank() -> "请求失败：$failureMessage"
                    else -> null
                }
                if (conversationIdForMsg != null && assistantContent != null) {
                    val duration = _uiState.value.streamingMessage?.thinkingDuration ?: 0
                    runCatching {
                        aiChatGateway.saveMessage(
                            conversationId = conversationIdForMsg,
                            role = AiMessageRole.ASSISTANT,
                            parts = generationUseCase.buildAssistantParts(
                                text = assistantContent,
                                reasoning = fullReasoning.toString(),
                                toolTrace = toolTrace,
                                usage = usagePartForSave(assistantContent),
                            ),
                            parentMessageId = parentMessageId,
                            thinkingDuration = duration
                        )
                    }
                    // Generate AI title for first conversation
                    if (historySnapshot.none { it.role == AiMessageRole.ASSISTANT }) {
                        generateConversationTitle(conversationIdForMsg, content, assistantContent)
                    }
                }
                _uiState.update {
                    if (waitingForToolConfirmation) it
                    else it.copy(isSending = false, streamingMessage = null)
                }
                if (streamingJob == currentCoroutineContext()[Job]) {
                    streamingJob = null
                }
            }
        }
    }

    private suspend fun continueAfterToolApproval(pending: PendingToolRun) {
        val fullText = StringBuilder(pending.fullText)
        val fullReasoning = StringBuilder(pending.fullReasoning)
        var waitingForToolConfirmation = false
        try {
            val assistantContent = fullText.substring(pending.assistantTextStart)
            val followUpRequest = generationUseCase.executeToolCalls(
                request = pending.request,
                assistantContent = assistantContent,
                toolTrace = pending.toolTrace,
                toolCalls = pending.toolCalls,
                onToolTraceUpdate = { updateStreamingToolTrace(pending.toolTrace) }
            )
            val nextAssistantTextStart = fullText.length
            collectStream(followUpRequest, fullText, fullReasoning, pending.toolTrace)
            waitingForToolConfirmation = continueToolRounds(
                conversationId = pending.conversationId,
                request = followUpRequest,
                fullText = fullText,
                fullReasoning = fullReasoning,
                toolTrace = pending.toolTrace,
                startRound = pending.round + 1,
                assistantTextStart = nextAssistantTextStart,
                parentMessageId = pending.parentMessageId
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _effects.tryEmit(AiChatEffect.ShowMessage(e.message ?: "AI tool failed"))
        } finally {
            if (!waitingForToolConfirmation && pending.conversationId != null && fullText.isNotEmpty()) {
                val duration = _uiState.value.streamingMessage?.thinkingDuration ?: 0
                val text = fullText.toString()
                runCatching {
                    aiChatGateway.saveMessage(
                        conversationId = pending.conversationId,
                        role = AiMessageRole.ASSISTANT,
                        parts = generationUseCase.buildAssistantParts(
                            text = text,
                            reasoning = fullReasoning.toString(),
                            toolTrace = pending.toolTrace,
                            usage = usagePartForSave(text),
                        ),
                        parentMessageId = pending.parentMessageId,
                        thinkingDuration = duration
                    )
                }
            }
            _uiState.update {
                if (waitingForToolConfirmation) it
                else it.copy(isSending = false, streamingMessage = null)
            }
            if (streamingJob == currentCoroutineContext()[Job]) {
                streamingJob = null
            }
        }
    }

    private suspend fun continueToolRounds(
        conversationId: String?,
        request: io.legado.app.domain.model.AiGenerateRequest,
        fullText: StringBuilder,
        fullReasoning: StringBuilder,
        toolTrace: ToolTraceBuilder,
        startRound: Int,
        assistantTextStart: Int,
        parentMessageId: String? = null
    ): Boolean {
        var currentRequest = request
        var currentRound = startRound
        var currentAssistantTextStart = assistantTextStart
        while (true) {
            val toolCalls = toolTrace.pendingToolCalls()
            if (toolCalls.isEmpty()) return false
            if (toolCalls.any { generationUseCase.requiresConfirmation(it.name) }) {
                pendingToolRun = PendingToolRun(
                    conversationId = conversationId,
                    request = currentRequest,
                    fullText = fullText.toString(),
                    fullReasoning = fullReasoning.toString(),
                    toolTrace = toolTrace,
                    toolCalls = toolCalls,
                    assistantTextStart = currentAssistantTextStart,
                    round = currentRound,
                    parentMessageId = parentMessageId
                )
                _uiState.update {
                    it.copy(
                        isSending = false,
                        pendingToolConfirmation = AiToolConfirmationUi(
                            title = toolCalls.joinToString { call -> call.name },
                            description = toolTrace.toString().take(2000)
                        )
                    )
                }
                return true
            }
            currentRequest = generationUseCase.executeToolCalls(
                request = currentRequest,
                assistantContent = fullText.substring(currentAssistantTextStart),
                toolTrace = toolTrace,
                toolCalls = toolCalls,
                onToolTraceUpdate = { updateStreamingToolTrace(toolTrace) }
            )
            currentRound += 1
            currentAssistantTextStart = fullText.length
            collectStream(currentRequest, fullText, fullReasoning, toolTrace)
        }
    }

    // ---- Streaming helpers ----

    private suspend fun collectStream(
        request: io.legado.app.domain.model.AiGenerateRequest,
        fullText: StringBuilder,
        fullReasoning: StringBuilder,
        toolTrace: ToolTraceBuilder
    ) {
        lastReportedUsage = null
        lastStreamRequest = request
        generationUseCase.collectStream(
            request = request,
            toolTrace = toolTrace,
            onContent = { delta ->
                // First content token: freeze thinking duration
                if (fullText.isEmpty() && thinkingStartTime > 0) {
                    val duration = ((System.currentTimeMillis() - thinkingStartTime) / 1000).toInt()
                    _uiState.update { state ->
                        state.streamingMessage?.let { msg ->
                            state.copy(streamingMessage = msg.copy(thinkingDuration = duration))
                        } ?: state
                    }
                }
                fullText.append(delta)
                _uiState.update { state ->
                    state.streamingMessage?.let { msg ->
                        state.copy(
                            streamingMessage = msg.copy(content = fullText.toString())
                                .withDisplayParts()
                        )
                    } ?: state
                }
            },
            onReasoning = { delta ->
                // First reasoning token: record start time
                if (fullReasoning.isEmpty()) {
                    thinkingStartTime = System.currentTimeMillis()
                }
                fullReasoning.append(delta)
                _uiState.update { state ->
                    state.streamingMessage?.let { msg ->
                        state.copy(
                            streamingMessage = msg.copy(reasoning = fullReasoning.toString())
                                .withDisplayParts()
                        )
                    } ?: state
                }
            },
            onToolTraceUpdate = { updateStreamingToolTrace(toolTrace) },
            onUsage = { usage ->
                lastReportedUsage = AiTokenUsage(
                    promptTokens = usage.promptTokens,
                    completionTokens = usage.completionTokens,
                    totalTokens = usage.totalTokens,
                    estimated = false,
                )
            },
        )
    }

    /** SPEC-262: prefer API usage; else estimate from prompt+completion text. */
    private fun resolveUsage(
        request: io.legado.app.domain.model.AiGenerateRequest,
        completion: String,
    ): AiTokenUsage {
        lastReportedUsage?.let { return it }
        val promptText = request.messages.joinToString("\n") { it.content.orEmpty() }
        return AiTokenEstimator.estimatePair(promptText, completion)
    }

    private fun AiTokenUsage.toPart(): AiMessagePart.Usage =
        AiMessagePart.Usage(
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens,
            estimated = estimated,
        )

    private fun AiTokenUsage.toUi(): AiChatTokenUsageUi =
        AiChatTokenUsageUi(
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens,
            estimated = estimated,
        )

    private fun usagePartForSave(completion: String): AiMessagePart.Usage {
        val req = lastStreamRequest
        val usage = if (req != null) {
            resolveUsage(req, completion)
        } else {
            AiTokenEstimator.estimatePair("", completion)
        }
        AiUsageLedger.record(usage)
        return usage.toPart()
    }

    private fun setStreamingPlaceholder() {
        thinkingStartTime = 0L
        _uiState.update {
            it.copy(
                streamingMessage = AiChatMessageUi(
                    id = "streaming_temp",
                    role = AiMessageRole.ASSISTANT,
                    content = "",
                    reasoning = null,
                    toolTrace = null,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun updateStreamingToolTrace(toolTrace: ToolTraceBuilder) {
        _uiState.update { state ->
            state.streamingMessage?.let { msg ->
                state.copy(
                    streamingMessage = msg.copy(
                        toolTrace = toolTrace.toString()
                    ).withDisplayParts(toolTrace.toParts())
                )
            } ?: state
        }
    }

    private fun AiChatMessageUi.withDisplayParts(
        toolParts: List<AiMessagePart> = parts.filter {
            it is AiMessagePart.Tool || it is AiMessagePart.BookResult
        }
    ): AiChatMessageUi {
        return copy(
            parts = buildList {
                reasoning?.takeIf { it.isNotBlank() }?.let { add(AiMessagePart.Reasoning(it)) }
                content.takeIf { it.isNotBlank() }?.let { add(AiMessagePart.Text(it)) }
                addAll(toolParts)
            }.toImmutableList()
        )
    }

    private fun PendingToolRun.deniedToolParts(): List<AiMessagePart> {
        val deniedCallIds = toolCalls.mapTo(mutableSetOf()) { it.id }
        return toolTrace.toParts().map { part ->
            if (part is AiMessagePart.Tool &&
                part.toolCallId in deniedCallIds &&
                part.output.isBlank()
            ) {
                part.copy(
                    output = "用户拒绝执行此工具调用。",
                    approvalState = AiToolApprovalState.DENIED
                )
            } else {
                part
            }
        }
    }

    // ---- Display helpers ----

    private fun List<AiMessagePart>.bookResults(): List<AiChatBookResultUi> {
        val explicit = filterIsInstance<AiMessagePart.BookResult>().map {
            AiChatBookResultUi(
                bookUrl = it.bookUrl,
                name = it.name,
                author = it.author,
                origin = it.origin,
                coverPath = it.coverPath,
                latestChapterTitle = it.latestChapterTitle,
                currentChapterTitle = it.currentChapterTitle,
                intro = it.intro
            )
        }
        if (explicit.isNotEmpty()) return explicit.distinctBy { it.bookUrl }
        val books = linkedMapOf<String, AiChatBookResultUi>()
        toolParts().forEach { tool ->
            val root = runCatching {
                GSON.fromJson(tool.output, JsonObject::class.java)
            }.getOrNull() ?: return@forEach
            root.getAsJsonArrayOrNull("books")?.forEach { element ->
                element.asJsonObjectOrNull()?.toBookResultUi()?.let { books.putIfAbsent(it.bookUrl, it) }
            }
            root.getAsJsonObjectOrNull("book")?.toBookResultUi()?.let {
                books.putIfAbsent(it.bookUrl, it)
            }
        }
        return books.values.toList()
    }

    private fun List<AiMessagePart>.toolTraceText(): String? {
        val tools = toolParts()
        if (tools.isEmpty()) return null
        return tools.joinToString("\n\n") { tool ->
            buildString {
                append("Tool: ")
                append(tool.toolName.ifBlank { tool.rawType.ifBlank { tool.toolCallId } })
                append('\n')
                append("ID: ")
                append(tool.toolCallId)
                if (tool.input.isNotBlank()) {
                    append('\n')
                    append(tool.input)
                }
                if (tool.approvalState != AiToolApprovalState.AUTO) {
                    append('\n')
                    append("Approval: ")
                    append(tool.approvalState.name.lowercase())
                }
                if (tool.output.isNotBlank()) {
                    append('\n')
                    append("Result: ")
                    append(tool.output.take(2000))
                }
            }
        }.takeIf { it.isNotBlank() }
    }

}

private fun JsonObject.toBookResultUi(): AiChatBookResultUi? {
    val bookUrl = string("bookUrl")?.takeIf { it.isNotBlank() } ?: return null
    return AiChatBookResultUi(
        bookUrl = bookUrl,
        name = string("name").orEmpty(),
        author = string("author").orEmpty(),
        // Prefer engine origin (browsesth://…) over display originName for SPEC-261 labeling.
        origin = string("origin") ?: string("originName"),
        coverPath = string("coverPath") ?: string("coverUrl"),
        latestChapterTitle = string("latestChapterTitle"),
        currentChapterTitle = string("currentChapterTitle"),
        intro = string("intro")
    )
}

private fun JsonObject.string(name: String): String? =
    get(name)?.takeIf { !it.isJsonNull }?.asString

private fun JsonObject.getAsJsonObjectOrNull(name: String): JsonObject? =
    get(name)?.let { if (it.isJsonObject) it.asJsonObject else null }

private fun JsonObject.getAsJsonArrayOrNull(name: String) = runCatching {
    get(name)?.takeIf { !it.isJsonNull && it.isJsonArray }?.asJsonArray
}.getOrNull()

private fun com.google.gson.JsonElement.asJsonObjectOrNull(): JsonObject? =
    takeIf { !it.isJsonNull && it.isJsonObject }?.asJsonObject
