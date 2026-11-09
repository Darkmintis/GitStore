package com.darkmintis.gitstore.feature.home.data.data_source

import com.darkmintis.gitstore.core.domain.model.GithubRepoSummary
import com.darkmintis.gitstore.core.domain.model.GithubUser
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HomeCategoryCacheTest {

    @Test
    fun `round trips a saved category and treats it as fresh`() = runTest {
        val dir = Files.createTempDirectory("home-cache-test").toFile()
        try {
            val cache = HomeCategoryCache(dir)

            cache.write("new", listOf(sampleRepo()))

            val cached = cache.read("new")
            assertNotNull(cached)
            assertEquals(1, cached.repos.size)
            assertEquals("sample", cached.repos.first().name)
            assertEquals("owner", cached.repos.first().owner.login)
            assertTrue(cache.isFresh(cached.lastUpdated))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `missing cache reads as null`() = runTest {
        val dir = Files.createTempDirectory("home-cache-test").toFile()
        try {
            assertEquals(null, HomeCategoryCache(dir).read("popular"))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun sampleRepo() = GithubRepoSummary(
        id = 1L,
        name = "sample",
        fullName = "owner/sample",
        owner = GithubUser(
            id = 2L,
            login = "owner",
            avatarUrl = "https://example.com/avatar.png",
            htmlUrl = "https://github.com/owner",
        ),
        description = "description",
        defaultBranch = "main",
        htmlUrl = "https://github.com/owner/sample",
        stargazersCount = 10,
        forksCount = 2,
        language = "Kotlin",
        topics = listOf("android"),
        releasesUrl = "https://api.github.com/repos/owner/sample/releases{/id}",
        updatedAt = "2026-11-01T00:00:00Z",
    )
}
