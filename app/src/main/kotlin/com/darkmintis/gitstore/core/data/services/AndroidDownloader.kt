package com.darkmintis.gitstore.core.data.services

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import com.darkmintis.gitstore.feature.details.domain.model.DownloadProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Marker written next to an APK once DownloadManager reports success. */
private const val COMPLETE_SUFFIX = ".complete"

class AndroidDownloader(
    private val context: Context,
    private val files: FileLocationsProvider,
    private val notifier: DownloadNotifier = DownloadNotifier(context),
) : Downloader {

    private val downloadManager by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    private val activeDownloads = ConcurrentHashMap<String, Long>()
    /** repoId, displayName, avatarUrl persisted for each download. */
    private val metaCache = ConcurrentHashMap<String, Triple<Long?, String?, String?>>()
    private val notifyJobs = ConcurrentHashMap<String, Job>()
    private val notifyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun download(
        url: String,
        suggestedFileName: String?,
        repoId: Long?,
        displayName: String?,
        avatarUrl: String?,
    ): Flow<DownloadProgress> = flow {
        val dirPath = files.appDownloadsDir()
        val dir = File(dirPath)
        if (!dir.exists()) dir.mkdirs()

        val safeName = (suggestedFileName?.takeIf { it.isNotBlank() }
            ?: url.substringAfterLast('/').ifBlank { "asset-${UUID.randomUUID()}.apk" })
        val title = displayName?.takeIf { it.isNotBlank() } ?: safeName

        val tentativeDestination = File(dir, safeName)

        if (tentativeDestination.exists()) {
            Logger.d { "Deleting existing file before download: ${tentativeDestination.absolutePath}" }
            tentativeDestination.delete()
        }
        deleteCompleteMarker(dir, safeName)

        if (repoId != null || displayName != null || avatarUrl != null) {
            metaCache[safeName] = Triple(repoId, displayName, avatarUrl)
            writeMetaSidecar(dir, safeName, repoId, displayName, avatarUrl)
        }

        Logger.d { "Starting download: $url" }

        // Prefer our notification (deep-linkable). Fall back to DM's tray entry when we can't.
        val useAppNotification = notifier.canNotify()
        val request = DownloadManager.Request(url.toUri()).apply {
            setTitle(title)
            setDescription(safeName)
            setNotificationVisibility(
                if (useAppNotification) {
                    DownloadManager.Request.VISIBILITY_HIDDEN
                } else {
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                }
            )
            setDestinationInExternalFilesDir(context, null, "ghs_downloads/$safeName")
            setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
        }

        val downloadId = downloadManager.enqueue(request)
        activeDownloads[safeName] = downloadId

        if (useAppNotification) {
            startNotifyWatch(downloadId, safeName, title, repoId)
        }

        var finishedCleanly = false
        try {
            var isDone = false
            while (!isDone && currentCoroutineContext().isActive) {
                val cursor =
                    downloadManager.query(DownloadManager.Query().setFilterById(downloadId))

                try {
                    if (!cursor.moveToFirst()) {
                        throw IllegalStateException("Download ID not found: $downloadId")
                    }

                    val status =
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val downloaded =
                        cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total =
                        cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val percent = if (total > 0) ((downloaded * 100L) / total).toInt() else null

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val localUriStr = cursor.getString(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
                            )
                            val finalPath = localUriStr
                                ?.takeIf { it.isNotBlank() }
                                ?.let { Uri.parse(it).path }
                                ?: throw IllegalStateException("Invalid local URI: $localUriStr")

                            val file = File(finalPath)

                            var attempts = 0
                            while ((!file.exists() || file.length() == 0L) && attempts < 6) {
                                delay(500L)
                                attempts++
                                emit(DownloadProgress(downloaded, total, 100))
                            }
                            if (!file.exists() || file.length() == 0L) {
                                throw IllegalStateException("File not ready after timeout: $finalPath")
                            }

                            markComplete(safeName)
                            Logger.d { "Download complete: $finalPath" }
                            emit(DownloadProgress(downloaded, total, 100))
                            isDone = true
                            finishedCleanly = true
                        }

                        DownloadManager.STATUS_FAILED -> {
                            val reason =
                                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            Logger.e { "Download failed with reason: $reason" }
                            throw IllegalStateException("Download failed: $reason")
                        }

                        else -> emit(
                            DownloadProgress(
                                downloaded,
                                if (total >= 0) total else null,
                                percent
                            )
                        )
                    }
                } finally {
                    cursor.close()
                }

                if (!isDone) delay(500L)
            }
        } catch (e: CancellationException) {
            // Leave DM + notify watch so the transfer survives leaving the screen.
            Logger.d { "Download collector cancelled; keeping system download for $safeName" }
            throw e
        } catch (e: Exception) {
            activeDownloads.remove(safeName)
            notifyJobs.remove(safeName)?.cancel()
            notifier.cancel(safeName)
            throw e
        } finally {
            if (finishedCleanly) {
                activeDownloads.remove(safeName)
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun saveToFile(url: String, suggestedFileName: String?): String =
        withContext(Dispatchers.IO) {
            val safeName = (suggestedFileName?.takeIf { it.isNotBlank() }
                ?: url.substringAfterLast('/').ifBlank { "asset-${UUID.randomUUID()}.apk" })

            val file = File(files.appDownloadsDir(), safeName)

            if (file.exists()) {
                Logger.d { "Deleting existing file before download: ${file.absolutePath}" }
                file.delete()
            }

            Logger.d { "saveToFile downloading file..." }
            download(url, suggestedFileName).collect { }

            file.absolutePath
        }

    override suspend fun getDownloadedFilePath(fileName: String): String? =
        withContext(Dispatchers.IO) {
            val file = File(files.appDownloadsDir(), fileName)
            if (!file.exists() || file.length() == 0L) return@withContext null

            // DownloadManager writes into the destination while downloading, so existence
            // alone is not enough: the transfer must have finished successfully.
            val state = getFileState(fileName)
            if (state?.status == DownloadFileStatus.COMPLETED) file.absolutePath else null
        }

    override fun getActiveDownloadIds(): Map<String, Long> {
        return activeDownloads.toMap()
    }

    override suspend fun getFileState(fileName: String): DownloadFileState? =
        withContext(Dispatchers.IO) {
            val dir = File(files.appDownloadsDir())
            val file = File(dir, fileName)

            activeDownloads[fileName]?.let { id ->
                queryState(id)?.let { return@withContext it }
            }

            // DownloadManager keeps records after the in-memory map is gone (process death).
            findDownloadId(fileName)?.let { id ->
                queryState(id)?.let { return@withContext it }
            }

            if (!file.exists() || file.length() == 0L) return@withContext null
            if (hasCompleteMarker(dir, fileName)) {
                DownloadFileState(DownloadFileStatus.COMPLETED, 100)
            } else {
                null
            }
        }

    override fun getRepoIdForFile(fileName: String): Long? = metaFor(fileName).first

    override fun getDisplayNameForFile(fileName: String): String? = metaFor(fileName).second

    override fun getAvatarUrlForFile(fileName: String): String? = metaFor(fileName).third

    override suspend fun cancelDownload(fileName: String): Boolean =
        withContext(Dispatchers.IO) {
            var cancelled = false
            var deleted = false

            activeDownloads[fileName]?.let { downloadId: Long ->
                val removedCount = downloadManager.remove(downloadId)
                cancelled = removedCount > 0
                activeDownloads.remove(fileName)
            }

            notifyJobs.remove(fileName)?.cancel()
            notifier.cancel(fileName)

            val dir = File(files.appDownloadsDir())
            val file = File(dir, fileName)
            if (file.exists()) {
                deleted = file.delete()
            }
            deleteMetaSidecar(dir, fileName)
            deleteCompleteMarker(dir, fileName)
            metaCache.remove(fileName)

            cancelled || deleted
        }

    /** Survives Flow collector cancel so progress → complete still deep-links. */
    private fun startNotifyWatch(
        downloadId: Long,
        fileName: String,
        title: String,
        repoId: Long?,
    ) {
        notifyJobs.remove(fileName)?.cancel()
        notifyJobs[fileName] = notifyScope.launch {
            notifier.notifyProgress(fileName, title, percent = null, repoId = repoId)
            try {
                while (isActive) {
                    val cursor = downloadManager.query(
                        DownloadManager.Query().setFilterById(downloadId)
                    )
                    try {
                        if (!cursor.moveToFirst()) break
                        val status = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                        )
                        val downloaded = cursor.getLong(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        )
                        val total = cursor.getLong(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        )
                        val percent = if (total > 0) ((downloaded * 100L) / total).toInt() else null

                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                markComplete(fileName)
                                notifier.notifyComplete(fileName, title, repoId)
                                activeDownloads.remove(fileName)
                                return@launch
                            }

                            DownloadManager.STATUS_FAILED -> {
                                notifier.cancel(fileName)
                                activeDownloads.remove(fileName)
                                return@launch
                            }

                            else -> notifier.notifyProgress(fileName, title, percent, repoId)
                        }
                    } finally {
                        cursor.close()
                    }
                    delay(500L)
                }
            } finally {
                notifyJobs.remove(fileName, coroutineContext[Job])
            }
        }
    }

    private fun metaFor(fileName: String): Triple<Long?, String?, String?> {
        metaCache[fileName]?.let { return it }
        val meta = readMetaSidecar(File(files.appDownloadsDir()), fileName)
        metaCache[fileName] = meta
        return meta
    }

    private fun queryState(downloadId: Long): DownloadFileState? {
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        return try {
            if (!cursor.moveToFirst()) return null

            val status = cursor.getInt(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            )
            val downloaded = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            )
            val total = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            )
            val progress = if (total > 0) ((downloaded * 100L) / total).toInt() else null

            when (status) {
                DownloadManager.STATUS_SUCCESSFUL ->
                    DownloadFileState(DownloadFileStatus.COMPLETED, 100)
                DownloadManager.STATUS_FAILED ->
                    DownloadFileState(DownloadFileStatus.FAILED)
                else ->
                    DownloadFileState(DownloadFileStatus.IN_PROGRESS, progress)
            }
        } catch (_: Exception) {
            null
        } finally {
            cursor.close()
        }
    }

    /** Newest DownloadManager record for this filename, if one survives. */
    private fun findDownloadId(fileName: String): Long? {
        val cursor = downloadManager.query(DownloadManager.Query())
        return try {
            val idIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val uriIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
            var match: Long? = null
            while (cursor.moveToNext()) {
                val localUri = cursor.getString(uriIndex) ?: continue
                if (Uri.parse(localUri).lastPathSegment == fileName) {
                    val id = cursor.getLong(idIndex)
                    if (match == null || id > match) match = id
                }
            }
            match
        } catch (_: Exception) {
            null
        } finally {
            cursor.close()
        }
    }

    private fun markComplete(fileName: String) {
        runCatching {
            File(files.appDownloadsDir(), "$fileName$COMPLETE_SUFFIX").writeText("1")
        }
    }

    private fun hasCompleteMarker(dir: File, fileName: String): Boolean =
        File(dir, "$fileName$COMPLETE_SUFFIX").exists()

    private fun deleteCompleteMarker(dir: File, fileName: String) {
        runCatching { File(dir, "$fileName$COMPLETE_SUFFIX").delete() }
    }

    /** Sidecar format: `repoId|displayName|avatarUrl` (each part may be blank). */
    private fun writeMetaSidecar(
        dir: File,
        fileName: String,
        repoId: Long?,
        displayName: String?,
        avatarUrl: String?,
    ) {
        runCatching {
            File(dir, "$fileName.meta").writeText(
                "${repoId ?: ""}|${displayName.orEmpty()}|${avatarUrl.orEmpty()}"
            )
            // Drop legacy sidecar if present.
            File(dir, "$fileName.repoid").delete()
        }
    }

    private fun readMetaSidecar(
        dir: File,
        fileName: String,
    ): Triple<Long?, String?, String?> {
        val meta = File(dir, "$fileName.meta")
        if (meta.exists()) {
            return runCatching {
                val parts = runCatching { meta.readText().trim() }.getOrDefault("")
                    .split("|", limit = 3)
                val id = parts.getOrNull(0)?.takeIf { it.isNotBlank() }?.toLongOrNull()
                val name = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                val avatar = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
                Triple(id, name, avatar)
            }.getOrElse { Triple(null, null, null) }
        }
        // Legacy .repoid-only sidecars from earlier builds.
        val legacyId = runCatching {
            File(dir, "$fileName.repoid").takeIf { it.exists() }
                ?.readText()?.trim()?.toLongOrNull()
        }.getOrNull()
        return Triple(legacyId, null, null)
    }

    private fun deleteMetaSidecar(dir: File, fileName: String) {
        runCatching { File(dir, "$fileName.meta").delete() }
        runCatching { File(dir, "$fileName.repoid").delete() }
    }
}
