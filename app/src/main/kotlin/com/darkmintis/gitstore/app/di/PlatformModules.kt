package com.darkmintis.gitstore.app.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import com.darkmintis.gitstore.core.data.services.AndroidApkInfoExtractor
import com.darkmintis.gitstore.core.data.services.AndroidLocalizationManager
import com.darkmintis.gitstore.core.data.services.AndroidPackageMonitor
import com.darkmintis.gitstore.core.data.services.PackageMonitor
import com.darkmintis.gitstore.core.data.local.data_store.createDataStore
import com.darkmintis.gitstore.core.data.local.db.AppDatabase
import com.darkmintis.gitstore.core.data.local.db.initDatabase
import com.darkmintis.gitstore.core.presentation.utils.AndroidAppLauncher
import com.darkmintis.gitstore.core.presentation.utils.AndroidBrowserHelper
import com.darkmintis.gitstore.core.presentation.utils.AndroidClipboardHelper
import com.darkmintis.gitstore.core.presentation.utils.AppLauncher
import com.darkmintis.gitstore.core.presentation.utils.BrowserHelper
import com.darkmintis.gitstore.core.presentation.utils.ClipboardHelper
import com.darkmintis.gitstore.feature.auth.data.AndroidTokenStore
import com.darkmintis.gitstore.feature.auth.data.TokenStore
import com.darkmintis.gitstore.core.data.services.AndroidDownloader
import com.darkmintis.gitstore.core.data.services.AndroidFileLocationsProvider
import com.darkmintis.gitstore.core.data.services.AndroidInstaller
import com.darkmintis.gitstore.core.data.services.DownloadNotifier
import com.darkmintis.gitstore.core.data.services.Downloader
import com.darkmintis.gitstore.core.data.services.FileLocationsProvider
import com.darkmintis.gitstore.core.data.services.Installer
import com.darkmintis.gitstore.core.data.services.LocalizationManager
import com.darkmintis.gitstore.core.presentation.utils.AndroidStringProvider
import com.darkmintis.gitstore.core.presentation.utils.ConnectivityObserver
import com.darkmintis.gitstore.core.presentation.utils.DeepLinkHandler
import com.darkmintis.gitstore.core.presentation.utils.StringProvider

val platformModule: Module = module {
    single<StringProvider> {
        AndroidStringProvider(androidContext())
    }

    single { DeepLinkHandler() }

    single { DownloadNotifier(androidContext()) }

    single<Downloader> {
        AndroidDownloader(
            context = get(),
            files = get(),
            notifier = get(),
        )
    }

    single<Installer> {
        AndroidInstaller(
            context = get(),
            apkInfoExtractor = AndroidApkInfoExtractor(androidContext())
        )
    }

    single<FileLocationsProvider> {
        AndroidFileLocationsProvider(context = get())
    }

    single<DataStore<Preferences>> {
        createDataStore(androidContext())
    }

    single<BrowserHelper> {
        AndroidBrowserHelper(androidContext())
    }

    single<ClipboardHelper> {
        AndroidClipboardHelper(androidContext())
    }

    single<TokenStore> {
        AndroidTokenStore(
            dataStore = get()
        )
    }

    single<AppDatabase> {
        initDatabase(androidContext())
    }

    single<PackageMonitor> {
        AndroidPackageMonitor(androidContext())
    }

    single<LocalizationManager> {
        AndroidLocalizationManager()
    }

    single<com.darkmintis.gitstore.core.domain.repository.TranslationRepository> {
        com.darkmintis.gitstore.core.data.repository.TranslationRepositoryImpl(
            preferences = get()
        )
    }

    single<com.darkmintis.gitstore.core.data.services.TranslationService> {
        com.darkmintis.gitstore.core.data.services.MyMemoryTranslationService(
            translationRepository = get(),
            localizationManager = get(),
            diskCache = com.darkmintis.gitstore.core.data.services.TranslationDiskCache(
                java.io.File(androidContext().cacheDir, "translation_cache.json")
            ),
        )
    }

    single<AppLauncher> {
        AndroidAppLauncher(androidContext())
    }

    single {
        ConnectivityObserver(androidContext())
    }
}

