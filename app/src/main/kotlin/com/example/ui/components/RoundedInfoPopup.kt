package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** Owns the rounded surface, rather than trying to clip a platform dropdown background. */
@Composable
fun RoundedInfoPopup(title: String, text: String, onDismiss: () -> Unit) {
    val margin = with(LocalDensity.current) { 12.dp.roundToPx() }
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    val configuration = LocalConfiguration.current
    val maxHeight = (configuration.screenHeightDp.dp * 0.6f).coerceAtMost(360.dp)
    val maxWidth = (configuration.screenWidthDp.dp - 24.dp).coerceAtLeast(1.dp).coerceAtMost(300.dp)
    val position = remember(margin, gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val preferredX = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - popupContentSize.width else anchorBounds.left
                val below = anchorBounds.bottom + gap
                val preferredY = if (below + popupContentSize.height <= windowSize.height - margin) below
                    else anchorBounds.top - popupContentSize.height - gap
                return IntOffset(
                    preferredX.coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)),
                    preferredY.coerceIn(margin, (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin))
                )
            }
        }
    }
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 8.dp,
            modifier = Modifier.widthIn(max = maxWidth).semantics { paneTitle = title }) {
            Text(text, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()).padding(20.dp))
        }
    }
}
