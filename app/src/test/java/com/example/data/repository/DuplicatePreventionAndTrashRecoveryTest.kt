package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.notes.TrashManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DuplicatePreventionAndTrashRecoveryTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: CobaltRepository
    private lateinit var trashManager: TrashManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        trashManager = TrashManager(context, database)
        runBlocking { trashManager.setEnabled(true) }
        repository = CobaltRepository(context, database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun movie_duplicate_detection_by_tmdbId_traktId_imdbId() = runBlocking {
        val movie1 = SavedItem(
            id = "movie-1",
            type = SavedItemType.MEDIA,
            title = "Dune: Part Two",
            content = "https://trakt.tv/movies/dune-2",
            tmdbId = 693134,
            traktId = 593134L,
            imdbId = "tt15239678",
            mediaType = "movie"
        )
        repository.saveNewMediaItem(movie1)

        // Find by tmdbId
        val existingByTmdb = repository.findExistingMediaItem(tmdbId = 693134, traktId = null, imdbId = null, id = "other-id")
        assertNotNull(existingByTmdb)
        assertEquals("movie-1", existingByTmdb?.id)
        assertFalse(existingByTmdb?.isDeleted ?: true)

        // Find by traktId
        val existingByTrakt = repository.findExistingMediaItem(tmdbId = null, traktId = 593134L, imdbId = null, id = "other-id")
        assertNotNull(existingByTrakt)
        assertEquals("movie-1", existingByTrakt?.id)

        // Find by imdbId
        val existingByImdb = repository.findExistingMediaItem(tmdbId = null, traktId = null, imdbId = "tt15239678", id = "other-id")
        assertNotNull(existingByImdb)
        assertEquals("movie-1", existingByImdb?.id)

        // Attempting to save again returns active without creating duplicate
        val movie2 = movie1.copy(id = "movie-2", title = "Dune 2 Duplicate")
        val savedResult = repository.saveNewMediaItem(movie2)
        assertEquals("movie-1", savedResult.id)
    }

    @Test
    fun movie_trash_recovery_on_re_add() = runBlocking {
        val movie = SavedItem(
            id = "movie-trash-test",
            type = SavedItemType.MEDIA,
            title = "Spirited Away",
            content = "https://trakt.tv/movies/spirited-away",
            tmdbId = 129,
            mediaType = "anime"
        )
        val saved = repository.saveNewMediaItem(movie)

        // Delete movie -> moved to Trash
        val deleted = trashManager.delete(saved.id)
        assertTrue(deleted)

        // Verify it is marked as deleted
        val inDbAfterDelete = database.savedItemDao().getItemById(saved.id)
        assertTrue(inDbAfterDelete?.isDeleted ?: false)

        // Re-adding the movie should recover it from Trash
        val recovered = repository.saveNewMediaItem(movie.copy(watchStatus = "Watching"))
        assertEquals(saved.id, recovered.id)
        assertFalse(recovered.isDeleted)

        val inDbAfterRecovery = database.savedItemDao().getItemById(saved.id)
        assertFalse(inDbAfterRecovery?.isDeleted ?: true)
    }

    @Test
    fun link_duplicate_and_trash_recovery() = runBlocking {
        val link = SavedItem(
            id = "link-1",
            type = SavedItemType.LINK,
            title = "GitHub",
            content = "https://github.com/example/repo"
        )
        repository.saveItem(link)

        // Check duplicate with trailing slash
        val foundWithSlash = repository.findExistingLink("https://github.com/example/repo/")
        assertNotNull(foundWithSlash)
        assertEquals("link-1", foundWithSlash?.id)

        // Delete link to Trash
        trashManager.delete(link.id)
        val inTrash = repository.findExistingLink("https://github.com/example/repo")
        assertNotNull(inTrash)
        assertTrue(inTrash?.isDeleted ?: false)

        // Restore link from trash
        trashManager.restore(link.id)
        val restored = repository.findExistingLink("https://github.com/example/repo")
        assertNotNull(restored)
        assertFalse(restored?.isDeleted ?: true)
    }

    @Test
    fun folder_duplicate_name_case_insensitive_detection() {
        val existing = listOf("Work", "Personal", "Archive")
        
        fun isDuplicate(name: String): Boolean {
            val trimmed = name.trim()
            return trimmed.isNotBlank() && existing.any { it.trim().equals(trimmed, ignoreCase = true) }
        }

        assertTrue(isDuplicate("work"))
        assertTrue(isDuplicate("WORK"))
        assertTrue(isDuplicate("  Personal  "))
        assertFalse(isDuplicate("Work Projects"))
        assertFalse(isDuplicate("Reading"))
    }
}
