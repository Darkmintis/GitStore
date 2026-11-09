package com.darkmintis.gitstore.app

import android.app.Application
import com.darkmintis.gitstore.app.di.initKoin
import com.darkmintis.gitstore.core.data.services.Downloader
import com.darkmintis.gitstore.core.domain.repository.ThemesRepository
import com.darkmintis.gitstore.core.presentation.utils.applyAppNightMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext

class GithubStoreApp : Application() {

    override fun onCreate() {
        super.onCreate()

        initKoin {
            androidContext(this@GithubStoreApp)
        }

        // Apply night mode before any Activity so the first frame isn't a light flash.
        runBlocking {
            val isDark = get<ThemesRepository>().getIsDarkTheme().first()
            applyAppNightMode(isDark)
        }

        // Reattach to downloads that survived a process kill. Without this the app-owned
        // notification freezes at the last percentage while DownloadManager keeps going.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { get<Downloader>().reconcileActiveDownloads() }
        }
    }
}
