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
        val id = item.traktId?.takeIf { it > 0 }
        val tmdb = item.tmdbId?.takeIf { it > 0 }
        require(id != null || tmdb != null) { "This show has no catalogue identity" }
        val auth = TraktAuthManager.getInstance(context)
        if (auth.hasValidToken()) try { auth.refreshAccessTokenIfNeeded() }
        catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (error: Exception) { if (cached.isEmpty()) throw error else return@withContext "Showing saved episodes. Refresh when connected." }
        // Saved rows display immediately; each active season refreshes silently.
        run {
            val key = com.example.util.SecretPreferences(context.getSharedPreferences("cobalt_prefs", 0), "cobalt_prefs").get("tmdb_api_key").orEmpty()
            val traktRows = if (id != null) try {
                kotlinx.coroutines.withTimeoutOrNull(15_000) { TraktSyncEngine.getApiService().getShowSeasonEpisodes(id.toString(), season) }
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { null } else null
            val tmdbRows = if (tmdb != null && key.isNotBlank()) try {
                kotlinx.coroutines.withTimeoutOrNull(15_000) {
                    (com.example.media.MediaDetailHydrator.tmdbProvider?.invoke() ?: com.example.data.remote.MediaApiClient.tmdbApiService).getTvSeasonDetails(tmdb, season, key).episodes
                }
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { null } else null
            if (traktRows == null && tmdbRows == null) {
                if (cached.isEmpty()) throw java.io.IOException("Episode providers are unavailable")
                return@withContext "Showing saved episodes. Refresh when connected."
            }
            val episodes = (traktRows.orEmpty().map { it.number } + tmdbRows.orEmpty().map { it.episodeNumber }).distinct().sorted().map { number ->
                val t = traktRows?.firstOrNull { it.number == number }
                val m = tmdbRows?.firstOrNull { it.episodeNumber == number }
                TraktEpisodeEntity(item.id, season, number, m?.name ?: t?.title, m?.overview ?: t?.overview,
                    m?.airDate ?: t?.first_aired, m?.runtime ?: t?.runtime, m?.voteAverage ?: t?.rating,
                    m?.stillPath?.let { "https://image.tmdb.org/t/p/w300$it" } ?: episodeThumbnail(t?.images),
                    cached.firstOrNull { it.number == number }?.playbackProgress)
            }
            db.withTransaction {
                if (db.savedItemDao().isItemDeleted(item.id) != false) return@withTransaction
                dao.deleteSeason(item.id, season)
                for (batch in episodes.chunked(100)) dao.put(batch)
            }
        }
        if (id == null || !TraktAuthManager.getInstance(context).hasValidToken()) return@withContext null
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
