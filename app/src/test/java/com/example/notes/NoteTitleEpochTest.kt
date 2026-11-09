package com.example.notes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.SavedItemEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class NoteTitleEpochTest {
    @Test fun titleUpdatesAdvanceEpochEvenWhenClockDoesNotAdvance() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val item = SavedItemEntity(id="title-epoch",type="TEXT",title="before",content="body",timestamp=10,foldersJson="[]",extractedText=null,thumbnailPath=null,updatedAt=10)
            db.savedItemDao().insertItem(item)
            db.savedItemDao().updateTitleAndDescription(item.id,"after",null,10)
            assertTrue(db.savedItemDao().getItemById(item.id)!!.updatedAt>10)
        } finally { db.close() }
    }
}
