package com.darkmintis.gitstore.feature.settings.presentation

import com.darkmintis.gitstore.R

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import com.darkmintis.gitstore.core.presentation.theme.GithubStoreTheme
import com.darkmintis.gitstore.core.presentation.components.GithubStoreTopBarTitle
import com.darkmintis.gitstore.core.presentation.utils.ObserveAsEvents
import com.darkmintis.gitstore.feature.settings.presentation.components.LogoutDialog
import com.darkmintis.gitstore.feature.settings.presentation.components.sections.about
import com.darkmintis.gitstore.feature.settings.presentation.components.sections.account
import com.darkmintis.gitstore.feature.settings.presentation.components.sections.appearance
import com.darkmintis.gitstore.feature.settings.presentation.components.sections.moreApps
import com.darkmintis.gitstore.feature.settings.presentation.components.sections.signInWithGitHub
import com.darkmintis.gitstore.feature.settings.presentation.components.sections.SHOW_SUPPORT_SECTION
import com.darkmintis.gitstore.feature.settings.presentation.components.sections.support
import com.darkmintis.gitstore.feature.settings.presentation.components.sections.translation

@Composable
fun SettingsRoot(
    onNavigateBack: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToStarredRepos: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val logoutSuccessMessage = stringResource(R.string.logout_success)
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            SettingsEvent.OnLogoutSuccessful -> {
                coroutineScope.launch {
                    snackbarState.showSnackbar(logoutSuccessMessage)

                    onNavigateBack()
                }
            }

            is SettingsEvent.OnLogoutError -> {
                coroutineScope.launch {
                    snackbarState.showSnackbar(event.message)
                }
            }
        }
    }

    SettingsScreen(
        state = state,
        onAction = { action ->
            when (action) {
                SettingsAction.OnNavigateBackClick -> {
                    onNavigateBack()
                }

                SettingsAction.OnGitHubSignInClick -> {
                    onNavigateToAuth()
                }

                SettingsAction.OnOpenStarredReposClick -> {
                    onNavigateToStarredRepos()
                }

                else -> {
                    viewModel.onAction(action)
                }
            }
        },
        snackbarState = snackbarState
    )

    if (state.isLogoutDialogVisible) {
        LogoutDialog(
            onDismissRequest = {
                viewModel.onAction(SettingsAction.OnLogoutDismiss)
            },
            onLogout = {
                viewModel.onAction(SettingsAction.OnLogoutConfirmClick)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
    snackbarState: SnackbarHostState
) {
    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarState)
        },
        topBar = {
            TopAppBar(
                isUserLoggedIn = state.isUserLoggedIn,
                onAction = onAction
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Sign in or Account section at the top
            if (state.isUserLoggedIn) {
                account(
                    state = state,
                    onAction = onAction
                )
            } else {
                signInWithGitHub(
                    onAction = onAction
                )
            }

            item {
                Spacer(Modifier.height(24.dp))
            }

            appearance(
                selectedThemeColor = state.selectedThemeColor,
                isAmoledThemeEnabled = state.isAmoledThemeEnabled,
                onAmoledThemeToggled = { enabled ->
                    onAction(SettingsAction.OnAmoledThemeToggled(enabled))
                },
                isDarkTheme = state.isDarkTheme,
                onDarkThemeChange = { isDark ->
                    onAction(SettingsAction.OnDarkThemeChange(isDark))
                },
                onThemeColorSelected = { theme ->
                    onAction(SettingsAction.OnThemeColorSelected(theme))
                }
            )

            item {
                Spacer(Modifier.height(24.dp))
            }

            translation(
                translationLanguage = state.translationLanguage,
                isAutoTranslateEnabled = state.isAutoTranslateEnabled,
                onAutoTranslateToggled = { enabled ->
                    onAction(SettingsAction.OnAutoTranslateToggled(enabled))
                },
                onLanguageSelected = { language ->
                    onAction(SettingsAction.OnTranslationLanguageSelected(language))
                }
            )

            item {
                Spacer(Modifier.height(24.dp))
            }

            moreApps(
                onAction = onAction
            )

            if (SHOW_SUPPORT_SECTION) {
                item {
                    Spacer(Modifier.height(24.dp))
                }

                support(
                    onAction = onAction
                )
            }

            item {
                Spacer(Modifier.height(24.dp))
            }

            about(
                state = state,
                onAction = onAction
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopAppBar(
    isUserLoggedIn: Boolean,
    onAction: (SettingsAction) -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(
                onClick = {
                    onAction(SettingsAction.OnNavigateBackClick)
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.navigate_back),
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            GithubStoreTopBarTitle(
                text = stringResource(R.string.settings_title)
            )
        },
        actions = {
            if (isUserLoggedIn) {
                IconButton(
                    onClick = {
                        onAction(SettingsAction.OnLogoutClick)
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = stringResource(R.string.logout),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}

@Preview
@Composable
private fun Preview() {
    GithubStoreTheme {
        SettingsScreen(
            state = SettingsState(),
            onAction = {},
            snackbarState = SnackbarHostState()
        )
    }
}
