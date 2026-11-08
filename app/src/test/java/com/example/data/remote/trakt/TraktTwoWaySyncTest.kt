package com.example.data.remote.trakt

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.*
import com.example.data.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TraktTwoWaySyncTest {
    private class Fixture : AutoCloseable {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val fake = FakeTraktApiService()
        val auth: TraktAuthManager
        init {
            context.getSharedPreferences("cobalt_trakt_prefs", 0).edit().clear().commit()
            context.getSharedPreferences("cobalt_prefs", 0).edit().remove("tmdb_api_key").commit()
            auth = TraktAuthManager(context, { fake }, FakeTraktCredentialStore())
            auth.saveTokens(TraktTokenResponse(access_token = "test", refresh_token = "test", expires_in = 864000))
            TraktAuthManager.setInstanceForTesting(auth)
        }
        fun api(api: TraktApiService) { TraktSyncEngine.apiServiceProvider = { api } }
        suspend fun movie(id: String, trakt: Long? = null, tmdb: Int? = null, rating: Int? = null, watchlist: Boolean = false) =
            SavedItemEntity.fromDomainModel(SavedItem(id = id, type = SavedItemType.MEDIA, title = "My title", content = "My local annotation",
                mediaType = "movie", traktId = trakt, tmdbId = tmdb, userRating = rating, inWatchlist = watchlist, watchStatus = "Plan to Watch"))
                .also { db.savedItemDao().insertItem(it) }
        override fun close() { TraktSyncEngine.apiServiceProvider = null; TraktAuthManager.setInstanceForTesting(null); db.close() }
    }

    @Test fun preLoginTmdbSaveUploadsThenMergesInboundWithoutDuplicatingOrReplacingLocalNotes() = runBlocking {
        Fixture().use { f ->
            f.movie("before-login", tmdb = 550, rating = 8, watchlist = true)
            val remote = TraktMovie(title = "Provider title", ids = TraktIds(trakt = 123, tmdb = 550))
            var sent = false
            val api = object : TraktApiService by f.fake {
                override suspend fun addToWatchlist(payload: TraktSyncPayload): TraktSyncResponse {
                    assertEquals(550, payload.movies!!.single().ids.tmdb); sent = true; return TraktSyncResponse()
                }
                override suspend fun getWatchlistMovies(page: Int?, limit: Int?): List<TraktWatchlistItem> {
                    assertTrue("Upload must precede download", sent)
                    return if (page == 1) listOf(TraktWatchlistItem(movie = remote)) else emptyList()
                }
                override suspend fun getMovieRatings(page: Int?, limit: Int?) = if (page == 1) listOf(TraktRatingItem(rating = 8, movie = remote)) else emptyList()
            }
            f.api(api)
            assertTrue(TraktSyncEngine.performSync(f.context, db = f.db).isSuccess)
            val saved = f.db.savedItemDao().getAllItems().single()
            assertEquals("before-login", saved.id)
            assertEquals("My title", saved.title)
            assertEquals("My local annotation", saved.content)
            assertEquals(123L, saved.traktId)
            assertEquals(8, saved.userRating)
            assertTrue(saved.inWatchlist)
        }
    }

    @Test fun clearedRemoteRatingAndWatchlistAreAppliedEvenWithNormalTraktActivityFields() = runBlocking {
        Fixture().use { f ->
            f.movie("saved", trakt = 10, rating = 8, watchlist = true)
            f.auth.markLibrarySeeded(f.auth.syncSessionId())
            f.auth.setLastSyncTimestamp(1_000L)
            val api = object : TraktApiService by f.fake {
                override suspend fun getLastActivities() = TraktLastActivitiesResponse(movies = TraktLastActivitiesMovies(
                    rated_at = "2026-11-08T12:00:00Z", watchlisted_at = "2026-11-08T12:00:00Z"))
            }
            f.api(api)
            assertTrue(TraktSyncEngine.performSync(f.context, db = f.db).isSuccess)
            val saved = f.db.savedItemDao().getItemById("saved")!!
            assertNull(saved.userRating)
            assertFalse(saved.inWatchlist)
            assertEquals("My local annotation", saved.content)
        }
    }

    @Test fun ratingsOnlyRemoteTitlesAreImportedAndMovieAndShowIdsStaySeparate() = runBlocking {
        Fixture().use { f ->
            f.auth.markLibrarySeeded(f.auth.syncSessionId())
            val api = object : TraktApiService by f.fake {
                override suspend fun getMovieRatings(page: Int?, limit: Int?) = if (page == 1) listOf(TraktRatingItem(rating = 8,
                    movie = TraktMovie(title = "Only rated movie", ids = TraktIds(trakt = 1)))) else emptyList()
                override suspend fun getShowRatings(page: Int?, limit: Int?) = if (page == 1) listOf(TraktRatingItem(rating = 6,
                    show = TraktShow(title = "Only rated show", ids = TraktIds(trakt = 1)))) else emptyList()
            }
            f.api(api)
            assertTrue(TraktSyncEngine.performSync(f.context, db = f.db).isSuccess)
            assertEquals(8, f.db.savedItemDao().getItemByTraktId(1, "movie")!!.userRating)
            assertEquals(6, f.db.savedItemDao().getItemByTraktId(1, "tv")!!.userRating)
        }
    }

    @Test fun unrecognizedLocalTitleDoesNotStopNewTraktTitlesFromImporting() = runBlocking {
        Fixture().use { f ->
            f.movie("unrecognized", tmdb = 999)
            val api = object : TraktApiService by f.fake {
                override suspend fun addToWatchlist(payload: TraktSyncPayload) = TraktSyncResponse(
                    not_found = TraktSyncNotFound(movies = listOf(mapOf("ids" to mapOf("tmdb" to 999)))))
                override suspend fun getWatchlistMovies(page: Int?, limit: Int?) = if (page == 1) listOf(TraktWatchlistItem(
                    movie = TraktMovie(title = "Remote film", ids = TraktIds(trakt = 100)))) else emptyList()
            }
            f.api(api)
            assertTrue(TraktSyncEngine.performSync(f.context, db = f.db).isSuccess)
            assertNotNull(f.db.savedItemDao().getItemByTraktId(100, "movie"))
            assertEquals(1, f.db.traktOutboxDao().pending(f.auth.syncSessionId(), now = Long.MAX_VALUE).size)
        }
    }

    @Test fun remoteHistoryRemovalResetsProgressWithoutDeletingTheSavedMovie() = runBlocking {
        Fixture().use { f ->
            val movie = f.movie("saved", trakt = 10).copy(watchStatus = "Completed", watchStatusOverride = "Completed", lastWatchedAt = 1_000L)
            f.db.savedItemDao().insertItem(movie)
            f.auth.markLibrarySeeded(f.auth.syncSessionId())
            f.api(f.fake)
            assertTrue(TraktSyncEngine.performSync(f.context, db = f.db).isSuccess)
            val after = f.db.savedItemDao().getItemById(movie.id)!!
            assertNull(after.lastWatchedAt)
            assertEquals("Plan to Watch", after.watchStatus)
            assertNull(after.watchStatusOverride)
        }
    }

    @Test fun failedRatingsSnapshotRetainsEarlierRatingsAndRetriesFromTheBeginning() = runBlocking {
        Fixture().use { f ->
            f.movie("keep", trakt = 999, rating = 9)
            f.auth.markLibrarySeeded(f.auth.syncSessionId())
            var failPage = true
            val pages = mutableListOf<Int?>()
            val api = object : TraktApiService by f.fake {
                override suspend fun getMovieRatings(page: Int?, limit: Int?): List<TraktRatingItem> {
                    pages += page
                    if (page == 2 && failPage) throw java.io.IOException("Interrupted snapshot")
                    return if (page == 1) (1..100).map { TraktRatingItem(rating = 8, movie = TraktMovie(title = "Film $it", ids = TraktIds(trakt = it.toLong()))) }
                    else if (page == 2) listOf(TraktRatingItem(rating = 9, movie = TraktMovie(title = "Kept film", ids = TraktIds(trakt = 999))))
                    else emptyList()
                }
            }
            f.api(api)
            assertTrue(TraktSyncEngine.performSync(f.context, db = f.db).isFailure)
            assertEquals(9, f.db.savedItemDao().getItemById("keep")!!.userRating)
            failPage = false
            assertTrue(TraktSyncEngine.performSync(f.context, db = f.db).isSuccess)
            assertEquals(listOf(1, 2, 1, 2, 3), pages)
            assertEquals(9, f.db.savedItemDao().getItemById("keep")!!.userRating)
        }
    }

    @Test fun matchingPersonalRatingStillLinksTraktIdentityWhenWatchlistSyncIsPaused() = runBlocking {
        Fixture().use { f ->
            f.movie("rated-local", tmdb = 550, rating = 8)
            f.auth.markLibrarySeeded(f.auth.syncSessionId())
            f.auth.setSyncWatchlistEnabled(false)
            val api = object : TraktApiService by f.fake {
                override suspend fun getMovieRatings(page: Int?, limit: Int?) = if (page == 1) listOf(TraktRatingItem(rating = 8,
                    movie = TraktMovie(title = "Provider title", ids = TraktIds(trakt = 123, tmdb = 550)))) else emptyList()
            }
            f.api(api)
            assertTrue(TraktSyncEngine.performSync(f.context, db = f.db).isSuccess)
            assertEquals(123L, f.db.savedItemDao().getItemById("rated-local")!!.traktId)
        }
    }

    @Test fun serverCappedShortPagesAreFullyImportedForAllSixSnapshots() = runBlocking {
        Fixture().use { f ->
            f.auth.markLibrarySeeded(f.auth.syncSessionId())
            val pages = mutableMapOf<String, MutableList<Int?>>()
            fun id(kind: String, page: Int?): Long? {
                pages.getOrPut(kind) { mutableListOf() }.add(page)
                return page?.takeIf { it in 1..2 }?.toLong()
            }
            fun movie(id: Long) = TraktMovie(title = "Film $id", ids = TraktIds(trakt = id))
            fun show(id: Long) = TraktShow(title = "Show $id", ids = TraktIds(trakt = id))
            f.api(object : TraktApiService by f.fake {
                override suspend fun getWatchedMovies(page: Int?, limit: Int?) = id("movies", page)?.let {
                    listOf(TraktWatchedMovieItem(movie = movie(it))) } ?: emptyList()
                override suspend fun getWatchedShows(page: Int?, limit: Int?) = id("shows", page)?.let {
                    listOf(TraktWatchedShowItem(show = show(it), seasons = listOf(TraktWatchedSeason(number = 1,
                        episodes = listOf(TraktWatchedEpisode(number = 1)))))) } ?: emptyList()
                override suspend fun getMovieRatings(page: Int?, limit: Int?) = id("movie-ratings", page)?.let {
                    listOf(TraktRatingItem(rating = 8, movie = movie(it))) } ?: emptyList()
                override suspend fun getShowRatings(page: Int?, limit: Int?) = id("show-ratings", page)?.let {
                    listOf(TraktRatingItem(rating = 7, show = show(it))) } ?: emptyList()
                override suspend fun getWatchlistMovies(page: Int?, limit: Int?) = id("movie-watchlist", page)?.let {
                    listOf(TraktWatchlistItem(movie = movie(it))) } ?: emptyList()
                override suspend fun getWatchlistShows(page: Int?, limit: Int?) = id("show-watchlist", page)?.let {
                    listOf(TraktWatchlistItem(show = show(it))) } ?: emptyList()
            })
            assertTrue(TraktSyncEngine.performSync(f.context, db = f.db).isSuccess)
            assertEquals(6, pages.size)
            pages.values.forEach { assertEquals(listOf(1, 2, 3), it) }
            assertEquals(4, f.db.savedItemDao().getAllItems().size)
            for (id in 1L..2L) {
                val movie = f.db.savedItemDao().getItemByTraktId(id, "movie")!!
                val show = f.db.savedItemDao().getItemByTraktId(id, "tv")!!
                assertTrue(movie.inWatchlist); assertEquals(8, movie.userRating)
                assertTrue(show.inWatchlist); assertEquals(7, show.userRating)
                assertEquals(1, TvShowProgress.fromJson(show.showProgressJson)!!.totalWatched)
                assertEquals("Watching", show.watchStatus)
            }
        }
    }

    @Test fun upgradingTheWatchedContractRefreshesUnchangedAccountsAndPreservesCatalogueTotals() = runBlocking {
        Fixture().use { f ->
            val progress = TvShowProgress(totalAired = 2, seasons = listOf(TvSeasonProgress(seasonNumber = 1,
                totalEpisodes = 2, episodes = listOf(TvEpisodeProgress(1, 1, title = "Pilot"), TvEpisodeProgress(2, 1, title = "Finale")))))
            val saved = f.movie("old-series", trakt = 10).copy(mediaType = "tv", showProgressJson = progress.toJson())
            f.db.savedItemDao().insertItem(saved)
            f.auth.markLibrarySeeded(f.auth.syncSessionId())
            f.auth.setLastSyncTimestamp(System.currentTimeMillis())
            f.auth.setSyncCheckpoint("MOVIES", 2)
            f.fake.watchedShowsToReturn = listOf(TraktWatchedShowItem(show = TraktShow(title = "Show", ids = TraktIds(trakt = 10)),
                seasons = listOf(TraktWatchedSeason(number = 1, episodes = listOf(TraktWatchedEpisode(number = 1))))))
            f.api(f.fake)
            assertTrue(TraktSyncEngine.performSync(f.context, db = f.db).isSuccess)
            val after = f.db.savedItemDao().getItemById(saved.id)!!
            val fresh = TvShowProgress.fromJson(after.showProgressJson)!!
            assertEquals(2, fresh.totalAired); assertEquals(1, fresh.totalWatched)
            assertFalse(fresh.completed); assertEquals("Watching", after.watchStatus)
            assertEquals("Pilot", fresh.seasons.single().episodes.first().title)
            assertEquals(2, fresh.seasons.single().episodes.size)
            assertTrue(f.auth.isSyncContractCurrent()); assertNull(f.auth.getSyncCheckpointStep())
        }
    }

    @Test fun missingSeasonsCannotEraseSavedWatchedEpisodesOrCertifyASuccessfulSync() = runBlocking {
        Fixture().use { f ->
            val progress = TvShowProgress(totalAired = 2, totalWatched = 1, seasons = listOf(TvSeasonProgress(seasonNumber = 1,
                totalEpisodes = 2, watchedEpisodes = 1, episodes = listOf(TvEpisodeProgress(1, 1, title = "Pilot", isWatched = true)))))
            val saved = f.movie("keep-progress", trakt = 10).copy(mediaType = "tv", watchStatus = "Watching", showProgressJson = progress.toJson())
            f.db.savedItemDao().insertItem(saved)
            f.auth.markLibrarySeeded(f.auth.syncSessionId())
            f.fake.watchedShowsToReturn = listOf(TraktWatchedShowItem(show = TraktShow(title = "Show", ids = TraktIds(trakt = 10))))
            f.api(f.fake)
            assertTrue(TraktSyncEngine.performSync(f.context, db = f.db).isFailure)
            assertEquals(saved, f.db.savedItemDao().getItemById(saved.id))
            assertFalse(f.auth.isSyncContractCurrent())
            assertEquals(0L, f.auth.getLastSyncTimestamp())
        }
    }

    @Test fun previewingTraktMetadataDoesNotChangeAnExistingSavedRecord() = runBlocking {
        Fixture().use { f ->
            val original = f.movie("preview", trakt = 10, rating = 8)
            val repo = com.example.data.repository.CobaltRepository(f.context, f.db)
            f.api(object : TraktApiService by f.fake {
                override suspend fun getMovieSummary(id: String) = TraktMovie(trailer = "https://youtu.be/abcdefghijk")
            })
            val preview = repo.enrichMediaItemDetails(original.toDomainModel(), saveToDb = false)
            assertEquals("https://youtu.be/abcdefghijk", preview.trailerUrl)
            assertEquals(original, f.db.savedItemDao().getItemById(original.id))
        }
    }

    @Test fun deletionDuringMetadataRefreshCannotRestoreOrModifyTheRemovedRecord() = runBlocking {
        Fixture().use { f ->
            val original = f.movie("deleted-during-refresh", trakt = 10)
            val repo = com.example.data.repository.CobaltRepository(f.context, f.db)
            f.api(object : TraktApiService by f.fake {
                override suspend fun getMovieSummary(id: String): TraktMovie {
                    f.db.savedItemDao().insertItem(original.copy(isDeleted = true))
                    return TraktMovie(trailer = "https://youtu.be/abcdefghijk")
                }
            })
            repo.enrichMediaItemDetails(original.toDomainModel())
            assertEquals(original.copy(isDeleted = true), f.db.savedItemDao().getItemById(original.id))
        }
    }

    @Test fun metadataRefreshReturnsTheNewerPersonalChoicesAlongWithCatalogueDetails() = runBlocking {
        Fixture().use { f ->
            val original = f.movie("edited-during-refresh", trakt = 10, rating = 8)
            val repo = com.example.data.repository.CobaltRepository(f.context, f.db)
            f.api(object : TraktApiService by f.fake {
                override suspend fun getMovieSummary(id: String): TraktMovie {
                    f.db.savedItemDao().insertItem(original.copy(userRating = 6, watchStatus = "Completed", lastWatchedAt = 1000L))
                    return TraktMovie(trailer = "https://youtu.be/abcdefghijk")
                }
            })
            val refreshed = repo.enrichMediaItemDetails(original.toDomainModel())
            assertEquals(6, refreshed.userRating)
            assertEquals("Completed", refreshed.watchStatus)
            assertEquals(1000L, refreshed.lastWatchedAt)
            assertEquals("My local annotation", refreshed.content)
            assertEquals("https://youtu.be/abcdefghijk", refreshed.trailerUrl)
            assertEquals(refreshed, f.db.savedItemDao().getItemById(original.id)!!.toDomainModel())
        }
    }

    @Test fun cancelledTvMetadataRequestStopsWithoutPersistingPartialDetails() = runBlocking {
        Fixture().use { f ->
            val original = f.movie("cancelled-refresh", trakt = 10).copy(mediaType = "tv", trailerUrl = "https://youtu.be/abcdefghijk")
            f.db.savedItemDao().insertItem(original)
            val repo = com.example.data.repository.CobaltRepository(f.context, f.db)
            f.api(object : TraktApiService by f.fake {
                override suspend fun getShowSeasons(id: String): List<TraktSeasonFullItem> = throw kotlinx.coroutines.CancellationException("Closed preview")
            })
            try {
                repo.enrichMediaItemDetails(original.toDomainModel())
                fail("A closed request must stay cancelled")
            } catch (_: kotlinx.coroutines.CancellationException) { }
            assertEquals(original, f.db.savedItemDao().getItemById(original.id))
        }
    }
}
