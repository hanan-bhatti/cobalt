package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.AppVersionManager
import com.example.util.AppVersionTag

/**
 * Modern Material 3 Expressive Version Tag Badge.
 * Renders dynamic colors based on the release channel (BETA, NIGHTLY, ALPHA, STABLE, etc.).
 */
@Composable
fun AppVersionBadge(
    tag: AppVersionTag = AppVersionManager.currentTag,
    versionName: String? = null,
    showIcon: Boolean = false,
    fontSize: TextUnit = 10.sp,
    horizontalPadding: Dp = 8.dp,
    verticalPadding: Dp = 3.dp,
    cornerRadius: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val containerColor = tag.containerColor()
    val contentColor = tag.contentColor()

    val displayText = if (versionName != null) {
        "${tag.label} $versionName"
    } else {
        tag.label
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(cornerRadius),
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showIcon) {
                Icon(
                    imageVector = Icons.Default.Sell,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size((fontSize.value + 2).dp)
                )
            }
            Text(
                text = displayText,
                color = contentColor,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}
