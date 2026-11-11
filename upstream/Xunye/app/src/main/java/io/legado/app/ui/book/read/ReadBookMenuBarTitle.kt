package io.legado.app.ui.book.read

import io.legado.app.ui.widget.components.icon.SymbolIcon
import io.legado.app.ui.widget.components.icon.MaterialSymbol
import io.legado.app.ui.widget.components.icon.Symbols
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import dev.chrisbanes.haze.HazeState
import io.legado.app.R
import io.legado.app.browser.engine.BrowserChromePrefs
import io.legado.app.browser.engine.HideBrowserChromeInReaderState
import io.legado.app.constant.ReadMenuBlurMode
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menuItem.MenuItemIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.reader.ReaderMenuEffect
import io.legado.app.ui.widget.components.reader.ReaderMenuPlacement
import io.legado.app.ui.widget.components.reader.ReaderMenuTintStyle
import io.legado.app.ui.widget.components.reader.ReaderMenuVisualState
import io.legado.app.ui.widget.components.reader.readerMenuHazeEffect
import io.legado.app.ui.widget.components.reader.readerMenuLiquidGlassAvailable
import io.legado.app.ui.widget.components.reader.readerMenuSurfaceBrush
import io.legado.app.ui.widget.components.settingItem.LocalSliderDragState
import io.legado.app.ui.widget.components.text.AppText
import kotlin.math.roundToInt

@Composable
internal fun MenuTitleBar(
    state: ReadBookUiState,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    backdrop: Backdrop?,
    hazeState: HazeState?,
    titleBarMode: String,
) {

    val topBarBorderWidth = state.menuConfig.readMenuBorderWidth
    val topBarBorderColor = readMenuBorderColor(state.menuConfig)
    val isSliderDragging = LocalSliderDragState.current?.isDragging == true
    val chromeSurfaceStyle =
        io.legado.app.ui.theme.LocalAppUiConfiguration.current.theme.chromeSurfaceStyle
    // SPEC-061: filled chrome → opaque solid top bar (not transparent).
    val forceFilledChrome =
        io.legado.app.ui.theme.ChromeSurfaceStylePolicy.isFilledActive(chromeSurfaceStyle)
    // SPEC-257: liquid glass — no full-width tinted strip; only back / title / more chips.
    val liquidChrome =
        io.legado.app.ui.theme.ChromeSurfaceStylePolicy.isLiquidGlassActive(chromeSurfaceStyle)
    // SPEC-209: fill mode uses same floating layout as liquid glass — no full-width strip.
    val topBarSurfaceAlpha = if (forceFilledChrome || liquidChrome) {
        0
    } else {
        io.legado.app.ui.theme.ChromeSurfaceStylePolicy.effectiveReadMenuSurfaceAlpha(
            chromeSurfaceStyle,
            state.menuConfig.readMenuBlurAlpha,
        )
    }
    val dragTopBarAlpha = topBarSurfaceAlpha.coerceAtMost(30)
    val animatedTopBarAlpha by animateFloatAsState(
        targetValue = if (isSliderDragging && !forceFilledChrome && !liquidChrome) {
            dragTopBarAlpha.toFloat()
        } else {
            topBarSurfaceAlpha.toFloat()
        },
        animationSpec = tween(durationMillis = 200),
        label = "ReadMenuTopBarAlpha",
    )
    val topBarAlpha = animatedTopBarAlpha / 100f
    val useTopBarBlur =
        !forceFilledChrome && !liquidChrome &&
            readMenuTopBarHazeEnabled(hazeState, state.menuConfig)
    val topBarVisualState = ReaderMenuVisualState(
        effect = if (useTopBarBlur) ReaderMenuEffect.Haze else ReaderMenuEffect.None,
        tintStyle = state.menuConfig.readMenuTopBarBlurStyle.toReaderMenuTintStyle(),
        styleEnabled = true,
        tintAllowed = true,
        tintFill = true,
    )
    val topBarTintColor = readMenuTintColor(state.menuConfig)
        .takeIf { topBarVisualState.useTint }
        ?: colors.background
    val titleTextColor = readMenuTextColor(state.menuConfig)
    val labelStyle = LegadoTheme.typography.labelSmallEmphasized.copy(
        shadow = menuTextShadow
    )
    val useTitleCapsule = state.menuConfig.readMenuTopBarTitleCapsule
    // SPEC-208: tablet shell host + optional hide of browser chrome.
    val underBrowserShell = LocalReaderUnderBrowserChrome.current
    val hideBrowserChrome by HideBrowserChromeInReaderState.value.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chromePrefs = remember(context) { BrowserChromePrefs(context) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (useTopBarBlur && hazeState != null) {
                    Modifier.readerMenuHazeEffect(
                        state = hazeState,
                        visualState = topBarVisualState,
                        placement = ReaderMenuPlacement.Top,
                        baseColor = colors.background,
                        tintColor = readMenuTintColor(state.menuConfig),
                        blurRadius = state.menuConfig.readMenuBlurRadius,
                        surfaceAlpha = animatedTopBarAlpha.roundToInt(),
                    )
                } else {
                    Modifier.background(
                        if (topBarVisualState.isGradient) {
                            readerMenuSurfaceBrush(
                                style = ReaderMenuTintStyle.Gradient,
                                placement = ReaderMenuPlacement.Top,
                                color = topBarTintColor,
                                alpha = topBarAlpha,
                            )
                        } else {
                            readerMenuSurfaceBrush(
                                style = ReaderMenuTintStyle.Fill,
                                placement = ReaderMenuPlacement.Top,
                                color = topBarTintColor,
                                alpha = topBarAlpha,
                            )
                        }
                    )
                }
            )
            .then(
                if (topBarBorderWidth > 0 && !useTitleCapsule) {
                    Modifier.drawBehind {
                        val strokeWidth = topBarBorderWidth.dp.toPx()
                        drawLine(
                            color = Color(topBarBorderColor),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = strokeWidth,
                        )
                    }
                } else Modifier
            )
            .then(
                // SPEC-122/210: tablet shell Column already reserves statusBars — never
                // double-apply here. Small gap under omnibox; 0 when chrome hidden.
                if (underBrowserShell) {
                    Modifier.padding(top = if (hideBrowserChrome) 0.dp else 4.dp)
                } else {
                    Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
                    )
                },
            )
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            )
    ) {
        val capsuleIconColor = LegadoTheme.colorScheme.onSurfaceVariant

        // Title row: left group (back + capsule/title) + right group (actions)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Left group: back + capsule/title — fills remaining space
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MenuTitleGlassButton(
                    onClick = { onIntent(ReadBookIntent.CloseReadBook()) },
                    icon = Symbols.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    state = state,
                    colors = colors,
                    backdrop = backdrop,
                )

                if (useTitleCapsule && titleBarMode != "1" && titleBarMode != "3") {
                    TitleCapsuleGlassLayout(
                        state = state,
                        colors = colors,
                        onIntent = onIntent,
                        backdrop = backdrop,
                        titleTextColor = capsuleIconColor,
                    )
                } else if (titleBarMode != "1" && titleBarMode != "3") {
                    AppText(
                        text = state.bookName,
                        modifier = Modifier
                            .weight(1f)
                            .combinedClickable(
                                onClick = { onIntent(ReadBookIntent.OpenBookInfo) },
                                onLongClick = { onIntent(ReadBookIntent.OpenBookInfoDirect) },
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        style = LegadoTheme.typography.titleMedium.copy(
                            shadow = menuTextShadow
                        ),
                        color = titleTextColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Right group: actions
            // SPEC-209: always independent circular keys (same layout fill / liquid glass).
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val compact = state.menuConfig.titleBarCompact
                if (!state.isLocalBook) {
                    if (!compact && state.bookSource?.customButton == true) {
                        SourceCustomActionButton(
                            state = state,
                            colors = colors,
                            onIntent = onIntent,
                            backdrop = backdrop,
                        )
                    }
                    if (!compact) {
                        SourceActionButton(
                            state = state,
                            colors = colors,
                            onIntent = onIntent,
                            backdrop = backdrop,
                        )
                        RefreshActionButton(
                            state = state,
                            colors = colors,
                            onIntent = onIntent,
                            backdrop = backdrop,
                        )
                        DownloadActionButton(
                            state = state,
                            colors = colors,
                            onIntent = onIntent,
                            backdrop = backdrop,
                        )
                    }
                } else if (state.isLocalBook && !compact) {
                    if (state.isLocalTxt) {
                        TxtTocRuleActionButton(
                            state = state,
                            colors = colors,
                            onIntent = onIntent,
                            backdrop = backdrop,
                        )
                    }
                    CharsetActionButton(
                        state = state,
                        colors = colors,
                        onIntent = onIntent,
                        backdrop = backdrop,
                    )
                }
                // SPEC-208: left of more; same MenuTitleGlassButton style.
                if (underBrowserShell) {
                    TabletReaderFullscreenButton(
                        hideBrowserChrome = hideBrowserChrome,
                        chromePrefs = chromePrefs,
                        state = state,
                        colors = colors,
                        backdrop = backdrop,
                    )
                }
                // SPEC-155: 右上角 → MoreConfigSheet「更多设置」（不再开 MoreActions）。
                MenuTitleGlassButton(
                    onClick = {
                        onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.MoreConfig))
                    },
                    icon = Symbols.MoreVert,
                    contentDescription = stringResource(R.string.more_setting),
                    state = state,
                    colors = colors,
                    backdrop = backdrop,
                )
            }
        }

        // Book name on its own line (mode "1") — hidden when capsule is active
        if (titleBarMode == "1" && !useTitleCapsule) {
            AppText(
                text = state.bookName,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onIntent(ReadBookIntent.OpenBookInfo) },
                        onLongClick = { onIntent(ReadBookIntent.OpenBookInfoDirect) },
                    )
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                style = LegadoTheme.typography.titleMedium.copy(
                    shadow = menuTextShadow
                ),
                color = titleTextColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Chapter name + source action (modes "0" and "1") — hidden when capsule is active
        if ((titleBarMode == "0" || titleBarMode == "1") && !useTitleCapsule) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    text = state.chapterName,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (!state.isLocalBook) {
                                Modifier.combinedClickable(
                                    onClick = { onIntent(ReadBookIntent.OpenChapterUrl) },
                                    onLongClick = {
                                        onIntent(ReadBookIntent.ToggleReadUrlInBrowser)
                                    },
                                )
                            } else {
                                Modifier
                            }
                        ),
                    style = labelStyle,
                    color = titleTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (!state.isLocalBook && state.bookSource != null) {
                    var sourceMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        AppText(
                            text = state.bookSource.bookSourceName,
                            modifier = Modifier
                                .clickable { sourceMenuExpanded = true }
                                .padding(start = 8.dp),
                            style = labelStyle,
                            color = titleTextColor,
                            maxLines = 1,
                        )
                        RoundDropdownMenu(
                            expanded = sourceMenuExpanded,
                            onDismissRequest = { sourceMenuExpanded = false },
                        ) {
                            if (!state.bookSource.loginUrl.isNullOrBlank()) {
                                RoundDropdownMenuItem(
                                    leadingIcon = { MenuItemIcon(Symbols.Login) },
                                    text = stringResource(R.string.login),
                                    onClick = {
                                        sourceMenuExpanded = false
                                        onIntent(ReadBookIntent.ShowLogin)
                                    },
                                )
                            }
                            if (!state.bookSource.getContentRule().payAction.isNullOrBlank()) {
                                RoundDropdownMenuItem(
                                    leadingIcon = { MenuItemIcon(Symbols.Payment) },
                                    text = stringResource(R.string.chapter_pay),
                                    onClick = {
                                        sourceMenuExpanded = false
                                        onIntent(ReadBookIntent.PayAction)
                                    },
                                )
                            }
                            RoundDropdownMenuItem(
                                leadingIcon = { MenuItemIcon(Symbols.Edit) },
                                text = stringResource(R.string.edit_source),
                                onClick = {
                                    sourceMenuExpanded = false
                                    onIntent(ReadBookIntent.OpenSourceEdit)
                                },
                            )
                            RoundDropdownMenuItem(
                                leadingIcon = { MenuItemIcon(Symbols.Block) },
                                text = stringResource(R.string.disable_source),
                                onClick = {
                                    sourceMenuExpanded = false
                                    onIntent(ReadBookIntent.DisableSource)
                                },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun TabletReaderFullscreenButton(
    hideBrowserChrome: Boolean,
    chromePrefs: BrowserChromePrefs,
    state: ReadBookUiState,
    colors: ReadMenuColors,
    backdrop: Backdrop?,
) {
    MenuTitleGlassButton(
        onClick = { chromePrefs.hideBrowserChromeInReader = !hideBrowserChrome },
        icon = if (hideBrowserChrome) Symbols.CloseFullscreen else Symbols.OpenInFull,
        contentDescription = stringResource(
            if (hideBrowserChrome) R.string.exit_full_screen else R.string.hide_browser_chrome_in_reader,
        ),
        state = state,
        colors = colors,
        backdrop = backdrop,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuTitleGlassButton(
    onClick: () -> Unit,
    icon: MaterialSymbol,
    state: ReadBookUiState,
    colors: ReadMenuColors,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    contentDescription: String? = null,
) {
    val glassEnabled = readMenuTopBarButtonLiquidGlassEnabled(
        backdrop,
        state.menuConfig,
        io.legado.app.ui.theme.LocalAppUiConfiguration.current.theme.chromeSurfaceStyle,
    )
    ReadMenuGlassIconButton(
        onClick = onClick,
        icon = icon,
        colors = colors,
        backdrop = backdrop,
        menuConfig = state.menuConfig,
        glassEnabled = glassEnabled,
        // SPEC-209: fill mode keeps circular tonal discs like liquid-glass keys.
        iconStyle = if (glassEnabled) {
            state.menuConfig.titleBarIconStyle
        } else {
            1
        },
        modifier = modifier,
        onLongClick = onLongClick,
        menuBorderEnabled = state.menuConfig.readMenuBorderWidth > 0 &&
                state.menuConfig.readMenuTopBarTitleCapsule,
        contentDescription = contentDescription,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReadMenuGlassIconButton(
    onClick: () -> Unit,
    icon: MaterialSymbol,
    colors: ReadMenuColors,
    backdrop: Backdrop?,
    menuConfig: ReadMenuConfig,
    glassEnabled: Boolean,
    iconStyle: Int = menuConfig.readMenuIconStyle,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    menuBorderEnabled: Boolean = false,
    contentDescription: String? = null,
    glassRole: ReadMenuGlassRole = ReadMenuGlassRole.TopBar,
) {
    ReadMenuGlassButtonSurface(
        onClick = onClick,
        colors = colors,
        backdrop = backdrop,
        menuConfig = menuConfig,
        glassEnabled = glassEnabled,
        iconStyle = iconStyle,
        modifier = modifier,
        onLongClick = onLongClick,
        selected = selected,
        menuBorderEnabled = menuBorderEnabled,
        contentDescription = contentDescription,
        glassRole = glassRole,
    ) { tint ->
        SymbolIcon(
            symbol = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReadMenuGlassButtonSurface(
    onClick: () -> Unit,
    colors: ReadMenuColors,
    backdrop: Backdrop?,
    menuConfig: ReadMenuConfig,
    glassEnabled: Boolean,
    iconStyle: Int = menuConfig.readMenuIconStyle,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    menuBorderEnabled: Boolean = false,
    contentDescription: String? = null,
    /** SPEC-240: bottom-bar chips use [ReadMenuGlassRole.BottomButton]. */
    glassRole: ReadMenuGlassRole = ReadMenuGlassRole.TopBar,
    content: @Composable (Color) -> Unit,
) {
    val shape = CircleShape
    val tint = when {
        selected -> LegadoTheme.colorScheme.primary
        else -> LegadoTheme.colorScheme.onSurfaceVariant
    }
    val containerColor = readMenuIconButtonContainerColor(
        selected = selected,
        iconStyle = iconStyle,
    )
    val border = when {
        selected -> BorderStroke(1.5.dp, LegadoTheme.colorScheme.secondary)
        menuBorderEnabled -> BorderStroke(
            menuConfig.readMenuBorderWidth.dp,
            Color(readMenuBorderColor(menuConfig)),
        )
        !glassEnabled && iconStyle == 2 -> BorderStroke(1.dp, tint.copy(alpha = 0.45f))
        else -> null
    }
    val outerSize = if (glassEnabled) 48.dp else 40.dp
    val innerSize = 40.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(outerSize),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(innerSize)
                .then(
                    if (glassEnabled) {
                        Modifier.readMenuLiquidGlass(
                            backdrop = backdrop,
                            colors = colors,
                            shape = shape,
                            useTopBarStyle = true,
                            useLens = true,
                            blurRadius = 32.dp,
                            interactive = true,
                            menuConfig = menuConfig,
                            glassRole = glassRole,
                        )
                    } else {
                        Modifier
                            .clip(shape)
                            .background(containerColor, shape)
                    }
                )
                .then(if (border != null) Modifier.border(border, shape) else Modifier)
                .combinedClickable(
                    indication = if (glassEnabled) null else LocalIndication.current,
                    interactionSource = remember { MutableInteractionSource() },
                    role = Role.Button,
                    onLongClick = onLongClick,
                    onClick = onClick,
                )
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    }
                ),
        ) {
            content(tint)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.TitleCapsuleGlassLayout(
    state: ReadBookUiState,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    backdrop: Backdrop?,
    titleTextColor: Color,
) {
    // SPEC-210: 24dp round-rect capsule (fill + liquid glass share layout).
    val pillShape = RoundedCornerShape(24.dp)
    val glassEnabled = readerMenuLiquidGlassAvailable(
        backdrop,
        io.legado.app.ui.theme.LocalAppUiConfiguration.current.theme.chromeSurfaceStyle,
    )
    val iconStyle = state.menuConfig.titleBarIconStyle
    val border = when {
        state.menuConfig.readMenuBorderWidth > 0 -> BorderStroke(
            state.menuConfig.readMenuBorderWidth.dp,
            Color(readMenuBorderColor(state.menuConfig)),
        )

        !glassEnabled && iconStyle == 2 -> BorderStroke(
            1.dp,
            LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        )

        else -> null
    }

    Row(
        modifier = Modifier
            .weight(1f)
            .padding(start = 12.dp)
            .height(40.dp)
            .then(
                if (glassEnabled) {
                    Modifier.readMenuLiquidGlass(
                        backdrop = backdrop,
                        colors = colors,
                        shape = pillShape,
                        useTopBarStyle = true,
                        useLens = false,
                        blurRadius = 32.dp,
                        menuConfig = state.menuConfig,
                    )
                } else {
                    // SPEC-210: fill mode always has tonal capsule background.
                    Modifier
                        .clip(pillShape)
                        .background(LegadoTheme.colorScheme.surfaceContainerLow, pillShape)
                }
            )
            .then(if (border != null) Modifier.border(border, pillShape) else Modifier)
            .then(
                Modifier.combinedClickable(
                    indication = if (glassEnabled) null else LocalIndication.current,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { onIntent(ReadBookIntent.OpenBookInfo) },
                    onLongClick = { onIntent(ReadBookIntent.OpenBookInfoDirect) },
                )
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            AppText(
                text = state.bookName,
                style = LegadoTheme.typography.labelMediumEmphasized,
                color = titleTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.chapterName.isNotBlank()) {
                AppText(
                    text = state.chapterName,
                    style = LegadoTheme.typography.labelSmall,
                    color = titleTextColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MergedGlassIconButton(
    icon: MaterialSymbol,
    tint: Color,
    contentDescription: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        role = Role.Button,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        role = Role.Button,
                        onClick = onClick,
                    )
                }
            )
            .semantics { contentDescription?.let { this.contentDescription = it } },
    ) {
        SymbolIcon(
            symbol = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun MergedGlassDivider(tint: Color) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(20.dp)
            .background(tint.copy(alpha = 0.15f))
            .clearAndSetSemantics { }
    )
}

@Composable
private fun ChangeSourceMenuItems(
    dismiss: () -> Unit,
    onBookChange: () -> Unit,
    onChapterChange: () -> Unit,
) {
    RoundDropdownMenuItem(
        text = stringResource(R.string.change_origin),
        onClick = { dismiss(); onBookChange() },
    )
    RoundDropdownMenuItem(
        text = stringResource(R.string.chapter_change_source),
        onClick = { dismiss(); onChapterChange() },
    )
}

@Composable
private fun RefreshMenuItems(
    dismiss: () -> Unit,
    onRefreshDur: () -> Unit,
    onRefreshAfter: () -> Unit,
) {
    RoundDropdownMenuItem(
        text = stringResource(R.string.menu_refresh_dur),
        onClick = { dismiss(); onRefreshDur() },
    )
    RoundDropdownMenuItem(
        text = stringResource(R.string.menu_refresh_after),
        onClick = { dismiss(); onRefreshAfter() },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuTitleBarMergedGlassButton(
    state: ReadBookUiState,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    backdrop: Backdrop?,
    glassEnabled: Boolean,
) {
    var sourceExpanded by remember { mutableStateOf(false) }
    var refreshExpanded by remember { mutableStateOf(false) }

    val pillShape = RoundedCornerShape(50)
    val tint = LegadoTheme.colorScheme.onSurfaceVariant
    val compact = state.menuConfig.titleBarCompact
    val iconStyle = state.menuConfig.titleBarIconStyle

    Box {
        Row(
            modifier = Modifier
                .height(40.dp)
                .then(
                    if (glassEnabled) {
                        Modifier.readMenuLiquidGlass(
                            backdrop = backdrop,
                            colors = colors,
                            shape = pillShape,
                            useTopBarStyle = true,
                            useLens = true,
                            blurRadius = 32.dp,
                            interactive = true,
                            menuConfig = state.menuConfig,
                        )
                    } else {
                        val containerColor = when (iconStyle) {
                            1 -> LegadoTheme.colorScheme.surfaceContainerLow
                            else -> Color.Transparent
                        }
                        Modifier.background(containerColor, pillShape)
                    }
                )
                .then(
                    when {
                        state.menuConfig.readMenuBorderWidth > 0 &&
                                state.menuConfig.readMenuTopBarTitleCapsule ->
                            Modifier.border(
                                BorderStroke(
                                    state.menuConfig.readMenuBorderWidth.dp,
                                    Color(readMenuBorderColor(state.menuConfig)),
                                ),
                                pillShape,
                            )

                        !glassEnabled && iconStyle == 2 ->
                            Modifier.border(
                                BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
                                pillShape,
                            )

                        else -> Modifier
                    }
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // SwapHoriz - change source
            if (!state.isLocalBook && !compact) {
                if (state.bookSource?.customButton == true) {
                    MergedGlassIconButton(
                        icon = Symbols.Extension,
                        tint = tint,
                        contentDescription = stringResource(R.string.custom_button),
                        onClick = { onIntent(ReadBookIntent.SourceCustomButton(false)) },
                        onLongClick = { onIntent(ReadBookIntent.SourceCustomButton(true)) },
                    )
                    MergedGlassDivider(tint)
                }

                MergedGlassIconButton(
                    icon = Symbols.SwapHoriz,
                    tint = tint,
                    contentDescription = stringResource(R.string.change_origin),
                    onClick = { onIntent(ReadBookIntent.MenuChangeSource) },
                    onLongClick = { sourceExpanded = true },
                )
                MergedGlassDivider(tint)

                // Refresh
                MergedGlassIconButton(
                    icon = Symbols.Refresh,
                    tint = tint,
                    contentDescription = stringResource(R.string.menu_refresh_dur),
                    onClick = { onIntent(ReadBookIntent.MenuRefreshDur) },
                    onLongClick = { refreshExpanded = true },
                )
                MergedGlassDivider(tint)

                // Download
                MergedGlassIconButton(
                    icon = Symbols.CloudDownload,
                    tint = tint,
                    contentDescription = stringResource(R.string.offline_cache),
                    onClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Download)) },
                )
                MergedGlassDivider(tint)
            } else if (state.isLocalBook && !compact) {
                // TXT directory rule
                if (state.isLocalTxt) {
                    MergedGlassIconButton(
                        icon = Symbols.Toc,
                        tint = tint,
                        contentDescription = stringResource(R.string.txt_toc_rule),
                        onClick = { onIntent(ReadBookIntent.MenuTocRegex) },
                    )
                    MergedGlassDivider(tint)
                }

                // Text encoding
                MergedGlassIconButton(
                    icon = Symbols.Translate,
                    tint = tint,
                    contentDescription = stringResource(R.string.set_charset),
                    onClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Charset)) },
                )
                MergedGlassDivider(tint)
            }
            // SPEC-155: 右上角 → MoreConfigSheet「更多设置」。
            MergedGlassIconButton(
                icon = Symbols.MoreVert,
                tint = tint,
                contentDescription = stringResource(R.string.more_setting),
                onClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.MoreConfig)) },
            )
        }

        // Dropdown menus
        if (!state.isLocalBook) {
            RoundDropdownMenu(
                expanded = sourceExpanded,
                onDismissRequest = { sourceExpanded = false },
            ) { dismiss ->
                ChangeSourceMenuItems(
                    dismiss = dismiss,
                    onBookChange = { onIntent(ReadBookIntent.MenuBookChangeSource) },
                    onChapterChange = { onIntent(ReadBookIntent.MenuChapterChangeSource) },
                )
            }

            RoundDropdownMenu(
                expanded = refreshExpanded,
                onDismissRequest = { refreshExpanded = false },
            ) { dismiss ->
                RefreshMenuItems(
                    dismiss = dismiss,
                    onRefreshDur = { onIntent(ReadBookIntent.MenuRefreshDur) },
                    onRefreshAfter = { onIntent(ReadBookIntent.MenuRefreshAfter) },
                )
            }
        }

    }
}

@Composable
private fun SourceCustomActionButton(
    state: ReadBookUiState,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    backdrop: Backdrop?,
) {
    MenuTitleGlassButton(
        onClick = { onIntent(ReadBookIntent.SourceCustomButton(false)) },
        onLongClick = { onIntent(ReadBookIntent.SourceCustomButton(true)) },
        icon = Symbols.Extension,
        contentDescription = stringResource(R.string.custom_button),
        state = state,
        colors = colors,
        backdrop = backdrop,
    )
}

@Composable
private fun SourceActionButton(
    state: ReadBookUiState,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    backdrop: Backdrop?,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        MenuTitleGlassButton(
            onClick = { onIntent(ReadBookIntent.MenuChangeSource) },
            onLongClick = { expanded = true },
            icon = Symbols.SwapHoriz,
            contentDescription = stringResource(R.string.change_origin),
            state = state,
            colors = colors,
            backdrop = backdrop,
        )

        RoundDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) { dismiss ->
            ChangeSourceMenuItems(
                dismiss = dismiss,
                onBookChange = { onIntent(ReadBookIntent.MenuBookChangeSource) },
                onChapterChange = { onIntent(ReadBookIntent.MenuChapterChangeSource) },
            )
        }
    }
}

@Composable
private fun RefreshActionButton(
    state: ReadBookUiState,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    backdrop: Backdrop?,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        MenuTitleGlassButton(
            onClick = { onIntent(ReadBookIntent.MenuRefreshDur) },
            onLongClick = { expanded = true },
            icon = Symbols.Refresh,
            contentDescription = stringResource(R.string.menu_refresh_dur),
            state = state,
            colors = colors,
            backdrop = backdrop,
        )

        RoundDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) { dismiss ->
            RefreshMenuItems(
                dismiss = dismiss,
                onRefreshDur = { onIntent(ReadBookIntent.MenuRefreshDur) },
                onRefreshAfter = { onIntent(ReadBookIntent.MenuRefreshAfter) },
            )
        }
    }
}

@Composable
private fun DownloadActionButton(
    state: ReadBookUiState,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    backdrop: Backdrop?,
) {
    MenuTitleGlassButton(
        onClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Download)) },
        icon = Symbols.CloudDownload,
        contentDescription = stringResource(R.string.offline_cache),
        state = state,
        colors = colors,
        backdrop = backdrop,
    )
}

@Composable
private fun TxtTocRuleActionButton(
    state: ReadBookUiState,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    backdrop: Backdrop?,
) {
    MenuTitleGlassButton(
        onClick = { onIntent(ReadBookIntent.MenuTocRegex) },
        icon = Symbols.Toc,
        contentDescription = stringResource(R.string.txt_toc_rule),
        state = state,
        colors = colors,
        backdrop = backdrop,
    )
}

@Composable
private fun CharsetActionButton(
    state: ReadBookUiState,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    backdrop: Backdrop?,
) {
    MenuTitleGlassButton(
        onClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Charset)) },
        icon = Symbols.Translate,
        contentDescription = stringResource(R.string.set_charset),
        state = state,
        colors = colors,
        backdrop = backdrop,
    )
}

private fun readMenuTopBarButtonLiquidGlassEnabled(
    backdrop: Backdrop?,
    menuConfig: ReadMenuConfig,
    chromeSurfaceStyle: String,
): Boolean {
    // SPEC-056: global chrome surface drives liquid glass.
    return readerMenuLiquidGlassAvailable(backdrop, chromeSurfaceStyle)
}

private fun readMenuTopBarHazeEnabled(
    hazeState: HazeState?,
    menuConfig: ReadMenuConfig,
): Boolean {
    return hazeState != null && menuConfig.readMenuTopBarBlurMode == ReadMenuBlurMode.Haze
}

@Composable
private fun readMenuIconButtonContainerColor(selected: Boolean, iconStyle: Int): Color {
    return when {
        selected -> LegadoTheme.colorScheme.secondaryContainer
        iconStyle == 1 -> LegadoTheme.colorScheme.surfaceContainerLow
        else -> Color.Transparent
    }
}
