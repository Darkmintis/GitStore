package com.darkmintis.gitstore.core.data.services

import com.darkmintis.gitstore.core.domain.model.Architecture
import com.darkmintis.gitstore.core.domain.model.GithubAsset

interface Installer {
    suspend fun isSupported(extOrMime: String): Boolean

    suspend fun ensurePermissionsOrThrow(extOrMime: String)

    suspend fun install(filePath: String, extOrMime: String)

    /** Opens the system uninstaller for [packageName]. The OS confirmation is required. */
    fun uninstall(packageName: String)

    fun isAssetInstallable(assetName: String): Boolean
    fun choosePrimaryAsset(assets: List<GithubAsset>): GithubAsset?
    fun detectSystemArchitecture(): Architecture
    fun isObtainiumInstalled(): Boolean
    fun openInObtainium(
        repoOwner: String,
        repoName: String,
        onOpenInstaller: () -> Unit
    )

    fun isAppManagerInstalled(): Boolean
    fun openInAppManager(
        filePath: String,
        onOpenInstaller: () -> Unit
    )

    fun getApkInfoExtractor(): ApkInfoExtractor
}

