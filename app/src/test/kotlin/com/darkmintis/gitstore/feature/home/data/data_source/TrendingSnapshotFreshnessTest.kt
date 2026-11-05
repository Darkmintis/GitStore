package com.darkmintis.gitstore.feature.home.data.data_source

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrendingSnapshotFreshnessTest {

    private val now = Instant.parse("2026-10-25T12:00:00Z")

    @Test
    fun `accepts a snapshot from the same day`() {
        assertTrue(isSnapshotFresh("2026-10-25T06:00:00Z", now))
    }

    @Test
    fun `accepts a snapshot exactly at the age limit`() {
        assertTrue(isSnapshotFresh("2026-10-23T12:00:00Z", now))
    }

    @Test
    fun `rejects a snapshot older than the age limit`() {
        assertFalse(isSnapshotFresh("2026-10-23T11:59:00Z", now))
    }

    @Test
    fun `rejects blank or malformed timestamps`() {
        assertFalse(isSnapshotFresh("", now))
        assertFalse(isSnapshotFresh("not-a-date", now))
    }
}
