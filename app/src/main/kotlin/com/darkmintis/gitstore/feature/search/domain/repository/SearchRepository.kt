package com.darkmintis.gitstore.feature.search.domain.repository

import kotlinx.coroutines.flow.Flow
import com.darkmintis.gitstore.feature.home.domain.model.PaginatedRepos
import com.darkmintis.gitstore.feature.search.domain.model.ProgrammingLanguage
import com.darkmintis.gitstore.feature.search.domain.model.SortBy

interface SearchRepository {
    fun searchRepositories(
        query: String,
        language: ProgrammingLanguage,
        page: Int,
        sortBy: SortBy = SortBy.BestMatch,
    ): Flow<PaginatedRepos>
}
