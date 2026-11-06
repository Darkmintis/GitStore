package com.darkmintis.gitstore.core.data.services

import kotlinx.coroutines.flow.Flow
import com.darkmintis.gitstore.feature.details.domain.model.DownloadProgress

interface Downloader {

    fun download(
        url: String,
        suggestedFileName: String? = null,
        repoId: Long? = null,
        /** Shown as the notification title (repo/app name). Falls back to the file name. */
        displayName: String? = null,
    ): Flow<DownloadProgress>

    suspend fun saveToFile(url: String, suggestedFileName: String? = null): String

    suspend fun getDownloadedFilePath(fileName: String): String?

    suspend fun cancelDownload(fileName: String): Boolean

    fun getActiveDownloadIds(): Map<String, Long>

    /** Repo id persisted for a download file, if known. */
    fun getRepoIdForFile(fileName: String): Long?

    /** Display name (repo/app) persisted for a download file, if known. */
    fun getDisplayNameForFile(fileName: String): String?
}
