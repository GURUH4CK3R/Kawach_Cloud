package com.example.data.model

data class TelegramUser(
    val id: Long,
    val firstName: String,
    val lastName: String = "",
    val username: String = "",
    val phoneNumber: String = "",
    val profilePhotoPath: String? = null
) {
    val displayName: String
        get() {
            val full = "$firstName $lastName".trim()
            return when {
                full.isNotEmpty() -> full
                username.isNotEmpty() -> "@$username"
                phoneNumber.isNotEmpty() -> phoneNumber
                else -> "User #$id"
            }
        }

    val displayHandle: String
        get() = if (username.isNotEmpty()) "@$username" else phoneNumber
}

sealed interface TelegramAuthState {
    data object Uninitialized : TelegramAuthState
    data object Initializing : TelegramAuthState
    data object WaitingPhoneNumber : TelegramAuthState
    data object SendingPhoneNumber : TelegramAuthState
    data class WaitingCode(
        val phoneNumber: String,
        val timeout: Int = 60,
        val deliveryType: String = "Telegram"
    ) : TelegramAuthState
    data object VerifyingCode : TelegramAuthState
    data class WaitingPassword(
        val hint: String? = null,
        val hasRecovery: Boolean = false
    ) : TelegramAuthState
    data object VerifyingPassword : TelegramAuthState
    data class Authenticated(val user: TelegramUser) : TelegramAuthState
    data class Error(val message: String, val canRetry: Boolean = true) : TelegramAuthState
    data object LoggingOut : TelegramAuthState
}
