package com.darkmintis.gitstore

import com.darkmintis.gitstore.core.presentation.model.AppTheme
import com.darkmintis.gitstore.network.RateLimitInfo

data class MainState(
    val isCheckingAuth: Boolean = true,
    val isThemeReady: Boolean = false,
    val isLoggedIn: Boolean = false,
    val rateLimitInfo: RateLimitInfo? = null,
    val showRateLimitDialog: Boolean = false,
    val currentColorTheme: AppTheme = AppTheme.OCEAN,
    val isAmoledTheme: Boolean = false,
    val isDarkTheme: Boolean? = null,
)


