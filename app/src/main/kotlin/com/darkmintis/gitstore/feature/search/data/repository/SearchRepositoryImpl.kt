package com.darkmintis.gitstore.feature.search.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import com.darkmintis.gitstore.app.app_state.AppStateManager
import com.darkmintis.gitstore.core.domain.model.GithubRepoSummary
import com.darkmintis.gitstore.core.data.mappers.toSummary
import com.darkmintis.gitstore.core.data.model.GithubRepoNetworkModel
import com.darkmintis.gitstore.core.data.model.GithubRepoSearchResponse
import com.darkmintis.gitstore.feature.home.domain.model.PaginatedRepos
import com.darkmintis.gitstore.feature.search.data.repository.dto.GithubReleaseNetworkModel
import com.darkmintis.gitstore.feature.search.data.repository.utils.LruCache
import com.darkmintis.gitstore.feature.search.domain.model.ProgrammingLanguage
import com.darkmintis.gitstore.feature.search.domain.model.SortBy
import com.darkmintis.gitstore.feature.search.domain.repository.SearchRepository
import com.darkmintis.gitstore.network.RateLimitException
import com.darkmintis.gitstore.network.safeApiCall

class SearchRepositoryImpl(
    private val githubNetworkClient: HttpClient,
    private val appStateManager: AppStateManager
) : SearchRepository {
    private val releaseCheckCache = LruCache<String, GithubRepoSummary>(maxSize = 500)
    private val cacheMutex = Mutex()

    override fun searchRepositories(
        query: String,
        language: ProgrammingLanguage,
        page: Int,
        sortBy: SortBy,
    ): Flow<PaginatedRepos> = channelFlow {
        val perPage = 30
        val searchQuery = buildSearchQuery(query, language)
        val (sort, order) = sortBy.toGithubParams()

        try {
            val responseResult = githubNetworkClient.safeApiCall<GithubRepoSearchResponse>(
                rateLimitHandler = appStateManager.rateLimitHandler,
                autoRetryOnRateLimit = false
            ) {
                get("/search/repositories") {
                    parameter("q", searchQuery)
                    parameter("per_page", perPage)
                    parameter("page", page)
                    if (sort != null) {
                        parameter("sort", sort)
                        parameter("order", order)
                    }
                }
            }

            val response = responseResult.getOrElse { error ->
                if (error is RateLimitException) {
                    appStateManager.updateRateLimit(error.rateLimitInfo)
                }
                throw error
            }

            val total = response.totalCount
            val baseHasMore = (page * perPage) < total && response.items.isNotEmpty()

            if (page == 1) {
                val tunedTargetCount = 24
                val tunedMinFirstEmit = 4
                val tunedVerifyConcurrency = 12
                val tunedPerCheckTimeoutMs = 1400L
                val tunedMaxBackfillPages = 3
                val tunedEarlyFallbackTimeoutMs = 0L
                val tunedCandidatesPerPage = 50
                val rawFallbackFirstItems = emptyList<GithubRepoSummary>()

                val strict = runStrictFirstRender(
                    firstPageItems = response.items,
                    searchQuery = searchQuery,
                    sort = sort,
                    order = order,
                    perPage = perPage,
                    startPage = page,
                    targetCount = tunedTargetCount,
                    minFirstEmit = tunedMinFirstEmit,
                    verifyConcurrency = tunedVerifyConcurrency,
                    perCheckTimeoutMs = tunedPerCheckTimeoutMs,
                    maxBackfillPages = tunedMaxBackfillPages,
                    earlyFallbackTimeoutMs = tunedEarlyFallbackTimeoutMs,
                    rawFallbackItems = rawFallbackFirstItems,
                    candidatesPerPage = tunedCandidatesPerPage
                ) { growingVerified ->
                    if (growingVerified.isNotEmpty()) {
                        send(
                            PaginatedRepos(
                                repos = rankSearchResults(growingVerified, query),
                                hasMore = true,
                                nextPageIndex = page + 1,
                                totalCount = total
                            )
                        )
                    }
                }

                send(
                    PaginatedRepos(
                        repos = rankSearchResults(strict.verified, query),
                        hasMore = strict.hasMore,
                        nextPageIndex = strict.nextPageIndex,
                        totalCount = total
                    )
                )
            } else {
                if (response.items.isNotEmpty()) {
                    val semaphore = Semaphore(10)
                    val timeoutMs = 2000L

                    val deferredChecks = coroutineScope {
                        response.items.map { repo ->
                            async {
                                try {
                                    semaphore.withPermit {
                                        withTimeoutOrNull(timeoutMs) {
                                            checkRepoHasInstallersCached(repo)
                                        }
                                    }
                                } catch (_: CancellationException) {
                                    null
                                }
                            }
                        }
                    }

                    val filtered = buildList {
                        for (i in response.items.indices) {
                            currentCoroutineContext().ensureActive()
                            val result = try {
                                deferredChecks[i].await()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                null
                            }
                            if (result != null) add(result)
                        }
                    }

                    if (filtered.isNotEmpty() || !baseHasMore) {
                        send(
                            PaginatedRepos(
                                repos = rankSearchResults(filtered, query),
                                hasMore = baseHasMore,
                                nextPageIndex = page + 1,
                                totalCount = total
                            )
                        )
                    } else {
                        send(
                            PaginatedRepos(
                                repos = emptyList(),
                                hasMore = true,
                                nextPageIndex = page + 1,
                                totalCount = total
                            )
                        )
                    }
                } else {
                    send(
                        PaginatedRepos(
                            repos = emptyList(),
                            hasMore = false,
                            nextPageIndex = page + 1,
                            totalCount = total
                        )
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        }
    }.flowOn(Dispatchers.IO)

    private fun buildSearchQuery(
        userQuery: String,
        language: ProgrammingLanguage
    ): String {
        val clean = userQuery.trim()
        val q = if (clean.isBlank()) {
            "stars:>100"
        } else {
            if (clean.any { it.isWhitespace() }) "\"$clean\"" else clean
        }
        // Drop readme — it floods results with repos that only mention the query.
        val scope = if (clean.any { it.isWhitespace() }) {
            " in:name,description"
        } else {
            " in:name"
        }
        val common = " archived:false fork:false"

        val languageFilter = if (language != ProgrammingLanguage.All && language.queryValue != null) {
            " language:${language.queryValue}"
        } else {
            ""
        }

        return ("$q$scope$common" + languageFilter).trim()
    }

    private data class StrictResult(
        val verified: List<GithubRepoSummary>,
        val hasMore: Boolean,
        val nextPageIndex: Int
    )

    private suspend fun runStrictFirstRender(
        firstPageItems: List<GithubRepoNetworkModel>,
        searchQuery: String,
        sort: String?,
        order: String,
        perPage: Int,
        startPage: Int,
        targetCount: Int,
        minFirstEmit: Int,
        verifyConcurrency: Int,
        perCheckTimeoutMs: Long,
        maxBackfillPages: Int,
        earlyFallbackTimeoutMs: Long,
        rawFallbackItems: List<GithubRepoSummary>,
        candidatesPerPage: Int,
        onEarlyEmit: suspend (growingVerified: List<GithubRepoSummary>) -> Unit
    ): StrictResult {
        return coroutineScope {
            var lastFetchedPage = startPage
            val verified = mutableListOf<GithubRepoSummary>()
            var emittedOnce = false

            val fallbackJob = if (rawFallbackItems.isNotEmpty() && earlyFallbackTimeoutMs > 0) {
                launch {
                    delay(earlyFallbackTimeoutMs)
                    if (!emittedOnce) {
                        emittedOnce = true
                        onEarlyEmit(rawFallbackItems)
                    }
                }
            } else null

            suspend fun verifyBatch(items: List<GithubRepoNetworkModel>) {
                val semaphore = Semaphore(verifyConcurrency)

                val deferred = coroutineScope {
                    items.map { repo ->
                        async {
                            try {
                                semaphore.withPermit {
                                    withTimeoutOrNull(perCheckTimeoutMs) {
                                        checkRepoHasInstallersCached(repo)
                                    }
                                }
                            } catch (_: CancellationException) {
                                null
                            }
                        }
                    }
                }

                for (i in items.indices) {
                    currentCoroutineContext().ensureActive()
                    val res = try {
                        deferred[i].await()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    if (res != null) {
                        verified.add(res)
                        if (!emittedOnce && (verified.size >= minFirstEmit)) {
                            emittedOnce = true
                            fallbackJob?.cancel()
                            onEarlyEmit(verified.toList())
                        }
                        if (verified.size >= targetCount) return
                    }
                }
            }

            verifyBatch(firstPageItems.take(candidatesPerPage))

            var hasMore = true
            var nextPageIndex = startPage + 1
            var pagesFetched = 0
            while (verified.size < targetCount && hasMore && pagesFetched < maxBackfillPages) {
                val nextPage = lastFetchedPage + 1
                val respResult = githubNetworkClient.safeApiCall<GithubRepoSearchResponse>(
                    rateLimitHandler = appStateManager.rateLimitHandler,
                    autoRetryOnRateLimit = false
                ) {
                    get("/search/repositories") {
                        parameter("q", searchQuery)
                        parameter("per_page", perPage)
                        parameter("page", nextPage)
                        if (sort != null) {
                            parameter("sort", sort)
                            parameter("order", order)
                        }
                    }
                }

                val resp = respResult.getOrElse { error ->
                    if (error is RateLimitException) {
                        appStateManager.updateRateLimit(error.rateLimitInfo)
                    }
                    throw error
                }

                if (resp.items.isEmpty()) {
                    hasMore = false
                    nextPageIndex = nextPage
                    break
                }

                verifyBatch(resp.items.take(candidatesPerPage))

                lastFetchedPage = nextPage
                pagesFetched++

                hasMore = (lastFetchedPage * perPage) < resp.totalCount && resp.items.isNotEmpty()
                nextPageIndex = lastFetchedPage + 1
            }

            if (!emittedOnce) {
                fallbackJob?.cancel()
                if (verified.isNotEmpty()) {
                    onEarlyEmit(verified.toList())
                }
            }

            StrictResult(
                verified = verified.toList(),
                hasMore = hasMore,
                nextPageIndex = nextPageIndex
            )
        }
    }

    private suspend fun checkRepoHasInstallers(
        repo: GithubRepoNetworkModel
    ): GithubRepoSummary? {
        return try {
            val releasesResult = githubNetworkClient.safeApiCall<List<GithubReleaseNetworkModel>>(
                rateLimitHandler = appStateManager.rateLimitHandler,
                autoRetryOnRateLimit = false
            ) {
                get("/repos/${repo.owner.login}/${repo.name}/releases") {
                    header("Accept", "application/vnd.github.v3+json")
                    parameter("per_page", 10)
                }
            }

            releasesResult.onFailure { error ->
                if (error is RateLimitException) {
                    appStateManager.updateRateLimit(error.rateLimitInfo)
                }
            }

            val allReleases = releasesResult.getOrNull() ?: return null

            val stableRelease = allReleases.firstOrNull {
                it.draft != true && it.prerelease != true
            }

            if (stableRelease == null || stableRelease.assets.isEmpty()) {
                return null
            }

            val hasRelevantAssets = stableRelease.assets.any { asset ->
                asset.name.lowercase().endsWith(".apk")
            }

            if (hasRelevantAssets) repo.toSummary() else null
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun checkRepoHasInstallersCached(
        repo: GithubRepoNetworkModel
    ): GithubRepoSummary? {
        val key = "${repo.owner.login}/${repo.name}"
        val cached = cacheMutex.withLock {
            if (releaseCheckCache.contains(key)) releaseCheckCache.get(key) else null
        }
        if (cached != null || cacheMutex.withLock {
                releaseCheckCache.contains(key) && releaseCheckCache.get(
                    key
                ) == null
            }) {
            return cached
        }

        val result = checkRepoHasInstallers(repo)
        cacheMutex.withLock {
            releaseCheckCache.put(key, result)
        }
        return result
    }
}

/** Prefer exact / prefix name matches so queries like "termux" surface the real app. */
internal fun rankSearchResults(
    repos: List<GithubRepoSummary>,
    query: String,
): List<GithubRepoSummary> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return repos
    return repos
        .mapIndexed { index, repo -> Triple(repo, nameRank(repo.name.lowercase(), needle), index) }
        .sortedWith(compareBy({ it.second }, { it.third }))
        .map { it.first }
}

private fun nameRank(name: String, needle: String): Int = when {
    name == needle -> 0
    name.startsWith(needle) -> 1
    name.contains(needle) -> 2
    else -> 3
}

