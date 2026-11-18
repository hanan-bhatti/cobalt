package com.example.media.recommendation

import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.SavedItemEntity
import com.example.data.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ScreeningDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun fixture(block: suspend (SavedItem, ScreeningStore, ScreeningRecommendation) -> Unit) {
        val db = AppDatabase.getDatabase(context)
        val now = System.currentTimeMillis()
        val item = SavedItem("screening_test_${UUID.randomUUID()}", SavedItemType.MEDIA, "Screening test · five episodes", "", timestamp = now - 40 * SCREENING_DAY,
            mediaType = "tv", watchStatus = "Watching", showProgress = TvShowProgress(totalAired = 5, totalWatched = 3))
        val store = ScreeningStore(context)
        cleanupScreeningDeviceFixtures(context)
        val key = ScreeningRecommendationEngine.key(item)
        db.savedItemDao().insertItem(SavedItemEntity.fromDomainModel(item))
        try {
            val pick = ScreeningRecommendationEngine.rank(listOf(item), store.observe(listOf(item)), now, includeCooldowns = false).single()
            block(item, store, pick)
        } finally {
            context.openOrCreateDatabase("screening_learning.db", Context.MODE_PRIVATE, null).use { sqlite ->
                sqlite.rawQuery("SELECT token FROM deliveries WHERE item_id = ?", arrayOf(item.id)).use { cursor -> while (cursor.moveToNext()) ScreeningNotifications.cancel(context, cursor.getString(0)) }
                sqlite.delete("deliveries", "item_id = ?", arrayOf(item.id)); sqlite.delete("signals", "k = ?", arrayOf(key)); sqlite.delete("feedback", "k = ?", arrayOf(key))
            }
            db.savedItemDao().deleteById(item.id)
            cleanupScreeningDeviceFixtures(context, listOf(item.id))
        }
    }
    @Test fun actualNotificationActionPersistsIdempotentlyWithoutChangingWatchProgress() = runBlocking {
        fixture { item, store, pick ->
            val now = System.currentTimeMillis()
            val delivery = store.createDelivery(pick, now, ScreeningSchedule.day(now), false)!!
            assertTrue("Grant notification permission before running device test", ScreeningNotifications.show(context, pick, delivery))
            val active = context.getSystemService(NotificationManager::class.java).activeNotifications.single { it.tag == delivery.token }
            assertEquals(3, active.notification.actions.size)
            val action = active.notification.actions.single { it.title.toString() == "Not interested" }.actionIntent
            action.send(); action.send()
            repeat(50) { if (store.delivery(delivery.token)!!.answered) return@repeat; delay(200) }
            assertTrue(store.delivery(delivery.token)!!.answered)
            assertTrue(store.snapshot().items.getValue(pick.key).notInterested)
            assertEquals(1, store.snapshot().feedback.count { it.key == pick.key })
            val latest = AppDatabase.getDatabase(context).savedItemDao().getItemById(item.id)!!.toDomainModel()
            assertEquals("Watching", latest.watchStatus); assertEquals(3, latest.showProgress!!.totalWatched)
            assertFalse(context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.tag == delivery.token })
        }
    }
    @Test fun confirmedFinishOnMissingProviderIdKeepsEpisodeHistoryAndHasNoSyncOutbox() = runBlocking {
        fixture { item, store, pick ->
            val now = System.currentTimeMillis()
            val d = store.createDelivery(pick, now, ScreeningSchedule.day(now), false)!!
            assertTrue(ScreeningService.answer(context, d.token, ScreeningFeedback.WATCHED))
            assertFalse(ScreeningService.answer(context, d.token, ScreeningFeedback.WATCHED))
            val latest = AppDatabase.getDatabase(context).savedItemDao().getItemById(item.id)!!.toDomainModel()
            assertEquals("Completed", latest.watchStatusOverride); assertEquals(3, latest.showProgress!!.totalWatched)
        }
    }
    @Test fun removedTitleIgnoresOldNotificationAction() = runBlocking {
        fixture { item, store, pick ->
            val now = System.currentTimeMillis()
            val d = store.createDelivery(pick, now, ScreeningSchedule.day(now), false)!!
            AppDatabase.getDatabase(context).savedItemDao().setDeleted(item.id, true, now)
            assertFalse(ScreeningService.answer(context, d.token, ScreeningFeedback.USEFUL))
            assertFalse(store.delivery(d.token)!!.answered)
            assertEquals(0, store.snapshot().feedback.count { it.key == pick.key })
        }
    }
}
