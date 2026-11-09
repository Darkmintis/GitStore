package com.darkmintis.gitstore.core.presentation.utils

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkHandlerTest {

    @Test
    fun parse_repoDeepLink() {
        assertEquals(
            DeepLink.Repo(12345L),
            DeepLinkHandler.parse("gitstore", "repo", "12345"),
        )
    }

    @Test
    fun parse_downloadsDeepLink() {
        assertEquals(
            DeepLink.Downloads,
            DeepLinkHandler.parse("gitstore", "downloads", null),
        )
    }

    @Test
    fun parse_ignoresOauthCallback() {
        assertNull(DeepLinkHandler.parse("gitstore", "callback", null))
    }

    @Test
    fun parse_rejectsBadRepoId() {
        assertNull(DeepLinkHandler.parse("gitstore", "repo", "nope"))
    }

    @Test
    fun offer_isDeliveredWhenCollectionStartsLater() = runTest {
        val handler = DeepLinkHandler()

        // Simulates MainActivity offering the intent before Compose starts collecting.
        handler.offer(DeepLink.Repo(42L))

        assertEquals(DeepLink.Repo(42L), handler.links.first())
    }
}
