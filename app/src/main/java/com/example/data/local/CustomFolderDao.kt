package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomFolderDao {
    @Query("SELECT * FROM custom_folders ORDER BY isPinned DESC, name ASC")
    fun getAllFoldersFlow(): Flow<List<CustomFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: CustomFolderEntity)

    @Delete
    suspend fun deleteFolder(folder: CustomFolderEntity)

    @Query("SELECT * FROM custom_folders ORDER BY isPinned DESC, name ASC")
    suspend fun getAllFolders(): List<CustomFolderEntity>
}
