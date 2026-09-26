package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.FileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    @Query("SELECT * FROM cloud_files WHERE userId = :userId ORDER BY uploadDate DESC")
    fun getFilesForUser(userId: Long): Flow<List<FileEntity>>

    @Query("SELECT * FROM cloud_files WHERE userId = :userId AND folderId = :folderId ORDER BY uploadDate DESC")
    fun getFilesByFolder(userId: Long, folderId: String): Flow<List<FileEntity>>

    @Query("SELECT * FROM cloud_files WHERE messageId = :messageId LIMIT 1")
    suspend fun getFileByMessageId(messageId: Long): FileEntity?

    @Query("SELECT * FROM cloud_files WHERE telegramFileId = :telegramFileId LIMIT 1")
    suspend fun getFileByTelegramFileId(telegramFileId: Int): FileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<FileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: FileEntity)

    @Query("DELETE FROM cloud_files WHERE messageId = :messageId")
    suspend fun deleteFileByMessageId(messageId: Long)

    @Query("DELETE FROM cloud_files WHERE userId = :userId")
    suspend fun deleteFilesForUser(userId: Long)

    @Query("UPDATE cloud_files SET folderId = :folderId WHERE messageId = :messageId")
    suspend fun updateFileFolder(messageId: Long, folderId: String)

    @Query("UPDATE cloud_files SET localPath = :localPath, isDownloaded = :isDownloaded WHERE messageId = :messageId")
    suspend fun updateFileLocalPath(messageId: Long, localPath: String?, isDownloaded: Boolean)

    @Query("UPDATE cloud_files SET fileName = :newName WHERE messageId = :messageId")
    suspend fun updateFileName(messageId: Long, newName: String)

    @Query("SELECT COUNT(*) FROM cloud_files WHERE userId = :userId AND folderId = :folderId")
    suspend fun countFilesForFolder(userId: Long, folderId: String): Int
}
