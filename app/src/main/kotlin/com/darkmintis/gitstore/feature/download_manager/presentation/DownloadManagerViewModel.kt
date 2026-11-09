package com.darkmintis.gitstore.feature.download_manager.presentation

import android.app.DownloadManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.darkmintis.gitstore.R
import com.darkmintis.gitstore.core.data.local.db.entities.InstallSource
import com.darkmintis.gitstore.core.data.local.db.entities.InstalledApp
import com.darkmintis.gitstore.core.data.services.DownloadFileStatus
import com.darkmintis.gitstore.core.data.services.Downloader
import com.darkmintis.gitstore.core.data.services.FileLocationsProvider
import com.darkmintis.gitstore.core.data.services.Installer
import com.darkmintis.gitstore.core.domain.model.ApkPackageInfo
import com.darkmintis.gitstore.core.domain.repository.InstalledAppsRepository
import com.darkmintis.gitstore.core.presentation.utils.ErrorMapper
import com.darkmintis.gitstore.core.presentation.utils.StringProvider
import com.darkmintis.gitstore.feature.details.domain.repository.DetailsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.io.File

class DownloadManagerViewModel(
    private val context: Context,
    private val downloader: Downloader,
    private val installer: Installer,
    private val files: FileLocationsProvider,
    private val stringProvider: StringProvider,
    private val installedAppsRepository: InstalledAppsRepository,
    private val detailsRepository: DetailsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DownloadManagerState())
    val state = _state
        .onStart {
            refreshDownloads()
            pollWhileDownloading()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = DownloadManagerState()
        )

    fun refreshDownloads() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val items = loadDownloadItems()
            _state.update { it.copy(downloads = items, isLoading = false) }
        }
    }

    private fun pollWhileDownloading() {
        viewModelScope.launch {
            while (isActive) {
                val items = loadDownloadItems()
                _state.update {
                    it.copy(
                        downloads = items,
                        isLoading = false
                    )
                }
                val hasActive = items.any { it.status == DownloadStatus.DOWNLOADING }
                if (!hasActive) delay(2_000L) else delay(500L)
            }
        }
    }

    private suspend fun loadDownloadItems(): List<DownloadItem> = withContext(Dispatchers.IO) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val activeIds = downloader.getActiveDownloadIds()
        val dir = File(files.appDownloadsDir())
        if (!dir.exists()) return@withContext emptyList()

        val fromDisk = dir.listFiles()
            .orEmpty()
            .filter {
                it.isFile && !it.name.endsWith(".repoid") &&
                    !it.name.endsWith(".meta") && !it.name.endsWith(".complete")
            }
            .sortedByDescending { it.lastModified() }
            .map { file ->
                val entry = activeIds.entries.find { (name, _) ->
                    file.name == name || file.name.contains(name.removeSuffix(".apk"))
                }
                val repoId = downloader.getRepoIdForFile(file.name)
                val displayName = downloader.getDisplayNameForFile(file.name)
                val avatarUrl = avatarFor(file.name, repoId, displayName)

                if (entry != null) {
                    activeItemFromDm(
                        downloadManager,
                        entry.key,
                        entry.value,
                        file,
                        repoId,
                        displayName,
                        avatarUrl,
                    )
                } else {
                    itemFromDiskState(
                        file = file,
                        repoId = repoId,
                        displayName = displayName,
                        avatarUrl = avatarUrl,
                    )
                }
            }

        val diskNames = fromDisk.map { it.fileName }.toSet()
        val inFlightOnly = activeIds
            .filterKeys { it !in diskNames }
            .map { (name, id) ->
                activeItemFromDm(
                    downloadManager,
                    name,
                    id,
                    file = File(dir, name),
                    repoId = downloader.getRepoIdForFile(name),
                    displayName = downloader.getDisplayNameForFile(name),
                    avatarUrl = avatarFor(name, null, downloader.getDisplayNameForFile(name)),
                )
            }

        (inFlightOnly + fromDisk).distinctBy { it.fileName }
    }

    private suspend fun avatarFor(
        fileName: String,
        repoId: Long?,
        displayName: String?,
    ): String? {
        val cached = downloader.getAvatarUrlForFile(fileName)
        if (!cached.isNullOrBlank()) return cached

        // Older downloads have no avatar in their sidecar; fall back to the installed-app row.
        val id = repoId ?: downloader.getRepoIdForFile(fileName)
        if (id != null) {
            val installed = installedAppsRepository.getAppByRepoId(id)?.repoOwnerAvatarUrl
            if (!installed.isNullOrBlank()) return installed
        }

        // Details downloads store "owner/repo" as the display name, so the avatar can be
        // derived without another API call.
        val owner = displayName
            ?.takeIf { it.contains('/') }
            ?.substringBefore('/')
            ?.takeIf { it.isNotBlank() }
        if (owner != null) return "https://github.com/$owner.png?size=100"

        return null
    }

    private suspend fun itemFromDiskState(
        file: File,
        repoId: Long?,
        displayName: String?,
        avatarUrl: String?,
    ): DownloadItem {
        val state = downloader.getFileState(file.name)
        return when (state?.status) {
            DownloadFileStatus.IN_PROGRESS -> DownloadItem(
                fileName = file.name,
                filePath = file.absolutePath,
                fileSize = file.length(),
                status = DownloadStatus.DOWNLOADING,
                progress = state.progress,
                repoId = repoId,
                displayName = displayName,
                avatarUrl = avatarUrl,
            )

            DownloadFileStatus.FAILED -> DownloadItem(
                fileName = file.name,
                filePath = file.absolutePath,
                fileSize = file.length(),
                status = DownloadStatus.FAILED,
                repoId = repoId,
                displayName = displayName,
                avatarUrl = avatarUrl,
            )

            DownloadFileStatus.COMPLETED -> DownloadItem(
                fileName = file.name,
                filePath = file.absolutePath,
                fileSize = file.length(),
                status = DownloadStatus.COMPLETED,
                progress = 100,
                repoId = repoId,
                displayName = displayName,
                avatarUrl = avatarUrl,
            )

            null -> {
                // No DownloadManager record and no completion marker (legacy or orphan):
                // only offer it for install when it is a readable APK.
                val valid = installer.getApkInfoExtractor()
                    .extractPackageInfo(file.absolutePath) != null
                DownloadItem(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    fileSize = file.length(),
                    status = if (valid) DownloadStatus.COMPLETED else DownloadStatus.FAILED,
                    progress = if (valid) 100 else null,
                    repoId = repoId,
                    displayName = displayName,
                    avatarUrl = avatarUrl,
                )
            }
        }
    }

    private fun activeItemFromDm(
        downloadManager: DownloadManager,
        fileName: String,
        downloadId: Long,
        file: File,
        repoId: Long?,
        displayName: String?,
        avatarUrl: String?,
    ): DownloadItem {
        val cursor = downloadManager.query(
            DownloadManager.Query().setFilterById(downloadId)
        )
        var progress: Int? = null
        var status = DownloadStatus.DOWNLOADING
        try {
            if (cursor.moveToFirst()) {
                val bytes = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                )
                val total = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                )
                progress = if (total > 0) ((bytes * 100L) / total).toInt() else null

                val sysStatus = cursor.getInt(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                )
                status = when (sysStatus) {
                    DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.COMPLETED
                    DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
                    else -> DownloadStatus.DOWNLOADING
                }
            }
        } finally {
            cursor.close()
        }

        return DownloadItem(
            fileName = fileName,
            filePath = file.absolutePath,
            fileSize = if (file.exists()) file.length() else 0L,
            status = status,
            progress = progress,
            repoId = repoId,
            displayName = displayName,
            avatarUrl = avatarUrl,
        )
    }

    fun installApk(filePath: String) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(errorMessage = null) }
                val file = File(filePath)
                val ext = filePath.substringAfterLast('.', "").lowercase()

                if (downloader.getFileState(file.name)?.status == DownloadFileStatus.IN_PROGRESS) {
                    _state.update {
                        it.copy(errorMessage = stringProvider.getString(R.string.error_download_failed))
                    }
                    return@launch
                }

                val apkInfo = installer.getApkInfoExtractor().extractPackageInfo(filePath)
                if (apkInfo == null) {
                    // Partial or corrupt file: remove it so the row cannot keep offering install.
                    downloader.cancelDownload(file.name)
                    _state.update {
                        it.copy(errorMessage = stringProvider.getString(R.string.error_download_failed))
                    }
                    refreshDownloads()
                    return@launch
                }

                // Persist before the system installer so Details can show Reinstall immediately.
                persistInstalledApp(file, apkInfo)

                installer.install(filePath, ext)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(errorMessage = ErrorMapper.message(e, stringProvider))
                }
            }
        }
    }

    private suspend fun persistInstalledApp(file: File, apkInfo: ApkPackageInfo) {
        val repoId = downloader.getRepoIdForFile(file.name) ?: return

        val existing = installedAppsRepository.getAppByPackage(apkInfo.packageName)
        if (existing != null) {
            installedAppsRepository.updatePendingStatus(apkInfo.packageName, true)
            return
        }

        val repo = runCatching { detailsRepository.getRepositoryById(repoId) }.getOrNull()
        val displayName = downloader.getDisplayNameForFile(file.name)
        val now = Clock.System.now().toEpochMilliseconds()
        val repoName = repo?.name
            ?: displayName?.substringAfterLast('/')
            ?: file.nameWithoutExtension
        val repoOwner = repo?.owner?.login
            ?: displayName?.substringBefore('/')?.takeIf { displayName.contains('/') }
            ?: ""

        val installedApp = InstalledApp(
            packageName = apkInfo.packageName,
            repoId = repoId,
            repoName = repoName,
            repoOwner = repoOwner,
            repoOwnerAvatarUrl = repo?.owner?.avatarUrl.orEmpty(),
            repoDescription = repo?.description,
            primaryLanguage = repo?.language,
            repoUrl = repo?.htmlUrl.orEmpty(),
            installedVersion = apkInfo.versionName,
            installedAssetName = file.name,
            installedAssetUrl = null,
            latestVersion = apkInfo.versionName,
            latestAssetName = file.name,
            latestAssetUrl = null,
            latestAssetSize = file.length(),
            appName = apkInfo.appName.ifBlank { repoName },
            installSource = InstallSource.THIS_APP,
            installedAt = now,
            lastCheckedAt = now,
            lastUpdatedAt = now,
            isUpdateAvailable = false,
            updateCheckEnabled = true,
            releaseNotes = "",
            systemArchitecture = installer.detectSystemArchitecture().name,
            fileExtension = file.extension,
            isPendingInstall = true,
            installedVersionName = apkInfo.versionName,
            installedVersionCode = apkInfo.versionCode,
            latestVersionName = apkInfo.versionName,
            latestVersionCode = apkInfo.versionCode,
        )
        installedAppsRepository.saveInstalledApp(installedApp)
        Logger.d { "Saved installed app from Downloads: ${apkInfo.packageName} → repo $repoId" }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun deleteFile(filePath: String) {
        viewModelScope.launch {
            val file = File(filePath)
            downloader.cancelDownload(file.name)
            refreshDownloads()
        }
    }

    fun cancelDownload(fileName: String) {
        viewModelScope.launch {
            downloader.cancelDownload(fileName)
            refreshDownloads()
        }
    }
}
