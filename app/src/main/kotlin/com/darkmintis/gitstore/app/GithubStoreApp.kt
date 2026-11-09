package com.darkmintis.gitstore.app

import android.app.Application
import com.darkmintis.gitstore.app.di.initKoin
import com.darkmintis.gitstore.core.domain.repository.ThemesRepository
import com.darkmintis.gitstore.core.presentation.utils.applyAppNightMode
import kotlinx.coroutines.flow.first
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
    }
}
