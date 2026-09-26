package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM cloud_folders WHERE userId = :userId ORDER BY createdAt ASC")
    fun getFoldersForUser(userId: Long): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)

    @Query("DELETE FROM cloud_folders WHERE id = :folderId AND userId = :userId")
    suspend fun deleteFolder(folderId: String, userId: Long)

    @Query("UPDATE cloud_folders SET name = :newName WHERE id = :folderId AND userId = :userId")
    suspend fun updateFolderName(folderId: String, userId: Long, newName: String)

    @Query("DELETE FROM cloud_folders WHERE userId = :userId")
    suspend fun deleteFoldersForUser(userId: Long)
}
