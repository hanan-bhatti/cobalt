package com.example.media

import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.remote.MediaSearchResultItem

/** Capture and Side Dock share the same provider identities and locally stored metadata. */
internal fun MediaSearchResultItem.toSavedMedia(status: String = "Plan to Watch", folders: List<String> = listOf("Media")) = SavedItem(
    id = id, type = SavedItemType.MEDIA, title = title,
    content = if (traktId != null) "https://trakt.tv/${if (mediaType == "movie") "movies" else "shows"}/$traktId" else overview.orEmpty(),
    extractedText = overview, thumbnailPath = posterUrl, backdropUrl = backdropUrl, mediaType = mediaType,
    watchStatus = status, releaseYear = releaseYear, genres = genres, watchProviders = watchProviders,
    trailerUrl = trailerUrl, rating = rating, runtime = runtime, tagline = tagline, budget = budget,
    revenue = revenue, productionCompanies = productionCompanies, folders = folders,
    traktId = traktId, tmdbId = tmdbId, imdbId = imdbId, mediaFacts = mediaFacts
)
