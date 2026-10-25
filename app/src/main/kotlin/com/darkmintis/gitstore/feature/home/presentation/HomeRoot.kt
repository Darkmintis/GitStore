package com.darkmintis.gitstore.feature.home.presentation

import com.darkmintis.gitstore.R

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle










import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import com.darkmintis.gitstore.app.navigation.LocalBottomNavigationLiquid
import com.darkmintis.gitstore.core.presentation.components.GithubStoreButton
import com.darkmintis.gitstore.core.presentation.components.RepositoryCard
import com.darkmintis.gitstore.core.presentation.components.RetryErrorState
import com.darkmintis.gitstore.core.presentation.theme.GithubStoreTheme
import com.darkmintis.gitstore.feature.home.presentation.components.HomeFilterChips
import com.darkmintis.gitstore.feature.home.presentation.model.HomeCategory

@Composable
fun HomeRoot(
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToApps: () -> Unit,
    onNavigateToDetails: (com.darkmintis.gitstore.core.domain.model.GithubRepoSummary) -> Unit,
    onNavigateToDeveloperProfile: (username: String) -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    HomeScreen(
        state = state,
        onAction = { action ->
            when (action) {
                HomeAction.OnSearchClick -> {
                    onNavigateToSearch()
                }

                HomeAction.OnSettingsClick -> {
                    onNavigateToSettings()
                }

                HomeAction.OnAppsClick -> {
                    onNavigateToApps()
                }

                is HomeAction.OnRepositoryClick -> {
                    onNavigateToDetails(action.repo)
                }

                is HomeAction.OnRepositoryDeveloperClick -> {
                    onNavigateToDeveloperProfile(action.username)
                }

                else -> {
                    viewModel.onAction(action)
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    state: HomeState,
    onAction: (HomeAction) -> Unit,
) {
    val listState = rememberLazyStaggeredGridState()
    val liquidState = LocalBottomNavigationLiquid.current

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()

            totalItems > 0 &&
                    lastVisibleItem != null &&
                    lastVisibleItem.index >= (totalItems - 5) &&
                    !state.isLoadingMore &&
                    !state.isLoading &&
                    state.hasMorePages
        }
    }

    val currentOnAction by rememberUpdatedState(onAction)

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            currentOnAction(HomeAction.LoadMore)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(state, onAction)
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 8.dp)
                .liquefiable(liquidState)
        ) {
            FilterChips(state, onAction)

            if (state.isShowingCachedData && state.repos.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                CachedDataNotice(
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Box(Modifier.fillMaxSize()) {
                LoadingState(state)

                ErrorState(state, onAction)

                MainState(
                    state = state,
                    listState = listState,
                    onAction = onAction,
                    liquidState = liquidState
                )
            }
        }
    }
}

@Composable
private fun MainState(
    state: HomeState,
    listState: LazyStaggeredGridState,
    onAction: (HomeAction) -> Unit,
    liquidState: LiquidState
) {
    if (state.repos.isNotEmpty()) {
        LazyVerticalStaggeredGrid(
            state = listState,
            columns = StaggeredGridCells.Adaptive(350.dp),
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = state.repos,
                key = { it.repository.id },
                contentType = { "repo" }
            ) { homeRepo ->
                RepositoryCard(
                    discoveryRepository = homeRepo,
                    onClick = {
                        onAction(HomeAction.OnRepositoryClick(homeRepo.repository))
                    },
                    onDeveloperClick = { username ->
                        onAction(HomeAction.OnRepositoryDeveloperClick(username))
                    },
                    onToggleFavorite = {
                        onAction(HomeAction.OnToggleFavorite(homeRepo.repository))
                    },
                    modifier = Modifier
                        .animateItem()
                        .liquefiable(liquidState)
                )
            }

            if (state.isLoadingMore) {
                item(key = "loading_indicator") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = stringResource(R.string.home_loading_more),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (!state.hasMorePages && !state.isLoadingMore) {
                item(key = "end_message") {
                    Text(
                        text = stringResource(R.string.home_no_more_repositories),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

/**
 * Shown when the visible list came from disk. Browsing still works, but installs need a
 * connection, so the notice says exactly that instead of silently pretending all is well.
 */
@Composable
private fun CachedDataNotice(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.WifiOff,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onTertiaryContainer
        )

        Text(
            text = stringResource(R.string.showing_cached_trending),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingState(state: HomeState) {
    if (state.isLoading && state.repos.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularWavyProgressIndicator()

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.home_finding_repositories),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorState(
    state: HomeState,
    onAction: (HomeAction) -> Unit
) {
    if (state.errorMessage != null && state.repos.isEmpty()) {
        RetryErrorState(
            title = stringResource(R.string.home_failed_to_load_repositories),
            message = state.errorMessage,
            onRetry = { onAction(HomeAction.Retry) },
            retryText = stringResource(R.string.home_retry)
        )
    }
}

@Composable
private fun FilterChips(
    state: HomeState,
    onAction: (HomeAction) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primaryContainer, CircleShape
            )
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HomeCategory.entries.forEach { category ->
            HomeFilterChips(
                selectedCategory = state.currentCategory,
                category = category,
                onClick = {
                    onAction(HomeAction.SwitchCategory(category))
                }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TopAppBar(
    state: HomeState,
    onAction: (HomeAction) -> Unit
) {
    TopAppBar(
        navigationIcon = {
            Image(
                painter = painterResource(R.drawable.app_icon),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(32.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        },
        title = {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily(
                    androidx.compose.ui.text.font.Font(R.font.poppins_bold)
                ),
                modifier = Modifier.padding(start = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        actions = {
            IconButton(
                onClick = {
                    onAction(HomeAction.OnSettingsClick)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopbarActions(
    state: HomeState,
    onAction: (HomeAction) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            shapes = IconButtonDefaults.shapes(),
            onClick = {
                onAction(HomeAction.OnSearchClick)
            },
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onBackground
            )
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.search_repositories_hint),
                modifier = Modifier.size(24.dp)
            )
        }

        if (state.isAppsSectionVisible) {
            IconButton(
                shapes = IconButtonDefaults.shapes(),
                onClick = {
                    onAction(HomeAction.OnAppsClick)
                },
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = stringResource(R.string.installed_apps),
                        modifier = Modifier.size(24.dp)
                    )

                    if (state.isUpdateAvailable) {
                        Box(
                            Modifier
                                .size(16.dp)
                                .padding(top = 8.dp, end = 8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .align(Alignment.TopEnd)
                        )
                    }
                }
            }
        }

        IconButton(
            shapes = IconButtonDefaults.shapes(),
            onClick = {
                onAction(HomeAction.OnSettingsClick)
            },
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onBackground
            )
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.settings_title),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}


@Preview
@Composable
private fun Preview() {
    GithubStoreTheme {
        val liquidState = rememberLiquidState()

        CompositionLocalProvider(
            value = LocalBottomNavigationLiquid provides liquidState
        ) {
            HomeScreen(
                state = HomeState(),
                onAction = {}
            )
        }
    }
}



