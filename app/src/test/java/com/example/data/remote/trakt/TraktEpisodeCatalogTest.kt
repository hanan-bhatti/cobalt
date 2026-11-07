package com.example.data.remote.trakt

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.*
import com.example.data.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TraktEpisodeCatalogTest {
    @Test fun failedMultiBatchRefreshKeepsTheEntirePreviousSeason() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
        val api = object : TraktApiService by FakeTraktApiService() {
            override suspend fun getShowSeasonEpisodes(id: String, season: Int) = (1..150).map { TraktSeasonEpisodeItem(season = 1,number = it,title = "New $it") }
        }
        val item = SavedItem(id = "show",type = SavedItemType.MEDIA,title = "Show",content = "",mediaType = "tv",traktId = 1)
        TraktSyncEngine.apiServiceProvider = { api }
        try {
            db.savedItemDao().insertItem(SavedItemEntity.fromDomainModel(item))
            db.traktEpisodeDao().put(listOf(TraktEpisodeEntity(item.id,1,1,"Original",null,null,42,8.0)))
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_episode BEFORE INSERT ON trakt_episode_cache WHEN NEW.number = 101 BEGIN SELECT RAISE(ABORT,'test failure'); END")
            try { TraktEpisodeCatalog.load(context,item,1,true,db); fail("Injected transaction failure must surface") } catch (_: android.database.sqlite.SQLiteException) {}
            assertEquals("Original",db.traktEpisodeDao().season(item.id,1).single().title)
        } finally { TraktSyncEngine.apiServiceProvider = null; db.close() }
    }
    @Test fun lateSeasonFetchCannotRecreateADeletedItemsEpisodeCache() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
        val item = SavedItem(id = "show",type = SavedItemType.MEDIA,title = "Show",content = "",mediaType = "tv",traktId = 1)
        val api = object : TraktApiService by FakeTraktApiService() {
            override suspend fun getShowSeasonEpisodes(id: String, season: Int): List<TraktSeasonEpisodeItem> {
                db.savedItemDao().setDeleted(item.id,true,1)
                return listOf(TraktSeasonEpisodeItem(number = 1))
            }
        }
        TraktSyncEngine.apiServiceProvider = { api }
        try {
            db.savedItemDao().insertItem(SavedItemEntity.fromDomainModel(item))
            TraktEpisodeCatalog.load(context,item,1,true,db)
            assertTrue(db.traktEpisodeDao().season(item.id,1).isEmpty())
        } finally { TraktSyncEngine.apiServiceProvider = null; db.close() }
    }
}
