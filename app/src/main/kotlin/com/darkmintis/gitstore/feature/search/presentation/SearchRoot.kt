package com.darkmintis.gitstore.feature.search.presentation

import com.darkmintis.gitstore.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle






import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import com.darkmintis.gitstore.core.domain.model.GithubRepoSummary
import com.darkmintis.gitstore.core.presentation.components.EmptyState
import com.darkmintis.gitstore.core.presentation.components.LoadingState
import com.darkmintis.gitstore.core.presentation.components.OfflineAwareErrorState
import com.darkmintis.gitstore.core.presentation.components.RepositoryCard
import com.darkmintis.gitstore.core.presentation.theme.GithubStoreTheme
import com.darkmintis.gitstore.feature.search.domain.model.ProgrammingLanguage
import com.darkmintis.gitstore.feature.search.domain.model.SortBy
import com.darkmintis.gitstore.feature.search.presentation.components.LanguageFilterBottomSheet
import com.darkmintis.gitstore.feature.search.presentation.components.SortByBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchRoot(
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (GithubRepoSummary) -> Unit,
    onNavigateToDeveloperProfile: (username: String) -> Unit,
    viewModel: SearchViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.isLanguageSheetVisible) {
        LanguageFilterBottomSheet(
            selectedLanguage = state.selectedLanguage,
            onLanguageSelected = { language ->
                viewModel.onAction(SearchAction.OnLanguageSelected(language))
            },
            onDismissRequest = {
                viewModel.onAction(SearchAction.OnToggleLanguageSheetVisibility)
            }
        )
    }

    if (state.isSortSheetVisible) {
        SortByBottomSheet(
            sortByOptions = SortBy.entries,
            selectedSortBy = state.selectedSortBy,
            onSortBySelected = { sortBy ->
                viewModel.onAction(SearchAction.OnSortBySelected(sortBy))
            },
            onDismissRequest = {
                viewModel.onAction(SearchAction.OnToggleSortSheetVisibility)
            }
        )
    }

    SearchScreen(
        state = state,
        onAction = { action ->
            when (action) {
                is SearchAction.OnRepositoryClick -> {
                    onNavigateToDetails(action.repository)
                }

                is SearchAction.OnRepositoryDeveloperClick -> {
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
fun SearchScreen(
    state: SearchState,
    onAction: (SearchAction) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyStaggeredGridState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo

            if (totalItems == 0 ||
                state.isLoadingMore ||
                state.isLoading ||
                !state.hasMorePages) {
                return@derivedStateOf false
            }

            val lastVisibleItem = visibleItems.lastOrNull() ?: return@derivedStateOf false
            val viewportEndOffset = layoutInfo.viewportEndOffset

            val hasEmptySpaceAtBottom = lastVisibleItem.index == totalItems - 1 &&
                    lastVisibleItem.offset.y + lastVisibleItem.size.height < viewportEndOffset

            val threshold = (totalItems * 0.8f).toInt()
            val isNearEnd = lastVisibleItem.index >= threshold

            isNearEnd || hasEmptySpaceAtBottom
        }
    }

    val currentOnAction by rememberUpdatedState(onAction)

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            currentOnAction(SearchAction.LoadMore)
        }
    }

    LaunchedEffect(listState.layoutInfo.totalItemsCount, listState.layoutInfo.viewportEndOffset) {
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        val lastVisible = visibleItems.lastOrNull()

        if (lastVisible != null &&
            layoutInfo.totalItemsCount > 0 &&
            !state.isLoadingMore &&
            !state.isLoading &&
            state.hasMorePages) {

            val hasEmptySpace = lastVisible.index == layoutInfo.totalItemsCount - 1 &&
                    lastVisible.offset.y + lastVisible.size.height < layoutInfo.viewportEndOffset

            if (hasEmptySpace) {
                delay(100)
                currentOnAction(SearchAction.LoadMore)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (state.query.isEmpty()) {
            focusRequester.requestFocus()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                SearchTopbar(
                    onAction = onAction,
                    state = state,
                    focusRequester = focusRequester
                )
                // Language filter chip
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedLanguage != ProgrammingLanguage.All,
                            onClick = { onAction(SearchAction.OnToggleLanguageSheetVisibility) },
                            label = {
                                Text(
                                    text = if (state.selectedLanguage == ProgrammingLanguage.All) {
                                        stringResource(R.string.language_all)
                                    } else {
                                        stringResource(state.selectedLanguage.label())
                                    }
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }

                    item {
                        FilterChip(
                            selected = state.selectedSortBy != SortBy.BestMatch,
                            onClick = { onAction(SearchAction.OnToggleSortSheetVisibility) },
                            label = { Text(stringResource(state.selectedSortBy.label())) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 8.dp)
        ) {
            if (state.totalCount != null) {
                Text(
                    text = stringResource(
                        R.string.results_found,
                        state.totalCount
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    state.isLoading && state.repositories.isEmpty() -> {
                        LoadingState(modifier = Modifier.imePadding())
                    }

                    state.errorMessage != null && state.repositories.isEmpty() -> {
                        OfflineAwareErrorState(
                            title = stringResource(R.string.search_failed),
                            message = state.errorMessage!!,
                            onRetry = { onAction(SearchAction.Retry) }
                        )
                    }

                    !state.isLoading &&
                        state.errorMessage == null &&
                        state.repositories.isEmpty() &&
                        state.query.isBlank() -> {
                        EmptyState(
                            title = stringResource(R.string.search_idle_title),
                            message = stringResource(R.string.search_idle_message),
                            icon = Icons.Default.Search
                        )
                    }

                    !state.isLoading &&
                        state.errorMessage == null &&
                        state.repositories.isEmpty() &&
                        state.query.isNotBlank() -> {
                        EmptyState(
                            title = stringResource(R.string.no_repositories_found),
                            message = stringResource(R.string.search_try_different_filters),
                            icon = Icons.Default.Search
                        )
                    }
                }

                if (state.repositories.isNotEmpty()) {
                    LazyVerticalStaggeredGrid(
                        state = listState,
                        columns = StaggeredGridCells.Adaptive(350.dp),
                        verticalItemSpacing = 12.dp,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = state.repositories,
                            key = { it.repository.id },
                        ) { discoveryRepository ->
                            RepositoryCard(
                                discoveryRepository = discoveryRepository,
                                onClick = {
                                    onAction(SearchAction.OnRepositoryClick(discoveryRepository.repository))
                                },
                                onDeveloperClick = { username ->
                                    onAction(SearchAction.OnRepositoryDeveloperClick(username))
                                },
                                onToggleFavorite = {
                                    onAction(SearchAction.OnToggleFavorite(discoveryRepository.repository))
                                },
                                modifier = Modifier.animateItem()
                            )
                        }

                        item {
                            if (state.isLoadingMore) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchTopbar(
    onAction: (SearchAction) -> Unit,
    state: SearchState,
    focusRequester: FocusRequester
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextField(
            value = state.query,
            onValueChange = { value ->
                onAction(SearchAction.OnSearchChange(value))
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.search_repositories_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = { onAction(SearchAction.OnSearchImeClick) }
            ),
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            shape = CircleShape,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
        )
    }
}

@Preview
@Composable
private fun Preview() {
    GithubStoreTheme {
        SearchScreen(
            state = SearchState(),
            onAction = {}
        )
    }
}



