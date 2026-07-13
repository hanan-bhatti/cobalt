package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedItemDao {
    @Query("SELECT * FROM saved_items ORDER BY orderIndex DESC, timestamp DESC")
    fun getAllItemsFlow(): Flow<List<SavedItemEntity>>

    @Query("SELECT * FROM saved_items WHERE id = :id")
    suspend fun getItemById(id: String): SavedItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: SavedItemEntity)

    @Delete
    suspend fun deleteItem(item: SavedItemEntity)

    @Query("SELECT * FROM saved_items ORDER BY orderIndex DESC, timestamp DESC")
    suspend fun getAllItems(): List<SavedItemEntity>
}
