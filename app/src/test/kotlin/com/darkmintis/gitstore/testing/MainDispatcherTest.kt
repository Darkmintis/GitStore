package com.darkmintis.gitstore.testing

import com.darkmintis.gitstore.core.domain.use_cases.SyncInstalledAppsUseCase
import com.darkmintis.gitstore.core.domain.repository.InstalledAppsRepository
import com.darkmintis.gitstore.core.data.services.PackageMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
open class MainDispatcherTest {
    protected lateinit var testDispatcher: TestDispatcher

    protected fun syncInstalledAppsUseCase(
        packageMonitor: PackageMonitor = FakePackageMonitor(),
        installedAppsRepository: InstalledAppsRepository,
    ) = SyncInstalledAppsUseCase(
        packageMonitor = packageMonitor,
        installedAppsRepository = installedAppsRepository,
        // Keep sync on the test scheduler — Dispatchers.IO resumes after resetMain()
        // are what left UncaughtExceptionsBeforeTest on CI.
        dispatcher = testDispatcher,
    )

    protected fun runViewModelTest(
        testBody: suspend TestScope.() -> Unit
    ) {
        testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        try {
            runTest(context = testDispatcher) {
                testBody()
                advanceUntilIdle()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
