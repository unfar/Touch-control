package com.touchcontrol.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.touchcontrol.ui.screens.AppMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "touchcontrol_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_HOST = stringPreferencesKey("host")
        private val KEY_PORT = intPreferencesKey("port")
        private val KEY_CURSOR_SPEED = floatPreferencesKey("cursor_speed")
        private val KEY_SCROLL_SPEED = floatPreferencesKey("scroll_speed")
        private val KEY_HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
        private val KEY_APP_MODE = stringPreferencesKey("app_mode")

        private const val DEFAULT_HOST = ""
        private const val DEFAULT_PORT = 9090
        private const val DEFAULT_CURSOR_SPEED = 1.0f
        private const val DEFAULT_SCROLL_SPEED = 1.0f
    }

    val host: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_HOST] ?: DEFAULT_HOST
    }

    val port: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_PORT] ?: DEFAULT_PORT
    }

    val cursorSpeed: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_CURSOR_SPEED] ?: DEFAULT_CURSOR_SPEED
    }

    val scrollSpeed: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_SCROLL_SPEED] ?: DEFAULT_SCROLL_SPEED
    }

    val hapticFeedback: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_HAPTIC_FEEDBACK] ?: true
    }

    val darkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DARK_MODE] ?: true
    }

    /** 获取保存的模式 */
    suspend fun getAppMode(): AppMode {
        val modeStr = context.dataStore.data.first()[KEY_APP_MODE] ?: ""
        return when (modeStr) {
            "phone" -> AppMode.PHONE_CONTROLLER
            "tablet" -> AppMode.TABLET_RECEIVER
            else -> AppMode.NOT_SELECTED
        }
    }

    suspend fun saveAppMode(mode: AppMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_APP_MODE] = when (mode) {
                AppMode.PHONE_CONTROLLER -> "phone"
                AppMode.TABLET_RECEIVER -> "tablet"
                AppMode.NOT_SELECTED -> ""
            }
        }
    }

    suspend fun saveHost(host: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HOST] = host
        }
    }

    suspend fun savePort(port: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PORT] = port
        }
    }

    suspend fun saveCursorSpeed(speed: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CURSOR_SPEED] = speed
        }
    }

    suspend fun saveScrollSpeed(speed: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SCROLL_SPEED] = speed
        }
    }

    suspend fun saveHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HAPTIC_FEEDBACK] = enabled
        }
    }

    suspend fun saveDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DARK_MODE] = enabled
        }
    }
}
