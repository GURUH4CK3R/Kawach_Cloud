package com.example.data.telegram

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.data.local.PreferenceManager
import com.example.data.local.dao.FileDao
import com.example.data.local.dao.FolderDao
import com.example.data.local.entity.FileEntity
import com.example.data.local.entity.FolderEntity
import com.example.data.model.CloudFile
import com.example.data.model.CloudFolder
import com.example.data.model.TelegramAuthState
import com.example.util.DownloadStorageManager
import com.example.util.SavedDownloadResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class TelegramRepository(
    private val context: Context,
    private val clientManager: TelegramClientManager,
    private val fileDao: FileDao,
    private val folderDao: FolderDao,
    private val preferenceManager: PreferenceManager = PreferenceManager(context)
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val authState: StateFlow<TelegramAuthState> = clientManager.authState

    // In-memory active file transfers for real-time UI progress (messageId -> CloudFile with progress)
    private val activeTransfers = MutableStateFlow<Map<Long, CloudFile>>(emptyMap())

    private val initialUserId = preferenceManager.getSyncActiveUserId()
    private val _activeUserId = MutableStateFlow<Long>(initialUserId)
    private var currentUserId: Long = initialUserId

    init {
        if (initialUserId != 0L) {
            scope.launch {
                ensureDefaultFolders(initialUserId)
            }
        }

        scope.launch {
            authState.collect { state ->
                if (state is TelegramAuthState.Authenticated) {
                    val uid = state.user.id
                    currentUserId = uid
                    _activeUserId.value = uid
                    // Ensure Default Folders exist
                    ensureDefaultFolders(uid)
                    // Auto-sync files from Saved Messages in background
                    syncFiles()
                } else if (state is TelegramAuthState.WaitingPhoneNumber) {
                    currentUserId = 0L
                    _activeUserId.value = 0L
                    activeTransfers.value = emptyMap()
                }
            }
        }

        clientManager.onDeleteMessagesListener = { deletedMessageIds ->
            val uid = currentUserId
            if (uid != 0L) {
                scope.launch(Dispatchers.IO) {
                    for (msgId in deletedMessageIds) {
                        fileDao.deleteFileByMessageId(msgId, uid)
                    }
                }
            }
        }

        clientManager.onThumbnailDownloadedListener = { fileId, path ->
            val uid = currentUserId
            if (uid != 0L) {
                scope.launch(Dispatchers.IO) {
                    fileDao.updateThumbnailForTelegramFileId(fileId, uid, path)
                }
            }
        }
    }

    private suspend fun ensureDefaultFolders(userId: Long) {
        val existing = folderDao.getFoldersForUser(userId).firstOrNull() ?: emptyList()
        val defaultList = listOf(
            FolderEntity(id = "root", userId = userId, name = "All Files"),
            FolderEntity(id = "saved_messages", userId = userId, name = "Saved Messages"),
            FolderEntity(id = "docs", userId = userId, name = "Documents"),
            FolderEntity(id = "media", userId = userId, name = "Media"),
            FolderEntity(id = "archives", userId = userId, name = "Archives")
        )
        val existingIds = existing.map { it.id }.toSet()
        val toInsert = defaultList.filter { it.id !in existingIds }
        if (toInsert.isNotEmpty()) {
            folderDao.insertFolders(toInsert)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getFilesFlow(): Flow<List<CloudFile>> {
        return _activeUserId.flatMapLatest { userId ->
            if (userId == 0L) {
                flowOf(emptyList())
            } else {
                combine(
                    fileDao.getFilesForUser(userId),
                    activeTransfers
                ) { dbFiles, transfers ->
                    val mapped = dbFiles.map { it.toCloudFile() }.toMutableList()
                    // Merge active transfers with DB files
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
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getFoldersFlow(): Flow<List<CloudFolder>> {
        return _activeUserId.flatMapLatest { userId ->
            if (userId == 0L) {
                flowOf(emptyList())
            } else {
                combine(
                    folderDao.getFoldersForUser(userId),
                    fileDao.getFilesForUser(userId)
                ) { folders, files ->
                    folders.map { folder ->
                        val count = if (folder.id == "root") {
                            files.size
                        } else {
                            files.count { it.folderId == folder.id }
                        }
                        folder.toCloudFolder(count)
                    }
                }
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
        _activeUserId.value = 0L
        currentUserId = 0L
        activeTransfers.value = emptyMap()
        try {
            File(context.cacheDir, "previews").deleteRecursively()
        } catch (_: Exception) {}
        return clientManager.logout()
    }

    suspend fun syncFiles(): Result<Int> = withContext(Dispatchers.IO) {
        val uid = currentUserId
        if (uid == 0L) return@withContext Result.failure(IllegalStateException("User not authenticated"))

        val result = clientManager.syncSavedMessages { batchFiles, batchFolders ->
            if (batchFolders.isNotEmpty()) {
                val folderEntities = batchFolders.map { (id, name) ->
                    FolderEntity(id = id, userId = uid, name = name)
                }
                folderDao.insertFolders(folderEntities)
            }
            if (batchFiles.isNotEmpty()) {
                val fileEntities = batchFiles.map { cloudFile ->
                    val existing = fileDao.getFileByMessageId(cloudFile.messageId)
                    val mergedLocalPath = if (!cloudFile.localPath.isNullOrBlank() && File(cloudFile.localPath).exists()) {
                        cloudFile.localPath
                    } else if (existing?.isDownloaded == true && !existing.localPath.isNullOrBlank() && File(existing.localPath).exists()) {
                        existing.localPath
                    } else {
                        cloudFile.localPath
                    }
                    val isDownloaded = (!mergedLocalPath.isNullOrBlank() && File(mergedLocalPath).exists()) || cloudFile.isDownloaded
                    val thumbPath = if (!cloudFile.thumbnailPath.isNullOrBlank()) cloudFile.thumbnailPath else existing?.thumbnailPath
                    FileEntity.fromCloudFile(cloudFile, uid).copy(
                        localPath = mergedLocalPath,
                        isDownloaded = isDownloaded,
                        thumbnailPath = thumbPath
                    )
                }
                fileDao.insertFiles(fileEntities)
            }
        }

        result
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
    ): Result<SavedDownloadResult> = withContext(Dispatchers.IO) {
        // If file is already locally available and verified in public Downloads, return directly
        if (!file.localPath.isNullOrBlank()) {
            val local = file.localPath
            if (local.startsWith("content://")) {
                val uri = Uri.parse(local)
                try {
                    val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                    if (descriptor != null && descriptor.statSize > 0) {
                        descriptor.close()
                        return@withContext Result.success(
                            SavedDownloadResult(
                                uri = uri,
                                finalFileName = file.name,
                                mimeType = file.mimeType,
                                size = file.size,
                                localPath = local
                            )
                        )
                    }
                } catch (_: Exception) {}
            } else if (local.contains("/Download/")) {
                val f = File(local)
                if (f.exists() && f.length() > 0) {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                    return@withContext Result.success(
                        SavedDownloadResult(
                            uri = uri,
                            finalFileName = file.name,
                            mimeType = file.mimeType,
                            size = f.length(),
                            localPath = f.absolutePath
                        )
                    )
                }
            }
        }

        // Mark downloading in active transfers
        val downloadingFile = file.copy(isDownloading = true, downloadProgress = 0.05f)
        activeTransfers.value = activeTransfers.value + (file.messageId to downloadingFile)

        // 1. Download completed source file from TDLib using message resolution
        val result = clientManager.downloadMessageFile(
            messageId = file.messageId,
            fallbackFileId = file.telegramFileId,
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

        if (result.isFailure) {
            return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Download from Telegram failed"))
        }

        val downloadedSource = result.getOrThrow()

        // 2. Verify source file exists and is not empty
        if (!downloadedSource.exists() || downloadedSource.length() <= 0) {
            return@withContext Result.failure(Exception("Telegram download source file is empty or missing"))
        }

        // 3. Save into Android's public Downloads directory safely
        try {
            val saved = DownloadStorageManager.saveToPublicDownloads(
                context = context,
                sourceFile = downloadedSource,
                desiredFileName = file.name,
                mimeType = file.mimeType
            )

            // 4. Update Room database with final local path / URI and mark downloaded
            val pathToStore = saved.localPath ?: saved.uri.toString()
            fileDao.updateFileLocalPath(file.messageId, pathToStore, true)

            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOrFetchPreviewFile(
        file: CloudFile,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        // 1. Check if already locally available via file.localPath
        if (!file.localPath.isNullOrBlank()) {
            val local = file.localPath
            if (!local.startsWith("content://")) {
                val f = File(local)
                if (f.exists() && f.length() > 0) {
                    onProgress(1f)
                    return@withContext Result.success(f)
                }
            } else {
                try {
                    val uri = Uri.parse(local)
                    val previewCacheDir = File(context.cacheDir, "previews").apply { mkdirs() }
                    val cachedPreview = File(previewCacheDir, "${file.messageId}_${file.name}")
                    if (cachedPreview.exists() && cachedPreview.length() > 0) {
                        onProgress(1f)
                        return@withContext Result.success(cachedPreview)
                    }
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        cachedPreview.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (cachedPreview.exists() && cachedPreview.length() > 0) {
                        onProgress(1f)
                        return@withContext Result.success(cachedPreview)
                    }
                } catch (_: Exception) {}
            }
        }

        // 2. Check preview cache dir
        val previewCacheDir = File(context.cacheDir, "previews").apply { mkdirs() }
        val cachedPreview = File(previewCacheDir, "${file.messageId}_${file.name}")
        if (cachedPreview.exists() && cachedPreview.length() > 0) {
            onProgress(1f)
            return@withContext Result.success(cachedPreview)
        }

        // 3. Download from TDLib
        val result = clientManager.downloadMessageFile(
            messageId = file.messageId,
            fallbackFileId = file.telegramFileId,
            onProgress = onProgress
        )

        if (result.isFailure) {
            return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Failed to load file from Telegram"))
        }

        val downloadedSource = result.getOrThrow()
        if (!downloadedSource.exists() || downloadedSource.length() <= 0) {
            return@withContext Result.failure(Exception("Downloaded file is empty or missing"))
        }

        // Copy or return the downloaded source file
        try {
            downloadedSource.copyTo(cachedPreview, overwrite = true)
            Result.success(cachedPreview)
        } catch (_: Exception) {
            Result.success(downloadedSource)
        }
    }

    suspend fun deleteFile(file: CloudFile): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = currentUserId
        // Real deletion from Telegram Saved Messages
        val result = clientManager.deleteFile(file.messageId)
        if (result.isSuccess) {
            if (uid != 0L) {
                fileDao.deleteFileByMessageId(file.messageId, uid)
            } else {
                fileDao.deleteFileByMessageId(file.messageId)
            }
            activeTransfers.value = activeTransfers.value - file.messageId
            // Delete local cached copy if present
            if (!file.localPath.isNullOrBlank() && !file.localPath.startsWith("content://")) {
                val f = File(file.localPath)
                if (f.exists()) f.delete()
            }
            if (!file.thumbnailPath.isNullOrBlank()) {
                val tf = File(file.thumbnailPath)
                if (tf.exists()) tf.delete()
            }
            val previewCacheFile = File(File(context.cacheDir, "previews"), "${file.messageId}_${file.name}")
            if (previewCacheFile.exists()) previewCacheFile.delete()
            Result.success(Unit)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Failed to delete file from Telegram"))
        }
    }

    suspend fun createFolder(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = currentUserId
        if (uid == 0L) return@withContext Result.failure(IllegalStateException("User not authenticated"))
        val id = UUID.randomUUID().toString().take(8)
        val cleanName = name.trim()
        folderDao.insertFolder(FolderEntity(id = id, userId = uid, name = cleanName))
        // Persist to Telegram Saved Messages metadata so it survives re-login on any device
        clientManager.saveFolderMetadataToTelegram(id, cleanName)
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

    fun getFileInfo(uri: Uri): Triple<String, Long, String> {
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

    fun updateApiCredentials(apiId: String, apiHash: String) {
        clientManager.updateApiCredentials(apiId, apiHash)
    }

    fun resetAuthState() {
        clientManager.resetToPhoneInput()
    }
}
