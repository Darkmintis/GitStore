package com.darkmintis.gitstore.core.presentation.utils

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun ApplyAndroidSystemBars(isDarkTheme: Boolean?) {
    // LocalView keeps the Activity even when LocalContext is swapped for locale overrides.
    val view = LocalView.current
    val activity = view.context.findComponentActivity() ?: return
    val isDark = isDarkTheme ?: isSystemInDarkTheme()

    SideEffect {
        activity.updateSystemBars(isDark)
        // Belt-and-suspenders for OEMs that ignore SystemBarStyle until a config change.
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }
}

private fun Context.findComponentActivity(): ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return current as? ComponentActivity
}

fun ComponentActivity.updateSystemBars(isDarkTheme: Boolean) {
    // Drive icon contrast from the *app* theme. enableEdgeToEdge()'s default auto style
    // follows system night mode, so a forced dark theme on a light system kept black icons.
    if (isDarkTheme) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
    } else {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
    }
}
