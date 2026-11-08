package com.example.media

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.local.AppDatabase
import com.example.data.model.SavedItemType
import com.example.data.remote.MediaApiClient
import com.example.data.remote.MediaSearchResultItem
import com.example.data.remote.trakt.*
import com.example.data.repository.CobaltRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Explicit opt-in device probes. These never run live-account writes in ordinary CI. */
@RunWith(AndroidJUnit4::class)
class MediaDeviceValidationTest {
    @Test fun runSelectedProbe(): Unit = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Requires explicit live media validation", arguments.getString("media_live") == "yes")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.getDatabase(context)
        val repo = CobaltRepository(context, db)
        val auth = TraktAuthManager.getInstance(context)
        val mode = arguments.getString("media_mode") ?: "inspect"
        val report = JSONObject().put("mode", mode).put("connected", auth.hasValidToken()).put("tmdbConfigured", repo.getTmdbApiKey().isNotBlank())
        val media = db.savedItemDao().getAllItems().filter { it.type == SavedItemType.MEDIA.name && !it.isDeleted }
        try {
            when (mode) {
                "inspect" -> report.put("titles", JSONArray(media.map { it.title })).put("count", media.size)
                "hydrate" -> {
                    val rows = JSONArray()
                    for (row in media.filter { it.mediaType in listOf("movie", "tv") }) {
                        val loaded = repo.enrichMediaItemDetails(row.toDomainModel())
                        val latest = db.savedItemDao().getItemById(row.id)!!.toDomainModel()
                        assertEquals(row.title, latest.title)
                        assertEquals(row.content, latest.content)
                        assertTrue("Synopsis missing for ${row.title}", latest.mediaSynopsis().isNotBlank())
                        assertNotNull("Runtime missing for ${row.title}", latest.runtime)
                        if (row.mediaType == "tv") assertTrue("Seasons missing for ${row.title}", latest.showProgress?.seasons?.isNotEmpty() == true)
                        rows.put(JSONObject().put("title", loaded.title).put("seasons", JSONArray(latest.showProgress?.seasons.orEmpty().map { it.seasonNumber }))
                            .put("cast", latest.showProgress?.cast?.size ?: 0).put("imdb", latest.showProgress?.imdbRating).put("runtime", latest.runtime)
                            .put("synopsisLength", latest.mediaSynopsis().length).put("trailer", !latest.trailerUrl.isNullOrBlank()))
                    }
                    report.put("rows", rows)
                }
                "capture" -> {
                    assertTrue(repo.getTmdbApiKey().isNotBlank())
                    val details = MediaApiClient.tmdbApiService.getMovieDetails(157336, repo.getTmdbApiKey())
                    val movie = MediaSearchResultItem("tmdb_movie_157336", details.title.orEmpty(), "movie", tmdbId = 157336,
                        overview = details.overview, releaseYear = details.releaseDate?.take(4),
                        posterUrl = details.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }).toSavedMedia()
                    val saved = repo.saveNewMediaItem(movie)
                    val duplicate = repo.saveNewMediaItem(movie)
                    assertEquals(saved.id, duplicate.id)
                    TraktOutbox.drain(context, TraktSyncEngine.getApiService())
                    val loaded = repo.enrichMediaItemDetails(saved)
                    assertNotNull("TMDB movie must link with Trakt", loaded.traktId)
                    assertTrue(loaded.mediaSynopsis().isNotBlank())
                    assertTrue(loaded.showProgress?.cast?.isNotEmpty() == true)
                    report.put("movie", loaded.title).put("movieId", loaded.id).put("duplicatePreserved", true)
                    val settings = MediaProviderSettings(context)
                    val mangaBefore = settings.mangaEnabled
                    val jikanBefore = settings.enabled("jikan")
                    val urlBefore = settings.jikanUrl
                    try {
                        settings.mangaEnabled = true
                        settings.setEnabled("jikan", true)
                        arguments.getString("jikan_url")?.let { settings.jikanUrl = it }
                        val results = repo.searchMedia("Berserk") { report.put("searchWarning", it) }
                        val manga = results.firstOrNull { it.mediaType == "manga" && it.id.startsWith("jikan_manga_") }
                            ?: error("Jikan returned no Berserk manga; ${report.optString("searchWarning")}")
                        val book = repo.saveNewMediaItem(manga.toSavedMedia(status = "Watching"))
                        assertEquals(book.id, repo.saveNewMediaItem(manga.toSavedMedia()).id)
                        assertEquals("Reading", mediaStatusLabel(book.watchStatus, book.mediaType))
                        assertNotNull(book.mediaFacts)
                        assertTrue(book.thumbnailPath?.startsWith("https://") == true)
                        assertFalse(db.traktOutboxDao().hasPending(auth.syncSessionId(), book.id))
                        report.put("manga", book.title).put("mangaId", book.id).put("mangaChapters", book.mediaFacts?.chapterCount)
                    } finally { settings.mangaEnabled = mangaBefore; settings.setEnabled("jikan", jikanBefore); settings.jikanUrl = urlBefore }
                }
                "local", "local_offline" -> {
                    val settings = MediaProviderSettings(context)
                    val urlBefore = settings.jikanUrl
                    val mangaBefore = settings.mangaEnabled
                    val enabledBefore = listOf("trakt", "tmdb", "jikan").associateWith { settings.enabled(it) }
                    try {
                        settings.jikanUrl = requireNotNull(arguments.getString("jikan_url"))
                        settings.mangaEnabled = true
                        settings.setEnabled("trakt", false); settings.setEnabled("tmdb", false); settings.setEnabled("jikan", true)
                        var warning: String? = null
                        val results = repo.searchMedia("Berserk") { warning = it }
                        val manga = results.first { it.mediaType == "manga" }
                        val saved = repo.saveNewMediaItem(manga.toSavedMedia(status = "Watching"))
                        assertTrue(saved.mediaFacts?.sourceUrl?.contains("myanimelist.net") == true)
                        assertTrue(saved.thumbnailPath?.startsWith("https://") == true)
                        assertTrue(saved.mediaSynopsis().isNotBlank())
                        assertFalse(db.traktOutboxDao().hasPending(auth.syncSessionId(), saved.id))
                        if (mode == "local_offline") {
                            assertNotNull("Stopped server must report failure while keeping cached results", warning)
                            report.put("cachedMangaAvailable", true).put("failureShown", warning)
                        } else {
                            assertNull("Local server search failed", warning)
                            val anime = repo.searchMedia("Cowboy Bebop").first { it.mediaType == "anime" }
                            val savedAnime = repo.saveNewMediaItem(anime.toSavedMedia())
                            assertEquals(26, savedAnime.mediaFacts?.episodeCount)
                            assertTrue(savedAnime.thumbnailPath?.startsWith("https://") == true)
                            report.put("anime", savedAnime.title).put("animeId", savedAnime.id)
                            settings.setEnabled("jikan", false)
                            assertTrue(repo.searchMedia("Berserk").any { it.id == saved.id })
                            report.put("disabledSourceKeepsSavedTitles", true)
                        }
                        report.put("manga", saved.title).put("mangaId", saved.id).put("readingStatus", mediaStatusLabel(saved.watchStatus, saved.mediaType))
                            .put("endpoint", settings.jikanUrl)
                    } finally {
                        settings.jikanUrl = urlBefore; settings.mangaEnabled = mangaBefore
                        enabledBefore.forEach { (source, enabled) -> settings.setEnabled(source, enabled) }
                    }
                }
                "remote" -> {
                    assertTrue(auth.hasValidToken())
                    val api = TraktSyncEngine.getApiService()
                    val dark = api.getShowSummary("dark")
                    api.addToWatchlist(TraktSyncPayload(shows = listOf(TraktSyncShowPayload(TraktSyncIds(trakt = dark.ids.trakt)))))
                    TraktSyncEngine.performSync(context, db = db, forceRefresh = true).getOrThrow()
                    val local = db.savedItemDao().getItemByTraktId(dark.ids.trakt, "tv") ?: error("Remote-added show was not imported")
                    val before = api.getShowWatchedProgress(dark.ids.trakt.toString())
                    val episode = before.seasons?.firstOrNull { it.number == 1 }?.episodes?.firstOrNull { it.number == 1 }
                    if (episode?.completed != true) {
                        val ids = TraktSyncIds(trakt = dark.ids.trakt)
                        val watchedSeason = TraktSyncSeasonPayload(1, episodes = listOf(TraktSyncEpisodePayload(1)))
                        val watched = TraktSyncPayload(shows = listOf(TraktSyncShowPayload(ids, seasons = listOf(watchedSeason))))
                        try {
                            val accepted = api.addToHistory(watched)
                            report.put("remoteEpisodesAccepted", accepted.added?.episodes ?: 0)
                            assertEquals("Remote episode write was not accepted", 1, accepted.added?.episodes)
                            val direct = api.getShowWatchedProgress(dark.ids.trakt.toString())
                            report.put("directProgressCompleted", direct.completed)
                                .put("directEpisodeCompleted", direct.seasons?.firstOrNull { it.number == 1 }?.episodes?.firstOrNull { it.number == 1 }?.completed)
                            var snapshotContainsEpisode = false
                            for (attempt in 0..9) {
                                val snapshot = api.getWatchedShows(page = 1, limit = 100)
                                val watchedShow = snapshot.firstOrNull { it.show?.ids?.trakt == dark.ids.trakt }
                                report.put("snapshotTitles", JSONArray(snapshot.map { it.show?.title }))
                                    .put("snapshotHasDark", watchedShow != null).put("snapshotSeasonCount", watchedShow?.seasons?.size ?: 0)
                                snapshotContainsEpisode = watchedShow?.seasons?.firstOrNull { it.number == 1 }?.episodes?.any { it.number == 1 } == true
                                if (snapshotContainsEpisode) break
                                kotlinx.coroutines.delay(3_000)
                            }
                            report.put("serverSnapshotContainsEpisode", snapshotContainsEpisode)
                            assertTrue("Trakt's watched snapshot did not expose the accepted episode", snapshotContainsEpisode)
                            val summary = TraktSyncEngine.performSync(context, db = db, forceRefresh = true).getOrThrow()
                            report.put("showsSynced", summary.showsSynced).put("tvSyncEnabled", auth.isSyncTvProgressEnabled())
                            val synced = db.savedItemDao().getItemById(local.id)!!.toDomainModel()
                            assertTrue("Remote episode completion was not imported", synced.showProgress?.seasons?.firstOrNull { it.seasonNumber == 1 }
                                ?.episodes?.firstOrNull { it.episodeNumber == 1 }?.isWatched == true)
                            report.put("remoteProgressDelivered", true)
                        } finally {
                            api.removeFromHistory(TraktSyncPayload(shows = listOf(TraktSyncShowPayload(ids, seasons = listOf(TraktSyncSeasonPayload(1,
                                episodes = listOf(TraktSyncEpisodePayload(1))))))))
                            TraktSyncEngine.performSync(context, db = db, forceRefresh = true).getOrThrow()
                            report.put("temporaryHistoryRestored", true)
                        }
                    } else report.put("remoteProgressAlreadyPresent", true)
                    val loaded = repo.enrichMediaItemDetails(db.savedItemDao().getItemById(local.id)!!.toDomainModel())
                    assertTrue(loaded.showProgress!!.seasons.count { it.seasonNumber > 0 } >= 3)
                    report.put("remoteShow", loaded.title).put("remoteShowId", loaded.id).put("seasons", loaded.showProgress!!.seasons.size)
                }
                "episodes" -> {
                    val series = media.first { it.title == "Stranger Things" }.toDomainModel()
                    val loaded = repo.enrichMediaItemDetails(series)
                    val counts = JSONArray()
                    for (season in loaded.showProgress!!.seasons.filter { it.seasonNumber > 0 }) {
                        TraktEpisodeCatalog.load(context, loaded, season.seasonNumber, refresh = true)
                        val rows = db.traktEpisodeDao().season(loaded.id, season.seasonNumber)
                        assertTrue("Missing season ${season.seasonNumber}", rows.isNotEmpty())
                        assertTrue(rows.all { !it.title.isNullOrBlank() })
                        assertTrue(rows.any { !it.thumbnailUrl.isNullOrBlank() })
                        counts.put(JSONObject().put("season", season.seasonNumber).put("episodes", rows.size))
                    }
                    report.put("seasonEpisodes", counts)
                }
                else -> error("Unknown validation mode")
            }
            report.put("passed", true)
        } catch (failure: Throwable) {
            report.put("passed", false).put("failure", failure.javaClass.simpleName).put("message", failure.message)
            throw failure
        } finally {
            val target = File(context.filesDir, "media-validation/$mode.json")
            target.parentFile!!.mkdirs()
            target.writeText(report.toString(2))
        }
    }
}
