package com.darkmintis.gitstore.feature.details.presentation

import kotlinx.datetime.Clock as DateClock
import kotlinx.datetime.Instant as DateInstant

import com.darkmintis.gitstore.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import com.darkmintis.gitstore.core.data.services.PackageMonitor
import com.darkmintis.gitstore.core.data.local.db.entities.FavoriteRepo
import com.darkmintis.gitstore.core.data.local.db.entities.InstallSource
import com.darkmintis.gitstore.core.data.local.db.entities.InstalledApp
import com.darkmintis.gitstore.core.domain.repository.FavouritesRepository
import com.darkmintis.gitstore.core.domain.repository.InstalledAppsRepository
import com.darkmintis.gitstore.core.presentation.utils.BrowserHelper
import com.darkmintis.gitstore.core.presentation.utils.ErrorMapper
import com.darkmintis.gitstore.core.presentation.utils.StringProvider
import com.darkmintis.gitstore.core.data.services.Downloader
import com.darkmintis.gitstore.core.data.services.Installer
import com.darkmintis.gitstore.core.domain.repository.StarredRepository
import com.darkmintis.gitstore.core.domain.use_cases.SyncInstalledAppsUseCase
import com.darkmintis.gitstore.feature.details.domain.repository.DetailsRepository
import com.darkmintis.gitstore.feature.details.presentation.model.LogResult

class DetailsViewModel(
    private val repositoryId: Long,
    private val detailsRepository: DetailsRepository,
    private val downloader: Downloader,
    private val installer: Installer,
    private val helper: BrowserHelper,
    private val installedAppsRepository: InstalledAppsRepository,
    private val favouritesRepository: FavouritesRepository,
    private val starredRepository: StarredRepository,
    private val stringProvider: StringProvider,
    private val packageMonitor: PackageMonitor,
    private val syncInstalledAppsUseCase: SyncInstalledAppsUseCase
) : ViewModel() {

    private var hasLoadedInitialData = false
    private var currentDownloadJob: Job? = null
    private var currentAssetName: String? = null

    /**
     * Outlives the ViewModel so download cleanup can still run after [onCleared], when
     * viewModelScope is already cancelled.
     */
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(DetailsState())
    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                loadInitial()
                hasLoadedInitialData = true
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DetailsState()
        )

    private val _events = Channel<DetailsEvent>()
    val events = _events.receiveAsFlow()

        private fun loadInitial() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null, warningMessage = null)

                val syncResult = syncInstalledAppsUseCase()
                if (syncResult.isFailure) {
                    Logger.w { "Sync had issues but continuing: ${syncResult.exceptionOrNull()?.message}" }
                }

                val repo = detailsRepository.getRepositoryById(repositoryId)
                val isFavoriteDeferred = async {
                    try {
                        favouritesRepository.isFavoriteSync(repo.id)
                    } catch (t: CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        Logger.e { "Failed to load if repo is favourite: ${t.localizedMessage}" }
                        false
                    }
                }
                val isFavorite = isFavoriteDeferred.await()
                val isStarredDeferred = async {
                    try {
                        starredRepository.isStarred(repo.id)
                    } catch (t: CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        Logger.e { "Failed to load if repo is starred: ${t.localizedMessage}" }
                        false
                    }
                }
                val isStarred = isStarredDeferred.await()

                val owner = repo.owner.login
                val name = repo.name

                _state.value = _state.value.copy(
                    repository = repo,
                    isFavourite = isFavorite,
                    isStarred = isStarred,
                )

                var releaseLoadFailed = false
                val latestReleaseDeferred = async {
                    try {
                        detailsRepository.getLatestPublishedRelease(
                            owner = owner,
                            repo = name,
                            defaultBranch = repo.defaultBranch
                        )
                    } catch (t: CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        Logger.w { "Failed to load latest release: ${t.message}" }
                        releaseLoadFailed = true
                        null
                    }
                }

                val statsDeferred = async {
                    try {
                        detailsRepository.getRepoStats(owner, name)
                    } catch (t: CancellationException) {
                        throw t
                    } catch (_: Throwable) {
                        null
                    }
                }

                val readmeDeferred = async {
                    try {
                        detailsRepository.getReadme(
                            owner = owner,
                            repo = name,
                            defaultBranch = repo.defaultBranch
                        )
                    } catch (t: CancellationException) {
                        throw t
                    } catch (_: Throwable) {
                        null
                    }
                }

                val userProfileDeferred = async {
                    try {
                        detailsRepository.getUserProfile(owner)
                    } catch (t: CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        Logger.w { "Failed to load user profile: ${t.message}" }
                        null
                    }
                }

                val installedAppDeferred = async {
                    try {
                        val dbApp = installedAppsRepository.getAppByRepoId(repo.id)

                        if (dbApp != null) {
                            if (dbApp.isPendingInstall &&
                                packageMonitor.isPackageInstalled(dbApp.packageName)
                            ) {
                                installedAppsRepository.updatePendingStatus(
                                    dbApp.packageName,
                                    false
                                )
                                installedAppsRepository.getAppByPackage(dbApp.packageName)
                            } else {
                                dbApp
                            }
                        } else {
                            null
                        }
                    } catch (t: Throwable) {
                        Logger.e { "Failed to load installed app: ${t.message}" }
                        null
                    }
                }

                val isObtainiumEnabled = true
                val isAppManagerEnabled = true

                val latestRelease = latestReleaseDeferred.await()
                val stats = statsDeferred.await()
                val readme = readmeDeferred.await()
                val userProfile = userProfileDeferred.await()
                val installedApp = installedAppDeferred.await()

                val installable = latestRelease?.assets?.filter { asset ->
                    installer.isAssetInstallable(asset.name)
                }.orEmpty()

                val primary = installer.choosePrimaryAsset(installable)

                val isObtainiumAvailable = installer.isObtainiumInstalled()
                val isAppManagerAvailable = installer.isAppManagerInstalled()

                Logger.d { "Loaded repo: ${repo.name}, installedApp: ${installedApp?.packageName}" }

                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = null,
                    warningMessage = if (releaseLoadFailed) {
                        stringProvider.getString(R.string.details_warning_release)
                    } else null,
                    repository = repo,
                    latestRelease = latestRelease,
                    stats = stats,
                    readmeMarkdown = readme?.first,
                    readmeLanguage = readme?.second,
                    installableAssets = installable,
                    primaryAsset = primary,
                    userProfile = userProfile,
                    systemArchitecture = installer.detectSystemArchitecture(),
                    isObtainiumAvailable = isObtainiumAvailable,
                    isObtainiumEnabled = isObtainiumEnabled,
                    isAppManagerAvailable = isAppManagerAvailable,
                    isAppManagerEnabled = isAppManagerEnabled,
                    installedApp = installedApp,
                )
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                Logger.e { "Details load failed: ${t.message}" }
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = ErrorMapper.message(t, stringProvider)
                )
            }
        }
    }

        fun onAction(action: DetailsAction) {
        when (action) {
            DetailsAction.Retry -> {
                hasLoadedInitialData = false
                loadInitial()
            }

            DetailsAction.InstallPrimary -> {
                val primary = _state.value.primaryAsset
                val release = _state.value.latestRelease
                if (primary != null && release != null) {
                    installAsset(
                        downloadUrl = primary.downloadUrl,
                        assetName = primary.name,
                        sizeBytes = primary.size,
                        releaseTag = release.tagName
                    )
                }
            }

            is DetailsAction.DownloadAsset -> {
                val release = _state.value.latestRelease
                downloadAsset(
                    downloadUrl = action.downloadUrl,
                    assetName = action.assetName,
                    sizeBytes = action.sizeBytes,
                    releaseTag = release?.tagName ?: ""
                )
            }

            DetailsAction.CancelCurrentDownload -> {
                currentDownloadJob?.cancel()
                currentDownloadJob = null

                val assetName = currentAssetName
                if (assetName != null) {
                    viewModelScope.launch {
                        try {
                            val deleted = downloader.cancelDownload(assetName)
                            Logger.d { "Cancel download - file deleted: $deleted" }

                            appendLog(
                                assetName = assetName,
                                size = 0L,
                                tag = _state.value.latestRelease?.tagName ?: "",
                                result = LogResult.Cancelled
                            )
                        } catch (t: Throwable) {
                            Logger.e { "Failed to cancel download: ${t.message}" }
                        }
                    }
                }

                currentAssetName = null
                _state.value = _state.value.copy(
                    isDownloading = false,
                    downloadProgressPercent = null,
                    downloadStage = DownloadStage.IDLE
                )
            }

            DetailsAction.OnToggleFavorite -> {
                viewModelScope.launch {
                    try {
                        val repo = _state.value.repository ?: return@launch
                        val latestRelease = _state.value.latestRelease

                        val favoriteRepo = FavoriteRepo(
                            repoId = repo.id,
                            repoName = repo.name,
                            repoOwner = repo.owner.login,
                            repoOwnerAvatarUrl = repo.owner.avatarUrl,
                            repoDescription = repo.description,
                            primaryLanguage = repo.language,
                            repoUrl = repo.htmlUrl,
                            latestVersion = latestRelease?.tagName,
                            latestReleaseUrl = latestRelease?.htmlUrl,
                            addedAt = DateClock.System.now().toEpochMilliseconds(),
                            lastSyncedAt = DateClock.System.now().toEpochMilliseconds()
                        )

                        favouritesRepository.toggleFavorite(favoriteRepo)

                        val newFavoriteState = favouritesRepository.isFavoriteSync(repo.id)
                        _state.value = _state.value.copy(isFavourite = newFavoriteState)

                        _events.send(
                            element = DetailsEvent.OnMessage(
                                message = stringProvider.getString(
                                    if (newFavoriteState) {
                                        R.string.added_to_favourites
                                    } else {
                                        R.string.removed_from_favourites
                                    }
                                )
                            )
                        )

                    } catch (t: CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        Logger.e { "Failed to toggle favorite: ${t.message}" }
                        viewModelScope.launch {
                            _events.send(
                                DetailsEvent.OnMessage(
                                    message = stringProvider.getString(R.string.error_favourite_toggle)
                                )
                            )
                        }
                    }
                }
            }

            DetailsAction.CheckForUpdates -> {
                viewModelScope.launch {
                    try {
                        syncInstalledAppsUseCase()

                        val installedApp = _state.value.installedApp ?: return@launch
                        val hasUpdate =
                            installedAppsRepository.checkForUpdates(installedApp.packageName)

                        if (hasUpdate) {
                            val updatedApp =
                                installedAppsRepository.getAppByPackage(installedApp.packageName)
                            _state.value = _state.value.copy(installedApp = updatedApp)
                        }
                    } catch (t: CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        Logger.e { "Failed to check for updates: ${t.message}" }
                        _events.send(
                            DetailsEvent.OnMessage(
                                message = stringProvider.getString(R.string.error_check_updates)
                            )
                        )
                    }
                }
            }

            DetailsAction.UpdateApp -> {
                val installedApp = _state.value.installedApp
                val latestRelease = _state.value.latestRelease

                if (installedApp != null && latestRelease != null && installedApp.isUpdateAvailable) {
                    val latestAsset = _state.value.installableAssets.firstOrNull {
                        it.name == installedApp.latestAssetName
                    } ?: _state.value.primaryAsset

                    if (latestAsset != null) {
                        installAsset(
                            downloadUrl = latestAsset.downloadUrl,
                            assetName = latestAsset.name,
                            sizeBytes = latestAsset.size,
                            releaseTag = latestRelease.tagName,
                            isUpdate = true
                        )
                    }
                }
            }

            DetailsAction.OpenRepoInBrowser -> {
                _state.value.repository?.htmlUrl?.let {
                    helper.openUrl(url = it)
                }
            }

            DetailsAction.OpenAuthorInBrowser -> {
                _state.value.userProfile?.htmlUrl?.let {
                    helper.openUrl(url = it)
                }
            }

            DetailsAction.OpenInObtainium -> {
                val repo = _state.value.repository
                repo?.owner?.login?.let {
                    installer.openInObtainium(
                        repoOwner = it,
                        repoName = repo.name,
                        onOpenInstaller = {
                            viewModelScope.launch {
                                _events.send(
                                    DetailsEvent.OnOpenRepositoryInApp(OBTAINIUM_REPO_ID)
                                )
                            }
                        }
                    )
                }
                _state.update {
                    it.copy(isInstallDropdownExpanded = false)
                }
            }

            DetailsAction.OpenInAppManager -> {
                viewModelScope.launch {
                    try {
                        val primary = _state.value.primaryAsset
                        val release = _state.value.latestRelease

                        if (primary != null && release != null) {
                            currentAssetName = primary.name

                            appendLog(
                                assetName = primary.name,
                                size = primary.size,
                                tag = release.tagName,
                                result = LogResult.PreparingForAppManager
                            )

                            _state.value = _state.value.copy(
                                downloadError = null,
                                installError = null,
                                downloadProgressPercent = null,
                                downloadStage = DownloadStage.DOWNLOADING
                            )

                            downloader.download(primary.downloadUrl, primary.name).collect { p ->
                                _state.value =
                                    _state.value.copy(downloadProgressPercent = p.percent)
                                if (p.percent == 100) {
                                    _state.value =
                                        _state.value.copy(downloadStage = DownloadStage.VERIFYING)
                                }
                            }

                            val filePath = downloader.getDownloadedFilePath(primary.name)
                                ?: throw IllegalStateException("Downloaded file not found")

                            appendLog(
                                assetName = primary.name,
                                size = primary.size,
                                tag = release.tagName,
                                result = LogResult.Downloaded
                            )

                            _state.value = _state.value.copy(downloadStage = DownloadStage.IDLE)
                            currentAssetName = null

                            installer.openInAppManager(
                                filePath = filePath,
                                onOpenInstaller = {
                                    viewModelScope.launch {
                                        _events.send(
                                            DetailsEvent.OnOpenRepositoryInApp(APP_MANAGER_REPO_ID)
                                        )
                                    }
                                }
                            )

                            appendLog(
                                assetName = primary.name,
                                size = primary.size,
                                tag = release.tagName,
                                result = LogResult.OpenedInAppManager
                            )
                        }
                    } catch (t: CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        Logger.e { "Failed to open in AppManager: ${t.message}" }
                        _state.value = _state.value.copy(
                            downloadStage = DownloadStage.IDLE,
                            installError = ErrorMapper.message(t, stringProvider)
                        )
                        currentAssetName = null

                        _state.value.primaryAsset?.let { asset ->
                            _state.value.latestRelease?.let { release ->
                                appendLog(
                                    assetName = asset.name,
                                    size = asset.size,
                                    tag = release.tagName,
                                    result = LogResult.Error(t.message)
                                )
                            }
                        }
                    }
                }
                _state.update {
                    it.copy(isInstallDropdownExpanded = false)
                }
            }

            DetailsAction.OnToggleInstallDropdown -> {
                _state.update {
                    it.copy(isInstallDropdownExpanded = !it.isInstallDropdownExpanded)
                }
            }

            DetailsAction.OnNavigateBackClick -> {
                // Handled in composable
            }

            is DetailsAction.OpenDeveloperProfile -> {
                // Handled in composable
            }
        }
    }

    private fun installAsset(
        downloadUrl: String,
        assetName: String,
        sizeBytes: Long,
        releaseTag: String,
        isUpdate: Boolean = false
    ) {
        currentDownloadJob?.cancel()
        currentDownloadJob = viewModelScope.launch {
            try {
                currentAssetName = assetName

                appendLog(
                    assetName = assetName,
                    size = sizeBytes,
                    tag = releaseTag,
                    result = if (isUpdate) {
                        LogResult.UpdateStarted
                    } else LogResult.DownloadStarted
                )
                _state.value = _state.value.copy(
                    downloadError = null,
                    installError = null,
                    downloadProgressPercent = null
                )

                installer.ensurePermissionsOrThrow(
                    extOrMime = assetName.substringAfterLast('.', "").lowercase()
                )

                _state.value = _state.value.copy(downloadStage = DownloadStage.DOWNLOADING)
                downloader.download(downloadUrl, assetName).collect { p ->
                    _state.value = _state.value.copy(downloadProgressPercent = p.percent)
                    if (p.percent == 100) {
                        _state.value = _state.value.copy(downloadStage = DownloadStage.VERIFYING)
                    }
                }

                val filePath = downloader.getDownloadedFilePath(assetName)
                    ?: throw IllegalStateException("Downloaded file not found")

                appendLog(
                    assetName = assetName,
                    size = sizeBytes,
                    tag = releaseTag,
                    result = LogResult.Downloaded
                )

                _state.value = _state.value.copy(downloadStage = DownloadStage.INSTALLING)
                val ext = assetName.substringAfterLast('.', "").lowercase()

                if (!installer.isSupported(ext)) {
                    throw IllegalStateException("Asset type .$ext not supported")
                }

                saveInstalledAppToDatabase(
                    assetName = assetName,
                    assetUrl = downloadUrl,
                    assetSize = sizeBytes,
                    releaseTag = releaseTag,
                    isUpdate = isUpdate,
                    filePath = filePath
                )

                installer.install(filePath, ext)

                _state.value = _state.value.copy(downloadStage = DownloadStage.IDLE)
                currentAssetName = null
                appendLog(
                    assetName = assetName,
                    size = sizeBytes,
                    tag = releaseTag,
                    result = if (isUpdate) {
                        LogResult.Updated
                    } else LogResult.Installed
                )

            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                Logger.e { "Install failed: ${t.message}" }
                t.printStackTrace()
                _state.value = _state.value.copy(
                    downloadStage = DownloadStage.IDLE,
                    installError = ErrorMapper.message(t, stringProvider, R.string.error_install_failed)
                )
                currentAssetName = null
                appendLog(
                    assetName = assetName,
                    size = sizeBytes,
                    tag = releaseTag,
                    result = LogResult.Error(t.message)
                )
            }
        }
    }

        private suspend fun saveInstalledAppToDatabase(
        assetName: String,
        assetUrl: String,
        assetSize: Long,
        releaseTag: String,
        isUpdate: Boolean,
        filePath: String
    ) {
        try {
            val repo = _state.value.repository ?: return

            var packageName: String
            var appName = repo.name
            var versionName: String? = null
            var versionCode = 0L

            if (assetName.lowercase().endsWith(".apk")) {
                val apkInfo = installer.getApkInfoExtractor().extractPackageInfo(filePath)
                if (apkInfo != null) {
                    packageName = apkInfo.packageName
                    appName = apkInfo.appName
                    versionName = apkInfo.versionName
                    versionCode = apkInfo.versionCode
                    Logger.d { "Extracted APK info - package: $packageName, name: $appName, versionName: $versionName, versionCode: $versionCode" }
                } else {
                    Logger.e { "Failed to extract APK info for $assetName" }
                    return
                }
            } else {
                packageName = "app.github.${repo.owner.login}.${repo.name}".lowercase()
            }

            if (isUpdate) {
                installedAppsRepository.updateAppVersion(
                    packageName = packageName,
                    newTag = releaseTag,
                    newAssetName = assetName,
                    newAssetUrl = assetUrl,
                    newVersionName = versionName ?: "unknown",
                    newVersionCode = versionCode
                )
            } else {
                val installedApp = InstalledApp(
                    packageName = packageName,
                    repoId = repo.id,
                    repoName = repo.name,
                    repoOwner = repo.owner.login,
                    repoOwnerAvatarUrl = repo.owner.avatarUrl,
                    repoDescription = repo.description,
                    primaryLanguage = repo.language,
                    repoUrl = repo.htmlUrl,
                    installedVersion = releaseTag,
                    installedAssetName = assetName,
                    installedAssetUrl = assetUrl,
                    latestVersion = releaseTag,
                    latestAssetName = assetName,
                    latestAssetUrl = assetUrl,
                    latestAssetSize = assetSize,
                    appName = appName,
                    installSource = InstallSource.THIS_APP,
                    installedAt = DateClock.System.now().toEpochMilliseconds(),
                    lastCheckedAt = DateClock.System.now().toEpochMilliseconds(),
                    lastUpdatedAt = DateClock.System.now().toEpochMilliseconds(),
                    isUpdateAvailable = false,
                    updateCheckEnabled = true,
                    releaseNotes = "",
                    systemArchitecture = installer.detectSystemArchitecture().name,
                    fileExtension = assetName.substringAfterLast('.', ""),
                    isPendingInstall = true,
                    installedVersionName = versionName,
                    installedVersionCode = versionCode,
                    latestVersionName = versionName,
                    latestVersionCode = versionCode
                )

                installedAppsRepository.saveInstalledApp(installedApp)
            }

            if (_state.value.isFavourite) {
                favouritesRepository.updateFavoriteInstallStatus(
                    repoId = repo.id,
                    installed = true,
                    packageName = packageName
                )
            }

            delay(500)
            val updatedApp = installedAppsRepository.getAppByPackage(packageName)
            _state.value = _state.value.copy(installedApp = updatedApp)

            Logger.d { "Successfully saved and reloaded app: ${updatedApp?.packageName}" }

        } catch (t: Throwable) {
            Logger.e { "Failed to save installed app to database: ${t.message}" }
            t.printStackTrace()
        }
    }

    private fun downloadAsset(
        downloadUrl: String,
        assetName: String,
        sizeBytes: Long,
        releaseTag: String
    ) {
        currentDownloadJob?.cancel()
        currentDownloadJob = viewModelScope.launch {
            try {
                currentAssetName = assetName

                appendLog(
                    assetName = assetName,
                    size = sizeBytes,
                    tag = releaseTag,
                    result = LogResult.DownloadStarted
                )
                _state.value = _state.value.copy(
                    isDownloading = true,
                    downloadError = null,
                    installError = null,
                    downloadProgressPercent = null
                )

                downloader.download(downloadUrl, assetName).collect { p ->
                    _state.value = _state.value.copy(downloadProgressPercent = p.percent)
                }

                _state.value = _state.value.copy(isDownloading = false)
                currentAssetName = null
                appendLog(
                    assetName = assetName,
                    size = sizeBytes,
                    tag = releaseTag,
                    result = LogResult.Downloaded
                )

            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isDownloading = false,
                    downloadError = ErrorMapper.message(t, stringProvider)
                )
                currentAssetName = null
                appendLog(
                    assetName = assetName,
                    size = sizeBytes,
                    tag = releaseTag,
                    result = LogResult.Error(t.message)
                )
            }
        }
    }

        private fun appendLog(
        assetName: String,
        size: Long,
        tag: String,
        result: LogResult
    ) {
        val now = DateClock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            .format(LocalDateTime.Format {
                year()
                char('-')
                monthNumber()
                char('-')
                dayOfMonth()
                char(' ')
                hour()
                char(':')
                minute()
                char(':')
                second()
            })
        val newItem = InstallLogItem(
            timeIso = now,
            assetName = assetName,
            assetSizeBytes = size,
            releaseTag = tag,
            result = result
        )
        _state.value = _state.value.copy(
            installLogs = listOf(newItem) + _state.value.installLogs
        )
    }

    override fun onCleared() {
        super.onCleared()
        currentDownloadJob?.cancel()

        currentAssetName?.let { assetName ->
            cleanupScope.launch {
                runCatching { downloader.cancelDownload(assetName) }
            }
        }
    }

    private companion object {
        const val OBTAINIUM_REPO_ID: Long = 523534328
        const val APP_MANAGER_REPO_ID: Long = 268006778
    }
}





