package com.darkmintis.gitstore.feature.home.domain.model

import com.darkmintis.gitstore.core.domain.model.GithubRepoSummary

data class PaginatedRepos(
    val repos: List<GithubRepoSummary>,
    val hasMore: Boolean,
    val nextPageIndex: Int,
    val totalCount: Int? = null,
    val isFromCache: Boolean = false
)

