package io.legado.app.ui.config

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.config.ai.AiConfigRouteScreen
import io.legado.app.ui.config.backupConfig.BackupConfigRouteScreen
import io.legado.app.ui.config.browserConfig.BrowserConfigScreen
import io.legado.app.ui.config.coverConfig.CoverConfigRouteScreen
import io.legado.app.ui.config.downloadCacheConfig.DownloadCacheConfigRouteScreen
import io.legado.app.ui.config.otherConfig.OtherConfigRouteScreen
import io.legado.app.ui.config.privateConfig.PrivateConfigRouteScreen
import io.legado.app.ui.config.readConfig.ReadConfigRouteScreen
import io.legado.app.ui.config.themeConfig.ThemeConfigRouteScreen
import io.legado.app.ui.config.translation.TranslationConfigRouteScreen
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnDivider
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.privacy.PrivateVerifyGate
import io.legado.app.ui.widget.components.settingItem.SettingItem
import io.legado.app.ui.widget.components.topbar.LocalSuppressTopBarNavigation
import io.legado.app.ui.widget.components.topbar.TopBarButtonStyle
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton

/** SPEC-060: tablet settings left-rail categories; default Browser. */
enum class TabletSettingsPane {
    Browser,
    Theme,
    Other,
    Read,
    Cover,
    DownloadCache,
    Backup,
    Ai,
    Translation,
    Private,
}

@Composable
fun TabletSettingsScreen(
    onBackClick: () -> Unit,
    initialPane: TabletSettingsPane = TabletSettingsPane.Browser,
    onOpenSoftwareUpdate: () -> Unit = {},
    onNavigateToCoverAlbums: () -> Unit,
    onNavigateToCustomTheme: () -> Unit,
    onNavigateToThemeManage: () -> Unit,
    onNavigateToAiProviderEdit: (providerId: String?) -> Unit,
    onNavigateToAiModelEdit: (providerId: String?, modelProfileId: String?) -> Unit,
    onNavigateToTranslation: () -> Unit,
    onNavigateToAiSummary: () -> Unit,
    onNavigateToAiPrompt: () -> Unit,
    onNavigateToAiUsage: () -> Unit = {},
    onNavigateToAiFromTranslation: () -> Unit,
    onOpenClearBrowsingData: () -> Unit = {},
    onOpenBrowserPrivacy: () -> Unit = {},
) {
    var selected by rememberSaveable { mutableStateOf(initialPane.name) }
    val selectedPane = TabletSettingsPane.entries
        .firstOrNull { it.name == selected }
        ?: TabletSettingsPane.Browser

    // SPEC-081: disable liquid-glass sampling — primary back caused RenderThread SIGSEGV.
    AppScaffold(
        topBar = {},
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        disableLiquidGlass = true,
        disableContentSampling = true,
    ) { _ ->
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .statusBarsPadding(),
            ) {
                // SPEC-168/203: match MediumFlexibleTopAppBar expanded title (headlineSmall)
                // and baseline on the lower edge of the 112dp expressive bar.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .padding(horizontal = 4.dp),
                ) {
                    // SPEC-081: force Tonal — no liquid-glass back on tablet settings L1.
                    TopBarNavigationButton(
                        onClick = onBackClick,
                        style = TopBarButtonStyle.Tonal,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.setting),
                        style = MaterialTheme.typography.headlineSmall,
                        color = LegadoTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 12.dp, bottom = 12.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    // SPEC-203: Expressive spliced group (same as phone ConfigNav / right pane).
                    SplicedColumnGroup(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                    ) {
                        // SPEC-205: 软件更新在浏览器上方，无 leading icon。
                        SplicedColumnDivider()
                        SettingItem(
                            title = stringResource(R.string.software_update),
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            trailingContent = {},
                            onClick = onOpenSoftwareUpdate,
                        )
                        TabletSettingsPane.entries.forEach { pane ->
                            val isSelected = pane == selectedPane
                            SplicedColumnDivider()
                            SettingItem(
                                title = paneLabel(pane),
                                modifier = Modifier.fillMaxWidth(),
                                color = if (isSelected) {
                                    LegadoTheme.colorScheme.primary.copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                trailingContent = {},
                                onClick = { selected = pane.name },
                            )
                        }
                    }
                }
            }
            VerticalDivider()
            // SPEC-080: hide secondary (right-pane) top-bar back; left rail switches categories.
            CompositionLocalProvider(LocalSuppressTopBarNavigation provides true) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    when (selectedPane) {
                        TabletSettingsPane.Browser -> BrowserConfigScreen(
                            onBackClick = onBackClick,
                            onOpenClearBrowsingData = onOpenClearBrowsingData,
                            onOpenPrivacy = onOpenBrowserPrivacy,
                        )
                        TabletSettingsPane.Theme -> ThemeConfigRouteScreen(
                            onBackClick = onBackClick,
                            onNavigateToCustomTheme = onNavigateToCustomTheme,
                            onNavigateToThemeManage = onNavigateToThemeManage,
                        )
                        TabletSettingsPane.Other -> OtherConfigRouteScreen(onBackClick = onBackClick)
                        TabletSettingsPane.Read -> ReadConfigRouteScreen(onBackClick = onBackClick)
                        TabletSettingsPane.Cover -> CoverConfigRouteScreen(
                            onBackClick = onBackClick,
                            onNavigateToCoverAlbums = onNavigateToCoverAlbums,
                        )
                        TabletSettingsPane.DownloadCache ->
                            DownloadCacheConfigRouteScreen(onBackClick = onBackClick)
                        TabletSettingsPane.Backup -> BackupConfigRouteScreen(onBackClick = onBackClick)
                        TabletSettingsPane.Ai -> AiConfigRouteScreen(
                            onBackClick = onBackClick,
                            onNavigateToProviderEdit = onNavigateToAiProviderEdit,
                            onNavigateToModelEdit = onNavigateToAiModelEdit,
                            onNavigateToTranslation = onNavigateToTranslation,
                            onNavigateToAiSummary = onNavigateToAiSummary,
                            onNavigateToAiPrompt = onNavigateToAiPrompt,
                            onNavigateToAiUsage = onNavigateToAiUsage,
                        )
                        TabletSettingsPane.Translation -> TranslationConfigRouteScreen(
                            onBackClick = onBackClick,
                            onNavigateToAi = onNavigateToAiFromTranslation,
                        )
                        TabletSettingsPane.Private -> PrivateVerifyGate(onCancel = onBackClick) {
                            PrivateConfigRouteScreen(onBackClick = onBackClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun paneLabel(pane: TabletSettingsPane): String = when (pane) {
    TabletSettingsPane.Browser -> "浏览器"
    TabletSettingsPane.Theme -> stringResource(R.string.theme_setting)
    TabletSettingsPane.Other -> stringResource(R.string.other_setting)
    TabletSettingsPane.Read -> stringResource(R.string.read_config)
    TabletSettingsPane.Cover -> stringResource(R.string.cover_config)
    TabletSettingsPane.DownloadCache -> stringResource(R.string.download_cache_config)
    TabletSettingsPane.Backup -> stringResource(R.string.backup_restore)
    TabletSettingsPane.Ai -> stringResource(R.string.ai_config)
    TabletSettingsPane.Translation -> stringResource(R.string.translation_config)
    TabletSettingsPane.Private -> stringResource(R.string.privacy)
}
