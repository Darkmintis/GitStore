package com.darkmintis.gitstore.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

data class GitStoreRelease(
    val versionTag: String,
    val downloadUrl: String,
)

/** Shared GitStore self-update check (Settings + cold-start dialog). */
object GitStoreSelfUpdate {
    const val LATEST_RELEASE_API_URL =
        "https://api.github.com/repos/Darkmintis/GitStore/releases/latest"

    suspend fun fetchLatest(): GitStoreRelease? = withContext(Dispatchers.IO) {
        runCatching {
            val connection =
                (URL(LATEST_RELEASE_API_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "GitStore-Android")
                }

            if (connection.responseCode !in 200..299) return@runCatching null

            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val root = Json.parseToJsonElement(payload).jsonObject

            val tagName = root["tag_name"]?.jsonPrimitive?.content.orEmpty()
            if (tagName.isBlank()) return@runCatching null

            val htmlUrl = root["html_url"]?.jsonPrimitive?.content.orEmpty()
            val assetUrls = root["assets"]
                ?.jsonArray
                ?.mapNotNull { element ->
                    element.jsonObject["browser_download_url"]?.jsonPrimitive?.content
                }
                .orEmpty()
            val apkAssetUrl = assetUrls.firstOrNull { it.endsWith(".apk", ignoreCase = true) }
            val downloadUrl = apkAssetUrl ?: htmlUrl
            if (downloadUrl.isBlank()) return@runCatching null

            GitStoreRelease(versionTag = tagName, downloadUrl = downloadUrl)
        }.getOrNull()
    }

    fun compareVersions(remote: String, local: String): Int {
        val remoteParts = versionParts(remote)
        val localParts = versionParts(local)
        val maxSize = maxOf(remoteParts.size, localParts.size)

        for (i in 0 until maxSize) {
            val remotePart = remoteParts.getOrElse(i) { 0 }
            val localPart = localParts.getOrElse(i) { 0 }
            if (remotePart != localPart) return remotePart.compareTo(localPart)
        }
        return 0
    }

    private fun versionParts(version: String): List<Int> =
        Regex("\\d+")
            .findAll(version)
            .map { it.value.toIntOrNull() ?: 0 }
            .toList()
}
