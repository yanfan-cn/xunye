package io.legado.app.ui.config.ai

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class AiConfigUiState(
    val wizardPresets: ImmutableList<AiWizardPresetUi> = persistentListOf(),
    val selectedPresetId: String = "",
    val apiKey: String = "",
    val modelId: String = "",
    val modelDisplayName: String = "",
    val suggestedModels: ImmutableList<AiWizardModelUi> = persistentListOf(),
    /** SPEC-260: true when「常用模型」选「自定义…」— show model ID field. */
    val customModel: Boolean = false,
    val saving: Boolean = false,
    val testing: Boolean = false,
    val currentModelProfileId: String? = null,
    val currentModelName: String = "",
    val currentProviderName: String = "",
    val providers: ImmutableList<AiProviderListItemUi> = persistentListOf(),
    val models: ImmutableList<AiModelListItemUi> = persistentListOf(),
)

@Stable
data class AiWizardPresetUi(
    val id: String,
    val name: String,
    val baseUrl: String,
)

@Stable
data class AiWizardModelUi(
    val displayName: String,
    val modelId: String,
)

@Stable
data class AiProviderListItemUi(
    val providerId: String,
    val providerName: String,
    val protocol: String,
    val baseUrl: String,
    val modelCount: Int,
    val enabled: Boolean,
    val models: ImmutableList<AiModelListItemUi> = persistentListOf(),
)

@Stable
data class AiModelListItemUi(
    val providerId: String,
    val modelProfileId: String,
    val providerName: String,
    val protocol: String,
    val baseUrl: String,
    val modelName: String,
    val modelId: String,
    val contextWindow: Int,
    val maxOutputTokens: Int,
    val enabled: Boolean,
    val isCurrent: Boolean,
)

sealed interface AiConfigIntent {
    data class SelectPreset(val presetId: String) : AiConfigIntent
    data class UpdateApiKey(val value: String) : AiConfigIntent
    data class SelectSuggestedModel(val modelId: String, val displayName: String) : AiConfigIntent
    data object SelectCustomModel : AiConfigIntent
    data class UpdateModelId(val value: String) : AiConfigIntent
    data object SaveWizard : AiConfigIntent
    data object TestWizardConnection : AiConfigIntent
    data class SetDefaultModel(val modelProfileId: String) : AiConfigIntent
}

sealed interface AiConfigEffect {
    data class ShowMessage(val message: String) : AiConfigEffect
}
