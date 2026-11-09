package com.darkmintis.gitstore.testing

import com.darkmintis.gitstore.core.data.services.ApkInfoExtractor
import com.darkmintis.gitstore.core.data.services.DownloadFileState
import com.darkmintis.gitstore.core.data.services.Downloader
import com.darkmintis.gitstore.core.data.services.Installer
import com.darkmintis.gitstore.core.domain.model.ApkPackageInfo
import com.darkmintis.gitstore.core.domain.model.Architecture
import com.darkmintis.gitstore.core.domain.model.GithubAsset
import com.darkmintis.gitstore.feature.details.domain.model.DownloadProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

class FakeDownloader : Downloader {
    override fun download(
        url: String,
        suggestedFileName: String?,
        repoId: Long?,
        displayName: String?,
        avatarUrl: String?,
    ): Flow<DownloadProgress> =
        flowOf(DownloadProgress(bytesDownloaded = 100, totalBytes = 100, percent = 100))

    override suspend fun saveToFile(url: String, suggestedFileName: String?): String =
        "/tmp/fake.apk"

    override suspend fun getDownloadedFilePath(fileName: String): String? = null

    override suspend fun cancelDownload(fileName: String): Boolean = true

    override fun getActiveDownloadIds(): Map<String, Long> = emptyMap()

    override fun getRepoIdForFile(fileName: String): Long? = null

    override fun getDisplayNameForFile(fileName: String): String? = null

    override fun getAvatarUrlForFile(fileName: String): String? = null

    override suspend fun getFileState(fileName: String): DownloadFileState? = null

    override suspend fun reconcileActiveDownloads() = Unit

    override fun observeProgress(fileName: String): Flow<DownloadProgress> = emptyFlow()
}

class FakeInstaller : Installer {
    override suspend fun isSupported(extOrMime: String): Boolean = true

    override suspend fun ensurePermissionsOrThrow(extOrMime: String) = Unit

    override suspend fun install(filePath: String, extOrMime: String) = Unit

    override fun isAssetInstallable(assetName: String): Boolean = assetName.endsWith(".apk")

    override fun choosePrimaryAsset(assets: List<GithubAsset>): GithubAsset? = assets.firstOrNull()

    override fun detectSystemArchitecture(): Architecture = Architecture.AARCH64

    override fun isObtainiumInstalled(): Boolean = false

    override fun openInObtainium(
        repoOwner: String,
        repoName: String,
        onOpenInstaller: () -> Unit
    ) = Unit

    override fun isAppManagerInstalled(): Boolean = false

    override fun openInAppManager(filePath: String, onOpenInstaller: () -> Unit) = Unit

    override fun getApkInfoExtractor(): ApkInfoExtractor = object : ApkInfoExtractor {
        override suspend fun extractPackageInfo(filePath: String): ApkPackageInfo? = null
    }
}
