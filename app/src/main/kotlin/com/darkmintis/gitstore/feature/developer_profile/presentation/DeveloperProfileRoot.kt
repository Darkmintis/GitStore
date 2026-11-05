package com.darkmintis.gitstore.feature.developer_profile.presentation

import com.darkmintis.gitstore.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle











import androidx.compose.ui.res.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.darkmintis.gitstore.core.presentation.components.GithubStoreBodyText
import com.darkmintis.gitstore.core.presentation.components.GithubStoreTopBarTitle
import com.darkmintis.gitstore.core.presentation.components.GithubStoreTitleText
import com.darkmintis.gitstore.core.presentation.components.OfflineAwareErrorState
import com.darkmintis.gitstore.feature.developer_profile.domain.model.RepoFilterType
import com.darkmintis.gitstore.feature.developer_profile.presentation.components.DeveloperRepoItem
import com.darkmintis.gitstore.feature.developer_profile.presentation.components.FilterSortControls
import com.darkmintis.gitstore.feature.developer_profile.presentation.components.ProfileInfoCard
import com.darkmintis.gitstore.feature.developer_profile.presentation.components.StatsRow

@Composable
fun DeveloperProfileRoot(
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (repoId: Long) -> Unit,
    viewModel: DeveloperProfileViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    DeveloperProfileScreen(
        state = state,
        onAction = { action ->
            when (action) {
                DeveloperProfileAction.OnNavigateBackClick -> onNavigateBack()
                is DeveloperProfileAction.OnRepositoryClick -> onNavigateToDetails(action.repoId)
                is DeveloperProfileAction.OnOpenLink -> {
                    val url = action.url.trim()
                    val allowed = url.startsWith("https://") || url.startsWith("http://")
                    if (allowed) uriHandler.openUri(url)
                }

                else -> viewModel.onAction(action)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeveloperProfileScreen(
    state: DeveloperProfileState,
    onAction: (DeveloperProfileAction) -> Unit,
) {
    Scaffold(
        topBar = {
            DevProfileTopbar(
                state = state,
                onAction = onAction
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                state.isLoading -> {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                state.errorMessage != null && state.profile == null -> {
                    ErrorContent(
                        message = state.errorMessage,
                        onRetry = { onAction(DeveloperProfileAction.OnRetry) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                state.profile != null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            ProfileInfoCard(
                                profile = state.profile,
                                onAction = onAction
                            )
                        }

                        item {
                            StatsRow(profile = state.profile)
                        }

                        item {
                            FilterSortControls(
                                currentFilter = state.currentFilter,
                                currentSort = state.currentSort,
                                searchQuery = state.searchQuery,
                                repoCount = state.filteredRepositories.size,
                                totalCount = state.repositories.size,
                                onAction = onAction
                            )
                        }

                        if (state.isLoadingRepos) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularWavyProgressIndicator()
                                }
                            }
                        } else if (state.filteredRepositories.isEmpty()) {
                            item {
                                EmptyReposContent(
                                    filter = state.currentFilter,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp)
                                )
                            }
                        } else {
                            items(
                                items = state.filteredRepositories,
                                key = { it.id }
                            ) { repo ->
                                DeveloperRepoItem(
                                    repository = repo,
                                    onItemClick = {
                                        onAction(DeveloperProfileAction.OnRepositoryClick(repo.id))
                                    },
                                    onToggleFavorite = {
                                        onAction(DeveloperProfileAction.OnToggleFavorite(repo))
                                    },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }
            }

            if (state.errorMessage != null && state.profile != null) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(
                            onClick = {
                                onAction(DeveloperProfileAction.OnRetry)
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.retry)
                            )
                        }
                    },
                    dismissAction = {
                        IconButton(
                            onClick = {
                                onAction(DeveloperProfileAction.OnDismissError)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.dismiss)
                            )
                        }
                    }
                ) {
                    Text(
                        text = state.errorMessage
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyReposContent(
    filter: RepoFilterType,
    modifier: Modifier = Modifier
) {
    val message = when (filter) {
        RepoFilterType.ALL -> stringResource(R.string.no_repositories_found)
        RepoFilterType.WITH_RELEASES -> stringResource(R.string.no_repos_with_releases)
        RepoFilterType.INSTALLED -> stringResource(R.string.no_installed_repos)
        RepoFilterType.FAVORITES -> stringResource(R.string.no_favorite_repos)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.FolderOff,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        GithubStoreBodyText(
            text = message,
            maxLines = 2,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DevProfileTopbar(
    state: DeveloperProfileState,
    onAction: (DeveloperProfileAction) -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(
                shapes = IconButtonDefaults.shapes(),
                onClick = { onAction(DeveloperProfileAction.OnNavigateBackClick) }
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
                text = state.username
            )
        },
        actions = {
            state.profile?.htmlUrl?.let {
                IconButton(
                    shapes = IconButtonDefaults.shapes(),
                    onClick = {
                        onAction(DeveloperProfileAction.OnOpenLink(it))
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = stringResource(R.string.open_repository),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    )
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    OfflineAwareErrorState(
        title = stringResource(R.string.failed_to_load_profile),
        message = message,
        onRetry = onRetry,
        modifier = modifier
    )
}



