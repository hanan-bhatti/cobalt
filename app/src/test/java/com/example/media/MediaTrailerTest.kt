package com.example.media

import com.example.ui.screens.extractYouTubeVideoId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MediaTrailerTest {
    @Test fun trailerLinksSupportWatchShortAndEmbedFormatsWithoutAcceptingLookalikeDomains() {
        val id = "abcdefghijk"
        listOf("https://www.youtube.com/watch?v=$id&t=3", "https://youtu.be/$id", "https://youtube.com/shorts/$id", "https://www.youtube-nocookie.com/embed/$id").forEach {
            assertEquals(id, extractYouTubeVideoId(it))
        }
        assertNull(extractYouTubeVideoId("https://notyoutube.com/watch?v=$id"))
        assertNull(extractYouTubeVideoId("javascript:alert(1)"))
    }
    @Test fun dockAndCaptureKeepProviderIdsAndMangaFacts() {
        val result = com.example.data.remote.MediaSearchResultItem("tmdb_movie_550","Title","movie",tmdbId = 550, traktId = 10,
            mediaFacts = com.example.data.model.MediaFacts("Jikan", chapterCount = 12, volumeCount = 3))
        val saved = result.toSavedMedia()
        assertEquals(550,saved.tmdbId); assertEquals(10L,saved.traktId)
        assertEquals(12,saved.mediaFacts!!.chapterCount)
        assertEquals(saved.mediaFacts,com.example.data.model.MediaFacts.fromJson(saved.mediaFacts.toJson()))
    }
}
