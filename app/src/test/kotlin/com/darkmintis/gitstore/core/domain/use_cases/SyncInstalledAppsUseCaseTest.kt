package com.darkmintis.gitstore.core.domain.use_cases

import com.darkmintis.gitstore.testing.FakeInstalledAppsRepository
import com.darkmintis.gitstore.testing.FakePackageMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SyncInstalledAppsUseCaseTest {

    @Test
    fun `removes apps that are no longer installed on device`() = runTest {
        val keep = FakeInstalledAppsRepository.sampleApp(packageName = "com.keep")
        val remove = FakeInstalledAppsRepository.sampleApp(packageName = "com.remove", repoId = 2)
        val repo = FakeInstalledAppsRepository(listOf(keep, remove))
        val monitor = FakePackageMonitor(installedPackages = setOf("com.keep"))
        val useCase = SyncInstalledAppsUseCase(monitor, repo, UnconfinedTestDispatcher(testScheduler))

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(listOf("com.remove"), repo.deleted)
    }

    @Test
    fun `migrates apps missing installedVersionName`() = runTest {
        val legacy = FakeInstalledAppsRepository.sampleApp(
            packageName = "com.legacy",
            installedVersionName = null
        )
        val repo = FakeInstalledAppsRepository(listOf(legacy))
        val monitor = FakePackageMonitor(
            installedPackages = setOf("com.legacy"),
            packageInfo = mapOf(
                "com.legacy" to com.darkmintis.gitstore.core.domain.model.SystemPackageInfo(
                    packageName = "com.legacy",
                    versionName = "2.0.0",
                    versionCode = 20L,
                    isInstalled = true
                )
            )
        )
        val useCase = SyncInstalledAppsUseCase(monitor, repo, UnconfinedTestDispatcher(testScheduler))

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(1, repo.updated.size)
        assertEquals("2.0.0", repo.updated.first().installedVersionName)
        assertEquals(20L, repo.updated.first().installedVersionCode)
    }
}
