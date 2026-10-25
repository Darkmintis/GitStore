package com.darkmintis.gitstore.feature.home.data.data_source

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.darkmintis.gitstore.core.domain.model.GithubRepoSummary
import com.darkmintis.gitstore.core.domain.model.GithubUser

/**
 * On-disk name for the offline copy of the trending list. Lives in the app's cacheDir so the
 * OS can reclaim it under storage pressure without losing any authoritative data.
 */
const val TRENDING_CACHE_FILE_NAME = "trending_apps.json"

/**
 * Serves the daily discovered trending list from jsDelivr and keeps a disk copy so the
 * Trending tab still renders when the device is offline.
 *
 * Network is tried first so users always see today's list; the disk copy is only used as a
 * fallback. That keeps it simple and guarantees freshness while still avoiding a dead screen.
 */
class CachedTrendingDataSource(
    private val cacheFile: File,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }

        install(HttpRequestRetry) {
            maxRetries = 2
            retryOnServerErrors(maxRetries = 2)
            exponentialDelay()
        }

        expectSuccess = false
    }

    /**
     * jsDelivr, not raw.githubusercontent.com: edge-cached across multiple CDNs with proper
     * cache headers, so the Trending seed list loads fast everywhere instead of depending on
     * GitHub's plain file server.
     *
     * `@main` is deliberate — jsDelivr revalidates branch refs within minutes, so the daily
     * run of scripts/discover.py propagates to users automatically. Never pin this to a tag
     * or SHA: those are cached immutably for a year and would freeze users on day-one data.
     */
    private val baseUrl =
        "https://cdn.jsdelivr.net/gh/Darkmintis/Git-Store@main/cached-data/trending"

    suspend fun loadTrending(): TrendingResult = withContext(Dispatchers.IO) {
        val fetched = fetchFromNetwork()

        if (fetched != null && fetched.repos.isNotEmpty()) {
            Logger.d { "✓ Loaded ${fetched.repos.size} trending repos from jsDelivr (updated ${fetched.lastUpdated})" }
            writeCache(fetched)
            return@withContext fetched
        }

        val cached = readCache()
        if (cached != null) {
            Logger.d { "↩ Serving ${cached.repos.size} trending repos from disk cache (updated ${cached.lastUpdated})" }
            return@withContext cached
        }

        Logger.d {
            if (fetched?.isOffline == true) {
                "No trending cache available and the device appears to be offline"
            } else {
                "No trending data available"
            }
        }

        TrendingResult(isOffline = fetched?.isOffline == true)
    }

    private suspend fun fetchFromNetwork(): TrendingResult? {
        val url = "$baseUrl/apps.json"

        return try {
            val response: HttpResponse = httpClient.get(url)

            if (response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                val cachedData = json.decodeFromString<CachedRepoResponse>(responseText)

                if (cachedData.repositories.isEmpty()) {
                    null
                } else {
                    TrendingResult(
                        repos = cachedData.repositories,
                        lastUpdated = cachedData.lastUpdated
                    )
                }
            } else if (response.status.value == 404) {
                Logger.w { "⚠️ Cached trending data not found (404) - may not be generated yet" }
                null
            } else {
                Logger.e { "❌ Failed to fetch trending repos: HTTP ${response.status.value}" }
                null
            }
        } catch (e: HttpRequestTimeoutException) {
            // Must stay above IOException: HttpRequestTimeoutException extends it.
            Logger.e { "⏱️ Timeout fetching trending repos: ${e.message}" }
            null
        } catch (e: SerializationException) {
            Logger.e { "🔧 Trending JSON parsing error: ${e.message}" }
            null
        } catch (e: IOException) {
            Logger.e { "📡 Trending fetch failed (${e::class.simpleName}): ${e.message}" }
            TrendingResult(isOffline = isOfflineCause(e))
        } catch (e: Exception) {
            Logger.e { "💥 Error fetching trending repos: ${e::class.simpleName}: ${e.message}" }
            null
        }
    }

    private fun readCache(): TrendingResult? {
        val text = runCatching {
            if (cacheFile.exists()) cacheFile.readText() else null
        }.getOrNull() ?: return null

        return runCatching {
            json.decodeFromString<CachedRepoResponse>(text)
        }.getOrNull()
            ?.takeIf { it.repositories.isNotEmpty() }
            ?.let {
                TrendingResult(
                    repos = it.repositories,
                    lastUpdated = it.lastUpdated,
                    isFromDiskCache = true
                )
            }
    }

    private fun writeCache(result: TrendingResult) {
        runCatching {
            val payload = CachedRepoResponse(
                lastUpdated = result.lastUpdated.orEmpty(),
                totalCount = result.repos.size,
                repositories = result.repos
            )
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(json.encodeToString(payload))
        }.onFailure { Logger.w { "Could not persist trending cache: ${it.message}" } }
    }

    /**
     * Clean up resources when done
     */
    fun close() {
        httpClient.close()
    }
}

/**
 * Outcome of loading the trending seed list. `isOffline` distinguishes "no connection" from
 * "the file is missing or malformed" so the UI can show the right message.
 */
data class TrendingResult(
    val repos: List<CachedGithubRepoSummary> = emptyList(),
    val lastUpdated: String? = null,
    val isOffline: Boolean = false,
    val isFromDiskCache: Boolean = false,
) {
    val hasData: Boolean get() = repos.isNotEmpty()
}

/**
 * Only treat failures that genuinely mean "nothing is reachable" as offline. A plain
 * IOException can also be a slow-but-alive server, where an offline message would be wrong.
 */
private fun isOfflineCause(error: Throwable): Boolean {
    var current: Throwable? = error
    var depth = 0

    while (current != null && depth < 5) {
        if (current is UnknownHostException ||
            current is ConnectException ||
            current is NoRouteToHostException ||
            current is PortUnreachableException
        ) {
            return true
        }
        current = current.cause
        depth++
    }

    return false
}

/**
 * Cached repository data (Android apps)
 */
@Serializable
data class CachedRepoResponse(
    val lastUpdated: String,
    val totalCount: Int,
    val repositories: List<CachedGithubRepoSummary>
)

/**
 * Simplified repo summary for cached data
 * Only includes the fields present in the cached JSON files
 */
@Serializable
data class CachedGithubRepoSummary(
    val id: Long,
    val name: String,
    val fullName: String,
    val owner: CachedGithubOwner,
    val description: String?,
    val defaultBranch: String,
    val htmlUrl: String,
    val stargazersCount: Int,
    val forksCount: Int,
    val language: String?,
    val topics: List<String>?,
    val releasesUrl: String,
    val updatedAt: String
)

/**
 * Simplified owner data for cached repos
 * Only includes login and avatarUrl (not id and htmlUrl)
 */
@Serializable
data class CachedGithubOwner(
    val login: String,
    val avatarUrl: String
)

/**
 * Extension to convert cached summary to full GithubRepoSummary
 */
fun CachedGithubRepoSummary.toGithubRepoSummary(): GithubRepoSummary {
    return GithubRepoSummary(
        id = id,
        name = name,
        fullName = fullName,
        owner = GithubUser(
            id = 0,
            login = owner.login,
            avatarUrl = owner.avatarUrl,
            htmlUrl = "https://github.com/${owner.login}"
        ),
        description = description,
        defaultBranch = defaultBranch,
        htmlUrl = htmlUrl,
        stargazersCount = stargazersCount,
        forksCount = forksCount,
        language = language,
        topics = topics,
        releasesUrl = releasesUrl,
        updatedAt = updatedAt
    )
}

