package com.darkmintis.gitstore.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitStoreSelfUpdateTest {
    @Test
    fun compareVersions_ordersSemverTags() {
        assertTrue(GitStoreSelfUpdate.compareVersions("v1.5.0", "1.4.1") > 0)
        assertTrue(GitStoreSelfUpdate.compareVersions("1.4.1", "v1.5.0") < 0)
        assertEquals(0, GitStoreSelfUpdate.compareVersions("v1.5.0", "1.5.0"))
        assertTrue(GitStoreSelfUpdate.compareVersions("1.5.1", "1.5.0") > 0)
    }
}
