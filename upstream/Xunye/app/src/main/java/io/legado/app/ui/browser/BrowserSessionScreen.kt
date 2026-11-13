package io.legado.app.ui.browser

import io.legado.app.R
import io.legado.app.ui.widget.components.icon.MaterialSymbol
import io.legado.app.ui.widget.components.icon.SymbolIcon
import io.legado.app.ui.widget.components.icon.Symbols
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.unit.DpOffset
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import androidx.compose.ui.zIndex
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousCapsule
import io.legado.app.browser.engine.AlohaBrowserCompose
import io.legado.app.browser.engine.AwContentsHostView
import io.legado.app.browser.engine.BrowseStore
import io.legado.app.browser.engine.BrowserAdRules
import io.legado.app.browser.engine.BrowserAwContentsClient
import io.legado.app.browser.engine.BrowserCustomViewCallback
import io.legado.app.browser.engine.BrowserJsCallback
import io.legado.app.browser.engine.BrowserChromePrefs
import io.legado.app.browser.engine.BrowserClipboardUrl
import io.legado.app.browser.engine.BrowserContextMenuJs
import io.legado.app.browser.engine.BrowserDownloadHelper
import io.legado.app.browser.engine.BrowserDownloadPaths
import io.legado.app.browser.engine.BrowserDownloadRepository
import io.legado.app.browser.engine.BrowserFormFactor
import io.legado.app.browser.engine.BrowserHttpsFallback
import io.legado.app.browser.engine.BrowserLoadProgress
import io.legado.app.browser.engine.BrowserNtpPrefs
import io.legado.app.browser.engine.BrowserOmniboxLabel
import io.legado.app.browser.engine.BrowserOmniboxSuggest
import io.legado.app.browser.engine.reselectAllOmniboxText
import io.legado.app.browser.engine.BrowserOpenBridge
import io.legado.app.browser.engine.BrowserReaderPrefs
import io.legado.app.browser.engine.BrowserScrollMemory
import io.legado.app.browser.engine.BrowserSessionHolder
import io.legado.app.browser.engine.BrowserShieldJs
import io.legado.app.browser.engine.BrowserShieldPrefs
import io.legado.app.browser.engine.BrowserSiteCssFixes
import io.legado.app.browser.engine.BrowserTabManager
import io.legado.app.browser.engine.BrowserUa
import io.legado.app.browser.engine.BrowserUrlPolicy
import io.legado.app.browser.engine.BrowserVideoOffer
import io.legado.app.browser.engine.BrowserVideoOfferBus
import io.legado.app.browser.engine.evalJs
import io.legado.app.browser.engine.resolveBrowserNavigationInput
import io.legado.app.ui.browser.video.BrowserVideoLeaveGuard
import io.legado.app.ui.browser.video.BrowserVideoPlaybackService
import io.legado.app.ui.browser.video.BrowserVideoPlayerActivity
import io.legado.app.ui.browser.video.BrowserVideoPlayerBridge
import io.legado.app.ui.browser.video.BrowserWebMediaBridge
import io.legado.app.ui.main.MainIntent
import io.legado.app.ui.main.MainRouteBrowserPickBlock
import io.legado.app.ui.main.MainRouteRequestBus
import androidx.compose.ui.platform.LocalConfiguration
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import org.apache.commons.text.StringEscapeUtils
import java.io.File
import io.legado.app.browser.manga.cimoc.CimocAdapterRegistry
import io.legado.app.browser.manga.cimoc.CimocMangaBridge
import io.legado.app.browser.manga.cimoc.json.CimocJsonMangaBridge
import io.legado.app.browser.manga.cimoc.json.CimocJsonRuleStore
import io.legado.app.browser.reader.WebArticleBookFactory
import io.legado.app.browser.reader.WebArticleChromeFilter
import io.legado.app.browser.reader.WebArticleExtractJs
import io.legado.app.browser.reader.WebArticleMixedHydration
import io.legado.app.browser.reader.WebArticleVideoThumb
import io.legado.app.browser.reader.WebChapterExpandJs
import io.legado.app.browser.reader.WebChapterHttpFetcher
import io.legado.app.browser.reader.WebMangaBookFactory
import io.legado.app.browser.reader.WebMangaImageFilter
import io.legado.app.browser.reader.WebPageAnalyzeJs
import io.legado.app.browser.reader.WebPageLangDetect
import io.legado.app.browser.reader.WebPageLangDetectJs
import io.legado.app.browser.reader.WebPageInPlaceTranslator
import io.legado.app.browser.reader.WebPageTranslate
import io.legado.app.browser.reader.WebTocDiscoverJs
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.gateway.TranslationSettingsGateway
import io.legado.app.ui.association.OnLineImportActivity
import io.legado.app.ui.book.read.sheet.EyeProtectionConfigSheet
import org.koin.core.context.GlobalContext
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalTopBarBackdrop
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.FloatingBottomBar
import io.legado.app.ui.widget.components.FloatingBottomBarItem
import io.legado.app.ui.widget.components.menu.MenuBarBoundsProvider
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppLinearProgressIndicator
import io.legado.app.utils.keepScreenOn
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.utils.toggleSystemBar
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import androidx.compose.runtime.LaunchedEffect

private val TopBarHeight = BrowserUiTokens.TopBarHeight
private val AddressFieldHeight = BrowserUiTokens.InputHeightSmall
private val CardCorner = BrowserUiTokens.CardCorner

data class BrowserSessionActions(
    val goBack: () -> Unit,
    val goForward: () -> Unit,
    val reload: () -> Unit,
    /** SPEC-163 */
    val backHistoryItems: () -> List<io.legado.app.browser.engine.BrowserNavHistory.Item> = { emptyList() },
    val forwardHistoryItems: () -> List<io.legado.app.browser.engine.BrowserNavHistory.Item> = { emptyList() },
    val goHistoryOffset: (Int) -> Unit = {},
    val scrollToTop: () -> Unit = {},
    val openReader: () -> Unit,
    val loadUrl: (String) -> Unit = {},
    /** SPEC-025: tablet toolbox helpers. */
    val currentPageUrl: () -> String = { "" },
    val currentPageTitle: () -> String = { "" },
    val addBookmark: () -> Unit = {},
    val copyCurrentUrl: () -> Unit = {},
    val startPickMode: () -> Unit = {},
    /** SPEC-058/064 */
    val shareCurrentPage: () -> Unit = {},
    val saveCurrentPage: () -> Unit = {},
    val openDownloads: () -> Unit = {},
    /** SPEC-107: live AwSettings from tablet toolbox. */
    val applyWebContentDarkMode: (String) -> Unit = {},
    val applyTextZoom: (Int) -> Unit = {},
    val injectAdShield: () -> Unit = {},
    /** SPEC-119 */
    val applyUserAgentAndReload: (BrowserUa.Mode) -> Unit = {},
    val openFindInPage: () -> Unit = {},
    /** SPEC-136 */
    val videoCapsuleAvailable: () -> Boolean = { false },
    val openVideoCapsule: () -> Unit = {},
    /** SPEC-120 */
    val applyForcePageZoom: (Boolean) -> Unit = {},
    val applyPrivacySettings: () -> Unit = {},
    /** SPEC-111: tablet reader long-press menu. */
    val readerAutoOpenEnabled: () -> Boolean = { false },
    val setReaderAutoOpen: (Boolean) -> Unit = {},
    /** SPEC-220: 「极致净化」同设置页偏好。 */
    val extremePurgeEnabled: () -> Boolean = { true },
    val setExtremePurge: (Boolean) -> Unit = {},
    val openReaderText: () -> Unit = {},
    val openReaderImage: () -> Unit = {},
    val openReaderMixed: () -> Unit = {},
    /** SPEC-251: in-page translate (tablet omnibox). */
    val translatePage: () -> Unit = {},
    /** SPEC-254: tablet omnibox shows translate only when page needs it. */
    val pageNeedsTranslate: Boolean = false,
    /** SPEC-262: page currently showing in-page translation (keep omnibox key for restore). */
    val pageTranslated: Boolean = false,
    /** SPEC-259: current URL bookmarked (toolbox + tablet omnibox toggle). */
    val pageBookmarked: Boolean = false,
)

/**
 * Browser session chrome per UI design §3.2 / SPEC-008/011/017/018.
 * [tabletEmbedded]=true hides phone top/bottom chrome (parent supplies tablet shell).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun BrowserSessionScreen(
    initialUrl: String,
    showNavigationBack: Boolean,
    onNavigateBack: () -> Unit,
    onImportBookSource: ((String) -> Unit)? = null,
    initialHtml: String? = null,
    incognito: Boolean = false,
    onOpenBrowserSettings: (() -> Unit)? = null,
    onOpenBookmarks: (() -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null,
    onOpenHistory: (() -> Unit)? = null,
    onOpenTabs: (() -> Unit)? = null,
    onOpenReader: ((bookUrl: String) -> Unit)? = null,
    onOpenMangaReader: ((bookUrl: String) -> Unit)? = null,
    sessionKey: String? = null,
    initialOmniboxLabel: String? = null,
    /** SPEC-125: page title for idle omnibox; bumped [navEpoch] forces load after park. */
    initialTitle: String? = null,
    navEpoch: Long = 0L,
    onPageMeta: ((url: String, title: String) -> Unit)? = null,
    tabletEmbedded: Boolean = false,
    onBindActions: ((BrowserSessionActions) -> Unit)? = null,
    onScrollDirection: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val store = remember { BrowseStore(context) }
    val shield = remember { BrowserShieldPrefs(context) }
    val readerPrefs = remember { BrowserReaderPrefs(context) }
    val chromePrefs = remember { BrowserChromePrefs(context) }
    var uaMode by remember {
        mutableStateOf(chromePrefs.uaModeOrDefault(BrowserFormFactor.isTablet(context)))
    }
    var webDarkMode by remember { mutableStateOf(chromePrefs.webContentDarkMode) }
    var textZoomPercent by remember { mutableIntStateOf(chromePrefs.textZoomPercent) }
    var showTextZoomSheet by remember { mutableStateOf(false) }
    var showEyeConfigSheet by remember { mutableStateOf(false) }
    var showFindInPage by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findMatchLabel by remember { mutableStateOf("") }
    val themeSettingsGateway = remember {
        GlobalContext.get().get<ThemeSettingsGateway>()
    }
    val themeSettings by themeSettingsGateway.settings.collectAsStateWithLifecycle(
        themeSettingsGateway.currentSettings,
    )
    remember { BrowserAdRules.ensureLoaded(context) }

    var progress by remember { mutableIntStateOf(100) }
    var mainFrameLoading by remember { mutableStateOf(false) }
    var hostRef by remember { mutableStateOf<AwContentsHostView?>(null) }
    var clientRef by remember { mutableStateOf<BrowserAwContentsClient?>(null) }
    // SPEC-259: Compose also re-applies AUTO when system night flips (Activity may not
    // forward View.onConfigurationChanged to the host in all paths).
    val systemUiMode = LocalConfiguration.current.uiMode
    LaunchedEffect(systemUiMode, webDarkMode, hostRef) {
        if (webDarkMode == BrowserChromePrefs.WEB_DARK_AUTO) {
            hostRef?.applyWebContentDarkMode(BrowserChromePrefs.WEB_DARK_AUTO)
        }
    }
    // SPEC-125: idle omnibox = page title / host; omniboxLabel keeps last typed draft for edit.
    var omniboxLabel by remember {
        mutableStateOf(BrowserOmniboxLabel.resolve(initialOmniboxLabel, initialUrl))
    }
    // SPEC-146 R1: address carries TextFieldValue so focus-gain can select-all.
    var address by remember {
        mutableStateOf(
            TextFieldValue(
                BrowserTabManager.pageDisplayLabel(initialTitle, initialUrl)
                    .ifBlank { omniboxLabel },
            ),
        )
    }
    var addressFocused by remember { mutableStateOf(false) }
    /** SPEC-146 R2: clipboard link candidate shown while the omnibox is focused. */
    var clipboardUrl by remember { mutableStateOf<String?>(null) }
    var appliedNavEpoch by remember(sessionKey) { mutableLongStateOf(0L) }
    /** SPEC-125: URL we already loadUrl'd in onReady; blocks stale lastUrl trust. */
    var pendingForcedUrl by remember(sessionKey) { mutableStateOf<String?>(null) }
    /** SPEC-022 B: phone floating chrome auto-hide on scroll down. */
    var phoneChromeVisible by remember { mutableStateOf(true) }
    /**
     * SPEC-242: toolbox fullscreen — hide chrome; no cross-tab memory
     * (`remember(sessionKey)` resets on tab switch).
     */
    var browserFullscreen by remember(sessionKey) { mutableStateOf(false) }
    var phoneBottomChromeHeightPx by remember { mutableIntStateOf(0) }
    /** SPEC-131: Chromium HTML5 custom fullscreen view. */
    var videoCustomView by remember { mutableStateOf<View?>(null) }
    var videoCustomViewCallback by remember {
        mutableStateOf<BrowserCustomViewCallback?>(null)
    }
    var videoFullscreenHost by remember { mutableStateOf<android.widget.FrameLayout?>(null) }
    var tabMenu by remember { mutableStateOf(false) }
    var readerModeMenu by remember { mutableStateOf(false) }
    // SPEC-163
    var backHistoryMenu by remember { mutableStateOf(false) }
    var forwardHistoryMenu by remember { mutableStateOf(false) }
    var backHistoryItems by remember {
        mutableStateOf<List<io.legado.app.browser.engine.BrowserNavHistory.Item>>(emptyList())
    }
    var forwardHistoryItems by remember {
        mutableStateOf<List<io.legado.app.browser.engine.BrowserNavHistory.Item>>(emptyList())
    }
    var readerAutoOpen by remember {
        mutableStateOf(readerPrefs.triggerMode != BrowserReaderPrefs.TriggerMode.Manual)
    }
    var extremePurge by remember {
        mutableStateOf(readerPrefs.strongContentExtract)
    }
    val openTabCount by BrowserTabManager.tabs.collectAsStateWithLifecycle()
    var pickMode by remember { mutableStateOf(false) }
    /** SPEC-134: selectors waiting for Confirm (multi-select). */
    var pendingSelectors by remember { mutableStateOf(emptySet<String>()) }
    /** SPEC-093: long-press link/image sheet. */
    var linkContextHit by remember { mutableStateOf<BrowserLinkContextHit?>(null) }
    val ntpPrefs = remember { BrowserNtpPrefs(context) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // SPEC-025: keep bottom gap on phone; 8dp also sits above function bar + reader FAB.
    val floatingBottomPad = BrowserUiTokens.FloatingChromeBottomGap + navBottom
    val floatingChromeTopGap = BrowserUiTokens.FloatingChromeTopGap
    // SPEC-103: web function bar + reader FAB are 48dp (main shell bar stays 64).
    val phoneChromeBarHeight = 48.dp
    /** SPEC-254: eligibility for translate entry (no bottom ask bar). */
    var pageNeedsTranslate by remember { mutableStateOf(false) }
    /** SPEC-262: in-page translate currently applied (restore path). */
    var pageTranslated by remember { mutableStateOf(false) }
    /** SPEC-259: current URL bookmarked for toolbox / tablet omnibox. */
    var pageBookmarked by remember { mutableStateOf(false) }
    var bookmarkEpoch by remember { mutableIntStateOf(0) }
    /** SPEC-259: in-page translate progress capsule (same chrome as 净读加载). */
    var translating by remember { mutableStateOf(false) }
    var translateJob by remember { mutableStateOf<Job?>(null) }
    val translatePromptExtra = 0.dp
    val floatingChromeReserve =
        floatingChromeTopGap + phoneChromeBarHeight +
            floatingBottomPad + translatePromptExtra
    // SPEC-130: auto-hide → bottom chrome overlays (inset 0, no show/hide resize jank).
    // Off → keep reserve so content is not covered by the bar.
    val pickBarReserve = floatingChromeTopGap + phoneChromeBarHeight + floatingBottomPad
    // SPEC-169: portrait = 1.7.0 top+bottom; landscape = SPEC-168 single top bar.
    val phoneSplitChrome = !tabletEmbedded &&
        TabletBrowserLayout.phoneUsesSplitWebChrome(
            portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT,
        )
    val phoneWebIconSlot = TabletBrowserLayout.PhoneWebIconSlotDp.dp
    val phoneWebFieldHeight = TabletBrowserLayout.PhoneWebOmniboxHeightDp.dp
    val phoneWebGap = TabletBrowserLayout.ChromeItemGapDp.dp
    val webBottomInset = when {
        // SPEC-134: pick bar always reserves space (phone + tablet).
        pickMode -> pickBarReserve
        // SPEC-243: toolbox fullscreen must not leave bottom chrome blank inset.
        browserFullscreen -> 0.dp
        phoneSplitChrome -> if (chromePrefs.autoHideTopBar) {
            0.dp
        } else {
            floatingChromeReserve
        }
        // SPEC-168: landscape phone chrome is the top bar. Keep only the gesture inset.
        !tabletEmbedded -> navBottom
        else -> 0.dp
    }

    fun applyOmniboxLabel(label: String?) {
        val text = label?.trim().orEmpty()
        omniboxLabel = text
        BrowserTabManager.updateActiveOmniboxLabel(text)
    }

    /** SPEC-125: idle address = title / site host. */
    fun syncIdleAddress(title: String?, url: String?) {
        if (addressFocused) return
        val next = BrowserTabManager.pageDisplayLabel(title, url)
            .ifBlank { omniboxLabel }
        if (next.isNotEmpty() && address.text != next) address = TextFieldValue(next)
    }

    fun syncOmniboxLabelFromTab(url: String?) {
        syncIdleAddress(clientRef?.lastTitle, url)
    }
    var showToolbox by remember { mutableStateOf(false) }
    var adBlockEnabled by remember { mutableStateOf(shield.adBlockEnabled) }
    var extracting by remember { mutableStateOf(false) }
    var extractJob by remember { mutableStateOf<Job?>(null) }
    var readerStatus by remember { mutableStateOf<String?>(null) }
    var showModePicker by remember { mutableStateOf(false) }
    var pendingAnalyze by remember { mutableStateOf<WebMangaBookFactory.AnalyzeResult?>(null) }
    var autoOpenedForUrl by remember { mutableStateOf<String?>(null) }
    /** SPEC-254: remember last URL we evaluated for translate eligibility. */
    var translateDetectUrl by remember { mutableStateOf<String?>(null) }
    var langDetectJob by remember { mutableStateOf<Job?>(null) }
    /** SPEC-066: transient download banner above bottom chrome (~5s). */
    var downloadBanner by remember { mutableStateOf<String?>(null) }
    var downloadBannerToken by remember { mutableIntStateOf(0) }
    /** SPEC-132: hide prompt for this stream URL until a new offer arrives. */
    var dismissedVideoPromptUrl by remember { mutableStateOf<String?>(null) }
    val videoOffer by BrowserVideoOfferBus.offer.collectAsStateWithLifecycle()
    // SPEC-145 R6: 页面检测到多个视频时，胶囊展示完整列表。
    val playlistOffers by BrowserVideoOfferBus.offers.collectAsStateWithLifecycle()
    // SPEC-135: only show offer that still belongs to the current page.
    val currentPageForVideo = clientRef?.lastUrl.orEmpty().ifBlank { address.text }
    val showVideoPlayerPrompt = chromePrefs.videoPlayerPromptEnabled &&
        videoOffer != null &&
        videoOffer?.url != dismissedVideoPromptUrl &&
        BrowserVideoOfferBus.belongsToPage(videoOffer, currentPageForVideo)
    // SPEC-217/224: 偏好悬浮球则直接球；否则完整胶囊，8s 未交互收球；展开后不自动再收。
    val videoCapsuleOfferUrl = videoOffer?.url
    val preferVideoBall = chromePrefs.videoFloatingBallPreferred
    var videoCapsuleCollapsed by remember(videoCapsuleOfferUrl, preferVideoBall) {
        mutableStateOf(preferVideoBall)
    }
    var videoCapsuleInteracted by remember(videoCapsuleOfferUrl) { mutableStateOf(false) }
    var videoCapsuleExpandedByUser by remember(videoCapsuleOfferUrl) { mutableStateOf(false) }
    LaunchedEffect(videoCapsuleOfferUrl, showVideoPlayerPrompt, preferVideoBall) {
        if (!showVideoPlayerPrompt || videoCapsuleOfferUrl.isNullOrBlank()) return@LaunchedEffect
        videoCapsuleInteracted = false
        videoCapsuleExpandedByUser = false
        if (preferVideoBall) {
            videoCapsuleCollapsed = true
            return@LaunchedEffect
        }
        videoCapsuleCollapsed = false
        delay(8_000)
        if (!videoCapsuleInteracted && !videoCapsuleExpandedByUser) {
            videoCapsuleCollapsed = true
        }
    }
    /** SPEC-094: short toast above bottom bar (24dp), not Material Snackbar over chrome. */
    var chromeSnack by remember { mutableStateOf<String?>(null) }
    var chromeSnackToken by remember { mutableIntStateOf(0) }
    /** SPEC-095: https URL armed for one-shot HTTP fallback after auto-upgrade failure. */
    var armedHttpsFallback by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val scheme = LegadoTheme.colorScheme
    val chromeBackdrop = rememberLayerBackdrop {
        drawRect(scheme.surfaceContainer)
    }
    val resolvedSessionKey = sessionKey ?: "legacy|$incognito|$initialUrl"
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            CimocJsonRuleStore.ensureLoaded(context)
            CimocJsonRuleStore.refreshFromNetwork(context)
        }
    }

    fun showSnack(msg: String) {
        chromeSnack = msg
        chromeSnackToken += 1
    }

    fun armHttpsFallback(rawInput: String?, resolvedUrl: String) {
        armedHttpsFallback = if (
            BrowserHttpsFallback.wasAutoHttpsUpgrade(
                rawInput,
                resolvedUrl,
                ntpPrefs.searchEngine(),
            )
        ) {
            resolvedUrl
        } else {
            null
        }
    }

    /** SPEC-095: one-shot http:// retry after auto-upgraded https:// connection failure. */
    fun tryHttpsFallback(failedUrl: String, netError: Int): Boolean {
        val armed = armedHttpsFallback ?: return false
        if (!BrowserHttpsFallback.isConnectionLikeError(netError)) return false
        val failed = failedUrl.ifBlank { armed }
        if (!failed.startsWith("https://", ignoreCase = true)) return false
        val armedHost = Uri.parse(armed).host ?: return false
        val failedHost = Uri.parse(failed).host ?: return false
        if (!armedHost.equals(failedHost, ignoreCase = true)) return false
        val http = BrowserHttpsFallback.httpFallbackUrl(failed) ?: return false
        armedHttpsFallback = null
        clientRef?.lastError = null
        hostRef?.loadUrl(http)
        BrowserSessionHolder.markLoaded()
        onPageMeta?.invoke(http, omniboxLabel.ifBlank { failedHost })
        return true
    }

    fun setReaderStatus(msg: String?) {
        readerStatus = msg
    }

    fun cancelExtract() {
        extractJob?.cancel()
        extractJob = null
        extracting = false
        readerStatus = null
    }

    fun currentHttpUrl(): String? {
        val fromClient = clientRef?.lastUrl?.takeIf { it.startsWith("http") }
        if (!fromClient.isNullOrBlank()) return fromClient
        return address.text.takeIf { it.startsWith("http") }
    }

    fun refreshPageBookmarked(url: String? = currentHttpUrl()) {
        pageBookmarked = !url.isNullOrBlank() && store.isBookmarked(url)
    }

    fun toggleBookmark() {
        val url = currentHttpUrl()
        if (url.isNullOrBlank()) {
            context.toastOnUi("当前没有可收藏的链接")
            return
        }
        if (store.isBookmarked(url)) {
            store.removeBookmark(url)
            context.toastOnUi("已移出收藏")
        } else {
            store.addBookmark(url, clientRef?.lastTitle.orEmpty())
            context.toastOnUi(context.getString(R.string.browser_favorite_added))
        }
        bookmarkEpoch += 1
        refreshPageBookmarked(url)
    }

    fun cancelTranslate() {
        translateJob?.cancel()
        translateJob = null
        translating = false
        val host = hostRef
        if (host != null) {
            scope.launch {
                runCatching {
                    host.evalJs(io.legado.app.browser.reader.WebPageInPlaceTranslateJs.RESTORE)
                }
            }
        }
    }

    fun startExtract(block: suspend () -> Unit) {
        if (extracting) return
        extractJob?.cancel()
        extractJob = scope.launch {
            extracting = true
            readerStatus = null
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                context.toastOnUi(t.message?.takeIf { it.isNotBlank() } ?: "打开净读失败")
            } finally {
                extracting = false
            }
        }
    }

    suspend fun discoverToc(host: AwContentsHostView): WebChapterHttpFetcher.TocResult? {
        val raw = host.evalJs(WebTocDiscoverJs.SCRIPT)
        return WebChapterHttpFetcher.parseToc(raw)
    }

    /** SPEC-017: lightweight collect without stepwise scroll loops. */
    suspend fun collectMangaLight(
        host: AwContentsHostView,
        analyze: WebMangaBookFactory.AnalyzeResult,
    ): Pair<List<WebMangaImageFilter.Candidate>, String> {
        val expand = WebChapterHttpFetcher.parseExpand(host.evalJs(WebChapterExpandJs.COLLECT))
        val merged = LinkedHashMap<String, WebMangaImageFilter.Candidate>()
        analyze.candidates.forEach { merged[it.url] = it }
        analyze.images.forEach { u ->
            merged.putIfAbsent(u, WebMangaImageFilter.Candidate(url = u))
        }
        expand?.images?.forEach { merged[it.url] = it }
        val title = expand?.title.orEmpty().ifBlank { analyze.title }
        return merged.values.toList() to title
    }

    /**
     * SPEC-123: poll until article images exist, the article signature stays still
     * for [WebArticleMixedHydration.SETTLE_MS], or the timeout elapses.
     */
    suspend fun awaitMixedArticleImages(host: AwContentsHostView) {
        val start = SystemClock.elapsedRealtime()
        var previousSignature: String? = null
        var stableHits = 0
        while (true) {
            val probe = WebArticleMixedHydration.parseProbe(
                host.evalJs(WebArticleMixedHydration.PROBE_SCRIPT),
            )
            val elapsed = SystemClock.elapsedRealtime() - start
            stableHits = if (previousSignature != null && previousSignature == probe.signature) {
                stableHits + 1
            } else {
                0
            }
            previousSignature = probe.signature
            if (WebArticleMixedHydration.readyToExtract(elapsed, probe.imageCount, stableHits)) {
                return
            }
            delay(WebArticleMixedHydration.POLL_MS)
        }
    }


    /**
     * SPEC-149/210: 会话重挂/遮罩时序下 hostRef 可能为空；回退 Holder 活跃宿主并回写自愈。
     * SPEC-210: key 失配时再试任意活跃宿主，避免退出净读后再进误报「不可用」。
     */
    fun resolveReaderHost(): AwContentsHostView? {
        hostRef?.let { return it }
        fun adopt(key: String?): AwContentsHostView? {
            val host = BrowserSessionHolder.activeHostFor(key) ?: return null
            val client = BrowserSessionHolder.activeClientFor(key) ?: return null
            clientRef = client
            hostRef = host
            return host
        }
        adopt(resolvedSessionKey)?.let {
            Log.i("Diag149", "reader host fallback via holder, key=$resolvedSessionKey")
            return it
        }
        adopt(null)?.let {
            Log.i("Diag210", "reader host fallback via holder any-active (key mismatch)")
            return it
        }
        return null
    }

    suspend fun openNovelReader(
        pageUrl: String,
        titleHint: String? = null,
        forceTranslate: Boolean? = null,
        /** SPEC-109: false = text-only (strip images); true = mixed HTML keeps in-body images. */
        keepImages: Boolean = true,
    ) {
        val host = resolveReaderHost()
        val open = onOpenReader
        if (host == null || open == null) {
            context.toastOnUi("净读视图不可用")
            return
        }
        // SPEC-123: Tencent Next.js articles often have no body <img> until hydration.
        if (keepImages && WebArticleMixedHydration.needsWait(pageUrl)) {
            awaitMixedArticleImages(host)
        }
        // SPEC-217: optional strong extract (prefs default off).
        val partRaw = host.evalJs(
            WebArticleExtractJs.script(extremePurge),
        )
        val extracted = WebArticleBookFactory.parseExtractResult(partRaw)
        val toc = discoverToc(host)
        val rawText = extracted?.text.orEmpty()
        // SPEC-057/109: mixed keeps <img>; text-only strips images first.
        val cleanedRaw = when {
            !rawText.contains('<') -> WebArticleChromeFilter.filter(rawText)
            keepImages -> WebArticleChromeFilter.filterHtml(rawText)
            else -> WebArticleChromeFilter.filterHtmlTextOnly(rawText)
        }
        // SPEC-212/213: 有归属可播 offer 时升原海报为 jingshi（不按 offer 数强行补张，防灰假图）。
        val pageOffers = BrowserVideoOfferBus.offers.value
            .filter { BrowserVideoOfferBus.belongsToPage(it, pageUrl) }
        val cleaned = if (pageOffers.isNotEmpty()) {
            WebArticleVideoThumb.ensureThumb(
                cleanedRaw,
                extracted?.coverUrl,
                minThumbs = 1,
            )
        } else {
            WebArticleVideoThumb.stripThumbs(cleanedRaw)
        }
        if (cleaned.length < 40) {
            context.toastOnUi("未能提取正文，请换一个页面再试")
            return
        }
        val title = titleHint?.takeIf { it.isNotBlank() }
            ?: extracted?.title.orEmpty().ifBlank { "网页正文" }
        val series = toc?.seriesUrl?.takeIf { it.isNotBlank() && toc.chapters.size >= 2 }
            ?: pageUrl
        // SPEC-251: 净读自动翻译以翻译设置为准（默认关）。
        val enableTranslate = forceTranslate
            ?: GlobalContext.get().get<TranslationSettingsGateway>().currentSettings.autoTranslateJingdu
        val book = withContext(Dispatchers.IO) {
            val created = WebArticleBookFactory.createEphemeralBook(
                context,
                pageUrl,
                WebArticleBookFactory.Extracted(
                    title = title,
                    text = cleaned,
                    coverUrl = extracted?.coverUrl,
                ),
                seriesKey = series,
                tocChapters = toc?.chapters.orEmpty(),
            )
            // SPEC-042/045: chapter translation when opening from browser.
            if (enableTranslate) {
                created.setTranslationMode(true)
                created.save()
            }
            created
        }
        open(book.bookUrl)
    }

    /** SPEC-251/259: Chrome-like in-page translate / restore (capsule, cancellable). */
    fun runInPageTranslate(forceTranslate: Boolean = false) {
        val host = hostRef
        if (host == null) {
            context.toastOnUi("页面未就绪")
            return
        }
        val pageUrl = clientRef?.lastUrl.orEmpty().ifBlank { address.text }
        if (!pageUrl.startsWith("http")) {
            context.toastOnUi("无法翻译当前页")
            return
        }
        if (WebPageTranslate.isTranslateServiceUrl(pageUrl)) {
            context.toastOnUi("当前已是翻译服务页")
            return
        }
        if (translating) {
            cancelTranslate()
            return
        }
        translateJob?.cancel()
        translateJob = scope.launch {
            translating = true
            try {
                val result = if (forceTranslate) {
                    WebPageInPlaceTranslator.translate(host) { }
                } else {
                    WebPageInPlaceTranslator.toggle(host) { }
                }
                // SPEC-262: keep omnibox/toolbox restore entry after translate.
                pageTranslated = result.translated
                context.toastOnUi(result.message)
            } catch (e: CancellationException) {
                throw e
            } finally {
                translating = false
                translateJob = null
            }
        }
    }

    fun openTranslationSettings() {
        context.startActivity(
            io.legado.app.ui.main.MainIntent.createSettingsIntent(
                context,
                io.legado.app.ui.main.MainRouteConst.ROUTE_SETTINGS_TRANSLATION,
            ),
        )
    }

    /** SPEC-254: detect need-to-translate; never show bottom ask bar. */
    fun maybeDetectTranslateNeed(url: String) {
        if (!WebPageTranslate.WEB_PAGE_TRANSLATE_UI_ENABLED) {
            pageNeedsTranslate = false
            return
        }
        if (incognito) {
            pageNeedsTranslate = false
            return
        }
        if (!url.startsWith("http")) {
            pageNeedsTranslate = false
            return
        }
        if (WebPageTranslate.isTranslateServiceUrl(url)) {
            pageNeedsTranslate = false
            return
        }
        langDetectJob?.cancel()
        langDetectJob = scope.launch {
            delay(600)
            val host = hostRef ?: return@launch
            val current = clientRef?.lastUrl.orEmpty().ifBlank { address.text }
            if (current != url) return@launch
            val raw = host.evalJs(WebPageLangDetectJs.SCRIPT)
            val sample = WebPageLangDetect.parse(raw) ?: return@launch
            val needs = WebPageLangDetect.shouldPrompt(sample.lang, sample.sample)
            pageNeedsTranslate = needs
            translateDetectUrl = url
            if (!needs) return@launch
            val auto = GlobalContext.get().get<TranslationSettingsGateway>()
                .currentSettings.autoTranslateWebpage
            if (auto) {
                runInPageTranslate(forceTranslate = true)
            }
        }
    }

    suspend fun openMangaReader(pageUrl: String, analyze: WebMangaBookFactory.AnalyzeResult) {
        val open = onOpenMangaReader
        val host = resolveReaderHost()
        if (open == null || host == null) {
            context.toastOnUi("漫画净读不可用")
            return
        }
        withContext(Dispatchers.IO) {
            CimocJsonRuleStore.ensureLoaded(context)
        }
        val cimocMatch = withContext(Dispatchers.IO) {
            CimocAdapterRegistry.match(pageUrl)
        }
        if (cimocMatch != null) {
            val book = withContext(Dispatchers.IO) {
                runCatching { CimocMangaBridge.loadBook(context, pageUrl, cimocMatch) }.getOrNull()
            }
            if (book != null) {
                open(book.bookUrl)
                return
            }
        }
        val jsonRule = withContext(Dispatchers.IO) {
            CimocJsonRuleStore.match(pageUrl)?.takeIf { it.isRich }
        }
        if (jsonRule != null) {
            val book = withContext(Dispatchers.IO) {
                runCatching { CimocJsonMangaBridge.loadBook(context, pageUrl, jsonRule) }.getOrNull()
            }
            if (book != null) {
                open(book.bookUrl)
                return
            }
        }
        val (candidates, expandTitle) = collectMangaLight(host, analyze)
        val toc = discoverToc(host)
        val images = WebMangaImageFilter.filter(candidates).map { it.url }
        if (images.isEmpty()) {
            context.toastOnUi("未能提取漫画图片，请换一个页面再试")
            return
        }
        val title = expandTitle.ifBlank { analyze.title }
        val series = toc?.seriesUrl?.takeIf { it.isNotBlank() && toc.chapters.size >= 2 }
            ?: pageUrl
        val book = withContext(Dispatchers.IO) {
            WebMangaBookFactory.createEphemeralMangaBook(
                context,
                pageUrl,
                title,
                images,
                candidates = candidates,
                seriesKey = series,
                tocChapters = toc?.chapters.orEmpty(),
            )
        }
        open(book.bookUrl)
    }

    fun openReaderView(forceKind: WebMangaBookFactory.Kind? = null) {
        val host = resolveReaderHost()
        if (host == null) {
            context.toastOnUi("净读视图不可用")
            return
        }
        startExtract {
            val raw = suspendCancellableCoroutine { cont ->
                host.evaluateJavaScript(
                    WebPageAnalyzeJs.SCRIPT,
                    BrowserJsCallback { result ->
                        if (cont.isActive) cont.resume(result)
                    },
                )
            }
            val analyze = WebMangaBookFactory.parseAnalyzeResult(raw)
            val pageUrl = clientRef?.lastUrl.orEmpty().ifBlank { address.text }
            if (analyze == null) {
                context.toastOnUi("页面分析失败，请重试")
                return@startExtract
            }
            // SPEC-046: with real body text, always prefer mixed novel reader
            // unless the user explicitly forced manga.
            val kind = when {
                forceKind != null -> forceKind
                analyze.textLen >= 200 -> WebMangaBookFactory.Kind.Novel
                else -> analyze.kind
            }
            when (kind) {
                WebMangaBookFactory.Kind.Manga -> openMangaReader(pageUrl, analyze)
                WebMangaBookFactory.Kind.Novel -> openNovelReader(pageUrl, analyze.title)
                WebMangaBookFactory.Kind.Unknown -> {
                    pendingAnalyze = analyze
                    showModePicker = true
                }
            }
        }
    }

    fun maybeAutoOpenReader(url: String) {
        if (incognito) return
        if (extracting) return
        if (url == autoOpenedForUrl) return
        val mode = readerPrefs.triggerMode
        if (mode == BrowserReaderPrefs.TriggerMode.Manual) return
        if (mode == BrowserReaderPrefs.TriggerMode.SiteAuto &&
            !BrowserReaderPrefs.looksLikeReaderSite(url)
        ) {
            return
        }
        autoOpenedForUrl = url
        // Slight delay so DOM settles.
        scope.launch {
            delay(700)
            if (!extracting && clientRef?.lastUrl.orEmpty().ifBlank { address.text } == url) {
                openReaderView()
            }
        }
    }

    fun applyCustomHide(url: String) {
        val host = BrowserAdRules.hostOf(url) ?: return
        val rules = shield.rulesForHost(host)
        hostRef?.evaluateJavaScript(BrowserShieldJs.hideSelectorsScript(rules), null)
    }

    /** SPEC-134 */
    fun enterPickMode() {
        pickMode = true
        phoneChromeVisible = true
        hostRef?.evaluateJavaScript(BrowserShieldJs.startPickModeScript(), null)
        context.toastOnUi("点选模式：可多选元素，点确认保存")
    }

    fun exitPickMode() {
        pickMode = false
        pendingSelectors = emptySet()
        hostRef?.evaluateJavaScript(BrowserShieldJs.stopPickModeScript(), null)
    }

    fun confirmPickMode() {
        if (pendingSelectors.isEmpty()) {
            exitPickMode()
            return
        }
        val hostName = BrowserAdRules.hostOf(clientRef?.lastUrl ?: address.text).orEmpty()
        if (hostName.isNotEmpty()) {
            shield.addRules(hostName, pendingSelectors)
            applyCustomHide(clientRef?.lastUrl ?: address.text)
            context.toastOnUi("已保存 ${pendingSelectors.size} 条屏蔽规则")
        }
        exitPickMode()
    }

    fun openPickBlockSettings() {
        // Keep pickMode (suspend); return continues picking.
        MainRouteRequestBus.request(MainRouteBrowserPickBlock)
    }

    /** SPEC-107: per-host hide + built-in cosmetic popup filters. */
    fun applyAdShield(url: String) {
        if (!shield.adBlockEnabled) return
        applyCustomHide(url)
        hostRef?.evaluateJavaScript(BrowserShieldJs.cosmeticPopupScript(), null)
    }

    fun shareCurrentPage() {
        val url = clientRef?.lastUrl.orEmpty().ifBlank { address.text }.trim()
        if (url.isEmpty() || !url.startsWith("http")) {
            context.toastOnUi("当前没有可转发的链接")
            return
        }
        val title = clientRef?.lastTitle.orEmpty().ifBlank { url }
        context.share("$title\n$url", title = "转发网页")
    }

    fun showDownloadBanner(fileName: String) {
        downloadBanner = fileName.ifBlank { "正在下载…" }
        downloadBannerToken += 1
        if (!tabletEmbedded) phoneChromeVisible = true
    }

    fun saveCurrentPage() {
        val host = hostRef
        if (host == null) {
            context.toastOnUi("页面未就绪")
            return
        }
        val url = clientRef?.lastUrl.orEmpty().ifBlank { address.text }.trim()
        val title = clientRef?.lastTitle.orEmpty().ifBlank { url }
        scope.launch {
            val raw = host.evalJs("(function(){try{return document.documentElement.outerHTML;}catch(e){return '';}})()")
            val html = raw
                ?.let { StringEscapeUtils.unescapeJson(it).trim().trim('"') }
                .orEmpty()
            val result = BrowserDownloadRepository.get(context).saveCurrentPage(
                html = html,
                pageUrl = url,
                pageTitle = title,
            )
            context.toastOnUi(
                result.fold(
                    onSuccess = { "已保存网页 ${it.fileName}" },
                    onFailure = { it.message ?: "保存失败" },
                ),
            )
        }
    }

    fun enqueueTriggeredDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
    ) {
        fun start() {
            val result = BrowserDownloadHelper.enqueue(
                context = context,
                url = url,
                userAgent = userAgent.ifBlank { BrowserUa.resolve(context) },
                contentDisposition = contentDisposition,
                mimeType = mimeType,
                incognito = incognito,
            )
            if (result.ok) {
                showDownloadBanner(result.fileName.ifBlank { "文件" })
            } else {
                showSnack(result.message)
            }
        }
        // SPEC-119: optional ask-before-download.
        if (chromePrefs.askBeforeDownload) {
            val name = BrowserDownloadHelper.guessFileName(url, contentDisposition, mimeType)
            android.app.AlertDialog.Builder(context)
                .setTitle("确认下载")
                .setMessage("下载「$name」到\n${BrowserDownloadPaths.rootDir(context).absolutePath}？")
                .setPositiveButton("下载") { _, _ -> start() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            start()
        }
    }

    LaunchedEffect(downloadBannerToken, downloadBanner) {
        if (downloadBanner == null) return@LaunchedEffect
        delay(5_000)
        downloadBanner = null
    }

    LaunchedEffect(chromeSnackToken, chromeSnack) {
        if (chromeSnack == null) return@LaunchedEffect
        delay(3_500)
        chromeSnack = null
    }

    // SPEC-188: bind JS evaluator for in-page media notification controls.
    DisposableEffect(hostRef) {
        val host = hostRef
        val evaluator = host?.let { h ->
            BrowserWebMediaBridge.JsEvaluator { script, callback ->
                h.evaluateJavaScript(script) { result -> callback?.invoke(result) }
            }.also { BrowserWebMediaBridge.attachEvaluator(it) }
        }
        onDispose {
            evaluator?.let { BrowserWebMediaBridge.detachEvaluator(it) }
        }
    }
    LaunchedEffect(hostRef) {
        val host = hostRef ?: return@LaunchedEffect
        BrowserOpenBridge.armedLocalHtml.collect { armed ->
            if (armed == null) return@collect
            BrowserOpenBridge.consumeArmedLocalHtml()
            val file = File(armed.path)
            val html = runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
            if (html.isNullOrBlank()) {
                context.toastOnUi("无法打开已保存网页")
                return@collect
            }
            // SPEC-066: Force Dark can blank offline snapshots — turn off for this load.
            host.applyForceDarkOff()
            host.loadDataWithBaseURL(
                armed.baseUrl,
                html,
                "text/html",
                "UTF-8",
                armed.baseUrl,
            )
            applyOmniboxLabel(armed.title.ifBlank { "已保存网页" })
            address = TextFieldValue(armed.title.ifBlank { "已保存网页" })
        }
    }

    fun openSuggestItem(item: BrowserOmniboxSuggest.Item) {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        addressFocused = false
        applyOmniboxLabel(item.title.ifBlank { item.url })
        address = TextFieldValue(item.title.ifBlank { item.url })
        hostRef?.loadUrl(item.url)
        BrowserSessionHolder.markLoaded()
        onPageMeta?.invoke(item.url, item.title)
    }

    fun navigateToAddress() {
        // SPEC-022: same search/host rules as NTP — never blind https:// (avoids xn-- junk).
        val raw = address.text.trim()
        val url = resolveBrowserNavigationInput(raw, ntpPrefs.searchEngine())
        if (url.isEmpty()) return
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        applyOmniboxLabel(raw)
        addressFocused = false
        clientRef?.lastError = null
        armHttpsFallback(raw, url)
        hostRef?.loadUrl(url)
        BrowserSessionHolder.markLoaded()
        // SPEC-125: show host until real page title arrives.
        syncIdleAddress(null, url)
        onPageMeta?.invoke(url, BrowserTabManager.pageDisplayLabel(null, url, raw))
    }

    /** SPEC-121: parse find script JSON `{n,i}` into match label. */
    fun runFindInPage(query: String, findNext: Boolean, forward: Boolean) {
        hostRef?.findInPage(query, findNext = findNext, forward = forward) { raw ->
            if (query.isBlank()) {
                findMatchLabel = ""
                return@findInPage
            }
            val payload = raw?.trim().orEmpty()
            val n: Int
            val i: Int
            if (payload.startsWith("{")) {
                n = Regex(""""n"\s*:\s*(\d+)""").find(payload)?.groupValues?.getOrNull(1)
                    ?.toIntOrNull() ?: 0
                i = Regex(""""i"\s*:\s*(\d+)""").find(payload)?.groupValues?.getOrNull(1)
                    ?.toIntOrNull() ?: 0
            } else {
                n = payload.toIntOrNull() ?: 0
                i = if (n > 0) 1 else 0
            }
            findMatchLabel = when {
                n <= 0 -> "无匹配"
                i > 0 -> "$i/$n"
                else -> "$n"
            }
        }
    }

    val addressSuggestions = remember(address, addressFocused) {
        if (!addressFocused || address.text.isBlank()) {
            emptyList()
        } else {
            BrowserOmniboxSuggest.match(address.text, store.bookmarks(), store.history())
        }
    }

    fun loadExternalUrl(raw: String) {
        val typed = raw.trim()
        val url = resolveBrowserNavigationInput(typed, ntpPrefs.searchEngine())
        if (url.isEmpty()) return
        applyOmniboxLabel(typed)
        clientRef?.lastError = null
        armHttpsFallback(typed, url)
        hostRef?.loadUrl(url)
        BrowserSessionHolder.markLoaded()
        syncIdleAddress(null, url)
        onPageMeta?.invoke(url, BrowserTabManager.pageDisplayLabel(null, url, typed))
    }

    /** SPEC-146 R2: open the clipboard link candidate straight away. */
    fun openClipboardUrl(url: String) {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        addressFocused = false
        clipboardUrl = null
        loadExternalUrl(url)
    }

    fun handleSpecialUrl(url: String): Boolean {
        val uri = Uri.parse(url)
        return when {
            // 外链由 BrowserAwContentsClient（hasUserGesture + onExternalAppOpen）处理。
            BrowserUrlPolicy.isSameAppImport(url) -> {
                val importUrl = uri.getQueryParameter("src")
                val isBookSourceImport = uri.path == "/bookSource" ||
                    (uri.path == "/importonline" && uri.host.equals("booksource", true))
                if (isBookSourceImport && !importUrl.isNullOrBlank() && onImportBookSource != null) {
                    onImportBookSource(importUrl)
                } else {
                    context.startActivity(
                        Intent(context, OnLineImportActivity::class.java).setData(uri),
                    )
                }
                true
            }
            else -> false
        }
    }

    /** SPEC-156: 有可处理应用才弹 Chooser；否则不弹空板。 */
    fun openExternalAppChooser(url: String) {
        runCatching {
            val intent = if (url.startsWith("intent:", ignoreCase = true)) {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolved = context.packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY,
            )
            if (resolved.isEmpty()) {
                showSnack("没有可打开此链接的应用")
                return
            }
            context.startActivity(
                Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            showSnack("无法打开此链接")
        }
    }

    fun clearVideoCustomViewUi() {
        val view = videoCustomView
        val host = videoFullscreenHost
        videoCustomView = null
        videoCustomViewCallback = null
        videoFullscreenHost = null
        (view?.parent as? ViewGroup)?.removeView(view)
        if (host != null) {
            (host.parent as? ViewGroup)?.removeView(host)
        }
        context.findActivityOrNull()?.let { act ->
            act.window?.clearFlags(
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
            )
            act.keepScreenOn(false)
            act.toggleSystemBar(true)
        }
    }

    /** SPEC-131: user/system exit — notify Chromium, then clear UI. */
    fun requestExitVideoFullscreen() {
        val callback = videoCustomViewCallback
        if (callback != null) {
            runCatching { callback.onCustomViewHidden() }
        }
        clearVideoCustomViewUi()
    }

    DisposableEffect(sessionKey) {
        onDispose {
            val callback = videoCustomViewCallback
            val view = videoCustomView
            val host = videoFullscreenHost
            if (callback != null || view != null || host != null) {
                runCatching { callback?.onCustomViewHidden() }
                (view?.parent as? ViewGroup)?.removeView(view)
                if (host != null) (host.parent as? ViewGroup)?.removeView(host)
                context.findActivityOrNull()?.let { act ->
                    act.keepScreenOn(false)
                    act.toggleSystemBar(true)
                }
            }
        }
    }

    // SPEC-139: while a nav overlay (history/bookmarks/tabs) is on top of Home,
    // nav3 caps this scene's lifecycle to STARTED — gate the handler on RESUMED
    // so the underlay session cannot steal the system back gesture (which used
    // to invisibly webView.goBack() and finally clearActiveToNtp → NTP).
    val sessionLifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    fun navigateBackOrHome() {
        val host = hostRef
        if (host?.canGoBack() == true) {
            host.goBack()
        } else if (showNavigationBack) {
            onNavigateBack()
        }
    }

    BackHandler(enabled = sessionLifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
        when {
            videoCustomView != null -> requestExitVideoFullscreen()
            pickMode -> {
                exitPickMode()
                context.toastOnUi("已取消点选屏蔽")
            }
            showToolbox -> showToolbox = false
            browserFullscreen -> {
                // SPEC-244: restore chrome immediately (no blank inset until scroll).
                browserFullscreen = false
                phoneChromeVisible = true
            }
            readerModeMenu -> readerModeMenu = false
            tabMenu -> tabMenu = false
            showModePicker -> showModePicker = false
            else -> navigateBackOrHome()
        }
    }

    LaunchedEffect(initialOmniboxLabel, initialUrl, initialTitle) {
        val draft = BrowserOmniboxLabel.resolve(initialOmniboxLabel, initialUrl)
        if (draft.isNotEmpty() && draft != omniboxLabel) {
            omniboxLabel = draft
        }
        syncIdleAddress(initialTitle ?: clientRef?.lastTitle, initialUrl)
    }

    // SPEC-021/125: tab URL / navEpoch change → load.
    // Explicit openUrlInActive bumps navEpoch (incl. after NTP park) and must load.
    // Same epoch: if live host is ahead (in-page nav) and not in error, trust lastUrl.
    LaunchedEffect(initialUrl, hostRef, navEpoch) {
        val host = hostRef ?: return@LaunchedEffect
        if (BrowserSessionHolder.sessionKey != resolvedSessionKey) return@LaunchedEffect
        if (!BrowserSessionHolder.hasLoadedInitialUrl) return@LaunchedEffect
        val last = clientRef?.lastUrl.orEmpty()
        val hadError = clientRef?.lastError != null
        if (initialUrl.isBlank()) return@LaunchedEffect
        if (navEpoch > appliedNavEpoch) {
            appliedNavEpoch = navEpoch
            if (initialUrl != last) {
                syncIdleAddress(initialTitle, initialUrl)
                clientRef?.lastError = null
                pendingForcedUrl = initialUrl
                host.loadUrl(initialUrl)
            } else {
                pendingForcedUrl = null
                syncIdleAddress(clientRef?.lastTitle ?: initialTitle, last)
            }
            return@LaunchedEffect
        }
        if (pendingForcedUrl != null && initialUrl == pendingForcedUrl) {
            // onReady / prior epoch already navigating here; wait for page callbacks.
            syncIdleAddress(initialTitle, initialUrl)
            return@LaunchedEffect
        }
        if (initialUrl == last) {
            pendingForcedUrl = null
            syncIdleAddress(clientRef?.lastTitle ?: initialTitle, last)
            return@LaunchedEffect
        }
        if (last.isNotBlank() && last != initialUrl && !hadError) {
            // In-page navigation already moved lastUrl; do not reload stale search/tab draft.
            syncIdleAddress(clientRef?.lastTitle, last)
            return@LaunchedEffect
        }
        syncIdleAddress(initialTitle, initialUrl)
        clientRef?.lastError = null
        pendingForcedUrl = initialUrl
        host.loadUrl(initialUrl)
    }

    // SPEC-254/259/262: rebind when translate eligibility/active or bookmark flips.
    LaunchedEffect(hostRef, onBindActions, pageNeedsTranslate, pageTranslated, pageBookmarked) {
        onBindActions?.invoke(
            BrowserSessionActions(
                goBack = { navigateBackOrHome() },
                goForward = { hostRef?.goForward() },
                reload = { hostRef?.reload() },
                backHistoryItems = {
                    hostRef?.navigationHistoryItems(
                        io.legado.app.browser.engine.BrowserNavHistory.Direction.Back,
                    ).orEmpty()
                },
                forwardHistoryItems = {
                    hostRef?.navigationHistoryItems(
                        io.legado.app.browser.engine.BrowserNavHistory.Direction.Forward,
                    ).orEmpty()
                },
                goHistoryOffset = { steps -> hostRef?.goBackOrForward(steps) },
                scrollToTop = { hostRef?.scrollToTop() },
                openReader = { openReaderView() },
                loadUrl = { loadExternalUrl(it) },
                currentPageUrl = {
                    clientRef?.lastUrl.orEmpty().ifBlank { address.text }
                },
                currentPageTitle = { clientRef?.lastTitle.orEmpty() },
                addBookmark = { toggleBookmark() },
                copyCurrentUrl = {
                    val currentUrl = clientRef?.lastUrl.orEmpty().ifBlank { address.text }
                    context.sendToClip(currentUrl)
                },
                startPickMode = { enterPickMode() },
                shareCurrentPage = { shareCurrentPage() },
                saveCurrentPage = { saveCurrentPage() },
                openDownloads = {
                    val open = onOpenDownloads
                    if (open != null) open() else showSnack("下载页不可用")
                },
                applyWebContentDarkMode = { hostRef?.applyWebContentDarkMode(it) },
                applyTextZoom = { hostRef?.applyTextZoom(it) },
                injectAdShield = {
                    applyAdShield(clientRef?.lastUrl.orEmpty().ifBlank { address.text })
                },
                applyUserAgentAndReload = { mode ->
                    hostRef?.applyUserAgent(mode)
                    hostRef?.reload()
                },
                openFindInPage = {
                    showFindInPage = true
                    findQuery = ""
                    findMatchLabel = ""
                },
                videoCapsuleAvailable = { showVideoPlayerPrompt },
                openVideoCapsule = {
                    val offer = videoOffer
                    if (offer != null &&
                        BrowserVideoOfferBus.belongsToPage(offer, currentPageForVideo)
                    ) {
                        dismissedVideoPromptUrl = null
                        phoneChromeVisible = true
                        context.toastOnUi("已显示净视胶囊")
                    } else {
                        context.toastOnUi("当前页未检测到可播放视频")
                    }
                },
                applyForcePageZoom = { hostRef?.applyForcePageZoom(it) },
                applyPrivacySettings = { hostRef?.applyPrivacySettings() },
                translatePage = { runInPageTranslate(forceTranslate = false) },
                pageNeedsTranslate = pageNeedsTranslate,
                pageTranslated = pageTranslated,
                pageBookmarked = pageBookmarked,
                readerAutoOpenEnabled = { readerAutoOpen },
                setReaderAutoOpen = { enabled ->
                    readerAutoOpen = enabled
                    readerPrefs.triggerMode = if (enabled) {
                        BrowserReaderPrefs.TriggerMode.AutoDetect
                    } else {
                        BrowserReaderPrefs.TriggerMode.Manual
                    }
                },
                extremePurgeEnabled = { extremePurge },
                setExtremePurge = { enabled ->
                    extremePurge = enabled
                    readerPrefs.strongContentExtract = enabled
                },
                openReaderText = {
                    val pageUrl = clientRef?.lastUrl.orEmpty().ifBlank { address.text }
                    startExtract { openNovelReader(pageUrl, keepImages = false) }
                },
                openReaderImage = {
                    openReaderView(WebMangaBookFactory.Kind.Manga)
                },
                openReaderMixed = {
                    val pageUrl = clientRef?.lastUrl.orEmpty().ifBlank { address.text }
                    startExtract { openNovelReader(pageUrl, keepImages = true) }
                },
            ),
        )
    }

    AppScaffold(
        disableContentSampling = true,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (!tabletEmbedded) {
                // SPEC-130: status bar always opaque; omnibox may collapse under auto-hide.
                // SPEC-242: fullscreen locks chrome hidden; top AV uses short slide (must collapse height).
                val showPhoneTopChrome = !browserFullscreen &&
                    (phoneChromeVisible || !chromePrefs.autoHideTopBar || showFindInPage)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(scheme.surface)
                        .padding(WindowInsets.statusBars.asPaddingValues()),
                ) {
                AnimatedVisibility(
                    visible = showPhoneTopChrome,
                    enter = slideInVertically(animationSpec = tween(120)) { -it },
                    exit = slideOutVertically(animationSpec = tween(140)) { -it },
                ) {
                    if (phoneSplitChrome) {
                        // SPEC-169 / 1.7.0: top = home · omnibox · refresh
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(TopBarHeight)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (showNavigationBack) {
                                // SPEC-096: Home glyph exits to NTP/host, never WebView goBack.
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .size(BrowserUiTokens.ControlMedium)
                                        // SPEC-199: 玻璃钮涟漪随圆形。
                                        .clip(CircleShape)
                                        .browserControlLiquidGlass()
                                        .clickable(onClick = onNavigateBack),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    SymbolIcon(
                                        Symbols.Home,
                                        contentDescription = "主页",
                                        tint = scheme.onSurface,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            BasicTextField(
                                value = address,
                                onValueChange = { address = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(BrowserUiTokens.ControlMedium)
                                    .browserControlLiquidGlass(
                                        shape = ContinuousCapsule,
                                        interactive = false,
                                        // SPEC-214: 液态玻璃下地址栏无阴影。
                                        showShadow = false,
                                    )
                                    .padding(horizontal = BrowserUiTokens.SpaceMd, vertical = 6.dp)
                                    .onFocusChanged { fs ->
                                        val nowFocused = fs.isFocused
                                        if (!addressFocused && nowFocused) {
                                            val editUrl = clientRef?.lastUrl.orEmpty()
                                                .ifBlank { initialUrl }
                                            address = if (editUrl.isNotBlank()) {
                                                TextFieldValue(editUrl, TextRange(0, editUrl.length))
                                            } else {
                                                TextFieldValue("")
                                            }
                                            clipboardUrl = BrowserClipboardUrl.latest(context)
                                            // SPEC-207: 点击落点会盖掉选区，获焦后再全选一次。
                                            scope.launch {
                                                reselectAllOmniboxText(
                                                    get = { address },
                                                    set = { address = it },
                                                )
                                            }
                                        } else if (addressFocused && !nowFocused) {
                                            clipboardUrl = null
                                            syncIdleAddress(
                                                clientRef?.lastTitle ?: initialTitle,
                                                clientRef?.lastUrl ?: initialUrl,
                                            )
                                        }
                                        addressFocused = nowFocused
                                    },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = scheme.onSurface,
                                    fontSize = 14.sp,
                                ),
                                cursorBrush = SolidColor(scheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = { navigateToAddress() }),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (address.text.isEmpty()) {
                                            Text(
                                                "搜索或输入网址",
                                                color = scheme.onSurfaceVariant,
                                                fontSize = 14.sp,
                                            )
                                        }
                                        inner()
                                    }
                                },
                            )
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(BrowserUiTokens.ControlMedium)
                                    // SPEC-199: 玻璃钮涟漪随圆形。
                                    .clip(CircleShape)
                                    .browserControlLiquidGlass()
                                    .combinedClickable(
                                        onClick = { hostRef?.reload() },
                                        onLongClick = { hostRef?.scrollToTop() },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                SymbolIcon(
                                    Symbols.Refresh,
                                    contentDescription = "刷新",
                                    tint = scheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    } else {
                            // SPEC-168: landscape combined top bar
                            // SPEC-200 R2: 栏边界=横屏组合顶栏 Row，长按菜单整体置于顶栏下 4dp。
                            MenuBarBoundsProvider {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(TopBarHeight)
                                .padding(horizontal = phoneWebGap),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(phoneWebGap),
                        ) {
                            if (showNavigationBack) {
                                PhoneWebChromeIcon(
                                    icon = Symbols.Home,
                                    description = "主页",
                                    onClick = onNavigateBack,
                                )
                            }
                            PhoneWebChromeIcon(
                                icon = Symbols.ArrowBack,
                                description = "后退",
                                onClick = { navigateBackOrHome() },
                                onLongClick = {
                                    val items = hostRef?.navigationHistoryItems(
                                        io.legado.app.browser.engine.BrowserNavHistory.Direction.Back,
                                    ).orEmpty()
                                    if (items.isEmpty()) {
                                        context.toastOnUi("没有可后退的页面")
                                    } else {
                                        backHistoryItems = items
                                        forwardHistoryMenu = false
                                        backHistoryMenu = true
                                    }
                                },
                                menu = {
                                    BrowserNavHistoryMenu(
                                        expanded = backHistoryMenu,
                                        items = backHistoryItems,
                                        onDismiss = { backHistoryMenu = false },
                                        onSelect = { hostRef?.goBackOrForward(it.steps) },
                                        aboveAnchor = false,
                                    )
                                },
                            )
                            PhoneWebChromeIcon(
                                icon = Symbols.ArrowForwardIos,
                                description = "前进",
                                onClick = { hostRef?.goForward() },
                                onLongClick = {
                                    val items = hostRef?.navigationHistoryItems(
                                        io.legado.app.browser.engine.BrowserNavHistory.Direction.Forward,
                                    ).orEmpty()
                                    if (items.isEmpty()) {
                                        context.toastOnUi("没有可前进的页面")
                                    } else {
                                        forwardHistoryItems = items
                                        backHistoryMenu = false
                                        forwardHistoryMenu = true
                                    }
                                },
                                menu = {
                                    BrowserNavHistoryMenu(
                                        expanded = forwardHistoryMenu,
                                        items = forwardHistoryItems,
                                        onDismiss = { forwardHistoryMenu = false },
                                        onSelect = { hostRef?.goBackOrForward(it.steps) },
                                        aboveAnchor = false,
                                    )
                                },
                            )
                            PhoneWebChromeIcon(
                                icon = Symbols.Refresh,
                                description = "刷新",
                                onClick = { hostRef?.reload() },
                                onLongClick = { hostRef?.scrollToTop() },
                            )
                            BasicTextField(
                                value = address,
                                onValueChange = { address = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp)
                                    .height(phoneWebFieldHeight)
                                    .browserControlLiquidGlass(
                                        shape = ContinuousCapsule,
                                        interactive = false,
                                        // SPEC-214: 液态玻璃下地址栏无阴影。
                                        showShadow = false,
                                    )
                                    .padding(horizontal = BrowserUiTokens.SpaceMd, vertical = 6.dp)
                                    .onFocusChanged { fs ->
                                        val nowFocused = fs.isFocused
                                        if (!addressFocused && nowFocused) {
                                            val editUrl = clientRef?.lastUrl.orEmpty()
                                                .ifBlank { initialUrl }
                                            address = if (editUrl.isNotBlank()) {
                                                TextFieldValue(editUrl, TextRange(0, editUrl.length))
                                            } else {
                                                TextFieldValue("")
                                            }
                                            clipboardUrl = BrowserClipboardUrl.latest(context)
                                            // SPEC-207: 点击落点会盖掉选区，获焦后再全选一次。
                                            scope.launch {
                                                reselectAllOmniboxText(
                                                    get = { address },
                                                    set = { address = it },
                                                )
                                            }
                                        } else if (addressFocused && !nowFocused) {
                                            clipboardUrl = null
                                            syncIdleAddress(
                                                clientRef?.lastTitle ?: initialTitle,
                                                clientRef?.lastUrl ?: initialUrl,
                                            )
                                        }
                                        addressFocused = nowFocused
                                    },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = scheme.onSurface,
                                    fontSize = 14.sp,
                                ),
                                cursorBrush = SolidColor(scheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = { navigateToAddress() }),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (address.text.isEmpty()) {
                                            Text(
                                                "搜索或输入网址",
                                                color = scheme.onSurfaceVariant,
                                                fontSize = 14.sp,
                                            )
                                        }
                                        inner()
                                    }
                                },
                            )
                            PhoneWebChromeIcon(
                                icon = Symbols.Box,
                                description = "工具箱",
                                onClick = { showToolbox = true },
                            )
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(phoneWebIconSlot)
                                        // SPEC-199: 标签槽涟漪随圆形。
                                        .clip(CircleShape)
                                        .combinedClickable(
                                            onClick = {
                                                val open = onOpenTabs
                                                if (open != null) {
                                                    open()
                                                    BrowserSessionHolder.captureActiveThumbnail()
                                                } else {
                                                    showSnack("标签页即将支持")
                                                }
                                            },
                                            onLongClick = { tabMenu = true },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    BrowserTabCountBadge(
                                        count = openTabCount.size,
                                        tint = scheme.onSurface,
                                        useGlass = false,
                                        // SPEC-181: match left-side 20dp glyphs in landscape top bar.
                                        size = 20.dp,
                                        borderWidth = 1.5.dp,
                                        textSizeScale = 20f / 22f,
                                    )
                                }
                                BrowserTabCountMenu(
                                    expanded = tabMenu,
                                    onDismiss = { tabMenu = false },
                                    aboveAnchor = false,
                                    onNewTab = {
                                        BrowserTabManager.openNewTab(url = null, select = true)
                                    },
                                    onCloseAll = { BrowserTabManager.closeAll() },
                                    onAddBookmark = {
                                        val url = clientRef?.lastUrl?.takeIf { it.startsWith("http") }
                                            ?: address.text
                                        store.addBookmark(url, clientRef?.lastTitle.orEmpty())
                                        context.toastOnUi(context.getString(R.string.browser_favorite_added))
                                    },
                                )
                            }
                                Box(
                                    modifier = Modifier
                                        // SPEC-170: landscape 净读 FAB 32dp touch slot.
                                        .size(TabletBrowserLayout.PhoneLandscapeReaderFabDp.dp)
                                        // SPEC-199: 涟漪随圆形。
                                        .clip(CircleShape)
                                        .combinedClickable(
                                        enabled = !extracting,
                                        onClick = { openReaderView() },
                                        onLongClick = { readerModeMenu = true },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                // SPEC-201: 蓝圆 20→22dp、图标 16→15dp（操作者指定）。
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .browserReaderFabGlass(scheme.primary),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    SymbolIcon(
                                        symbol = Symbols.Book2,
                                        contentDescription = "净读",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                                BrowserReaderModeMenu(
                                    expanded = readerModeMenu,
                                    onDismiss = { readerModeMenu = false },
                                    autoOpenEnabled = readerAutoOpen,
                                    onAutoOpenChange = { enabled ->
                                        readerAutoOpen = enabled
                                        readerPrefs.triggerMode = if (enabled) {
                                            BrowserReaderPrefs.TriggerMode.AutoDetect
                                        } else {
                                            BrowserReaderPrefs.TriggerMode.Manual
                                        }
                                        showSnack(
                                            if (enabled) "已开启自动净读模式" else "已关闭自动净读模式",
                                        )
                                    },
                                    extremePurgeEnabled = extremePurge,
                                    onExtremePurgeChange = { enabled ->
                                        extremePurge = enabled
                                        readerPrefs.strongContentExtract = enabled
                                        showSnack(
                                            if (enabled) "已开启极致净化" else "已关闭极致净化",
                                        )
                                    },
                                    onTextMode = {
                                        val pageUrl = clientRef?.lastUrl.orEmpty().ifBlank { address.text }
                                        startExtract {
                                            openNovelReader(pageUrl, keepImages = false)
                                        }
                                    },
                                    onImageMode = {
                                        openReaderView(WebMangaBookFactory.Kind.Manga)
                                    },
                                    onMixedMode = {
                                        val pageUrl = clientRef?.lastUrl.orEmpty().ifBlank { address.text }
                                        startExtract {
                                            openNovelReader(pageUrl, keepImages = true)
                                        }
                                    },
                                    belowAnchor = true,
                                )
                            }
                        }
                        }
                    }
                    // SPEC-121: find bar under omnibox (not bottom — IME covers bottom).
                    if (showFindInPage) {
                        BrowserFindInPageBar(
                            query = findQuery,
                            matchLabel = findMatchLabel,
                            onQueryChange = { q ->
                                findQuery = q
                                runFindInPage(q, findNext = false, forward = true)
                            },
                            onNext = {
                                runFindInPage(findQuery, findNext = true, forward = true)
                            },
                            onPrev = {
                                runFindInPage(findQuery, findNext = true, forward = false)
                            },
                            onClose = {
                                runFindInPage("", findNext = false, forward = true)
                                showFindInPage = false
                                findQuery = ""
                                findMatchLabel = ""
                            },
                        )
                    }
                }
                }
            }
        },
    ) { paddingValues ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            AlohaBrowserCompose(
                // SPEC-075: web above fixed chrome reserve (stable height).
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = webBottomInset),
                incognito = incognito,
                retainAcrossDispose = true,
                sessionKey = resolvedSessionKey,
                clientFactory = {
                    BrowserAwContentsClient(
                        onStatus = { status ->
                            if (status.startsWith("错误:")) {
                                showSnack(status.removePrefix("错误:").trim())
                            }
                        },
                        onProgress = { value ->
                            if (mainFrameLoading) {
                                progress = value.coerceIn(0, 100)
                                if (value >= 100) mainFrameLoading = false
                            }
                        },
                        onMainFrameLoadStarted = {
                            mainFrameLoading = true
                            progress = 0
                            pageNeedsTranslate = false
                            pageTranslated = false
                            langDetectJob?.cancel()
                            // SPEC-135: drop previous site's sniffed video so B does not show A's offer.
                            BrowserVideoOfferBus.clear()
                            dismissedVideoPromptUrl = null
                            // SPEC-188: navigate away → stop page/native video (听音频例外).
                            BrowserVideoLeaveGuard.stopExceptAudioListen(context)
                        },
                        onOverrideUrl = { handleSpecialUrl(it) },
                        onNavigationOverride = { target -> hostRef?.loadUrl(target) },
                        isAdBlockEnabled = { shield.adBlockEnabled },
                        isPopupBlockEnabled = { shield.blockPopups },
                        isExternalBlockEnabled = { shield.blockExternalApps },
                        onExternalBlocked = { showSnack("已阻止打开其它应用") },
                        onExternalAppOpen = { openExternalAppChooser(it) },
                        onPopupBlocked = { showSnack("已阻止弹出窗口") },
                        isPickModeActive = { pickMode },
                        onConsoleBlockPick = { payload ->
                            if (payload.startsWith("OFF:")) {
                                val sel = payload.removePrefix("OFF:")
                                if (sel.isNotEmpty()) {
                                    pendingSelectors = pendingSelectors - sel
                                }
                            } else if (payload.isNotEmpty()) {
                                pendingSelectors = pendingSelectors + payload
                            }
                        },
                        onContextMenuHit = { json ->
                            // SPEC-218: link long-press should not leave a stuck text selection bar.
                            hostRef?.clearTextSelection()
                            val parsed = GSON.fromJsonObject<BrowserContextMenuPayload>(json)
                                .getOrNull()
                            if (parsed != null &&
                                (parsed.href.startsWith("http") || parsed.img.startsWith("http"))
                            ) {
                                linkContextHit = BrowserLinkContextHit(
                                    href = parsed.href,
                                    linkText = parsed.text,
                                    imageUrl = parsed.img,
                                    pageTitle = parsed.title,
                                    pageUrl = parsed.page,
                                )
                            }
                        },
                        onScrollDirection = { dir ->
                            // SPEC-218: compositor/window scroll often skips container
                            // onScrollChanged — clear stuck selection when page scrolls.
                            hostRef?.clearTextSelectionIfActive()
                            if (!tabletEmbedded && !browserFullscreen) {
                                phoneChromeVisible = if (chromePrefs.autoHideTopBar) {
                                    dir != "down"
                                } else {
                                    true
                                }
                            }
                            onScrollDirection?.invoke(dir)
                        },
                        userAgentProvider = { BrowserUa.resolve(context) },
                        onDownloadRequested = { url, ua, disposition, mime, _ ->
                            enqueueTriggeredDownload(url, ua, disposition, mime)
                        },
                        onMainFrameError = { url, netError, _ ->
                            tryHttpsFallback(url, netError)
                        },
                        customViewShowHandler = { view, callback ->
                            // SPEC-178: attach on the UI thread; Chromium docs use
                            // window.addContentView. Immediate onCustomViewHidden on
                            // failure returns the picture so the page does not stay black.
                            val run = Runnable {
                                val activity = context.findActivityOrNull()
                                val window = activity?.window
                                if (activity == null || window == null) {
                                    android.util.Log.w(
                                        "Diag146",
                                        "onShowCustomView: no activity/window",
                                    )
                                    runCatching { callback.onCustomViewHidden() }
                                    return@Runnable
                                }
                                if (videoCustomView != null) {
                                    android.util.Log.w(
                                        "Diag146",
                                        "onShowCustomView: already showing",
                                    )
                                    runCatching { callback.onCustomViewHidden() }
                                    return@Runnable
                                }
                                val host = android.widget.FrameLayout(activity).apply {
                                    setBackgroundColor(android.graphics.Color.BLACK)
                                    isClickable = true
                                    importantForAccessibility =
                                        android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                                }
                                (view.parent as? ViewGroup)?.removeView(view)
                                host.addView(
                                    view,
                                    android.widget.FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    ),
                                )
                                window.addFlags(
                                    android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                                )
                                window.addContentView(
                                    host,
                                    android.widget.FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.Gravity.CENTER,
                                    ),
                                )
                                host.bringToFront()
                                videoFullscreenHost = host
                                videoCustomView = view
                                videoCustomViewCallback = callback
                                activity.keepScreenOn(true)
                                activity.toggleSystemBar(false)
                                android.util.Log.i("Diag146", "onShowCustomView: attached")
                            }
                            val decor = context.findActivityOrNull()?.window?.decorView
                            if (decor != null) {
                                decor.post(run)
                            } else {
                                run.run()
                            }
                        },
                        customViewHideHandler = {
                            clearVideoCustomViewUi()
                        },
                        onPlayableMedia = { mediaUrl, headersRaw, documentUrl, isHls ->
                            // SPEC-132: publish offer; UI shows prompt when switch is on.
                            BrowserVideoOfferBus.publish(
                                BrowserVideoOffer(
                                    url = mediaUrl,
                                    headers = BrowserVideoOfferBus.parseHeaders(headersRaw),
                                    pageUrl = documentUrl.ifBlank { clientRef?.lastUrl.orEmpty() },
                                    title = clientRef?.lastTitle.orEmpty(),
                                    isHls = isHls,
                                ),
                            )
                        },
                        onPageMediaState = { playing, mediaUrl, documentUrl, durationSec, _ ->
                            // SPEC-188: webpage player → system media notification.
                            when (playing) {
                                true -> {
                                    if (!BrowserVideoPlaybackService.isAudioOnlyActive() &&
                                        !BrowserVideoPlayerBridge.hasNativeVideo()
                                    ) {
                                        BrowserWebMediaBridge.onPageMediaPlay(
                                            mediaUrl = mediaUrl,
                                            documentUrl = documentUrl,
                                            durationSec = durationSec,
                                            pageTitle = clientRef?.lastTitle.orEmpty(),
                                        )
                                        BrowserVideoPlaybackService.startPage(
                                            context,
                                            BrowserWebMediaBridge.title,
                                        )
                                    }
                                }
                                false -> {
                                    BrowserWebMediaBridge.onPageMediaPause()
                                    if (BrowserWebMediaBridge.active &&
                                        !BrowserVideoPlaybackService.isAudioOnlyActive() &&
                                        !BrowserVideoPlayerBridge.hasNativeVideo()
                                    ) {
                                        BrowserVideoPlaybackService.startPage(
                                            context,
                                            BrowserWebMediaBridge.title,
                                        )
                                    }
                                }
                                null -> {
                                    BrowserWebMediaBridge.onPageMediaDestroy()
                                    if (!BrowserVideoPlaybackService.isAudioOnlyActive() &&
                                        !BrowserVideoPlayerBridge.hasNativeVideo()
                                    ) {
                                        BrowserVideoPlaybackService.stop(context)
                                    }
                                }
                            }
                        },
                        onUrlChanged = { url ->
                            pendingForcedUrl = null
                            syncIdleAddress(clientRef?.lastTitle, url)
                        },
                        onTitleChanged = { url, title ->
                            syncIdleAddress(title, url)
                            onPageMeta?.invoke(url, title)
                        },
                        onPageFinishedExtra = { url, title ->
                            mainFrameLoading = false
                            progress = 100
                            pendingForcedUrl = null
                            // SPEC-106: do not clear HTTPS fallback arm immediately —
                            // Chromium often finishes the failed main frame before
                            // onReceivedError, which raced away agefans.com HTTP retry.
                            // Give error handling a short window, then drop the one-shot arm.
                            scope.launch {
                                delay(500)
                                armedHttpsFallback = null
                            }
                            // SPEC-242: after NTP→page, clear redirect history so back exits to NTP.
                            if (BrowserSessionHolder.clearHistoryAfterNtpExit) {
                                BrowserSessionHolder.clearHistoryAfterNtpExit = false
                                hostRef?.clearHistory()
                            }
                            syncIdleAddress(title, url)
                            onPageMeta?.invoke(url, title)
                            refreshPageBookmarked(url)
                            if (!incognito) {
                                store.addHistory(url, title)
                            }
                            val historyNav = clientRef?.historyNavigationThisLoad == true
                            // SPEC-022 B: scroll hook on phone + tablet for chrome auto-hide + 073 memory.
                            hostRef?.evaluateJavaScript(
                                BrowserAwContentsClient.SCROLL_HOOK_JS,
                                null,
                            )
                            hostRef?.evaluateJavaScript(
                                BrowserAwContentsClient.VIDEO_HOOK_JS,
                                null,
                            )
                            hostRef?.evaluateJavaScript(BrowserContextMenuJs.HOOK_JS, null)
                            if (historyNav) {
                                // SPEC-073: skip layout-affecting injects on back/forward; restore scroll.
                                BrowserScrollMemory.recall(url)?.let { y ->
                                    hostRef?.restoreScrollY(y)
                                }
                            } else {
                                BrowserSiteCssFixes.scriptForUrl(url)?.let { js ->
                                    hostRef?.evaluateJavaScript(js, null)
                                }
                                applyAdShield(url)
                                maybeAutoOpenReader(url)
                            }
                            // SPEC-134: keep pick listeners after any load while still in pick mode.
                            if (pickMode) {
                                hostRef?.evaluateJavaScript(
                                    BrowserShieldJs.startPickModeScript(),
                                    null,
                                )
                            }
                            // SPEC-120: force page zoom viewport override when enabled.
                            if (chromePrefs.forcePageZoom) {
                                hostRef?.evaluateJavaScript(
                                    io.legado.app.browser.engine.BrowserPrivacyControls
                                        .forceZoomViewportScript(true),
                                    null,
                                )
                            }
                            // SPEC-123 diag: per-page viewport probe.
                            hostRef?.let {
                                io.legado.app.browser.engine.BrowserDiagProbe.run(it, url)
                            }
                            // SPEC-254/262: detect translate eligibility (no ask bar).
                            if (url != translateDetectUrl) {
                                pageNeedsTranslate = false
                                pageTranslated = false
                            }
                            if (!historyNav) {
                                maybeDetectTranslateNeed(url)
                            }
                        },
                    )
                },
                onReady = { host, client ->
                    hostRef = host
                    clientRef = client
                    if (!BrowserSessionHolder.hasLoadedInitialUrl) {
                        if (initialHtml.isNullOrEmpty()) {
                            armHttpsFallback(initialOmniboxLabel, initialUrl)
                            host.loadUrl(initialUrl)
                        } else {
                            armedHttpsFallback = null
                            host.loadDataWithBaseURL(
                                initialUrl,
                                initialHtml,
                                "text/html",
                                "utf-8",
                                initialUrl,
                            )
                        }
                        BrowserSessionHolder.markLoaded()
                        appliedNavEpoch = navEpoch
                        syncIdleAddress(initialTitle, initialUrl)
                    } else {
                        // SPEC-125: restored after park — if tab URL moved (open from NTP),
                        // must load; switchTo with same URL keeps parked page.
                        if (initialUrl.isNotBlank() && initialUrl != client.lastUrl) {
                            armHttpsFallback(initialOmniboxLabel, initialUrl)
                            client.lastError = null
                            pendingForcedUrl = initialUrl
                            host.loadUrl(initialUrl)
                            appliedNavEpoch = navEpoch
                            syncIdleAddress(initialTitle, initialUrl)
                        } else {
                            pendingForcedUrl = null
                            appliedNavEpoch = navEpoch
                            syncIdleAddress(
                                client.lastTitle.ifBlank { initialTitle.orEmpty() },
                                client.lastUrl.ifBlank { initialUrl },
                            )
                        }
                    }
                },
                onDestroyed = {
                    hostRef = null
                    clientRef = null
                },
            )
            if (BrowserLoadProgress.shouldShow(mainFrameLoading, progress)) {
                AppLinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    progress = (progress / 100f).coerceIn(0f, 1f),
                )
            }
            if (!tabletEmbedded && (addressSuggestions.isNotEmpty() || clipboardUrl != null)) {
                BrowserOmniboxSuggestPanel(
                    items = addressSuggestions,
                    onSelect = { openSuggestItem(it) },
                    clipboardUrl = clipboardUrl,
                    onClipboardSelect = { openClipboardUrl(it) },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            // SPEC-134: pick-block bar replaces normal bottom chrome (phone + tablet).
            if (pickMode) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = BrowserUiTokens.PagePaddingH)
                        .padding(bottom = floatingBottomPad)
                        .padding(top = floatingChromeTopGap),
                ) {
                    val blurOn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    val barBackdrop = LocalTopBarBackdrop.current ?: chromeBackdrop
                    FloatingBottomBar(
                        modifier = Modifier.fillMaxWidth(),
                        selectedIndex = { 0 },
                        onSelected = {},
                        onReselected = {},
                        backdrop = barBackdrop,
                        tabsCount = 3,
                        isBlurEnabled = blurOn,
                        enableDragSwitch = false,
                        persistentSelection = false,
                        barHeight = phoneChromeBarHeight,
                        indicatorHeight = phoneChromeBarHeight,
                    ) {
                        FloatingBottomBarItem(
                            onClick = {
                                exitPickMode()
                                context.toastOnUi("已关闭点选屏蔽")
                            },
                        ) {
                            SymbolIcon(
                                Symbols.Close,
                                contentDescription = "关闭",
                                tint = scheme.onSurface,
                            )
                        }
                        FloatingBottomBarItem(
                            onClick = { openPickBlockSettings() },
                        ) {
                            SymbolIcon(
                                Symbols.Settings,
                                contentDescription = "设置",
                                tint = scheme.onSurface,
                            )
                        }
                        FloatingBottomBarItem(
                            onClick = { confirmPickMode() },
                        ) {
                            SymbolIcon(
                                Symbols.Check,
                                contentDescription = "确认",
                                tint = scheme.onSurface,
                            )
                        }
                    }
                }
            } else if (!tabletEmbedded) {
                // Floating chrome — phone only (SPEC-011 2A); tablet uses top chrome (SPEC-018).
                // SPEC-022 B / SPEC-242: auto-hide via layout offset (keep glass composed).
                // SPEC-266: do NOT .alpha() liquid-glass chrome over AwContents (RenderThread risk).
                // SPEC-243: skip compose entirely in toolbox fullscreen (no blank placeholder).
                // SPEC-045: translate prompt sits above the bottom chrome (phone) / content bottom (tablet).
                if (!browserFullscreen) {
                val showPhoneBottomChrome = phoneChromeVisible
                val bottomChromeProgress by animateFloatAsState(
                    targetValue = if (showPhoneBottomChrome) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = if (showPhoneBottomChrome) 120 else 160,
                    ),
                    label = "phoneBottomChrome",
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { phoneBottomChromeHeightPx = it.height }
                        .offset {
                            val h = phoneBottomChromeHeightPx.coerceAtLeast(1)
                            IntOffset(0, ((1f - bottomChromeProgress) * h).roundToInt())
                        }
                        .padding(horizontal = BrowserUiTokens.PagePaddingH)
                        .padding(bottom = floatingBottomPad)
                        .padding(top = floatingChromeTopGap),
                ) {
                        val snackText = chromeSnack
                        if (snackText != null) {
                            ChromeSnackBar(message = snackText)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        val bannerText = downloadBanner
                        if (bannerText != null) {
                            DownloadBannerBar(
                                fileName = bannerText,
                                onOpenDownloads = {
                                    downloadBanner = null
                                    val open = onOpenDownloads
                                    if (open != null) open() else showSnack("下载页不可用")
                                },
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        if (showVideoPlayerPrompt) {
                            val offer = videoOffer
                            if (offer != null) {
                                // SPEC-201: 手机横屏宽度 0.75→0.375（50%）并降高；竖屏不变。
                                val landscape =
                                    LocalConfiguration.current.orientation ==
                                        Configuration.ORIENTATION_LANDSCAPE
                                // SPEC-220: capsule↔ball morph; collapsed gap to bar = 4dp.
                                AnimatedContent(
                                    targetState = videoCapsuleCollapsed,
                                    transitionSpec = {
                                        (fadeIn(animationSpec = tween(220)) togetherWith
                                            fadeOut(animationSpec = tween(180)))
                                            .using(SizeTransform(clip = false))
                                    },
                                    label = "videoCapsuleMorph",
                                    modifier = Modifier.fillMaxWidth(),
                                ) { collapsed ->
                                    if (collapsed) {
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            VideoPlayerCollapsedBall(
                                                onExpand = {
                                                    videoCapsuleInteracted = true
                                                    videoCapsuleExpandedByUser = true
                                                    videoCapsuleCollapsed = false
                                                },
                                                onPlay = {
                                                    videoCapsuleInteracted = true
                                                    BrowserVideoPlaybackService.stop(context)
                                                    context.startActivity(
                                                        BrowserVideoPlayerActivity.intent(
                                                            context,
                                                            offer,
                                                        ),
                                                    )
                                                },
                                                modifier = Modifier.align(Alignment.CenterEnd),
                                            )
                                        }
                                    } else {
                                        VideoPlayerPromptCapsule(
                                            widthFraction = if (landscape) 0.375f else 0.75f,
                                            compact = landscape,
                                            onPlay = {
                                                videoCapsuleInteracted = true
                                                BrowserVideoPlaybackService.stop(context)
                                                context.startActivity(
                                                    BrowserVideoPlayerActivity.intent(
                                                        context,
                                                        offer,
                                                    ),
                                                )
                                            },
                                            onAudioOnly = {
                                                videoCapsuleInteracted = true
                                                BrowserVideoPlaybackService.startAudio(
                                                    context,
                                                    offer,
                                                )
                                                dismissedVideoPromptUrl = offer.url
                                                context.toastOnUi("已开始听音频")
                                            },
                                            onPip = {
                                                videoCapsuleInteracted = true
                                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                                                    context.toastOnUi("当前系统不支持画中画")
                                                } else {
                                                    BrowserVideoPlaybackService.stop(context)
                                                    context.startActivity(
                                                        BrowserVideoPlayerActivity.intent(
                                                            context,
                                                            offer,
                                                            autoPip = true,
                                                        ),
                                                    )
                                                }
                                            },
                                            onDismiss = {
                                                videoCapsuleInteracted = true
                                                dismissedVideoPromptUrl = offer.url
                                            },
                                            showPlaylist = playlistOffers.size > 1,
                                            playlist = playlistOffers,
                                            onPlayOffer = { item ->
                                                videoCapsuleInteracted = true
                                                BrowserVideoPlaybackService.stop(context)
                                                context.startActivity(
                                                    BrowserVideoPlayerActivity.intent(
                                                        context,
                                                        item,
                                                    ),
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                                // SPEC-221: 迷你球与展开胶囊同为 16dp，避免贴死底栏。
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                        if (phoneSplitChrome) {
                            // SPEC-169 / 1.7.0: bottom = back · forward · toolbox · tabs · reader FAB
                            // SPEC-200 R2: 栏边界=整条底栏 Row，长按菜单整体置于栏上 4dp。
                            MenuBarBoundsProvider {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    val blurOn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                    val barBackdrop = LocalTopBarBackdrop.current ?: chromeBackdrop
                                    fun activateChromeTab(index: Int) {
                                        when (index) {
                                            0 -> navigateBackOrHome()
                                            1 -> hostRef?.goForward()
                                            2 -> showToolbox = true
                                            3 -> {
                                                val open = onOpenTabs
                                                if (open != null) {
                                                    open()
                                                    BrowserSessionHolder.captureActiveThumbnail()
                                                } else {
                                                    showSnack("标签页即将支持")
                                                }
                                            }
                                        }
                                    }
                                    fun openBackHistory() {
                                        val items = hostRef?.navigationHistoryItems(
                                            io.legado.app.browser.engine.BrowserNavHistory.Direction.Back,
                                        ).orEmpty()
                                        if (items.isEmpty()) {
                                            context.toastOnUi("没有可后退的页面")
                                        } else {
                                            backHistoryItems = items
                                            forwardHistoryMenu = false
                                            backHistoryMenu = true
                                        }
                                    }
                                    fun openForwardHistory() {
                                        val items = hostRef?.navigationHistoryItems(
                                            io.legado.app.browser.engine.BrowserNavHistory.Direction.Forward,
                                        ).orEmpty()
                                        if (items.isEmpty()) {
                                            context.toastOnUi("没有可前进的页面")
                                        } else {
                                            forwardHistoryItems = items
                                            backHistoryMenu = false
                                            forwardHistoryMenu = true
                                        }
                                    }
                                    FloatingBottomBar(
                                        modifier = Modifier.fillMaxWidth(),
                                            selectedIndex = { 0 },
                                            onSelected = { activateChromeTab(it) },
                                            onReselected = { activateChromeTab(it) },
                                            backdrop = barBackdrop,
                                            tabsCount = 4,
                                            isBlurEnabled = blurOn,
                                            enableDragSwitch = false,
                                            persistentSelection = false,
                                            barHeight = phoneChromeBarHeight,
                                            indicatorHeight = phoneChromeBarHeight,
                                        ) {
                                        FloatingBottomBarItem(
                                            onClick = { activateChromeTab(0) },
                                            onLongClick = { openBackHistory() },
                                        ) {
                                            Box {
                                                SymbolIcon(
                                                    Symbols.ArrowBack,
                                                    contentDescription = "后退",
                                                    tint = scheme.onSurface,
                                                )
                                                BrowserNavHistoryMenu(
                                                    expanded = backHistoryMenu,
                                                    items = backHistoryItems,
                                                    onDismiss = { backHistoryMenu = false },
                                                    onSelect = { hostRef?.goBackOrForward(it.steps) },
                                                    aboveAnchor = true,
                                                )
                                            }
                                        }
                                        FloatingBottomBarItem(
                                            onClick = { activateChromeTab(1) },
                                            onLongClick = { openForwardHistory() },
                                        ) {
                                            Box {
                                                SymbolIcon(
                                                    Symbols.ArrowForwardIos,
                                                    contentDescription = "前进",
                                                    tint = scheme.onSurface,
                                                )
                                                BrowserNavHistoryMenu(
                                                    expanded = forwardHistoryMenu,
                                                    items = forwardHistoryItems,
                                                    onDismiss = { forwardHistoryMenu = false },
                                                    onSelect = { hostRef?.goBackOrForward(it.steps) },
                                                    aboveAnchor = true,
                                                )
                                            }
                                        }
                                        FloatingBottomBarItem(
                                            onClick = { activateChromeTab(2) },
                                        ) {
                                            SymbolIcon(
                                                Symbols.Box,
                                                contentDescription = "工具箱",
                                                tint = scheme.onSurface,
                                                size = 28.dp,
                                            )
                                        }
                                        FloatingBottomBarItem(
                                            onClick = { activateChromeTab(3) },
                                            onLongClick = { tabMenu = true },
                                        ) {
                                            Box {
                                                BrowserTabCountBadge(
                                                    count = openTabCount.size,
                                                    tint = scheme.onSurface,
                                                    useGlass = false,
                                                    size = 28.dp,
                                                    borderWidth = 2.dp,
                                                )
                                                BrowserTabCountMenu(
                                                    expanded = tabMenu,
                                                    onDismiss = { tabMenu = false },
                                                    aboveAnchor = true,
                                                    onNewTab = {
                                                        BrowserTabManager.openNewTab(url = null, select = true)
                                                    },
                                                    onCloseAll = { BrowserTabManager.closeAll() },
                                                    onAddBookmark = {
                                                        val url = clientRef?.lastUrl?.takeIf { it.startsWith("http") }
                                                            ?: address.text
                                                        store.addBookmark(url, clientRef?.lastTitle.orEmpty())
                                                        context.toastOnUi(
                                                            context.getString(R.string.browser_favorite_added),
                                                        )
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(BrowserUiTokens.SpaceMd))
                                Box(
                                    modifier = Modifier
                                        .size(phoneChromeBarHeight)
                                        // SPEC-199: 涟漪随圆形。
                                        .clip(CircleShape)
                                        .browserReaderFabGlass(scheme.primary)
                                        .combinedClickable(
                                            enabled = !extracting,
                                            onClick = { openReaderView() },
                                            onLongClick = { readerModeMenu = true },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    SymbolIcon(
                                        symbol = Symbols.Book2,
                                        contentDescription = "净读视图",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    BrowserReaderModeMenu(
                                        expanded = readerModeMenu,
                                        onDismiss = { readerModeMenu = false },
                                        autoOpenEnabled = readerAutoOpen,
                                        onAutoOpenChange = { enabled ->
                                            readerAutoOpen = enabled
                                            readerPrefs.triggerMode = if (enabled) {
                                                BrowserReaderPrefs.TriggerMode.AutoDetect
                                            } else {
                                                BrowserReaderPrefs.TriggerMode.Manual
                                            }
                                            showSnack(
                                                if (enabled) "已开启自动净读模式" else "已关闭自动净读模式",
                                            )
                                        },
                                        extremePurgeEnabled = extremePurge,
                                        onExtremePurgeChange = { enabled ->
                                            extremePurge = enabled
                                            readerPrefs.strongContentExtract = enabled
                                            showSnack(
                                                if (enabled) "已开启极致净化" else "已关闭极致净化",
                                            )
                                        },
                                        onTextMode = {
                                            val pageUrl = clientRef?.lastUrl.orEmpty().ifBlank { address.text }
                                            startExtract {
                                                openNovelReader(pageUrl, keepImages = false)
                                            }
                                        },
                                        onImageMode = {
                                            openReaderView(WebMangaBookFactory.Kind.Manga)
                                        },
                                        onMixedMode = {
                                            val pageUrl = clientRef?.lastUrl.orEmpty().ifBlank { address.text }
                                            startExtract {
                                                openNovelReader(pageUrl, keepImages = true)
                                            }
                                        },
                                    )
                                }
                            }
                            }
                        }
                    }
                } // SPEC-243: end !browserFullscreen phone bottom chrome
            } else {
                // SPEC-136/217: tablet video capsule — below top chrome, top-end;
                // 8s idle → collapsed ball same corner.
                if (showVideoPlayerPrompt) {
                    val offer = videoOffer
                    if (offer != null) {
                        // SPEC-220: tablet capsule↔ball morph (TR corner unchanged).
                        AnimatedContent(
                            targetState = videoCapsuleCollapsed,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(220)) togetherWith
                                    fadeOut(animationSpec = tween(180)))
                                    .using(SizeTransform(clip = false))
                            },
                            label = "tabletVideoCapsuleMorph",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 12.dp, end = 16.dp),
                        ) { collapsed ->
                        if (collapsed) {
                            VideoPlayerCollapsedBall(
                                onExpand = {
                                    videoCapsuleInteracted = true
                                    videoCapsuleExpandedByUser = true
                                    videoCapsuleCollapsed = false
                                },
                                onPlay = {
                                    videoCapsuleInteracted = true
                                    BrowserVideoPlaybackService.stop(context)
                                    context.startActivity(
                                        BrowserVideoPlayerActivity.intent(context, offer),
                                    )
                                },
                            )
                        } else {
                            VideoPlayerPromptCapsule(
                                onPlay = {
                                    videoCapsuleInteracted = true
                                    BrowserVideoPlaybackService.stop(context)
                                    context.startActivity(
                                        BrowserVideoPlayerActivity.intent(context, offer),
                                    )
                                },
                                onAudioOnly = {
                                    videoCapsuleInteracted = true
                                    BrowserVideoPlaybackService.startAudio(context, offer)
                                    dismissedVideoPromptUrl = offer.url
                                    context.toastOnUi("已开始听音频")
                                },
                                onPip = {
                                    videoCapsuleInteracted = true
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                                        context.toastOnUi("当前系统不支持画中画")
                                    } else {
                                        BrowserVideoPlaybackService.stop(context)
                                        context.startActivity(
                                            BrowserVideoPlayerActivity.intent(
                                                context,
                                                offer,
                                                autoPip = true,
                                            ),
                                        )
                                    }
                                },
                                onDismiss = {
                                    videoCapsuleInteracted = true
                                    dismissedVideoPromptUrl = offer.url
                                },
                                showPlaylist = playlistOffers.size > 1,
                                playlist = playlistOffers,
                                onPlayOffer = { item ->
                                    videoCapsuleInteracted = true
                                    BrowserVideoPlaybackService.stop(context)
                                    context.startActivity(
                                        BrowserVideoPlayerActivity.intent(context, item),
                                    )
                                },
                                widthFraction = 0.315f,
                                // SPEC-201: 平板宽度 0.45→0.315（70%）+ 紧凑高度。
                                compact = true,
                            )
                        }
                        }
                    }
                }
                // SPEC-066/132 tablet: banners near bottom of content (shell has top chrome only).
                val tabletBanner = downloadBanner
                val tabletSnack = chromeSnack
                if (tabletBanner != null || tabletSnack != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = BrowserUiTokens.PagePaddingH)
                            .padding(bottom = 16.dp),
                    ) {
                        if (tabletSnack != null) {
                            ChromeSnackBar(message = tabletSnack)
                            if (tabletBanner != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        if (tabletBanner != null) {
                            DownloadBannerBar(
                                fileName = tabletBanner,
                                onOpenDownloads = {
                                    downloadBanner = null
                                    val open = onOpenDownloads
                                    if (open != null) open() else showSnack("下载页不可用")
                                },
                            )
                        }
                    }
                }
            }

            // SPEC-242/243: phone chrome-fullscreen restore — more opaque (更实).
            if (!tabletEmbedded && browserFullscreen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 54.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(scheme.surface.copy(alpha = 0.82f))
                        .clickable {
                            // SPEC-244: show bottom chrome immediately on exit.
                            browserFullscreen = false
                            phoneChromeVisible = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    SymbolIcon(
                        Symbols.CloseFullscreen,
                        contentDescription = "退出全屏",
                        tint = scheme.onSurface.copy(alpha = 0.92f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // SPEC-121: tablet embedded has no phone topBar — show find under chrome at top.
            if (tabletEmbedded && showFindInPage) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(scheme.surface),
                ) {
                    BrowserFindInPageBar(
                        query = findQuery,
                        matchLabel = findMatchLabel,
                        onQueryChange = { q ->
                            findQuery = q
                            runFindInPage(q, findNext = false, forward = true)
                        },
                        onNext = {
                            runFindInPage(findQuery, findNext = true, forward = true)
                        },
                        onPrev = {
                            runFindInPage(findQuery, findNext = true, forward = false)
                        },
                        onClose = {
                            runFindInPage("", findNext = false, forward = true)
                            showFindInPage = false
                            findQuery = ""
                            findMatchLabel = ""
                        },
                    )
                }
            }

            if (extracting || translating) {
                // SPEC-149/259: 胶囊加载态——无遮罩；净读「加载中」/ 翻译「翻译中」；点取消可中止。
                // 宽度 = 屏幕宽 40%；手机在底栏上方 16dp，平板在顶栏下方 16dp。
                val extractCapsuleWidth = LocalConfiguration.current.screenWidthDp.dp * 0.40f
                val capsuleLabel = if (translating) "翻译中" else "加载中"
                val onCapsuleCancel = if (translating) {
                    { cancelTranslate() }
                } else {
                    { cancelExtract() }
                }
                if (tabletEmbedded) {
                    // 平板顶栏 = 状态栏 + 标签条 40dp + 功能栏 TopBarHeight（同 TabletBrowserChrome）。
                    ReaderLoadingCapsule(
                        label = capsuleLabel,
                        onCancel = onCapsuleCancel,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(
                                top = WindowInsets.statusBars.asPaddingValues()
                                    .calculateTopPadding() + 40.dp + TopBarHeight + 16.dp,
                            )
                            .width(extractCapsuleWidth),
                    )
                } else {
                    // SPEC-169: raise above portrait bottom bar; landscape has no function bar.
                    val barLift = if (phoneSplitChrome) phoneChromeBarHeight else 0.dp
                    ReaderLoadingCapsule(
                        label = capsuleLabel,
                        onCancel = onCapsuleCancel,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = floatingBottomPad + barLift + 16.dp)
                            .width(extractCapsuleWidth),
                    )
                }
            }
        }
    }

    // SPEC-070: Edge-style toolbox grid (half screen).
    BrowserLinkContextMenu(
        hit = linkContextHit,
        onDismiss = { linkContextHit = null },
        onOpenNewTab = { url ->
            BrowserTabManager.openNewTab(url = url, incognito = false, select = true)
            BrowserOpenBridge.offer(url)
        },
        onOpenIncognito = { url ->
            BrowserTabManager.openNewTab(url = url, incognito = true, select = true)
            BrowserOpenBridge.offer(url)
        },
        onOpenNewWindow = { url ->
            runCatching {
                val intent = MainIntent.createWebViewIntent(context, url = url)
                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK,
                )
                context.startActivity(intent)
            }.onFailure {
                BrowserTabManager.openNewTab(url = url, select = true)
                BrowserOpenBridge.offer(url)
                context.toastOnUi("已在新标签页打开")
            }
        },
        onCopyLink = { url ->
            context.sendToClip(url)
            context.toastOnUi("已复制链接")
        },
        onCopyLinkText = { text ->
            context.sendToClip(text)
            context.toastOnUi("已复制文本")
        },
        onDownloadLink = { url ->
            enqueueTriggeredDownload(url, BrowserUa.resolve(context), "", "")
        },
        onShareLink = { url, title ->
            context.share(
                if (title.isNotBlank()) "$title\n$url" else url,
                title = "分享链接",
            )
        },
        onDownloadImage = { url ->
            enqueueTriggeredDownload(url, BrowserUa.resolve(context), "", "image/*")
        },
        onShareImage = { url ->
            context.share(url, title = "分享图片")
        },
    )

    BrowserToolboxSheet(
        show = showToolbox,
        onDismiss = { showToolbox = false },
        actions = BrowserToolboxActions(
            onOpenBookmarks = {
                val open = onOpenBookmarks
                if (open != null) open() else showSnack("书签页不可用")
            },
            onOpenHistory = {
                val open = onOpenHistory
                if (open != null) open() else showSnack("历史页不可用")
            },
            onOpenDownloads = {
                val open = onOpenDownloads
                if (open != null) open() else showSnack("下载页不可用")
            },
            onOpenSettings = {
                val open = onOpenBrowserSettings
                if (open != null) open() else showSnack("请从主页进入浏览器设置")
            },
            onNewIncognitoTab = {
                BrowserTabManager.openNewTab(url = null, incognito = true, select = true)
            },
            onAddBookmark = { toggleBookmark() },
            pageBookmarked = pageBookmarked,
            onSavePage = { saveCurrentPage() },
            onShare = { shareCurrentPage() },
            onCopyLink = {
                val currentUrl = clientRef?.lastUrl.orEmpty().ifBlank { address.text }
                context.sendToClip(currentUrl)
            },
            onPickBlock = { enterPickMode() },
            adBlockEnabled = adBlockEnabled,
            onToggleAdBlock = {
                val enabled = !adBlockEnabled
                adBlockEnabled = enabled
                shield.adBlockEnabled = enabled
                if (enabled) {
                    applyAdShield(clientRef?.lastUrl ?: address.text)
                    showSnack("已开启广告拦截")
                } else {
                    showSnack("已关闭广告拦截（新请求生效）")
                }
            },
            uaMode = uaMode,
            onToggleUa = {
                uaMode = when (uaMode) {
                    BrowserUa.Mode.Mobile -> BrowserUa.Mode.Desktop
                    BrowserUa.Mode.Desktop -> BrowserUa.Mode.Mobile
                }
                chromePrefs.uaMode = uaMode
                // SPEC-119: apply UA and reload immediately.
                hostRef?.applyUserAgent(uaMode)
                hostRef?.reload()
                showSnack("已切换为${uaMode.label} UA")
            },
            webDarkMode = webDarkMode,
            onWebDarkModeChange = { mode ->
                webDarkMode = mode
                chromePrefs.webContentDarkMode = mode
                hostRef?.applyWebContentDarkMode(mode)
                showSnack(
                    when (mode) {
                        BrowserChromePrefs.WEB_DARK_OFF -> "网页背景：浅色"
                        BrowserChromePrefs.WEB_DARK_ON -> "网页背景：深色"
                        else -> "网页背景：跟随系统"
                    },
                )
            },
            eyeProtectionEnabled = themeSettings.eyeProtectionEnabled,
            onToggleEyeProtection = {
                val next = !themeSettings.eyeProtectionEnabled
                scope.launch {
                    themeSettingsGateway.update { it.copy(eyeProtectionEnabled = next) }
                }
                showSnack(if (next) "已开启护眼模式" else "已关闭护眼模式")
            },
            onOpenEyeProtectionSettings = { showEyeConfigSheet = true },
            textZoomPercent = textZoomPercent,
            onOpenTextZoom = { showTextZoomSheet = true },
            onFindInPage = {
                showFindInPage = true
                findQuery = ""
                findMatchLabel = ""
            },
            videoCapsuleAvailable = showVideoPlayerPrompt,
            onOpenVideoCapsule = {
                val offer = videoOffer
                if (offer != null &&
                    BrowserVideoOfferBus.belongsToPage(offer, currentPageForVideo)
                ) {
                    dismissedVideoPromptUrl = null
                    phoneChromeVisible = true
                    context.toastOnUi("已显示净视胶囊")
                } else {
                    context.toastOnUi("当前页未检测到可播放视频")
                }
            },
            onEnterFullscreen = {
                browserFullscreen = true
                phoneChromeVisible = false
            },
            onTranslatePage = { runInPageTranslate(forceTranslate = false) },
            onOpenTranslationSettings = { openTranslationSettings() },
            pageTranslated = pageTranslated,
        ),
    )

    BrowserTextZoomSheet(
        show = showTextZoomSheet,
        percent = textZoomPercent,
        onDismiss = { showTextZoomSheet = false },
        onPercentChange = { zoom ->
            textZoomPercent = zoom
            chromePrefs.textZoomPercent = zoom
            hostRef?.applyTextZoom(zoom)
        },
        forcePageZoom = chromePrefs.forcePageZoom,
        onForcePageZoomChange = { enabled ->
            chromePrefs.forcePageZoom = enabled
            hostRef?.applyForcePageZoom(enabled)
        },
    )

    EyeProtectionConfigSheet(
        show = showEyeConfigSheet,
        enabled = themeSettings.eyeProtectionEnabled,
        intensity = themeSettings.colorTemperature,
        autoNight = themeSettings.eyeProtectionAutoNight,
        schedule = themeSettings.eyeProtectionSchedule,
        startTime = themeSettings.eyeProtectionStartTime,
        endTime = themeSettings.eyeProtectionEndTime,
        onDismissRequest = { showEyeConfigSheet = false },
        onEnabledChange = { value ->
            scope.launch {
                themeSettingsGateway.update { it.copy(eyeProtectionEnabled = value) }
            }
        },
        onIntensityChange = { value ->
            scope.launch {
                themeSettingsGateway.update { it.copy(colorTemperature = value) }
            }
        },
        onAutoNightChange = { value ->
            scope.launch {
                themeSettingsGateway.update { it.copy(eyeProtectionAutoNight = value) }
            }
        },
        onScheduleChange = { value ->
            scope.launch {
                themeSettingsGateway.update { it.copy(eyeProtectionSchedule = value) }
            }
        },
        onStartTimeChange = { value ->
            scope.launch {
                themeSettingsGateway.update { it.copy(eyeProtectionStartTime = value) }
            }
        },
        onEndTimeChange = { value ->
            scope.launch {
                themeSettingsGateway.update { it.copy(eyeProtectionEndTime = value) }
            }
        },
    )

    if (showModePicker) {
        val analyze = pendingAnalyze
        AlertDialog(
            onDismissRequest = { showModePicker = false },
            title = { Text("选择净读方式") },
            text = {
                Text(
                    "未能自动判断页面类型" +
                        (analyze?.let { "（文字约 ${it.textLen}，图片 ${it.imgCount}）" } ?: "") +
                        "，请手动选择：",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showModePicker = false
                        val pageUrl = clientRef?.lastUrl.orEmpty().ifBlank { address.text }
                        if (analyze != null) {
                            startExtract { openMangaReader(pageUrl, analyze) }
                        } else {
                            openReaderView(WebMangaBookFactory.Kind.Manga)
                        }
                    },
                ) { Text("漫画净读") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showModePicker = false }) { Text("取消") }
                    TextButton(
                        onClick = {
                            showModePicker = false
                            val pageUrl = clientRef?.lastUrl.orEmpty().ifBlank { address.text }
                            startExtract { openNovelReader(pageUrl, analyze?.title) }
                        },
                    ) { Text("小说净读") }
                }
            },
        )
    }

}

/** SPEC-094: short status above bottom chrome, 24dp corners. */
@Composable
private fun ChromeSnackBar(message: String) {
    val scheme = LegadoTheme.colorScheme
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.inverseSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        color = scheme.inverseOnSurface,
        fontSize = 14.sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhoneWebChromeIcon(
    icon: MaterialSymbol,
    description: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    menu: (@Composable () -> Unit)? = null,
) {
    val scheme = LegadoTheme.colorScheme
    val slot = TabletBrowserLayout.PhoneWebIconSlotDp.dp
    Box(
        modifier = Modifier.size(slot),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(slot)
                // SPEC-199: 涟漪随 32dp 图标槽圆形（替代 SPEC-198 统一 24dp）。
                .clip(CircleShape)
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    } else {
                        Modifier.clickable(onClick = onClick)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            SymbolIcon(
                icon,
                contentDescription = description,
                tint = scheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        menu?.invoke()
    }
}

/**
 * SPEC-149: 阅读提取加载胶囊——无遮罩；胶囊本体吞掉点击（不透传网页），取消键可用。
 * SPEC-259: [label] 净读「加载中」/ 页内翻译「翻译中」。
 */
@Composable
private fun ReaderLoadingCapsule(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "加载中",
) {
    val scheme = LegadoTheme.colorScheme
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(ContinuousCapsule)
            .background(scheme.surfaceContainerHigh, ContinuousCapsule)
            .clickable(onClick = onCancel),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppCircularProgressIndicator(
            modifier = Modifier
                .padding(start = 14.dp)
                .size(18.dp),
            strokeWidth = 2.dp,
        )
        Text(
            label,
            color = scheme.onSurface,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
            SymbolIcon(
                Symbols.Close,
                contentDescription = "取消",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * SPEC-217: collapsed 净视 ball — left expands full capsule, right plays.
 */
@Composable
private fun VideoPlayerCollapsedBall(
    onExpand: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = LegadoTheme.colorScheme
    val motionPlaySymbol = remember { MaterialSymbol("motion_play", autoMirror = false) }
    Row(
        modifier = modifier
            .clip(ContinuousCapsule)
            .background(scheme.surface)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconButton(onClick = onExpand, modifier = Modifier.size(40.dp)) {
            SymbolIcon(
                Symbols.ArrowBack,
                contentDescription = "展开净视胶囊",
                tint = scheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = onPlay, modifier = Modifier.size(40.dp)) {
            SymbolIcon(
                motionPlaySymbol,
                contentDescription = "播放",
                tint = scheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * SPEC-135/136: mini capsule (~75% width) — Play / Audio / PiP / Close.
 * Icons enlarged (32/28); capsule width unchanged.
 */
@Composable
private fun VideoPlayerPromptCapsule(
    onPlay: () -> Unit,
    onAudioOnly: () -> Unit,
    onPip: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** SPEC-136: phone ~0.75; tablet corner can be narrower. */
    widthFraction: Float = 0.75f,
    /**
     * SPEC-201: 紧凑高度——按钮 48→44dp、上下留白 8→6dp（高 64→56dp）。
     * 手机横屏（操作者指定降高）与平板（0.315 宽下多视频 5 键防溢出）启用。
     */
    compact: Boolean = false,
    /** SPEC-145 R6: 页面检测到多个视频时显示列表键，菜单宽度与胶囊一致。 */
    showPlaylist: Boolean = false,
    playlist: List<BrowserVideoOffer> = emptyList(),
    onPlayOffer: (BrowserVideoOffer) -> Unit = {},
) {
    val scheme = LegadoTheme.colorScheme
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val capsuleWidth = screenWidth * widthFraction
    val pipSymbol = remember { MaterialSymbol("picture_in_picture_alt", autoMirror = false) }
    // SPEC-145 R6: 多视频列表菜单。
    var playlistMenuOpen by remember { mutableStateOf(false) }
    val playlistSymbol = remember { MaterialSymbol("playlist_play", autoMirror = false) }
    // SPEC-147 R7: 播放图标换 Motion Play，颜色保持主题 primary。
    val motionPlaySymbol = remember { MaterialSymbol("motion_play", autoMirror = false) }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .width(capsuleWidth)
                .clip(ContinuousCapsule)
            // SPEC-215 A: 与网页顶栏同色 surface。
            .background(scheme.surface)
                // SPEC-201: compact 高 56dp（原 64dp）。
                .padding(horizontal = 10.dp, vertical = if (compact) 6.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(
                onClick = onPlay,
                // SPEC-201: compact 按钮 44dp（原最小触控 48dp）。
                modifier = Modifier.size(if (compact) 44.dp else 48.dp),
            ) {
                SymbolIcon(
                    motionPlaySymbol,
                    contentDescription = "播放",
                    tint = scheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
            IconButton(
                onClick = onAudioOnly,
                modifier = Modifier.size(if (compact) 44.dp else 48.dp),
            ) {
                SymbolIcon(
                    Symbols.Headphones,
                    contentDescription = "听音频",
                    tint = scheme.onSurface,
                    modifier = Modifier.size(28.dp),
                )
            }
            IconButton(
                onClick = onPip,
                modifier = Modifier.size(if (compact) 44.dp else 48.dp),
            ) {
                SymbolIcon(
                    pipSymbol,
                    contentDescription = "画中画",
                    tint = scheme.onSurface,
                    modifier = Modifier.size(28.dp),
                )
            }
            // SPEC-145 R6: 多视频列表键（菜单现锚定胶囊底边中点，见下方）。
            if (showPlaylist && playlist.size > 1) {
                IconButton(
                    onClick = { playlistMenuOpen = true },
                    modifier = Modifier.size(if (compact) 44.dp else 48.dp),
                ) {
                    SymbolIcon(
                        playlistSymbol,
                        contentDescription = "视频列表",
                        tint = scheme.onSurface,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(if (compact) 44.dp else 48.dp),
            ) {
                SymbolIcon(
                    Symbols.Close,
                    contentDescription = "关闭",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        // SPEC-147 R5: 列表菜单锚定胶囊底边中点（隐形零宽 anchor），x 偏移 -半胶囊宽，
        // 展开后水平居中、左右边缘与胶囊对齐；宽度仍=胶囊宽。
        if (showPlaylist && playlist.size > 1) {
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                DropdownMenu(
                    expanded = playlistMenuOpen,
                    onDismissRequest = { playlistMenuOpen = false },
                    offset = DpOffset(-capsuleWidth / 2, 0.dp),
                    modifier = Modifier.width(capsuleWidth),
                    // SPEC-147 R4: M3 Expressive 圆角。
                    shape = RoundedCornerShape(24.dp),
                ) {
                    playlist.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = item.title.ifBlank {
                                        Uri.parse(item.url).host ?: item.url
                                    },
                                    color = scheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            onClick = {
                                playlistMenuOpen = false
                                onPlayOffer(item)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** SPEC-066: bottom-chrome download notice (~5s), right action opens downloads. */
@Composable
private fun DownloadBannerBar(
    fileName: String,
    onOpenDownloads: () -> Unit,
) {
    val scheme = LegadoTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolIcon(
            Symbols.Download,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "已开始下载",
                color = scheme.onSurface,
                fontSize = 14.sp,
                maxLines = 1,
            )
            Text(
                fileName,
                color = scheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onOpenDownloads) {
            Text("查看")
        }
    }
}

@Composable
private fun BrowserChromeIconButton(
    symbol: MaterialSymbol,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = LegadoTheme.colorScheme.onSurface,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        SymbolIcon(
            symbol = symbol,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = tint,
        )
    }
}

/** SPEC-119: Chrome-style find-in-page bar above the bottom chrome. */
@Composable
private fun BrowserFindInPageBar(
    query: String,
    matchLabel: String,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit,
) {
    val scheme = LegadoTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = scheme.onSurface, fontSize = 15.sp),
            cursorBrush = SolidColor(scheme.primary),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("页内查找", color = scheme.onSurfaceVariant, fontSize = 15.sp)
                }
                inner()
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onNext() }),
        )
        if (matchLabel.isNotEmpty()) {
            Text(
                matchLabel,
                color = scheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        IconButton(onClick = onPrev) {
            SymbolIcon(Symbols.KeyboardArrowUp, contentDescription = "上一个")
        }
        IconButton(onClick = onNext) {
            SymbolIcon(Symbols.KeyboardArrowDown, contentDescription = "下一个")
        }
        IconButton(onClick = onClose) {
            SymbolIcon(Symbols.Close, contentDescription = "关闭")
        }
    }
}

/** SPEC-131: resolve Activity from Compose LocalContext (may be wrapped). */
private tailrec fun Context.findActivityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityOrNull()
    else -> null
}

