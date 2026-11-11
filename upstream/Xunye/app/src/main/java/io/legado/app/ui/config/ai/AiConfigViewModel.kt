package io.legado.app.ui.config.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiModelDraft
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiProviderDraft
import io.legado.app.domain.model.AiProviderPresets
import io.legado.app.domain.model.AiTaskType
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx

class AiConfigViewModel(
    private val aiProfileGateway: AiProfileGateway,
    private val aiTextGateway: AiTextGateway,
) : ViewModel() {

    private val wizardPresets = AiProviderPresets.wizardItems

    private val _uiState = MutableStateFlow(
        AiConfigUiState(
            wizardPresets = wizardPresets.map {
                AiWizardPresetUi(it.id, it.name, it.baseUrl)
            }.toImmutableList(),
            selectedPresetId = wizardPresets.firstOrNull()?.id.orEmpty(),
            modelId = wizardPresets.firstOrNull()?.modelId.orEmpty(),
            modelDisplayName = wizardPresets.firstOrNull()?.modelName.orEmpty(),
            suggestedModels = wizardPresets.firstOrNull()?.suggestedModels.orEmpty()
                .map { AiWizardModelUi(it.displayName, it.modelId) }
                .toImmutableList(),
        ),
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiConfigEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                aiProfileGateway.observeProviders(),
                aiProfileGateway.observeModels(),
                aiProfileGateway.observePresets(),
            ) { providers, models, presets ->
                Triple(providers, models, presets)
            }.collect { (providers, models, presets) ->
                val providerMap = providers.associateBy { it.id }
                val defaultTranslatePreset = presets.firstOrNull {
                    it.taskType == AiTaskType.TRANSLATE_CHAPTER && it.isDefault
                }
                val currentModelProfileId = defaultTranslatePreset?.modelProfileId
                    ?: models.firstOrNull()?.id
                val modelItems = models.mapNotNull { model ->
                    val provider = providerMap[model.providerId] ?: return@mapNotNull null
                    AiModelListItemUi(
                        providerId = provider.id,
                        modelProfileId = model.id,
                        providerName = provider.name,
                        protocol = provider.protocol,
                        baseUrl = provider.baseUrl,
                        modelName = model.displayName,
                        modelId = model.modelId,
                        contextWindow = model.contextWindow,
                        maxOutputTokens = model.maxOutputTokens,
                        enabled = provider.enabled && model.enabled,
                        isCurrent = model.id == currentModelProfileId,
                    )
                }
                val modelItemsByProvider = modelItems.groupBy { it.providerId }
                val providerItems = providers.map { provider ->
                    val providerModels = modelItemsByProvider[provider.id].orEmpty().toImmutableList()
                    AiProviderListItemUi(
                        providerId = provider.id,
                        providerName = provider.name,
                        protocol = provider.protocol,
                        baseUrl = provider.baseUrl,
                        modelCount = providerModels.size,
                        enabled = provider.enabled,
                        models = providerModels,
                    )
                }.toImmutableList()
                val currentModel = models.firstOrNull { it.id == currentModelProfileId }
                val currentProvider = currentModel?.providerId?.let { providerMap[it] }

                _uiState.update { current ->
                    val shouldPrefill = current.apiKey.isBlank() &&
                        current.modelId == wizardPresets.firstOrNull()?.modelId &&
                        currentModel != null &&
                        currentProvider != null
                    val matchedPreset = if (shouldPrefill) {
                        wizardPresets.firstOrNull { preset ->
                            normalizeUrl(preset.baseUrl) == normalizeUrl(currentProvider.baseUrl) ||
                                preset.name.equals(currentProvider.name, ignoreCase = true)
                        }
                    } else {
                        null
                    }
                    val suggested = (matchedPreset ?: wizardPresets.firstOrNull {
                        it.id == current.selectedPresetId
                    })?.suggestedModels.orEmpty()
                        .map { AiWizardModelUi(it.displayName, it.modelId) }
                        .toImmutableList()
                    val modelId = if (shouldPrefill) currentModel.modelId else current.modelId
                    val custom = shouldPrefill &&
                        suggested.none { it.modelId == modelId }
                    current.copy(
                        providers = providerItems,
                        models = modelItems.toImmutableList(),
                        currentModelProfileId = currentModelProfileId,
                        currentModelName = currentModel?.displayName.orEmpty(),
                        currentProviderName = currentProvider?.name.orEmpty(),
                        selectedPresetId = matchedPreset?.id ?: current.selectedPresetId,
                        apiKey = if (shouldPrefill) currentProvider.apiKey else current.apiKey,
                        modelId = modelId,
                        modelDisplayName = if (shouldPrefill) {
                            currentModel.displayName
                        } else {
                            current.modelDisplayName
                        },
                        suggestedModels = suggested,
                        customModel = if (shouldPrefill) custom else current.customModel,
                    )
                }
            }
        }
    }

    fun onIntent(intent: AiConfigIntent) {
        when (intent) {
            is AiConfigIntent.SelectPreset -> selectPreset(intent.presetId)
            is AiConfigIntent.UpdateApiKey -> _uiState.update { it.copy(apiKey = intent.value) }
            is AiConfigIntent.SelectSuggestedModel -> _uiState.update {
                it.copy(
                    customModel = false,
                    modelId = intent.modelId,
                    modelDisplayName = intent.displayName,
                )
            }
            AiConfigIntent.SelectCustomModel -> _uiState.update {
                it.copy(customModel = true)
            }
            is AiConfigIntent.UpdateModelId -> _uiState.update {
                it.copy(
                    modelId = intent.value,
                    modelDisplayName = intent.value,
                    customModel = true,
                )
            }
            AiConfigIntent.SaveWizard -> saveWizard()
            AiConfigIntent.TestWizardConnection -> testWizardConnection()
            is AiConfigIntent.SetDefaultModel -> setDefaultModel(intent.modelProfileId)
        }
    }

    private fun selectPreset(presetId: String) {
        val preset = wizardPresets.firstOrNull { it.id == presetId } ?: return
        _uiState.update {
            it.copy(
                selectedPresetId = preset.id,
                modelId = preset.modelId,
                modelDisplayName = preset.modelName,
                customModel = false,
                suggestedModels = preset.suggestedModels
                    .map { m -> AiWizardModelUi(m.displayName, m.modelId) }
                    .toImmutableList(),
            )
        }
    }

    private fun testWizardConnection() {
        val state = _uiState.value
        if (state.testing || state.saving) return
        val preset = wizardPresets.firstOrNull { it.id == state.selectedPresetId }
        if (preset == null) {
            _effects.tryEmit(
                AiConfigEffect.ShowMessage(appCtx.getString(R.string.ai_wizard_pick_provider)),
            )
            return
        }
        if (state.apiKey.isBlank()) {
            _effects.tryEmit(
                AiConfigEffect.ShowMessage(appCtx.getString(R.string.ai_wizard_need_api_key)),
            )
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(testing = true) }
            try {
                val models = aiTextGateway.fetchModels(
                    AiProviderConfig(
                        id = preset.id,
                        name = preset.name,
                        protocol = preset.protocol,
                        baseUrl = preset.baseUrl,
                        apiKey = state.apiKey.trim(),
                        modelsUrl = preset.modelsUrl,
                    ),
                ).getOrThrow()
                val count = models.size
                val message = if (count == 0) {
                    appCtx.getString(R.string.ai_test_success_no_models)
                } else {
                    appCtx.getString(R.string.ai_test_success_with_models, count)
                }
                _effects.tryEmit(AiConfigEffect.ShowMessage(message))
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                val errorMsg = error.message
                val failMsg = appCtx.getString(R.string.ai_test_failed)
                val message = if (errorMsg.isNullOrBlank()) failMsg else "$failMsg: $errorMsg"
                _effects.tryEmit(AiConfigEffect.ShowMessage(message))
            } finally {
                _uiState.update { it.copy(testing = false) }
            }
        }
    }

    private fun saveWizard() {
        val state = _uiState.value
        val preset = wizardPresets.firstOrNull { it.id == state.selectedPresetId }
        if (preset == null) {
            _effects.tryEmit(
                AiConfigEffect.ShowMessage(appCtx.getString(R.string.ai_wizard_pick_provider)),
            )
            return
        }
        if (state.apiKey.isBlank()) {
            _effects.tryEmit(
                AiConfigEffect.ShowMessage(appCtx.getString(R.string.ai_wizard_need_api_key)),
            )
            return
        }
        val modelId = state.modelId.trim()
        if (modelId.isBlank()) {
            _effects.tryEmit(
                AiConfigEffect.ShowMessage(appCtx.getString(R.string.ai_wizard_need_model)),
            )
            return
        }
        val displayName = state.modelDisplayName.trim().ifBlank { modelId }
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true) }
            runCatching {
                val existingProvider = state.providers.firstOrNull { provider ->
                    normalizeUrl(provider.baseUrl) == normalizeUrl(preset.baseUrl) &&
                        provider.providerName.equals(preset.name, ignoreCase = true)
                }
                val provider = aiProfileGateway.saveProvider(
                    AiProviderDraft(
                        providerId = existingProvider?.providerId,
                        providerName = preset.name,
                        protocol = preset.protocol,
                        baseUrl = preset.baseUrl,
                        modelsUrl = preset.modelsUrl,
                        apiKey = state.apiKey.trim(),
                    ),
                )
                val existingModel = state.models.firstOrNull {
                    it.providerId == provider.id && it.modelId == modelId
                }
                val model = aiProfileGateway.saveModel(
                    AiModelDraft(
                        modelProfileId = existingModel?.modelProfileId,
                        providerId = provider.id,
                        modelName = displayName,
                        modelId = modelId,
                    ),
                )
                aiProfileGateway.setDefaultModel(model.id)
            }.onSuccess {
                _effects.tryEmit(
                    AiConfigEffect.ShowMessage(appCtx.getString(R.string.ai_wizard_saved)),
                )
            }.onFailure { error ->
                _effects.tryEmit(
                    AiConfigEffect.ShowMessage(
                        error.message ?: appCtx.getString(R.string.ai_config_save_failed),
                    ),
                )
            }
            _uiState.update { it.copy(saving = false) }
        }
    }

    private fun setDefaultModel(modelProfileId: String) {
        viewModelScope.launch {
            runCatching {
                aiProfileGateway.setDefaultModel(modelProfileId)
            }.onSuccess {
                _effects.tryEmit(
                    AiConfigEffect.ShowMessage(appCtx.getString(R.string.ai_wizard_saved)),
                )
            }.onFailure { error ->
                _effects.tryEmit(
                    AiConfigEffect.ShowMessage(
                        error.message ?: appCtx.getString(R.string.ai_config_save_failed),
                    ),
                )
            }
        }
    }

    private fun normalizeUrl(url: String): String =
        url.trim().trimEnd('/').lowercase()
}
