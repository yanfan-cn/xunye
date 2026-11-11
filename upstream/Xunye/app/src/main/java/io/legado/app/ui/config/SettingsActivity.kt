package io.legado.app.ui.config

import android.content.Intent
import android.os.Bundle
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.NavDisplay
import io.legado.app.base.BaseComposeActivity
import io.legado.app.browser.engine.BrowserFormFactor
import io.legado.app.ui.browser.BrowserClearDataScreen
import io.legado.app.ui.browser.BrowserPickBlockScreen
import io.legado.app.ui.browser.BrowserPrivacyScreen
import io.legado.app.ui.config.ai.AiConfigRouteScreen
import io.legado.app.ui.config.ai.AiModelEditRouteScreen
import io.legado.app.ui.config.ai.AiProviderEditRouteScreen
import io.legado.app.ui.config.ai.prompt.AiPromptConfigRouteScreen
import io.legado.app.ui.config.ai.summary.AiSummaryConfigRouteScreen
import io.legado.app.ui.config.backupConfig.BackupConfigRouteScreen
import io.legado.app.ui.config.browserConfig.BrowserConfigScreen
import io.legado.app.ui.config.coverConfig.CoverAlbumManageRouteScreen
import io.legado.app.ui.config.coverConfig.CoverConfigRouteScreen
import io.legado.app.ui.config.customTheme.CustomThemeRouteScreen
import io.legado.app.ui.config.downloadCacheConfig.DownloadCacheConfigRouteScreen
import io.legado.app.ui.config.otherConfig.OtherConfigRouteScreen
import io.legado.app.ui.config.privateConfig.PrivateConfigRouteScreen
import io.legado.app.ui.config.readConfig.ReadConfigRouteScreen
import io.legado.app.ui.config.themeConfig.ThemeConfigRouteScreen
import io.legado.app.ui.config.themeManage.ThemeManageRouteScreen
import io.legado.app.ui.config.translation.TranslationConfigRouteScreen
import io.legado.app.ui.main.MainNavigator
import io.legado.app.ui.main.MainRouteBrowserClearData
import io.legado.app.ui.main.MainRouteBrowserPickBlock
import io.legado.app.ui.main.MainRouteBrowserPrivacy
import io.legado.app.ui.main.MainRouteSettings
import io.legado.app.ui.main.MainRouteSettingsAi
import io.legado.app.ui.main.MainRouteSettingsAiModelEdit
import io.legado.app.ui.main.MainRouteSettingsAiProviderEdit
import io.legado.app.ui.main.MainRouteSettingsAiPrompt
import io.legado.app.ui.main.MainRouteSettingsAiSummary
import io.legado.app.ui.main.MainRouteSettingsAiUsage
import io.legado.app.ui.main.MainRouteSettingsBackup
import io.legado.app.ui.main.MainRouteSettingsBrowser
import io.legado.app.ui.main.MainRouteSettingsCover
import io.legado.app.ui.main.MainRouteSettingsCoverAlbums
import io.legado.app.ui.main.MainRouteSettingsCustomTheme
import io.legado.app.ui.main.MainRouteSettingsDownloadCache
import io.legado.app.ui.main.MainRouteSettingsOther
import io.legado.app.ui.main.MainRouteSettingsPrivate
import io.legado.app.ui.main.MainRouteSettingsRead
import io.legado.app.ui.main.MainRouteSettingsTheme
import io.legado.app.ui.main.MainRouteSettingsThemeManage
import io.legado.app.ui.main.MainRouteSettingsTranslation
import io.legado.app.ui.widget.components.privacy.PrivateVerifyGate

/**
 * SPEC-192: 独立 Activity 宿主的设置族。
 *
 * 原版 gedoor legado 的设置本就是 Activity。此前设置压在 MainActivity 的 nav3 栈上，
 * 退出时整个主壳（阅读列表/我的/网页所在场景）要在转场帧整棵重建——重建期间无内容，
 * 观感为「无动画、闪一下、卡顿」。
 *
 * 设置搬出后：进出转场交给系统窗口动画，MainActivity 全程保持渲染、不重组；
 * 设置族内部的层级（根→子页→孙页）由本 Activity 自己的 nav3 栈承载，转场沿用
 * 与原版一致的 Material Shared Axis（照抄 SPEC-162 的 metadata）。
 *
 * 跨族跳转（阅读器/书源登录/书源编辑/网页）经 MainIntent 工厂走 Intent，
 * 与 SPEC-190 的阅读器 Activity 同一套模式。
 */
class SettingsActivity : BaseComposeActivity() {

    private val pendingStartRoute = mutableStateOf<io.legado.app.ui.main.MainRoute?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleTask 复用实例：把新的 start route 落成当前栈（旧栈丢弃）。
        pendingStartRoute.value = resolveFamilyRoute(intent)
    }

    @Composable
    override fun Content() {
        val backStack = remember { listOf<io.legado.app.ui.main.MainRoute>(resolveFamilyRoute(intent)).toMutableStateList() }
        LaunchedEffect(pendingStartRoute.value) {
            pendingStartRoute.value?.let { route ->
                backStack.clear()
                backStack.add(route)
                pendingStartRoute.value = null
            }
        }
        fun navigateBack() {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            } else {
                finish()
            }
        }
        // SPEC-224: 废除导航转场玻璃抑制。

        NavDisplay(
            backStack = backStack,
            transitionSpec = { settingsSharedAxisPush() },
            popTransitionSpec = { settingsSharedAxisPop() },
            predictivePopTransitionSpec = { _ -> settingsSharedAxisPop() },
            entryProvider = settingsFamilyEntryProvider(
                backStack = backStack,
                onNavigateBack = { navigateBack() },
                onOpenSoftwareUpdate = {
                    // SPEC-205 / SPEC-119 / SPEC-221: 飞盘链在巡页浏览器页内打开并切到主页。
                    io.legado.app.browser.engine.BrowserOpenBridge.offer(
                        getString(io.legado.app.R.string.software_update_url),
                    )
                    io.legado.app.ui.main.MainShortcutBus.offer(
                        io.legado.app.ui.main.MainShortcutBus.Event.BrowserHome,
                    )
                    startActivity(
                        android.content.Intent(
                            this,
                            io.legado.app.ui.main.MainActivity::class.java,
                        ).apply {
                            addFlags(
                                android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP,
                            )
                        },
                    )
                    finish()
                },
            ),
        )
    }

    private fun resolveFamilyRoute(intent: Intent?): io.legado.app.ui.main.MainRoute {
        return when (val resolved = MainNavigator.resolveStartRoute(intent)) {
            is MainRouteSettings,
            is MainRouteSettingsBrowser,
            is MainRouteBrowserPrivacy,
            is MainRouteBrowserPickBlock,
            is MainRouteBrowserClearData,
            is MainRouteSettingsOther,
            is MainRouteSettingsRead,
            is MainRouteSettingsCover,
            is MainRouteSettingsCoverAlbums,
            is MainRouteSettingsTheme,
            is MainRouteSettingsThemeManage,
            is MainRouteSettingsCustomTheme,
            is MainRouteSettingsBackup,
            is MainRouteSettingsAi,
            is MainRouteSettingsAiSummary,
            is MainRouteSettingsAiPrompt,
            is MainRouteSettingsAiUsage,
            is MainRouteSettingsDownloadCache,
            is MainRouteSettingsTranslation,
            is MainRouteSettingsPrivate -> resolved
            else -> MainRouteSettings
        }
    }
}

private fun settingsFamilyEntryProvider(
    backStack: MutableList<io.legado.app.ui.main.MainRoute>,
    onNavigateBack: () -> Unit,
    onOpenSoftwareUpdate: () -> Unit,
) = entryProvider {
    entry<MainRouteSettings>(metadata = settingsSharedAxisMetadata()) {
        val isTablet = BrowserFormFactor.isTablet(LocalConfiguration.current)
        if (isTablet) {
            TabletSettingsScreen(
                onBackClick = onNavigateBack,
                initialPane = TabletSettingsPane.Browser,
                onOpenSoftwareUpdate = onOpenSoftwareUpdate,
                onNavigateToCoverAlbums = { backStack.add(MainRouteSettingsCoverAlbums) },
                onNavigateToCustomTheme = { backStack.add(MainRouteSettingsCustomTheme) },
                onNavigateToThemeManage = { backStack.add(MainRouteSettingsThemeManage) },
                onNavigateToAiProviderEdit = { providerId ->
                    backStack.add(MainRouteSettingsAiProviderEdit(providerId = providerId))
                },
                onNavigateToAiModelEdit = { providerId, modelProfileId ->
                    backStack.add(
                        MainRouteSettingsAiModelEdit(
                            providerId = providerId,
                            modelProfileId = modelProfileId,
                        ),
                    )
                },
                onNavigateToTranslation = { backStack.add(MainRouteSettingsTranslation) },
                onNavigateToAiSummary = { backStack.add(MainRouteSettingsAiSummary) },
                onNavigateToAiPrompt = { backStack.add(MainRouteSettingsAiPrompt) },
                onNavigateToAiUsage = { backStack.add(MainRouteSettingsAiUsage) },
                onNavigateToAiFromTranslation = { backStack.add(MainRouteSettingsAi) },
                onOpenClearBrowsingData = { backStack.add(MainRouteBrowserClearData) },
                onOpenBrowserPrivacy = { backStack.add(MainRouteBrowserPrivacy) },
            )
        } else {
            ConfigNavScreen(
                onBackClick = onNavigateBack,
                onOpenSoftwareUpdate = onOpenSoftwareUpdate,
                onNavigateToBrowser = { backStack.add(MainRouteSettingsBrowser) },
                onNavigateToOther = { backStack.add(MainRouteSettingsOther) },
                onNavigateToRead = { backStack.add(MainRouteSettingsRead) },
                onNavigateToCover = { backStack.add(MainRouteSettingsCover) },
                onNavigateToTheme = { backStack.add(MainRouteSettingsTheme) },
                onNavigateToBackup = { backStack.add(MainRouteSettingsBackup) },
                onNavigateToAi = { backStack.add(MainRouteSettingsAi) },
                onNavigateToDownloadCache = { backStack.add(MainRouteSettingsDownloadCache) },
                onNavigateToTranslation = { backStack.add(MainRouteSettingsTranslation) },
                onNavigateToPrivate = { backStack.add(MainRouteSettingsPrivate) },
            )
        }
    }

    entry<MainRouteSettingsBrowser>(metadata = settingsSharedAxisMetadata()) {
        BrowserConfigScreen(
            onBackClick = onNavigateBack,
            onOpenClearBrowsingData = { backStack.add(MainRouteBrowserClearData) },
            onOpenPrivacy = { backStack.add(MainRouteBrowserPrivacy) },
            onOpenPickBlock = { backStack.add(MainRouteBrowserPickBlock) },
        )
    }

    entry<MainRouteBrowserPrivacy>(metadata = settingsSharedAxisMetadata()) {
        BrowserPrivacyScreen(
            onBack = onNavigateBack,
            onOpenClearBrowsingData = { backStack.add(MainRouteBrowserClearData) },
        )
    }

    entry<MainRouteBrowserPickBlock>(metadata = settingsSharedAxisMetadata()) {
        BrowserPickBlockScreen(
            onBack = onNavigateBack,
        )
    }

    entry<MainRouteBrowserClearData>(metadata = settingsSharedAxisMetadata()) {
        BrowserClearDataScreen(
            onBack = onNavigateBack,
        )
    }

    entry<MainRouteSettingsOther>(metadata = settingsSharedAxisMetadata()) {
        OtherConfigRouteScreen(onBackClick = onNavigateBack)
    }

    entry<MainRouteSettingsRead>(metadata = settingsSharedAxisMetadata()) {
        ReadConfigRouteScreen(onBackClick = onNavigateBack)
    }

    entry<MainRouteSettingsCover>(metadata = settingsSharedAxisMetadata()) {
        CoverConfigRouteScreen(
            onBackClick = onNavigateBack,
            onNavigateToCoverAlbums = {
                backStack.add(MainRouteSettingsCoverAlbums)
            },
        )
    }

    entry<MainRouteSettingsCoverAlbums>(metadata = settingsSharedAxisMetadata()) {
        CoverAlbumManageRouteScreen(onBackClick = onNavigateBack)
    }

    entry<MainRouteSettingsTheme>(metadata = settingsSharedAxisMetadata()) {
        ThemeConfigRouteScreen(
            onBackClick = onNavigateBack,
            onNavigateToCustomTheme = { backStack.add(MainRouteSettingsCustomTheme) },
            onNavigateToThemeManage = { backStack.add(MainRouteSettingsThemeManage) },
        )
    }

    entry<MainRouteSettingsBackup>(metadata = settingsSharedAxisMetadata()) {
        BackupConfigRouteScreen(onBackClick = onNavigateBack)
    }

    entry<MainRouteSettingsAi>(metadata = settingsSharedAxisMetadata()) {
        AiConfigRouteScreen(
            onBackClick = onNavigateBack,
            onNavigateToProviderEdit = { providerId ->
                backStack.add(MainRouteSettingsAiProviderEdit(providerId = providerId))
            },
            onNavigateToModelEdit = { providerId, modelProfileId ->
                backStack.add(
                    MainRouteSettingsAiModelEdit(
                        providerId = providerId,
                        modelProfileId = modelProfileId,
                    )
                )
            },
            onNavigateToTranslation = { backStack.add(MainRouteSettingsTranslation) },
            onNavigateToAiSummary = { backStack.add(MainRouteSettingsAiSummary) },
            onNavigateToAiPrompt = { backStack.add(MainRouteSettingsAiPrompt) },
            onNavigateToAiUsage = { backStack.add(MainRouteSettingsAiUsage) },
        )
    }

    entry<MainRouteSettingsAiSummary>(metadata = settingsSharedAxisMetadata()) {
        AiSummaryConfigRouteScreen(onBackClick = onNavigateBack)
    }

    entry<MainRouteSettingsAiPrompt>(metadata = settingsSharedAxisMetadata()) {
        AiPromptConfigRouteScreen(onBackClick = onNavigateBack)
    }

    entry<MainRouteSettingsAiUsage>(metadata = settingsSharedAxisMetadata()) {
        io.legado.app.ui.config.ai.AiUsageRouteScreen(onBackClick = onNavigateBack)
    }

    entry<MainRouteSettingsAiProviderEdit>(metadata = settingsSharedAxisMetadata()) { route ->
        AiProviderEditRouteScreen(
            providerId = route.providerId,
            onBackClick = onNavigateBack,
        )
    }

    entry<MainRouteSettingsAiModelEdit>(metadata = settingsSharedAxisMetadata()) { route ->
        AiModelEditRouteScreen(
            providerId = route.providerId,
            modelProfileId = route.modelProfileId,
            onBackClick = onNavigateBack,
        )
    }

    entry<MainRouteSettingsDownloadCache>(metadata = settingsSharedAxisMetadata()) {
        DownloadCacheConfigRouteScreen(onBackClick = onNavigateBack)
    }

    entry<MainRouteSettingsTranslation>(metadata = settingsSharedAxisMetadata()) {
        TranslationConfigRouteScreen(
            onBackClick = onNavigateBack,
            onNavigateToAi = { backStack.add(MainRouteSettingsAi) },
        )
    }

    entry<MainRouteSettingsPrivate>(metadata = settingsSharedAxisMetadata()) {
        // 隐私设置页可以关掉三种验证时机，所以进入前要求确认身份（生物或密码）。
        // 只校验、不授予：进来一次不该顺带解锁私密内容。
        PrivateVerifyGate(onCancel = onNavigateBack) {
            PrivateConfigRouteScreen(onBackClick = onNavigateBack)
        }
    }

    entry<MainRouteSettingsCustomTheme>(metadata = settingsSharedAxisMetadata()) {
        CustomThemeRouteScreen(
            onBackClick = onNavigateBack,
        )
    }

    entry<MainRouteSettingsThemeManage>(metadata = settingsSharedAxisMetadata()) {
        ThemeManageRouteScreen(onBackClick = onNavigateBack)
    }
}

/** 照抄 mainEntryProvider 的设置 Shared Axis（SPEC-162，对齐原版 legado / M3）。 */
private fun AnimatedContentTransitionScope<*>.settingsSharedAxisPush() =
    (slideInHorizontally(
        animationSpec = tween(durationMillis = SettingsAxisSlideMs, easing = FastOutSlowInEasing),
        initialOffsetX = { fullWidth -> fullWidth },
    ) + fadeIn(
        animationSpec = tween(durationMillis = SettingsAxisFadeMs, easing = LinearOutSlowInEasing),
    )) togetherWith (slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(durationMillis = SettingsAxisSlideMs, easing = FastOutSlowInEasing),
        targetOffset = { fullWidth -> fullWidth / 4 },
    ) + fadeOut(
        animationSpec = tween(durationMillis = SettingsAxisFadeMs, easing = LinearOutSlowInEasing),
    ))

private fun AnimatedContentTransitionScope<*>.settingsSharedAxisPop() =
    (slideInHorizontally(
        animationSpec = tween(durationMillis = SettingsAxisSlideMs, easing = FastOutSlowInEasing),
        initialOffsetX = { fullWidth -> -fullWidth / 4 },
    ) + fadeIn(
        animationSpec = tween(durationMillis = SettingsAxisFadeMs, easing = LinearOutSlowInEasing),
    )) togetherWith (scaleOut(
        targetScale = 0.8f,
        animationSpec = tween(durationMillis = SettingsAxisSlideMs, easing = FastOutSlowInEasing),
    ) + fadeOut(animationSpec = tween(durationMillis = SettingsAxisFadeMs)))

private fun settingsSharedAxisMetadata() = metadata {
    put(NavDisplay.TransitionKey) { settingsSharedAxisPush() }
    put(NavDisplay.PopTransitionKey) { settingsSharedAxisPop() }
    put(NavDisplay.PredictivePopTransitionKey) { _ -> settingsSharedAxisPop() }
}

private const val SettingsAxisSlideMs = 300
private const val SettingsAxisFadeMs = 210
