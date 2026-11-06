package com.darkmintis.gitstore.testing

import com.darkmintis.gitstore.core.domain.model.GithubRepoSummary
import com.darkmintis.gitstore.core.domain.model.GithubUser
import com.darkmintis.gitstore.feature.home.domain.model.PaginatedRepos
import com.darkmintis.gitstore.feature.search.domain.model.ProgrammingLanguage
import com.darkmintis.gitstore.feature.search.domain.model.SortBy
import com.darkmintis.gitstore.feature.search.domain.repository.SearchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeSearchRepository : SearchRepository {
    var lastQuery: String? = null
    var lastLanguage: ProgrammingLanguage? = null
    var lastPage: Int? = null
    var lastSortBy: SortBy? = null
    var searchCount: Int = 0
    var results: PaginatedRepos = PaginatedRepos(
        repos = emptyList(),
        hasMore = false,
        nextPageIndex = 2,
        totalCount = 0
    )
    var error: Throwable? = null

    override fun searchRepositories(
        query: String,
        language: ProgrammingLanguage,
        page: Int,
        sortBy: SortBy,
    ): Flow<PaginatedRepos> = flow {
        lastQuery = query
        lastLanguage = language
        lastPage = page
        lastSortBy = sortBy
        searchCount += 1
        error?.let { throw it }
        emit(results)
    }

    companion object {
        fun sampleRepo(id: Long = 1L, name: String = "git-store") = GithubRepoSummary(
            id = id,
            name = name,
            fullName = "owner/$name",
            owner = GithubUser(
                id = 10,
                login = "owner",
                avatarUrl = "https://example.com/a.png",
                htmlUrl = "https://github.com/owner"
            ),
            description = "desc",
            defaultBranch = "main",
            htmlUrl = "https://github.com/owner/$name",
            stargazersCount = 10,
            forksCount = 2,
            language = "Kotlin",
            topics = listOf("android"),
            releasesUrl = "https://api.github.com/repos/owner/$name/releases",
            updatedAt = "2026-01-01T00:00:00Z"
        )
    }
}
