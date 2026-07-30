package com.lr.immersiveaudiobook.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("lr_audiobook_settings")

data class AppSettings(
    val themeMode: String = "SYSTEM",
    val speechRate: Float = 0.88f,
    val pitch: Float = 0.82f,
    val volume: Float = 1f,
    val fontSize: Int = 18,
    val lineSpacing: Float = 1.45f,
    val autoScroll: Boolean = true,
    val resumeAfterInterruption: Boolean = false,
    val wifiOnlyCache: Boolean = true,
    val cacheLimitMb: Int = 2048,
    val skipSeconds: Int = 15,
    val defaultPreset: String = "低沉悬疑"
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val rate = floatPreferencesKey("rate")
        val pitch = floatPreferencesKey("pitch")
        val volume = floatPreferencesKey("volume")
        val fontSize = intPreferencesKey("font_size")
        val lineSpacing = floatPreferencesKey("line_spacing")
        val autoScroll = booleanPreferencesKey("auto_scroll")
        val resumeAfterInterruption = booleanPreferencesKey("resume_after_interruption")
        val wifiOnlyCache = booleanPreferencesKey("wifi_only_cache")
        val cacheLimitMb = intPreferencesKey("cache_limit_mb")
        val skipSeconds = intPreferencesKey("skip_seconds")
        val defaultPreset = stringPreferencesKey("default_preset")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { values ->
        AppSettings(
            themeMode = values[Keys.theme] ?: "SYSTEM",
            speechRate = values[Keys.rate] ?: 0.88f,
            pitch = values[Keys.pitch] ?: 0.82f,
            volume = values[Keys.volume] ?: 1f,
            fontSize = values[Keys.fontSize] ?: 18,
            lineSpacing = values[Keys.lineSpacing] ?: 1.45f,
            autoScroll = values[Keys.autoScroll] ?: true,
            resumeAfterInterruption = values[Keys.resumeAfterInterruption] ?: false,
            wifiOnlyCache = values[Keys.wifiOnlyCache] ?: true,
            cacheLimitMb = values[Keys.cacheLimitMb] ?: 2048,
            skipSeconds = values[Keys.skipSeconds] ?: 15,
            defaultPreset = values[Keys.defaultPreset] ?: "低沉悬疑"
        )
    }

    suspend fun update(block: suspend (MutablePreferencesEditor) -> Unit) {
        context.dataStore.edit { values -> block(MutablePreferencesEditor(values)) }
    }

    class MutablePreferencesEditor internal constructor(
        private val values: androidx.datastore.preferences.core.MutablePreferences
    ) {
        operator fun <T> set(key: Preferences.Key<T>, value: T) {
            values[key] = value
        }

        fun setTheme(value: String) = set(Keys.theme, value)
        fun setRate(value: Float) = set(Keys.rate, value)
        fun setPitch(value: Float) = set(Keys.pitch, value)
        fun setVolume(value: Float) = set(Keys.volume, value)
        fun setFontSize(value: Int) = set(Keys.fontSize, value)
        fun setLineSpacing(value: Float) = set(Keys.lineSpacing, value)
        fun setAutoScroll(value: Boolean) = set(Keys.autoScroll, value)
        fun setResumeAfterInterruption(value: Boolean) = set(Keys.resumeAfterInterruption, value)
        fun setWifiOnlyCache(value: Boolean) = set(Keys.wifiOnlyCache, value)
        fun setCacheLimitMb(value: Int) = set(Keys.cacheLimitMb, value)
        fun setSkipSeconds(value: Int) = set(Keys.skipSeconds, value)
        fun setDefaultPreset(value: String) = set(Keys.defaultPreset, value)
    }
}
