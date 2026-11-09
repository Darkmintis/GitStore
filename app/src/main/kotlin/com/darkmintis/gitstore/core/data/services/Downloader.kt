package com.darkmintis.gitstore.core.data.services

import com.darkmintis.gitstore.feature.details.domain.model.DownloadProgress
import kotlinx.coroutines.flow.Flow

/** Completeness of a file managed by [Downloader], independent of the UI layer. */
enum class DownloadFileStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED,
}

data class DownloadFileState(
    val status: DownloadFileStatus,
    /** 0..100 when known. */
    val progress: Int? = null,
)

interface Downloader {

    fun download(
        url: String,
        suggestedFileName: String? = null,
        repoId: Long? = null,
        /** Shown as the notification title and the Downloads list title. */
        displayName: String? = null,
        /** Repo avatar cached alongside the file so Downloads can show it offline. */
        avatarUrl: String? = null,
    ): Flow<DownloadProgress>

    suspend fun saveToFile(url: String, suggestedFileName: String? = null): String

    /** Returns the path only when the file has actually finished downloading. */
    suspend fun getDownloadedFilePath(fileName: String): String?

    suspend fun cancelDownload(fileName: String): Boolean

    fun getActiveDownloadIds(): Map<String, Long>

    /** Repo id persisted for a download file, if known. */
    fun getRepoIdForFile(fileName: String): Long?

    /** Display name (repo/app) persisted for a download file, if known. */
    fun getDisplayNameForFile(fileName: String): String?

    /** Repo avatar persisted for a download file, if known. */
    fun getAvatarUrlForFile(fileName: String): String?

    /**
     * Current state of a download file. Null means there is no trustworthy record:
     * callers should validate the file before offering it for install.
     */
    suspend fun getFileState(fileName: String): DownloadFileState?
}
