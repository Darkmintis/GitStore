package com.darkmintis.gitstore.feature.details.presentation

import com.darkmintis.gitstore.core.data.local.db.entities.InstalledApp
import com.darkmintis.gitstore.core.domain.model.Architecture
import com.darkmintis.gitstore.core.domain.model.GithubRepoSummary
import com.darkmintis.gitstore.core.domain.model.GithubRelease
import com.darkmintis.gitstore.core.domain.model.GithubAsset
import com.darkmintis.gitstore.core.domain.model.GithubUserProfile
import com.darkmintis.gitstore.feature.details.domain.model.RepoStats
import com.darkmintis.gitstore.feature.details.presentation.model.LogResult

data class InstallLogItem(
    val timeIso: String,
    val assetName: String,
    val assetSizeBytes: Long,
    val releaseTag: String,
    val result: LogResult
)

data class DetailsState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val warningMessage: String? = null,

    val repository: GithubRepoSummary? = null,
    val latestRelease: GithubRelease? = null,
    val installableAssets: List<GithubAsset> = emptyList(),
    val primaryAsset: GithubAsset? = null,
    val userProfile: GithubUserProfile? = null,

    val stats: RepoStats? = null,
    val readmeMarkdown: String? = null,
    val readmeLanguage: String? = null,

    val installLogs: List<InstallLogItem> = emptyList(),

    val isDownloading: Boolean = false,
    val downloadProgressPercent: Int? = null,
    val isInstalling: Boolean = false,
    val downloadError: String? = null,
    val installError: String? = null,

    val downloadStage: DownloadStage = DownloadStage.IDLE,
    val systemArchitecture: Architecture = Architecture.UNKNOWN,

    val isObtainiumAvailable: Boolean = false,
    val isObtainiumEnabled: Boolean = false,

    val isInstallDropdownExpanded: Boolean = false,

    val isAppManagerAvailable: Boolean = false,
    val isAppManagerEnabled: Boolean = false,

    val installedApp: InstalledApp? = null,
    /** Primary (or matching) APK already on disk under Downloads. */
    val hasDownloadedApk: Boolean = false,
    val isFavourite: Boolean = false,
    val isStarred: Boolean = false,
)

enum class DownloadStage {
    IDLE, DOWNLOADING, VERIFYING, INSTALLING
}

