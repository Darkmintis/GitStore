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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import com.darkmintis.gitstore.app.app_state.AppStateManager
import com.darkmintis.gitstore.core.domain.model.GithubRepoSummary
import com.darkmintis.gitstore.core.data.mappers.toSummary
import com.darkmintis.gitstore.feature.home.data.data_source.CachedTrendingDataSource
import com.darkmintis.gitstore.feature.home.data.data_source.TrendingResult
import com.darkmintis.gitstore.feature.home.data.data_source.toGithubRepoSummary
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

/**
 * GitHub repo search → keep repos whose latest release has an `.apk`.
 *
 * Wide GitHub query, `per_page=100`, allow forks, skip empty verified pages.
 */
class SearchRepositoryImpl(
    private val githubNetworkClient: HttpClient,
    private val appStateManager: AppStateManager,
    private val catalogSource: CachedTrendingDataSource,
) : SearchRepository {
    private var catalog: TrendingResult? = null
    private var lastCatalogAttemptMs = 0L
    private val catalogMutex = Mutex()

    private val releaseCheckCache = LruCache<String, GithubRepoSummary>(maxSize = 500)
    private val cacheMutex = Mutex()

    private companion object {
        const val PER_PAGE = 100
        const val VERIFY_CONCURRENCY = 15
        const val PER_CHECK_TIMEOUT_MS = 2_000L
        const val MAX_AUTO_SKIP_PAGES = 3

        /**
         * Release checks cost one API call each (60/hr anonymous), so when the live search is
         * only a fallback for a catalog miss, probe just the best name matches.
         */
        const val LIVE_VERIFY_CAP = 20
        const val CATALOG_RETRY_MS = 60 * 60 * 1000L
    }

    /** GitStore-data catalog: memory → disk → jsDelivr. Empty when nothing is reachable. */
    private suspend fun loadCatalog(): List<GithubRepoSummary> = catalogMutex.withLock {
        var current = catalog ?: catalogSource.loadCached()
        val stale = current == null || !catalogSource.isFresh(current)
        val now = System.currentTimeMillis()
        // Retry at most hourly: right after midnight the CDN may still serve yesterday's file.
        if (stale && now - lastCatalogAttemptMs > CATALOG_RETRY_MS) {
            lastCatalogAttemptMs = now
            val fresh = catalogSource.refresh()
            if (fresh.hasData) current = fresh
        }
        catalog = current
        current?.repos?.map { it.toGithubRepoSummary() }.orEmpty()
    }

    override fun searchRepositories(
        query: String,
        language: ProgrammingLanguage,
        page: Int,
        sortBy: SortBy,
    ): Flow<PaginatedRepos> = channelFlow {
        val searchQuery = buildSearchQuery(query, language)
        val (sort, order) = sortBy.toGithubParams()

        // Local catalog first: instant, and costs no GitHub API calls.
        var catalogLoaded = false
        if (page == 1 && query.isNotBlank()) {
            val all = loadCatalog()
            catalogLoaded = all.isNotEmpty()
            val local = searchCatalog(all, query, language, sortBy)
            if (local.isNotEmpty()) {
                // A catalog hit is enough: the live search would spend the whole rate limit
                // on release checks just to add lower-ranked repos.
                send(
                    PaginatedRepos(
                        repos = local,
                        hasMore = false,
                        nextPageIndex = 2,
                        totalCount = local.size,
                    )
                )
                return@channelFlow
            }
        }

        try {
            var currentPage = page
            var pagesSkipped = 0

            while (pagesSkipped <= MAX_AUTO_SKIP_PAGES) {
                currentCoroutineContext().ensureActive()

                val responseResult = githubNetworkClient.safeApiCall<GithubRepoSearchResponse>(
                    rateLimitHandler = appStateManager.rateLimitHandler,
                    autoRetryOnRateLimit = false
                ) {
                    get("/search/repositories") {
                        parameter("q", searchQuery)
                        parameter("per_page", PER_PAGE)
                        parameter("page", currentPage)
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
                val baseHasMore =
                    (currentPage * PER_PAGE) < total && response.items.isNotEmpty()

                if (response.items.isEmpty()) {
                    send(
                        PaginatedRepos(
                            repos = emptyList(),
                            hasMore = false,
                            nextPageIndex = currentPage + 1,
                            totalCount = total,
                        )
                    )
                    return@channelFlow
                }

                val candidates = if (catalogLoaded) {
                    rankByName(response.items, query).take(LIVE_VERIFY_CAP)
                } else {
                    response.items
                }
                val verified = verifyBatch(candidates)

                if (verified.isNotEmpty()) {
                    send(
                        PaginatedRepos(
                            repos = rankSearchResults(verified, query),
                            hasMore = baseHasMore,
                            nextPageIndex = currentPage + 1,
                            totalCount = total,
                        )
                    )
                    return@channelFlow
                }

                if (!baseHasMore) {
                    send(
                        PaginatedRepos(
                            repos = emptyList(),
                            hasMore = false,
                            nextPageIndex = currentPage + 1,
                            totalCount = total,
                        )
                    )
                    return@channelFlow
                }

                currentPage++
                pagesSkipped++
            }

            send(
                PaginatedRepos(
                    repos = emptyList(),
                    hasMore = true,
                    nextPageIndex = currentPage + 1,
                    totalCount = null,
                )
            )
        } catch (e: CancellationException) {
            throw e
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun verifyBatch(
        items: List<GithubRepoNetworkModel>,
    ): List<GithubRepoSummary> {
        val semaphore = Semaphore(VERIFY_CONCURRENCY)

        val deferredChecks = coroutineScope {
            items.map { repo ->
                async {
                    try {
                        semaphore.withPermit {
                            withTimeoutOrNull(PER_CHECK_TIMEOUT_MS) {
                                checkRepoHasInstallersCached(repo)
                            }
                        }
                    } catch (_: CancellationException) {
                        null
                    }
                }
            }
        }

        return buildList {
            for (i in items.indices) {
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
    }

    private suspend fun checkRepoHasInstallers(
        repo: GithubRepoNetworkModel
    ): InstallerCheck {
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

            val allReleases = releasesResult.getOrNull()
                ?: return InstallerCheck.Unknown

            fun hasApk(release: GithubReleaseNetworkModel): Boolean =
                release.assets.any { asset -> asset.name.lowercase().endsWith(".apk") }

            // Prefer stable; fall back to a prerelease with APKs (common for active apps).
            val release = allReleases.firstOrNull { candidate ->
                candidate.draft != true && candidate.prerelease != true && hasApk(candidate)
            } ?: allReleases.firstOrNull { candidate ->
                candidate.draft != true && hasApk(candidate)
            }

            if (release != null) {
                InstallerCheck.Hit(repo.toSummary())
            } else {
                InstallerCheck.Miss
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            InstallerCheck.Unknown
        }
    }

    private suspend fun checkRepoHasInstallersCached(
        repo: GithubRepoNetworkModel
    ): GithubRepoSummary? {
        val key = "${repo.owner.login}/${repo.name}"
        cacheMutex.withLock {
            if (releaseCheckCache.contains(key)) {
                return releaseCheckCache.get(key)
            }
        }

        return when (val check = checkRepoHasInstallers(repo)) {
            is InstallerCheck.Hit -> {
                cacheMutex.withLock { releaseCheckCache.put(key, check.repo) }
                check.repo
            }
            InstallerCheck.Miss -> {
                cacheMutex.withLock { releaseCheckCache.put(key, null) }
                null
            }
            InstallerCheck.Unknown -> null
        }
    }

    private sealed class InstallerCheck {
        data class Hit(val repo: GithubRepoSummary) : InstallerCheck()
        data object Miss : InstallerCheck()
        data object Unknown : InstallerCheck()
    }
}

/**
 * Case-insensitive match of every query word against name, description and topics.
 * Pure function so it can be unit-tested without a network.
 */
internal fun searchCatalog(
    catalog: List<GithubRepoSummary>,
    query: String,
    language: ProgrammingLanguage,
    sortBy: SortBy,
): List<GithubRepoSummary> {
    val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    val hits = catalog.filter { repo ->
        val haystack = "${repo.fullName} ${repo.description.orEmpty()} ${repo.topics.orEmpty().joinToString(" ")}".lowercase()
        words.all { it in haystack } &&
            (language == ProgrammingLanguage.All || language.queryValue == null ||
                repo.language.equals(language.queryValue, ignoreCase = true))
    }
    return when (sortBy) {
        SortBy.MostStars -> hits.sortedByDescending { it.stargazersCount }
        SortBy.MostForks -> hits.sortedByDescending { it.forksCount }
        SortBy.BestMatch -> rankSearchResults(hits.sortedByDescending { it.stargazersCount }, query)
    }
}

/** Same name ordering as [rankSearchResults], for raw GitHub items (stable on ties). */
private fun rankByName(items: List<GithubRepoNetworkModel>, query: String): List<GithubRepoNetworkModel> {
    val needle = query.trim().lowercase()
    return items.sortedBy { nameRank(it.name.lowercase(), needle) }
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

/**
 * Quoted term, name+description, allow forks.
 * `fork:false` was dropping mirrors/forks that still ship APKs.
 */
internal fun buildSearchQuery(
    userQuery: String,
    language: ProgrammingLanguage
): String {
    val clean = userQuery.trim()
    val q = if (clean.isBlank()) {
        "stars:>100"
    } else {
        "\"$clean\""
    }
    val scope = " in:name,description"
    val common = " archived:false fork:true"

    val languageFilter = if (language != ProgrammingLanguage.All && language.queryValue != null) {
        " language:${language.queryValue}"
    } else {
        ""
    }

    return ("$q$scope$common" + languageFilter).trim()
}
