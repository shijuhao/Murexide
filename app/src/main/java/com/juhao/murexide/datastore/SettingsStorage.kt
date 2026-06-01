package com.juhao.murexide.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_preferences")

class SettingsStorage(private val context: Context) {
    
    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val SQUARE_AVATAR_KEY = booleanPreferencesKey("square_avatar")
    }

    // 主题模式
    val themeModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE_KEY] ?: "system"
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode
        }
    }

    suspend fun getThemeMode(): String {
        return themeModeFlow.first()
    }

    // 圆角正方形头像
    val squareAvatarFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SQUARE_AVATAR_KEY] ?: false
    }

    suspend fun setSquareAvatar(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SQUARE_AVATAR_KEY] = enabled
        }
    }

    suspend fun getSquareAvatar(): Boolean {
        return squareAvatarFlow.first()
    }
}