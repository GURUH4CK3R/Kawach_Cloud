package com.example.ui.viewmodel

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.KawachApplication
import com.example.data.local.PreferenceManager
import com.example.data.model.CloudFile
import com.example.data.model.CloudFolder
import com.example.data.model.CountryCode
import com.example.data.model.CountryRepository
import com.example.data.model.FileCategory
import com.example.data.model.TelegramAuthState
import com.example.data.telegram.TelegramRepository
import com.example.ui.theme.ThemeMode
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

    private val uploadMutex = kotlinx.coroutines.sync.Mutex()
    private var inFlightUri: String? = null

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
        val uriStr = uri.toString()
        if (_isUploading.value || !uploadMutex.tryLock()) {
            return
        }
        if (inFlightUri == uriStr) {
            uploadMutex.unlock()
            return
        }
        inFlightUri = uriStr

        viewModelScope.launch {
            try {
                _isUploading.value = true
                _activeUploadProgress.value = 0.05f
                _activeUploadName.value = "Preparing upload..."

                val folder = if (_selectedFolderId.value == "all") "root" else _selectedFolderId.value
                val result = repository.uploadFromUri(
                    uri = uri,
                    folderId = folder,
                    onProgress = { progress ->
                        _activeUploadProgress.value = progress
                    }
                )

                if (result.isSuccess) {
                    val file = result.getOrThrow()
                    emitMessage("Upload complete: ${file.name}")
                } else {
                    emitMessage(result.exceptionOrNull()?.message ?: "Upload failed")
                }
            } finally {
                _isUploading.value = false
                _activeUploadName.value = null
                inFlightUri = null
                uploadMutex.unlock()
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

    fun openFile(context: Context, file: CloudFile) {
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
