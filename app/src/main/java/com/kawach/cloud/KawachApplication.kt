package com.kawach.cloud

import android.app.Application
import com.kawach.cloud.data.local.AppDatabase
import com.kawach.cloud.data.local.PreferenceManager
import com.kawach.cloud.data.telegram.TelegramClientManager
import com.kawach.cloud.data.telegram.TelegramRepository

class KawachApplication : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    val preferenceManager: PreferenceManager by lazy {
        PreferenceManager(this)
    }

    val clientManager: TelegramClientManager by lazy {
        TelegramClientManager(this)
    }

    val repository: TelegramRepository by lazy {
        TelegramRepository(
            context = this,
            clientManager = clientManager,
            fileDao = database.fileDao(),
            folderDao = database.folderDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: KawachApplication
            private set
    }
}
