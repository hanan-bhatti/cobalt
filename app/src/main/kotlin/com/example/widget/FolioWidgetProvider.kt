package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.CustomFolderEntity
import com.example.data.model.SavedItem
import com.example.data.repository.CobaltRepository
import com.example.util.displayTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min

class FolioWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) = update(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = update(context)
    override fun onDeleted(context: Context, appWidgetIds: IntArray) { appWidgetIds.forEach { FolioConfigStore(context).delete(it); FolioSnapshotStore(context).delete(it) } }
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val store=FolioConfigStore(context)
        oldWidgetIds.zip(newWidgetIds).forEach { (old,new)->if(store.isConfigured(old))store.save(new,store.get(old));store.delete(old);FolioSnapshotStore(context).delete(old) }
        update(context)
    }
    private fun update(context: Context) {
        val pending=goAsync()
        scope.launch { try { runCatching { showSaved(context.applicationContext); FolioRefreshWorker.enqueue(context.applicationContext) } } finally { pending.finish() } }
    }
    companion object {
        private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        fun ids(context: Context) = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context,FolioWidgetProvider::class.java)).sorted()
        fun placedIds(context: Context): List<Int> {
            val store = FolioConfigStore(context)
            return ids(context).filter { store.isConfigured(it) }
        }
        fun showSaved(context: Context, status: String = "Saved preview · tap to open Cobalt.") {
            val manager = AppWidgetManager.getInstance(context)
            val store = FolioConfigStore(context)
            val cache = FolioSnapshotStore(context)
            ids(context).forEach { id ->
                val config = store.get(id)
                val saved = cache.get(id, config)
                runCatching { manager.updateAppWidget(id, views(context, id, config, saved?.folder, saved?.items.orEmpty(),
                    manager.getAppWidgetOptions(id), saved?.count ?: 0,
                    if (config.folderName == null) null else if (saved == null) "Open Cobalt to refresh this Folio." else status)) }
            }
        }
        suspend fun updateAll(context: Context): Boolean = updateWithLoader(context) {
            val repository = CobaltRepository(context)
            repository.getAllFolderEntitiesFlow().first() to repository.getWidgetItems()
        }
        internal suspend fun updateWithLoader(context: Context, timeoutMillis: Long = 8_000,
            load: suspend () -> Pair<List<CustomFolderEntity>, List<SavedItem>>): Boolean {
            val ids = ids(context); if (ids.isEmpty()) return true
            if (com.example.notes.StartupCrashGuard.shouldPause(context) || com.example.notes.NoteMigrationManager.shouldPauseBackground(context)) {
                showSaved(context, "Open Cobalt to finish note recovery."); return true
            }
            val snapshot = try { withTimeoutOrNull(timeoutMillis) { load() } }
                catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                catch (_: Exception) { null }
            if (snapshot == null) { showSaved(context); return false }
            val manager = AppWidgetManager.getInstance(context)
            val store = FolioConfigStore(context)
            val cache = FolioSnapshotStore(context)
            var successful = true
            ids.forEach { id ->
                // Re-read just before publication: a folder picker may have changed this ID during the query.
                val config = store.get(id)
                val folder = snapshot.first.find { it.name == config.folderName }
                val items = if (folder != null) FolioItems.select(snapshot.second, config) else emptyList()
                runCatching {
                    if (folder != null) cache.save(id, folder, items) else cache.delete(id)
                    FolioRows.prepare(context, items.take(5))
                    manager.updateAppWidget(id, views(context, id, config, folder, items, manager.getAppWidgetOptions(id)))
                    refreshLegacyList(manager, id)
                }.onFailure { successful = false; android.util.Log.w("PocketFolio", "Widget $id refresh failed", it) }
            }
            return successful
        }
        fun views(context: Context,id: Int,config: FolioConfig,folder: CustomFolderEntity?,items: List<SavedItem>,options: Bundle=Bundle(), itemCount: Int=items.size, status: String?=null): RemoteViews {
            val landscape=context.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
            val hostW=options.getInt(if(landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,316).coerceAtLeast(100)
            val hostH=options.getInt(if(landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,248).coerceAtLeast(80)
            val scale=min(hostW/FolioRenderer.WIDTH,hostH/FolioRenderer.HEIGHT)
            val dpW=FolioRenderer.WIDTH*scale;val dpH=FolioRenderer.HEIGHT*scale
            val density=context.resources.displayMetrics.density
            val pixelW=(dpW*density).toInt().coerceIn(1,900);val pixelH=(pixelW*FolioRenderer.HEIGHT/FolioRenderer.WIDTH).toInt()
            val view=RemoteViews(context.packageName,R.layout.folio_widget)
            val px=((hostW-dpW)*density/2).toInt();val py=((hostH-dpH)*density/2).toInt()
            view.setViewPadding(R.id.folio_root,px,py,px,py)
            view.setImageViewBitmap(R.id.folio_image,FolioRenderer.render(context,config,folder,items,pixelW,pixelH,itemCount,status, drawItems = false))
            view.setViewVisibility(R.id.folio_initial_message, android.view.View.GONE)
            val configure=Intent(context,FolioConfigurationActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
                .setData(Uri.parse("cobalt://folio/configure/$id"))
            val open=if(folder==null)configure else Intent(context,MainActivity::class.java)
                .setAction("com.example.ACTION_OPEN_FOLIO_FOLDER").putExtra("FOLIO_FOLDER",folder.name)
                .setData(Uri.parse("cobalt://folio/folder/$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            fun pending(intent: Intent)=PendingIntent.getActivity(context,id,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            for(v in listOf(R.id.folio_root,R.id.folio_open,R.id.folio_footer)) {
                view.setOnClickPendingIntent(v,pending(open));view.setContentDescription(v,folder?.let { "Open ${it.name}, ${itemCount} items" } ?: "Choose a folder for Pocket Folio")
            }
            val hasRows = folder != null && items.isNotEmpty()
            view.setViewVisibility(R.id.folio_list, if (hasRows) android.view.View.VISIBLE else android.view.View.GONE)
            view.setViewVisibility(R.id.folio_empty, if (hasRows) android.view.View.GONE else android.view.View.VISIBLE)
            val emptyTarget = if (folder != null && items.isEmpty()) Intent(context, MainActivity::class.java)
                .setAction("com.example.ACTION_FOLIO_CAPTURE").putExtra("FOLIO_FOLDER", folder.name).setData(Uri.parse("cobalt://folio/capture/$id")) else open
            view.setOnClickPendingIntent(R.id.folio_empty, pending(emptyTarget))
            view.setContentDescription(R.id.folio_empty, if (folder != null) "Add a note to ${folder.name}" else "Choose a folder")
            if (hasRows) {
                val padding = (18 * scale * density).toInt()
                view.setViewPadding(R.id.folio_list, padding, 0, padding, 0)
                val width = (280 * scale * density).toInt().coerceIn(100, 560)
                val template = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                view.setPendingIntentTemplate(R.id.folio_list, PendingIntent.getActivity(context, id, template,
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)))
                if (Build.VERSION.SDK_INT >= 31) {
                    val collection = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1)
                    items.take(5).forEach { collection.addItem(FolioListService.stableId(it.id), FolioListService.row(context, it, width)) }
                    view.setRemoteAdapter(R.id.folio_list, collection.build())
                } else attachLegacyList(context, view, id, width)
            }
            return view
        }
        @Suppress("DEPRECATION") // Required only by launchers before Android 12.
        private fun attachLegacyList(context: Context, views: RemoteViews, id: Int, width: Int) {
            views.setRemoteAdapter(R.id.folio_list, Intent(context, FolioListService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("rowWidth", width)
                .setData(Uri.parse("cobalt://folio/rows/$id/$width")))
        }
        @Suppress("DEPRECATION") // Android 12+ receives the collection directly in updateAppWidget.
        private fun refreshLegacyList(manager: AppWidgetManager, id: Int) {
            if (Build.VERSION.SDK_INT < 31) manager.notifyAppWidgetViewDataChanged(id, R.id.folio_list)
        }
        fun requestPin(context: Context,config: FolioConfig,folder: CustomFolderEntity,items: List<SavedItem>): Boolean {
            val manager=AppWidgetManager.getInstance(context)
            if(Build.VERSION.SDK_INT<26 || !manager.isRequestPinAppWidgetSupported)return false
            val token=FolioConfigStore(context).stagePin(config)
            val intent=Intent(context,FolioPinReceiver::class.java).setAction("com.example.FOLIO_PINNED")
                .setData(Uri.parse("cobalt://folio/pin/$token")).putExtra("token",token)
            // Android supplies the assigned widget ID in fill-in extras, so this explicit callback must be mutable.
            val callback=PendingIntent.getBroadcast(context,token.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            val extras=Bundle().apply { putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW,views(context,-1,config,folder,items)) }
            return manager.requestPinAppWidget(ComponentName(context,FolioWidgetProvider::class.java),extras,callback)
        }
    }
}

class FolioPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID)
        val token=intent.getStringExtra("token") ?: return
        val info=AppWidgetManager.getInstance(context).getAppWidgetInfo(id) ?: return
        if(info.provider!=ComponentName(context,FolioWidgetProvider::class.java))return
        if(FolioConfigStore(context).finishPin(token,id))runCatching { FolioRefreshWorker.enqueue(context) }
    }
}
