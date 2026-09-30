package com.kawach.cloud.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kawach.cloud.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kawach_cloud_prefs")

class PreferenceManager(private val context: Context) {

    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    private val KEY_ACTIVE_USER_ID = longPreferencesKey("active_user_id")
    private val KEY_CACHED_USER_NAME = stringPreferencesKey("cached_user_name")
    private val KEY_CACHED_PHONE = stringPreferencesKey("cached_phone")

    private val sharedPrefs = context.getSharedPreferences("kawach_api_prefs", Context.MODE_PRIVATE)

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        val name = prefs[KEY_THEME_MODE] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    val activeUserIdFlow: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_USER_ID]
    }

    val cachedUserNameFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_CACHED_USER_NAME]
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    fun getSyncActiveUserId(): Long {
        return sharedPrefs.getLong("active_user_id", 0L)
    }

    fun saveSyncActiveUser(userId: Long, name: String, phone: String) {
        sharedPrefs.edit()
            .putLong("active_user_id", userId)
            .putString("cached_user_name", name)
            .putString("cached_phone", phone)
            .apply()
    }

    fun clearSyncActiveUser() {
        sharedPrefs.edit()
            .remove("active_user_id")
            .remove("cached_user_name")
            .remove("cached_phone")
            .apply()
    }

    suspend fun setActiveUser(userId: Long, name: String, phone: String) {
        saveSyncActiveUser(userId, name, phone)
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_USER_ID] = userId
            prefs[KEY_CACHED_USER_NAME] = name
            prefs[KEY_CACHED_PHONE] = phone
        }
    }

    suspend fun clearActiveUser() {
        clearSyncActiveUser()
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ACTIVE_USER_ID)
            prefs.remove(KEY_CACHED_USER_NAME)
            prefs.remove(KEY_CACHED_PHONE)
        }
    }
}
