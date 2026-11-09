package com.darkmintis.gitstore.feature.home.data.repository

import kotlinx.datetime.Clock as DateClock
import kotlinx.datetime.Instant as DateInstant
import kotlin.time.Duration.Companion.days

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.darkmintis.gitstore.app.app_state.AppStateManager
import com.darkmintis.gitstore.core.domain.model.GithubRepoSummary
import com.darkmintis.gitstore.core.data.mappers.toSummary
import com.darkmintis.gitstore.core.data.model.GithubRepoNetworkModel
import com.darkmintis.gitstore.core.data.model.GithubRepoSearchResponse
import com.darkmintis.gitstore.feature.home.data.data_source.CachedTrendingDataSource
import com.darkmintis.gitstore.feature.home.data.data_source.HomeCategoryCache
import com.darkmintis.gitstore.feature.home.data.data_source.TrendingResult
import com.darkmintis.gitstore.feature.home.data.data_source.toGithubRepoSummary
import com.darkmintis.gitstore.feature.home.domain.repository.HomeRepository
import com.darkmintis.gitstore.feature.home.domain.model.PaginatedRepos
import com.darkmintis.gitstore.network.RateLimitException
import com.darkmintis.gitstore.network.safeApiCall

class HomeRepositoryImpl(
    private val githubNetworkClient: HttpClient,
    private val appStateManager: AppStateManager,
    private val cachedDataSource: CachedTrendingDataSource,
    private val categoryCache: HomeCategoryCache,
) : HomeRepository {

        override fun getTrendingRepositories(
        page: Int,
        forceRefresh: Boolean,
        isOffline: Boolean,
    ): Flow<PaginatedRepos> = flow {
        if (page == 1) {
            // Paint the saved snapshot first; only hit jsDelivr when it is stale or forced.
            val cached = cachedDataSource.loadCached()
            if (cached != null && cached.hasData) {
                Logger.d { "Serving ${cached.repos.size} saved trending repos immediately" }
                emit(cached.toPaginated(isFromCache = true))
                if (isOffline) return@flow
                if (!forceRefresh && cachedDataSource.isFresh(cached)) return@flow
            } else if (isOffline) {
                Logger.d { "No saved trending list while offline" }
                emit(
                    PaginatedRepos(
                        repos = emptyList(),
                        hasMore = false,
                        nextPageIndex = 2,
                        isOffline = true
                    )
                )
                return@flow
            }

            val fresh = cachedDataSource.refresh()
            if (fresh.hasData) {
                emit(fresh.toPaginated(isFromCache = false))
                return@flow
            }

            // Refresh failed: the stale snapshot (if any) is already on screen.
            if (cached != null) return@flow

            if (fresh.isOffline) {
                Logger.d { "No cached trending data available while offline" }
                emit(
                    PaginatedRepos(
                        repos = emptyList(),
                        hasMore = false,
                        nextPageIndex = 2,
                        isOffline = true
                    )
                )
                return@flow
            }

            Logger.d { "No cached data available, falling back to live API" }
        }

        emitAll(searchReposWithInstallersFlow(page))

    }.flowOn(Dispatchers.IO)

    private fun searchReposWithInstallersFlow(startPage: Int): Flow<PaginatedRepos> = flow {
        val oneWeekAgo = DateClock.System.now()
            .minus(7.days)
            .toLocalDateTime(TimeZone.UTC)
            .date

        val results = mutableListOf<GithubRepoSummary>()
        var currentApiPage = startPage
        val perPage = 100
        val semaphore = Semaphore(25)
        val maxPagesToFetch = 5
        var pagesFetchedCount = 0
        var lastEmittedCount = 0
        val desiredCount = 10

        val query = buildSimplifiedQuery("stars:>500 archived:false pushed:>=$oneWeekAgo")
        Logger.d { "Live API Query: $query | Page: $startPage" }

        while (results.size < desiredCount && pagesFetchedCount < maxPagesToFetch) {
            currentCoroutineContext().ensureActive()

            try {
                val response = githubNetworkClient.safeApiCall<GithubRepoSearchResponse>(
                    rateLimitHandler = appStateManager.rateLimitHandler,
                    autoRetryOnRateLimit = false
                ) {
                    get("/search/repositories") {
                        parameter("q", query)
                        parameter("sort", "stars")
                        parameter("order", "desc")
                        parameter("per_page", perPage)
                        parameter("page", currentApiPage)
                    }
                }.getOrElse { error ->
                    Logger.e { "Search request failed: ${error.message}" }

                    if (error is RateLimitException) {
                        appStateManager.updateRateLimit(error.rateLimitInfo)
                    }

                    throw error
                }

                Logger.d { "API Page $currentApiPage: Got ${response.items.size} repos" }

                if (response.items.isEmpty()) {
                    Logger.d { "No more items from API, breaking" }
                    break
                }

                val candidates = response.items
                    .map { repo -> repo to calculatePlatformScore(repo) }
                    .filter { it.second > 0 }
                    .take(50)
                    .map { it.first }

                Logger.d { "Checking ${candidates.size} candidates for installers" }

                coroutineScope {
                    val deferredResults = candidates.map { repo ->
                        async {
                            semaphore.withPermit {
                                withTimeoutOrNull(5000) {
                                    checkRepoHasInstallers(repo)
                                }
                            }
                        }
                    }

                    for (deferred in deferredResults) {
                        currentCoroutineContext().ensureActive()

                        val result = deferred.await()
                        if (result != null) {
                            results.add(result)
                            Logger.d { "Found installer repo: ${result.fullName} (${results.size}/$desiredCount)" }

                            if (results.size % 3 == 0 || results.size >= desiredCount) {
                                val newItems = results.subList(lastEmittedCount, results.size)

                                if (newItems.isNotEmpty()) {
                                    emit(
                                        PaginatedRepos(
                                            repos = newItems.toList(),
                                            hasMore = true,
                                            nextPageIndex = currentApiPage + 1
                                        )
                                    )
                                    Logger.d { "Emitted ${newItems.size} repos (total: ${results.size})" }
                                    lastEmittedCount = results.size
                                }
                            }

                            if (results.size >= desiredCount) {
                                Logger.d { "Reached desired count, breaking" }
                                break
                            }
                        }
                    }
                }

                if (results.size >= desiredCount || response.items.size < perPage) {
                    Logger.d { "Breaking: results=${results.size}, response size=${response.items.size}" }
                    break
                }

                currentApiPage++
                pagesFetchedCount++

            } catch (e: RateLimitException) {
                Logger.e { "Rate limited during search" }
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e { "Search failed: ${e.message}" }
                e.printStackTrace()
                break
            }
        }

        if (results.size > lastEmittedCount) {
            val finalBatch = results.subList(lastEmittedCount, results.size)
            val finalHasMore = pagesFetchedCount < maxPagesToFetch && results.size >= desiredCount
            emit(
                PaginatedRepos(
                    repos = finalBatch.toList(),
                    hasMore = finalHasMore,
                    nextPageIndex = if (finalHasMore) currentApiPage + 1 else currentApiPage
                )
            )
            Logger.d { "Final emit: ${finalBatch.size} repos (total: ${results.size})" }
        } else if (results.isEmpty()) {
            emit(
                PaginatedRepos(
                    repos = emptyList(),
                    hasMore = false,
                    nextPageIndex = currentApiPage
                )
            )
            Logger.d { "No results found" }
        }
    }.flowOn(Dispatchers.IO)

        override fun getNew(
        page: Int,
        forceRefresh: Boolean,
        isOffline: Boolean,
    ): Flow<PaginatedRepos> {
        val thirtyDaysAgo = DateClock.System.now()
            .minus(30.days)
            .toLocalDateTime(TimeZone.UTC)
            .date

        return cachedCategoryFlow("new", page, forceRefresh, isOffline) {
            searchReposWithInstallersFlow(
                baseQuery = "stars:>5 archived:false created:>=$thirtyDaysAgo",
                sort = "created",
                order = "desc",
                startPage = page,
                cacheKey = "new",
            )
        }
    }

        override fun getPopular(
        page: Int,
        forceRefresh: Boolean,
        isOffline: Boolean,
    ): Flow<PaginatedRepos> {
        val threeDaysAgo = DateClock.System.now()
            .minus(3.days)
            .toLocalDateTime(TimeZone.UTC)
            .date

        return cachedCategoryFlow("popular", page, forceRefresh, isOffline) {
            searchReposWithInstallersFlow(
                baseQuery = "stars:>50 archived:false pushed:>=$threeDaysAgo",
                sort = "updated",
                order = "desc",
                startPage = page,
                cacheKey = "popular",
            )
        }
    }

    /**
     * Emits the last saved page immediately so tab switches feel instant. A fresh entry is
     * returned as-is; a stale one is followed by the live flow, which replaces it.
     */
    private fun cachedCategoryFlow(
        key: String,
        page: Int,
        forceRefresh: Boolean,
        isOffline: Boolean,
        live: () -> Flow<PaginatedRepos>,
    ): Flow<PaginatedRepos> = flow {
        if (page == 1) {
            val cached = categoryCache.read(key)
            if (cached != null) {
                emit(
                    PaginatedRepos(
                        repos = cached.repos.map { it.toGithubRepoSummary() },
                        hasMore = false,
                        nextPageIndex = 2,
                        isFromCache = true,
                        cachedUpdatedAt = cached.lastUpdated,
                    )
                )
            }

            if (isOffline) {
                if (cached == null) {
                    emit(
                        PaginatedRepos(
                            repos = emptyList(),
                            hasMore = false,
                            nextPageIndex = 2,
                            isOffline = true,
                        )
                    )
                }
                return@flow
            }

            if (cached != null && !forceRefresh && categoryCache.isFresh(cached.lastUpdated)) {
                return@flow
            }
        }

        emitAll(live())
    }.flowOn(Dispatchers.IO)

    private fun TrendingResult.toPaginated(isFromCache: Boolean): PaginatedRepos =
        PaginatedRepos(
            repos = repos.map { it.toGithubRepoSummary() },
            hasMore = false,
            nextPageIndex = 2,
            isFromCache = isFromCache,
            cachedUpdatedAt = lastUpdated,
        )

    private fun searchReposWithInstallersFlow(
        baseQuery: String,
        sort: String,
        order: String,
        startPage: Int,
        desiredCount: Int = 10,
        cacheKey: String? = null,
    ): Flow<PaginatedRepos> = flow {
        val results = mutableListOf<GithubRepoSummary>()
        var currentApiPage = startPage
        val perPage = 100
        val semaphore = Semaphore(25)
        val maxPagesToFetch = 5
        var pagesFetchedCount = 0
        var lastEmittedCount = 0

        val query = buildSimplifiedQuery(baseQuery)
        Logger.d { "Query: $query | Sort: $sort | Page: $startPage" }

        while (results.size < desiredCount && pagesFetchedCount < maxPagesToFetch) {
            currentCoroutineContext().ensureActive()

            try {
                val response = githubNetworkClient.safeApiCall<GithubRepoSearchResponse>(
                    rateLimitHandler = appStateManager.rateLimitHandler,
                    autoRetryOnRateLimit = false
                ) {
                    get("/search/repositories") {
                        parameter("q", query)
                        parameter("sort", sort)
                        parameter("order", order)
                        parameter("per_page", perPage)
                        parameter("page", currentApiPage)
                    }
                }.getOrElse { error ->
                    Logger.e { "Search request failed: ${error.message}" }

                    if (error is RateLimitException) {
                        appStateManager.updateRateLimit(error.rateLimitInfo)
                    }

                    throw error
                }

                Logger.d { "API Page $currentApiPage: Got ${response.items.size} repos" }

                if (response.items.isEmpty()) {
                    Logger.d { "No more items from API, breaking" }
                    break
                }

                val candidates = response.items
                    .map { repo -> repo to calculatePlatformScore(repo) }
                    .filter { it.second > 0 }
                    .take(50)
                    .map { it.first }

                Logger.d { "Checking ${candidates.size} candidates for installers" }

                coroutineScope {
                    val deferredResults = candidates.map { repo ->
                        async {
                            semaphore.withPermit {
                                withTimeoutOrNull(5000) {
                                    checkRepoHasInstallers(repo)
                                }
                            }
                        }
                    }

                    for (deferred in deferredResults) {
                        currentCoroutineContext().ensureActive()

                        val result = deferred.await()
                        if (result != null) {
                            results.add(result)
                            Logger.d { "Found installer repo: ${result.fullName} (${results.size}/$desiredCount)" }

                            if (results.size % 3 == 0 || results.size >= desiredCount) {
                                val newItems = results.subList(lastEmittedCount, results.size)

                                if (newItems.isNotEmpty()) {
                                    emit(
                                        PaginatedRepos(
                                            repos = newItems.toList(),
                                            hasMore = true,
                                            nextPageIndex = currentApiPage + 1
                                        )
                                    )
                                    Logger.d { "Emitted ${newItems.size} repos (total: ${results.size})" }
                                    lastEmittedCount = results.size
                                    if (cacheKey != null && startPage == 1) {
                                        // Save after each batch so leaving mid-load still caches.
                                        categoryCache.write(cacheKey, results.toList())
                                    }
                                }
                            }

                            if (results.size >= desiredCount) {
                                Logger.d { "Reached desired count, breaking" }
                                break
                            }
                        }
                    }
                }

                if (results.size >= desiredCount || response.items.size < perPage) {
                    Logger.d { "Breaking: results=${results.size}, response size=${response.items.size}" }
                    break
                }

                currentApiPage++
                pagesFetchedCount++

            } catch (e: RateLimitException) {
                Logger.e { "Rate limited during search" }
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e { "Search failed: ${e.message}" }
                e.printStackTrace()
                break
            }
        }

        if (results.size > lastEmittedCount) {
            val finalBatch = results.subList(lastEmittedCount, results.size)
            val finalHasMore = pagesFetchedCount < maxPagesToFetch && results.size >= desiredCount
            emit(
                PaginatedRepos(
                    repos = finalBatch.toList(),
                    hasMore = finalHasMore,
                    nextPageIndex = if (finalHasMore) currentApiPage + 1 else currentApiPage
                )
            )
            Logger.d { "Final emit: ${finalBatch.size} repos (total: ${results.size})" }
        } else if (results.isEmpty()) {
            emit(
                PaginatedRepos(
                    repos = emptyList(),
                    hasMore = false,
                    nextPageIndex = currentApiPage
                )
            )
            Logger.d { "No results found" }
        }

        if (cacheKey != null && startPage == 1 && results.isNotEmpty()) {
            categoryCache.write(cacheKey, results.toList())
        }
    }.flowOn(Dispatchers.IO)

    private fun buildSimplifiedQuery(baseQuery: String): String {
        // Android-only app, always use android topic
        return "$baseQuery topic:android"
    }

    private fun calculatePlatformScore(repo: GithubRepoNetworkModel): Int {
        var score = 5
        val topics = repo.topics.orEmpty().map { it.lowercase() }
        val language = repo.language?.lowercase()
        val desc = repo.description?.lowercase() ?: ""

        // Android-specific scoring
        if (topics.contains("android")) score += 10
        if (topics.contains("mobile")) score += 5
        if (language == "kotlin" || language == "java") score += 5
        if (desc.contains("android") || desc.contains("apk")) score += 3

        return score
    }

    private suspend fun checkRepoHasInstallers(repo: GithubRepoNetworkModel): GithubRepoSummary? {
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

            val allReleases = releasesResult.getOrNull() ?: return null

            val stableRelease = allReleases.firstOrNull {
                it.draft != true && it.prerelease != true
            }

            if (stableRelease == null || stableRelease.assets.isEmpty()) {
                return null
            }

            val relevantAssets = stableRelease.assets.filter { asset ->
                val name = asset.name.lowercase()
                // Android-only: check for APK files
                name.endsWith(".apk")
            }

            if (relevantAssets.isNotEmpty()) {
                repo.toSummary()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    @Serializable
    private data class GithubReleaseNetworkModel(
        val assets: List<AssetNetworkModel>,
        val draft: Boolean? = null,
        val prerelease: Boolean? = null,
        @SerialName("published_at") val publishedAt: String? = null
    )

    @Serializable
    private data class AssetNetworkModel(
        val name: String
    )
}



