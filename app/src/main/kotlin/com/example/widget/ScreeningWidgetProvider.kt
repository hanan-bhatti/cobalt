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
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.media.recommendation.ScreeningRecommendation
import com.example.media.recommendation.ScreeningSchedule
import com.example.media.recommendation.ScreeningService
import com.example.media.recommendation.ScreeningDecisionSnapshot
import com.example.media.recommendation.ScreeningStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ScreeningWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) = refresh(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refresh(context)
    override fun onDeleted(context: Context, appWidgetIds: IntArray) { appWidgetIds.forEach { ScreeningWidgetStore(context).delete(it) } }
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val store = ScreeningWidgetStore(context)
        oldWidgetIds.zip(newWidgetIds).forEach { (old, new) ->
            if (store.isConfigured(old)) store.save(new, store.get(old))
            store.delete(old)
        }
        refresh(context)
    }
    private fun refresh(context: Context) {
        val pending = goAsync()
        scope.launch {
            try { showSaved(context.applicationContext); ScreeningWidgetRefreshWorker.enqueue(context.applicationContext) }
            catch (error: Exception) { android.util.Log.w("ScreeningCard", "Could not queue refresh", error) }
            finally { pending.finish() }
        }
    }
    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val refreshMutex = Mutex()
        fun ids(context: Context): List<Int> = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, ScreeningWidgetProvider::class.java)).sorted()
        fun placedIds(context: Context): List<Int> = ids(context).filter { ScreeningWidgetStore(context).isConfigured(it) }

        fun showSaved(context: Context, status: String? = null) {
            val manager = AppWidgetManager.getInstance(context)
            val store = ScreeningWidgetStore(context)
            ids(context).forEach { id ->
                runCatching {
                    val entry = store.get(id)
                    manager.updateAppWidget(id, views(context, id, entry, manager.getAppWidgetOptions(id), status))
                    if (store.isConfigured(id)) recordPublication(context, id, entry)
                }
                    .onFailure { android.util.Log.w("ScreeningCard", "Could not publish cached widget $id", it) }
            }
        }

        suspend fun updateAll(context: Context, advanceId: Int? = null, expectedGeneration: Long? = null): Boolean =
            updateWithLoader(context, advanceId = advanceId, expectedGeneration = expectedGeneration) {
                val ranked = ScreeningService.ranked(context, cooldowns = false)
                // Optional network refresh has its own small budget; offline picks still render from Room.
                val kinds = ranked.mapNotNull { com.example.media.recommendation.ScreeningRecommendationEngine.kind(it.item) }.distinct()
                for (kind in kinds) withTimeoutOrNull(2_000) {
                    com.example.data.remote.trakt.TraktPlaybackRepository.refresh(context, kind)
                }
                ranked
            }

        internal suspend fun updateWithLoader(context: Context, timeoutMillis: Long = 8_000,
            advanceId: Int? = null, expectedGeneration: Long? = null,
            load: suspend () -> List<ScreeningRecommendation>): Boolean = refreshMutex.withLock {
            if (placedIds(context).isEmpty()) return@withLock true
            if (com.example.notes.StartupCrashGuard.shouldPause(context) || com.example.notes.NoteMigrationManager.shouldPauseBackground(context)) {
                showSaved(context, "Open Cobalt to finish note recovery"); return@withLock true
            }
            val ranked = try { withTimeoutOrNull(timeoutMillis) { load() } }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { null }
            if (ranked == null) { showSaved(context, "Saved pick · open Cobalt to refresh"); return@withLock false }
            val manager = AppWidgetManager.getInstance(context)
            val store = ScreeningWidgetStore(context)
            val session = com.example.data.remote.trakt.TraktAuthManager.getInstance(context).playbackSession.value
            val positions = session?.let { com.example.data.local.AppDatabase.getDatabase(context).traktPlaybackDao().positions(it) }.orEmpty()
            val day = ScreeningSchedule.day(System.currentTimeMillis())
            var successful = true
            placedIds(context).forEach { id ->
                val before = store.get(id)
                val advance = id == advanceId && (expectedGeneration == null || before.generation == expectedGeneration)
                val selected = ScreeningWidgetSelection.update(before, ranked, id, day, advance)
                val updated = ScreeningWidgetSelection.withPlayback(selected, ranked.firstOrNull { it.item.id == selected.pick?.itemId }?.item, session, positions)
                // Image failures never hold the widget in a loading state or discard its title.
                ScreeningArtworkCache.load(context, updated.pick?.artwork)
                if (id !in placedIds(context) || !store.compareAndSave(id, before, updated)) {
                    successful = false
                } else {
                    runCatching {
                        val entry = store.get(id)
                        manager.updateAppWidget(id, views(context, id, entry, manager.getAppWidgetOptions(id)))
                        recordPublication(context, id, entry)
                    }
                        .onFailure { successful = false; android.util.Log.w("ScreeningCard", "Widget $id refresh failed", it) }
                }
            }
            successful
        }

        private fun recordPublication(context: Context, id: Int, entry: ScreeningWidgetEntry) {
            val pick = entry.pick ?: return
            ScreeningStore(context).publish(ScreeningDecisionSnapshot(
                "widget:$id:${entry.day}:${entry.generation}", "Widget $id", entry.config.source.name,
                pick.key, pick.itemId, pick.title, System.currentTimeMillis(), pick.trace, reason = pick.reason))
        }

        fun views(context: Context, id: Int, entry: ScreeningWidgetEntry, options: Bundle = Bundle(), status: String? = null): RemoteViews {
            val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val hostW = options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 336).coerceAtLeast(100)
            val hostH = options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 264).coerceAtLeast(100)
            val density = context.resources.displayMetrics.density
            val pixelScale = minOf(density, 900f / maxOf(hostW, hostH))
            val width = (hostW * pixelScale).toInt().coerceAtLeast(1)
            val height = (hostH * pixelScale).toInt().coerceAtLeast(1)
            val root = RemoteViews(context.packageName, R.layout.screening_widget)
            root.setViewPadding(R.id.screening_root, 0, 0, 0, 0)
            val session = com.example.data.remote.trakt.TraktAuthManager.getInstance(context).playbackSession.value
            val visible = if (session != null && session == entry.pick?.playbackSession) entry
                else entry.copy(pick = entry.pick?.copy(playbackProgress = null, playbackSession = null))
            root.setImageViewBitmap(R.id.screening_image, ScreeningRenderer.render(context, visible, width, height,
                ScreeningArtworkCache.cached(context, entry.pick?.artwork), status = status, layoutWidth = hostW.toFloat()))
            root.setViewVisibility(R.id.screening_initial_message, View.GONE)
            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                data = Uri.parse("cobalt-screening://widget/details/$id/${entry.generation}")
                if (entry.pick != null) putExtra("OPEN_ITEM_ID", entry.pick.itemId) else putExtra("NAVIGATE_TO", "media_hub")
            }
            val pending = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            root.setOnClickPendingIntent(R.id.screening_body, pending)
            root.setContentDescription(R.id.screening_body, entry.pick?.let { "${it.title}. ${it.reason}. View details." } ?: "No available picks. Open Media in Cobalt.")
            root.setViewVisibility(R.id.screening_feedback, if (entry.pick == null) View.GONE else View.VISIBLE)
            listOf(ScreeningThumb.LIKED to R.id.screening_like, ScreeningThumb.DISLIKED to R.id.screening_dislike).forEach { (choice, viewId) ->
                val intent = Intent(context, ScreeningThumbReceiver::class.java).apply {
                    action = "com.example.SCREENING_THUMB"
                    data = Uri.parse("cobalt-screening://widget/thumb/$id/${entry.generation}/${choice.name}")
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id); putExtra("generation", entry.generation)
                    putExtra("itemId", entry.pick?.itemId); putExtra("choice", choice.name)
                }
                root.setOnClickPendingIntent(viewId, if (entry.pick != null)
                    PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) else null)
                root.setBoolean(viewId, "setEnabled", entry.pick != null)
                val label = if (choice == ScreeningThumb.LIKED) "Like" else "Not for me"
                root.setContentDescription(viewId, "$label ${entry.pick?.title.orEmpty()}" + if (entry.thumb == choice) ". Selected. Tap again to clear." else ".")
            }
            return root
        }

        fun requestPin(context: Context, entry: ScreeningWidgetEntry): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            if (Build.VERSION.SDK_INT < 26 || !manager.isRequestPinAppWidgetSupported) return false
            val token = ScreeningWidgetStore(context).stagePin(entry)
            val intent = Intent(context, ScreeningPinReceiver::class.java).setAction("com.example.SCREENING_PINNED")
                .setData(Uri.parse("cobalt-screening://widget/pin/$token")).putExtra("token", token)
            // The launcher adds its assigned widget ID to this explicit callback.
            val callback = PendingIntent.getBroadcast(context, token.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            val extras = Bundle().apply { putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, views(context, -1, entry)) }
            return manager.requestPinAppWidget(ComponentName(context, ScreeningWidgetProvider::class.java), extras, callback)
        }
    }
}

class ScreeningPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val token = intent.getStringExtra("token") ?: return
        if (AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider != ComponentName(context, ScreeningWidgetProvider::class.java)) return
        if (ScreeningWidgetStore(context).finishPin(token, id)) runCatching { ScreeningWidgetRefreshWorker.enqueue(context) }
    }
}

class ScreeningNextReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider != ComponentName(context, ScreeningWidgetProvider::class.java)) return
        val generation = intent.getLongExtra("generation", -1)
        if (generation < 0 || !ScreeningWidgetStore(context).isConfigured(id)) return
        val pending = goAsync()
        executor.execute {
            try { ScreeningWidgetRefreshWorker.enqueue(context, id, generation)?.result?.get(8, TimeUnit.SECONDS) }
            catch (error: Exception) { android.util.Log.w("ScreeningCard", "Could not queue next pick", error) }
            finally { pending.finish() }
        }
    }
    companion object { private val executor = Executors.newSingleThreadExecutor() }
}

class ScreeningThumbReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider != ComponentName(context, ScreeningWidgetProvider::class.java)) return
        val generation = intent.getLongExtra("generation", -1)
        val itemId = intent.getStringExtra("itemId") ?: return
        val choice = runCatching { ScreeningThumb.valueOf(intent.getStringExtra("choice").orEmpty()) }.getOrNull() ?: return
        val pending = goAsync()
        scope.launch {
            try {
                val item = com.example.data.local.AppDatabase.getDatabase(context).savedItemDao().getItemById(itemId)
                if (item != null && !item.isDeleted && !item.isArchived &&
                    ScreeningWidgetStore(context).toggleThumb(id, generation, itemId, choice)) ScreeningWidgetProvider.showSaved(context)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                android.util.Log.w("ScreeningCard", "Could not save widget preference")
            } finally { pending.finish() }
        }
    }
    companion object { private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO) }
}
