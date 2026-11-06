package com.darkmintis.gitstore.core.presentation.utils

import androidx.appcompat.app.AppCompatDelegate

/** Align the Android night mode with the in-app dark/light/system preference. */
fun applyAppNightMode(isDarkTheme: Boolean?) {
    AppCompatDelegate.setDefaultNightMode(
        when (isDarkTheme) {
            true -> AppCompatDelegate.MODE_NIGHT_YES
            false -> AppCompatDelegate.MODE_NIGHT_NO
            null -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    )
}
