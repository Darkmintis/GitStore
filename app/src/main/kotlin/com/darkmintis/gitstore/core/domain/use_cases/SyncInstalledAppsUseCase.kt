package com.darkmintis.gitstore.core.domain.use_cases
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.darkmintis.gitstore.core.data.local.db.entities.InstalledApp
import com.darkmintis.gitstore.core.data.services.PackageMonitor
import com.darkmintis.gitstore.core.domain.repository.InstalledAppsRepository

/**
 * Use case for synchronizing installed apps state with the system package manager.
 * 
 * Responsibilities:
 * 1. Remove apps from DB that are no longer installed on the system
 * 2. Migrate legacy apps missing versionName/versionCode fields
 * 
 * This should be called before loading or refreshing app data to ensure consistency.
 */
class SyncInstalledAppsUseCase(
    private val packageMonitor: PackageMonitor,
    private val installedAppsRepository: InstalledAppsRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Executes the sync operation.
     * 
     * @return Result indicating success or failure with error details
     */
    suspend operator fun invoke(): Result<Unit> = withContext(dispatcher) {
        try {
            val installedPackageNames = packageMonitor.getAllInstalledPackageNames()
            val appsInDb = installedAppsRepository.getAllInstalledApps().first()

            val toDelete = mutableListOf<String>()
            val toMigrate = mutableListOf<Pair<String, MigrationResult>>()

            appsInDb.forEach { app ->
                when {
                    !installedPackageNames.contains(app.packageName) -> {
                        toDelete.add(app.packageName)
                    }
                    app.installedVersionName == null -> {
                        val migrationResult = determineMigrationData(app)
                        toMigrate.add(app.packageName to migrationResult)
                    }
                }
            }

            installedAppsRepository.executeInTransaction {
                toDelete.forEach { packageName ->
                    try {
                        installedAppsRepository.deleteInstalledApp(packageName)
                        Logger.d { "Removed uninstalled app: $packageName" }
                    } catch (e: Exception) {
                        Logger.e { "Failed to delete $packageName: ${e.message}" }
                    }
                }

                toMigrate.forEach { (packageName, migrationResult) ->
                    try {
                        val app = appsInDb.find { it.packageName == packageName } ?: return@forEach

                        installedAppsRepository.updateApp(
                            app.copy(
                                installedVersionName = migrationResult.versionName,
                                installedVersionCode = migrationResult.versionCode,
                                latestVersionName = migrationResult.versionName,
                                latestVersionCode = migrationResult.versionCode
                            )
                        )

                        Logger.d {
                            "Migrated $packageName: ${migrationResult.source} " +
                                    "(versionName=${migrationResult.versionName}, code=${migrationResult.versionCode})"
                        }
                    } catch (e: Exception) {
                        Logger.e { "Failed to migrate $packageName: ${e.message}" }
                    }
                }
            }

            Logger.d { 
                "Sync completed: ${toDelete.size} deleted, ${toMigrate.size} migrated" 
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Logger.e { "Sync failed: ${e.message}" }
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private suspend fun determineMigrationData(app: InstalledApp): MigrationResult {
        val systemInfo = packageMonitor.getInstalledPackageInfo(app.packageName)
        return if (systemInfo != null) {
            MigrationResult(
                versionName = systemInfo.versionName,
                versionCode = systemInfo.versionCode,
                source = "system package manager"
            )
        } else {
            MigrationResult(
                versionName = app.installedVersion,
                versionCode = 0L,
                source = "fallback to release tag"
            )
        }
    }

    private data class MigrationResult(
        val versionName: String,
        val versionCode: Long,
        val source: String
    )
}

