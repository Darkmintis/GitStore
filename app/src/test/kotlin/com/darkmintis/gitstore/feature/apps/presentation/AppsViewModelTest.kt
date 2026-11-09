package com.darkmintis.gitstore.feature.apps.presentation

import app.cash.turbine.test
import com.darkmintis.gitstore.R
import com.darkmintis.gitstore.testing.FakeAppsRepository
import com.darkmintis.gitstore.testing.FakeDetailsRepository
import com.darkmintis.gitstore.testing.FakeDownloader
import com.darkmintis.gitstore.testing.FakeInstaller
import com.darkmintis.gitstore.testing.FakeInstalledAppsRepository
import com.darkmintis.gitstore.testing.FakePackageMonitor
import com.darkmintis.gitstore.testing.FakeStringProvider
import com.darkmintis.gitstore.testing.MainDispatcherTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppsViewModelTest : MainDispatcherTest() {

    @Test
    fun `loads installed apps and enables update all when updates exist`() = runViewModelTest {
        val updateApp = FakeInstalledAppsRepository.sampleApp(
            packageName = "com.update",
            isUpdateAvailable = true
        )
        val installed = FakeInstalledAppsRepository(listOf(updateApp))
        val viewModel = AppsViewModel(
            appsRepository = FakeAppsRepository(listOf(updateApp)),
            installer = FakeInstaller(),
            downloader = FakeDownloader(),
            installedAppsRepository = installed,
            packageMonitor = FakePackageMonitor(setOf("com.update")),
            detailsRepository = FakeDetailsRepository(),
            syncInstalledAppsUseCase = syncInstalledAppsUseCase(
                FakePackageMonitor(setOf("com.update")),
                installed
            ),
            stringProvider = FakeStringProvider(
                mapOf(R.string.no_updates_available to "No updates")
            )
        )

        viewModel.state.test {
            var loaded = false
            for (i in 0 until 15) {
                val state = awaitItem()
                if (state.apps.isNotEmpty()) {
                    assertEquals(1, state.apps.size)
                    assertTrue(state.updateAllButtonEnabled)
                    loaded = true
                    break
                }
            }
            assertTrue(loaded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search query is stored in state`() = runViewModelTest {
        val installed = FakeInstalledAppsRepository()
        val viewModel = AppsViewModel(
            appsRepository = FakeAppsRepository(),
            installer = FakeInstaller(),
            downloader = FakeDownloader(),
            installedAppsRepository = installed,
            packageMonitor = FakePackageMonitor(),
            detailsRepository = FakeDetailsRepository(),
            syncInstalledAppsUseCase = syncInstalledAppsUseCase(installedAppsRepository = installed),
            stringProvider = FakeStringProvider()
        )

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(AppsAction.OnSearchChange("blink"))
            var matched = false
            for (i in 0 until 10) {
                if (awaitItem().searchQuery == "blink") {
                    matched = true
                    break
                }
            }
            assertTrue(matched)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
