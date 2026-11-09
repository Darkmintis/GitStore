package com.darkmintis.gitstore.core.presentation.utils

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** In-app destinations from `gitstore://` intents (notifications, external links). */
sealed interface DeepLink {
    data class Repo(val repositoryId: Long) : DeepLink
    data object Downloads : DeepLink
}

class DeepLinkHandler {
    /**
     * Buffered so a notification tap that cold-starts the app is not dropped before Compose
     * starts collecting; CONFLATED keeps only the most recent destination.
     */
    private val channel = Channel<DeepLink>(capacity = Channel.CONFLATED)
    val links: Flow<DeepLink> = channel.receiveAsFlow()

    fun offer(link: DeepLink) {
        channel.trySend(link)
    }

    fun offerFromIntent(intent: Intent?) {
        parse(intent)?.let(::offer)
    }

    companion object {
        const val SCHEME = "gitstore"
        const val HOST_REPO = "repo"
        const val HOST_DOWNLOADS = "downloads"

        fun repoUri(repositoryId: Long): Uri =
            Uri.Builder()
                .scheme(SCHEME)
                .authority(HOST_REPO)
                .appendPath(repositoryId.toString())
                .build()

        fun downloadsUri(): Uri =
            Uri.Builder()
                .scheme(SCHEME)
                .authority(HOST_DOWNLOADS)
                .build()

        fun parse(intent: Intent?): DeepLink? {
            val data = intent?.data ?: return null
            return parse(data.scheme, data.host, data.lastPathSegment)
        }

        /** Pure parse for tests / non-Android call sites. */
        fun parse(scheme: String?, host: String?, lastPathSegment: String?): DeepLink? {
            if (scheme != SCHEME) return null
            return when (host) {
                HOST_REPO -> lastPathSegment?.toLongOrNull()?.let(DeepLink::Repo)
                HOST_DOWNLOADS -> DeepLink.Downloads
                else -> null
            }
        }
    }
}
