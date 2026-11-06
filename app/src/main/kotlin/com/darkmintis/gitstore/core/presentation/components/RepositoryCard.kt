package com.darkmintis.gitstore.core.presentation.components

import com.darkmintis.gitstore.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.koin.compose.koinInject
import com.darkmintis.gitstore.core.data.services.TranslationService
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.darkmintis.gitstore.core.domain.model.GithubRepoSummary
import com.darkmintis.gitstore.core.domain.model.GithubUser
import com.darkmintis.gitstore.core.presentation.model.DiscoveryRepository
import com.darkmintis.gitstore.core.presentation.theme.GithubStoreTheme
import com.darkmintis.gitstore.core.presentation.utils.formatUpdatedAt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RepositoryCard(
    discoveryRepository: DiscoveryRepository,
    onClick: () -> Unit,
    onDeveloperClick: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box {
            if (discoveryRepository.isFavourite) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.BottomStart)
                        .offset(x = (-32).dp, y = 32.dp)
                )
            }

            if (discoveryRepository.isStarred) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 32.dp, y = (-32).dp)
                )
            }

            // Favorite button - top right corner
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = stringResource(
                            if (discoveryRepository.isFavourite) {
                                R.string.remove_from_favourites
                            } else {
                                R.string.add_to_favourites
                            }
                        ),
                        tint = if (discoveryRepository.isFavourite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "${discoveryRepository.repository.stargazersCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row (
                        modifier = Modifier.clickable(onClick = {
                            onDeveloperClick(discoveryRepository.repository.owner.login )
                        }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AsyncImage(
                            model = discoveryRepository.repository.owner.avatarUrl,
                            contentDescription = discoveryRepository.repository.owner.login,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                        )

                        Text(
                            text = discoveryRepository.repository.owner.login,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = "/ ${discoveryRepository.repository.name}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = discoveryRepository.repository.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )

                val translationService = koinInject<TranslationService>()
                val rawDescription = discoveryRepository.repository.description
                val isAutoTranslateEnabled by translationService.isAutoTranslateEnabled.collectAsState()
                val currentTargetLang by translationService.targetLanguage.collectAsState()

                var translatedDescription by remember(rawDescription, currentTargetLang) { mutableStateOf<String?>(null) }
                var showOriginal by remember(rawDescription, currentTargetLang) { mutableStateOf(false) }
                var manualTranslateRequested by remember(rawDescription, currentTargetLang) { mutableStateOf(false) }
                var translationFailed by remember(rawDescription, currentTargetLang) { mutableStateOf(false) }

                val needsTranslation = remember(rawDescription, currentTargetLang) {
                    rawDescription != null && translationService.needsTranslation(rawDescription)
                }

                LaunchedEffect(rawDescription, currentTargetLang) {
                    translatedDescription = null
                    showOriginal = false
                    manualTranslateRequested = false
                    translationFailed = false
                }

                LaunchedEffect(rawDescription, currentTargetLang, isAutoTranslateEnabled, manualTranslateRequested) {
                    if (rawDescription == null || !needsTranslation) return@LaunchedEffect
                    if (!isAutoTranslateEnabled && !manualTranslateRequested) return@LaunchedEffect
                    val res = translationService.translate(rawDescription)
                    if (res != rawDescription) {
                        translatedDescription = res
                        translationFailed = false
                    } else {
                        translationFailed = true
                    }
                }

                val langLabel = translationService.getEffectiveLanguageCode().uppercase()
                val hasTranslation = translatedDescription != null && translatedDescription != rawDescription
                val showingTranslation = hasTranslation &&
                    !showOriginal &&
                    (isAutoTranslateEnabled || manualTranslateRequested)
                val displayedDesc = when {
                    showingTranslation -> translatedDescription
                    else -> rawDescription
                }

                displayedDesc?.let { desc ->
                    Column {
                        Text(
                            text = desc,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge,
                            softWrap = true
                        )
                        if (needsTranslation) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    hasTranslation && !showOriginal ->
                                        stringResource(R.string.translated_show_original, langLabel)
                                    hasTranslation && showOriginal ->
                                        stringResource(R.string.show_translation, langLabel)
                                    translationFailed ->
                                        stringResource(R.string.translation_unavailable)
                                    else ->
                                        stringResource(R.string.translate_with_language, langLabel)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    when {
                                        hasTranslation -> showOriginal = !showOriginal
                                        translationFailed -> {
                                            translationFailed = false
                                            manualTranslateRequested = true
                                        }
                                        else -> manualTranslateRequested = true
                                    }
                                }
                            )
                        }
                    }
                }

                if (discoveryRepository.isInstalled) {
                    Spacer(Modifier.height(12.dp))

                    InstallStatusBadge(
                        isUpdateAvailable = discoveryRepository.isUpdateAvailable
                    )
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GithubStoreButton(
                        text = stringResource(R.string.home_view_details),
                        onClick = onClick,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            uriHandler.openUri(discoveryRepository.repository.htmlUrl)
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_github),
                            contentDescription = stringResource(R.string.open_in_browser),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InstallStatusBadge(
    isUpdateAvailable: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isUpdateAvailable) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

    val textColor = if (isUpdateAvailable) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    val icon = if (isUpdateAvailable) {
        Icons.Filled.Upgrade
    } else {
        Icons.Filled.CheckCircle
    }

    val text = if (isUpdateAvailable) {
        stringResource(R.string.update_available)
    } else {
        stringResource(R.string.installed)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = textColor
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Preview
@Composable
fun RepositoryCardPreview() {
    GithubStoreTheme {
        RepositoryCard(
            discoveryRepository = DiscoveryRepository(
                repository = GithubRepoSummary(
                    id = 0L,
                    name = "Hello",
                    fullName = "JIFEOJEF",
                    owner = GithubUser(
                        id = 0L,
                        login = "Skydoves",
                        avatarUrl = "ewfew",
                        htmlUrl = "grgrre"
                    ),
                    description = "Hello wolrd Hello wolrd Hello wolrd Hello wolrd Hello wolrd",
                    htmlUrl = "",
                    stargazersCount = 20,
                    forksCount = 4,
                    language = "Kotlin",
                    topics = null,
                    releasesUrl = "",
                    updatedAt = "2025-12-01T12:00:00Z",
                    defaultBranch = ""
                ),
                isUpdateAvailable = true,
                isFavourite = true,
                isInstalled = true,
                isStarred = false
            ),
            onClick = { },
            onDeveloperClick = { },
            onToggleFavorite = { }
        )
    }
}



