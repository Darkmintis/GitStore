package com.darkmintis.gitstore.core.presentation.utils

import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.darkmintis.gitstore.core.data.local.db.entities.InstalledApp

class AndroidAppLauncher(
    private val context: Context
) : AppLauncher {

    override suspend fun launchApp(installedApp: InstalledApp): Result<Unit> =
        withContext(Dispatchers.Main) {
            runCatching {
                val packageManager = context.packageManager

                val launchIntent = packageManager.getLaunchIntentForPackage(installedApp.packageName)
                
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    Logger.d { "Launched app: ${installedApp.packageName}" }
                } else {
                    throw Exception("No launch intent found for ${installedApp.packageName}")
                }
            }.onFailure { error ->
                Logger.e { "Failed to launch app ${installedApp.packageName}: ${error.message}" }
            }
        }

    override suspend fun canLaunchApp(installedApp: InstalledApp): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val packageManager = context.packageManager
                packageManager.getLaunchIntentForPackage(installedApp.packageName) != null
            } catch (e: Exception) {
                false
            }
        }

    override suspend fun launchPackage(packageName: String): Result<Unit> =
        withContext(Dispatchers.Main) {
            runCatching {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                    ?: throw Exception("No launch intent found for $packageName")

                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                Logger.d { "Launched app: $packageName" }
            }.onFailure { error ->
                Logger.e { "Failed to launch app $packageName: ${error.message}" }
            }
        }
}

