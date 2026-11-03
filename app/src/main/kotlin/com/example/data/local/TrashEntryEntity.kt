package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Kept until physical deletion finishes, including after a crash during purge. */
@Entity(tableName = "trash_entries", indices = [Index("expiresAt")])
data class TrashEntryEntity(
    @PrimaryKey val itemId: String,
    val deletedAt: Long,
    val expiresAt: Long,
    val undoOnly: Boolean,
    val purgeStarted: Boolean = false
)

data class TrashItem(
    val id: String, val title: String, val type: String, val preview: String,
    val byteLength: Long, val deletedAt: Long, val expiresAt: Long,
    val undoOnly: Boolean, val purgeStarted: Boolean
)

@Dao
interface TrashDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TrashEntryEntity)
    @Query("SELECT * FROM trash_entries WHERE itemId = :id")
    suspend fun entry(id: String): TrashEntryEntity?
    @Query("SELECT * FROM trash_entries WHERE expiresAt <= :now OR purgeStarted = 1 ORDER BY expiresAt LIMIT 64")
    suspend fun due(now: Long): List<TrashEntryEntity>
    @Query("SELECT itemId FROM trash_entries")
    suspend fun ids(): List<String>
    @Query("SELECT MIN(expiresAt) FROM trash_entries")
    suspend fun nextExpiry(): Long?
    @Query("UPDATE trash_entries SET purgeStarted = 1 WHERE itemId = :id")
    suspend fun beginPurge(id: String)
    @Query("DELETE FROM trash_entries WHERE itemId = :id")
    suspend fun remove(id: String)
    @Query("SELECT s.id, substr(s.title, 1, 160) AS title, s.type, CASE WHEN s.bodyLocation = 'FILE' THEN s.preview ELSE substr(s.content, 1, 200) END AS preview, CASE WHEN s.bodyLocation = 'FILE' THEN s.byteLength ELSE s.sizeBytes END AS byteLength, t.deletedAt, t.expiresAt, t.undoOnly, t.purgeStarted FROM trash_entries t JOIN saved_items s ON s.id = t.itemId ORDER BY t.deletedAt DESC")
    fun observe(): Flow<List<TrashItem>>
}
