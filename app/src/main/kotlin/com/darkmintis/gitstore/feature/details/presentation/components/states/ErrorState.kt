package com.darkmintis.gitstore.feature.details.presentation.components.states

import com.darkmintis.gitstore.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign




import androidx.compose.ui.res.stringResource
import com.darkmintis.gitstore.core.presentation.components.OfflineAwareErrorState
import com.darkmintis.gitstore.feature.details.presentation.DetailsAction

@Composable
fun ErrorState(
    errorMessage: String,
    onAction: (DetailsAction) -> Unit
) {
    OfflineAwareErrorState(
        title = stringResource(R.string.error_loading_details),
        message = errorMessage,
        onRetry = { onAction(DetailsAction.Retry) }
    )
}



