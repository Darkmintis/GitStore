package com.darkmintis.gitstore.feature.details.presentation

import app.cash.turbine.test
import com.darkmintis.gitstore.R
import com.darkmintis.gitstore.testing.FakeBrowserHelper
import com.darkmintis.gitstore.testing.FakeDetailsRepository
import com.darkmintis.gitstore.testing.FakeDownloader
import com.darkmintis.gitstore.testing.FakeFavouritesRepository
import com.darkmintis.gitstore.testing.FakeInstaller
import com.darkmintis.gitstore.testing.FakeInstalledAppsRepository
import com.darkmintis.gitstore.testing.FakePackageMonitor
import com.darkmintis.gitstore.testing.FakeStarredRepository
import com.darkmintis.gitstore.testing.FakeStringProvider
import com.darkmintis.gitstore.testing.MainDispatcherTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModelTest : MainDispatcherTest() {

    @Test
    fun `loads repository summary on start`() = runViewModelTest {
        val installed = FakeInstalledAppsRepository()
        val detailsRepository = FakeDetailsRepository()
        val viewModel = DetailsViewModel(
            repositoryId = 1L,
            detailsRepository = detailsRepository,
            downloader = FakeDownloader(),
            installer = FakeInstaller(),
            helper = FakeBrowserHelper(),
            installedAppsRepository = installed,
            favouritesRepository = FakeFavouritesRepository(),
            starredRepository = FakeStarredRepository(),
            stringProvider = FakeStringProvider(
                mapOf(
                    R.string.added_to_favourites to "Added",
                    R.string.removed_from_favourites to "Removed"
                )
            ),
            packageMonitor = FakePackageMonitor(),
            syncInstalledAppsUseCase = syncInstalledAppsUseCase(installedAppsRepository = installed)
        )

        viewModel.state.test {
            var loadedName: String? = null
            for (i in 0 until 15) {
                val state = awaitItem()
                if (state.repository != null) {
                    loadedName = state.repository?.name
                    break
                }
            }
            assertEquals("git-store", loadedName)
            assertNotNull(loadedName)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
