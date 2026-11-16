package com.darkmintis.gitstore.feature.download_manager.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelfUpdateDownloadTest {
    private val own = "com.darkmintis.gitstore"

    @Test
    fun matchesOwnPackageOrSettingsFileName() {
        assertTrue(isGitStoreSelfUpdateDownload("GitStore-v1.4.1.apk", null, own))
        assertTrue(isGitStoreSelfUpdateDownload("other.apk", own, own))
        assertFalse(isGitStoreSelfUpdateDownload("termux.apk", "com.termux", own))
        assertFalse(isGitStoreSelfUpdateDownload("GitStoreHelper.apk", "com.example", own))
    }
}
