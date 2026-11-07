package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Each episode is a separate small row; full season overviews never enter the note/list row. */
@Entity(tableName = "trakt_episode_cache", primaryKeys = ["itemId", "season", "number"])
data class TraktEpisodeEntity(val itemId: String, val season: Int, val number: Int,
    val title: String?, val overview: String?, val airDate: String?, val runtime: Int?, val rating: Double?, val thumbnailUrl: String? = null, val playbackProgress: Double? = null)

@Dao
interface TraktEpisodeDao {
    @Query("SELECT * FROM trakt_episode_cache WHERE itemId = :id AND season = :season ORDER BY number")
    fun observeSeason(id: String, season: Int): Flow<List<TraktEpisodeEntity>>
    @Query("SELECT * FROM trakt_episode_cache WHERE itemId = :id AND season = :season ORDER BY number")
    suspend fun season(id: String, season: Int): List<TraktEpisodeEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(episodes: List<TraktEpisodeEntity>)
    @Query("DELETE FROM trakt_episode_cache WHERE itemId = :id AND season = :season")
    suspend fun deleteSeason(id: String, season: Int)
    @Query("DELETE FROM trakt_episode_cache WHERE itemId = :id")
    suspend fun deleteItem(id: String)
}
