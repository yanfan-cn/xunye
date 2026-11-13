package io.legado.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.browser.engine.NtpWallpaperStore
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ThemeConfigStore
import io.legado.app.ui.theme.ChromeSurfaceStyle
import io.legado.app.ui.theme.ChromeSurfaceStylePolicy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx

class OnboardingViewModel(
    private val themeSettingsGateway: ThemeSettingsGateway,
    private val appShellSettingsGateway: AppShellSettingsGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OnboardingUiState(
            privacyPolicy = readAsset("privacyPolicy.md"),
            featuresIntro = readAsset("onboardingFeatures.md"),
            theme = themeSettingsGateway.currentSettings,
            themeMode = appShellSettingsGateway.currentSettings.themeMode,
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<OnboardingEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        // SPEC-223: default follow system; do not force light mode.
        viewModelScope.launch {
            themeSettingsGateway.settings.collect { theme ->
                _uiState.update { it.copy(theme = theme) }
            }
        }
        viewModelScope.launch {
            appShellSettingsGateway.settings.collect { shell ->
                _uiState.update { it.copy(themeMode = shell.themeMode) }
            }
        }
    }

    fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.Next -> nextPage()
            OnboardingIntent.Prev -> previousPage()
            is OnboardingIntent.SelectTheme -> selectTheme(intent.value)
            is OnboardingIntent.SetThemeMode -> viewModelScope.launch {
                appShellSettingsGateway.update { it.copy(themeMode = intent.value) }
                _effects.tryEmit(OnboardingEffect.ApplyDayNight)
            }
            is OnboardingIntent.SetPureBlack -> viewModelScope.launch {
                themeSettingsGateway.update { it.copy(isPureBlack = intent.enabled) }
            }
            is OnboardingIntent.SetLiquidGlass -> setLiquidGlass(intent.enabled)
        }
    }

    private fun nextPage() {
        val state = _uiState.value
        if (state.page < state.pageCount - 1) {
            _uiState.update { it.copy(page = it.page + 1) }
        } else {
            LocalConfig.privacyPolicyOk = true
            // SPEC-266: seed NTP wallpaper once from theme (dark→neon / light→mist).
            NtpWallpaperStore(appCtx).seedDefaultIfNeeded(ThemeConfigStore.isNightTheme)
            _effects.tryEmit(OnboardingEffect.NavigateHome)
        }
    }

    private fun previousPage() {
        val state = _uiState.value
        if (state.page > 0) {
            _uiState.update { it.copy(page = it.page - 1) }
        } else {
            _effects.tryEmit(OnboardingEffect.Finish)
        }
    }

    private fun selectTheme(value: String) {
        val theme = _uiState.value.theme
        if (value == "13" &&
            (theme.backgroundImageLight.isNullOrEmpty() || theme.backgroundImageDark.isNullOrEmpty())
        ) {
            _effects.tryEmit(OnboardingEffect.ShowToast(R.string.transparent_theme_alarm))
            return
        }
        viewModelScope.launch {
            themeSettingsGateway.update {
                it.copy(
                    appTheme = value,
                    containerOpacity = if (value == "13") 0 else it.containerOpacity,
                )
            }
        }
    }

    private fun setLiquidGlass(enabled: Boolean) {
        val style = if (enabled) ChromeSurfaceStyle.LiquidGlass else ChromeSurfaceStyle.Filled
        val storage = style.storageValue
        viewModelScope.launch {
            themeSettingsGateway.update {
                it.copy(
                    chromeSurfaceStyle = storage,
                    topBarButtonStyle =
                        ChromeSurfaceStylePolicy.effectiveTopBarButtonStyle(storage),
                )
            }
        }
    }

    private fun readAsset(fileName: String): String =
        runCatching {
            appCtx.assets.open(fileName).bufferedReader().use { it.readText() }
        }.getOrDefault("")
}
