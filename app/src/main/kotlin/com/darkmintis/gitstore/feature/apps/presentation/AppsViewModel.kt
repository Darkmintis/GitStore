package com.darkmintis.gitstore.feature.apps.presentation

import com.darkmintis.gitstore.R

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.darkmintis.gitstore.core.data.services.PackageMonitor
import com.darkmintis.gitstore.core.data.local.db.entities.InstalledApp
import com.darkmintis.gitstore.core.domain.repository.InstalledAppsRepository
import com.darkmintis.gitstore.core.presentation.utils.ErrorMapper
import com.darkmintis.gitstore.core.presentation.utils.StringProvider
import com.darkmintis.gitstore.feature.apps.domain.repository.AppsRepository
import com.darkmintis.gitstore.feature.apps.presentation.model.AppItem
import com.darkmintis.gitstore.feature.apps.presentation.model.UpdateAllProgress
import com.darkmintis.gitstore.feature.apps.presentation.model.UpdateState
import com.darkmintis.gitstore.core.data.services.Downloader
import com.darkmintis.gitstore.core.data.services.Installer
import com.darkmintis.gitstore.core.domain.use_cases.SyncInstalledAppsUseCase
import com.darkmintis.gitstore.feature.details.domain.repository.DetailsRepository
import java.io.File

class AppsViewModel(
    private val appsRepository: AppsRepository,
    private val installer: Installer,
    private val downloader: Downloader,
    private val installedAppsRepository: InstalledAppsRepository,
    private val packageMonitor: PackageMonitor,
    private val detailsRepository: DetailsRepository,
    private val syncInstalledAppsUseCase: SyncInstalledAppsUseCase,
    private val stringProvider: StringProvider
) : ViewModel() {

    private var hasLoadedInitialData = false
    private val activeUpdates = mutableMapOf<String, Job>()
    private val updateOutcomes = mutableMapOf<String, Boolean>()
    private var updateAllJob: Job? = null
    private var isBatchUpdating = false

    /**
     * Outlives the ViewModel so cleanup that must happen after [onCleared] (when
     * viewModelScope is already cancelled) can still run.
     */
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(AppsState())
    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                loadApps()
                hasLoadedInitialData = true
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AppsState()
        )

    private val _events = Channel<AppsEvent>()
    val events = _events.receiveAsFlow()

    private fun loadApps() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            try {
                val syncResult = syncInstalledAppsUseCase()
                if (syncResult.isFailure) {
                    Logger.w { "Sync had issues but continuing: ${syncResult.exceptionOrNull()?.message}" }
                }

                appsRepository.getApps().collect { apps ->
                    val appItems = apps.map { app ->
                        val existing = _state.value.apps.find {
                            it.installedApp.packageName == app.packageName
                        }
                        AppItem(
                            installedApp = app,
                            updateState = existing?.updateState ?: UpdateState.Idle,
                            downloadProgress = existing?.downloadProgress,
                            error = existing?.error
                        )
                    }.sortedBy { it.installedApp.isUpdateAvailable }

                    _state.update {
                        it.copy(
                            apps = appItems,
                            isLoading = false,
                            errorMessage = null,
                            updateAllButtonEnabled = appItems.any { item ->
                                item.installedApp.isUpdateAvailable
                            }
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e { "Failed to load apps: ${e.message}" }
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = ErrorMapper.message(e, stringProvider)
                    )
                }
            }
        }
    }


    private suspend fun syncSystemExistenceAndMigrate() {
        withContext(Dispatchers.IO) {
            try {
                val installedPackageNames = packageMonitor.getAllInstalledPackageNames()
                val appsInDb = installedAppsRepository.getAllInstalledApps().first()

                appsInDb.forEach { app ->
                    if (!installedPackageNames.contains(app.packageName)) {
                        Logger.d { "App ${app.packageName} no longer installed (not in system packages), removing from DB" }
                        installedAppsRepository.deleteInstalledApp(app.packageName)
                    } else if (app.installedVersionName == null) {
                        val systemInfo = packageMonitor.getInstalledPackageInfo(app.packageName)
                        if (systemInfo != null) {
                            installedAppsRepository.updateApp(
                                app.copy(
                                    installedVersionName = systemInfo.versionName,
                                    installedVersionCode = systemInfo.versionCode,
                                    latestVersionName = systemInfo.versionName,
                                    latestVersionCode = systemInfo.versionCode
                                )
                            )
                            Logger.d { "Migrated ${app.packageName}: set versionName/code from system" }
                        } else {
                            installedAppsRepository.updateApp(
                                app.copy(
                                    installedVersionName = app.installedVersion,
                                    installedVersionCode = 0L,
                                    latestVersionName = app.installedVersion,
                                    latestVersionCode = 0L
                                )
                            )
                            Logger.d { "Migrated ${app.packageName}: fallback to tag as versionName" }
                        }
                    }
                }

                Logger.d { "Robust system existence sync and data migration completed" }
            } catch (e: Exception) {
                Logger.e { "Failed to sync existence or migrate data: ${e.message}" }
            }
        }
    }

    private fun checkAllForUpdates() {
        viewModelScope.launch {
            try {
                syncInstalledAppsUseCase()

                installedAppsRepository.checkAllForUpdates()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e { "Check all for updates failed: ${e.message}" }
                _events.send(
                    AppsEvent.ShowError(
                        ErrorMapper.message(e, stringProvider)
                    )
                )
            }
        }
    }

    fun onAction(action: AppsAction) {
        when (action) {
            AppsAction.OnNavigateBackClick -> {
            }

            is AppsAction.OnSearchChange -> {
                _state.update { it.copy(searchQuery = action.query) }
            }

            is AppsAction.OnOpenApp -> {
                openApp(action.app)
            }

            is AppsAction.OnUpdateApp -> {
                updateSingleApp(action.app)
            }

            is AppsAction.OnCancelUpdate -> {
                cancelUpdate(action.packageName)
            }

            AppsAction.OnUpdateAll -> {
                updateAllApps()
            }

            AppsAction.OnCancelUpdateAll -> {
                cancelAllUpdates()
            }

            AppsAction.OnCheckAllForUpdates -> {
                checkAllForUpdates()
            }

            is AppsAction.OnNavigateToRepo -> {
                viewModelScope.launch {
                    _events.send(AppsEvent.NavigateToRepo(action.repoId))
                }
            }

            AppsAction.OnRetry -> {
                loadApps()
            }
        }
    }

    private fun openApp(app: InstalledApp) {
        viewModelScope.launch {
            try {
                appsRepository.openApp(
                    installedApp = app,
                    onCantLaunchApp = {
                        viewModelScope.launch {
                            _events.send(
                                AppsEvent.ShowError(
                                    stringProvider.getString(
                                        R.string.cannot_launch,
                                        app.appName
                                    )
                                )
                            )
                        }
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e { "Failed to open app: ${e.message}" }
                _events.send(
                    AppsEvent.ShowError(
                        ErrorMapper.message(e, stringProvider)
                    )
                )
            }
        }
    }

    private fun updateSingleApp(app: InstalledApp) {
        if (activeUpdates.containsKey(app.packageName)) {
            Logger.w { "Update already in progress for ${app.packageName}" }
            return
        }

        val job = viewModelScope.launch {
            try {
                updateAppState(app.packageName, UpdateState.CheckingUpdate)

                val latestRelease = try {
                    detailsRepository.getLatestPublishedRelease(
                        owner = app.repoOwner,
                        repo = app.repoName,
                        defaultBranch = ""
                    )
                } catch (e: Exception) {
                    Logger.e { "Failed to fetch latest release: ${e.message}" }
                    throw IllegalStateException("Failed to fetch latest release: ${e.message}")
                }

                if (latestRelease == null) {
                    throw IllegalStateException("No release found for ${app.appName}")
                }

                val installableAssets = latestRelease.assets.filter { asset ->
                    installer.isAssetInstallable(asset.name)
                }

                if (installableAssets.isEmpty()) {
                    throw IllegalStateException("No installable assets found for this platform")
                }

                val primaryAsset = installer.choosePrimaryAsset(installableAssets)
                    ?: throw IllegalStateException("Could not determine primary asset")

                Logger.d {
                    "Update: ${app.appName} from ${app.installedVersion} to ${latestRelease.tagName}, " +
                            "asset: ${primaryAsset.name}"
                }

                val latestAssetUrl = primaryAsset.downloadUrl
                val latestAssetName = primaryAsset.name
                val latestVersion = latestRelease.tagName
                val latestAssetSize = primaryAsset.size

                val ext = latestAssetName.substringAfterLast('.', "").lowercase()
                installer.ensurePermissionsOrThrow(ext)

                val existingPath = downloader.getDownloadedFilePath(latestAssetName)
                if (existingPath != null) {
                    val file = File(existingPath)
                    try {
                        val apkInfo =
                            installer.getApkInfoExtractor().extractPackageInfo(existingPath)
                        val normalizedExisting =
                            apkInfo?.versionName?.removePrefix("v")?.removePrefix("V") ?: ""
                        val normalizedLatest = latestVersion.removePrefix("v").removePrefix("V")
                        if (normalizedExisting != normalizedLatest) {
                            val deleted = file.delete()
                            Logger.d { "Deleted mismatched existing file ($normalizedExisting != $normalizedLatest): $deleted" }
                        }
                    } catch (e: Exception) {
                        Logger.w { "Failed to extract APK info for existing file: ${e.message}" }
                        val deleted = file.delete()
                        Logger.d { "Deleted unextractable existing file: $deleted" }
                    }
                }

                updateAppState(app.packageName, UpdateState.Downloading)

                downloader.download(latestAssetUrl, latestAssetName).collect { progress ->
                    updateAppProgress(app.packageName, progress.percent)
                }

                val filePath = downloader.getDownloadedFilePath(latestAssetName)
                    ?: throw IllegalStateException("Downloaded file not found")

                val apkInfo = installer.getApkInfoExtractor().extractPackageInfo(filePath)
                    ?: throw IllegalStateException("Failed to extract APK info")

                updateAppInDatabase(
                    app = app,
                    newVersion = latestVersion,
                    assetName = latestAssetName,
                    assetUrl = latestAssetUrl,
                    newVersionName = apkInfo.versionName,
                    newVersionCode = apkInfo.versionCode
                )

                updateAppState(app.packageName, UpdateState.Installing)
                installer.install(filePath, ext)

                updateAppState(app.packageName, UpdateState.Success)
                updateOutcomes[app.packageName] = true

                // During a batch the caller reads this outcome after join(); resetting to Idle
                // here would race with that read, so only auto-reset single updates.
                if (!isBatchUpdating) {
                    delay(2000)
                    updateAppState(app.packageName, UpdateState.Idle)
                    updateOutcomes.remove(app.packageName)
                }

                Logger.d { "Successfully updated ${app.appName} to ${latestVersion}" }

            } catch (e: CancellationException) {
                Logger.d { "Update cancelled for ${app.packageName}" }
                updateOutcomes[app.packageName] = false
                withContext(NonCancellable) {
                    cleanupUpdate(app.packageName, app.latestAssetName)
                }
                updateAppState(app.packageName, UpdateState.Idle)
                throw e
            } catch (e: Exception) {
                Logger.e { "Update failed for ${app.packageName}: ${e.message}" }
                e.printStackTrace()
                updateOutcomes[app.packageName] = false
                cleanupUpdate(app.packageName, app.latestAssetName)
                updateAppState(
                    app.packageName,
                    UpdateState.Error(ErrorMapper.message(e, stringProvider))
                )
                if (!isBatchUpdating) {
                    _events.send(
                        AppsEvent.ShowError(
                            stringProvider.getString(
                                R.string.failed_to_update,
                                app.appName, ErrorMapper.message(e, stringProvider)
                            )
                        )
                    )
                }
            } finally {
                activeUpdates.remove(app.packageName)
            }
        }

        activeUpdates[app.packageName] = job
    }

    private fun updateAllApps() {
        if (_state.value.isUpdatingAll) {
            Logger.w { "Update all already in progress" }
            return
        }

        updateAllJob = viewModelScope.launch {
            try {
                isBatchUpdating = true
                _state.update { it.copy(isUpdatingAll = true) }

                val appsToUpdate = _state.value.apps.filter {
                    it.installedApp.isUpdateAvailable &&
                            it.updateState !is UpdateState.Success
                }

                if (appsToUpdate.isEmpty()) {
                    _events.send(AppsEvent.ShowError(stringProvider.getString(R.string.no_updates_available)))
                    return@launch
                }

                Logger.d { "Starting update all for ${appsToUpdate.size} apps" }

                var succeeded = 0
                var failed = 0

                appsToUpdate.forEachIndexed { index, appItem ->
                    if (!isActive) {
                        Logger.d { "Update all cancelled" }
                        return@launch
                    }

                    _state.update {
                        it.copy(
                            updateAllProgress = UpdateAllProgress(
                                current = index + 1,
                                total = appsToUpdate.size,
                                currentAppName = appItem.installedApp.appName,
                                succeeded = succeeded,
                                failed = failed
                            )
                        )
                    }

                    Logger.d { "Updating ${index + 1}/${appsToUpdate.size}: ${appItem.installedApp.appName}" }

                    updateSingleApp(appItem.installedApp)
                    activeUpdates[appItem.installedApp.packageName]?.join()

                    // Read the recorded outcome rather than the UI state: the update job may
                    // already have reset itself to Idle (or still be showing Success).
                    when (updateOutcomes.remove(appItem.installedApp.packageName)) {
                        true -> succeeded++
                        false -> failed++
                        null -> Logger.w {
                            "No update outcome recorded for ${appItem.installedApp.packageName}"
                        }
                    }

                    _state.update {
                        it.copy(
                            updateAllProgress = UpdateAllProgress(
                                current = index + 1,
                                total = appsToUpdate.size,
                                currentAppName = appItem.installedApp.appName,
                                succeeded = succeeded,
                                failed = failed
                            )
                        )
                    }

                    delay(1000)
                }

                Logger.d { "Update all completed: $succeeded succeeded, $failed failed" }
                if (failed == 0) {
                    _events.send(AppsEvent.ShowSuccess(stringProvider.getString(R.string.all_apps_updated_successfully)))
                } else {
                    _events.send(AppsEvent.ShowError(stringProvider.getString(R.string.update_all_failed, "$succeeded updated, $failed failed")))
                }

            } catch (e: CancellationException) {
                Logger.d { "Update all cancelled" }
                throw e
            } catch (e: Exception) {
                Logger.e { "Update all failed: ${e.message}" }
                _events.send(AppsEvent.ShowError(ErrorMapper.message(e, stringProvider)))
            } finally {
                isBatchUpdating = false
                updateOutcomes.clear()
                _state.update { currentState ->
                    currentState.copy(
                        // Success states are only kept alive during the batch so the loop can
                        // observe them; reset afterwards so a future update is not filtered out.
                        apps = currentState.apps.map { item ->
                            if (item.updateState is UpdateState.Success) {
                                item.copy(updateState = UpdateState.Idle)
                            } else {
                                item
                            }
                        },
                        isUpdatingAll = false,
                        updateAllProgress = null
                    )
                }
                updateAllJob = null
            }
        }
    }

    private fun cancelUpdate(packageName: String) {
        activeUpdates[packageName]?.cancel()
        activeUpdates.remove(packageName)

        val app = _state.value.apps.find { it.installedApp.packageName == packageName }
        app?.installedApp?.latestAssetName?.let { assetName ->
            viewModelScope.launch {
                cleanupUpdate(packageName, assetName)
            }
        }

        updateAppState(packageName, UpdateState.Idle)
    }

    private fun cancelAllUpdates() {
        updateAllJob?.cancel()
        updateAllJob = null

        activeUpdates.values.forEach { it.cancel() }
        activeUpdates.clear()

        viewModelScope.launch {
            _state.value.apps.forEach { appItem ->
                if (appItem.updateState != UpdateState.Idle &&
                    appItem.updateState != UpdateState.Success
                ) {

                    appItem.installedApp.latestAssetName?.let { assetName ->
                        cleanupUpdate(appItem.installedApp.packageName, assetName)
                    }
                    updateAppState(appItem.installedApp.packageName, UpdateState.Idle)
                }
            }
        }

        _state.update {
            it.copy(
                isUpdatingAll = false,
                updateAllProgress = null
            )
        }
    }

    private fun updateAppState(packageName: String, state: UpdateState) {
        _state.update { currentState ->
            currentState.copy(
                apps = currentState.apps.map { appItem ->
                    if (appItem.installedApp.packageName == packageName) {
                        appItem.copy(
                            updateState = state,
                            downloadProgress = if (state is UpdateState.Downloading)
                                appItem.downloadProgress else null,
                            error = if (state is UpdateState.Error) state.message else null
                        )
                    } else {
                        appItem
                    }
                }
            )
        }
    }

    private fun updateAppProgress(packageName: String, progress: Int?) {
        _state.update { currentState ->
            currentState.copy(
                apps = currentState.apps.map { appItem ->
                    if (appItem.installedApp.packageName == packageName) {
                        appItem.copy(downloadProgress = progress)
                    } else {
                        appItem
                    }
                }
            )
        }
    }

    private suspend fun updateAppInDatabase(
        app: InstalledApp,
        newVersion: String,
        assetName: String,
        assetUrl: String,
        newVersionName: String,
        newVersionCode: Long
    ) {
        try {
            installedAppsRepository.updateAppVersion(
                packageName = app.packageName,
                newTag = newVersion,
                newAssetName = assetName,
                newAssetUrl = assetUrl,
                newVersionName = newVersionName,
                newVersionCode = newVersionCode
            )

            installedAppsRepository.updatePendingStatus(app.packageName, true)

            Logger.d { "Updated database for ${app.packageName} to tag $newVersion, versionName $newVersionName" }
        } catch (e: Exception) {
            Logger.e { "Failed to update database: ${e.message}" }
        }
    }

    private suspend fun cleanupUpdate(packageName: String, assetName: String?) {
        try {
            if (assetName != null) {
                val deleted = downloader.cancelDownload(assetName)
                Logger.d { "Cleanup for $packageName - file deleted: $deleted" }
            }
        } catch (e: Exception) {
            Logger.w { "Cleanup failed for $packageName: ${e.message}" }
        }
    }

    override fun onCleared() {
        super.onCleared()

        updateAllJob?.cancel()
        activeUpdates.values.forEach { it.cancel() }

        // viewModelScope is already cancelled here, so cleanup has to run on its own scope.
        val pendingAssetNames = _state.value.apps
            .filter { it.updateState != UpdateState.Idle && it.updateState != UpdateState.Success }
            .mapNotNull { it.installedApp.latestAssetName }

        if (pendingAssetNames.isNotEmpty()) {
            cleanupScope.launch {
                pendingAssetNames.forEach { assetName ->
                    runCatching { downloader.cancelDownload(assetName) }
                }
            }
        }
    }
}




