package com.example.data.local

/** Only the personal state needed to upload the library; no note bodies or artwork. */
data class TraktLibraryRow(
    val id: String,
    val mediaType: String?,
    val traktId: Long?,
    val tmdbId: Int?,
    val imdbId: String?,
    val watchStatus: String?,
    val inWatchlist: Boolean,
    val userRating: Int?,
    val lastWatchedAt: Long?,
    val timestamp: Long,
    val showProgressJson: String?
)
