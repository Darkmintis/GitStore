package com.darkmintis.gitstore.feature.search.data.repository

import com.darkmintis.gitstore.feature.search.domain.model.ProgrammingLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildSearchQueryTest {

    @Test
    fun `quotes term and searches name plus description with forks allowed`() {
        val q = buildSearchQuery("termux", ProgrammingLanguage.All)
        assertEquals(
            "\"termux\" in:name,description archived:false fork:true",
            q,
        )
    }

    @Test
    fun `blank query falls back to popular repos`() {
        val q = buildSearchQuery("  ", ProgrammingLanguage.All)
        assertTrue(q.startsWith("stars:>100"))
        assertTrue(q.contains("fork:true"))
    }

    @Test
    fun `language filter is appended`() {
        val q = buildSearchQuery("termux", ProgrammingLanguage.Kotlin)
        assertTrue(q.endsWith("language:kotlin"), q)
    }
}
