package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.R
import com.example.data.model.SavedItem
import com.example.util.displayTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Pre-Android-12 launcher support; reads only the five-row snapshot, never note bodies or Room. */
class FolioListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext, intent)
    private class Factory(val context: Context, intent: Intent) : RemoteViewsFactory {
        private val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        private val width = intent.getIntExtra("rowWidth", 560).coerceIn(100, 560)
        private var rows = emptyList<SavedItem>()
        override fun onCreate() = Unit
        override fun onDataSetChanged() {
            rows = FolioSnapshotStore(context).get(id, FolioConfigStore(context).get(id))?.items.orEmpty().take(5)
            runBlocking(Dispatchers.IO) { FolioRows.prepare(context, rows) }
        }
        override fun onDestroy() { rows = emptyList() }
        override fun getCount() = rows.size
        override fun getViewAt(position: Int): RemoteViews? = rows.getOrNull(position)?.let { row(context, it, width) }
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = stableId(rows.getOrNull(position)?.id.orEmpty())
        override fun hasStableIds() = true
    }
    companion object {
        fun stableId(id: String): Long = id.fold(1125899906842597L) { hash, character -> hash * 31 + character.code }
        fun row(context: Context, item: SavedItem, width: Int): RemoteViews = RemoteViews(context.packageName, R.layout.folio_item_row).apply {
            setImageViewBitmap(R.id.folio_row_image, FolioRows.render(context, item, width))
            setContentDescription(R.id.folio_row, listOf(item.displayTitle(context), FolioRows.excerpt(item)).filter { it.isNotBlank() }.joinToString(". "))
            setOnClickFillInIntent(R.id.folio_row, Intent(Intent.ACTION_VIEW).setData(Uri.parse("cobalt://item/${item.id}")))
        }
    }
}
