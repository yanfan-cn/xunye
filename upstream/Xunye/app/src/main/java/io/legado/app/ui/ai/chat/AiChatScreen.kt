package io.legado.app.ui.ai.chat

import io.legado.app.ui.widget.components.icon.Symbols
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import android.content.ClipData
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun AiChatRouteScreen(
    onBackClick: () -> Unit,
    onOpenBookInfo: (AiChatBookResultUi) -> Unit,
    /** SPEC-250: 未配置模型时跳转 AI 设置。 */
    onOpenAiSettings: () -> Unit = {},
    viewModel: AiChatViewModel = koinViewModel()
) {
    AiChatScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        effects = viewModel.effects,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
        onOpenBookInfo = onOpenBookInfo,
        onOpenAiSettings = onOpenAiSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    state: AiChatUiState,
    effects: Flow<AiChatEffect>,
    onIntent: (AiChatIntent) -> Unit,
    onBackClick: () -> Unit,
    onOpenBookInfo: (AiChatBookResultUi) -> Unit,
    onOpenAiSettings: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var topOverlayHeightPx by remember { mutableIntStateOf(0) }
    var bottomOverlayHeightPx by remember { mutableIntStateOf(0) }
    var initiallyPositionedConversationId by remember { mutableStateOf<String?>(null) }
    var errorDialogMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarActionLabel = stringResource(R.string.details)
    val errorDialogTitle = stringResource(R.string.error_details)
    val currentConversation = state.conversations.firstOrNull {
        it.id == state.currentConversationId
    } ?: state.conversations.firstOrNull { it.isSelected }
    val conversationTitle = currentConversation?.title?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.ai_chat)
    val modelName = currentConversation?.modelName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.ai_select_model)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // SPEC-262: landscape sidebar pin (true = split, false = fullscreen + drawer).
    var sidebarPinned by rememberSaveable { mutableStateOf(true) }
    val showSplitSidebar = isLandscape && sidebarPinned
    val generationGradientProgress = if (state.isSending) {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(
            label = "AiChatGeneratingGradient"
        )
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "AiChatGeneratingGradientProgress"
        )
        progress
    } else {
        0f
    }
    val bottomGradientColor = lerp(
        LegadoTheme.colorScheme.surface,
        LegadoTheme.colorScheme.secondaryContainer,
        generationGradientProgress
    )

    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            when (effect) {
                is AiChatEffect.ShowMessage -> {
                    val result = snackbarHostState.showSnackbar(
                        message = effect.message,
                        actionLabel = snackbarActionLabel
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        errorDialogMessage = effect.message
                    }
                }
                AiChatEffect.OpenAiSettings -> onOpenAiSettings()
            }
        }
    }

    val onSelectConversation: (String) -> Unit = { id ->
        onIntent(AiChatIntent.SelectConversation(id))
        if (!isLandscape) scope.launch { drawerState.close() }
    }
    val onDeleteConversation: (String) -> Unit = { id ->
        onIntent(AiChatIntent.DeleteConversation(id))
    }

    val chatBody: @Composable () -> Unit = {
        AppScaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            alwaysDrawBehindBars = true,
            contentWindowInsets = WindowInsets(0)
        ) {
            // Track whether user is near the bottom
            val isNearBottom by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val lastVisible = info.visibleItemsInfo.lastOrNull()
                    lastVisible == null || lastVisible.index >= info.totalItemsCount - 3
                }
            }
            val isAtBottom by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val lastVisible = info.visibleItemsInfo.lastOrNull()
                    lastVisible == null || lastVisible.index >= info.totalItemsCount - 1
                }
            }

            // Stick-to-bottom: tracks whether user has scrolled away
            var shouldStickToBottom by remember { mutableStateOf(true) }

            // Position immediately at the bottom once this conversation is laid out.
            LaunchedEffect(
                state.currentConversationId,
                state.messages.size,
                state.streamingMessage?.id
            ) {
                val conversationId = state.currentConversationId ?: currentConversation?.id
                if (conversationId == null ||
                    initiallyPositionedConversationId == conversationId ||
                    (state.messages.isEmpty() && state.streamingMessage == null)
                ) {
                    return@LaunchedEffect
                }
                val bottomAnchorIndex = state.messages.size +
                    if (state.streamingMessage != null) 1 else 0
                snapshotFlow { listState.layoutInfo.totalItemsCount }
                    .first { it > bottomAnchorIndex }
                listState.scrollToItem(bottomAnchorIndex)
                initiallyPositionedConversationId = conversationId
                shouldStickToBottom = true
            }

            // Update stick-to-bottom when user scrolls
            LaunchedEffect(listState) {
                snapshotFlow { listState.isScrollInProgress to isNearBottom }
                    .collectLatest { (isScrolling, nearBottom) ->
                        if (isScrolling) {
                            shouldStickToBottom = nearBottom
                        }
                    }
            }

            // SPEC-262: do NOT force stick when a new streaming message starts —
            // only follow if the user is already near the bottom.

            // Auto-scroll: when messages or streaming content changes, scroll to bottom if sticking
            LaunchedEffect(
                shouldStickToBottom,
                state.messages.size,
                state.streamingMessage?.content?.length,
                state.streamingMessage?.reasoning?.length,
            ) {
                if (shouldStickToBottom && !listState.isScrollInProgress) {
                    val lastIndex = listState.layoutInfo.totalItemsCount - 1
                    if (lastIndex >= 0) {
                        // Instant jump while streaming — animate fights user scroll.
                        listState.scrollToItem(lastIndex)
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                val systemBottomPadding = maxOf(
                    WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                )
                val topContentPadding = with(density) {
                    topOverlayHeightPx.toDp()
                }
                val bottomContentPadding = with(density) {
                    bottomOverlayHeightPx.toDp()
                } + systemBottomPadding

                // Message list
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        top = topContentPadding + 8.dp,
                        end = 8.dp,
                        bottom = bottomContentPadding + 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.messages.isEmpty() && state.streamingMessage == null) {
                        item {
                            EmptyChatHint()
                        }
                    }
                    items(state.messages, key = { it.id }) { message ->
                        ChatMessageItem(
                            message = message,
                            isStreaming = false,
                            onOpenBookInfo = onOpenBookInfo,
                            onCopy = {
                                scope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(ClipData.newPlainText("content", message.content))
                                    )
                                }
                            },
                            onRegenerate = if (message.role == AiMessageRole.ASSISTANT && message.parentMessageId != null) {
                                { onIntent(AiChatIntent.RegenerateMessage(message.id)) }
                            } else null,
                            onSwitchBranch = if (message.totalBranches > 1) { direction ->
                                val siblings = state.messages.filter {
                                    it.parentMessageId == message.parentMessageId && it.role == message.role
                                }
                                val currentIdx = siblings.indexOfFirst { it.id == message.id }
                                if (currentIdx >= 0) {
                                    val targetIdx = when (direction) {
                                        -1 -> (currentIdx - 1).coerceAtLeast(0)
                                        1 -> (currentIdx + 1).coerceAtMost(siblings.size - 1)
                                        else -> currentIdx
                                    }
                                    siblings.getOrNull(targetIdx)?.let {
                                        onIntent(AiChatIntent.SwitchBranch(it.id))
                                    }
                                }
                            } else null
                        )
                    }
                    // Streaming message
                    val streaming = state.streamingMessage
                    if (streaming != null) {
                        item(key = "streaming") {
                            ChatMessageItem(
                                message = streaming,
                                isStreaming = true,
                                onOpenBookInfo = onOpenBookInfo,
                                onCopy = {}
                            )
                        }
                    }
                    // Bottom anchor for scroll target
                    item(key = "bottom_anchor") {
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                }

                // Draw behind both the floating input and the system navigation/IME area.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(bottomContentPadding - systemBottomPadding)
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f to bottomGradientColor.copy(alpha = 0f),
                                        0.58f to bottomGradientColor.copy(alpha = 0f),
                                        0.82f to bottomGradientColor.copy(alpha = 0.64f),
                                        1f to bottomGradientColor.copy(alpha = 0.88f)
                                    )
                                )
                            )
                    )
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(systemBottomPadding)
                            .background(bottomGradientColor.copy(alpha = 0.88f))
                    )
                }

                // Bottom controls consume IME/navigation insets exactly once.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { bottomOverlayHeightPx = it.height }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 32.dp)
                        ) {
                            if (!isAtBottom &&
                                (state.messages.isNotEmpty() || state.streamingMessage != null)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    SmallPlainButton(
                                        onClick = {
                                            scope.launch {
                                                val lastIndex =
                                                    listState.layoutInfo.totalItemsCount - 1
                                                if (lastIndex >= 0) {
                                                    listState.animateScrollToItem(lastIndex)
                                                }
                                                shouldStickToBottom = true
                                            }
                                        },
                                        icon = Symbols.KeyboardArrowDown,
                                        contentDescription = stringResource(
                                            R.string.ai_scroll_to_bottom
                                        ),
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(LegadoTheme.colorScheme.surfaceVariant)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            state.pendingToolConfirmation?.let { confirmation ->
                                PendingToolConfirmationCard(
                                    confirmation = confirmation,
                                    onConfirm = {
                                        onIntent(AiChatIntent.ConfirmPendingTool)
                                    },
                                    onReject = {
                                        onIntent(AiChatIntent.RejectPendingTool)
                                    }
                                )
                            }
                            ChatInputBar(
                                value = draft,
                                isSending = state.isSending,
                                onValueChange = { draft = it },
                                onSend = {
                                    val text = draft
                                    draft = ""
                                    onIntent(AiChatIntent.SendMessage(text))
                                },
                                onStop = { onIntent(AiChatIntent.StopGenerating) },
                            )
                        }
                    }
                }

                // Top controls draw above the list while keeping actions below the status bar.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onSizeChanged { topOverlayHeightPx = it.height }
                ) {
                    Spacer(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f to LegadoTheme.colorScheme.surface.copy(alpha = 0.88f),
                                        0.38f to LegadoTheme.colorScheme.surface.copy(alpha = 0.72f),
                                        0.72f to LegadoTheme.colorScheme.surface.copy(alpha = 0.40f),
                                        1f to LegadoTheme.colorScheme.surface.copy(alpha = 0f)
                                    )
                                )
                            )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // SPEC-262: Menu always available — landscape toggles pin vs fullscreen.
                        MediumTonalButton(
                            onClick = {
                                if (isLandscape) {
                                    sidebarPinned = !sidebarPinned
                                } else {
                                    scope.launch { drawerState.open() }
                                }
                            },
                            icon = Symbols.Menu,
                            contentDescription = stringResource(R.string.ai_recent_chats)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(LegadoTheme.colorScheme.surfaceContainerLow)
                                .clickable { onIntent(AiChatIntent.OpenModelPicker) }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .heightIn(min = 40.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.Center
                            ) {
                                AppText(
                                    text = conversationTitle,
                                    style = LegadoTheme.typography.labelMediumEmphasized,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                AppText(
                                    text = modelName,
                                    style = LegadoTheme.typography.labelSmall,
                                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MediumTonalButton(
                                onClick = { onIntent(AiChatIntent.NewConversation) },
                                icon = Symbols.Add,
                                contentDescription = stringResource(R.string.ai_new_chat)
                            )
                            MediumTonalButton(
                                onClick = onBackClick,
                                icon = Symbols.Close,
                                contentDescription = stringResource(R.string.ai_close_assistant)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSplitSidebar) {
        Row(modifier = Modifier.fillMaxSize()) {
            ConversationPane(
                conversations = state.conversations,
                onSelectConversation = onSelectConversation,
                onDeleteConversation = onDeleteConversation,
                modifier = Modifier
                    .weight(0.30f)
                    .fillMaxHeight(),
                asDrawerSheet = false,
            )
            Box(
                modifier = Modifier
                    .weight(0.70f)
                    .fillMaxHeight()
            ) {
                chatBody()
            }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ConversationPane(
                    conversations = state.conversations,
                    onSelectConversation = onSelectConversation,
                    onDeleteConversation = onDeleteConversation,
                    modifier = Modifier.width(280.dp),
                    asDrawerSheet = true,
                )
            },
        ) {
            chatBody()
        }
    }

    if (state.showModelPicker) {
        AiModelPickerSheet(
            models = state.availableModels,
            onSelect = { onIntent(AiChatIntent.SelectModel(it)) },
            onAddModel = { onIntent(AiChatIntent.AddModel) },
            onDismiss = { onIntent(AiChatIntent.DismissModelPicker) },
        )
    }

    AppAlertDialog(
        show = errorDialogMessage != null,
        onDismissRequest = { errorDialogMessage = null },
        title = errorDialogTitle,
        text = errorDialogMessage,
        confirmText = stringResource(R.string.ok),
        onConfirm = { errorDialogMessage = null }
    )

    // SPEC-250: 未配置模型时提示，并可跳转 AI 设置。
    AppAlertDialog(
        show = state.showMissingModelPrompt,
        onDismissRequest = { onIntent(AiChatIntent.DismissMissingModelPrompt) },
        title = stringResource(R.string.ai_model_not_configured),
        text = stringResource(R.string.ai_chat_model_required),
        confirmText = stringResource(R.string.ai_config),
        onConfirm = { onIntent(AiChatIntent.OpenAiSettings) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(AiChatIntent.DismissMissingModelPrompt) },
    )
}

@Composable
private fun PendingToolConfirmationCard(
    confirmation: AiToolConfirmationUi,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    GlassCard(
        containerColor = LegadoTheme.colorScheme.tertiaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            AppText(
                text = stringResource(R.string.ai_tool_confirmation),
                style = LegadoTheme.typography.labelMedium,
                color = LegadoTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            AppText(
                text = confirmation.title,
                style = LegadoTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = LegadoTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            AppText(
                text = confirmation.description,
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onTertiaryContainer,
                maxLines = 6
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                SmallPlainButton(
                    onClick = onReject,
                    text = stringResource(R.string.cancel)
                )
                SmallPlainButton(
                    onClick = onConfirm,
                    text = stringResource(R.string.confirm)
                )
            }
        }
    }
}

/** SPEC-261: conversation list — search always visible; no sidebar top bar. */
@Composable
private fun ConversationPane(
    conversations: List<AiChatConversationUi>,
    onSelectConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
    asDrawerSheet: Boolean = true,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var conversationToDelete by remember { mutableStateOf<AiChatConversationUi?>(null) }
    val filteredConversations = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) {
            conversations
        } else {
            conversations.filter {
                it.title.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 8.dp)
        ) {
            // SPEC-262: same capsule search as bookmarks/favorites.
            io.legado.app.ui.browser.BrowserListSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = stringResource(R.string.search),
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredConversations, key = { it.id }) { conversation ->
                    val isSelected = conversation.isSelected
                    NormalCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelectConversation(conversation.id) },
                        onLongClick = { conversationToDelete = conversation },
                        containerColor = if (isSelected) {
                            LegadoTheme.colorScheme.primaryContainer
                        } else {
                            LegadoTheme.colorScheme.surface
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            AppText(
                                text = conversation.title,
                                style = LegadoTheme.typography.bodyMedium,
                                maxLines = 2,
                                color = if (isSelected) {
                                    LegadoTheme.colorScheme.onPrimaryContainer
                                } else {
                                    LegadoTheme.colorScheme.onSurface
                                }
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            AppText(
                                text = formatRelativeTime(conversation.updatedAt),
                                style = LegadoTheme.typography.labelSmall,
                                color = LegadoTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }

    if (asDrawerSheet) {
        ModalDrawerSheet(
            modifier = modifier,
            drawerContainerColor = LegadoTheme.colorScheme.surfaceContainerLow
        ) {
            content()
        }
    } else {
        Surface(
            modifier = modifier,
            color = LegadoTheme.colorScheme.surfaceContainerLow
        ) {
            content()
        }
    }

    AppAlertDialog(
        show = conversationToDelete != null,
        onDismissRequest = { conversationToDelete = null },
        title = stringResource(R.string.delete),
        text = stringResource(R.string.sure_del),
        confirmText = stringResource(R.string.delete),
        onConfirm = {
            conversationToDelete?.let { onDeleteConversation(it.id) }
            conversationToDelete = null
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { conversationToDelete = null }
    )
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes}分钟前"
        hours < 24 -> "${hours}小时前"
        days < 7 -> "${days}天前"
        days < 30 -> "${days / 7}周前"
        else -> {
            val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: AiChatMessageUi,
    isStreaming: Boolean,
    onOpenBookInfo: (AiChatBookResultUi) -> Unit,
    onCopy: () -> Unit,
    onRegenerate: (() -> Unit)? = null,
    onSwitchBranch: ((direction: Int) -> Unit)? = null
) {
    val isUser = message.role == AiMessageRole.USER
    val isAssistant = message.role == AiMessageRole.ASSISTANT

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (isUser) {
            GlassCard(
                containerColor = LegadoTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(0.86f)
            ) {
                AiGeneratedMessageContent(
                    isUser = true,
                    isAssistant = false,
                    isStreaming = isStreaming,
                    message = message,
                    onOpenBookInfo = onOpenBookInfo,
                    onCopy = onCopy,
                    onRegenerate = onRegenerate,
                    onSwitchBranch = onSwitchBranch,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else {
            AiGeneratedMessageContent(
                isUser = false,
                isAssistant = isAssistant,
                isStreaming = isStreaming,
                message = message,
                onOpenBookInfo = onOpenBookInfo,
                onCopy = onCopy,
                onRegenerate = onRegenerate,
                onSwitchBranch = onSwitchBranch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    isSending: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val isKeyboardVisible =
        WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
    val horizontalPadding by animateDpAsState(
        targetValue = if (isKeyboardVisible) 16.dp else 46.dp,
        animationSpec = tween(durationMillis = 250),
        label = "AiChatInputHorizontalPadding"
    )
    val bottomPadding by animateDpAsState(
        targetValue = if (isKeyboardVisible) 16.dp else 32.dp,
        animationSpec = tween(durationMillis = 250),
        label = "AiChatInputBottomPadding"
    )

    // Floating capsule input bar
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = horizontalPadding,
                end = horizontalPadding,
                bottom = bottomPadding
            ),
        shape = RoundedCornerShape(32.dp),
        color = LegadoTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // SPEC-260: chat thinking fixed to AUTO — no multi-level picker.
            // Text field
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = !isSending,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp, max = 160.dp),
                textStyle = LegadoTheme.typography.bodyMedium.copy(
                    color = LegadoTheme.colorScheme.onSurface
                ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            AppText(
                                text = stringResource(R.string.ai_chat_input_hint),
                                style = LegadoTheme.typography.bodyMedium,
                                color = LegadoTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Send/Stop button
            if (isSending) {
                MediumTonalButton(
                    onClick = onStop,
                    icon = Symbols.Stop,
                    contentDescription = stringResource(R.string.stop)
                )
            } else {
                MediumTonalButton(
                    onClick = onSend,
                    icon = Symbols.ArrowUpward,
                    enabled = value.isNotBlank(),
                    contentDescription = stringResource(R.string.ai_send)
                )
            }
        }
    }
}

@Composable
private fun EmptyChatHint() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        AppText(
            text = stringResource(R.string.ai_chat_empty),
            color = LegadoTheme.colorScheme.outline,
            style = LegadoTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun AiChatEffectHandler(
    effects: Flow<AiChatEffect>,
    snackbarHostState: SnackbarHostState,
    onOpenAiSettings: () -> Unit = {},
) {
    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            when (effect) {
                is AiChatEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                AiChatEffect.OpenAiSettings -> onOpenAiSettings()
            }
        }
    }
}

@Composable
private fun AiModelPickerSheet(
    models: List<AiChatModelOptionUi>,
    onSelect: (String) -> Unit,
    onAddModel: () -> Unit,
    onDismiss: () -> Unit,
) {
    io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.ai_select_model),
    ) {
        LazyColumn {
            items(models, key = { it.modelProfileId }) { model ->
                NormalCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    onClick = { onSelect(model.modelProfileId) },
                    containerColor = if (model.isSelected) {
                        LegadoTheme.colorScheme.primaryContainer
                    } else {
                        LegadoTheme.colorScheme.surface
                    },
                ) {
                    AppText(
                        text = model.modelName,
                        style = LegadoTheme.typography.bodyMedium,
                        fontWeight = if (model.isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
                    )
                    if (model.providerName.isNotBlank()) {
                        AppText(
                            text = model.providerName,
                            style = LegadoTheme.typography.labelSmall,
                            color = LegadoTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        )
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
            item {
                MediumTonalButton(
                    onClick = onAddModel,
                    icon = Symbols.Add,
                    text = stringResource(R.string.ai_add_model),
                    contentDescription = stringResource(R.string.ai_add_model),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
        }
    }
}
