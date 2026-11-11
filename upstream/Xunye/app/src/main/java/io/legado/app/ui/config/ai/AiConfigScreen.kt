package io.legado.app.ui.config.ai

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.model.ai.AiUsageLedger
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.InputSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.icon.Symbols
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun AiConfigRouteScreen(
    onBackClick: () -> Unit,
    onNavigateToProviderEdit: (providerId: String?) -> Unit,
    onNavigateToModelEdit: (providerId: String?, modelProfileId: String?) -> Unit,
    onNavigateToTranslation: () -> Unit,
    onNavigateToAiSummary: () -> Unit,
    onNavigateToAiPrompt: () -> Unit,
    onNavigateToAiUsage: () -> Unit = {},
    viewModel: AiConfigViewModel = koinViewModel(),
) {
    AiConfigScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        effects = viewModel.effects,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
        onNavigateToProviderEdit = onNavigateToProviderEdit,
        onNavigateToModelEdit = onNavigateToModelEdit,
        onNavigateToTranslation = onNavigateToTranslation,
        onNavigateToAiSummary = onNavigateToAiSummary,
        onNavigateToAiPrompt = onNavigateToAiPrompt,
        onNavigateToAiUsage = onNavigateToAiUsage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfigScreen(
    state: AiConfigUiState,
    effects: Flow<AiConfigEffect>,
    onIntent: (AiConfigIntent) -> Unit,
    onBackClick: () -> Unit,
    onNavigateToProviderEdit: (providerId: String?) -> Unit,
    @Suppress("UNUSED_PARAMETER")
    onNavigateToModelEdit: (providerId: String?, modelProfileId: String?) -> Unit,
    onNavigateToTranslation: () -> Unit,
    onNavigateToAiSummary: () -> Unit,
    onNavigateToAiPrompt: () -> Unit,
    onNavigateToAiUsage: () -> Unit = {},
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    var showModelSheet by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var apiKeyDraft by remember(state.apiKey) { mutableStateOf(state.apiKey) }

    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            when (effect) {
                is AiConfigEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    val presetEntries = state.wizardPresets.map { it.name }.toTypedArray()
    val presetValues = state.wizardPresets.map { it.id }.toTypedArray()
    val customToken = "__custom__"
    val suggestedEntries = buildList {
        state.suggestedModels.forEach { add(it.displayName) }
        add(stringResource(R.string.ai_wizard_model_custom))
    }.toTypedArray()
    val suggestedValues = buildList {
        state.suggestedModels.forEach { add(it.modelId) }
        add(customToken)
    }.toTypedArray()
    val selectedSuggest = when {
        state.customModel -> customToken
        else -> state.suggestedModels
            .firstOrNull { it.modelId == state.modelId }
            ?.modelId
            ?: customToken
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.ai_config),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                },
                actions = {
                    // SPEC-262: usage ledger
                    TopBarActionButton(
                        symbol = Symbols.BarChart,
                        contentDescription = stringResource(R.string.ai_usage),
                        onClick = onNavigateToAiUsage,
                    )
                    // SPEC-260: 模型列表 / 新增模型接入
                    TopBarActionButton(
                        symbol = Symbols.FormatListBulleted,
                        contentDescription = stringResource(R.string.ai_model_list),
                        onClick = { showModelSheet = true },
                    )
                    TopBarActionButton(
                        symbol = Symbols.Add,
                        contentDescription = stringResource(R.string.ai_wizard_add_provider),
                        onClick = { onNavigateToProviderEdit(null) },
                    )
                },
            )
        },
    ) { paddingValues ->
        var restoreLastChat by remember {
            mutableStateOf(AiUsageLedger.restoreLastChatEnabled())
        }
        LazyColumn(
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp,
            ),
        ) {
            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_chat)) {
                    SwitchSettingItem(
                        title = stringResource(R.string.ai_restore_last_chat),
                        description = stringResource(R.string.ai_restore_last_chat_summary),
                        checked = restoreLastChat,
                        onCheckedChange = {
                            restoreLastChat = it
                            AiUsageLedger.setRestoreLastChatEnabled(it)
                        },
                    )
                }
            }
            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_wizard_title)) {
                    if (state.currentModelName.isNotBlank()) {
                        ClickableSettingItem(
                            title = stringResource(R.string.ai_wizard_current),
                            description = listOf(
                                state.currentProviderName,
                                state.currentModelName,
                            ).filter { it.isNotBlank() }.joinToString(" · "),
                            onClick = {
                                if (state.models.isNotEmpty()) showModelSheet = true
                            },
                        )
                    }
                    if (presetEntries.isNotEmpty()) {
                        DropdownListSettingItem(
                            title = stringResource(R.string.ai_wizard_provider),
                            selectedValue = state.selectedPresetId,
                            displayEntries = presetEntries,
                            entryValues = presetValues,
                            onValueChange = { onIntent(AiConfigIntent.SelectPreset(it)) },
                        )
                    }
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_api_key),
                        description = if (state.apiKey.isBlank()) {
                            stringResource(R.string.ai_wizard_api_key_hint)
                        } else {
                            stringResource(R.string.ai_wizard_api_key_set)
                        },
                        onClick = {
                            apiKeyDraft = state.apiKey
                            showApiKeyDialog = true
                        },
                    )
                    if (suggestedEntries.isNotEmpty()) {
                        DropdownListSettingItem(
                            title = stringResource(R.string.ai_wizard_model_pick),
                            selectedValue = selectedSuggest,
                            displayEntries = suggestedEntries,
                            entryValues = suggestedValues,
                            onValueChange = { id ->
                                if (id == customToken) {
                                    onIntent(AiConfigIntent.SelectCustomModel)
                                    return@DropdownListSettingItem
                                }
                                val sug = state.suggestedModels.firstOrNull { it.modelId == id }
                                if (sug != null) {
                                    onIntent(
                                        AiConfigIntent.SelectSuggestedModel(
                                            sug.modelId,
                                            sug.displayName,
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    if (state.customModel) {
                        InputSettingItem(
                            title = stringResource(R.string.ai_wizard_model_id),
                            value = state.modelId,
                            description = stringResource(R.string.ai_wizard_model_id_hint),
                            onConfirm = { onIntent(AiConfigIntent.UpdateModelId(it)) },
                        )
                    }
                    ClickableSettingItem(
                        title = if (state.testing) {
                            stringResource(R.string.ai_wizard_testing)
                        } else {
                            stringResource(R.string.ai_test_connection)
                        },
                        description = stringResource(R.string.ai_wizard_test_desc),
                        onClick = {
                            if (!state.testing && !state.saving) {
                                onIntent(AiConfigIntent.TestWizardConnection)
                            }
                        },
                    )
                    ClickableSettingItem(
                        title = if (state.saving) {
                            stringResource(R.string.ai_wizard_saving)
                        } else {
                            stringResource(R.string.ai_wizard_save)
                        },
                        description = stringResource(R.string.ai_wizard_save_desc),
                        onClick = {
                            if (!state.saving && !state.testing) {
                                onIntent(AiConfigIntent.SaveWizard)
                            }
                        },
                    )
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_tasks)) {
                    ClickableSettingItem(
                        title = stringResource(R.string.translation_config),
                        onClick = onNavigateToTranslation,
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_chapter_summary),
                        onClick = onNavigateToAiSummary,
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_prompt_config),
                        description = stringResource(R.string.ai_prompt_config_desc),
                        onClick = onNavigateToAiPrompt,
                    )
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_wizard_advanced)) {
                    state.providers.forEach { provider ->
                        ClickableSettingItem(
                            title = provider.providerName,
                            description = provider.baseUrl,
                            option = "${provider.protocol} / ${provider.modelCount}",
                            onClick = { onNavigateToProviderEdit(provider.providerId) },
                        )
                    }
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_wizard_advanced_provider),
                        description = stringResource(R.string.ai_wizard_advanced_provider_desc),
                        onClick = { onNavigateToProviderEdit(null) },
                    )
                }
            }
        }
    }

    AppModalBottomSheet(
        show = showModelSheet,
        onDismissRequest = { showModelSheet = false },
        title = stringResource(R.string.ai_select_model),
    ) {
        LazyColumn {
            state.providers.forEach { provider ->
                item {
                    SplicedColumnGroup(title = provider.providerName) {
                        if (provider.models.isEmpty()) {
                            ClickableSettingItem(
                                title = stringResource(R.string.ai_no_models_imported),
                                onClick = {
                                    showModelSheet = false
                                    onNavigateToProviderEdit(provider.providerId)
                                },
                            )
                        } else {
                            provider.models.forEach { model ->
                                ClickableSettingItem(
                                    title = model.modelName,
                                    description = model.modelId,
                                    trailingContent = if (model.isCurrent) {
                                        { TextCard(text = stringResource(R.string.ai_current)) }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        onIntent(AiConfigIntent.SetDefaultModel(model.modelProfileId))
                                        showModelSheet = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showApiKeyDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { androidx.compose.material3.Text(stringResource(R.string.ai_api_key)) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = apiKeyDraft,
                    onValueChange = { apiKeyDraft = it },
                    singleLine = true,
                    modifier = Modifier,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onIntent(AiConfigIntent.UpdateApiKey(apiKeyDraft))
                        showApiKeyDialog = false
                    },
                ) {
                    androidx.compose.material3.Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    androidx.compose.material3.Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
