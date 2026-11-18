package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.R

@Composable
internal fun WidgetSetupMenu(onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val configuration = LocalConfiguration.current
    val width = (configuration.screenWidthDp.dp - 24.dp).coerceIn(1.dp, 288.dp)
    val height = (configuration.screenHeightDp.dp * 0.6f).coerceAtLeast(1.dp)
    val margin = with(LocalDensity.current) { 12.dp.roundToPx() }
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    val position = remember(margin, gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val x = if (layoutDirection == LayoutDirection.Ltr) {
                    anchorBounds.right - popupContentSize.width
                } else anchorBounds.left
                val below = anchorBounds.bottom + gap
                val y = if (below + popupContentSize.height <= windowSize.height - margin) {
                    below
                } else anchorBounds.top - popupContentSize.height - gap
                return IntOffset(
                    x.coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)),
                    y.coerceIn(margin, (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin))
                )
            }
        }
    }
    Box {
        IconButton(onClick = { open = !open }) {
            Icon(painterResource(R.drawable.ic_custom_more_vert), "Widget options")
        }
        if (open) Popup(
            popupPositionProvider = position,
            onDismissRequest = { open = false },
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceContainerHigh,
                contentColor = colors.onSurface,
                shadowElevation = 8.dp,
                modifier = Modifier.width(width).semantics { paneTitle = "Widget options" }
            ) {
                Column(Modifier.heightIn(max = height).verticalScroll(rememberScrollState()).padding(8.dp)) {
                    Text(
                        "Widget options",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                    Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainerHigh) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable(role = Role.Button, onClickLabel = "Review removal") {
                                    open = false
                                    onRemove()
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(shape = RoundedCornerShape(12.dp), color = colors.errorContainer) {
                                Icon(
                                    painterResource(R.drawable.ic_widget_remove_setup), null,
                                    modifier = Modifier.padding(10.dp).size(22.dp),
                                    tint = colors.onErrorContainer
                                )
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Remove saved setup", style = MaterialTheme.typography.titleSmall, color = colors.error)
                                Text(
                                    "Clear this widget’s saved configuration",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
