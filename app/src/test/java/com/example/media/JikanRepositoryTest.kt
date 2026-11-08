package com.example.media

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.CobaltRepository
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class JikanRepositoryTest {
    private class Fixture : AutoCloseable {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val mangaCalls = AtomicInteger()
        var broken = false
        var unknownCounts = false
        var credentialsSeen = false
        val settings = MediaProviderSettings(context)
        val repo = CobaltRepository(context, db)
        init {
            server.createContext("/v4/") { request ->
                if (request.requestHeaders.getFirst("Authorization") != null || request.requestURI.toString().contains("api_key")) credentialsSeen = true
                val manga = request.requestURI.path.endsWith("/manga")
                if (manga) mangaCalls.incrementAndGet()
                val counts = if (unknownCounts) "\"chapters\":null,\"volumes\":null" else "\"chapters\":42,\"volumes\":8"
                val body = if (broken) "{invalid" else if (manga)
                    """{"data":[{"mal_id":123,"title":"Fixture Manga","title_english":"","synopsis":"A local catalogue fixture.",$counts,"status":"Publishing","score":8.5,"genres":[{"name":"Adventure"}]}]}"""
                    else """{"data":[{"mal_id":123,"title":"Fixture Anime","episodes":12}]}"""
                val bytes = body.toByteArray()
                request.responseHeaders.add("Content-Type", "application/json")
                request.sendResponseHeaders(200, bytes.size.toLong())
                request.responseBody.use { it.write(bytes) }
            }
            server.start()
            context.getSharedPreferences("cobalt_media_providers", 0).edit().clear().commit()
            settings.setEnabled("trakt", false); settings.setEnabled("tmdb", false)
            settings.jikanUrl = "http://127.0.0.1:${server.address.port}/v4/"
        }
        override fun close() { server.stop(0); db.close() }
    }

    @Test fun mangaSearchSaveDuplicateAndCachedFallbackKeepChapterFactsSeparateFromAnime() = runBlocking {
        Fixture().use { f ->
            val results = f.repo.searchMedia("Fixture")
            assertEquals(setOf("anime", "manga"), results.map { it.mediaType }.toSet())
            val manga = results.single { it.mediaType == "manga" }
            assertEquals("Fixture Manga", manga.title)
            val saved = f.repo.saveNewMediaItem(manga.toSavedMedia(status = "Watching"))
            val duplicate = f.repo.saveNewMediaItem(manga.toSavedMedia())
            assertEquals(saved.id, duplicate.id)
            assertEquals("Watching", duplicate.watchStatus)
            val fromDisk = f.db.savedItemDao().getItemById(saved.id)!!.toDomainModel()
            assertEquals(42, fromDisk.mediaFacts!!.chapterCount)
            assertEquals(8, fromDisk.mediaFacts!!.volumeCount)
            assertEquals("Reading", mediaStatusLabel(fromDisk.watchStatus, fromDisk.mediaType))
            assertEquals(0, f.db.traktOutboxDao().pendingCount("any-session"))
            f.broken = true
            var warning: String? = null
            val cached = f.repo.searchMedia("Fixture") { warning = it }.single()
            assertEquals(saved.id, cached.id)
            assertEquals(42, cached.mediaFacts!!.chapterCount)
            assertNotNull(warning)
            assertFalse(f.credentialsSeen)
        }
    }

    @Test fun disablingMangaStopsRequestsAndUnknownCountsStayUnknown() = runBlocking {
        Fixture().use { f ->
            f.settings.mangaEnabled = false
            assertEquals("anime", f.repo.searchMedia("Fixture").single().mediaType)
            assertEquals(0, f.mangaCalls.get())
            f.settings.mangaEnabled = true
            f.unknownCounts = true
            val manga = f.repo.searchMedia("Fixture").single { it.mediaType == "manga" }
            assertNull(manga.mediaFacts!!.chapterCount)
            assertNull(manga.mediaFacts!!.volumeCount)
        }
    }
}
