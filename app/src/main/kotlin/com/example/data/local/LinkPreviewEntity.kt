package com.example.data.local

import androidx.room.*

/** Durable fetch-job state only. The sole description remains saved_items.linkDescription. */
@Entity(tableName = "link_preview_queue")
data class LinkPreviewEntity(
    @PrimaryKey val itemId: String,
    val url: String,
    val replaceFallback: Boolean = false,
    val pending: Boolean = true,
    val attempts: Int = 0,
    val generation: String = java.util.UUID.randomUUID().toString()
)

@Dao
interface LinkPreviewDao {
    @Query("SELECT * FROM link_preview_queue WHERE itemId = :id")
    suspend fun get(id: String): LinkPreviewEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(value: LinkPreviewEntity)
    @Query("SELECT p.* FROM link_preview_queue p INNER JOIN saved_items s ON s.id = p.itemId WHERE p.pending = 1 AND s.isDeleted = 0 AND s.content = p.url AND upper(s.type) = 'LINK' ORDER BY p.attempts LIMIT 1")
    suspend fun next(): LinkPreviewEntity?
}
