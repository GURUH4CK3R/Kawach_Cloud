package com.kawach.cloud.data.telegram

import com.kawach.cloud.BuildConfig

object TelegramConstants {

    val API_ID: Int by lazy {
        try {
            val idStr = BuildConfig.TELEGRAM_API_ID
            if (!idStr.isNullOrBlank() && idStr != "0" && idStr != "UNCONFIGURED") {
                val parsed = idStr.trim().toIntOrNull() ?: 0
                if (parsed > 0) parsed else 0
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }

    val API_HASH: String by lazy {
        try {
            val hashStr = BuildConfig.TELEGRAM_API_HASH
            if (!hashStr.isNullOrBlank() && hashStr != "UNCONFIGURED" && hashStr != "0") {
                hashStr.trim()
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun isApiIdPresent(): Boolean = API_ID != 0
    fun isApiIdValid(): Boolean = API_ID > 0
    fun isApiHashPresent(): Boolean = API_HASH.isNotBlank()
    fun isApiHashValidLength(): Boolean = API_HASH.length >= 16
    fun isApiConfigured(): Boolean = isApiIdValid() && isApiHashPresent() && isApiHashValidLength()

    const val APPLICATION_NAME = "Kawach Cloud"
    const val APPLICATION_VERSION = "10.0"
    const val DEVICE_MODEL = "Android"
    const val SYSTEM_LANGUAGE = "en"

    // Signature used to uniquely identify Kawach Cloud messages in Saved Messages
    const val KAWACH_SIGNATURE = "[KawachCloud]"
    const val KAWACH_TAG = "#KawachCloud"
}
