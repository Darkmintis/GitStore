package com.darkmintis.gitstore.feature.search.presentation

import app.cash.turbine.test
import com.darkmintis.gitstore.R
import com.darkmintis.gitstore.feature.home.domain.model.PaginatedRepos
import com.darkmintis.gitstore.feature.search.domain.model.SortBy
import com.darkmintis.gitstore.testing.FakeFavouritesRepository
import com.darkmintis.gitstore.testing.FakeInstalledAppsRepository
import com.darkmintis.gitstore.testing.FakeSearchRepository
import com.darkmintis.gitstore.testing.FakeStarredRepository
import com.darkmintis.gitstore.testing.FakeStringProvider
import com.darkmintis.gitstore.testing.MainDispatcherTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest : MainDispatcherTest() {

    private fun createViewModel(
        searchRepository: FakeSearchRepository = FakeSearchRepository()
    ): SearchViewModel {
        val installed = FakeInstalledAppsRepository()
        return SearchViewModel(
            stringProvider = FakeStringProvider(
                mapOf(
                    R.string.no_repositories_found to "No repositories found",
                    R.string.search_failed to "Search failed"
                )
            ),
            searchRepository = searchRepository,
            installedAppsRepository = installed,
            syncInstalledAppsUseCase = syncInstalledAppsUseCase(installedAppsRepository = installed),
            favouritesRepository = FakeFavouritesRepository(),
            starredRepository = FakeStarredRepository()
        )
    }

    @Test
    fun `blank query clears results without searching`() = runViewModelTest {
        val searchRepository = FakeSearchRepository()
        val viewModel = createViewModel(searchRepository)

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(SearchAction.OnSearchChange("git"))
            viewModel.onAction(SearchAction.OnSearchChange(""))

            var cleared = false
            for (i in 0 until 10) {
                val state = awaitItem()
                if (state.query.isEmpty() && state.repositories.isEmpty()) {
                    cleared = true
                    break
                }
            }
            assertTrue(cleared)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty search results keep errorMessage null for the empty state`() = runViewModelTest {
        val searchRepository = FakeSearchRepository().apply {
            results = PaginatedRepos(
                repos = emptyList(),
                hasMore = false,
                nextPageIndex = 2,
                totalCount = 0
            )
        }
        val viewModel = createViewModel(searchRepository)

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(SearchAction.OnSearchChange("missing"))
            viewModel.onAction(SearchAction.OnSearchImeClick)

            var found = false
            for (i in 0 until 15) {
                val state = awaitItem()
                if (!state.isLoading && state.errorMessage == null) {
                    found = true
                    assertTrue(state.repositories.isEmpty())
                    break
                }
            }
            assertTrue(found)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `changing sort triggers a new search`() = runViewModelTest {
        val searchRepository = FakeSearchRepository().apply {
            results = PaginatedRepos(
                repos = listOf(FakeSearchRepository.sampleRepo()),
                hasMore = false,
                nextPageIndex = 2,
                totalCount = 1
            )
        }
        val viewModel = createViewModel(searchRepository)

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(SearchAction.OnSearchChange("gitstore"))
            viewModel.onAction(SearchAction.OnSearchImeClick)

            var firstSearchFinished = false
            for (i in 0 until 20) {
                val state = awaitItem()
                if (!state.isLoading) {
                    firstSearchFinished = true
                    break
                }
            }
            assertTrue(firstSearchFinished)
            val searchesAfterQuery = searchRepository.searchCount

            viewModel.onAction(SearchAction.OnSortBySelected(SortBy.MostStars))

            var sawExpected = false
            for (i in 0 until 20) {
                val state = awaitItem()
                if (state.selectedSortBy == SortBy.MostStars && !state.isLoading) {
                    sawExpected = true
                    break
                }
            }
            assertTrue(sawExpected)
            assertTrue(searchRepository.searchCount > searchesAfterQuery)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
