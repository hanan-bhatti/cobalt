package com.example.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CustomFolderEntity
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WidgetRemovalTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun bind(id: Int, provider: Class<*>) {
        shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(id,
            AppWidgetProviderInfo().apply { this.provider = ComponentName(context, provider) })
    }

    @Test fun deletionRemovesOnlyItsOwnConfigAndCacheAndLateSavesCannotRecreateIt() {
        val folios = FolioConfigStore(context)
        val screenings = ScreeningWidgetStore(context)
        bind(81, FolioWidgetProvider::class.java); bind(82, ScreeningWidgetProvider::class.java)
        folios.save(-1, FolioConfig("Draft")); folios.save(81, FolioConfig("Ideas")); folios.save(83, FolioConfig("Other"))
        screenings.save(-1, ScreeningWidgetEntry(config = ScreeningWidgetConfig(ScreeningSource.ANIME)))
        screenings.save(82, ScreeningWidgetEntry()); screenings.save(84, ScreeningWidgetEntry(config = ScreeningWidgetConfig(ScreeningSource.SHOWS)))
        FolioSnapshotStore(context).save(81, CustomFolderEntity("Ideas"), listOf(SavedItem(id = "note", type = SavedItemType.TEXT, title = "My note", content = "Body")))
        val before = screenings.get(82)

        FolioWidgetProvider().onDeleted(context, intArrayOf(81))
        ScreeningWidgetProvider().onDeleted(context, intArrayOf(82))
        // Simulate a launcher still retaining the bound ID: it must not count as a configured widget.
        assertTrue(ScreeningWidgetProvider.ids(context).contains(82))
        assertFalse(FolioWidgetProvider.placedIds(context).contains(81))
        assertFalse(ScreeningWidgetProvider.placedIds(context).contains(82))
        assertFalse(folios.saveConfigured(81, FolioConfig("Ideas")))
        assertFalse(screenings.saveConfigured(82, ScreeningWidgetConfig(ScreeningSource.MOVIES)))
        assertFalse(screenings.compareAndSave(82, before, before.copy(day = "tomorrow")))
        assertNull(FolioSnapshotStore(context).get(81, FolioConfig("Ideas")))
        assertEquals("Draft", folios.get(-1).folderName); assertEquals("Other", folios.get(83).folderName)
        assertEquals(ScreeningSource.ANIME, screenings.get(-1).config.source)
        assertEquals(ScreeningSource.SHOWS, screenings.get(84).config.source)
    }

    @Test fun inventoryIgnoresUnregisteredSettingsButPreservesThemForRestorationAndKeepsDraftsSeparate() = runBlocking {
        val folios = FolioConfigStore(context)
        val screenings = ScreeningWidgetStore(context)
        folios.save(-1, FolioConfig("Draft")); folios.save(11, FolioConfig("Keep")); folios.save(12, FolioConfig("Removed"))
        screenings.save(-1, ScreeningWidgetEntry()); screenings.save(21, ScreeningWidgetEntry()); screenings.save(22, ScreeningWidgetEntry())
        val folioPin = folios.stagePin(FolioConfig("Pending"))
        val screeningPin = screenings.stagePin(ScreeningWidgetEntry(config = ScreeningWidgetConfig(ScreeningSource.MOVIES)))
        bind(11, FolioWidgetProvider::class.java); bind(21, ScreeningWidgetProvider::class.java)
        val inventory = withTimeout(5_000) { WidgetInventory.get(context).state.first { it.ready } }
        assertEquals(listOf(11), inventory.folioIds); assertEquals(listOf(21), inventory.screeningIds)
        // Android may send onRestored after application startup: its old IDs must still be readable.
        assertTrue(folios.isConfigured(12)); assertTrue(screenings.isConfigured(22))
        assertTrue(folios.isConfigured(11)); assertTrue(screenings.isConfigured(21))
        assertEquals("Draft", folios.get(-1).folderName)
        assertTrue(folios.finishPin(folioPin, 13)); assertTrue(screenings.finishPin(screeningPin, 23))
    }
}
