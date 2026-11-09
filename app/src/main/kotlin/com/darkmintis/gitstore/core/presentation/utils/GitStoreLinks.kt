package com.darkmintis.gitstore.core.presentation.utils

import android.app.Activity
import android.content.Intent
import android.content.Context
import androidx.core.net.toUri

object GitStoreLinks {
    const val REPOSITORY = "https://github.com/Darkmintis/GitStore"
    const val DEVELOPER_SITE = "https://darkmintis.dev"
    const val NEW_BUG_REPORT =
        "https://github.com/Darkmintis/GitStore/issues/new?template=bug_report.yml"
}

fun Context.openWebUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
    if (this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}
