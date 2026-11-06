package com.darkmintis.gitstore.feature.details.presentation.components

import com.darkmintis.gitstore.R
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.darkmintis.gitstore.core.data.local.db.entities.InstalledApp
import com.darkmintis.gitstore.core.domain.model.GithubRelease
import com.darkmintis.gitstore.core.domain.model.GithubRepoSummary
import com.darkmintis.gitstore.core.domain.model.GithubUserProfile
import com.darkmintis.gitstore.feature.details.presentation.DownloadStage

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppHeader(
    author: GithubUserProfile?,
    repository: GithubRepoSummary,
    release: GithubRelease?,
    installedApp: InstalledApp?,
    modifier: Modifier = Modifier,
    downloadStage: DownloadStage = DownloadStage.IDLE,
    downloadProgress: Int? = null
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (downloadProgress ?: 0) / 100f,
        animationSpec = tween(durationMillis = 500),
        label = "avatar_progress_animation"
    )

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.Top
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(100.dp)
            ) {
                AsyncImage(
                    model = author?.avatarUrl,
                    contentDescription = author?.login,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                )

                if (downloadStage != DownloadStage.IDLE) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(100.dp)
                    ) {
                        when (downloadStage) {
                            DownloadStage.DOWNLOADING -> {
                                CircularProgressIndicator(
                                    progress = { 1f },
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    strokeWidth = 4.dp,
                                )

                                CircularProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 4.dp,
                                    strokeCap = StrokeCap.Round
                                )
                            }

                            DownloadStage.VERIFYING, DownloadStage.INSTALLING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 4.dp,
                                    strokeCap = StrokeCap.Round
                                )
                            }

                            else -> { }
                        }
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = repository.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Text(
                    text = stringResource(R.string.by_author, author?.login.toString()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(Modifier.height(8.dp))

                if (installedApp != null) {
                    InstallStatusBadge(
                        isUpdateAvailable = installedApp.isUpdateAvailable,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    release?.tagName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }

                    if (installedApp != null && installedApp.installedVersion != release?.tagName) {
                        Text(
                            text = stringResource(R.string.installed_version, installedApp.installedVersion),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        val translationService = koinInject<TranslationService>()
        val rawDescription = repository.description
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

        if (rawDescription != null) {
            Column {
                Text(
                    text = if (showingTranslation) translatedDescription!! else rawDescription,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (needsTranslation) {
                    Spacer(modifier = Modifier.height(4.dp))
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
        } else {
            Text(
                text = stringResource(R.string.no_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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



