package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.example.R
import com.example.data.local.AppDatabase
import com.example.media.recommendation.ScreeningReadModel
import com.example.widget.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Native counterpart of Artwork A. Layout and controls render before optional artwork arrives. */
@Composable
fun ScreeningCardPreview(entry: ScreeningWidgetEntry, busy: Boolean = false, onOpen: () -> Unit, feedbackOrigin: String = "Review screen") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = remember(context) { ScreeningReadModel.get(context) }
    val state by model.state.collectAsState()
    val display = remember(entry, state) { if (state.ready) state.preview(entry) else entry }
    var voting by remember { mutableStateOf(false) }
    var artwork by remember(entry.pick?.artwork) { mutableStateOf(ScreeningArtworkCache.peek(entry.pick?.artwork)) }
    LaunchedEffect(entry.pick?.artwork) { artwork = ScreeningArtworkCache.load(context, entry.pick?.artwork) }
    val image = remember(artwork) { artwork?.asImageBitmap() }
    val colors = MaterialTheme.colorScheme
    val fontScale = LocalDensity.current.fontScale
    val ink = if (image != null) Color.White else colors.onSurface
    val secondary = if (image != null) Color(0xDDFFFFFF) else colors.onSurfaceVariant
    val feedbackInk = Color(0xFF191A21)
    val accent = if (image == null || ColorUtils.calculateContrast(colors.primary.toArgb(), feedbackInk.toArgb()) >= 4.5)
        colors.primary else Color(ColorUtils.blendARGB(colors.primary.toArgb(), Color.White.toArgb(), .55f))

    fun vote(choice: ScreeningThumb) {
        val pick = display.pick ?: return
        if (busy || voting) return
        voting = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val item = AppDatabase.getDatabase(context).savedItemDao().getItemById(pick.itemId)
                    if (item != null && !item.isDeleted && !item.isArchived && !item.isUnavailable &&
                        ScreeningWidgetStore(context).togglePreference(pick.itemId, pick.key, choice, feedbackOrigin)) {
                        ScreeningWidgetProvider.showSaved(context)
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { Toast.makeText(context, "Couldn't save your preference. Try again.", Toast.LENGTH_SHORT).show() }
            finally { voting = false }
        }
    }

    BoxWithConstraints(Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(ScreeningRenderer.WIDTH / ScreeningRenderer.HEIGHT)
        .clip(RoundedCornerShape(28.dp)).background(colors.surfaceContainer)) {
        val cardWidth = maxWidth
        val titleSize = (((if (cardWidth < 300.dp) 23f else 27f) * fontScale.coerceIn(1f, 1.4f)).coerceAtMost(32f) / fontScale).sp
        val metadataSize = ((11f * fontScale.coerceIn(1f, 1.4f)).coerceAtMost(16f) / fontScale).sp
        image?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.4f to Color.Transparent, .61f to Color(0x44000000), 1f to Color(0xED101116))))
        }
        val title = display.pick?.title ?: when (display.config.source) {
            ScreeningSource.ALL -> "Your next watch"
            ScreeningSource.MOVIES -> "Your next movie"
            ScreeningSource.SHOWS -> "Your next show"
            ScreeningSource.ANIME -> "Your next anime"
            ScreeningSource.MANGA -> "Your next read"
        }
        val copyWidth = (cardWidth - 32.dp - if (display.pick != null) 124.dp else 0.dp).coerceAtLeast(1.dp)
        Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 16.dp).width(copyWidth).height(54.dp)) {
            Box(Modifier.height(34.dp).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Text(title, color = ink, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium,
                    fontSize = titleSize), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.height(16.dp).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Text(display.pick?.metadata ?: "Save titles in Media to get started", color = secondary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = metadataSize), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.fillMaxSize().clickable(enabled = !busy, onClick = onOpen).semantics {
            contentDescription = display.pick?.let { "${it.title}. ${it.reason}. View details." } ?: "Open Media in Cobalt"
            role = Role.Button
        })
        display.pick?.let { pick ->
            Row(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp).size(108.dp, 56.dp)
                .clip(RoundedCornerShape(28.dp)).background(if (image != null) Color(0xFA191A21) else colors.surfaceContainerHighest)
                .border(1.dp, if (image != null) Color(0x667F8396) else colors.outlineVariant, RoundedCornerShape(28.dp)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(ScreeningThumb.LIKED to R.drawable.ic_custom_helpful, ScreeningThumb.DISLIKED to R.drawable.ic_custom_unhelpful).forEach { (choice, res) ->
                    val selected = display.thumb == choice
                    Box(Modifier.size(48.dp).clip(CircleShape).background(if (selected) accent else Color.Transparent)
                        .clickable(enabled = !busy && !voting, onClick = { vote(choice) }).semantics {
                            contentDescription = "${if (choice == ScreeningThumb.LIKED) "Like" else "Not for me"} ${pick.title}" +
                                if (selected) ". Selected. Tap again to clear." else "."
                            role = Role.Button
                        }, contentAlignment = Alignment.Center) {
                        Icon(painterResource(res), null, Modifier.size(22.dp), tint = if (selected) {
                            if (image != null) feedbackInk else colors.onPrimary
                        } else if (image != null) accent else colors.onSurfaceVariant)
                    }
                }
            }
            pick.playbackProgress?.takeIf { it.isFinite() && it > 0 && it < 100 }?.let { progress ->
                Box(Modifier.align(Alignment.BottomStart).padding(start = 28.dp)
                    .width((cardWidth - 56.dp) * (progress / 100).toFloat()).height(3.dp).background(accent))
            }
        }
    }
}
