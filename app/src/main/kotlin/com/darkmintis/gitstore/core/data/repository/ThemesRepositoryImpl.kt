package com.darkmintis.gitstore.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.darkmintis.gitstore.core.domain.repository.ThemesRepository
import com.darkmintis.gitstore.core.presentation.model.AppTheme
import com.darkmintis.gitstore.core.presentation.utils.applyAppNightMode

class ThemesRepositoryImpl(
    private val preferences: DataStore<Preferences>
) : ThemesRepository {
    private val THEME_KEY = stringPreferencesKey("app_theme")
    private val AMOLED_KEY = booleanPreferencesKey("amoled_theme")
    private val IS_DARK_THEME_KEY = booleanPreferencesKey("is_dark_theme")

    override fun getThemeColor(): Flow<AppTheme> {
        return preferences.data.map { prefs ->
            val themeName = prefs[THEME_KEY]
            AppTheme.fromName(themeName)
        }
    }

    override suspend fun setThemeColor(theme: AppTheme) {
        preferences.edit { prefs ->
            prefs[THEME_KEY] = theme.name
        }
    }

    override fun getIsDarkTheme(): Flow<Boolean?> {
        return preferences.data.map { prefs ->
            prefs[IS_DARK_THEME_KEY]
        }
    }

    override suspend fun setDarkTheme(isDarkTheme: Boolean?) {
        preferences.edit { prefs ->
            if (isDarkTheme == null) {
                prefs.remove(IS_DARK_THEME_KEY)
            } else {
                prefs[IS_DARK_THEME_KEY] = isDarkTheme
            }
        }
        applyAppNightMode(isDarkTheme)
    }


    override fun getAmoledTheme(): Flow<Boolean> {
        return preferences.data.map { prefs ->
            prefs[AMOLED_KEY] ?: false
        }
    }

    override suspend fun setAmoledTheme(enabled: Boolean) {
        preferences.edit { prefs ->
            prefs[AMOLED_KEY] = enabled
        }
    }
}

