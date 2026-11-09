package com.darkmintis.gitstore.feature.home.data.data_source

import co.touchlab.kermit.Logger
import com.darkmintis.gitstore.core.domain.model.GithubRepoSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Disk cache for the New/Popular lists so switching tabs, or reopening the app, shows the
 * last result immediately instead of paying for the release-probing pass again.
 *
 * A fresh entry is served without touching the network; a stale one is emitted first and
 * then refreshed by the live flow.
 */
class HomeCategoryCache(
    private val cacheDir: File,
    private val ttl: Duration = 24.hours,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    suspend fun read(key: String): CachedCategory? = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "home_$key.json")
        if (!file.exists()) return@withContext null

        runCatching {
            json.decodeFromString<CachedCategoryFile>(file.readText())
        }.getOrNull()
            ?.takeIf { it.repositories.isNotEmpty() }
            ?.let { payload ->
                CachedCategory(
                    repos = payload.repositories,
                    lastUpdated = payload.lastUpdated,
                )
            }
    }

    suspend fun write(key: String, repos: List<GithubRepoSummary>) = withContext(Dispatchers.IO) {
        if (repos.isEmpty()) return@withContext

        runCatching {
            cacheDir.mkdirs()
            val payload = CachedCategoryFile(
                lastUpdated = Clock.System.now().toString(),
                repositories = repos.map { it.toCached() },
            )
            File(cacheDir, "home_$key.json").writeText(json.encodeToString(payload))
        }.onFailure {
            Logger.w { "Could not cache home list '$key': ${it.message}" }
        }
    }

    fun isFresh(lastUpdated: String): Boolean {
        val saved = runCatching { Instant.parse(lastUpdated) }.getOrNull() ?: return false
        return (Clock.System.now() - saved) <= ttl
    }
}

data class CachedCategory(
    val repos: List<CachedGithubRepoSummary>,
    val lastUpdated: String,
)

@Serializable
private data class CachedCategoryFile(
    val lastUpdated: String,
    val repositories: List<CachedGithubRepoSummary>,
)

private fun GithubRepoSummary.toCached(): CachedGithubRepoSummary = CachedGithubRepoSummary(
    id = id,
    name = name,
    fullName = fullName,
    owner = CachedGithubOwner(login = owner.login, avatarUrl = owner.avatarUrl),
    description = description,
    defaultBranch = defaultBranch,
    htmlUrl = htmlUrl,
    stargazersCount = stargazersCount,
    forksCount = forksCount,
    language = language,
    topics = topics,
    releasesUrl = releasesUrl,
    updatedAt = updatedAt,
)
