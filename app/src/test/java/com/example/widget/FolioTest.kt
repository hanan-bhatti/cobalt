package com.example.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.local.CustomFolderEntity
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FolioTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun item(id: String, time: Long = 1, updated: Long = time) = SavedItem(id = id, type = SavedItemType.TEXT, title = id, content = "Private full body", timestamp = time, updatedAt = updated, folders = listOf("Ideas"))
    @Before fun clear() {
        for (name in listOf("cobalt_folios", "cobalt_folio_snapshots", "cobalt_settings", "note_migration")) context.getSharedPreferences(name, 0).edit().clear().commit()
        context.getSharedPreferences("note_migration", 0).edit().putInt("completed_format", 1).commit()
        context.getSharedPreferences("cobalt_settings", 0).edit().putBoolean("dynamic_color", false).putString("theme_mode", "Light").commit()
    }
    @Test fun filteringAndStableOrderingKeepTotalCount() {
        val items = listOf(item("b", 2, 5), item("a", 1, 5), item("c", 3, 4), item("deleted").copy(isDeleted = true), item("archived").copy(isArchived = true), item("other").copy(folders = listOf("Work")))
        assertEquals(listOf("a", "b", "c"), FolioItems.select(items, FolioConfig("Ideas")).map { it.id })
        assertEquals(listOf("c", "b", "a"), FolioItems.select(items, FolioConfig("Ideas", order = FolioOrder.NEWEST)).map { it.id })
        assertEquals(listOf("a", "b", "c"), FolioItems.select(items, FolioConfig("Ideas", order = FolioOrder.OLDEST)).map { it.id })
        assertTrue(FolioItems.select(items, FolioConfig()).isEmpty())
    }
    @Test fun instancesPinCallbackRenameAndDeletionAreIndependent() {
        val store = FolioConfigStore(context)
        store.save(10, FolioConfig("Ideas", "#F3D986")); store.save(11, FolioConfig("Work", order = FolioOrder.OLDEST))
        val token = store.stagePin(FolioConfig("Ideas", order = FolioOrder.NEWEST))
        assertEquals(FolioConfig(), store.get(12))
        store.renameFolder("Ideas", "Reading")
        assertTrue(FolioConfigStore(context).finishPin(token, 12))
        assertFalse(store.finishPin(token, 13))
        assertEquals("Reading", store.get(10).folderName); assertEquals("Reading", store.get(12).folderName)
        assertEquals("Work", store.get(11).folderName)
        store.removeFolder("Reading")
        assertNull(store.get(10).folderName); assertEquals("Reading", store.get(10).removedFolder)
        assertEquals(FolioOrder.NEWEST, store.get(12).order)
        store.delete(10); assertEquals(FolioConfig(), store.get(10)); assertEquals("Work", store.get(11).folderName)
    }
    @Test fun expiredAndMalformedPinRequestsCannotConfigureWidgets() {
        val store = FolioConfigStore(context)
        val token = store.stagePin(FolioConfig("Ideas"))
        store.preferences.edit().putLong("pin_${token}_time", System.currentTimeMillis() - 86_400_001).commit()
        assertFalse(store.finishPin(token, 1)); assertFalse(store.finishPin("unknown", 1))
        store.preferences.edit().putString("widget_1", "broken json").commit()
        assertEquals(FolioConfig(), store.get(1))
    }
    @Test fun canceledPinAllocationIsNotListedAsPlaced() {
        val shadow = shadowOf(AppWidgetManager.getInstance(context))
        val info = AppWidgetProviderInfo().apply { provider = ComponentName(context, FolioWidgetProvider::class.java) }
        shadow.addBoundWidget(51, info)
        assertTrue(FolioWidgetProvider.ids(context).contains(51))
        assertFalse(FolioWidgetProvider.placedIds(context).contains(51))
        val store = FolioConfigStore(context)
        val token = store.stagePin(FolioConfig("Ideas"))
        assertFalse(store.finishPin(token, -1)); assertTrue(store.finishPin(token, 51))
        assertTrue(FolioWidgetProvider.placedIds(context).contains(51))
    }
    @Test fun timeoutAndDatabaseFailurePreserveUsableDurablePreview() = runBlocking {
        val shadow = shadowOf(AppWidgetManager.getInstance(context))
        shadow.addBoundWidget(52, AppWidgetProviderInfo().apply { provider = ComponentName(context, FolioWidgetProvider::class.java) })
        FolioConfigStore(context).save(52, FolioConfig("Ideas"))
        FolioSnapshotStore(context).save(52, CustomFolderEntity("Ideas"), listOf(item("Last good note")))
        assertFalse(FolioWidgetProvider.updateWithLoader(context, 30) { delay(1_000); emptyList<CustomFolderEntity>() to emptyList<SavedItem>() })
        assertEquals("Last good note", FolioSnapshotStore(context).get(52, FolioConfig("Ideas"))!!.items.single().title)
        assertFalse(FolioWidgetProvider.updateWithLoader(context) { error("Database unavailable") })
        val view = shadow.getViewFor(52)
        assertEquals(View.GONE, view.findViewById<View>(R.id.folio_initial_message).visibility)
        assertEquals("Last good note", view.findViewById<View>(R.id.folio_item_one).contentDescription)
    }
    @Test fun folderColorWinsAndOverrideOnlyAppliesToUncoloredFolders() {
        val red = FolioConfig("Ideas", "#FF6B6B")
        val blue = red.copy(colorHex = "#29B6F6")
        val folder = CustomFolderEntity("Ideas", colorHex = "#4CAF50")
        fun render(c: FolioConfig, f: CustomFolderEntity) = FolioRenderer.render(context, c, f, emptyList(), 316, 248)
        assertTrue(render(red, folder).sameAs(render(blue, folder)))
        assertFalse(render(red, folder.copy(colorHex = null)).sameAs(render(blue, folder.copy(colorHex = null))))
    }
    @Test fun projectionParsesFolderNamesContainingCommasAndQuotes() {
        val row = FolioItemRow("1", "Text", "Note", 1, 2, "[\"Reading, later\",\"My \\\"ideas\\\"\",\"📚\"]", null)
        assertEquals(listOf("Reading, later", "My \"ideas\"", "📚"), row.toItem().folders)
        assertEquals(SavedItemType.TEXT, row.toItem().type)
    }
    @Test fun durableSnapshotIsBoundedAndNeverStoresNoteBodies() {
        val cache = FolioSnapshotStore(context)
        cache.save(1, CustomFolderEntity("Ideas"), (1..100).map { item("item $it") })
        val saved = FolioSnapshotStore(context).get(1, FolioConfig("Ideas"))!!
        assertEquals(100, saved.count); assertEquals(2, saved.items.size)
        assertTrue(saved.items.all { it.content.isEmpty() })
        assertFalse(context.getSharedPreferences("cobalt_folio_snapshots", 0).getString("1", "")!!.contains("Private full body"))
        assertNull(cache.get(1, FolioConfig("Work"))); assertNull(cache.get(1, FolioConfig(removedFolder = "Ideas")))
        cache.delete(1); assertNull(cache.get(1, FolioConfig("Ideas")))
    }
    @Test fun remoteViewsInflateWithoutGlanceOrLoadingLayout() {
        val views = FolioWidgetProvider.views(context, 1, FolioConfig("Ideas"), CustomFolderEntity("Ideas"), listOf(item("One")))
        val root = views.apply(context, FrameLayout(context))
        assertNotNull(root.findViewById<ImageView>(R.id.folio_image).drawable)
        assertEquals(View.GONE, root.findViewById<View>(R.id.folio_initial_message).visibility)
        assertEquals("One", root.findViewById<View>(R.id.folio_item_one).contentDescription)
        val empty = FolioWidgetProvider.views(context, 2, FolioConfig(), null, emptyList()).apply(context, FrameLayout(context))
        assertTrue(empty.findViewById<View>(R.id.folio_open).contentDescription.contains("Choose a folder"))
    }
    @Test fun appThemeOverridesSystemAndNoteOnlyFontsDoNotChangeWidget() {
        val prefs = context.getSharedPreferences("cobalt_settings", 0)
        val folder = CustomFolderEntity("Ideas")
        fun render() = FolioRenderer.render(context, FolioConfig("Ideas"), folder, listOf(item("One")), 316, 248)
        val original = render()
        prefs.edit().putString("pref_text_font", "lexend").putBoolean("pref_apply_to_app_ui", false).commit()
        assertTrue(original.sameAs(render()))
        prefs.edit().putString("theme_mode", "Dark").commit()
        assertTrue(FolioRenderer.isDark(context)); assertFalse(original.sameAs(render()))
        prefs.edit().putString("theme_mode", "Light").commit()
        assertFalse(FolioRenderer.isDark(context))
    }
    @Test fun renderVisualFixturesIncludingEmptyMissingAndLargeText() {
        val directory = File("build/folio-validation").apply { mkdirs() }
        val prefs = context.getSharedPreferences("cobalt_settings", 0)
        val items = listOf(item("A quieter kind of\nproductivity"), item("Forest trail, morning").copy(type = SavedItemType.IMAGE))
        for (theme in listOf("Light", "Dark")) {
            prefs.edit().putString("theme_mode", theme).commit()
            File(directory, "folio-${theme.lowercase()}.png").outputStream().use { FolioRenderer.render(context, FolioConfig("Ideas"), CustomFolderEntity("Ideas"), items, 948, 744).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        for (count in 0..2) assertNotNull(FolioRenderer.render(context, FolioConfig("A very long folder name with emoji 📚 and many words"), CustomFolderEntity("A very long folder name with emoji 📚 and many words"), items.take(count), 180, 142))
        assertNotNull(FolioRenderer.render(context, FolioConfig(removedFolder = "Ideas"), null, emptyList(), 180, 142))
    }
}
