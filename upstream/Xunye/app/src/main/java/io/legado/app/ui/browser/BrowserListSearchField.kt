package io.legado.app.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.icon.SymbolIcon
import io.legado.app.ui.widget.components.icon.Symbols

/** SPEC-071: history-style capsule search used on history / bookmarks / downloads. */
@Composable
fun BrowserListSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    /** SPEC-263: when false, field ignores focus until [onActivate] unlocks it. */
    canFocus: Boolean = true,
    onActivate: (() -> Unit)? = null,
) {
    val scheme = LegadoTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(canFocus) {
        if (canFocus && onActivate != null) {
            focusRequester.requestFocus()
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = BrowserUiTokens.PagePaddingH,
                vertical = BrowserUiTokens.SpaceSm,
            )
            .height(BrowserUiTokens.InputHeightLarge)
            .clip(RoundedCornerShape(BrowserUiTokens.CardCorner))
            .background(scheme.surfaceContainer)
            .then(
                if (onActivate != null && !canFocus) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onActivate,
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolIcon(
            Symbols.Search,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .focusProperties { this.canFocus = canFocus },
            singleLine = true,
            textStyle = TextStyle(color = scheme.onSurface, fontSize = 16.sp),
            cursorBrush = SolidColor(BrowserUiTokens.Primary),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, color = scheme.onSurfaceVariant, fontSize = 14.sp)
                }
                inner()
            },
        )
    }
}
