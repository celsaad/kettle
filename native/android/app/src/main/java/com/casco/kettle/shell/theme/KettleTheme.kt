package com.casco.kettle.shell.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalKettleColors = staticCompositionLocalOf { LightColors }

/**
 * The shell's theme, and the Compose side of RN's `useTheme`: screens read tokens through
 * [KettleTheme.colors]. Material components get a scheme mapped from the same tokens, so a bare
 * `Surface` or `Text` already lands on the right background and ink. [darkTheme] is a parameter
 * rather than always the system's because the RN shell has a manual override, which native reads from
 * the preferences file once it owns that (M3 on).
 */
@Composable
fun KettleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalKettleColors provides colors) {
        MaterialTheme(colorScheme = colors.toColorScheme(darkTheme), content = content)
    }
}

object KettleTheme {
    val colors: KettleColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKettleColors.current
}

/**
 * The live session runner's theme: [RunnerColors] whatever the system scheme, as the RN runner is.
 * It reads no scheme at all, so nested inside a light [KettleTheme] it is still dark.
 */
@Composable
fun RunnerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RunnerColorScheme, content = content)
}

// Only the roles a token answers. The rest keep Material's defaults until a screen needs one, so a
// mapping nobody chose isn't presented as the design.
private fun KettleColors.toColorScheme(dark: Boolean): ColorScheme =
    (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = accent,
        onPrimary = onAccent,
        background = background,
        onBackground = text,
        surface = background,
        onSurface = text,
        surfaceVariant = backgroundElement,
        onSurfaceVariant = textSecondary,
        outline = border,
    )

private val RunnerColorScheme =
    darkColorScheme(
        primary = RunnerColors.accent,
        // The RN runner's labels on an accent fill are set in its background color.
        onPrimary = RunnerColors.background,
        background = RunnerColors.background,
        onBackground = RunnerColors.text,
        surface = RunnerColors.background,
        onSurface = RunnerColors.text,
        surfaceVariant = RunnerColors.backgroundElement,
        onSurfaceVariant = RunnerColors.textSecondary,
        outline = RunnerColors.border,
    )
