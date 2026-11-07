package com.example.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.MediaSearchResultItem
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MediaProvidersTest {
    @Test fun wildwoodSharesTmdbAndTraktIdentityAndKeepsPreferredMetadata() {
        val tmdb = MediaSearchResultItem("tmdb_movie_1", "Wildwood", "movie", releaseYear = "2026", tmdbId = 1, overview = "TMDB description")
        val trakt = MediaSearchResultItem("trakt_movie_2", "Wildwood", "movie", releaseYear = "2026", tmdbId = 1, traktId = 2, overview = "Trakt description")
        val result = MediaIdentity.merge(listOf(tmdb, trakt), "tmdb").single()
        assertEquals(1, result.tmdbId); assertEquals(2L, result.traktId); assertEquals("TMDB description", result.overview)
    }
    @Test fun cachedAndRemoteMangaWithNoReleaseYearMergeByMalId() {
        val cached = MediaSearchResultItem("jikan_manga_restored_123", "Manga title", "manga")
        val remote = cached.copy(id = "jikan_manga_123")
        assertEquals(1, MediaIdentity.merge(listOf(cached, remote), "trakt").size)
    }
    @Test fun remakesAndDifferentProviderIdsAreNotSilentlyMerged() {
        val first = MediaSearchResultItem("a", "Same title", "movie", releaseYear = "2026", tmdbId = 1)
        assertEquals(2, MediaIdentity.merge(listOf(first, first.copy(id = "b", tmdbId = 2)), "tmdb").size)
        assertEquals(2, MediaIdentity.merge(listOf(first, first.copy(id = "b", releaseYear = "1990", tmdbId = null)), "tmdb").size)
        assertEquals(2, MediaIdentity.merge(listOf(first, first.copy(id = "b", mediaType = "tv")), "tmdb").size)
    }
    @Test fun providerSwitchesAndLocalEndpointSurviveNewSettingsInstance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("cobalt_media_providers", 0).edit().clear().commit()
        MediaProviderSettings(context).apply {
            setEnabled("tmdb", false); priority = "tmdb"; jikanUrl = "http://192.168.1.20:8080/v4"; mangaEnabled = true
        }
        MediaProviderSettings(context).apply {
            assertFalse(enabled("tmdb")); assertTrue(enabled("trakt")); assertEquals("tmdb", priority)
            assertEquals("http://192.168.1.20:8080/v4/", jikanUrl); assertTrue(mangaEnabled)
        }
    }
    @Test fun endpointValidationRejectsCredentialsPublicHttpAndLookalikePrivateHosts() {
        listOf("http://example.com/v4", "https://user:password@example.com/v4", "https://example.com/v4?token=x", "http://10.evil.com/v4").forEach {
            try { MediaProviderSettings.validateJikanUrl(it); fail("Must reject $it") } catch (_: IllegalArgumentException) {}
        }
        assertEquals("http://10.0.2.2:8080/v4/", MediaProviderSettings.validateJikanUrl("http://10.0.2.2:8080/v4"))
    }
}
