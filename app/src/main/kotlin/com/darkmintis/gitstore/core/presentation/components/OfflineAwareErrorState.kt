package com.darkmintis.gitstore.core.presentation.components

import com.darkmintis.gitstore.R

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.darkmintis.gitstore.core.presentation.utils.ConnectivityObserver
import org.koin.compose.koinInject

/**
 * Shared network/offline error UI.
 *
 * Offline / no-connection cases always use the WifiOff [EmptyState] — never the old
 * text-only [RetryErrorState]. When connectivity returns after we have been offline,
 * [onRetry] runs once automatically.
 */
@Composable
fun OfflineAwareErrorState(
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryText: String = stringResource(R.string.retry),
) {
    val connectivityObserver: ConnectivityObserver = koinInject()

    // null until the first connectivity emission — never assume online (that caused a
    // one-frame flash of the old RetryErrorState on Popular/New when offline).
    var isOnline by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(connectivityObserver) {
        connectivityObserver.isOnline.collect { online ->
            isOnline = online
        }
    }

    val offlineMessage = stringResource(R.string.error_offline)
    val networkMessage = stringResource(R.string.error_network)
    val timeoutMessage = stringResource(R.string.error_timeout)
    val youAreOffline = stringResource(R.string.you_are_offline)

    val isNetworkishError = message == offlineMessage ||
        message == networkMessage ||
        message == timeoutMessage ||
        message == youAreOffline

    val showOffline = when (isOnline) {
        false -> true
        true -> isNetworkishError && (
            message == offlineMessage ||
                message == networkMessage ||
                message == youAreOffline
            )
        null -> isNetworkishError
    }

    var sawOffline by remember { mutableStateOf(false) }
    LaunchedEffect(isOnline) {
        when (isOnline) {
            false -> sawOffline = true
            true -> if (sawOffline) {
                sawOffline = false
                onRetry()
            }
            null -> Unit
        }
    }

    if (showOffline) {
        EmptyState(
            title = youAreOffline,
            message = offlineMessage,
            icon = Icons.Default.WifiOff,
            modifier = modifier,
            actionText = retryText,
            onActionClick = onRetry
        )
    } else {
        RetryErrorState(
            title = title,
            message = message,
            onRetry = onRetry,
            modifier = modifier,
            retryText = retryText
        )
    }
}
