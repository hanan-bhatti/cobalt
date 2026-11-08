package com.example.media

import com.example.data.model.*

internal fun SavedItem.mediaSynopsis(): String = sequenceOf(extractedText, content)
    .mapNotNull { it?.trim()?.takeIf { value -> value.isNotBlank() && !value.startsWith("https://") && !value.startsWith("http://") } }
    .firstOrNull().orEmpty()

/** A watched snapshot owns completion flags, while the local catalogue owns episode details. */
internal fun mergeWatchedSnapshot(local: TvShowProgress?, remote: TvShowProgress): TvShowProgress {
    if (local == null) return remote
    val seasons = (local.seasons.map { it.seasonNumber } + remote.seasons.map { it.seasonNumber }).distinct().sorted().map { number ->
        val old = local.seasons.firstOrNull { it.seasonNumber == number }
        val fresh = remote.seasons.firstOrNull { it.seasonNumber == number }
        val episodes = (old?.episodes.orEmpty().map { it.episodeNumber } + fresh?.episodes.orEmpty().map { it.episodeNumber }).distinct().sorted().map { episode ->
            val metadata = old?.episodes?.firstOrNull { it.episodeNumber == episode }
            val watched = fresh?.episodes?.firstOrNull { it.episodeNumber == episode }
            (metadata ?: watched ?: TvEpisodeProgress(episode, number)).copy(isWatched = watched?.isWatched == true,
                plays = watched?.plays ?: 0, lastWatchedAt = watched?.lastWatchedAt)
        }
        (old ?: fresh ?: TvSeasonProgress(number)).copy(totalEpisodes = maxOf(old?.totalEpisodes ?: 0, fresh?.totalEpisodes ?: 0),
            watchedEpisodes = fresh?.watchedEpisodes ?: 0, episodes = episodes)
    }
    return local.copy(totalAired = maxOf(local.totalAired, remote.totalAired), totalWatched = remote.totalWatched,
        completed = remote.completed, lastWatchedSeason = remote.lastWatchedSeason, lastWatchedEpisode = remote.lastWatchedEpisode,
        lastWatchedTitle = remote.lastWatchedTitle, nextEpisodeSeason = remote.nextEpisodeSeason,
        nextEpisodeNumber = remote.nextEpisodeNumber, nextEpisodeTitle = remote.nextEpisodeTitle,
        minutesWatched = remote.minutesWatched, minutesLeft = remote.minutesLeft, seasons = seasons,
        traktRatingPercent = remote.traktRatingPercent ?: local.traktRatingPercent, traktVotes = remote.traktVotes ?: local.traktVotes)
}
