package com.darkmintis.gitstore.feature.home.domain.repository

import kotlinx.coroutines.flow.Flow
import com.darkmintis.gitstore.feature.home.domain.model.PaginatedRepos

interface HomeRepository {
    fun getTrendingRepositories(
        page: Int,
        forceRefresh: Boolean = false,
        isOffline: Boolean = false,
    ): Flow<PaginatedRepos>

    fun getNew(
        page: Int,
        forceRefresh: Boolean = false,
        isOffline: Boolean = false,
    ): Flow<PaginatedRepos>

    fun getPopular(
        page: Int,
        forceRefresh: Boolean = false,
        isOffline: Boolean = false,
    ): Flow<PaginatedRepos>
}
