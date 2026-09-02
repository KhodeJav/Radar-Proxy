package com.radarproxy.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.radarproxy.domain.AppLanguage
import com.radarproxy.domain.AppTheme
import kotlinx.coroutines.flow.map

private val Context.radarSettings by preferencesDataStore("radar_settings")

enum class AppTextSize(val fontScale: Float) { LARGE(1f), SMALL(.9f), VERY_SMALL(.8f) }

data class Settings(val language: AppLanguage, val theme: AppTheme, val intervalMinutes: Long, val wifiOnly: Boolean, val autoUpdateEnabled: Boolean, val autoDeleteEnabled: Boolean, val autoDeleteHours: Long, val textSize: AppTextSize, val showCategoryControls: Boolean, val showSortControl: Boolean, val showTestAllControl: Boolean)

class SettingsStore(private val context: Context) {
    private val languageKey = stringPreferencesKey("language")
    private val themeKey = stringPreferencesKey("theme")
    private val intervalKey = longPreferencesKey("interval_minutes")
    private val wifiKey = booleanPreferencesKey("wifi_only")
    private val autoUpdateKey = booleanPreferencesKey("auto_update_enabled")
    private val autoDeleteKey = booleanPreferencesKey("auto_delete_enabled")
    private val autoDeleteHoursKey = longPreferencesKey("auto_delete_hours")
    private val textSizeKey = stringPreferencesKey("text_size")
    private val showCategoryKey = booleanPreferencesKey("show_category_controls")
    private val showSortKey = booleanPreferencesKey("show_sort_control")
    private val showTestAllKey = booleanPreferencesKey("show_test_all_control")

    val flow = context.radarSettings.data.map { preferences ->
        val language = preferences[languageKey]?.let { value -> runCatching { AppLanguage.valueOf(value) }.getOrNull() } ?: AppLanguage.PERSIAN
        val theme = preferences[themeKey]?.let { value -> runCatching { AppTheme.valueOf(value) }.getOrNull() } ?: AppTheme.LIGHT
        val textSize = preferences[textSizeKey]?.let { value -> runCatching { AppTextSize.valueOf(value) }.getOrNull() } ?: AppTextSize.LARGE
        Settings(
            language = language,
            theme = theme,
            intervalMinutes = (preferences[intervalKey] ?: 60L).coerceIn(15L, 60L),
            wifiOnly = preferences[wifiKey] ?: false,
            autoUpdateEnabled = preferences[autoUpdateKey] ?: true,
            autoDeleteEnabled = preferences[autoDeleteKey] ?: false,
            autoDeleteHours = (preferences[autoDeleteHoursKey] ?: 24L).let { if (it in listOf(4L, 8L, 12L, 24L)) it else 24L },
            textSize = textSize,
            showCategoryControls = preferences[showCategoryKey] ?: true,
            showSortControl = preferences[showSortKey] ?: true,
            showTestAllControl = preferences[showTestAllKey] ?: true
        )
    }

    suspend fun language(value: AppLanguage) = context.radarSettings.edit { it[languageKey] = value.name }
    suspend fun theme(value: AppTheme) = context.radarSettings.edit { it[themeKey] = value.name }
    suspend fun interval(minutes: Long) = context.radarSettings.edit { it[intervalKey] = minutes.coerceIn(15L, 60L) }
    suspend fun wifiOnly(value: Boolean) = context.radarSettings.edit { it[wifiKey] = value }
    suspend fun autoUpdateEnabled(value: Boolean) = context.radarSettings.edit { it[autoUpdateKey] = value }
    suspend fun autoDeleteEnabled(value: Boolean) = context.radarSettings.edit { it[autoDeleteKey] = value }
    suspend fun autoDeleteHours(value: Long) = context.radarSettings.edit { it[autoDeleteHoursKey] = value.takeIf { it in listOf(4L, 8L, 12L, 24L) } ?: 24L }
    suspend fun textSize(value: AppTextSize) = context.radarSettings.edit { it[textSizeKey] = value.name }
    suspend fun showCategoryControls(value: Boolean) = context.radarSettings.edit { it[showCategoryKey] = value }
    suspend fun showSortControl(value: Boolean) = context.radarSettings.edit { it[showSortKey] = value }
    suspend fun showTestAllControl(value: Boolean) = context.radarSettings.edit { it[showTestAllKey] = value }
}
