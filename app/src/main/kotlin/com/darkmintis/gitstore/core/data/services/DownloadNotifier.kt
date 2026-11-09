package com.darkmintis.gitstore.core.data.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.darkmintis.gitstore.MainActivity
import com.darkmintis.gitstore.R
import com.darkmintis.gitstore.core.presentation.utils.DeepLinkHandler

/**
 * App-owned download notifications so taps can deep-link into repo details.
 * DownloadManager's system tray entry cannot take a custom PendingIntent.
 */
class DownloadNotifier(
    private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_downloads),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_channel_downloads_desc)
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    fun canNotify(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return true
    }

    // canNotify() performs the POST_NOTIFICATIONS check; lint cannot see across methods.
    @SuppressLint("MissingPermission")
    fun notifyProgress(
        fileName: String,
        title: String,
        percent: Int?,
        repoId: Long?,
    ) {
        if (!canNotify()) return
        val builder = baseBuilder(title, repoId)
            .setContentText(
                if (percent != null) {
                    context.getString(R.string.notification_downloading_percent, percent)
                } else {
                    context.getString(R.string.downloading)
                }
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent ?: 0, percent == null)

        runCatching { manager.notify(notificationId(fileName), builder.build()) }
    }

    @SuppressLint("MissingPermission")
    fun notifyComplete(
        fileName: String,
        title: String,
        repoId: Long?,
    ) {
        if (!canNotify()) return
        val builder = baseBuilder(title, repoId)
            .setContentText(context.getString(R.string.notification_download_complete))
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        runCatching { manager.notify(notificationId(fileName), builder.build()) }
    }

    fun cancel(fileName: String) {
        manager.cancel(notificationId(fileName))
    }

    private fun baseBuilder(title: String, repoId: Long?): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setContentTitle(title)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPendingIntent(repoId))
            .setPriority(NotificationCompat.PRIORITY_LOW)

    private fun contentPendingIntent(repoId: Long?): PendingIntent {
        val uri = if (repoId != null) {
            DeepLinkHandler.repoUri(repoId)
        } else {
            DeepLinkHandler.downloadsUri()
        }
        val intent = Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val requestCode = repoId?.toInt() ?: DOWNLOADS_REQUEST_CODE
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationId(fileName: String): Int = fileName.hashCode()

    private companion object {
        const val CHANNEL_ID = "gitstore_downloads"
        const val DOWNLOADS_REQUEST_CODE = 0x6773_646c // "gsdl"
    }
}
