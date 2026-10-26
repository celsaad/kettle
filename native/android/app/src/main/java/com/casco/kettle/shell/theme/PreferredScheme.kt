package com.casco.kettle.shell.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.parsePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

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
    var preference by remember { mutableStateOf(readThemePreference(preferences)) }
    LaunchedEffect(reread) {
        if (reread) preference = withContext(Dispatchers.IO) { readThemePreference(preferences) }
    }
    return when (preference) {
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }
}

/**
 * The scheme [file] pins. No file, and one RN would refuse, both follow the device, as they do in RN:
 * the bar must never pin a scheme the app around it ignores.
 */
internal fun readThemePreference(file: File): ThemePreference {
    val text =
        try {
            file.readText()
        } catch (_: IOException) {
            null
        }
    return text?.let(::parsePreferences)?.themePreference ?: ThemePreference.SYSTEM
}
