package com.darkmintis.gitstore.feature.search.data.repository

import com.darkmintis.gitstore.feature.search.domain.model.ProgrammingLanguage
import com.darkmintis.gitstore.feature.search.domain.model.SortBy
import com.darkmintis.gitstore.testing.FakeSearchRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchCatalogTest {
    private val termux = FakeSearchRepository.sampleRepo(id = 1, name = "termux-app")
    private val other = FakeSearchRepository.sampleRepo(id = 2, name = "notes")

    @Test
    fun `exact repo name is found locally`() {
        val hits = searchCatalog(listOf(other, termux), "termux", ProgrammingLanguage.All, SortBy.BestMatch)
        assertEquals(listOf(1L), hits.map { it.id })
    }

    @Test
    fun `blank query and non-matching query return nothing`() {
        assertEquals(emptyList(), searchCatalog(listOf(termux), " ", ProgrammingLanguage.All, SortBy.BestMatch))
        assertEquals(emptyList(), searchCatalog(listOf(termux), "zzz", ProgrammingLanguage.All, SortBy.BestMatch))
    }
}
