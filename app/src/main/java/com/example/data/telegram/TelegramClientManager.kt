package com.example.data.telegram

import android.content.Context
import com.example.data.model.CloudFile
import com.example.data.model.TelegramAuthState
import com.example.data.model.TelegramUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class TelegramClientManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var client: Client? = null
    private var databaseDir: File = File(context.filesDir, "tdlib_db")
    private var filesDir: File = File(context.filesDir, "tdlib_files")

    private val _authState = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Uninitialized)
    val authState: StateFlow<TelegramAuthState> = _authState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var currentUserId: Long = 0L
    private var savedMessagesChatId: Long = 0L

    // Progress listeners: fileId -> (progress: Float, isCompleted: Boolean, path: String?)
    private val progressListeners = ConcurrentHashMap<Int, (Float, Boolean, String?) -> Unit>()

    init {
        initClient()
    }

    @Synchronized
    fun initClient() {
        if (client != null) return

        if (!databaseDir.exists()) databaseDir.mkdirs()
        if (!filesDir.exists()) filesDir.mkdirs()

        _authState.value = TelegramAuthState.Initializing

        try {
            client = Client.create({ update ->
                handleUpdate(update)
            }, { error ->
                scope.launch {
                    _events.emit("Client error: ${error.message}")
                }
            }, { exception ->
                scope.launch {
                    _events.emit("Exception: ${exception.message}")
                }
            })
        } catch (t: Throwable) {
            _authState.value = TelegramAuthState.Error(t.message ?: "Failed to initialize Telegram client")
        }
    }

    private fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> {
                handleAuthorizationState(update.authorizationState)
            }
            is TdApi.UpdateFile -> {
                handleUpdateFile(update.file)
            }
            is TdApi.UpdateUser -> {
                if (update.user.id == currentUserId) {
                    val user = mapUser(update.user)
                    _authState.value = TelegramAuthState.Authenticated(user)
                }
            }
        }
    }

    private fun handleAuthorizationState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val params = TdApi.SetTdlibParameters()
                params.useTestDc = false
                params.databaseDirectory = databaseDir.absolutePath
                params.filesDirectory = filesDir.absolutePath
                params.databaseEncryptionKey = ByteArray(0)
                params.useFileDatabase = true
                params.useChatInfoDatabase = true
                params.useMessageDatabase = true
                params.useSecretChats = false
                params.apiId = TelegramConstants.API_ID
                params.apiHash = TelegramConstants.API_HASH
                params.systemLanguageCode = TelegramConstants.SYSTEM_LANGUAGE
                params.deviceModel = TelegramConstants.DEVICE_MODEL
                params.applicationVersion = TelegramConstants.APPLICATION_VERSION

                send(params) { result ->
                    if (result is TdApi.Error) {
                        _authState.value = TelegramAuthState.Error("Parameters error: ${result.message}")
                    }
                }
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                _authState.value = TelegramAuthState.WaitingPhoneNumber
            }
            is TdApi.AuthorizationStateWaitCode -> {
                val info = state.codeInfo
                val deliveryType = info?.type?.javaClass?.simpleName?.replace("AuthenticationCodeType", "") ?: "Telegram"
                val timeout = info?.timeout ?: 60
                val phone = (_authState.value as? TelegramAuthState.WaitingCode)?.phoneNumber ?: ""
                _authState.value = TelegramAuthState.WaitingCode(
                    phoneNumber = phone,
                    timeout = timeout,
                    deliveryType = deliveryType
                )
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                _authState.value = TelegramAuthState.WaitingPassword(
                    hint = state.passwordHint,
                    hasRecovery = state.hasRecoveryEmailAddress
                )
            }
            is TdApi.AuthorizationStateReady -> {
                scope.launch {
                    loadCurrentUserAndSavedMessages()
                }
            }
            is TdApi.AuthorizationStateLoggingOut -> {
                _authState.value = TelegramAuthState.LoggingOut
            }
            is TdApi.AuthorizationStateClosed -> {
                client = null
                _authState.value = TelegramAuthState.WaitingPhoneNumber
            }
        }
    }

    private fun handleUpdateFile(file: TdApi.File) {
        val listener = progressListeners[file.id] ?: return
        val totalSize = file.size
        val downloadedSize = file.local?.downloadedSize ?: 0
        val uploadedSize = file.remote?.uploadedSize ?: 0

        val isUploading = file.remote?.isUploadingActive == true
        val isDownloading = file.local?.isDownloadingActive == true
        val isDownloadCompleted = file.local?.isDownloadingCompleted == true
        val isUploadCompleted = file.remote?.isUploadingCompleted == true

        when {
            isDownloading || isDownloadCompleted -> {
                val progress = if (totalSize > 0) (downloadedSize.toFloat() / totalSize.toFloat()).coerceIn(0f, 1f) else if (isDownloadCompleted) 1f else 0f
                val path = if (isDownloadCompleted) file.local?.path else null
                listener(progress, isDownloadCompleted, path)
            }
            isUploading || isUploadCompleted -> {
                val progress = if (totalSize > 0) (uploadedSize.toFloat() / totalSize.toFloat()).coerceIn(0f, 1f) else if (isUploadCompleted) 1f else 0f
                listener(progress, isUploadCompleted, null)
            }
        }
    }

    private suspend fun loadCurrentUserAndSavedMessages() {
        val meResult = sendAsync(TdApi.GetMe())
        if (meResult.isSuccess) {
            val user = meResult.getOrThrow()
            currentUserId = user.id
            val tgUser = mapUser(user)

            // Saved Messages chat is the private chat with current user
            val chatResult = sendAsync(TdApi.CreatePrivateChat(user.id, false))
            if (chatResult.isSuccess) {
                savedMessagesChatId = chatResult.getOrThrow().id
            }

            _authState.value = TelegramAuthState.Authenticated(tgUser)
        } else {
            _authState.value = TelegramAuthState.Error(
                meResult.exceptionOrNull()?.message ?: "Failed to retrieve user profile"
            )
        }
    }

    private fun mapUser(user: TdApi.User): TelegramUser {
        return TelegramUser(
            id = user.id,
            firstName = user.firstName ?: "",
            lastName = user.lastName ?: "",
            username = user.usernames?.activeUsernames?.firstOrNull() ?: "",
            phoneNumber = user.phoneNumber ?: "",
            profilePhotoPath = user.profilePhoto?.small?.local?.path
        )
    }

    suspend fun sendPhoneNumber(fullPhoneNumber: String): Result<Unit> {
        _authState.value = TelegramAuthState.SendingPhoneNumber
        val cleanPhone = fullPhoneNumber.replace(Regex("[^0-9+]"), "")
        val result = sendAsync(TdApi.SetAuthenticationPhoneNumber(cleanPhone, null))
        return if (result.isSuccess) {
            _authState.value = TelegramAuthState.WaitingCode(cleanPhone)
            Result.success(Unit)
        } else {
            val err = result.exceptionOrNull()?.message ?: "Failed to send phone number"
            _authState.value = TelegramAuthState.Error(err)
            Result.failure(Exception(err))
        }
    }

    suspend fun sendCode(code: String): Result<Unit> {
        _authState.value = TelegramAuthState.VerifyingCode
        val cleanCode = code.trim()
        val result = sendAsync(TdApi.CheckAuthenticationCode(cleanCode))
        return if (result.isSuccess) {
            Result.success(Unit)
        } else {
            val err = result.exceptionOrNull()?.message ?: "Invalid Telegram OTP code"
            _authState.value = TelegramAuthState.Error(err)
            Result.failure(Exception(err))
        }
    }

    suspend fun sendPassword(password: String): Result<Unit> {
        _authState.value = TelegramAuthState.VerifyingPassword
        val result = sendAsync(TdApi.CheckAuthenticationPassword(password))
        return if (result.isSuccess) {
            Result.success(Unit)
        } else {
            val err = result.exceptionOrNull()?.message ?: "Incorrect 2FA password"
            _authState.value = TelegramAuthState.Error(err)
            Result.failure(Exception(err))
        }
    }

    suspend fun logout(): Result<Unit> {
        _authState.value = TelegramAuthState.LoggingOut
        val result = sendAsync(TdApi.LogOut())
        return if (result.isSuccess) {
            currentUserId = 0L
            savedMessagesChatId = 0L
            _authState.value = TelegramAuthState.WaitingPhoneNumber
            Result.success(Unit)
        } else {
            val err = result.exceptionOrNull()?.message ?: "Logout failed"
            _authState.value = TelegramAuthState.WaitingPhoneNumber
            Result.failure(Exception(err))
        }
    }

    suspend fun getSavedChatId(): Long {
        if (savedMessagesChatId != 0L) return savedMessagesChatId
        if (currentUserId != 0L) {
            val chatResult = sendAsync(TdApi.CreatePrivateChat(currentUserId, false))
            if (chatResult.isSuccess) {
                savedMessagesChatId = chatResult.getOrThrow().id
                return savedMessagesChatId
            }
        }
        return 0L
    }

    suspend fun uploadFile(
        file: File,
        fileName: String,
        mimeType: String,
        folderId: String,
        onProgress: (Float) -> Unit
    ): Result<CloudFile> = withContext(Dispatchers.IO) {
        val chatId = getSavedChatId()
        if (chatId == 0L) {
            return@withContext Result.failure(IllegalStateException("Saved Messages chat is not available. Please ensure you are connected."))
        }

        val uniqueId = UUID.randomUUID().toString()
        val signatureCaption = "${TelegramConstants.KAWACH_SIGNATURE} folder:$folderId | id:$uniqueId | name:$fileName ${TelegramConstants.KAWACH_TAG}"

        val inputFile = TdApi.InputFileLocal(file.absolutePath)
        val formattedCaption = TdApi.FormattedText(signatureCaption, null)
        val inputDoc = TdApi.InputDocument()
        inputDoc.document = inputFile

        val inputMsgDoc = TdApi.InputMessageDocument()
        inputMsgDoc.document = inputDoc
        inputMsgDoc.caption = formattedCaption

        val sendMsg = TdApi.SendMessage()
        sendMsg.chatId = chatId
        sendMsg.inputMessageContent = inputMsgDoc

        val sendResult = sendAsync(sendMsg)
        if (sendResult.isFailure) {
            return@withContext Result.failure(sendResult.exceptionOrNull() ?: Exception("Upload failed"))
        }

        val message = sendResult.getOrThrow()
        val content = message.content as? TdApi.MessageDocument
        val doc = content?.document

        val tgFileId = doc?.document?.id ?: 0

        if (tgFileId > 0) {
            progressListeners[tgFileId] = { progress, isCompleted, _ ->
                onProgress(progress)
                if (isCompleted) {
                    progressListeners.remove(tgFileId)
                }
            }
        }

        val cloudFile = CloudFile(
            messageId = message.id,
            telegramFileId = tgFileId,
            remoteFileId = doc?.document?.remote?.id ?: uniqueId,
            name = fileName,
            size = file.length(),
            mimeType = mimeType,
            uploadDate = message.date.toLong(),
            folderId = folderId,
            localPath = file.absolutePath,
            isDownloaded = true,
            isUploading = false,
            uploadProgress = 1f,
            kawachTag = signatureCaption
        )

        Result.success(cloudFile)
    }

    suspend fun downloadFile(
        telegramFileId: Int,
        targetFile: File,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val downloadFunction = TdApi.DownloadFile(telegramFileId, 1, 0, 0, false)

        val downloadedFile = suspendCancellableCoroutine<Result<File>> { continuation ->
            progressListeners[telegramFileId] = { progress, isCompleted, downloadedPath ->
                onProgress(progress)
                if (isCompleted && continuation.isActive) {
                    progressListeners.remove(telegramFileId)
                    if (downloadedPath != null) {
                        try {
                            val src = File(downloadedPath)
                            if (src.exists()) {
                                targetFile.parentFile?.mkdirs()
                                src.copyTo(targetFile, overwrite = true)
                                continuation.resume(Result.success(targetFile))
                            } else {
                                continuation.resume(Result.failure(Exception("Downloaded source file not found")))
                            }
                        } catch (e: Exception) {
                            continuation.resume(Result.failure(e))
                        }
                    } else {
                        continuation.resume(Result.failure(Exception("Downloaded path is null")))
                    }
                }
            }

            client?.send(downloadFunction) { result ->
                if (result is TdApi.Error && continuation.isActive) {
                    progressListeners.remove(telegramFileId)
                    continuation.resume(Result.failure(Exception("Download error: ${result.message}")))
                }
            }
        }

        downloadedFile
    }

    suspend fun deleteFile(messageId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val chatId = getSavedChatId()
        if (chatId == 0L) {
            return@withContext Result.failure(IllegalStateException("Chat ID not available"))
        }

        val deleteMsg = TdApi.DeleteMessages(chatId, longArrayOf(messageId), true)
        val result = sendAsync(deleteMsg)
        return@withContext if (result.isSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Delete failed"))
        }
    }

    suspend fun fetchKawachFilesFromSavedMessages(): Result<List<CloudFile>> = withContext(Dispatchers.IO) {
        val chatId = getSavedChatId()
        if (chatId == 0L) {
            return@withContext Result.failure(IllegalStateException("Chat ID not available"))
        }

        val files = mutableListOf<CloudFile>()
        var fromMessageId = 0L

        // Fetch up to 100 messages from chat history
        val getHistory = TdApi.GetChatHistory(chatId, fromMessageId, 0, 100, false)
        val result = sendAsync(getHistory)

        if (result.isSuccess) {
            val messages = result.getOrThrow().messages
            for (msg in messages) {
                val parsed = parseKawachMessage(msg)
                if (parsed != null) {
                    files.add(parsed)
                }
            }
            Result.success(files)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Failed to fetch messages"))
        }
    }

    private fun parseKawachMessage(message: TdApi.Message): CloudFile? {
        val content = message.content

        val (captionText, doc) = when (content) {
            is TdApi.MessageDocument -> {
                content.caption?.text to content.document
            }
            else -> return null
        }

        // Must contain Kawach Cloud signature to be treated as a Kawach Cloud file
        if (captionText == null || (!captionText.contains(TelegramConstants.KAWACH_SIGNATURE) && !captionText.contains(TelegramConstants.KAWACH_TAG))) {
            return null
        }

        // Extract folderId from caption e.g., "folder:Documents" or fallback "root"
        val folderRegex = Regex("folder:([a-zA-Z0-9_-]+)")
        val folderMatch = folderRegex.find(captionText)
        val folderId = folderMatch?.groupValues?.getOrNull(1) ?: "root"

        val fileName = doc.fileName?.ifBlank { "file_${message.id}" } ?: "file_${message.id}"
        val mimeType = doc.mimeType?.ifBlank { "application/octet-stream" } ?: "application/octet-stream"
        val size = doc.document?.size ?: 0L
        val localPath = doc.document?.local?.path
        val isDownloaded = doc.document?.local?.isDownloadingCompleted == true && !localPath.isNullOrBlank() && File(localPath).exists()

        return CloudFile(
            messageId = message.id,
            telegramFileId = doc.document?.id ?: 0,
            remoteFileId = doc.document?.remote?.id ?: "",
            name = fileName,
            size = size,
            mimeType = mimeType,
            uploadDate = message.date.toLong(),
            folderId = folderId,
            localPath = localPath,
            isDownloaded = isDownloaded,
            kawachTag = captionText
        )
    }

    private fun send(function: TdApi.Function<*>, handler: (TdApi.Object) -> Unit) {
        client?.send(function) { result ->
            handler(result)
        }
    }

    private suspend fun <T : TdApi.Object> sendAsync(function: TdApi.Function<T>): Result<T> =
        suspendCancellableCoroutine { continuation ->
            val c = client
            if (c == null) {
                continuation.resume(Result.failure(IllegalStateException("TDLib client is not initialized")))
                return@suspendCancellableCoroutine
            }
            c.send(function) { result ->
                if (result is TdApi.Error) {
                    continuation.resume(Result.failure(Exception(result.message ?: "Telegram Error ${result.code}")))
                } else {
                    @Suppress("UNCHECKED_CAST")
                    continuation.resume(Result.success(result as T))
                }
            }
        }
}
