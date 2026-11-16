package com.darkmintis.gitstore.feature.download_manager.presentation

/**
 * Settings self-updates share the Downloads folder. Hide / drop those rows —
 * Git Store updates are not catalog downloads.
 *
 * Settings names files `GitStore-<tag>.apk` (see SettingsViewModel).
 */
internal fun isGitStoreSelfUpdateDownload(
    fileName: String,
    apkPackageName: String?,
    ownPackageName: String,
): Boolean {
    if (apkPackageName == ownPackageName) return true
    val name = fileName.lowercase()
    return name.startsWith("gitstore-") && name.endsWith(".apk")
}
