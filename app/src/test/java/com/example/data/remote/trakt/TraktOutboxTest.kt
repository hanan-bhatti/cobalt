package com.example.data.remote.trakt

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.*
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TraktOutboxTest {
    private class Fixture : AutoCloseable {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val api = FakeTraktApiService()
        val auth = TraktAuthManager(context, { api }, FakeTraktCredentialStore())
        init {
            context.getSharedPreferences("cobalt_trakt_prefs", Context.MODE_PRIVATE).edit().clear().commit()
            auth.saveTokens(TraktTokenResponse(access_token = "test", refresh_token = "refresh", expires_in = 864_000))
            TraktAuthManager.setInstanceForTesting(auth)
        }
        suspend fun item(id: String = "test", rating: Int? = null) = SavedItemEntity.fromDomainModel(SavedItem(id = id,
            type = SavedItemType.MEDIA, title = "Test movie", content = "https://trakt.tv/movies/1", mediaType = "movie", traktId = 1,
            userRating = rating)).also { db.savedItemDao().insertItem(it) }
        override fun close() { db.close(); TraktAuthManager.setInstanceForTesting(null) }
    }
    @Test fun failedBatchStaysDurableAndThousandChangesDrainInBatchesOf100() = runBlocking {
        Fixture().use { f ->
            val id = f.auth.syncSessionId()
            repeat(1000) { index ->
                f.db.traktOutboxDao().put(TraktOutboxEntity("$id:$index", id, "movie-$index", "rating:add",
                    "{\"movies\":[{\"ids\":{\"trakt\":${index + 1}},\"rating\":7}]}", created = index.toLong()))
            }
            val failing = object : TraktApiService by f.api {
                override suspend fun addRatings(payload: TraktSyncPayload): TraktSyncResponse { throw java.io.IOException("offline") }
            }
            try { TraktOutbox.drain(f.context, failing, f.db); fail("Must retain a failed request") }
            catch (_: java.io.IOException) {}
            assertEquals(1000, f.db.traktOutboxDao().pending(id, 1100).size)
            val batches = ArrayList<Int>()
            val passing = object : TraktApiService by f.api {
                override suspend fun addRatings(payload: TraktSyncPayload): TraktSyncResponse {
                    batches += payload.movies.orEmpty().size; return TraktSyncResponse()
                }
            }
            TraktOutbox.drain(f.context, passing, f.db)
            assertEquals(List(10) { 100 }, batches)
            assertTrue(f.db.traktOutboxDao().pending(id).isEmpty())
        }
    }
    @Test fun newTmdbMovieIsAtomicallySavedAndQueuedWithoutAZeroTraktId() = runBlocking {
        Fixture().use { f ->
            val item = SavedItem(id = "tmdb_movie_550", type = SavedItemType.MEDIA, title = "Test TMDB result",
                content = "", mediaType = "movie", tmdbId = 550, watchStatus = "Plan to Watch")
            TraktOutbox.savePlannedMedia(f.context, item, f.db)
            assertTrue(f.db.savedItemDao().getItemById(item.id)!!.inWatchlist)
            val row = f.db.traktOutboxDao().pending(f.auth.syncSessionId()).single()
            assertTrue(row.payload.contains("\"tmdb\":550"))
            assertFalse(row.payload.contains("\"trakt\""))
            var movieId: Int? = null
            val api = object : TraktApiService by f.api {
                override suspend fun addToWatchlist(payload: TraktSyncPayload): TraktSyncResponse {
                    movieId = payload.movies!!.single().ids.tmdb
                    return TraktSyncResponse()
                }
            }
            TraktOutbox.drain(f.context, api, f.db)
            assertEquals(550, movieId)
            assertTrue(f.db.traktOutboxDao().pending(f.auth.syncSessionId()).isEmpty())
        }
    }
    @Test fun disabledWatchlistSyncKeepsNewTmdbMovieQueuedWithoutUploading() = runBlocking {
        Fixture().use { f ->
            f.auth.setSyncWatchlistEnabled(false)
            val item = SavedItem(id = "tmdb", type = SavedItemType.MEDIA, title = "Local", content = "", mediaType = "movie", tmdbId = 550)
            TraktOutbox.savePlannedMedia(f.context, item, f.db)
            assertNotNull(f.db.savedItemDao().getItemById(item.id))
            var uploaded = false
            val api = object : TraktApiService by f.api {
                override suspend fun addToWatchlist(payload: TraktSyncPayload): TraktSyncResponse { uploaded = true; return TraktSyncResponse() }
            }
            TraktOutbox.drain(f.context, api, f.db)
            assertFalse(uploaded)
            assertEquals(1, f.db.traktOutboxDao().pending(f.auth.syncSessionId()).size)
            f.auth.setSyncWatchlistEnabled(true)
            TraktOutbox.drain(f.context, api, f.db)
            assertTrue(uploaded)
        }
    }
    @Test fun nullAndInvalidActivityDatesDoNotTriggerCatalogueDownloads() {
        assertEquals(0L, TraktSyncEngine.activityTimestamp(null))
        assertEquals(0L, TraktSyncEngine.activityTimestamp("invalid"))
        assertEquals(1_000L, TraktSyncEngine.activityTimestamp("1970-01-01T00:00:01Z"))
    }
    @Test fun unmatchedTitleDoesNotBlockOtherChangesAndRemainsDurable() = runBlocking {
        Fixture().use { f ->
            val session = f.auth.syncSessionId()
            repeat(2) { index -> f.db.traktOutboxDao().put(TraktOutboxEntity("$index",session,"movie-$index","rating:add",
                "{\"movies\":[{\"ids\":{\"trakt\":${index+1}},\"rating\":7}]}")) }
            val api = object : TraktApiService by f.api {
                override suspend fun addRatings(payload: TraktSyncPayload) = TraktSyncResponse(
                    not_found = TraktSyncNotFound(movies = listOf(mapOf("ids" to mapOf("trakt" to 1)))))
            }
            try { TraktOutbox.drain(f.context, api, f.db); fail("Unmatched title must be reported") } catch (_: IllegalStateException) {}
            assertTrue(f.db.traktOutboxDao().pending(session).isEmpty())
            assertEquals("0", f.db.traktOutboxDao().pending(session, now = Long.MAX_VALUE).single().key)
        }
    }
    @Test fun locallyDeletedMediaCannotBeRecreatedByInboundSync() = runBlocking {
        Fixture().use { f ->
            val row = f.item()
            f.db.mediaTombstoneDao().put(MediaTombstoneEntity.from(row.toDomainModel(), System.currentTimeMillis()))
            f.db.savedItemDao().deleteById(row.id)
            TraktOutbox.persistInbound(f.db, listOf(row.copy(id = "new-import")), f.auth.syncSessionId())
            assertNull(f.db.savedItemDao().getItemById("new-import"))
            f.db.mediaTombstoneDao().clearMatching("movie", "1", "", "")
            TraktOutbox.persistInbound(f.db, listOf(row.copy(id = "new-import")), f.auth.syncSessionId())
            assertNotNull(f.db.savedItemDao().getItemById("new-import"))
        }
    }
    @Test fun pausedRatingsDoNotBlockWatchlistUploadsOrLoseTheirIntent() = runBlocking {
        Fixture().use { f ->
            val session = f.auth.syncSessionId()
            f.auth.setSyncRatingsEnabled(false)
            f.db.traktOutboxDao().put(TraktOutboxEntity("rating",session,"one","rating:add","{\"movies\":[{\"ids\":{\"trakt\":1},\"rating\":7}]}"))
            f.db.traktOutboxDao().put(TraktOutboxEntity("watch",session,"two","watchlist:add","{\"movies\":[{\"ids\":{\"trakt\":2}}]}"))
            var ratings = 0; var watchlists = 0
            val api = object : TraktApiService by f.api {
                override suspend fun addRatings(payload: TraktSyncPayload): TraktSyncResponse { ratings++; return TraktSyncResponse() }
                override suspend fun addToWatchlist(payload: TraktSyncPayload): TraktSyncResponse { watchlists++; return TraktSyncResponse() }
            }
            TraktOutbox.drain(f.context,api,f.db)
            assertEquals(0,ratings); assertEquals(1,watchlists)
            assertEquals("rating",f.db.traktOutboxDao().pending(session).single().key)
            f.auth.setSyncRatingsEnabled(true)
            TraktOutbox.drain(f.context,api,f.db); assertEquals(1,ratings)
        }
    }
    @Test fun historySelectionRespectsMovieAndShowSwitches() = runBlocking {
        Fixture().use { f ->
            val session = f.auth.syncSessionId()
            f.db.traktOutboxDao().put(TraktOutboxEntity("movie",session,"one","history:add","{\"movies\":[{\"ids\":{\"trakt\":1}}]}"))
            f.db.traktOutboxDao().put(TraktOutboxEntity("show",session,"two","history:add","{\"shows\":[{\"ids\":{\"trakt\":2}}]}"))
            assertEquals("movie",f.db.traktOutboxDao().pending(session,shows = false).single().key)
            assertEquals("show",f.db.traktOutboxDao().pending(session,movies = false).single().key)
        }
    }
    @Test fun enablingACategoryKeepsANewerRefreshRequestDuringAnOlderImport() {
        Fixture().use { f ->
            f.auth.setSyncRatingsEnabled(false); f.auth.setSyncRatingsEnabled(true)
            val first = f.auth.dirtyCategory("ratings"); assertNotNull(first)
            f.auth.setSyncRatingsEnabled(false); f.auth.setSyncRatingsEnabled(true)
            val next = f.auth.dirtyCategory("ratings"); assertNotEquals(first,next)
            f.auth.acknowledgeCategory("ratings",first); assertEquals(next,f.auth.dirtyCategory("ratings"))
            f.auth.acknowledgeCategory("ratings",next); assertNull(f.auth.dirtyCategory("ratings"))
        }
    }
    @Test fun acknowledgingAnOldGenerationCannotDeleteANewerUserChoice() = runBlocking {
        Fixture().use { f ->
            val session = f.auth.syncSessionId()
            val first = TraktOutboxEntity("rating", session, "movie", "rating:add", "{}")
            f.db.traktOutboxDao().put(first)
            val next = first.copy(generation = "new", payload = "{\"movies\":[]}")
            f.db.traktOutboxDao().put(next)
            f.db.traktOutboxDao().acknowledge(first.key, first.generation)
            assertEquals(next, f.db.traktOutboxDao().pending(session).single())
            assertTrue(f.db.traktOutboxDao().pending("another-account").isEmpty())
        }
    }
    @Test fun inboundRefreshPreservesQueuedRatingUntilAcknowledged() = runBlocking {
        Fixture().use { f ->
            val item = f.item(rating = 8)
            val session = f.auth.syncSessionId()
            val pending = TraktOutboxEntity("rating", session, item.id, "rating:add", "{}")
            f.db.traktOutboxDao().put(pending)
            TraktOutbox.persistInbound(f.db, listOf(item.copy(userRating = 2)), session)
            assertEquals(8, f.db.savedItemDao().getItemById(item.id)!!.userRating)
            f.db.traktOutboxDao().acknowledge(pending.key, pending.generation)
            TraktOutbox.persistInbound(f.db, listOf(item.copy(userRating = 2)), session)
            assertEquals(2, f.db.savedItemDao().getItemById(item.id)!!.userRating)
        }
    }
    @Test fun movieAndShowIdsDoNotCollide() = runBlocking {
        Fixture().use { f ->
            val movie = f.item()
            val show = movie.copy(id = "show", mediaType = "tv")
            f.db.savedItemDao().insertItem(show)
            assertEquals(movie.id, f.db.savedItemDao().getItemByTraktId(1, "movie")!!.id)
            assertEquals(show.id, f.db.savedItemDao().getItemByTraktId(1, "tv")!!.id)
        }
    }
    @Test fun tokenRotationKeepsTheSyncSessionValid() = runBlocking {
        Fixture().use { f ->
            f.auth.saveTokens(TraktTokenResponse(access_token = "expired", refresh_token = "refresh", expires_in = 1))
            val epoch = f.auth.currentSessionEpoch()
            val identity = f.auth.syncSessionId()
            f.auth.refreshAccessTokenIfNeeded()
            assertTrue(f.api.refreshTokenCalled)
            assertFalse(f.auth.isSessionInvalid(epoch))
            assertEquals(identity, f.auth.syncSessionId())
        }
    }
}
