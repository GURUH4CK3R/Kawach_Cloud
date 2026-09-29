package com.kawach.cloud.ui.viewmodel

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kawach.cloud.KawachApplication
import com.kawach.cloud.data.local.PreferenceManager
import com.kawach.cloud.data.model.CloudFile
import com.kawach.cloud.data.model.CloudFolder
import com.kawach.cloud.data.model.CountryCode
import com.kawach.cloud.data.model.CountryRepository
import com.kawach.cloud.data.model.FileCategory
import com.kawach.cloud.data.model.TelegramAuthState
import com.kawach.cloud.data.telegram.TelegramRepository
import com.kawach.cloud.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class SortOption(val label: String) {
    NEWEST("Date (Newest)"),
    OLDEST("Date (Oldest)"),
    NAME_AZ("Name (A to Z)"),
    NAME_ZA("Name (Z to A)"),
    SIZE_LARGEST("Size (Largest)"),
    SIZE_SMALLEST("Size (Smallest)")
}

enum class UploadItemStatus {
    QUEUED,
    UPLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class UploadQueueItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
    val status: UploadItemStatus = UploadItemStatus.QUEUED,
    val progress: Float = 0f,
    val error: String? = null
)

class KawachViewModel(
    private val repository: TelegramRepository = KawachApplication.instance.repository,
    private val preferenceManager: PreferenceManager = KawachApplication.instance.preferenceManager
) : ViewModel() {

    val authState: StateFlow<TelegramAuthState> = repository.authState

    val themeMode: StateFlow<ThemeMode> = preferenceManager.themeModeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ThemeMode.SYSTEM
    )

    val configuredApiId: StateFlow<String> = preferenceManager.apiIdFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = preferenceManager.getSyncApiId()
    )

    val configuredApiHash: StateFlow<String> = preferenceManager.apiHashFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = preferenceManager.getSyncApiHash()
    )

    // Country selection for phone login
    private val _selectedCountry = MutableStateFlow<CountryCode>(CountryRepository.DEFAULT_COUNTRY)
    val selectedCountry: StateFlow<CountryCode> = _selectedCountry.asStateFlow()

    private val _phoneNumberInput = MutableStateFlow("")
    val phoneNumberInput: StateFlow<String> = _phoneNumberInput.asStateFlow()

    private val _otpInput = MutableStateFlow("")
    val otpInput: StateFlow<String> = _otpInput.asStateFlow()

    private val _passwordInput = MutableStateFlow("")
    val passwordInput: StateFlow<String> = _passwordInput.asStateFlow()

    // Filter, Search, and Folders
    private val _selectedFolderId = MutableStateFlow<String>("all")
    val selectedFolderId: StateFlow<String> = _selectedFolderId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<FileCategory>(FileCategory.ALL)
    val selectedCategory: StateFlow<FileCategory> = _selectedCategory.asStateFlow()

    private val _sortOption = MutableStateFlow<SortOption>(SortOption.NEWEST)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _isGridView = MutableStateFlow(false)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Active upload feedback
    private val _activeUploadName = MutableStateFlow<String?>(null)
    val activeUploadName: StateFlow<String?> = _activeUploadName.asStateFlow()

    private val _activeUploadProgress = MutableStateFlow(0f)
    val activeUploadProgress: StateFlow<Float> = _activeUploadProgress.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    // Multi-file upload queue
    private val _uploadQueue = MutableStateFlow<List<UploadQueueItem>>(emptyList())
    val uploadQueue: StateFlow<List<UploadQueueItem>> = _uploadQueue.asStateFlow()

    private val _uploadSummary = MutableStateFlow<String?>(null)
    val uploadSummary: StateFlow<String?> = _uploadSummary.asStateFlow()

    // In-app media preview (Photo Viewer & Video Player)
    private val _activePreviewFile = MutableStateFlow<CloudFile?>(null)
    val activePreviewFile: StateFlow<CloudFile?> = _activePreviewFile.asStateFlow()

    private val _previewLocalFile = MutableStateFlow<File?>(null)
    val previewLocalFile: StateFlow<File?> = _previewLocalFile.asStateFlow()

    // Multi-file selection & batch download
    private val _selectedFileIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedFileIds: StateFlow<Set<Long>> = _selectedFileIds.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _previewLoading = MutableStateFlow(false)
    val previewLoading: StateFlow<Boolean> = _previewLoading.asStateFlow()

    private val _previewProgress = MutableStateFlow(0f)
    val previewProgress: StateFlow<Float> = _previewProgress.asStateFlow()

    private val _previewError = MutableStateFlow<String?>(null)
    val previewError: StateFlow<String?> = _previewError.asStateFlow()

    private var uploadJob: kotlinx.coroutines.Job? = null
    private var previewJob: kotlinx.coroutines.Job? = null

    // Snackbars / notifications
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val allFolders: StateFlow<List<CloudFolder>> = repository.getFoldersFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val rawFiles: StateFlow<List<CloudFile>> = repository.getFilesFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Filtered, searched, and sorted files
    val filteredFiles: StateFlow<List<CloudFile>> = combine(
        rawFiles,
        _selectedFolderId,
        _searchQuery,
        _selectedCategory,
        _sortOption
    ) { files, folderId, query, category, sort ->
        files.filter { file ->
            val matchesFolder = if (folderId == "all" || folderId == "root") true else file.folderId == folderId
            val matchesQuery = query.isBlank() || file.name.contains(query.trim(), ignoreCase = true)
            val matchesCategory = category == FileCategory.ALL || file.category == category
            matchesFolder && matchesQuery && matchesCategory
        }.let { filtered ->
            when (sort) {
                SortOption.NEWEST -> filtered.sortedByDescending { it.uploadDate }
                SortOption.OLDEST -> filtered.sortedBy { it.uploadDate }
                SortOption.NAME_AZ -> filtered.sortedBy { it.name.lowercase() }
                SortOption.NAME_ZA -> filtered.sortedByDescending { it.name.lowercase() }
                SortOption.SIZE_LARGEST -> filtered.sortedByDescending { it.size }
                SortOption.SIZE_SMALLEST -> filtered.sortedBy { it.size }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val recentFiles: StateFlow<List<CloudFile>> = rawFiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setSelectedCountry(country: CountryCode) {
        _selectedCountry.value = country
    }

    fun setPhoneNumberInput(phone: String) {
        _phoneNumberInput.value = phone
    }

    fun setOtpInput(code: String) {
        _otpInput.value = code
    }

    fun setPasswordInput(password: String) {
        _passwordInput.value = password
    }

    fun setSelectedFolder(folderId: String) {
        _selectedFolderId.value = folderId
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: FileCategory) {
        _selectedCategory.value = category
    }

    fun setSortOption(sort: SortOption) {
        _sortOption.value = sort
    }

    fun toggleGridView() {
        _isGridView.value = !_isGridView.value
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferenceManager.setThemeMode(mode)
        }
    }

    fun updateApiCredentials(apiId: String, apiHash: String) {
        val cleanId = apiId.trim()
        val cleanHash = apiHash.trim()
        if (cleanId.isNotBlank() && cleanId.toIntOrNull() == null) {
            emitMessage("API ID must be a numeric value")
            return
        }
        repository.updateApiCredentials(cleanId, cleanHash)
        emitMessage("Telegram API credentials updated. Reconnecting to Telegram...")
    }

    fun resetAuthState() {
        repository.resetAuthState()
    }

    fun sendPhoneNumber() {
        val currentAuth = authState.value
        if (currentAuth is TelegramAuthState.SendingPhoneNumber) {
            return
        }

        val phone = _phoneNumberInput.value.trim()
        if (phone.isBlank()) {
            emitMessage("Please enter your Telegram phone number")
            return
        }

        val dial = _selectedCountry.value.dialCode
        val cleanPhoneDigits = phone.replace(Regex("[^0-9]"), "")
        val dialDigits = dial.replace(Regex("[^0-9]"), "")
        val fullPhone = when {
            phone.startsWith("+") -> "+$cleanPhoneDigits"
            cleanPhoneDigits.startsWith(dialDigits) -> "+$cleanPhoneDigits"
            cleanPhoneDigits.startsWith("0") -> "+$dialDigits" + cleanPhoneDigits.dropWhile { it == '0' }
            else -> "+$dialDigits$cleanPhoneDigits"
        }

        if (fullPhone.length < 9) {
            emitMessage("Please enter a valid phone number with country code")
            return
        }

        viewModelScope.launch {
            val result = repository.sendPhoneNumber(fullPhone)
            if (result.isFailure) {
                emitMessage(result.exceptionOrNull()?.message ?: "Failed to send verification code")
            }
        }
    }

    fun sendOtp() {
        if (authState.value is TelegramAuthState.VerifyingCode) {
            return
        }
        val code = _otpInput.value.trim()
        if (code.isBlank()) {
            emitMessage("Please enter the Telegram OTP")
            return
        }
        viewModelScope.launch {
            val result = repository.sendCode(code)
            if (result.isFailure) {
                emitMessage(result.exceptionOrNull()?.message ?: "Invalid code. Please try again.")
            }
        }
    }

    fun send2faPassword() {
        if (authState.value is TelegramAuthState.VerifyingPassword) {
            return
        }
        val password = _passwordInput.value
        if (password.isBlank()) {
            emitMessage("Please enter your Telegram 2FA password")
            return
        }
        viewModelScope.launch {
            val result = repository.sendPassword(password)
            if (result.isFailure) {
                emitMessage(result.exceptionOrNull()?.message ?: "Incorrect 2FA password")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            closePreview()
            cancelUploadQueue()
            clearFileSelection()
            _phoneNumberInput.value = ""
            _otpInput.value = ""
            _passwordInput.value = ""
            _uploadQueue.value = emptyList()
            val result = repository.logout()
            if (result.isSuccess) {
                emitMessage("Disconnected from Telegram")
            } else {
                emitMessage(result.exceptionOrNull()?.message ?: "Error logging out")
            }
        }
    }

    fun refreshFiles() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = repository.syncFiles()
            _isRefreshing.value = false
            if (result.isSuccess) {
                emitMessage("Files synchronized with Saved Messages")
            } else {
                emitMessage(result.exceptionOrNull()?.message ?: "Failed to sync files")
            }
        }
    }

    fun uploadFile(uri: Uri) {
        uploadFiles(listOf(uri))
    }

    fun uploadFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val newItems = uris.map { uri ->
            val (name, size, mime) = repository.getFileInfo(uri)
            UploadQueueItem(
                uri = uri,
                name = name,
                size = size,
                mimeType = mime,
                status = UploadItemStatus.QUEUED
            )
        }
        _uploadQueue.value = _uploadQueue.value + newItems
        startUploadQueueProcessing()
    }

    private fun startUploadQueueProcessing() {
        if (uploadJob?.isActive == true) return
        uploadJob = viewModelScope.launch {
            _isUploading.value = true
            while (true) {
                val currentQueue = _uploadQueue.value
                val nextItem = currentQueue.firstOrNull { it.status == UploadItemStatus.QUEUED } ?: break
                val totalCount = currentQueue.size
                val completedOrFailed = currentQueue.count { it.status == UploadItemStatus.COMPLETED || it.status == UploadItemStatus.FAILED }
                val currentIndex = completedOrFailed + 1

                _uploadSummary.value = if (totalCount > 1) "Uploading $currentIndex of $totalCount" else "Uploading to Saved Messages..."
                _activeUploadName.value = nextItem.name
                _activeUploadProgress.value = 0.05f

                _uploadQueue.value = _uploadQueue.value.map {
                    if (it.id == nextItem.id) it.copy(status = UploadItemStatus.UPLOADING, progress = 0.05f) else it
                }

                val folder = if (_selectedFolderId.value == "all") "root" else _selectedFolderId.value
                val result = repository.uploadFromUri(
                    uri = nextItem.uri,
                    folderId = folder,
                    onProgress = { progress ->
                        _activeUploadProgress.value = progress
                        _uploadQueue.value = _uploadQueue.value.map {
                            if (it.id == nextItem.id) it.copy(progress = progress) else it
                        }
                    }
                )

                if (result.isSuccess) {
                    val file = result.getOrThrow()
                    _uploadQueue.value = _uploadQueue.value.map {
                        if (it.id == nextItem.id) it.copy(status = UploadItemStatus.COMPLETED, progress = 1f) else it
                    }
                    emitMessage("Upload complete: ${file.name}")
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Upload failed"
                    _uploadQueue.value = _uploadQueue.value.map {
                        if (it.id == nextItem.id) it.copy(status = UploadItemStatus.FAILED, error = err) else it
                    }
                    emitMessage("Upload failed for ${nextItem.name}: $err")
                }
            }

            _isUploading.value = false
            _activeUploadName.value = null
            _activeUploadProgress.value = 0f
            _uploadSummary.value = null

            val finalQueue = _uploadQueue.value
            val failedCount = finalQueue.count { it.status == UploadItemStatus.FAILED }
            val completedCount = finalQueue.count { it.status == UploadItemStatus.COMPLETED }
            if (finalQueue.size > 1) {
                if (failedCount == 0) {
                    emitMessage("All $completedCount files uploaded successfully")
                } else {
                    emitMessage("Upload queue finished: $completedCount uploaded, $failedCount failed")
                }
            }
        }
    }

    fun cancelUploadQueue() {
        uploadJob?.cancel()
        uploadJob = null
        _uploadQueue.value = _uploadQueue.value.map {
            if (it.status == UploadItemStatus.QUEUED || it.status == UploadItemStatus.UPLOADING) {
                it.copy(status = UploadItemStatus.CANCELLED)
            } else it
        }
        _isUploading.value = false
        _activeUploadName.value = null
        _activeUploadProgress.value = 0f
        _uploadSummary.value = null
        emitMessage("Upload cancelled")
    }

    fun retryFailedUploads() {
        _uploadQueue.value = _uploadQueue.value.map {
            if (it.status == UploadItemStatus.FAILED || it.status == UploadItemStatus.CANCELLED) {
                it.copy(status = UploadItemStatus.QUEUED, progress = 0f, error = null)
            } else it
        }
        startUploadQueueProcessing()
    }

    fun clearCompletedUploads() {
        _uploadQueue.value = _uploadQueue.value.filter {
            it.status == UploadItemStatus.UPLOADING || it.status == UploadItemStatus.QUEUED
        }
    }

    fun openImagePreview(file: CloudFile) {
        _activePreviewFile.value = file
        fetchPreview(file)
    }

    fun openVideoPlayer(file: CloudFile) {
        _activePreviewFile.value = file
        fetchPreview(file)
    }

    fun closePreview() {
        previewJob?.cancel()
        _activePreviewFile.value = null
        _previewLocalFile.value = null
        _previewLoading.value = false
        _previewError.value = null
    }

    fun retryPreview() {
        _activePreviewFile.value?.let { fetchPreview(it) }
    }

    private fun fetchPreview(file: CloudFile) {
        previewJob?.cancel()
        _previewLocalFile.value = null
        _previewError.value = null
        _previewLoading.value = true
        _previewProgress.value = 0.05f

        previewJob = viewModelScope.launch {
            val result = repository.getOrFetchPreviewFile(file, onProgress = { progress ->
                _previewProgress.value = progress
            })
            _previewLoading.value = false
            if (result.isSuccess) {
                _previewLocalFile.value = result.getOrThrow()
            } else {
                _previewError.value = result.exceptionOrNull()?.message ?: "Failed to load file"
            }
        }
    }

    fun downloadFile(file: CloudFile, onReady: ((Uri) -> Unit)? = null) {
        viewModelScope.launch {
            emitMessage("Downloading ${file.name}...")
            val result = repository.downloadFile(file)
            if (result.isSuccess) {
                val downloaded = result.getOrThrow()
                emitMessage("Download complete: ${downloaded.finalFileName}")
                onReady?.invoke(downloaded.uri)
            } else {
                emitMessage(result.exceptionOrNull()?.message ?: "Download failed")
            }
        }
    }

    fun toggleSelectionMode() {
        val newMode = !_isSelectionMode.value
        _isSelectionMode.value = newMode
        if (!newMode) {
            _selectedFileIds.value = emptySet()
        }
    }

    fun setSelectionMode(enabled: Boolean) {
        _isSelectionMode.value = enabled
        if (!enabled) {
            _selectedFileIds.value = emptySet()
        }
    }

    fun toggleFileSelection(messageId: Long) {
        val current = _selectedFileIds.value
        if (current.contains(messageId)) {
            val updated = current - messageId
            _selectedFileIds.value = updated
            if (updated.isEmpty()) {
                _isSelectionMode.value = false
            }
        } else {
            _selectedFileIds.value = current + messageId
            _isSelectionMode.value = true
        }
    }

    fun selectAllFiles(files: List<CloudFile>) {
        _selectedFileIds.value = files.map { it.messageId }.toSet()
        _isSelectionMode.value = true
    }

    fun clearFileSelection() {
        _selectedFileIds.value = emptySet()
        _isSelectionMode.value = false
    }

    fun downloadSelectedFiles() {
        val selectedIds = _selectedFileIds.value
        if (selectedIds.isEmpty()) {
            emitMessage("No files selected to download")
            return
        }

        val allCurrentFiles = rawFiles.value
        val filesToDownload = allCurrentFiles.filter { it.messageId in selectedIds }
        if (filesToDownload.isEmpty()) {
            emitMessage("Selected files are no longer available")
            clearFileSelection()
            return
        }

        val count = filesToDownload.size
        clearFileSelection()

        viewModelScope.launch {
            emitMessage("Downloading $count selected files...")
            var successCount = 0
            var failCount = 0

            for (file in filesToDownload) {
                val result = repository.downloadFile(file)
                if (result.isSuccess) {
                    successCount++
                } else {
                    failCount++
                }
            }

            if (failCount == 0) {
                emitMessage("Downloaded all $successCount files to Downloads")
            } else {
                emitMessage("Downloaded $successCount files ($failCount failed)")
            }
        }
    }

    fun openFile(context: Context, file: CloudFile) {
        if (file.isImage) {
            openImagePreview(file)
            return
        }
        if (file.isVideo) {
            openVideoPlayer(file)
            return
        }
        if (!file.hasLocalFile) {
            downloadFile(file) { uri ->
                launchViewIntent(context, uri, file.mimeType)
            }
        } else {
            val uri = resolveFileUri(context, file)
            if (uri != null) {
                launchViewIntent(context, uri, file.mimeType)
            } else {
                downloadFile(file) { newUri ->
                    launchViewIntent(context, newUri, file.mimeType)
                }
            }
        }
    }

    fun shareFile(context: Context, file: CloudFile) {
        if (!file.hasLocalFile) {
            downloadFile(file) { uri ->
                launchShareIntent(context, uri, file.mimeType)
            }
        } else {
            val uri = resolveFileUri(context, file)
            if (uri != null) {
                launchShareIntent(context, uri, file.mimeType)
            } else {
                downloadFile(file) { newUri ->
                    launchShareIntent(context, newUri, file.mimeType)
                }
            }
        }
    }

    private fun resolveFileUri(context: Context, file: CloudFile): Uri? {
        val path = file.localPath ?: return null
        return if (path.startsWith("content://")) {
            val uri = Uri.parse(path)
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null && pfd.statSize > 0) {
                    pfd.close()
                    uri
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        } else {
            val f = File(path)
            if (f.exists() && f.length() > 0) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            } else {
                null
            }
        }
    }

    private fun launchViewIntent(context: Context, uri: Uri, mimeType: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (_: ActivityNotFoundException) {
            emitMessage("No compatible app found to open this file")
        } catch (e: Exception) {
            emitMessage("Could not open file: ${e.message}")
        }
    }

    private fun launchShareIntent(context: Context, uri: Uri, mimeType: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType.ifBlank { "*/*" }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share file"))
        } catch (_: ActivityNotFoundException) {
            emitMessage("No compatible app found to share this file")
        } catch (e: Exception) {
            emitMessage("Could not share file")
        }
    }

    fun deleteFile(file: CloudFile) {
        viewModelScope.launch {
            val result = repository.deleteFile(file)
            if (result.isSuccess) {
                emitMessage("File deleted from Saved Messages")
            } else {
                emitMessage(result.exceptionOrNull()?.message ?: "Failed to delete file")
            }
        }
    }

    fun renameFile(file: CloudFile, newName: String) {
        viewModelScope.launch {
            val result = repository.renameFile(file.messageId, newName)
            if (result.isSuccess) {
                emitMessage("File renamed")
            } else {
                emitMessage("Failed to rename file")
            }
        }
    }

    fun moveFile(file: CloudFile, targetFolderId: String) {
        viewModelScope.launch {
            val result = repository.moveFile(file.messageId, targetFolderId)
            if (result.isSuccess) {
                emitMessage("File moved to folder")
            } else {
                emitMessage("Failed to move file")
            }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val result = repository.createFolder(name)
            if (result.isSuccess) {
                emitMessage("Folder \"$name\" created")
            } else {
                emitMessage("Failed to create folder")
            }
        }
    }

    fun renameFolder(id: String, newName: String) {
        viewModelScope.launch {
            val result = repository.renameFolder(id, newName)
            if (result.isSuccess) {
                emitMessage("Folder renamed")
            } else {
                emitMessage("Failed to rename folder")
            }
        }
    }

    fun deleteFolder(id: String) {
        viewModelScope.launch {
            val result = repository.deleteFolder(id)
            if (result.isSuccess) {
                emitMessage("Folder removed")
            } else {
                emitMessage("Failed to remove folder")
            }
        }
    }

    private fun emitMessage(msg: String) {
        viewModelScope.launch {
            _messages.emit(msg)
        }
    }
}
