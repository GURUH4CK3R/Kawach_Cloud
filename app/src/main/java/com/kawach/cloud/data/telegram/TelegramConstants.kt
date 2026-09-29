package com.kawach.cloud.data.telegram

import com.kawach.cloud.BuildConfig

object TelegramConstants {
    // Official Telegram open client credentials (Telegram Desktop MTProto API)
    // Built directly into Kawach Cloud so NO regular user needs to configure or enter credentials.
    const val OFFICIAL_API_ID = 17349
    const val OFFICIAL_API_HASH = "344583e45741c457fe1862106095a5eb"

    val API_ID: Int by lazy {
        try {
            val idStr = BuildConfig.TELEGRAM_API_ID
            if (!idStr.isNullOrBlank() && idStr != "0" && idStr != "94575" && idStr != "6") {
                idStr.toInt()
            } else {
                OFFICIAL_API_ID
            }
        } catch (e: Exception) {
            OFFICIAL_API_ID
        }
    }

    val API_HASH: String by lazy {
        try {
            val hashStr = BuildConfig.TELEGRAM_API_HASH
            if (!hashStr.isNullOrBlank() && hashStr != "a3406de8d1717142218bb14d800e635b" && hashStr != "eb06d4abfb49dc3eeb1aeb98ae0f581e") {
                hashStr
            } else {
                OFFICIAL_API_HASH
            }
        } catch (e: Exception) {
            OFFICIAL_API_HASH
        }
    }

    const val APPLICATION_NAME = "Kawach Cloud"
    const val APPLICATION_VERSION = "10.0"
    const val DEVICE_MODEL = "Android"
    const val SYSTEM_LANGUAGE = "en"

    // Signature used to uniquely identify Kawach Cloud messages in Saved Messages
    const val KAWACH_SIGNATURE = "[KawachCloud]"
    const val KAWACH_TAG = "#KawachCloud"
}
