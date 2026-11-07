package com.example.data.local

import androidx.room.*
import com.example.data.model.SavedItem

/** Provider IDs only: prevent background imports from recreating locally deleted media. */
@Entity(tableName = "media_tombstones", indices = [Index(value = ["groupId"])])
data class MediaTombstoneEntity(@PrimaryKey val key: String, val groupId: String, val kind: String,
    val provider: String, val providerId: String, val deletedAt: Long) {
    companion object {
        fun from(item: SavedItem, now: Long): List<MediaTombstoneEntity> {
            if (item.mediaType !in listOf("movie", "tv")) return emptyList()
            return listOfNotNull(item.traktId?.takeIf { it > 0 }?.let { "trakt" to it.toString() },
                item.tmdbId?.takeIf { it > 0 }?.let { "tmdb" to it.toString() },
                item.imdbId?.takeIf { it.isNotBlank() }?.let { "imdb" to it }).map { (provider, id) ->
                MediaTombstoneEntity("${item.mediaType}:$provider:$id",item.id,item.mediaType!!,provider,id,now)
            }
        }
    }
}

@Dao
interface MediaTombstoneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(rows: List<MediaTombstoneEntity>)
    @Query("SELECT EXISTS(SELECT 1 FROM media_tombstones WHERE `key` IN (:kind || ':trakt:' || :trakt, :kind || ':tmdb:' || :tmdb, :kind || ':imdb:' || :imdb))")
    suspend fun blocked(kind: String, trakt: String, tmdb: String, imdb: String): Boolean
    @Query("DELETE FROM media_tombstones WHERE groupId IN (SELECT groupId FROM media_tombstones WHERE `key` IN (:kind || ':trakt:' || :trakt, :kind || ':tmdb:' || :tmdb, :kind || ':imdb:' || :imdb))")
    suspend fun clearMatching(kind: String, trakt: String, tmdb: String, imdb: String)
    @Query("SELECT COUNT(DISTINCT groupId) FROM media_tombstones") fun observeCount(): kotlinx.coroutines.flow.Flow<Int>
    @Query("DELETE FROM media_tombstones") suspend fun clearAll()
}
