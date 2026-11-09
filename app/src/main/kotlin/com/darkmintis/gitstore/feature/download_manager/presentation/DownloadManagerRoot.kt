package com.darkmintis.gitstore.feature.download_manager.presentation

import com.darkmintis.gitstore.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.darkmintis.gitstore.core.presentation.components.EmptyState
import com.darkmintis.gitstore.core.presentation.components.GithubStoreTopBarTitle
import com.darkmintis.gitstore.core.presentation.components.LoadingState
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerRoot(
    onOpenRepo: (Long) -> Unit = {},
    viewModel: DownloadManagerViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<DownloadItem?>(null) }
    var uninstallTarget by remember { mutableStateOf<DownloadItem?>(null) }

    // Returning from the system installer/uninstaller should update the row immediately.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshDownloads()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    GithubStoreTopBarTitle(
                        text = stringResource(R.string.downloads)
                    )
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            state.errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            when {
                state.isLoading && state.downloads.isEmpty() -> {
                    LoadingState()
                }

                state.downloads.isEmpty() -> {
                    EmptyState(
                        title = stringResource(R.string.no_downloads_yet),
                        message = stringResource(R.string.no_downloads_yet_message),
                        icon = Icons.Filled.Download
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.downloads, key = { it.filePath }) { item ->
                            DownloadCard(
                                item = item,
                                onInstall = { viewModel.installApk(item.filePath) },
                                onOpen = { item.packageName?.let(viewModel::openApp) },
                                onDeleteRequest = { deleteTarget = item },
                                onUninstallRequest = { uninstallTarget = item },
                                onCancel = { viewModel.cancelDownload(item.fileName) },
                                onOpenRepo = {
                                    item.repoId?.let(onOpenRepo)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_download_title)) },
            text = {
                Text(stringResource(R.string.delete_download_message, item.fileName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFile(item.filePath)
                        deleteTarget = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    uninstallTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { uninstallTarget = null },
            title = { Text(stringResource(R.string.uninstall_app_title, item.title)) },
            text = { Text(stringResource(R.string.uninstall_app_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.uninstallApp(item)
                        uninstallTarget = null
                    }
                ) {
                    Text(stringResource(R.string.uninstall))
                }
            },
            dismissButton = {
                TextButton(onClick = { uninstallTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadCard(
    item: DownloadItem,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onDeleteRequest: () -> Unit,
    onUninstallRequest: () -> Unit,
    onCancel: () -> Unit,
    onOpenRepo: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (item.repoId != null) {
                    Modifier.clickable(onClick = onOpenRepo)
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DownloadAvatar(
                    avatarUrl = item.avatarUrl,
                    fallbackText = item.title,
                )
                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            if (item.displayName != null) {
                                append(item.fileName)
                                append(" · ")
                            }
                            append(formatSize(item.fileSize))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (item.isInstalled) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (item.isUpdateAvailable) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                            Text(
                                text = stringResource(
                                    if (item.isUpdateAvailable) {
                                        R.string.update_available
                                    } else {
                                        R.string.installed
                                    }
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.isUpdateAvailable) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }

                when {
                    item.status == DownloadStatus.DOWNLOADING -> {
                        IconButton(onClick = onCancel) {
                            Icon(
                                imageVector = Icons.Filled.Cancel,
                                contentDescription = stringResource(R.string.cancel),
                                tint = DeleteRed
                            )
                        }
                    }

                    item.status == DownloadStatus.COMPLETED -> {
                        if (item.isInstalled && !item.isUpdateAvailable) {
                            TextButton(onClick = onOpen) {
                                Text(
                                    text = stringResource(R.string.open),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        } else {
                            TextButton(onClick = onInstall) {
                                Text(
                                    text = stringResource(
                                        if (item.isUpdateAvailable) {
                                            R.string.install_update
                                        } else {
                                            R.string.install
                                        }
                                    ),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        DownloadMenu(
                            canUninstall = item.isInstalled,
                            onDelete = onDeleteRequest,
                            onUninstall = onUninstallRequest,
                        )
                    }

                    item.status == DownloadStatus.FAILED -> {
                        DownloadMenu(
                            canUninstall = false,
                            onDelete = onDeleteRequest,
                            onUninstall = onUninstallRequest,
                        )
                    }
                }
            }

            if (item.status == DownloadStatus.DOWNLOADING) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (item.progress ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${item.progress ?: 0}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

private val DeleteRed = Color(0xFFD32F2F)

@Composable
private fun DownloadMenu(
    canUninstall: Boolean,
    onDelete: () -> Unit,
    onUninstall: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.more_options),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete_apk)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )

            if (canUninstall) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.uninstall)) },
                    onClick = {
                        expanded = false
                        onUninstall()
                    },
                )
            }
        }
    }
}

@Composable
private fun DownloadAvatar(
    avatarUrl: String?,
    fallbackText: String,
    size: Dp = 44.dp,
) {
    val shape = RoundedCornerShape(12.dp)
    val base = Modifier
        .size(size)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerHighest)

    if (avatarUrl != null) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = base,
        )
    } else {
        Box(modifier = base, contentAlignment = Alignment.Center) {
            Text(
                text = fallbackText.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
