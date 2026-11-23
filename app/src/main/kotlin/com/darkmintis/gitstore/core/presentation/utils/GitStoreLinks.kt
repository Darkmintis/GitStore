package com.darkmintis.gitstore.core.presentation.utils

import android.app.Activity
import android.content.Intent
import android.content.Context
import androidx.core.net.toUri

object GitStoreLinks {
    const val REPOSITORY = "https://github.com/Darkmintis/GitStore"
    const val DATA_REPOSITORY = "https://github.com/Darkmintis/GitStore-data"
    const val DEVELOPER_SITE = "https://darkmintis.dev"
    const val NEW_BUG_REPORT =
        "https://github.com/Darkmintis/GitStore/issues/new?template=bug_report.yml"

    /** v1.5+ data CDN (GitStore-data): trending/, new/, popular/, catalog/ each hold apps.json. */
    const val DATA_CDN_BASE = "https://cdn.jsdelivr.net/gh/Darkmintis/GitStore-data@main"
    const val DATA_CDN_TRENDING = "$DATA_CDN_BASE/trending"

    /**
     * Pre-1.5 clients still read this path. Keep publishing here until those
     * installs age out — see TODO.md.
     */
    const val LEGACY_CDN_TRENDING =
        "https://cdn.jsdelivr.net/gh/Darkmintis/GitStore@main/cached-data/trending"
}

fun Context.openWebUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
    if (this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}
