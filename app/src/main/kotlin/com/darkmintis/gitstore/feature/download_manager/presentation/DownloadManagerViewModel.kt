package com.darkmintis.gitstore.feature.download_manager.presentation

import android.app.DownloadManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.darkmintis.gitstore.core.data.local.db.entities.InstallSource
import com.darkmintis.gitstore.core.data.local.db.entities.InstalledApp
import com.darkmintis.gitstore.core.data.services.Downloader
import com.darkmintis.gitstore.core.data.services.FileLocationsProvider
import com.darkmintis.gitstore.core.data.services.Installer
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
            .filter { it.isFile && !it.name.endsWith(".repoid") && !it.name.endsWith(".meta") }
            .sortedByDescending { it.lastModified() }
            .map { file ->
                val entry = activeIds.entries.find { (name, _) ->
                    file.name == name || file.name.contains(name.removeSuffix(".apk"))
                }
                val repoId = downloader.getRepoIdForFile(file.name)
                val displayName = downloader.getDisplayNameForFile(file.name)

                if (entry != null) {
                    activeItemFromDm(
                        downloadManager,
                        entry.key,
                        entry.value,
                        file,
                        repoId,
                        displayName
                    )
                } else {
                    DownloadItem(
                        fileName = file.name,
                        filePath = file.absolutePath,
                        fileSize = file.length(),
                        status = DownloadStatus.COMPLETED,
                        progress = 100,
                        repoId = repoId,
                        displayName = displayName,
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
                )
            }

        (inFlightOnly + fromDisk).distinctBy { it.fileName }
    }

    private fun activeItemFromDm(
        downloadManager: DownloadManager,
        fileName: String,
        downloadId: Long,
        file: File,
        repoId: Long?,
        displayName: String?,
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
        )
    }

    fun installApk(filePath: String) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(errorMessage = null) }
                val file = File(filePath)
                val ext = filePath.substringAfterLast('.', "").lowercase()

                // Persist before the system installer so Details can show Reinstall immediately.
                persistInstalledApp(file)

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

    private suspend fun persistInstalledApp(file: File) {
        val repoId = downloader.getRepoIdForFile(file.name) ?: return
        val apkInfo = installer.getApkInfoExtractor().extractPackageInfo(file.absolutePath)
        if (apkInfo == null) {
            Logger.w { "Could not extract APK info for ${file.name}" }
            return
        }

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
            withContext(Dispatchers.IO) {
                val file = File(filePath)
                file.delete()
                File(file.parentFile, "${file.name}.meta").delete()
                File(file.parentFile, "${file.name}.repoid").delete()
            }
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
