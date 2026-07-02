package com.juhao.murexide.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SettingsStorage(private val context: Context) {
    
    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val THEME_COLOR_KEY = stringPreferencesKey("theme_color")
        private val SQUARE_AVATAR_KEY = booleanPreferencesKey("square_avatar")
        private val AVATAR_FOLLOW_KEY = booleanPreferencesKey("avatar_follow")
        private val BIG_SCREEN_KEY = booleanPreferencesKey("big_screen")
        private val SHOW_STICKY_KEY = booleanPreferencesKey("show_sticky")
        private val UPDATE_CHANNEL_KEY = stringPreferencesKey("update_channel")
        
        private val BUBBLE_CORNER_RADIUS_KEY = floatPreferencesKey("bubble_corner_radius")
        private val BUBBLE_OPACITY_KEY = floatPreferencesKey("bubble_opacity")
        private val SHOW_MY_BUBBLE_AVATAR_KEY = booleanPreferencesKey("show_my_bubble_avatar")
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

    // 主题颜色
    val themeColorFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_COLOR_KEY] ?: "DYNAMIC"
    }

    suspend fun setThemeColor(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_COLOR_KEY] = mode
        }
    }

    suspend fun getThemeColor(): String {
        return themeColorFlow.first()
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
    
    // 头像跟随
    val avatarFollowFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AVATAR_FOLLOW_KEY] ?: true
    }

    suspend fun setAvatarFollow(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AVATAR_FOLLOW_KEY] = enabled
        }
    }

    suspend fun getAvatarFollow(): Boolean {
        return avatarFollowFlow.first()
    }

    // 大屏模式
    val bigScreenFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[BIG_SCREEN_KEY] ?: true
    }

    suspend fun setBigScreen(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BIG_SCREEN_KEY] = enabled
        }
    }

    suspend fun getBigScreen(): Boolean {
        return bigScreenFlow.first()
    }
    
    // 显示置顶会话
    val showStickyFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHOW_STICKY_KEY] ?: true
    }

    suspend fun setShowSticky(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_STICKY_KEY] = enabled
        }
    }

    suspend fun getShowSticky(): Boolean {
        return showStickyFlow.first()
    }
    
    // 更新频道
    val updateChannelFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[UPDATE_CHANNEL_KEY] ?: "stable"
    }

    suspend fun setUpdateChannel(channel: String) {
        context.dataStore.edit { preferences ->
            preferences[UPDATE_CHANNEL_KEY] = channel
        }
    }

    suspend fun getUpdateChannel(): String {
        return updateChannelFlow.first()
    }
    
    // ====== 气泡圆角 ======
    val bubbleCornerRadiusFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[BUBBLE_CORNER_RADIUS_KEY] ?: 16f
    }

    suspend fun setBubbleCornerRadius(radius: Float) {
        context.dataStore.edit { preferences ->
            preferences[BUBBLE_CORNER_RADIUS_KEY] = radius
        }
    }

    suspend fun getBubbleCornerRadius(): Float {
        return bubbleCornerRadiusFlow.first()
    }

    // ====== 气泡透明度 ======
    val bubbleOpacityFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[BUBBLE_OPACITY_KEY] ?: 0.9f
    }

    suspend fun setBubbleOpacity(opacity: Float) {
        context.dataStore.edit { preferences ->
            preferences[BUBBLE_OPACITY_KEY] = opacity
        }
    }

    suspend fun getBubbleOpacity(): Float {
        return bubbleOpacityFlow.first()
    }

    // ====== 显示气泡（我）头像 ======
    val showMyBubbleAvatarFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHOW_MY_BUBBLE_AVATAR_KEY] ?: true
    }

    suspend fun setShowMyBubbleAvatar(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_MY_BUBBLE_AVATAR_KEY] = show
        }
    }

    suspend fun getShowMyBubbleAvatar(): Boolean {
        return showMyBubbleAvatarFlow.first()
    }
}