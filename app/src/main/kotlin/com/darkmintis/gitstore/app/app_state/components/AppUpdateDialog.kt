package com.darkmintis.gitstore.app.app_state.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.darkmintis.gitstore.R
import com.darkmintis.gitstore.core.presentation.theme.GithubStoreTheme

@Composable
fun AppUpdateDialog(
    versionTag: String,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onIgnore: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        icon = {
            Icon(
                imageVector = Icons.Filled.Upgrade,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.update_available),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.app_update_available_message, versionTag),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text(stringResource(R.string.update))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onIgnore) {
                    Text(stringResource(R.string.ignore))
                }
                TextButton(onClick = onLater) {
                    Text(stringResource(R.string.later))
                }
            }
        },
    )
}

@Preview
@Composable
private fun AppUpdateDialogPreview() {
    GithubStoreTheme {
        AppUpdateDialog(
            versionTag = "v1.5.0",
            onUpdate = {},
            onLater = {},
            onIgnore = {},
        )
    }
}
