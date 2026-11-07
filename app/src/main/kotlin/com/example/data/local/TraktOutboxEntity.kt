package com.example.data.local

import androidx.room.*

/** Small durable user-intent records. Account/session binding prevents cross-account uploads. */
@Entity(tableName = "trakt_outbox")
data class TraktOutboxEntity(
    @PrimaryKey val key: String,
    val session: String,
    val itemId: String,
    val action: String,
    val payload: String,
    val generation: String = java.util.UUID.randomUUID().toString(),
    val created: Long = System.currentTimeMillis()
)

@Dao
interface TraktOutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: TraktOutboxEntity)
    @Query("SELECT * FROM trakt_outbox WHERE session = :session AND created <= :now AND ((action LIKE 'rating:%' AND :ratings) OR (action LIKE 'watchlist:%' AND :watchlist) OR (action LIKE 'history:%' AND ((:movies AND payload LIKE '%\"movies\":%') OR (:shows AND payload LIKE '%\"shows\":%')))) ORDER BY created, `key` LIMIT :limit")
    suspend fun pending(session: String, limit: Int = 100, now: Long = System.currentTimeMillis(), ratings: Boolean = true, watchlist: Boolean = true, movies: Boolean = true, shows: Boolean = true): List<TraktOutboxEntity>
    @Query("DELETE FROM trakt_outbox WHERE `key` = :key AND generation = :generation")
    suspend fun acknowledge(key: String, generation: String)
    @Query("SELECT action FROM trakt_outbox WHERE session = :session AND itemId = :id")
    suspend fun pendingActions(session: String, id: String): List<String>
    @Query("SELECT MIN(created) FROM trakt_outbox WHERE session = :session AND created > :now")
    suspend fun nextRetryAt(session: String, now: Long): Long?
    @Query("SELECT EXISTS(SELECT 1 FROM trakt_outbox WHERE session = :session AND itemId = :itemId)")
    suspend fun hasPending(session: String, itemId: String): Boolean
    @Query("UPDATE trakt_outbox SET created = :retryAt WHERE `key` = :key AND generation = :generation")
    suspend fun defer(key: String, generation: String, retryAt: Long)
    @Query("SELECT count(*) FROM trakt_outbox WHERE session = :session")
    fun observePendingCount(session: String): kotlinx.coroutines.flow.Flow<Int>
    @Query("DELETE FROM trakt_outbox WHERE itemId = :id")
    suspend fun deleteItem(id: String)
    @Query("DELETE FROM trakt_outbox WHERE session = :session")
    suspend fun clearSession(session: String)
}
