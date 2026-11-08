package com.example.media

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.remote.*
import com.example.data.remote.trakt.*
import kotlinx.coroutines.*

/** Catalogue refresh is independent of account imports and never blocks displaying a saved title. */
internal object MediaDetailHydrator {
    @Volatile internal var tmdbProvider: (() -> TmdbApiService)? = null
    @Volatile internal var omdbProvider: (() -> OmdbApiService)? = null

    private suspend fun <T> optional(block: suspend () -> T): T? = try {
        withTimeoutOrNull(15_000) { block() }
    } catch (cancel: CancellationException) { throw cancel }
    catch (_: Exception) { null }

    suspend fun load(context: Context, item: SavedItem, saveToDb: Boolean, db: AppDatabase): SavedItem = withContext(Dispatchers.IO) {
        supervisorScope {
            val tv = item.mediaType == "tv"
            val auth = TraktAuthManager.getInstance(context)
            val epoch = auth.currentSessionEpoch()
            val connected = auth.hasValidToken()
            val key = com.example.util.SecretPreferences(context.getSharedPreferences("cobalt_prefs", 0), "cobalt_prefs").get("tmdb_api_key").orEmpty()
            val tmdb = tmdbProvider?.invoke() ?: MediaApiClient.tmdbApiService
            val api = TraktSyncEngine.getApiService()
            val old = item.showProgress ?: TvShowProgress()
            val tmdbId = item.tmdbId?.takeIf { it > 0 } ?: if (item.id.startsWith("tmdb_${item.mediaType}_")) item.id.substringAfterLast('_').toIntOrNull() else null
            val detailsJob = async { if (tmdbId != null && key.isNotBlank()) optional {
                if (tv) tmdb.getTvDetails(tmdbId, key, "videos,watch/providers,external_ids,credits,content_ratings")
                else tmdb.getMovieDetails(tmdbId, key, "videos,watch/providers,external_ids,credits")
            } else null }
            val identityJob = async {
                item.traktId?.takeIf { it > 0 } ?: if (connected && tmdbId != null) optional {
                    api.lookupTmdb(tmdbId, if (tv) "show" else "movie").firstOrNull {
                        val ids = if (tv) it.show?.ids else it.movie?.ids
                        ids?.tmdb == tmdbId
                    }?.let { if (tv) it.show?.ids?.trakt else it.movie?.ids?.trakt }
                } else null
            }
            val traktId = identityJob.await()
            val summaryJob = async { if (traktId != null) optional {
                if (tv) api.getShowSummary(traktId.toString()) else api.getMovieSummary(traktId.toString())
            } else null }
            val seasonsJob = async { if (tv && traktId != null) optional { api.getShowSeasons(traktId.toString()) } else null }
            val ratingsJob = async { if (traktId != null) optional { if (tv) api.getShowRatings(traktId.toString()) else api.getMovieCommunityRatings(traktId.toString()) } else null }
            val statsJob = async { if (traktId != null) optional { if (tv) api.getShowStats(traktId.toString()) else api.getMovieStats(traktId.toString()) } else null }
            val peopleJob = async { if (traktId != null) optional { if (tv) api.getShowPeople(traktId.toString()) else api.getMoviePeople(traktId.toString()) } else null }
            val watchedJob = async { if (tv && traktId != null && connected && auth.isSyncTvProgressEnabled())
                optional { api.getShowWatchedProgress(traktId.toString()) } else null }
            val details = detailsJob.await()
            val summary = summaryJob.await()
            val show = summary as? TraktShow
            val movie = summary as? TraktMovie
            val imdb = details?.externalIds?.imdbId?.takeIf { it.startsWith("tt") } ?: show?.ids?.imdb ?: movie?.ids?.imdb ?: item.imdbId
            val externalJob = async { if (imdb?.matches(Regex("tt\\d+")) == true) optional {
                (omdbProvider?.invoke() ?: MediaApiClient.omdbApiService).getByImdbId(imdb)
            } else null }
            val seasons = seasonsJob.await()
            val ratings = ratingsJob.await()
            val stats = statsJob.await()
            val people = peopleJob.await()
            val watched = watchedJob.await()
            val external = externalJob.await()?.takeIf { it.response.equals("True", true) }
            if (details == null && summary == null && seasons == null && ratings == null && stats == null && people == null && watched == null && external == null)
                return@supervisorScope item
            val headers = if (!seasons.isNullOrEmpty()) seasons.map { TvSeasonProgress(it.number, it.title,
                it.episode_count ?: it.aired_episodes ?: 0) } else details?.seasons.orEmpty().map {
                TvSeasonProgress(it.seasonNumber, it.name, it.episodeCount ?: 0)
            }
            fun withCatalogue(personal: TvShowProgress): TvShowProgress {
                val catalogue = if (headers.isEmpty()) personal.seasons else headers.map { header ->
                    val local = personal.seasons.firstOrNull { it.seasonNumber == header.seasonNumber }
                    header.copy(watchedEpisodes = local?.watchedEpisodes ?: 0, episodes = local?.episodes.orEmpty())
                }
                val cast = details?.credits?.cast?.take(40)?.map { MediaCastMember(it.name.orEmpty(), it.character,
                    it.profilePath?.let { p -> "https://image.tmdb.org/t/p/w185$p" }, it.order) }.orEmpty()
                    .ifEmpty { people?.cast.orEmpty().take(40).mapNotNull { credit -> credit.person?.let {
                        MediaCastMember(it.name, credit.character ?: credit.characters?.joinToString(", ")) } } }
                return personal.copy(seasons = catalogue,
                    totalAired = show?.aired_episodes ?: details?.numberOfEpisodes ?: personal.totalAired,
                    traktRatingPercent = ratings?.rating?.times(10)?.toInt() ?: (show?.rating ?: movie?.rating)?.times(10)?.toInt() ?: personal.traktRatingPercent,
                    traktVotes = ratings?.votes ?: show?.votes ?: movie?.votes ?: personal.traktVotes,
                    tmdbRating = details?.voteAverage ?: personal.tmdbRating, tmdbVotes = details?.voteCount ?: personal.tmdbVotes,
                    ratingDistribution = ratings?.distribution ?: personal.ratingDistribution,
                    seasonRatings = seasons?.filter { it.number > 0 && it.rating != null }?.map { SeasonQualityPoint(it.number, (it.rating!! * 10).toInt()) }
                        ?.takeIf { it.isNotEmpty() } ?: personal.seasonRatings,
                    plays = stats?.plays ?: personal.plays, watchers = stats?.watchers ?: personal.watchers,
                    listsCount = stats?.lists ?: personal.listsCount, favoritesCount = stats?.favorited ?: personal.favoritesCount,
                    network = show?.network ?: details?.networks?.mapNotNull { it.name }?.joinToString(", ")?.takeIf { it.isNotBlank() } ?: personal.network,
                    country = show?.country ?: movie?.country ?: details?.originCountry?.joinToString(", ") ?: personal.country,
                    language = show?.language ?: movie?.language ?: details?.originalLanguage ?: personal.language,
                    creators = details?.createdBy?.mapNotNull { it.name }?.takeIf { it.isNotEmpty() } ?: personal.creators,
                    studios = details?.productionCompanies?.mapNotNull { it.name }?.takeIf { it.isNotEmpty() } ?: personal.studios,
                    contentRating = show?.certification ?: movie?.certification ?: details?.contentRatings?.results?.firstOrNull { it.iso_3166_1 == "US" }?.rating ?: personal.contentRating,
                    premiered = show?.first_aired ?: movie?.released ?: details?.firstAirDate ?: details?.releaseDate ?: personal.premiered,
                    homepage = show?.homepage ?: movie?.homepage ?: details?.homepage ?: personal.homepage,
                    ending = show?.ending ?: personal.ending,
                    totalRuntimeFormatted = show?.total_runtime?.takeIf { it > 0 }?.let { "${it / 60}h ${it % 60}m" } ?: personal.totalRuntimeFormatted,
                    imdbRating = external?.imdbRating?.takeIf { it != "N/A" } ?: personal.imdbRating,
                    imdbVotes = external?.imdbVotes?.takeIf { it != "N/A" } ?: personal.imdbVotes,
                    rottenTomatoesScore = external?.ratings?.firstOrNull { it.source == "Rotten Tomatoes" }?.value ?: personal.rottenTomatoesScore,
                    metacriticScore = external?.ratings?.firstOrNull { it.source == "Metacritic" }?.value ?: personal.metacriticScore,
                    cast = cast.ifEmpty { personal.cast })
            }
            val country = details?.watchProviders?.results?.get("US") ?: details?.watchProviders?.results?.values?.firstOrNull()
            val providers = buildList {
                fun add(rows: List<TmdbWatchProviderItem>?, kind: String) { rows.orEmpty().forEach { p -> p.providerName?.let {
                    add(MediaWatchProvider(name = it, type = kind, logoUrl = p.logoPath?.let { logo -> "https://image.tmdb.org/t/p/w154$logo" }, url = country?.link, providerId = p.providerId))
                } } }
                add(country?.flatrate, "Stream"); add(country?.ads, "Stream with ads"); add(country?.rent, "Rent"); add(country?.buy, "Buy")
            }.ifEmpty { item.richWatchProviders }
            val synopsis = sequenceOf(details?.overview, show?.overview, movie?.overview).firstOrNull { !it.isNullOrBlank() } ?: item.extractedText
            val trailer = details?.videos?.results?.firstOrNull { it.site.equals("YouTube", true) && it.type.equals("Trailer", true) }?.key
                ?.let { "https://www.youtube.com/watch?v=$it" } ?: show?.trailer ?: movie?.trailer ?: item.trailerUrl
            val images = show?.images ?: movie?.images
            fun image(type: String) = images?.get(type)?.firstOrNull()?.let { if (it.startsWith("https://")) it else "https://$it" }
            val hydrated = item.copy(traktId = traktId ?: item.traktId, tmdbId = tmdbId ?: show?.ids?.tmdb ?: movie?.ids?.tmdb,
                imdbId = imdb, extractedText = synopsis,
                thumbnailPath = details?.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" } ?: image("poster") ?: item.thumbnailPath,
                backdropUrl = details?.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" } ?: image("fanart") ?: item.backdropUrl,
                trailerUrl = trailer, genres = details?.genres?.map { it.name }?.takeIf { it.isNotEmpty() } ?: show?.genres ?: movie?.genres ?: item.genres,
                rating = details?.voteAverage ?: show?.rating ?: movie?.rating ?: item.rating,
                runtime = details?.runtime ?: details?.episodeRunTime?.firstOrNull() ?: show?.runtime ?: movie?.runtime ?: item.runtime,
                tagline = details?.tagline ?: show?.tagline ?: movie?.tagline ?: item.tagline,
                status = details?.status ?: show?.status ?: movie?.status ?: item.status,
                budget = details?.budget ?: item.budget, revenue = details?.revenue ?: item.revenue,
                productionCompanies = details?.productionCompanies?.mapNotNull { it.name }?.takeIf { it.isNotEmpty() } ?: item.productionCompanies,
                richWatchProviders = providers, watchProviders = providers.map { it.name }, showProgress = withCatalogue(old))
            if (!saveToDb) return@supervisorScope hydrated
            var result = hydrated
            auth.runIfSessionValid(epoch) {
                db.withTransaction {
                    val current = db.savedItemDao().getItemById(item.id) ?: return@withTransaction
                    if (current.isDeleted || com.example.notes.NoteDeletionFence(context).deleted(item.id)) return@withTransaction
                    var personal = TvShowProgress.fromJson(current.showProgressJson) ?: TvShowProgress()
                    val pending = db.traktOutboxDao().pendingActions(auth.syncSessionId(), item.id)
                    if (watched != null && current.updatedAt == item.updatedAt && pending.none { it.startsWith("history:") }) {
                        personal = mergeWatchedSnapshot(personal, watched.toProgress())
                    }
                    val metadata = SavedItemEntity.fromDomainModel(hydrated.copy(showProgress = withCatalogue(personal)))
                    val refreshed = current.copy(traktId = metadata.traktId ?: current.traktId, tmdbId = metadata.tmdbId ?: current.tmdbId,
                        imdbId = metadata.imdbId ?: current.imdbId, extractedText = metadata.extractedText ?: current.extractedText,
                        thumbnailPath = metadata.thumbnailPath, backdropUrl = metadata.backdropUrl, trailerUrl = metadata.trailerUrl,
                        genresJson = metadata.genresJson, rating = metadata.rating, runtime = metadata.runtime, tagline = metadata.tagline,
                        status = metadata.status, budget = metadata.budget, revenue = metadata.revenue,
                        productionCompaniesJson = metadata.productionCompaniesJson, watchProvidersJson = metadata.watchProvidersJson,
                        showProgressJson = metadata.showProgressJson, updatedAt = System.currentTimeMillis())
                    db.savedItemDao().insertItem(refreshed)
                    result = refreshed.toDomainModel()
                }
            }
            result
        }
    }

    private fun TraktShowWatchedProgressResponse.toProgress() = TvShowProgress(totalAired = aired, totalWatched = completed,
        completed = aired > 0 && completed >= aired, lastWatchedSeason = last_episode?.season, lastWatchedEpisode = last_episode?.number,
        lastWatchedTitle = last_episode?.title, nextEpisodeSeason = next_episode?.season, nextEpisodeNumber = next_episode?.number,
        nextEpisodeTitle = next_episode?.title, minutesWatched = stats?.minutes_watched ?: 0, minutesLeft = stats?.minutes_left ?: 0,
        seasons = seasons.orEmpty().map { s -> TvSeasonProgress(s.number, s.title, s.aired, s.completed,
            s.episodes.orEmpty().map { e -> TvEpisodeProgress(e.number, s.number, isWatched = e.completed, lastWatchedAt = TraktDates.parse(e.last_watched_at)) }) })
}
