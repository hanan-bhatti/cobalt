package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val LocalCodeContinuation = staticCompositionLocalOf { false to false }

/** Wrapping is deliberate on phones. Source spaces/tabs are never trimmed or collapsed. */
@Composable
internal fun MarkdownCodeBlock(code: AnnotatedString) {
    val (before, after) = LocalCodeContinuation.current
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(top = if (before) 0.dp else 4.dp, bottom = if (after) 0.dp else 4.dp)
        .background(colors.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(
            topStart = if (before) 0.dp else 8.dp, topEnd = if (before) 0.dp else 8.dp,
            bottomStart = if (after) 0.dp else 8.dp, bottomEnd = if (after) 0.dp else 8.dp))
        .padding(start = 10.dp, end = 10.dp, top = if (before) 0.dp else 10.dp, bottom = if (after) 0.dp else 10.dp)) {
        Text(code,
            modifier = Modifier.testTag("markdown_code"), color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace,
                fontSize = 13.sp, lineHeight = 19.sp, textDirection = TextDirection.Ltr))
    }
}

internal fun openMarkdownLink(context: android.content.Context, url: String, dark: Boolean) {
    if (url.startsWith("mailto:", ignoreCase = true)) {
        try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse(url))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(context, "No email app is available", android.widget.Toast.LENGTH_SHORT).show()
        }
    } else com.example.util.CobaltCustomTabsLauncher.launch(context, url, isDarkTheme = dark)
}
