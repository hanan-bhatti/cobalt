package com.example.media.recommendation

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.FrameLayout
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.SavedItemEntity
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.widget.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ScreeningWidgetDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun realThumbPendingIntentsSwitchClearAndLeaveThePickAndWatchStatusIntact() = runBlocking {
        val host = AppWidgetHost(context, 20261112)
        val manager = AppWidgetManager.getInstance(context)
        val store = ScreeningWidgetStore(context)
        val database = AppDatabase.getDatabase(context)
        cleanupScreeningDeviceFixtures(context)
        val now = System.currentTimeMillis()
        val items = (1..3).map { SavedItem("screening_widget_test_${UUID.randomUUID()}", SavedItemType.MEDIA,
            "Screening widget test $it", "", timestamp = now - 30 * SCREENING_DAY, mediaType = "movie", watchStatus = "Plan to Watch") }
        val ids = mutableListOf<Int>()
        try {
            items.forEach { database.savedItemDao().insertItem(SavedItemEntity.fromDomainModel(it)) }
            val ranked = ScreeningRecommendationEngine.rank(items, now = now, includeCooldowns = false)
            repeat(2) {
                val id = host.allocateAppWidgetId(); ids += id
                assertTrue("Grant appwidget bind permission before running this test", manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, ScreeningWidgetProvider::class.java)))
                store.save(id, ScreeningWidgetSelection.update(ScreeningWidgetEntry(config = ScreeningWidgetConfig(ScreeningSource.MOVIES)), ranked, id, ScreeningSchedule.day(now)))
            }
            assertTrue(ScreeningWidgetProvider.updateAll(context))
            val before = store.get(ids[0]); val other = store.get(ids[1])
            val beforePick = requireNotNull(before.pick)
            val views = ScreeningWidgetProvider.views(context, ids[0], before)
            suspend fun tap(target: Int, expected: ScreeningThumb?) {
                instrumentation.runOnMainSync {
                    assertTrue(views.apply(context, FrameLayout(context)).findViewById<android.view.View>(target).performClick())
                }
                val deadline = System.currentTimeMillis() + 5_000
                while (store.get(ids[0]).thumb != expected && System.currentTimeMillis() < deadline) delay(100)
                assertEquals(expected,store.get(ids[0]).thumb)
                assertEquals(before.pick,store.get(ids[0]).pick)
                assertEquals(before.generation,store.get(ids[0]).generation)
            }
            tap(R.id.screening_like,ScreeningThumb.LIKED)
            tap(R.id.screening_dislike,ScreeningThumb.DISLIKED)
            tap(R.id.screening_dislike,null)
            assertEquals(other,store.get(ids[1]))
            val live = database.savedItemDao().getItemById(beforePick.itemId)!!.toDomainModel()
            assertEquals("Plan to Watch", live.watchStatus)
            assertEquals(0, ScreeningStore(context).snapshot().feedback.count { it.key == beforePick.key })
        } finally {
            ids.forEach { host.deleteAppWidgetId(it); store.delete(it) }
            items.forEach { database.savedItemDao().deleteById(it.id) }
            context.openOrCreateDatabase("screening_learning.db", Context.MODE_PRIVATE, null).use { sqlite ->
                items.forEach {
                    store.preferences.edit().remove("thumb_${it.id}").commit()
                    sqlite.delete("signals", "item_id = ?", arrayOf(it.id))
                    sqlite.delete("deliveries", "item_id = ?", arrayOf(it.id))
                    sqlite.delete("feedback", "k = ?", arrayOf(ScreeningRecommendationEngine.key(it)))
                }
            }
            cleanupScreeningDeviceFixtures(context, items.map { it.id })
            WidgetUpdater.update(context)
        }
    }

    @Test fun rendererMatchesAndroidWallpaperPaletteInBothAppModes() {
        val prefs = context.getSharedPreferences("cobalt_settings", Context.MODE_PRIVATE)
        val mode = prefs.getString("theme_mode", null)
        val dynamic = if (prefs.contains("dynamic_color")) prefs.getBoolean("dynamic_color", true) else null
        try {
            for (dark in listOf(false, true)) {
                prefs.edit().putString("theme_mode", if (dark) "Dark" else "Light").putBoolean("dynamic_color", true).commit()
                val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                val bitmap = ScreeningRenderer.render(context, ScreeningWidgetEntry(), 316, 300)
                assertEquals(scheme.surfaceContainer.toArgb(), bitmap.getPixel(10, 205))
                assertEquals(dark, FolioRenderer.isDark(context))
            }
        } finally {
            prefs.edit().apply {
                if (mode == null) remove("theme_mode") else putString("theme_mode", mode)
                if (dynamic == null) remove("dynamic_color") else putBoolean("dynamic_color", dynamic)
            }.commit()
            WidgetUpdater.update(context)
        }
    }
}
