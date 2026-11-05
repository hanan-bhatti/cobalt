package com.example.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LinkPreviewQueueTest {
    @Test fun resultsCannotOverwriteNewDescriptionOrResurrectTrash() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            val item = SavedItem(type = SavedItemType.LINK, title = "Original", content = "https://example.org", linkDescription = "Before")
            val dao = db.savedItemDao()
            dao.insertItem(SavedItemEntity.fromDomainModel(item))
            db.linkPreviewDao().put(LinkPreviewEntity(item.id, item.content))
            assertEquals(item.id, db.linkPreviewDao().next()?.itemId)
            dao.updateTitleAndDescription(item.id, "My title", "My description", 1)
            assertEquals(0, dao.applyLinkPreview(item.id, item.content, "Original", "Before", "Page", "Page description", null))
            assertEquals("My description", dao.getItemById(item.id)?.linkDescription)
            // A matching description still cannot overwrite a separately edited title.
            assertEquals(1, dao.applyLinkPreview(item.id, item.content, "Original", "My description", "Page", "My description", null))
            assertEquals("My title", dao.getItemById(item.id)?.title)
            dao.setDeleted(item.id, true, 2)
            assertNull(db.linkPreviewDao().next())
            assertEquals(0, dao.applyLinkPreview(item.id, item.content, "My title", "My description", "Page", "Page description", null))
            assertTrue(dao.isItemDeleted(item.id) == true)
        } finally { db.close() }
    }

    @Test fun changedUrlDoesNotReceiveOldResult() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            val item = SavedItem(type = SavedItemType.LINK, title = "New URL", content = "https://example.org/new")
            db.savedItemDao().insertItem(SavedItemEntity.fromDomainModel(item))
            db.linkPreviewDao().put(LinkPreviewEntity(item.id, "https://example.org/old"))
            assertNull(db.linkPreviewDao().next())
            assertEquals(0, db.savedItemDao().applyLinkPreview(item.id, "https://example.org/old", item.title, null, "Old page", "Wrong", null))
        } finally { db.close() }
    }
}
