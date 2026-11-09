package com.darkmintis.gitstore.core.presentation.utils

import com.darkmintis.gitstore.core.data.local.db.entities.InstalledApp

interface AppLauncher {
    suspend fun launchApp(installedApp: InstalledApp): Result<Unit>
    suspend fun canLaunchApp(installedApp: InstalledApp): Boolean
    suspend fun launchPackage(packageName: String): Result<Unit>
}

