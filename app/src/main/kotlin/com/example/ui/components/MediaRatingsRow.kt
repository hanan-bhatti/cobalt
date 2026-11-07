package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.TvShowProgress
import java.util.Locale

internal data class ExternalRatingBadge(
    val source: String,
    val score: String,
    @param:DrawableRes val logo: Int,
    val wide: Boolean = false
)

/** These are source-specific scores, never the generic Trakt/TMDB community rating. */
internal fun externalRatingBadges(progress: TvShowProgress?): List<ExternalRatingBadge> {
    if (progress == null) return emptyList()
    fun score(raw: String?, maximum: Double, percent: Boolean): String? {
        val value = raw?.trim()?.removeSuffix("%")?.trim()?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it in 0.0..maximum } ?: return null
        return if (percent) "${String.format(Locale.US, "%.0f", value)}%"
        else "${String.format(Locale.US, "%.1f", value)}/10"
    }
    return buildList {
        score(progress.imdbRating, 10.0, false)?.let {
            add(ExternalRatingBadge("IMDb", it, R.drawable.ic_rating_imdb, wide = true))
        }
        score(progress.rottenTomatoesScore, 100.0, true)?.let {
            add(ExternalRatingBadge("Rotten Tomatoes critics", it, R.drawable.ic_rating_rotten_tomatoes))
        }
        score(progress.popcornScore, 100.0, true)?.let {
            add(ExternalRatingBadge("Popcornmeter audience", it, R.drawable.ic_rating_popcornmeter))
        }
    }
}

@Composable
internal fun MediaRatingsRow(progress: TvShowProgress?, modifier: Modifier = Modifier) {
    val badges = externalRatingBadges(progress)
    if (badges.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        badges.forEach { badge ->
            Row(Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                .semantics(mergeDescendants = true) { contentDescription = "${badge.source}: ${badge.score}" }
                .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Image deliberately avoids Icon's theme tint. Preserve supplied logo proportions.
                Image(painterResource(badge.logo), contentDescription = null,
                    modifier = Modifier.size(width = if (badge.wide) 36.dp else 19.dp, height = 18.dp))
                Text(badge.score, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
