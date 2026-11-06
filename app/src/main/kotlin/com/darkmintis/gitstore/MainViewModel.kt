package com.darkmintis.gitstore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.darkmintis.gitstore.app.app_state.AppStateManager
import com.darkmintis.gitstore.core.data.services.PackageMonitor
import com.darkmintis.gitstore.core.data.data_source.TokenDataSource
import com.darkmintis.gitstore.core.domain.repository.InstalledAppsRepository
import com.darkmintis.gitstore.core.domain.repository.ThemesRepository

class MainViewModel(
    private val tokenDataSource: TokenDataSource,
    private val themesRepository: ThemesRepository,
    private val appStateManager: AppStateManager,
    private val packageMonitor: PackageMonitor,
    private val installedAppsRepository: InstalledAppsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MainState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val initialToken = tokenDataSource.reloadFromStore()
            _state.update {
                it.copy(
                    isCheckingAuth = false,
                    isLoggedIn = initialToken != null
                )
            }
            Logger.d("MainViewModel") { "Initial token loaded: ${initialToken != null}" }
        }

        viewModelScope.launch(Dispatchers.IO) {
            tokenDataSource
                .tokenFlow
                .drop(1)
                .distinctUntilChanged()
                .collect { authInfo ->
                    _state.update { it.copy(isLoggedIn = authInfo != null) }
                }
        }

        viewModelScope.launch {
            // Seed theme before first compose frame; keep splash up until then.
            val theme = themesRepository.getThemeColor().first()
            val amoled = themesRepository.getAmoledTheme().first()
            val isDark = themesRepository.getIsDarkTheme().first()
            _state.update {
                it.copy(
                    currentColorTheme = theme,
                    isAmoledTheme = amoled,
                    isDarkTheme = isDark,
                    isThemeReady = true,
                )
            }

            launch {
                themesRepository.getThemeColor().collect { color ->
                    _state.update { it.copy(currentColorTheme = color) }
                }
            }
            launch {
                themesRepository.getAmoledTheme().collect { isAmoled ->
                    _state.update { it.copy(isAmoledTheme = isAmoled) }
                }
            }
            launch {
                themesRepository.getIsDarkTheme().collect { isDarkTheme ->
                    _state.update { it.copy(isDarkTheme = isDarkTheme) }
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            appStateManager.appState.collect { appState ->
                _state.update {
                    it.copy(
                        rateLimitInfo = appState.rateLimitInfo,
                        showRateLimitDialog = appState.showRateLimitDialog
                    )
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val installedPackageNames = packageMonitor.getAllInstalledPackageNames()

                val appsInDb = installedAppsRepository.getAllInstalledApps().first()

                appsInDb.forEach { app ->
                    if (!installedPackageNames.contains(app.packageName)) {
                        Logger.d { "App ${app.packageName} no longer installed (not in system packages), removing from DB" }
                        installedAppsRepository.deleteInstalledApp(app.packageName)
                    } else if (app.installedVersionName == null) {  // Migrate only if new fields unset
                        val systemInfo = packageMonitor.getInstalledPackageInfo(app.packageName)
                        if (systemInfo != null) {
                            installedAppsRepository.updateApp(app.copy(
                                installedVersionName = systemInfo.versionName,
                                installedVersionCode = systemInfo.versionCode,
                                latestVersionName = systemInfo.versionName,
                                latestVersionCode = systemInfo.versionCode
                            ))
                            Logger.d { "Migrated ${app.packageName}: set versionName/code from system" }
                        } else {
                            installedAppsRepository.updateApp(app.copy(
                                installedVersionName = app.installedVersion,
                                installedVersionCode = 0L,
                                latestVersionName = app.installedVersion,
                                latestVersionCode = 0L
                            ))
                            Logger.d { "Migrated ${app.packageName}: fallback to tag as versionName" }
                        }
                    }
                }

                Logger.d { "Robust system existence sync and data migration completed" }
            } catch (e: Exception) {
                Logger.e { "Failed to sync existence or migrate data: ${e.message}" }
            }

            installedAppsRepository.checkAllForUpdates()
        }
    }

    fun onAction(action: MainAction) {
        when (action) {
            MainAction.DismissRateLimitDialog -> {
                appStateManager.dismissRateLimitDialog()
            }
        }
    }
}


