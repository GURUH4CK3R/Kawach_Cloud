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
import com.example.data.local.PreferenceManager
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

import android.os.Build
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

class TelegramClientManager(
    private val context: Context,
    private val preferenceManager: PreferenceManager = PreferenceManager(context)
) {

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
    private var pendingPhoneNumber: String = ""

    // Progress listeners: fileId -> (progress: Float, isCompleted: Boolean, path: String?)
    private val progressListeners = ConcurrentHashMap<Int, (Float, Boolean, String?) -> Unit>()

    fun getEffectiveApiId(): Int {
        val custom = preferenceManager.getSyncApiId()
        if (custom.isNotBlank()) {
            val parsed = custom.toIntOrNull()
            if (parsed != null && parsed > 0) return parsed
        }
        return TelegramConstants.API_ID
    }

    fun getEffectiveApiHash(): String {
        val custom = preferenceManager.getSyncApiHash()
        if (custom.isNotBlank()) {
            return custom
        }
        return TelegramConstants.API_HASH
    }

    fun resetToPhoneInput() {
        _authState.value = TelegramAuthState.WaitingPhoneNumber
    }

    fun updateApiCredentials(apiId: String, apiHash: String) {
        preferenceManager.saveSyncApiCredentials(apiId, apiHash)
        scope.launch {
            try {
                preferenceManager.saveApiCredentials(apiId, apiHash)
            } catch (e: Exception) {}

            try {
                client?.send(TdApi.Close(), null)
            } catch (e: Exception) {}
            client = null

            // Delete old session DB so TDLib accepts fresh SetTdlibParameters
            try {
                databaseDir.deleteRecursively()
                databaseDir.mkdirs()
            } catch (e: Exception) {}

            _authState.value = TelegramAuthState.Initializing
            initClient()
        }
    }

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
        if (BuildConfig.DEBUG) {
            val stateName = when (state) {
                is TdApi.AuthorizationStateWaitTdlibParameters -> "WAIT_TDLIB_PARAMETERS"
                is TdApi.AuthorizationStateWaitPhoneNumber -> "WAIT_PHONE_NUMBER"
                is TdApi.AuthorizationStateWaitCode -> "WAIT_CODE"
                is TdApi.AuthorizationStateWaitPassword -> "WAIT_PASSWORD"
                is TdApi.AuthorizationStateReady -> "READY"
                is TdApi.AuthorizationStateLoggingOut -> "LOGGING_OUT"
                is TdApi.AuthorizationStateClosing -> "CLOSING"
                is TdApi.AuthorizationStateClosed -> "CLOSED"
                else -> state.javaClass.simpleName
            }
            Log.d("KawachAuth", "AUTH STATE: $stateName")
        }

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
                params.apiId = getEffectiveApiId()
                params.apiHash = getEffectiveApiHash()
                params.systemLanguageCode = TelegramConstants.SYSTEM_LANGUAGE
                params.deviceModel = Build.MODEL.ifBlank { "Android" }
                params.systemVersion = Build.VERSION.RELEASE.ifBlank { "14.0" }
                params.applicationVersion = TelegramConstants.APPLICATION_VERSION

                send(params) { result ->
                    if (result is TdApi.Error) {
                        val msg = if (result.message.contains("API_ID_INVALID", ignoreCase = true)) {
                            "Unable to connect to Telegram network. Please check your internet connection and try again."
                        } else {
                            "Connection error: ${result.message}"
                        }
                        _authState.value = TelegramAuthState.Error(msg)
                    }
                }
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                _authState.value = TelegramAuthState.WaitingPhoneNumber
            }
            is TdApi.AuthorizationStateWaitCode -> {
                val info = state.codeInfo
                val (deliveryType, deliveryDesc) = parseCodeInfo(info)
                val timeout = info?.timeout ?: 60
                val phone = if (pendingPhoneNumber.isNotBlank()) pendingPhoneNumber else {
                    (_authState.value as? TelegramAuthState.WaitingCode)?.phoneNumber ?: ""
                }
                _authState.value = TelegramAuthState.WaitingCode(
                    phoneNumber = phone,
                    timeout = timeout,
                    deliveryType = deliveryType,
                    deliveryDescription = deliveryDesc
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
            is TdApi.AuthorizationStateClosing -> {
                // TDLib is closing
            }
            is TdApi.AuthorizationStateClosed -> {
                client = null
                _authState.value = TelegramAuthState.Closed
            }
        }
    }

    private fun parseCodeInfo(info: TdApi.AuthenticationCodeInfo?): Pair<String, String> {
        if (info == null) return Pair("Telegram", "A login code was sent to your active Telegram session or via SMS.")
        return when (info.type) {
            is TdApi.AuthenticationCodeTypeTelegramMessage -> {
                Pair(
                    "Telegram App",
                    "We sent the login code to your active Telegram app. Check the chat from 'Telegram' (Service Notifications) on your other phone or Telegram session."
                )
            }
            is TdApi.AuthenticationCodeTypeSms -> {
                Pair(
                    "SMS",
                    "We sent the login code via SMS to your mobile number."
                )
            }
            is TdApi.AuthenticationCodeTypeCall -> {
                Pair(
                    "Phone Call",
                    "Telegram is calling your phone number to dictate your login code."
                )
            }
            is TdApi.AuthenticationCodeTypeFlashCall -> {
                Pair(
                    "Flash Call",
                    "Telegram is verifying your number via flash call."
                )
            }
            is TdApi.AuthenticationCodeTypeMissedCall -> {
                Pair(
                    "Missed Call",
                    "Enter the last digits of the incoming phone number that just called you."
                )
            }
            is TdApi.AuthenticationCodeTypeFragment -> {
                Pair(
                    "Fragment",
                    "Your login code was delivered via Fragment."
                )
            }
            else -> {
                Pair(
                    "Telegram",
                    "A login code was sent to your Telegram account."
                )
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
        val cleanPhone = fullPhoneNumber.replace(Regex("[^0-9+]"), "").trim()
        if (cleanPhone.length < 8) {
            val err = "Please enter a valid international phone number with country code"
            _authState.value = TelegramAuthState.Error(err)
            return Result.failure(Exception(err))
        }

        if (client == null) {
            initClient()
            delay(1000)
        }

        pendingPhoneNumber = cleanPhone
        _authState.value = TelegramAuthState.SendingPhoneNumber

        val settings = TdApi.PhoneNumberAuthenticationSettings()
        settings.allowFlashCall = false
        settings.allowMissedCall = false
        settings.isCurrentPhoneNumber = false
        settings.allowSmsRetrieverApi = false

        return try {
            withTimeout(25_000L) {
                val result = sendAsync(TdApi.SetAuthenticationPhoneNumber(cleanPhone, settings))
                if (result.isSuccess) {
                    if (_authState.value !is TelegramAuthState.WaitingCode) {
                        _authState.value = TelegramAuthState.WaitingCode(
                            phoneNumber = cleanPhone,
                            deliveryType = "Telegram",
                            deliveryDescription = "We sent your Telegram login code. Check your active Telegram app or SMS."
                        )
                    }
                    Result.success(Unit)
                } else {
                    val rawErr = result.exceptionOrNull()?.message ?: "Failed to send phone number"
                    val friendlyErr = parseTelegramError(rawErr)
                    _authState.value = TelegramAuthState.Error(friendlyErr)
                    Result.failure(Exception(friendlyErr))
                }
            }
        } catch (e: TimeoutCancellationException) {
            val timeoutErr = "Request timed out connecting to Telegram. Please check your internet connection or verify your Telegram API credentials."
            _authState.value = TelegramAuthState.Error(timeoutErr)
            Result.failure(Exception(timeoutErr))
        } catch (e: Exception) {
            val err = parseTelegramError(e.message ?: "Authentication error")
            _authState.value = TelegramAuthState.Error(err)
            Result.failure(Exception(err))
        }
    }

    suspend fun sendCode(code: String): Result<Unit> {
        _authState.value = TelegramAuthState.VerifyingCode
        val cleanCode = code.trim()
        return try {
            withTimeout(25_000L) {
                val result = sendAsync(TdApi.CheckAuthenticationCode(cleanCode))
                if (result.isSuccess) {
                    Result.success(Unit)
                } else {
                    val rawErr = result.exceptionOrNull()?.message ?: "Invalid Telegram OTP code"
                    val friendlyErr = parseTelegramError(rawErr)
                    _authState.value = TelegramAuthState.Error(friendlyErr)
                    Result.failure(Exception(friendlyErr))
                }
            }
        } catch (e: TimeoutCancellationException) {
            val timeoutErr = "Verification timed out. Please check your connection and try again."
            _authState.value = TelegramAuthState.Error(timeoutErr)
            Result.failure(Exception(timeoutErr))
        } catch (e: Exception) {
            val err = parseTelegramError(e.message ?: "Verification failed")
            _authState.value = TelegramAuthState.Error(err)
            Result.failure(Exception(err))
        }
    }

    suspend fun sendPassword(password: String): Result<Unit> {
        _authState.value = TelegramAuthState.VerifyingPassword
        return try {
            withTimeout(25_000L) {
                val result = sendAsync(TdApi.CheckAuthenticationPassword(password))
                if (result.isSuccess) {
                    Result.success(Unit)
                } else {
                    val rawErr = result.exceptionOrNull()?.message ?: "Incorrect 2FA password"
                    val friendlyErr = parseTelegramError(rawErr)
                    _authState.value = TelegramAuthState.Error(friendlyErr)
                    Result.failure(Exception(friendlyErr))
                }
            }
        } catch (e: TimeoutCancellationException) {
            val timeoutErr = "Password verification timed out. Please try again."
            _authState.value = TelegramAuthState.Error(timeoutErr)
            Result.failure(Exception(timeoutErr))
        } catch (e: Exception) {
            val err = parseTelegramError(e.message ?: "Password verification failed")
            _authState.value = TelegramAuthState.Error(err)
            Result.failure(Exception(err))
        }
    }

    fun parseTelegramError(raw: String): String {
        return when {
            raw.contains("PHONE_NUMBER_INVALID", ignoreCase = true) ->
                "Invalid phone number. Ensure your country code (+91 for India) is selected and enter valid subscriber digits."
            raw.contains("PHONE_PASSWORD_FLOOD", ignoreCase = true) ->
                "Too many failed attempts. Telegram requires you to wait before trying again."
            raw.contains("PHONE_CODE_INVALID", ignoreCase = true) ->
                "Incorrect Telegram login code. Please check your Telegram app or SMS and try again."
            raw.contains("PHONE_CODE_EXPIRED", ignoreCase = true) ->
                "Telegram login code has expired. Please re-enter your phone number to receive a new code."
            raw.contains("PASSWORD_HASH_INVALID", ignoreCase = true) ->
                "Incorrect Two-Step Verification (2FA) password. Please try again."
            raw.contains("FLOOD_WAIT", ignoreCase = true) -> {
                val seconds = Regex("\\d+").find(raw)?.value ?: "several"
                "Telegram rate limit: FLOOD_WAIT. Please wait $seconds seconds before requesting another code."
            }
            raw.contains("API_ID_INVALID", ignoreCase = true) || raw.contains("API_ID_PUBLISHED_FLOOD", ignoreCase = true) ->
                "Telegram network connection error ($raw). Please try again shortly or check your internet connection."
            raw.contains("NETWORK", ignoreCase = true) || raw.contains("CONNECTION", ignoreCase = true) ->
                "Network connection issue connecting to Telegram MTProto servers. Check your internet connection."
            else -> raw
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

    suspend fun downloadMessageFile(
        messageId: Long,
        fallbackFileId: Int = 0,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val chatId = getSavedChatId()
        if (chatId == 0L) {
            return@withContext Result.failure(IllegalStateException("Saved Messages chat is not available. Please ensure you are connected."))
        }

        // 1. Resolve fresh live file from live Telegram message
        var liveFile: TdApi.File? = null
        val msgResult = sendAsync(TdApi.GetMessage(chatId, messageId))
        if (msgResult.isSuccess) {
            val msg = msgResult.getOrThrow()
            when (val content = msg.content) {
                is TdApi.MessageDocument -> liveFile = content.document?.document
                is TdApi.MessagePhoto -> liveFile = content.photo?.sizes?.lastOrNull()?.photo
                is TdApi.MessageVideo -> liveFile = content.video?.video
                is TdApi.MessageAudio -> liveFile = content.audio?.audio
                is TdApi.MessageVoiceNote -> liveFile = content.voiceNote?.voice
                is TdApi.MessageAnimation -> liveFile = content.animation?.animation
            }
        }

        // If message resolution didn't yield file, try fallbackFileId
        if (liveFile == null && fallbackFileId > 0) {
            val getFileResult = sendAsync(TdApi.GetFile(fallbackFileId))
            if (getFileResult.isSuccess) {
                liveFile = getFileResult.getOrThrow()
            }
        }

        if (liveFile == null) {
            return@withContext Result.failure(Exception("Could not locate file in Telegram message $messageId"))
        }

        // 2. Check if already downloaded on disk
        val local = liveFile.local
        if (local?.isDownloadingCompleted == true && !local.path.isNullOrBlank()) {
            val src = File(local.path)
            if (src.exists() && src.length() > 0) {
                onProgress(1f)
                return@withContext Result.success(src)
            }
        }

        val targetFileId = liveFile.id

        // 3. Initiate download and monitor UpdateFile
        val downloaded = suspendCancellableCoroutine<Result<File>> { continuation ->
            continuation.invokeOnCancellation {
                progressListeners.remove(targetFileId)
                client?.send(TdApi.CancelDownloadFile(targetFileId, false)) {}
            }

            progressListeners[targetFileId] = { progress, isCompleted, downloadedPath ->
                onProgress(progress)
                if (isCompleted && continuation.isActive) {
                    progressListeners.remove(targetFileId)
                    scope.launch(Dispatchers.IO) {
                        var resolvedPath = downloadedPath
                        if (resolvedPath.isNullOrBlank()) {
                            val fresh = sendAsync(TdApi.GetFile(targetFileId)).getOrNull()
                            resolvedPath = fresh?.local?.path
                        }
                        if (!resolvedPath.isNullOrBlank()) {
                            val src = File(resolvedPath)
                            if (src.exists() && src.length() > 0) {
                                if (continuation.isActive) continuation.resume(Result.success(src))
                                return@launch
                            }
                        }
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(Exception("Downloaded source file not found or is empty")))
                        }
                    }
                }
            }

            client?.send(TdApi.DownloadFile(targetFileId, 1, 0, 0, false)) { result ->
                if (!continuation.isActive) return@send
                if (result is TdApi.Error) {
                    progressListeners.remove(targetFileId)
                    continuation.resume(Result.failure(Exception("Download error: ${result.message}")))
                } else if (result is TdApi.File) {
                    val l = result.local
                    if (l?.isDownloadingCompleted == true && !l.path.isNullOrBlank()) {
                        val src = File(l.path)
                        if (src.exists() && src.length() > 0) {
                            progressListeners.remove(targetFileId)
                            onProgress(1f)
                            continuation.resume(Result.success(src))
                        }
                    }
                }
            }
        }

        downloaded
    }

    suspend fun downloadFile(
        telegramFileId: Int,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val downloadFunction = TdApi.DownloadFile(telegramFileId, 1, 0, 0, false)

        val downloadedFile = suspendCancellableCoroutine<Result<File>> { continuation ->
            continuation.invokeOnCancellation {
                progressListeners.remove(telegramFileId)
                client?.send(TdApi.CancelDownloadFile(telegramFileId, false)) {}
            }

            progressListeners[telegramFileId] = { progress, isCompleted, downloadedPath ->
                onProgress(progress)
                if (isCompleted && continuation.isActive) {
                    progressListeners.remove(telegramFileId)
                    scope.launch(Dispatchers.IO) {
                        var resolvedPath = downloadedPath
                        if (resolvedPath.isNullOrBlank()) {
                            val fresh = sendAsync(TdApi.GetFile(telegramFileId)).getOrNull()
                            resolvedPath = fresh?.local?.path
                        }
                        if (!resolvedPath.isNullOrBlank()) {
                            val src = File(resolvedPath)
                            if (src.exists() && src.length() > 0) {
                                if (continuation.isActive) continuation.resume(Result.success(src))
                                return@launch
                            }
                        }
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(Exception("Downloaded source file not found or is empty")))
                        }
                    }
                }
            }

            client?.send(downloadFunction) { result ->
                if (!continuation.isActive) return@send
                if (result is TdApi.Error) {
                    progressListeners.remove(telegramFileId)
                    continuation.resume(Result.failure(Exception("Download error: ${result.message}")))
                } else if (result is TdApi.File) {
                    val local = result.local
                    if (local?.isDownloadingCompleted == true && !local.path.isNullOrBlank()) {
                        val src = File(local.path)
                        if (src.exists() && src.length() > 0) {
                            progressListeners.remove(telegramFileId)
                            onProgress(1f)
                            continuation.resume(Result.success(src))
                        }
                    }
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

    suspend fun saveFolderMetadataToTelegram(folderId: String, folderName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val chatId = getSavedChatId()
        if (chatId == 0L) return@withContext Result.failure(IllegalStateException("Chat ID not available"))

        val text = "[KawachCloud:Folder] id:$folderId | name:$folderName"
        val input = TdApi.InputMessageText()
        input.text = TdApi.FormattedText(text, null)
        input.clearDraft = true
        val sendMsg = TdApi.SendMessage()
        sendMsg.chatId = chatId
        sendMsg.inputMessageContent = input

        val result = sendAsync(sendMsg)
        if (result.isSuccess) Result.success(Unit)
        else Result.failure(result.exceptionOrNull() ?: Exception("Failed to save folder to Telegram"))
    }

    suspend fun syncSavedMessages(
        onBatch: suspend (List<CloudFile>, List<Pair<String, String>>) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        val chatId = getSavedChatId()
        if (chatId == 0L) {
            return@withContext Result.failure(IllegalStateException("Chat ID not available"))
        }

        var fromMessageId = 0L
        var totalSynced = 0
        var pages = 0
        val maxPages = 20 // 20 pages * 100 = up to 2000 messages

        while (pages < maxPages) {
            val getHistory = TdApi.GetChatHistory(chatId, fromMessageId, 0, 100, false)
            val result = sendAsync(getHistory)
            if (result.isFailure) {
                if (totalSynced > 0) break
                return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Failed to fetch messages"))
            }

            val messages = result.getOrThrow().messages
            if (messages.isEmpty()) {
                break
            }

            val batchFiles = mutableListOf<CloudFile>()
            val batchFolders = mutableListOf<Pair<String, String>>()

            for (msg in messages) {
                val parsed = parseSavedMessage(msg)
                parsed.file?.let { batchFiles.add(it) }
                parsed.folder?.let { batchFolders.add(it) }
            }

            if (batchFiles.isNotEmpty() || batchFolders.isNotEmpty()) {
                onBatch(batchFiles, batchFolders)
                totalSynced += batchFiles.size
            }

            fromMessageId = messages.last().id
            pages++

            if (messages.size < 100) {
                break // reached beginning of chat history
            }
        }

        Result.success(totalSynced)
    }

    suspend fun fetchKawachFilesFromSavedMessages(): Result<List<CloudFile>> = withContext(Dispatchers.IO) {
        val allFiles = mutableListOf<CloudFile>()
        val result = syncSavedMessages { batchFiles, _ ->
            allFiles.addAll(batchFiles)
        }
        if (result.isSuccess) {
            Result.success(allFiles)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Failed to fetch files"))
        }
    }

    data class ParsedMessageResult(
        val file: CloudFile? = null,
        val folder: Pair<String, String>? = null
    )

    fun parseSavedMessage(message: TdApi.Message): ParsedMessageResult {
        val content = message.content

        // 1. Check for folder metadata messages
        if (content is TdApi.MessageText) {
            val text = content.text.text
            if (text.startsWith("[KawachCloud:Folder]")) {
                val idMatch = Regex("id:([a-zA-Z0-9_-]+)").find(text)?.groupValues?.getOrNull(1)
                val nameMatch = Regex("name:(.+)").find(text)?.groupValues?.getOrNull(1)?.trim()
                if (!idMatch.isNullOrBlank() && !nameMatch.isNullOrBlank()) {
                    return ParsedMessageResult(folder = idMatch to nameMatch)
                }
            }
            return ParsedMessageResult()
        }

        // 2. Extract media and caption
        var captionText: String? = null
        var tdFile: TdApi.File? = null
        var fileName: String = ""
        var mimeType: String = ""
        var thumbFile: TdApi.File? = null

        when (content) {
            is TdApi.MessageDocument -> {
                captionText = content.caption?.text
                tdFile = content.document?.document
                fileName = content.document?.fileName?.ifBlank { "document_${message.id}" } ?: "document_${message.id}"
                mimeType = content.document?.mimeType?.ifBlank { "application/octet-stream" } ?: "application/octet-stream"
                thumbFile = content.document?.thumbnail?.file
            }
            is TdApi.MessagePhoto -> {
                captionText = content.caption?.text
                val sizes = content.photo?.sizes
                tdFile = sizes?.lastOrNull()?.photo
                thumbFile = sizes?.firstOrNull()?.photo
                fileName = "photo_${message.date}.jpg"
                mimeType = "image/jpeg"
            }
            is TdApi.MessageVideo -> {
                captionText = content.caption?.text
                tdFile = content.video?.video
                fileName = content.video?.fileName?.ifBlank { "video_${message.date}.mp4" } ?: "video_${message.date}.mp4"
                mimeType = content.video?.mimeType?.ifBlank { "video/mp4" } ?: "video/mp4"
                thumbFile = content.video?.thumbnail?.file
            }
            is TdApi.MessageAudio -> {
                captionText = content.caption?.text
                tdFile = content.audio?.audio
                val performer = content.audio?.performer ?: ""
                val title = content.audio?.title ?: ""
                fileName = content.audio?.fileName?.ifBlank {
                    "$performer - $title".trim(' ', '-').ifBlank { "audio_${message.date}.mp3" }
                } ?: "audio_${message.date}.mp3"
                mimeType = content.audio?.mimeType?.ifBlank { "audio/mpeg" } ?: "audio/mpeg"
                thumbFile = content.audio?.albumCoverThumbnail?.file
            }
            is TdApi.MessageVoiceNote -> {
                captionText = content.caption?.text
                tdFile = content.voiceNote?.voice
                fileName = "voice_${message.date}.ogg"
                mimeType = content.voiceNote?.mimeType?.ifBlank { "audio/ogg" } ?: "audio/ogg"
            }
            is TdApi.MessageAnimation -> {
                captionText = content.caption?.text
                tdFile = content.animation?.animation
                fileName = content.animation?.fileName?.ifBlank { "animation_${message.date}.mp4" } ?: "animation_${message.date}.mp4"
                mimeType = content.animation?.mimeType?.ifBlank { "video/mp4" } ?: "video/mp4"
                thumbFile = content.animation?.thumbnail?.file
            }
            else -> return ParsedMessageResult()
        }

        if (tdFile == null) return ParsedMessageResult()

        // Trigger thumbnail download in background if available and not yet downloaded
        if (thumbFile != null && thumbFile.local?.isDownloadingCompleted == false) {
            client?.send(TdApi.DownloadFile(thumbFile.id, 32, 0, 0, false)) {}
        }

        val folderRegex = Regex("folder:([a-zA-Z0-9_-]+)")
        val folderMatch = if (captionText != null) folderRegex.find(captionText) else null
        val explicitFolder = folderMatch?.groupValues?.getOrNull(1)

        val folderId = when {
            explicitFolder != null -> explicitFolder
            content is TdApi.MessagePhoto || content is TdApi.MessageVideo || content is TdApi.MessageAnimation -> "media"
            content is TdApi.MessageAudio || content is TdApi.MessageVoiceNote -> "media"
            content is TdApi.MessageDocument -> {
                val ext = fileName.substringAfterLast('.', "").lowercase()
                if (ext in listOf("zip", "rar", "7z", "tar", "gz")) "archives"
                else if (ext in listOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx")) "docs"
                else "saved_messages"
            }
            else -> "saved_messages"
        }

        val localPath = tdFile.local?.path
        val isDownloaded = tdFile.local?.isDownloadingCompleted == true && !localPath.isNullOrBlank() && File(localPath).exists()
        val thumbPath = thumbFile?.local?.path?.takeIf { !it.isNullOrBlank() && File(it).exists() }

        val cloudFile = CloudFile(
            messageId = message.id,
            telegramFileId = tdFile.id,
            remoteFileId = tdFile.remote?.id ?: "",
            name = fileName,
            size = tdFile.size,
            mimeType = mimeType,
            uploadDate = message.date.toLong(),
            folderId = folderId,
            localPath = localPath,
            isDownloaded = isDownloaded,
            kawachTag = captionText ?: "",
            thumbnailPath = thumbPath
        )

        return ParsedMessageResult(file = cloudFile)
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
                if (continuation.isActive) {
                    continuation.resume(Result.failure(IllegalStateException("TDLib client is not initialized")))
                }
                return@suspendCancellableCoroutine
            }
            try {
                c.send(function) { result ->
                    if (!continuation.isActive) return@send
                    if (result is TdApi.Error) {
                        continuation.resume(Result.failure(Exception(result.message ?: "Telegram Error ${result.code}")))
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        continuation.resume(Result.success(result as T))
                    }
                }
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume(Result.failure(e))
                }
            }
        }
}
