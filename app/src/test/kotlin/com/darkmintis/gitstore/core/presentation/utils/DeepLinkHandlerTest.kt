package com.darkmintis.gitstore.core.presentation.utils

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
}
