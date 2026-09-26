package com.example.data.telegram

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.dao.FileDao
import com.example.data.local.dao.FolderDao
import com.example.data.local.entity.FileEntity
import com.example.data.local.entity.FolderEntity
import com.example.data.model.CloudFile
import com.example.data.model.CloudFolder
import com.example.data.model.TelegramAuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class TelegramRepository(
    private val context: Context,
    private val clientManager: TelegramClientManager,
    private val fileDao: FileDao,
    private val folderDao: FolderDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val authState: StateFlow<TelegramAuthState> = clientManager.authState

    // In-memory active file transfers for real-time UI progress (messageId -> CloudFile with progress)
    private val activeTransfers = MutableStateFlow<Map<Long, CloudFile>>(emptyMap())

    private var currentUserId: Long = 0L

    init {
        scope.launch {
            authState.collect { state ->
                if (state is TelegramAuthState.Authenticated) {
                    currentUserId = state.user.id
                    // Ensure Default Folders exist
                    ensureDefaultFolders(state.user.id)
                    // Auto-sync files from Saved Messages
                    syncFiles()
                } else if (state is TelegramAuthState.WaitingPhoneNumber) {
                    currentUserId = 0L
                    activeTransfers.value = emptyMap()
                }
            }
        }
    }

    private suspend fun ensureDefaultFolders(userId: Long) {
        val existing = folderDao.getFoldersForUser(userId).firstOrNull() ?: emptyList()
        if (existing.isEmpty()) {
            folderDao.insertFolder(FolderEntity(id = "root", userId = userId, name = "All Files"))
            folderDao.insertFolder(FolderEntity(id = "docs", userId = userId, name = "Documents"))
            folderDao.insertFolder(FolderEntity(id = "media", userId = userId, name = "Media"))
            folderDao.insertFolder(FolderEntity(id = "archives", userId = userId, name = "Archives"))
        }
    }

    fun getFilesFlow(): Flow<List<CloudFile>> {
        return combine(
            fileDao.getFilesForUser(currentUserId),
            activeTransfers
        ) { dbFiles, transfers ->
            val mapped = dbFiles.map { it.toCloudFile() }.toMutableList()
            // Merge transfers with DB files
            transfers.values.forEach { transfer ->
                val index = mapped.indexOfFirst { it.messageId == transfer.messageId }
                if (index >= 0) {
                    mapped[index] = transfer
                } else {
                    mapped.add(0, transfer)
                }
            }
            mapped
        }
    }

    fun getFoldersFlow(): Flow<List<CloudFolder>> {
        return combine(
            folderDao.getFoldersForUser(currentUserId),
            fileDao.getFilesForUser(currentUserId)
        ) { folders, files ->
            folders.map { folder ->
                val count = files.count { it.folderId == folder.id }
                folder.toCloudFolder(count)
            }
        }
    }

    suspend fun sendPhoneNumber(phone: String): Result<Unit> {
        return clientManager.sendPhoneNumber(phone)
    }

    suspend fun sendCode(code: String): Result<Unit> {
        return clientManager.sendCode(code)
    }

    suspend fun sendPassword(password: String): Result<Unit> {
        return clientManager.sendPassword(password)
    }

    suspend fun logout(): Result<Unit> {
        if (currentUserId != 0L) {
            fileDao.deleteFilesForUser(currentUserId)
            folderDao.deleteFoldersForUser(currentUserId)
        }
        return clientManager.logout()
    }

    suspend fun syncFiles(): Result<Int> = withContext(Dispatchers.IO) {
        val result = clientManager.fetchKawachFilesFromSavedMessages()
        if (result.isSuccess) {
            val files = result.getOrThrow()
            if (currentUserId != 0L) {
                val entities = files.map { FileEntity.fromCloudFile(it, currentUserId) }
                fileDao.insertFiles(entities)
            }
            Result.success(files.size)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Failed to sync files"))
        }
    }

    suspend fun uploadFromUri(
        uri: Uri,
        folderId: String = "root",
        onProgress: (Float) -> Unit = {}
    ): Result<CloudFile> = withContext(Dispatchers.IO) {
        try {
            val (fileName, fileSize, mimeType) = getFileInfo(uri)
            val cacheFile = copyUriToCache(uri, fileName)

            val tempMessageId = System.currentTimeMillis()
            val tempCloudFile = CloudFile(
                messageId = tempMessageId,
                telegramFileId = 0,
                name = fileName,
                size = fileSize,
                mimeType = mimeType,
                uploadDate = System.currentTimeMillis() / 1000L,
                folderId = folderId,
                localPath = cacheFile.absolutePath,
                isUploading = true,
                uploadProgress = 0.05f,
                isDownloaded = true
            )

            // Emit to active transfers for instant UI feedback
            activeTransfers.value = activeTransfers.value + (tempMessageId to tempCloudFile)

            val uploadResult = clientManager.uploadFile(
                file = cacheFile,
                fileName = fileName,
                mimeType = mimeType,
                folderId = folderId,
                onProgress = { progress ->
                    onProgress(progress)
                    val updated = tempCloudFile.copy(
                        isUploading = progress < 1f,
                        uploadProgress = progress
                    )
                    activeTransfers.value = activeTransfers.value + (tempMessageId to updated)
                }
            )

            if (uploadResult.isSuccess) {
                val realCloudFile = uploadResult.getOrThrow()
                activeTransfers.value = activeTransfers.value - tempMessageId

                if (currentUserId != 0L) {
                    fileDao.insertFile(FileEntity.fromCloudFile(realCloudFile, currentUserId))
                }
                Result.success(realCloudFile)
            } else {
                activeTransfers.value = activeTransfers.value - tempMessageId
                Result.failure(uploadResult.exceptionOrNull() ?: Exception("Upload failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFile(
        file: CloudFile,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val downloadsDir = File(context.getExternalFilesDir(null), "KawachDownloads")
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val targetFile = File(downloadsDir, file.name)
        if (targetFile.exists() && targetFile.length() == file.size && file.size > 0) {
            fileDao.updateFileLocalPath(file.messageId, targetFile.absolutePath, true)
            return@withContext Result.success(targetFile)
        }

        // Mark downloading in active transfers
        val downloadingFile = file.copy(isDownloading = true, downloadProgress = 0.05f)
        activeTransfers.value = activeTransfers.value + (file.messageId to downloadingFile)

        val result = clientManager.downloadFile(
            telegramFileId = file.telegramFileId,
            targetFile = targetFile,
            onProgress = { progress ->
                onProgress(progress)
                val updated = downloadingFile.copy(
                    isDownloading = progress < 1f,
                    downloadProgress = progress
                )
                activeTransfers.value = activeTransfers.value + (file.messageId to updated)
            }
        )

        activeTransfers.value = activeTransfers.value - file.messageId

        if (result.isSuccess) {
            fileDao.updateFileLocalPath(file.messageId, targetFile.absolutePath, true)
            Result.success(targetFile)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Download failed"))
        }
    }

    suspend fun deleteFile(file: CloudFile): Result<Unit> = withContext(Dispatchers.IO) {
        // Real deletion from Telegram Saved Messages
        val result = clientManager.deleteFile(file.messageId)
        if (result.isSuccess) {
            fileDao.deleteFileByMessageId(file.messageId)
            // Delete local cached copy if present
            if (!file.localPath.isNullOrBlank()) {
                val f = File(file.localPath)
                if (f.exists()) f.delete()
            }
            Result.success(Unit)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Failed to delete file from Telegram"))
        }
    }

    suspend fun createFolder(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (currentUserId == 0L) return@withContext Result.failure(IllegalStateException("User not authenticated"))
        val id = UUID.randomUUID().toString().take(8)
        folderDao.insertFolder(FolderEntity(id = id, userId = currentUserId, name = name.trim()))
        Result.success(Unit)
    }

    suspend fun renameFolder(id: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (currentUserId == 0L) return@withContext Result.failure(IllegalStateException("User not authenticated"))
        folderDao.updateFolderName(id, currentUserId, newName.trim())
        Result.success(Unit)
    }

    suspend fun deleteFolder(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (currentUserId == 0L) return@withContext Result.failure(IllegalStateException("User not authenticated"))
        folderDao.deleteFolder(id, currentUserId)
        Result.success(Unit)
    }

    suspend fun moveFile(messageId: Long, folderId: String): Result<Unit> = withContext(Dispatchers.IO) {
        fileDao.updateFileFolder(messageId, folderId)
        Result.success(Unit)
    }

    suspend fun renameFile(messageId: Long, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        fileDao.updateFileName(messageId, newName)
        Result.success(Unit)
    }

    private fun getFileInfo(uri: Uri): Triple<String, Long, String> {
        var name = "file_${System.currentTimeMillis()}"
        var size = 0L
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) {
                    name = cursor.getString(nameIdx) ?: name
                }
                if (sizeIdx >= 0) {
                    size = cursor.getLong(sizeIdx)
                }
            }
        }
        return Triple(name, size, mime)
    }

    private fun copyUriToCache(uri: Uri, fileName: String): File {
        val cacheDir = File(context.cacheDir, "uploads")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val dest = File(cacheDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
        return dest
    }
}
