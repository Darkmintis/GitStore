package com.darkmintis.gitstore.feature.settings.presentation

import com.darkmintis.gitstore.R

import android.app.Application
import com.darkmintis.gitstore.core.presentation.utils.AndroidStringProvider
import com.darkmintis.gitstore.core.presentation.utils.ErrorMapper
import com.darkmintis.gitstore.BuildConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.darkmintis.gitstore.core.data.services.Downloader
import com.darkmintis.gitstore.core.data.services.Installer
import com.darkmintis.gitstore.core.domain.repository.ThemesRepository
import com.darkmintis.gitstore.core.presentation.utils.BrowserHelper
import com.darkmintis.gitstore.core.presentation.utils.GitStoreLinks
import com.darkmintis.gitstore.feature.settings.domain.repository.SettingsRepository
import com.darkmintis.gitstore.feature.settings.presentation.SettingsEvent.*
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import java.net.HttpURLConnection
import java.net.URL

private const val LATEST_RELEASE_API_URL = "https://api.github.com/repos/Darkmintis/GitStore/releases/latest"

class SettingsViewModel(
    private val browserHelper: BrowserHelper,
    private val themesRepository: ThemesRepository,
    private val settingsRepository: SettingsRepository,
    private val downloader: Downloader,
    private val installer: Installer,
    private val application: Application,
    private val tokenDataSource: com.darkmintis.gitstore.core.data.data_source.TokenDataSource,
    private val starredRepository: com.darkmintis.gitstore.core.domain.repository.StarredRepository,
    private val translationRepository: com.darkmintis.gitstore.core.domain.repository.TranslationRepository
) : ViewModel() {

    private val stringProvider = AndroidStringProvider(application)


    private var hasLoadedInitialData = false
    private var currentDownloadJob: Job? = null

    private val _state = MutableStateFlow(SettingsState())
    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                loadCurrentTheme()
                loadTranslationSettings()
                collectIsUserLoggedIn()
                checkForGitStoreUpdate()

                hasLoadedInitialData = true
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = SettingsState()
        )

    private val _events = Channel<SettingsEvent>()
    val events = _events.receiveAsFlow()

    private fun collectIsUserLoggedIn() {
        viewModelScope.launch {
            settingsRepository.isUserLoggedIn
                .collect { isLoggedIn ->
                    _state.update { it.copy(isUserLoggedIn = isLoggedIn) }
                    if (isLoggedIn) {
                        fetchUserProfile()
                        observeStarredReposCount()
                    } else {
                        _state.update { it.copy(userLogin = null, userAvatarUrl = null, starredReposCount = 0) }
                    }
                }
        }
    }

    private fun observeStarredReposCount() {
        viewModelScope.launch {
            starredRepository.getAllStarred()
                .flowOn(Dispatchers.IO)
                .collect { repos ->
                    _state.update { it.copy(starredReposCount = repos.size) }
                }
        }
    }

    private fun fetchUserProfile() {
        viewModelScope.launch {
            val profile = fetchGitHubUserProfile() ?: return@launch
            _state.update {
                it.copy(
                    userLogin = profile.login,
                    userAvatarUrl = profile.avatarUrl
                )
            }
        }
    }

    private suspend fun fetchGitHubUserProfile(): GitHubUser? = withContext(Dispatchers.IO) {
        runCatching {
            val token = tokenDataSource.current()?.accessToken ?: return@runCatching null
            val connection = (URL("https://api.github.com/user").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "GitStore-Android")
            }

            if (connection.responseCode !in 200..299) return@runCatching null

            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val root = Json.parseToJsonElement(payload).jsonObject

            GitHubUser(
                login = root["login"]?.jsonPrimitive?.content.orEmpty(),
                avatarUrl = root["avatar_url"]?.jsonPrimitive?.content.orEmpty()
            )
        }.getOrNull()
    }

    private fun loadCurrentTheme() {
        viewModelScope.launch {
            themesRepository.getThemeColor().collect { theme ->
                _state.update {
                    it.copy(selectedThemeColor = theme)
                }
            }
        }

        viewModelScope.launch {
            themesRepository.getAmoledTheme().collect { isAmoled ->
                _state.update {
                    it.copy(isAmoledThemeEnabled = isAmoled)
                }
            }
        }

        viewModelScope.launch {
            themesRepository.getIsDarkTheme().collect { isDarkTheme ->
                _state.update {
                    it.copy(isDarkTheme = isDarkTheme)
                }
            }
        }
    }

    private fun loadTranslationSettings() {
        viewModelScope.launch {
            translationRepository.getTargetLanguage().collect { lang ->
                _state.update {
                    it.copy(translationLanguage = lang)
                }
            }
        }

        viewModelScope.launch {
            translationRepository.isAutoTranslateEnabled().collect { enabled ->
                _state.update {
                    it.copy(isAutoTranslateEnabled = enabled)
                }
            }
        }
    }

    private fun checkForGitStoreUpdate() {
        viewModelScope.launch {
            if (_state.value.isCheckingGitStoreUpdate) return@launch
            _state.update {
                it.copy(
                    isCheckingGitStoreUpdate = true,
                    gitStoreUpdateErrorMessage = null
                )
            }

            val release = fetchLatestGitStoreRelease()
            if (release == null) {
                _state.update {
                    it.copy(
                        isCheckingGitStoreUpdate = false,
                        isGitStoreUpdateAvailable = false,
                        gitStoreUpdateErrorMessage = application.getString(R.string.unable_to_check_for_updates)
                    )
                }
                return@launch
            }

            val currentVersion = BuildConfig.VERSION_NAME
            val hasUpdate = compareVersions(release.versionTag, currentVersion) > 0

            _state.update {
                it.copy(
                    isCheckingGitStoreUpdate = false,
                    isGitStoreUpdateAvailable = hasUpdate,
                    latestGitStoreVersion = release.versionTag,
                    latestGitStoreDownloadUrl = release.downloadUrl,
                    gitStoreUpdateErrorMessage = null
                )
            }
        }
    }

    private suspend fun fetchLatestGitStoreRelease(): GitStoreRelease? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(LATEST_RELEASE_API_URL).openConnection() as HttpURLConnection).apply {
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

            GitStoreRelease(
                versionTag = tagName,
                downloadUrl = downloadUrl
            )
        }.getOrNull()
    }

    private fun compareVersions(remote: String, local: String): Int {
        val remoteParts = versionParts(remote)
        val localParts = versionParts(local)
        val maxSize = maxOf(remoteParts.size, localParts.size)

        for (i in 0 until maxSize) {
            val remotePart = remoteParts.getOrElse(i) { 0 }
            val localPart = localParts.getOrElse(i) { 0 }

            if (remotePart != localPart) {
                return remotePart.compareTo(localPart)
            }
        }

        return 0
    }

    private fun versionParts(version: String): List<Int> {
        return Regex("\\d+")
            .findAll(version)
            .map { it.value.toIntOrNull() ?: 0 }
            .toList()
    }

    private data class GitStoreRelease(
        val versionTag: String,
        val downloadUrl: String
    )

    private data class GitHubUser(
        val login: String,
        val avatarUrl: String
    )

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.OnHelpClick -> {
                browserHelper.openUrl(
                    url = GitStoreLinks.NEW_BUG_REPORT
                )
            }

            SettingsAction.OnGitHubSignInClick -> {
                // Handled in composable - navigation to AuthenticationScreen
            }

            SettingsAction.OnOpenStarredReposClick -> {
                // Handled in composable - navigation to StarredReposScreen
            }

            is SettingsAction.OnBrowserOpen -> {
                browserHelper.openUrl(
                    url = action.url,
                    useChooser = action.useChooser
                )
            }

            is SettingsAction.OnThemeColorSelected -> {
                viewModelScope.launch {
                    themesRepository.setThemeColor(action.themeColor)
                }
            }

            is SettingsAction.OnAmoledThemeToggled -> {
                viewModelScope.launch {
                    themesRepository.setAmoledTheme(action.enabled)
                }
            }

            SettingsAction.OnLogoutClick -> {
                _state.update {
                    it.copy(
                        isLogoutDialogVisible = true
                    )
                }
            }

            SettingsAction.OnLogoutConfirmClick -> {
                viewModelScope.launch {
                    runCatching {
                        settingsRepository.logout()
                    }.onSuccess {
                        _state.update { it.copy(isLogoutDialogVisible = false) }
                        _events.send(SettingsEvent.OnLogoutSuccessful)
                    }.onFailure { error ->
                        _state.update { it.copy(isLogoutDialogVisible = false) }
                        _events.send(
                            OnLogoutError(
                                ErrorMapper.message(error, stringProvider, R.string.error_logout_failed)
                            )
                        )
                    }
                }
            }

            SettingsAction.OnLogoutDismiss -> {
                _state.update {
                    it.copy(
                        isLogoutDialogVisible = false
                    )
                }
            }

            SettingsAction.OnNavigateBackClick -> {
                /* Handed in composable */
            }

            is SettingsAction.OnDarkThemeChange -> {
                viewModelScope.launch {
                    themesRepository.setDarkTheme(action.isDarkTheme)
                }
            }

            is SettingsAction.OnTranslationLanguageSelected -> {
                viewModelScope.launch {
                    translationRepository.setTargetLanguage(action.language)
                }
            }

            is SettingsAction.OnAutoTranslateToggled -> {
                viewModelScope.launch {
                    translationRepository.setAutoTranslateEnabled(action.enabled)
                }
            }

            SettingsAction.OnUpdateGitStoreClick -> {
                val url = _state.value.latestGitStoreDownloadUrl
                if (url != null) {
                    downloadAndInstallGitStoreUpdate(url)
                }
            }
        }
    }

    private fun downloadAndInstallGitStoreUpdate(url: String) {
        val versionTag = _state.value.latestGitStoreVersion ?: "update"
        val assetName = "GitStore-$versionTag.apk"

        currentDownloadJob?.cancel()
        currentDownloadJob = viewModelScope.launch {
            try {
                _state.update {
                    it.copy(
                        isDownloadingUpdate = true,
                        updateDownloadProgress = null,
                        updateDownloadError = null
                    )
                }

                installer.ensurePermissionsOrThrow("apk")

                downloader.download(url, assetName).collect { progress ->
                    _state.update {
                        it.copy(updateDownloadProgress = progress.percent)
                    }
                }

                val filePath = downloader.getDownloadedFilePath(assetName)
                    ?: throw IllegalStateException("Downloaded file not found")

                installer.install(filePath, "apk")

                _state.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        updateDownloadProgress = null
                    )
                }
            } catch (t: kotlinx.coroutines.CancellationException) {
                throw t
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        updateDownloadProgress = null,
                        updateDownloadError = ErrorMapper.message(t, stringProvider)
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentDownloadJob?.cancel()
        currentDownloadJob = null
    }

}


