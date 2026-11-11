package io.legado.app.ui.config.ai

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.model.ai.AiUsageLedger
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** SPEC-262: local AI token usage summary. */
@Composable
fun AiUsageRouteScreen(onBackClick: () -> Unit) {
    AiUsageScreen(onBackClick = onBackClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiUsageScreen(onBackClick: () -> Unit) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val snap = remember { AiUsageLedger.snapshot() }
    val updated = if (snap.updatedAt > 0L) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(snap.updatedAt))
    } else {
        "—"
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.ai_usage),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp,
            ),
        ) {
            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_usage)) {
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_usage_total),
                        description = snap.totalTokens.toString(),
                        onClick = {},
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_usage_prompt),
                        description = snap.promptTokens.toString(),
                        onClick = {},
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_usage_completion),
                        description = snap.completionTokens.toString(),
                        onClick = {},
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_usage_requests),
                        description = snap.requestCount.toString(),
                        onClick = {},
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_usage_estimated_requests),
                        description = snap.estimatedRequestCount.toString(),
                        onClick = {},
                    )
                    ClickableSettingItem(
                        title = "上次更新",
                        description = updated,
                        onClick = {},
                    )
                }
            }
        }
    }
}
