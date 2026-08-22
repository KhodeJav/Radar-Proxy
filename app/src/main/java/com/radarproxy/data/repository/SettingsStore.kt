package com.radarproxy.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.radarproxy.domain.AppLanguage
import com.radarproxy.domain.AppTheme
import kotlinx.coroutines.flow.map

private val Context.radarSettings by preferencesDataStore("radar_settings")

data class Settings(val language: AppLanguage, val theme: AppTheme, val intervalMinutes: Long, val wifiOnly: Boolean, val autoUpdateEnabled: Boolean, val autoDeleteEnabled: Boolean, val autoDeleteHours: Long)

class SettingsStore(private val context: Context) {
    private val languageKey = stringPreferencesKey("language")
    private val themeKey = stringPreferencesKey("theme")
    private val intervalKey = longPreferencesKey("interval_minutes")
    private val wifiKey = booleanPreferencesKey("wifi_only")
    private val autoUpdateKey = booleanPreferencesKey("auto_update_enabled")
    private val autoDeleteKey = booleanPreferencesKey("auto_delete_enabled")
    private val autoDeleteHoursKey = longPreferencesKey("auto_delete_hours")

    val flow = context.radarSettings.data.map { preferences ->
        val language = preferences[languageKey]?.let { value -> runCatching { AppLanguage.valueOf(value) }.getOrNull() } ?: AppLanguage.PERSIAN
        val theme = preferences[themeKey]?.let { value -> runCatching { AppTheme.valueOf(value) }.getOrNull() } ?: AppTheme.LIGHT
        Settings(language, theme, (preferences[intervalKey] ?: 60L).coerceIn(15L, 60L), preferences[wifiKey] ?: false, preferences[autoUpdateKey] ?: true, preferences[autoDeleteKey] ?: false, (preferences[autoDeleteHoursKey] ?: 24L).let { if (it in listOf(4L, 8L, 12L, 24L)) it else 24L })
    }

    suspend fun language(value: AppLanguage) = context.radarSettings.edit { it[languageKey] = value.name }
    suspend fun theme(value: AppTheme) = context.radarSettings.edit { it[themeKey] = value.name }
    suspend fun interval(minutes: Long) = context.radarSettings.edit { it[intervalKey] = minutes.coerceIn(15L, 60L) }
    suspend fun wifiOnly(value: Boolean) = context.radarSettings.edit { it[wifiKey] = value }
    suspend fun autoUpdateEnabled(value: Boolean) = context.radarSettings.edit { it[autoUpdateKey] = value }
    suspend fun autoDeleteEnabled(value: Boolean) = context.radarSettings.edit { it[autoDeleteKey] = value }
    suspend fun autoDeleteHours(value: Long) = context.radarSettings.edit { it[autoDeleteHoursKey] = value.takeIf { it in listOf(4L, 8L, 12L, 24L) } ?: 24L }
}
