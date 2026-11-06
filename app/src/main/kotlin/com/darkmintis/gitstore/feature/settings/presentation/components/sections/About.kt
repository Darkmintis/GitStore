package com.darkmintis.gitstore.feature.settings.presentation.components.sections

import com.darkmintis.gitstore.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp




import androidx.compose.ui.res.stringResource
import com.darkmintis.gitstore.core.presentation.utils.GitStoreLinks
import com.darkmintis.gitstore.feature.settings.presentation.SettingsAction
import com.darkmintis.gitstore.feature.settings.presentation.SettingsState
import com.darkmintis.gitstore.feature.settings.presentation.utils.getVersionName

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LazyListScope.about(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
) {
    item {
        Text(
            text = stringResource(R.string.section_about),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.outline,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp)
        )

        Spacer(Modifier.height(8.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            AboutItem(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.version),
                actions = {
                    Text(
                        text = getVersionName(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            )

            HorizontalDivider()

            AboutItem(
                icon = Icons.Filled.Upgrade,
                title = stringResource(R.string.updates),
                actions = {
                    when {
                        state.isCheckingGitStoreUpdate -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        state.isDownloadingUpdate -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                LinearProgressIndicator(
                                    progress = { (state.updateDownloadProgress ?: 0) / 100f },
                                    modifier = Modifier.width(80.dp)
                                )
                                Text(
                                    text = "${state.updateDownloadProgress ?: 0}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        state.isGitStoreUpdateAvailable -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.update_to_version, state.latestGitStoreVersion ?: ""),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                                TextButton(onClick = { onAction(SettingsAction.OnUpdateGitStoreClick) }) {
                                    Text(stringResource(R.string.update))
                                }
                            }
                        }
                        state.updateDownloadError != null || state.gitStoreUpdateErrorMessage != null -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = state.updateDownloadError ?: stringResource(R.string.update_check_failed),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        else -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.up_to_date),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }
            )

            HorizontalDivider()

            AboutLinkItem(
                icon = Icons.Filled.Star,
                title = stringResource(R.string.star_git_store),
                onClick = {
                    onAction(SettingsAction.OnBrowserOpen(url = GitStoreLinks.REPOSITORY))
                }
            )

            HorizontalDivider()

            AboutLinkItem(
                icon = Icons.Filled.Code,
                title = stringResource(R.string.view_source_code),
                onClick = {
                    onAction(SettingsAction.OnBrowserOpen(url = GitStoreLinks.REPOSITORY))
                }
            )

            HorizontalDivider()

            AboutLinkItem(
                icon = Icons.Filled.BugReport,
                title = stringResource(R.string.report_an_issue),
                onClick = {
                    onAction(SettingsAction.OnHelpClick)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AboutLinkItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    AboutItem(
        icon = icon,
        title = title,
        onClick = onClick,
        actions = {
            IconButton(
                shape = IconButtonDefaults.shapes().shape,
                onClick = onClick,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = title,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AboutItem(
    icon: ImageVector,
    title: String,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            shapes = IconButtonDefaults.shapes(),
            onClick = { },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }

        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        actions.invoke()
    }
}



