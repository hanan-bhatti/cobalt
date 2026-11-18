package com.example.data.remote.trakt

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.*
import com.example.data.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TraktPlaybackRepositoryTest {
    private class Fixture : AutoCloseable {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val fake = FakeTraktApiService()
        val auth: TraktAuthManager
        init {
            context.getSharedPreferences("cobalt_trakt_prefs", 0).edit().clear().commit()
            auth = TraktAuthManager(context, { fake }, FakeTraktCredentialStore())
            auth.saveTokens(TraktTokenResponse(access_token = "test", refresh_token = "test", expires_in = 864000))
            TraktAuthManager.setInstanceForTesting(auth)
        }
        val session get() = auth.playbackSession.value!!
        fun api(api: TraktApiService) { TraktSyncEngine.apiServiceProvider = { api } }
        suspend fun refresh(kind: String = "movie") = TraktPlaybackRepository.refresh(context, kind, force = true, db = db)
        suspend fun rows() = db.traktPlaybackDao().positions(session)
        override fun close() { TraktSyncEngine.apiServiceProvider = null; TraktAuthManager.setInstanceForTesting(null); db.close() }
    }
    private fun movie(progress: Double, id: Long = 42) = TraktPlaybackMovie(progress, "2026-11-13T08:30:00.000Z",
        TraktMovie(title = "A film", ids = TraktIds(trakt = id, tmdb = id.toInt() + 100, imdb = "tt$id")))
    private fun item(trakt: Long? = 42, tmdb: Int? = null, imdb: String? = null, kind: String = "movie") =
        SavedItem(id = "local", type = SavedItemType.MEDIA, title = "My own title", content = "My annotation", mediaType = kind,
            traktId = trakt, tmdbId = tmdb, imdbId = imdb, watchStatus = "Plan to Watch", userRating = 8)

    @Test fun fractionalPlaybackMatchesEveryProviderWithoutChangingTheSavedTitleOrHistory() = runBlocking {
        Fixture().use { f ->
            val saved = SavedItemEntity.fromDomainModel(item())
            f.db.savedItemDao().insertItem(saved)
            f.api(object : TraktApiService by f.fake { override suspend fun getPlaybackMovies(page: Int, limit: Int) = listOf(movie(35.5)) })
            assertTrue(f.refresh())
            for (local in listOf(item(), item(null,142), item(null,null,"tt42"))) {
                assertEquals(35.5, TraktPlaybackRepository.find(local, f.rows())!!.progress, .00001)
            }
            assertEquals(saved, f.db.savedItemDao().getItemById(saved.id))
            assertEquals(0, f.db.traktOutboxDao().pendingCount(f.auth.syncSessionId()))
            assertNull(TraktPlaybackRepository.find(item(kind = "tv"), f.rows()))
            assertNull(TraktPlaybackRepository.find(item(trakt = 99, tmdb = 142), f.rows()))
        }
    }
    @Test fun zeroFinishedInvalidAndUnidentifiedPositionsNeverProduceABar() = runBlocking {
        Fixture().use { f ->
            f.api(object : TraktApiService by f.fake { override suspend fun getPlaybackMovies(page: Int, limit: Int) =
                listOf(movie(0.0,1),movie(100.0,2),movie(-1.0,3),movie(101.0,4),movie(Double.NaN,5),movie(Double.POSITIVE_INFINITY,6),
                    TraktPlaybackMovie(35.5, movie = TraktMovie(ids = TraktIds()))) })
            assertTrue(f.refresh())
            assertEquals(2, f.rows().size)
            for (id in 1L..6L) assertNull(TraktPlaybackRepository.find(item(trakt = id), f.rows()))
        }
    }
    @Test fun failedSecondPagePreservesSnapshotAndSuccessfulEmptyResponseClearsIt() = runBlocking {
        Fixture().use { f ->
            f.api(object : TraktApiService by f.fake { override suspend fun getPlaybackMovies(page: Int, limit: Int) = listOf(movie(35.5)) })
            assertTrue(f.refresh())
            f.api(object : TraktApiService by f.fake {
                override suspend fun getPlaybackMovies(page: Int, limit: Int): List<TraktPlaybackMovie> {
                    if (page == 2) throw java.io.IOException("offline")
                    return (1L..100L).map { movie(66.6, it) }
                }
            })
            assertFalse(f.refresh()); assertEquals(35.5, f.rows().single().progress, .00001)
            f.api(f.fake); assertTrue(f.refresh()); assertTrue(f.rows().isEmpty())
        }
    }
    @Test fun allPagesAreFetchedAndTheCacheAvoidsRepeatedCalls() = runBlocking {
        Fixture().use { f ->
            val pages = mutableListOf<Int>()
            f.api(object : TraktApiService by f.fake { override suspend fun getPlaybackMovies(page: Int, limit: Int): List<TraktPlaybackMovie> {
                pages += page; assertEquals(100, limit)
                return when (page) { 1 -> (1L..100L).map { movie(25.0,it) }; 2 -> listOf(movie(35.5,101)); else -> emptyList() }
            } })
            assertTrue(f.refresh()); assertEquals(101, f.rows().size)
            assertTrue(TraktPlaybackRepository.refresh(f.context, db = f.db))
            assertEquals(listOf(1,2), pages)
        }
    }
    @Test fun replacingAnAccountHidesOldPositionsAndRejectsALateResponse() = runBlocking {
        Fixture().use { f ->
            val old = f.session
            f.db.traktPlaybackDao().put(listOf(TraktPlaybackEntity(old,"movie:trakt:42","movie",42,null,null,null,null,35.5,null)))
            f.api(object : TraktApiService by f.fake { override suspend fun getPlaybackMovies(page: Int, limit: Int): List<TraktPlaybackMovie> {
                f.auth.saveTokens(TraktTokenResponse(access_token = "replacement", refresh_token = "replacement", expires_in = 864000))
                return listOf(movie(80.0))
            } })
            assertFalse(f.refresh()); assertNotEquals(old, f.session); assertTrue(f.rows().isEmpty())
            assertEquals(35.5, f.db.traktPlaybackDao().positions(old).single().progress, .00001)
            f.api(f.fake); assertTrue(f.refresh()); assertTrue(f.db.traktPlaybackDao().positions(old).isEmpty())
        }
    }
    @Test fun tokenRefreshKeepsTheOwnerWhileDisconnectClearsIt() = runBlocking {
        Fixture().use { f ->
            val session = f.session
            f.auth.saveTokens(TraktTokenResponse(access_token = "refreshed",refresh_token = "test",expires_in = 864000), newSession = false)
            assertEquals(session, f.session)
            f.auth.disconnect(); assertNull(f.auth.playbackSession.value)
            assertFalse(f.refresh())
        }
    }
    @Test fun episodeSnapshotDoesNotReplaceMoviePlaybackAndUsesActualEpisodePosition() = runBlocking {
        Fixture().use { f ->
            f.api(object : TraktApiService by f.fake {
                override suspend fun getPlaybackMovies(page: Int, limit: Int) = listOf(movie(35.5))
                override suspend fun getPlaybackEpisodes(page: Int, limit: Int) = listOf(TraktPlaybackEpisode(12.5,"2026-11-13T08:00:00.000Z",
                    TraktSeasonEpisodeItem(season = 2,number = 3),TraktShow(ids = TraktIds(trakt = 42))))
            })
            assertTrue(f.refresh()); assertTrue(f.refresh("tv"))
            assertEquals(35.5, TraktPlaybackRepository.find(item(), f.rows())!!.progress,.00001)
            val episode = TraktPlaybackRepository.find(item(kind = "tv"),f.rows())!!
            assertEquals(12.5,episode.progress,.00001); assertEquals(2,episode.season); assertEquals(3,episode.episode)
        }
    }
    @Test fun parentCancellationPropagatesAndKeepsTheSnapshot() = runBlocking {
        Fixture().use { f ->
            f.api(object : TraktApiService by f.fake { override suspend fun getPlaybackMovies(page: Int, limit: Int): List<TraktPlaybackMovie> { delay(1_000); return listOf(movie(90.0)) } })
            assertNull(withTimeoutOrNull(30) { f.refresh() })
            assertTrue(f.rows().isEmpty())
        }
    }
}
