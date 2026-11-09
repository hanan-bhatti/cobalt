package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.model.SavedItem
import com.example.data.remote.trakt.TraktDates

@Composable
internal fun MediaMoreInfoContent(item: SavedItem) {
    val context = LocalContext.current
    val progress = item.showProgress
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Additional information", style = MaterialTheme.typography.headlineSmall)
        item.tagline?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        val facts = listOfNotNull(
            progress?.premiered?.let { TraktDates.displayDate(it) }?.let { "Premiered" to it },
            (item.mediaFacts?.publicationStatus ?: item.status)?.takeIf { it.isNotBlank() }?.let { "Status" to it },
            item.mediaFacts?.episodeCount?.let { "Episodes" to it.toString() },
            item.mediaFacts?.chapterCount?.let { "Chapters" to it.toString() },
            item.mediaFacts?.volumeCount?.let { "Volumes" to it.toString() },
            progress?.ending?.let { "Ending" to it.replaceFirstChar { c -> c.titlecase() } }, item.runtime?.takeIf { it > 0 }?.let { "Runtime" to "$it min" },
            progress?.totalRuntimeFormatted?.let { "Total runtime" to it },
            progress?.network?.takeIf { it.isNotBlank() }?.let { "Network" to it },
            progress?.country?.takeIf { it.isNotBlank() }?.let { value -> "Country" to value.split(',').joinToString(", ") { codeStr ->
                val code = codeStr.trim().uppercase(java.util.Locale.ROOT)
                runCatching { java.util.Locale.Builder().setRegion(code).build().displayCountry }.getOrNull()?.takeIf { it.isNotBlank() } ?: codeStr.trim()
            } },
            progress?.language?.takeIf { it.isNotBlank() }?.let { "Language" to java.util.Locale.forLanguageTag(it).displayLanguage.ifBlank { it } },
            progress?.contentRating?.takeIf { it.isNotBlank() }?.let { "Age rating" to it },
            progress?.totalAired?.takeIf { item.mediaType == "tv" && it > 0 }?.let { "Episodes" to it.toString() },
            progress?.seasons?.count { it.seasonNumber > 0 }?.takeIf { item.mediaType == "tv" && it > 0 }?.let { "Seasons" to it.toString() })
        if (facts.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    facts.chunked(2).forEach { pair -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        pair.forEach { (label, value) -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, style = MaterialTheme.typography.bodyMedium)
                        } }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    } }
                }
            }
        }
        if (item.mediaFacts?.source == "Jikan" && item.rating?.let { it.isFinite() && it in 0.0..10.0 } == true) {
            Text("MyAnimeList rating", style = MaterialTheme.typography.titleMedium)
            Text("${String.format(java.util.Locale.US, "%.1f", item.rating)}/10", style = MaterialTheme.typography.bodyLarge)
        }
        if (progress != null) {
            val metacritic = progress.metacriticScore?.trim()?.substringBefore('/')?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..100.0 }
            if (progress.traktRatingPercent != null || progress.tmdbRating != null || externalRatingBadges(progress).isNotEmpty() || metacritic != null) {
                Text("Ratings", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    progress.traktRatingPercent?.let { SuggestionChip(onClick = {}, label = { Text("Trakt $it%") }) }
                    progress.tmdbRating?.let { SuggestionChip(onClick = {}, label = { Text("TMDB ${String.format(java.util.Locale.US, "%.1f", it)}/10") }) }
                    metacritic?.let { SuggestionChip(onClick = {}, label = { Text("Metacritic ${String.format(java.util.Locale.US, "%.0f", it)}/100") }) }
                }
                MediaRatingsRow(progress)
            }
            progress.traktVotes?.takeIf { it > 0 }?.let { Text("$it Trakt votes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (progress.ratingDistribution.values.any { it > 0 }) {
                val values = (1..10).map { progress.ratingDistribution[it.toString()] ?: 0 }
                val maximum = values.maxOrNull()?.coerceAtLeast(1) ?: 1
                val primary = MaterialTheme.colorScheme.primary
                Text("Trakt rating distribution", style = MaterialTheme.typography.labelLarge)
                Canvas(Modifier.fillMaxWidth().height(80.dp)) {
                    val unit = size.width / 10
                    values.forEachIndexed { i, value ->
                        val height = size.height * value / maximum
                        drawRoundRect(primary, Offset(i * unit + 3.dp.toPx(), size.height - height),
                            androidx.compose.ui.geometry.Size((unit - 6.dp.toPx()).coerceAtLeast(1f), height), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { (1..10).forEach { Text(it.toString(), style = MaterialTheme.typography.labelSmall) } }
            }
            if (progress.seasonRatings.size > 1) {
                Text("Quality across seasons", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    progress.seasonRatings.forEach { point -> SuggestionChip(onClick = {}, label = { Text("S${point.seasonNumber} · ${point.ratingPercent}%") }) }
                }
            }
            val stats = listOfNotNull(progress.plays?.let { "Plays" to it }, progress.watchers?.let { "Watchers" to it },
                progress.listsCount?.let { "Lists" to it }, progress.favoritesCount?.let { "Favorites" to it })
            if (stats.isNotEmpty()) {
                Text("Community", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stats.forEach { (label, value) -> Column { Text(value.toString(), style = MaterialTheme.typography.titleMedium); Text(label, style = MaterialTheme.typography.labelSmall) } }
                }
            }
            progress.creators.takeIf { it.isNotEmpty() }?.let { Text("Created by", style = MaterialTheme.typography.titleMedium); Text(it.joinToString(", ")) }
        }
        item.productionCompanies.takeIf { it.isNotEmpty() }?.let { Text("Production", style = MaterialTheme.typography.titleMedium); Text(it.joinToString(", ")) }
        item.budget?.takeIf { it > 0 }?.let { Text("Budget · ${java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US).format(it)}", style = MaterialTheme.typography.bodyMedium) }
        item.revenue?.takeIf { it > 0 }?.let { Text("Revenue · ${java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US).format(it)}", style = MaterialTheme.typography.bodyMedium) }
        val links = listOfNotNull(item.traktId?.let { "Trakt" to "https://trakt.tv/${if (item.mediaType == "tv") "shows" else "movies"}/$it" },
            item.tmdbId?.let { "TMDB" to "https://www.themoviedb.org/${item.mediaType}/$it" },
            item.imdbId?.let { "IMDb" to "https://www.imdb.com/title/$it/" },
            item.mediaFacts?.sourceUrl?.takeIf { it.startsWith("https://") }?.let { "MyAnimeList" to it },
            progress?.homepage?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { "Official website" to it })
        if (links.isNotEmpty()) {
            Text("More on the web", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { links.forEach { (label, url) ->
                OutlinedButton(onClick = { com.example.util.CobaltCustomTabsLauncher.launch(context, url) }) { Text(label) }
            } }
        }
        Spacer(Modifier.height(12.dp))
    }
}
