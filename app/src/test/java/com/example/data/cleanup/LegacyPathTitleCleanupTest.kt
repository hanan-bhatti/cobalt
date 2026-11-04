/*
 * Cobalt - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 */

package com.example.data.cleanup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.local.SavedItemEntity
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.repository.CobaltRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LegacyPathTitleCleanupTest {

    private lateinit var context: Context
    private lateinit var repository: CobaltRepository
    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(LegacyPathTitleCleanup.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = CobaltRepository(context, database)
    }

    @After
    fun tearDown() {
        database.close()
        context.getSharedPreferences(LegacyPathTitleCleanup.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private suspend fun insertDirectEntity(
        id: String = UUID.randomUUID().toString(),
        type: SavedItemType,
        title: String,
        content: String = "content",
        linkTitle: String? = null,
        linkDescription: String? = null
    ): String {
        val item = SavedItem(
            id = id,
            type = type,
            title = title,
            content = content,
            linkTitle = linkTitle,
            linkDescription = linkDescription
        )
        database.savedItemDao().insertItem(SavedItemEntity.fromDomainModel(item))
        return id
    }

    @Test
    fun handlesEmptyDatabase() = runBlocking {
        val count = LegacyPathTitleCleanup.performCleanup(context, repository)
        assertEquals(0, count)
        assertTrue(LegacyPathTitleCleanup.isCleanupDone(context))

        // Subsequent run should do nothing
        val second = LegacyPathTitleCleanup.performCleanup(context, repository)
        assertEquals(0, second)
    }

    @Test
    fun dryRunCountsWithoutModifyingData() = runBlocking {
        insertDirectEntity(
            type = SavedItemType.IMAGE,
            title = "/data/user/0/com.example/cache/media_c3b71a11-6655-4abc-8def-0123456789ab.jpg"
        )
        insertDirectEntity(
            type = SavedItemType.VIDEO,
            title = "media_12345678-abcd-ef01-2345-6789abcdef01.mp4"
        )
        insertDirectEntity(
            type = SavedItemType.TEXT,
            title = "Valid User Title"
        )

        val needed = LegacyPathTitleCleanup.countItemsNeedingCleanup(repository)
        assertEquals(2, needed)

        // Flag should not be set by a dry-run
        assertFalse(LegacyPathTitleCleanup.isCleanupDone(context))

        // Data should remain untouched
        val items = repository.getAllItems()
        assertTrue(items.any { it.title.startsWith("/data/user") })
    }

    @Test
    fun renamesOnlyBadTitlesAndLeavesUserTitlesAlone() = runBlocking {
        val badImageId = insertDirectEntity(
            type = SavedItemType.IMAGE,
            title = "/data/user/0/com.example/cache/media_c3b71a11-6655-4abc-8def-0123456789ab.jpg",
            content = "/data/user/0/com.example/cache/media_c3b71a11-6655-4abc-8def-0123456789ab.jpg"
        )
        val badVideoId = insertDirectEntity(
            type = SavedItemType.VIDEO,
            title = "media_c3b71a11-6655-4abc-8def-0123456789ab.mp4",
            content = "/data/user/0/com.example/cache/media_c3b71a11-6655-4abc-8def-0123456789ab.mp4"
        )
        val goodNoteId = insertDirectEntity(
            type = SavedItemType.TEXT,
            title = "Shopping List For Party",
            content = "- Milk\n- Apples"
        )
        val goodImageId = insertDirectEntity(
            type = SavedItemType.IMAGE,
            title = "Sunset at the Beach",
            content = "/data/user/0/com.example/files/media/sunset.jpg"
        )
        val badLinkWithGoodMetaId = insertDirectEntity(
            type = SavedItemType.LINK,
            title = "https://example.com/cache/media_abc12345.html",
            content = "https://example.com/article",
            linkTitle = "Fascinating Article"
        )
        val itemWithBadDescriptionId = insertDirectEntity(
            type = SavedItemType.TEXT,
            title = "Normal Title",
            content = "Note text",
            linkDescription = "/data/user/0/com.example/cache/leaked_path.txt"
        )

        val updatedCount = LegacyPathTitleCleanup.performCleanup(context, repository)
        assertEquals(4, updatedCount)
        assertTrue(LegacyPathTitleCleanup.isCleanupDone(context))

        val allItems = repository.getAllItems().associateBy { it.id }

        // Bad image title replaced with localized fallback
        assertEquals("Untitled image", allItems[badImageId]?.title)

        // Bad video title replaced with localized fallback
        assertEquals("Untitled video", allItems[badVideoId]?.title)

        // User typed titles left untouched
        assertEquals("Shopping List For Party", allItems[goodNoteId]?.title)
        assertEquals("Sunset at the Beach", allItems[goodImageId]?.title)

        // Recovered from linkTitle
        assertEquals("Fascinating Article", allItems[badLinkWithGoodMetaId]?.title)

        // Bad description cleared, good title untouched
        assertEquals("Normal Title", allItems[itemWithBadDescriptionId]?.title)
        assertNull(allItems[itemWithBadDescriptionId]?.linkDescription)
    }

    @Test
    fun isIdempotentSecondRunChangesNothing() = runBlocking {
        insertDirectEntity(
            type = SavedItemType.IMAGE,
            title = "/data/user/0/com.example/cache/media_c3b71a11-6655-4abc-8def-0123456789ab.jpg"
        )

        val firstRun = LegacyPathTitleCleanup.performCleanup(context, repository)
        assertEquals(1, firstRun)
        assertTrue(LegacyPathTitleCleanup.isCleanupDone(context))

        val secondRun = LegacyPathTitleCleanup.performCleanup(context, repository)
        assertEquals(0, secondRun)
    }
}
