package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R

/** Compact variants of the existing Settings rows used by the approved Settings screens. */
@Composable
fun SettingsGroupLabel(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsIconBadge(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    iconColor: Color? = null
) {
    val accent = settingsIconColors(icon)
    Box(
        modifier = modifier.size(40.dp).background(containerColor ?: accent.first, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = iconColor ?: accent.second, modifier = Modifier.size(24.dp))
    }
}

data class SettingsChoice(
    val key: String,
    val label: String,
    val description: String? = null,
    @param:DrawableRes val icon: Int? = null,
    val fontFamily: FontFamily? = null
)

/** Keeps native bottom-sheet dismissal and predictive Back under Material 3's control. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsChoiceSheet(
    title: String,
    description: String,
    choices: List<SettingsChoice>,
    selectedKey: String,
    onSelectionChanged: (String) -> Unit,
    onDismiss: () -> Unit
) {
    SettingsBottomSheet(title, description, onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)
        ) {
            items(choices, key = { it.key }) { choice ->
                val isSelected = choice.key == selectedKey
                val foreground = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                Surface(
                    onClick = {
                        onSelectionChanged(choice.key)
                        onDismiss()
                    },
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().semantics { selected = isSelected }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        choice.icon?.let {
                            Icon(painterResource(it), contentDescription = null, tint = foreground, modifier = Modifier.size(24.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                choice.label,
                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = choice.fontFamily ?: MaterialTheme.typography.titleLarge.fontFamily),
                                color = foreground
                            )
                            choice.description?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = choice.fontFamily ?: MaterialTheme.typography.bodyMedium.fontFamily),
                                    color = if (isSelected) foreground else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (isSelected) {
                            Icon(painterResource(R.drawable.ic_custom_check), contentDescription = null, tint = foreground, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    title: String,
    description: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(painterResource(R.drawable.ic_custom_close), stringResource(R.string.settings_close_sheet), modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/** Stable per-glyph accents, adapted to the actual app surface and wallpaper/theme colors. */
@Composable
fun settingsIconColors(icon: Int): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    val dark = androidx.core.graphics.ColorUtils.calculateLuminance(scheme.background.toArgb()) < 0.5
    val hue = ((icon.toLong() * 137.508) % 360).toFloat()
    fun hsl(saturation: Float, lightness: Float) = Color(androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness)))
    val background = androidx.compose.ui.graphics.lerp(hsl(if (dark) 0.42f else 0.65f, if (dark) 0.18f else 0.92f), scheme.primaryContainer, 0.14f)
    var foreground = androidx.compose.ui.graphics.lerp(hsl(0.64f, if (dark) 0.82f else 0.28f), scheme.primary, 0.12f)
    repeat(12) {
        if (androidx.core.graphics.ColorUtils.calculateContrast(foreground.toArgb(), background.toArgb()) < 4.5)
            foreground = androidx.compose.ui.graphics.lerp(foreground, if (dark) Color.White else Color.Black, 0.15f)
    }
    return background to foreground
}
