package com.casco.kettle.shell.theme

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File

/**
 * [KettleTheme] in the scheme [preferences] pins ([rememberPreferredDarkTheme], which [reread] is
 * passed to), with the system bars' icons drawn to contrast with what's under them (#248). Nothing else
 * sets them: the window's theme leaves them light in either scheme, white on the light background, and
 * a theme attribute couldn't follow Settings → Appearance anyway. [runnerShown] keeps them light over
 * the runner, which is dark in either scheme.
 *
 * Each of the shell's Compose views is wrapped in one, so each sets the same window's icons to the same
 * value from the same file and configuration.
 */
@Composable
fun PreferredKettleTheme(
    preferences: File,
    reread: Boolean,
    runnerShown: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = rememberPreferredDarkTheme(preferences, reread)
    val window = LocalActivity.current?.window
    val darkIcons = !dark && !runnerShown
    SideEffect {
        if (window == null) return@SideEffect
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = darkIcons
        }
    }
    KettleTheme(dark, content)
}
