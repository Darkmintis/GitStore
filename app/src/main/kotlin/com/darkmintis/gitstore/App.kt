package com.darkmintis.gitstore

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.savedstate.compose.serialization.serializers.SnapshotStateListSerializer
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject
import com.darkmintis.gitstore.app.app_state.components.RateLimitDialog
import com.darkmintis.gitstore.app.navigation.AppNavigation
import com.darkmintis.gitstore.app.navigation.GithubStoreGraph
import com.darkmintis.gitstore.app.navigation.replaceAll
import com.darkmintis.gitstore.core.presentation.components.OfflineBanner
import com.darkmintis.gitstore.core.presentation.theme.GithubStoreTheme
import com.darkmintis.gitstore.core.presentation.utils.ApplyAndroidSystemBars
import com.darkmintis.gitstore.core.presentation.utils.ConnectivityObserver

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.content.res.Configuration
import java.util.Locale
import com.darkmintis.gitstore.core.domain.model.TranslationLanguage
import com.darkmintis.gitstore.core.domain.repository.TranslationRepository

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Preview
fun App(
    onAuthenticationChecked: () -> Unit = { },
) {
    val viewModel: MainViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connectivityObserver: ConnectivityObserver = koinInject()
    val isOnline by connectivityObserver.isOnline.collectAsStateWithLifecycle(initialValue = true)
    val translationRepository: TranslationRepository = koinInject()
    val targetLanguage by translationRepository.getTargetLanguage().collectAsState(initial = TranslationLanguage.SYSTEM)

    val currentLocale = remember(targetLanguage) {
        if (targetLanguage == TranslationLanguage.SYSTEM) {
            Locale.getDefault()
        } else {
            val code = targetLanguage.code
            val parts = code.split("-", "_")
            if (parts.size > 1) {
                Locale(parts[0], parts[1])
            } else {
                Locale(parts[0])
            }
        }
    }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val localizedContext = remember(context, configuration, currentLocale) {
        val config = Configuration(configuration)
        config.setLocale(currentLocale)
        config.setLayoutDirection(currentLocale)
        context.createConfigurationContext(config)
    }

    val localizedConfiguration = remember(configuration, currentLocale) {
        Configuration(configuration).apply {
            setLocale(currentLocale)
            setLayoutDirection(currentLocale)
        }
    }

    val navBackStack = rememberSerializable(
        serializer = SnapshotStateListSerializer<GithubStoreGraph>()
    ) {
        mutableStateListOf(GithubStoreGraph.HomeScreen)
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration
    ) {
        GithubStoreTheme(
            appTheme = state.currentColorTheme,
            isAmoledTheme = state.isAmoledTheme,
            isDarkTheme = state.isDarkTheme ?: isSystemInDarkTheme()
        ) {
        ApplyAndroidSystemBars(state.isDarkTheme)

        LaunchedEffect(state.isCheckingAuth) {
            if (!state.isCheckingAuth) {
                onAuthenticationChecked()
            }
        }

        if (state.isCheckingAuth) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularWavyProgressIndicator()
            }

            return@GithubStoreTheme
        }

        Column(modifier = Modifier.fillMaxSize()) {
            OfflineBanner(isOffline = !isOnline)

            Box(modifier = Modifier.weight(1f)) {
                if (state.showRateLimitDialog && state.rateLimitInfo != null) {
                    RateLimitDialog(
                        rateLimitInfo = state.rateLimitInfo,
                        isAuthenticated = state.isLoggedIn,
                        onDismiss = {
                            viewModel.onAction(MainAction.DismissRateLimitDialog)
                        },
                        onSignIn = {
                            viewModel.onAction(MainAction.DismissRateLimitDialog)
                            navBackStack.replaceAll(GithubStoreGraph.AuthenticationScreen)
                        }
                    )
                }

                AppNavigation(
                    navBackStack = navBackStack
                )
            }
        }
    }
}
}


