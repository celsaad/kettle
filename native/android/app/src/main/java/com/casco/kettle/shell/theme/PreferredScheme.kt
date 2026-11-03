package com.casco.kettle.shell.theme

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.parsePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Whether the chrome draws dark, from Settings → Appearance in [preferences]. RN Settings writes that
 * file until the preferences flip, so it's read again each time [reread] turns true: the shell passes
 * the bar coming back, since Settings is a modal and the bar is hidden under it. Native Settings
 * writes it through `LatestFiles` (#237), whose [latest] the chrome follows the moment it changes,
 * with the sheet still up; null there is a read that found none, or none landed yet, and the file's
 * own read stands.
 */
@Composable
fun rememberPreferredDarkTheme(
    preferences: File,
    latest: Preferences?,
    reread: Boolean,
): Boolean {
    // Read once on the main thread, so the first frame is already the right scheme.
    var preference by remember { mutableStateOf(readThemePreference(preferences)) }
    LaunchedEffect(reread) {
        if (reread) preference = withContext(Dispatchers.IO) { readThemePreference(preferences) }
    }
    LaunchedEffect(latest?.themePreference) { latest?.let { preference = it.themePreference } }
    return isDark(preference, systemDark = isSystemInDarkTheme())
}

/** Whether [preference] means dark, on a device that is dark when [systemDark]. */
private fun isDark(
    preference: ThemePreference,
    systemDark: Boolean,
) = when (preference) {
    ThemePreference.LIGHT -> false
    ThemePreference.DARK -> true
    ThemePreference.SYSTEM -> systemDark
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

/**
 * [rememberPreferredDarkTheme] outside Compose, read now: the scheme for the views the shell draws
 * itself, such as the sheet's chrome. The file is a few bytes, so the main thread reads it.
 */
fun preferredColors(
    preferences: File,
    configuration: Configuration,
): KettleColors {
    val systemDark = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    return if (isDark(readThemePreference(preferences), systemDark)) DarkColors else LightColors
}
