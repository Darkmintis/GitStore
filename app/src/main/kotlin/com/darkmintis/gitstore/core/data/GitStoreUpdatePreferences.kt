package com.darkmintis.gitstore.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class GitStoreUpdatePreferences(
    private val preferences: DataStore<Preferences>,
) {
    private val ignoredVersionKey = stringPreferencesKey("ignored_gitstore_version")

    val ignoredVersion: Flow<String?> = preferences.data.map { it[ignoredVersionKey] }

    suspend fun getIgnoredVersion(): String? = ignoredVersion.first()

    suspend fun ignoreVersion(versionTag: String) {
        preferences.edit { it[ignoredVersionKey] = versionTag }
    }
}
