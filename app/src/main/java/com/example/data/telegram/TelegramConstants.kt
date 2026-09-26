package com.example.data.telegram

import com.example.BuildConfig

object TelegramConstants {
    // Application developer credentials from BuildConfig (.env)
    // Fallback to standard open-source Telegram client developer credentials if not specified in .env
    val API_ID: Int by lazy {
        try {
            val idStr = BuildConfig.TELEGRAM_API_ID
            if (!idStr.isNullOrBlank() && idStr != "0") {
                idStr.toInt()
            } else {
                94575 // Public developer API ID for open-source Telegram development
            }
        } catch (e: Exception) {
            94575
        }
    }

    val API_HASH: String by lazy {
        try {
            val hashStr = BuildConfig.TELEGRAM_API_HASH
            if (!hashStr.isNullOrBlank()) {
                hashStr
            } else {
                "a3406de8d1717142218bb14d800e635b" // Public developer API HASH
            }
        } catch (e: Exception) {
            "a3406de8d1717142218bb14d800e635b"
        }
    }

    const val APPLICATION_NAME = "Kawach Cloud"
    const val APPLICATION_VERSION = "1.0.0-alpha01"
    const val DEVICE_MODEL = "Android"
    const val SYSTEM_LANGUAGE = "en"

    // Signature used to uniquely identify Kawach Cloud messages in Saved Messages
    const val KAWACH_SIGNATURE = "[KawachCloud]"
    const val KAWACH_TAG = "#KawachCloud"
}
