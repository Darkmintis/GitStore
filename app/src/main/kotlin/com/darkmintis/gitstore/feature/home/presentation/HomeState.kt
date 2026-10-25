package com.darkmintis.gitstore.feature.home.presentation

import com.darkmintis.gitstore.core.data.local.db.entities.InstalledApp
import com.darkmintis.gitstore.core.domain.model.GithubRepoSummary
import com.darkmintis.gitstore.core.presentation.model.DiscoveryRepository
import com.darkmintis.gitstore.feature.home.presentation.model.HomeCategory

data class HomeState(
    val repos: List<DiscoveryRepository> = emptyList(),
    val installedApps: List<InstalledApp> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val hasMorePages: Boolean = true,
    val currentCategory: HomeCategory = HomeCategory.TRENDING,
    val isAppsSectionVisible: Boolean = true,
    val isUpdateAvailable: Boolean = false,
    /** True when the visible list was served from disk rather than the network. */
    val isShowingCachedData: Boolean = false,
    /** Timestamp of the saved snapshot backing the visible list, when there is one. */
    val cachedUpdatedAt: String? = null,
)

