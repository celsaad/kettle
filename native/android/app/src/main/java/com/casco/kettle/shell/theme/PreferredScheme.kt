package com.casco.kettle.shell.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Whether the chrome draws dark, from Settings → Appearance in [preferences]. RN writes that file and
 * the shell only reads it (M5 moves it to native), so it's read again each time [reread] turns true:
 * the shell passes the bar coming back, since Settings is a modal and the bar is hidden under it.
 */
@Composable
fun rememberPreferredDarkTheme(
    preferences: File,
    reread: Boolean,
): Boolean {
    // Read once on the main thread, so the first frame is already the right scheme.
    var preference by remember { mutableStateOf(ThemePreference.read(preferences)) }
    LaunchedEffect(reread) {
        if (reread) preference = withContext(Dispatchers.IO) { ThemePreference.read(preferences) }
    }
    return when (preference) {
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
        ThemePreference.System -> isSystemInDarkTheme()
    }
}
