package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Playback is account data, separate from a saved title's watch status and history. */
@Entity(tableName = "trakt_playback_cache", primaryKeys = ["session", "key"])
data class TraktPlaybackEntity(
    val session: String,
    val key: String,
    val kind: String,
    val traktId: Long?,
    val tmdbId: Int?,
    val imdbId: String?,
    val season: Int?,
    val episode: Int?,
    val progress: Double,
    val pausedAt: String?
)

@Dao
interface TraktPlaybackDao {
    @Query("SELECT * FROM trakt_playback_cache WHERE session = :session ORDER BY pausedAt DESC")
    fun observe(session: String): Flow<List<TraktPlaybackEntity>>
    @Query("SELECT * FROM trakt_playback_cache WHERE session = :session ORDER BY pausedAt DESC")
    suspend fun positions(session: String): List<TraktPlaybackEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(positions: List<TraktPlaybackEntity>)
    @Query("DELETE FROM trakt_playback_cache WHERE session = :session AND kind = :kind")
    suspend fun clearKind(session: String, kind: String)
    @Query("DELETE FROM trakt_playback_cache WHERE session != :session")
    suspend fun clearOtherSessions(session: String)
    @Query("DELETE FROM trakt_playback_cache")
    suspend fun clear()
}
