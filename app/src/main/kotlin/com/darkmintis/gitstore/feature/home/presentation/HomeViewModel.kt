package com.darkmintis.gitstore.feature.home.presentation

import com.darkmintis.gitstore.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.app.Application
import com.darkmintis.gitstore.core.presentation.utils.AndroidStringProvider
import com.darkmintis.gitstore.core.presentation.utils.ErrorMapper
import com.darkmintis.gitstore.core.data.local.db.entities.FavoriteRepo
import com.darkmintis.gitstore.core.domain.repository.FavouritesRepository
import com.darkmintis.gitstore.core.domain.repository.InstalledAppsRepository
import com.darkmintis.gitstore.core.domain.repository.StarredRepository
import com.darkmintis.gitstore.core.domain.use_cases.SyncInstalledAppsUseCase
import com.darkmintis.gitstore.core.presentation.model.DiscoveryRepository
import com.darkmintis.gitstore.feature.home.domain.repository.HomeRepository
import com.darkmintis.gitstore.feature.home.presentation.model.HomeCategory
import kotlinx.datetime.Clock as DateClock

class HomeViewModel(
    private val application: Application,
    private val homeRepository: HomeRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val syncInstalledAppsUseCase: SyncInstalledAppsUseCase,
    private val favouritesRepository: FavouritesRepository,
    private val starredRepository: StarredRepository,
) : ViewModel() {

    private val stringProvider = AndroidStringProvider(application)


    private var hasLoadedInitialData = false
    private var currentJob: Job? = null
    private var nextPageIndex = 1

    private val _state = MutableStateFlow(HomeState())
    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                syncSystemState()

                loadRepos(isInitial = true)
                observeInstalledApps()
                observeFavourites()
                observeStarredRepos()

                hasLoadedInitialData = true
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = HomeState()
        )

    private fun syncSystemState() {
        viewModelScope.launch {
            try {
                val result = syncInstalledAppsUseCase()
                if (result.isFailure) {
                    Logger.w { "Initial sync had issues: ${result.exceptionOrNull()?.message}" }
                }
            } catch (e: Exception) {
                Logger.e { "Initial sync failed: ${e.message}" }
            }
        }
    }

    private fun observeInstalledApps() {
        viewModelScope.launch {
            installedAppsRepository.getAllInstalledApps().collect { installedApps ->
                val installedMap = installedApps.associateBy { it.repoId }
                _state.update { current ->
                    current.copy(
                        repos = current.repos.map { homeRepo ->
                            val app = installedMap[homeRepo.repository.id]
                            homeRepo.copy(
                                isInstalled = app != null,
                                isUpdateAvailable = app?.isUpdateAvailable ?: false
                            )
                        },
                        isUpdateAvailable = installedMap.any { it.value.isUpdateAvailable }
                    )
                }
            }
        }
    }

    private fun loadRepos(isInitial: Boolean = false, category: HomeCategory? = null) {
        if (_state.value.isLoading || _state.value.isLoadingMore) {
            Logger.d { "Already loading, skipping..." }
            return
        }

        currentJob?.cancel()

        if (isInitial) {
            nextPageIndex = 1
        }

        val targetCategory = category ?: _state.value.currentCategory

        Logger.d { "Loading repos: category=$targetCategory, page=$nextPageIndex, isInitial=$isInitial" }

        currentJob = viewModelScope.launch {

            _state.update {
                it.copy(
                    isLoading = isInitial,
                    isLoadingMore = !isInitial,
                    errorMessage = null,
                    currentCategory = targetCategory,
                    repos = if (isInitial) emptyList() else it.repos,
                    isShowingCachedData = false
                )
            }

            try {
                val flow = when (targetCategory) {
                    HomeCategory.TRENDING -> homeRepository.getTrendingRepositories(nextPageIndex)
                    HomeCategory.NEW -> homeRepository.getNew(nextPageIndex)
                    HomeCategory.POPULAR -> homeRepository.getPopular(nextPageIndex)
                }

                flow.collect { paginatedRepos ->
                    Logger.d { "Received ${paginatedRepos.repos.size} repos, hasMore=${paginatedRepos.hasMore}, nextPage=${paginatedRepos.nextPageIndex}" }

                    this@HomeViewModel.nextPageIndex = paginatedRepos.nextPageIndex

                    val installedAppsMap = installedAppsRepository
                        .getAllInstalledApps()
                        .first()
                        .associateBy { it.repoId }

                    val favoritesMap = favouritesRepository
                        .getAllFavorites()
                        .first()
                        .associateBy { it.repoId }

                    val starredReposMap = starredRepository
                        .getAllStarred()
                        .first()
                        .associateBy { it.repoId }

                    val newReposWithStatus = paginatedRepos.repos.map { repo ->
                        val app = installedAppsMap[repo.id]
                        val favourite = favoritesMap[repo.id]
                        val starred = starredReposMap[repo.id]

                        DiscoveryRepository(
                            isInstalled = app != null,
                            isFavourite = favourite != null,
                            isStarred = starred != null,
                            isUpdateAvailable = app?.isUpdateAvailable ?: false,
                            repository = repo
                        )
                    }

                    _state.update { currentState ->
                        val rawList = currentState.repos + newReposWithStatus
                        val uniqueList = rawList.distinctBy { it.repository.fullName }

                        currentState.copy(
                            repos = uniqueList,
                            hasMorePages = paginatedRepos.hasMore,
                            isShowingCachedData = paginatedRepos.isFromCache,
                            errorMessage = if (uniqueList.isEmpty() && !paginatedRepos.hasMore) {
                                application.getString(R.string.no_repositories_found)
                            } else null
                        )
                    }
                }

                Logger.d { "Flow completed" }
                _state.update {
                    it.copy(isLoading = false, isLoadingMore = false)
                }

            } catch (t: Throwable) {
                if (t is CancellationException) {
                    Logger.d { "Load cancelled (expected)" }
                    throw t
                }

                Logger.e { "Load failed: ${t.message}" }
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = ErrorMapper.message(t, stringProvider)
                    )
                }
            }
        }
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Refresh -> {
                viewModelScope.launch {
                    syncInstalledAppsUseCase()
                    nextPageIndex = 1
                    loadRepos(isInitial = true)
                }
            }

            HomeAction.Retry -> {
                nextPageIndex = 1
                loadRepos(isInitial = true)
            }

            HomeAction.LoadMore -> {
                Logger.d { "LoadMore action: isLoading=${_state.value.isLoading}, isLoadingMore=${_state.value.isLoadingMore}, hasMore=${_state.value.hasMorePages}" }

                if (!_state.value.isLoadingMore && !_state.value.isLoading && _state.value.hasMorePages) {
                    loadRepos(isInitial = false)
                }
            }

            is HomeAction.SwitchCategory -> {
                if (_state.value.currentCategory != action.category) {
                    nextPageIndex = 1
                    loadRepos(isInitial = true, category = action.category)
                }
            }

            is HomeAction.OnRepositoryClick -> {
                /* Handled in composable */
            }

            is HomeAction.OnToggleFavorite -> {
                viewModelScope.launch {
                    try {
                        val repo = action.repo

                        val favoriteRepo = FavoriteRepo(
                            repoId = repo.id,
                            repoName = repo.name,
                            repoOwner = repo.owner.login,
                            repoOwnerAvatarUrl = repo.owner.avatarUrl,
                            repoDescription = repo.description,
                            primaryLanguage = repo.language,
                            repoUrl = repo.htmlUrl,
                            latestVersion = null,
                            latestReleaseUrl = null,
                            addedAt = DateClock.System.now().toEpochMilliseconds(),
                            lastSyncedAt = DateClock.System.now().toEpochMilliseconds()
                        )

                        favouritesRepository.toggleFavorite(favoriteRepo)
                    } catch (t: CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        Logger.e { "Failed to toggle favorite: ${t.message}" }
                    }
                }
            }

            is HomeAction.OnRepositoryDeveloperClick -> {
                /* Handled in composable */
            }

            HomeAction.OnSearchClick -> {
                /* Handled in composable */
            }

            HomeAction.OnSettingsClick -> {
                /* Handled in composable */
            }

            HomeAction.OnAppsClick -> {
                /* Handled in composable */
            }
        }
    }

    private fun observeFavourites() {
        viewModelScope.launch {
            favouritesRepository.getAllFavorites().collect { favourites ->
                val favouritesMap = favourites.associateBy { it.repoId }
                _state.update { current ->
                    current.copy(
                        repos = current.repos.map { homeRepo ->
                            homeRepo.copy(
                                isFavourite = favouritesMap.containsKey(homeRepo.repository.id)
                            )
                        }
                    )
                }
            }
        }
    }

    private fun observeStarredRepos() {
        viewModelScope.launch {
            starredRepository.getAllStarred().collect { starredRepos ->
                val starredReposById = starredRepos.associateBy { it.repoId }
                _state.update { current ->
                    current.copy(
                        repos = current.repos.map { homeRepo ->
                            homeRepo.copy(
                                isStarred = starredReposById.containsKey(homeRepo.repository.id)
                            )
                        }
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentJob?.cancel()
    }
}




