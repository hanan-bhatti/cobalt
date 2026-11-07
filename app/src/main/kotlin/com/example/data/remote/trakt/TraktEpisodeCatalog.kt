package com.example.data.remote.trakt

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.TraktEpisodeEntity
import com.example.data.model.SavedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object TraktEpisodeCatalog {
    internal fun episodeThumbnail(images: Map<String, List<String>>?): String? {
        val raw = images?.get("screenshot")?.firstOrNull() ?: return null
        val uri = android.net.Uri.parse(if (raw.startsWith("https://")) raw else "https://$raw")
        return uri.toString().takeIf { uri.scheme == "https" && uri.host?.endsWith(".trakt.tv") == true }
    }

    suspend fun load(context: Context, item: SavedItem, season: Int, refresh: Boolean = false,
        db: AppDatabase = AppDatabase.getDatabase(context)): String? = withContext(Dispatchers.IO) {
        val dao = db.traktEpisodeDao()
        val cached = dao.season(item.id, season)
        val id = requireNotNull(item.traktId).also { require(it > 0) }
        val auth = TraktAuthManager.getInstance(context)
        if (auth.hasValidToken()) try { auth.refreshAccessTokenIfNeeded() }
        catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (error: Exception) { if (cached.isEmpty()) throw error else return@withContext "Showing saved episodes. Refresh when connected." }
        if (refresh || cached.isEmpty()) {
            val episodes = TraktSyncEngine.getApiService().getShowSeasonEpisodes(id.toString(), season)
            db.withTransaction {
                if (db.savedItemDao().isItemDeleted(item.id) != false) return@withTransaction
                dao.deleteSeason(item.id, season)
                for (batch in episodes.chunked(100)) dao.put(batch.map { episode ->
                    TraktEpisodeEntity(item.id, season, episode.number, episode.title, episode.overview,
                        episode.first_aired, episode.runtime, episode.rating, episodeThumbnail(episode.images),
                        cached.firstOrNull { it.number == episode.number }?.playbackProgress)
                })
            }
        }
        if (!TraktAuthManager.getInstance(context).hasValidToken()) return@withContext null
        try {
            val positions = TraktPlaybackCache.positions(context, refresh)
                .filter { it.show?.ids?.trakt == id && it.episode?.season == season }.associateBy { it.episode!!.number }
            db.withTransaction {
                if (db.savedItemDao().isItemDeleted(item.id) != false) return@withTransaction
                dao.put(dao.season(item.id, season).map { row -> row.copy(playbackProgress = positions[row.number]?.progress) })
            }
            null
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { "Couldn’t refresh playback progress. Saved episode details are available." }
    }
}
