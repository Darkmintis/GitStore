package com.darkmintis.gitstore.feature.search.data.repository

import com.darkmintis.gitstore.testing.FakeSearchRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class RankSearchResultsTest {

    @Test
    fun `exact name match ranks above partial mentions`() {
        val exact = FakeSearchRepository.sampleRepo(id = 1, name = "termux")
        val prefix = FakeSearchRepository.sampleRepo(id = 2, name = "termux-app")
        val other = FakeSearchRepository.sampleRepo(id = 3, name = "awesome-android")

        val ranked = rankSearchResults(listOf(other, prefix, exact), "termux")

        assertEquals(listOf(exact, prefix, other).map { it.id }, ranked.map { it.id })
    }
}
