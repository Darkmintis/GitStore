package com.darkmintis.gitstore.feature.download_manager.presentation

data class DownloadManagerState(
    val downloads: List<DownloadItem> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

data class DownloadItem(
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val status: DownloadStatus,
    val progress: Int? = null,
    val repoId: Long? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val packageName: String? = null,
    val apkVersionName: String? = null,
    val apkVersionCode: Long? = null,
    val isInstalled: Boolean = false,
    val isUpdateAvailable: Boolean = false,
) {
    val title: String get() = displayName?.takeIf { it.isNotBlank() } ?: fileName
}

enum class DownloadStatus {
    DOWNLOADING,
    COMPLETED,
    FAILED,
}
