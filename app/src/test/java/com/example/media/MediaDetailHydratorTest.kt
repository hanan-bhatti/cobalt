package com.example.media

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.remote.*
import com.example.data.remote.trakt.*
import com.example.data.repository.CobaltRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MediaDetailHydratorTest {
    private class Fixture : AutoCloseable {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val fake = FakeTraktApiService()
        val repo = CobaltRepository(context, db)
        init {
            context.getSharedPreferences("cobalt_prefs", 0).edit().clear().putString("tmdb_api_key", "fixture").commit()
            context.getSharedPreferences("cobalt_trakt_prefs", 0).edit().clear().commit()
            TraktAuthManager.setInstanceForTesting(TraktAuthManager(context, { fake }, FakeTraktCredentialStore()))
            TraktSyncEngine.apiServiceProvider = { fake }
            MediaDetailHydrator.tmdbProvider = { object : TmdbApiService {
                override suspend fun searchMulti(query: String, apiKey: String) = TmdbMultiSearchResponse()
                override suspend fun getMovieDetails(id: Int, apiKey: String, append: String) = details(id)
                override suspend fun getTvDetails(id: Int, apiKey: String, append: String) = details(id)
                override suspend fun getTvSeasonDetails(id: Int, seasonNumber: Int, apiKey: String) = TmdbSeasonDetailsResponse()
                override suspend fun getTvCredits(id: Int, apiKey: String) = TmdbCreditsResponse()
                override suspend fun getMovieCredits(id: Int, apiKey: String) = TmdbCreditsResponse()
                private fun details(id: Int) = TmdbDetailsResponse(id, overview = "A catalogue synopsis.", originalLanguage = "ja", runtime = 90,
                    genres = listOf(TmdbGenre(16, "Animation")), numberOfEpisodes = 24,
                    seasons = listOf(TmdbSeasonSummary(1, "First season", 12), TmdbSeasonSummary(2, "Second season", 12)),
                    networks = listOf(TmdbNetwork("Fixture Network")), createdBy = listOf(TmdbCreator("Fixture Creator")),
                    externalIds = TmdbExternalIds("tt123456"), credits = TmdbCreditsResponse(cast = listOf(TmdbCastItem(name = "Fixture Actor"))))
            } }
            MediaDetailHydrator.omdbProvider = { object : OmdbApiService {
                override suspend fun getByImdbId(imdbId: String, apiKey: String) = OmdbResponse(response = "True", imdbRating = "8.7",
                    ratings = listOf(OmdbRatingItem("Rotten Tomatoes", "95%")))
            } }
        }
        override fun close() { MediaDetailHydrator.tmdbProvider = null; MediaDetailHydrator.omdbProvider = null; TraktSyncEngine.apiServiceProvider = null; TraktAuthManager.setInstanceForTesting(null); db.close() }
    }
    @Test fun tmdbOnlyShowGetsAllSeasonHeadersCastExternalIdentityAndRealSourceScores() = runBlocking {
        Fixture().use { f ->
            val item = SavedItem(id = "tmdb_tv_100", type = SavedItemType.MEDIA, title = "My title", content = "My annotation", mediaType = "tv", tmdbId = 100, userRating = 6)
            f.db.savedItemDao().insertItem(SavedItemEntity.fromDomainModel(item))
            val enriched = f.repo.enrichMediaItemDetails(item)
            assertEquals(listOf(1, 2), enriched.showProgress!!.seasons.map { it.seasonNumber })
            assertEquals("Fixture Actor", enriched.showProgress!!.cast.single().name)
            assertEquals("Fixture Network", enriched.showProgress!!.network)
            assertEquals("tt123456", enriched.imdbId)
            assertEquals("8.7", enriched.showProgress!!.imdbRating)
            assertEquals("95%", enriched.showProgress!!.rottenTomatoesScore)
            assertNull("A critics score must never become an audience score", enriched.showProgress!!.popcornScore)
            assertEquals("My annotation", enriched.content); assertEquals(6, enriched.userRating)
            assertEquals("A catalogue synopsis.", enriched.mediaSynopsis())
        }
    }
    @Test fun tmdbAnimatedFilmKeepsMovieIdentityAndDoesNotDisplayItsProviderUrlAsASynopsis() = runBlocking {
        Fixture().use { f ->
            val item = SavedItem(id = "tmdb_movie_100", type = SavedItemType.MEDIA, title = "Film", content = "https://trakt.tv/movies/100", mediaType = "movie", tmdbId = 100)
            assertEquals("", item.mediaSynopsis())
            val preview = f.repo.enrichMediaItemDetails(item, saveToDb = false)
            assertEquals("movie", preview.mediaType)
            assertEquals("A catalogue synopsis.", preview.mediaSynopsis())
            assertNull(f.db.savedItemDao().getItemById(item.id))
        }
    }
    @Test fun watchedSnapshotPreservesFullCatalogueAndClearsRemovedEpisodeFlags() {
        val old = TvShowProgress(totalAired = 24, cast = listOf(MediaCastMember("Actor")), seasons = listOf(
            TvSeasonProgress(1, "First", 12, 1, listOf(TvEpisodeProgress(1, 1, title = "Episode title", isWatched = true, stillPath = "poster"))),
            TvSeasonProgress(2, "Second", 12)))
        val refreshed = mergeWatchedSnapshot(old, TvShowProgress(totalAired = 24, totalWatched = 1,
            seasons = listOf(TvSeasonProgress(1, totalEpisodes = 12, watchedEpisodes = 1, episodes = listOf(TvEpisodeProgress(2, 1, isWatched = true))))))
        assertEquals(2, refreshed.seasons.size)
        assertEquals("Episode title", refreshed.seasons[0].episodes[0].title)
        assertFalse(refreshed.seasons[0].episodes[0].isWatched)
        assertTrue(refreshed.seasons[0].episodes[1].isWatched)
        assertEquals(old.cast, refreshed.cast)
    }
    @Test fun aProfileFailureCannotBlockAccountSyncWithAnExistingValidToken() = runBlocking {
        Fixture().use { f ->
            val auth = TraktAuthManager.getInstance(f.context)
            auth.saveTokens(TraktTokenResponse(access_token = "fixture", refresh_token = "fixture", expires_in = 864000))
            TraktSyncEngine.apiServiceProvider = { object : TraktApiService by f.fake {
                override suspend fun getCurrentUser(): TraktUser = throw java.io.IOException("Profile endpoint unavailable")
            } }
            assertTrue(TraktSyncEngine.performSync(f.context, db = f.db, forceRefresh = true).isSuccess)
        }
    }
}
