package com.casco.kettle.shell.theme

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.view.WindowInsetsControllerCompat
import com.casco.kettle.domain.Preferences
import java.io.File

/**
 * [KettleTheme] in the scheme [preferences] pins ([rememberPreferredDarkTheme], which [latest] and
 * [reread] are passed to), with the system bars' icons drawn to contrast with what's under them (#248). Nothing else
 * sets them: the window's theme leaves them light in either scheme, white on the light background, and
 * a theme attribute couldn't follow Settings → Appearance anyway. While [runnerShown], they're the
 * runner's: whatever its field last asked for ([RunnerBarIcons]), read from [LocalRunnerBars].
 * Each of [splashExits] sets them again: on API 31 and 32
 * the splash's exit resets them from the window's theme, with nothing here recomposing to undo it.
 *
 * The shell's composition is wrapped in one, which recomposes on its own (the preferences reread on
 * Resume, for one) while the runner shows: over the runner it reads the runner's choice from
 * [RunnerBars], or it would set the shell's light icons back over the orange field (#470).
 */
@Suppress("LongParameterList") // Each is an input the bars follow; a holder would serve one call site.
@Composable
fun PreferredKettleTheme(
    preferences: File,
    latest: Preferences?,
    reread: Boolean,
    runnerShown: Boolean,
    splashExits: Int,
    content: @Composable () -> Unit,
) {
    val dark = rememberPreferredDarkTheme(preferences, latest, reread)
    val window = LocalActivity.current?.window
    val runnerBars = LocalRunnerBars.current
    // Light by default over the runner, dark in either scheme but for its orange field.
    val darkIcons = if (runnerShown) runnerBars?.darkIcons ?: false else !dark
    // A new splashExits already recomposes this, and every composition sets the icons; the key is the
    // parameter's one use, and says what an exit is for.
    key(splashExits) {
        SideEffect {
            if (window == null) return@SideEffect
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = darkIcons
                isAppearanceLightNavigationBars = darkIcons
            }
        }
    }
    KettleTheme(dark, content)
}

/**
 * What the runner's field asks of the system bars' icons, held for the shell's theme to read
 * ([PreferredKettleTheme]); one per activity, provided as [LocalRunnerBars].
 */
@Stable
class RunnerBars {
    var darkIcons by mutableStateOf(false)
}

/** The activity's [RunnerBars]; none in a screen's test, where the runner is the only view. */
val LocalRunnerBars = staticCompositionLocalOf<RunnerBars?> { null }

/**
 * The bars' icons over the runner's field, which decides them (`RunnerField.darkBarIcons`): over the
 * orange one light icons would measure 2.96:1. Set on the window at once, and handed to [RunnerBars],
 * whose change recomposes the shell's [PreferredKettleTheme] to the same value; once the runner
 * closes, `runnerShown` changing sets the shell's icons back. Cleared as it leaves, so the next
 * session's first frame can't show the last one's orange-field icons over its black count-in.
 */
@Composable
fun RunnerBarIcons(darkIcons: Boolean) {
    val window = LocalActivity.current?.window
    val runnerBars = LocalRunnerBars.current
    DisposableEffect(runnerBars) { onDispose { runnerBars?.darkIcons = false } }
    SideEffect {
        runnerBars?.darkIcons = darkIcons
        if (window == null) return@SideEffect
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = darkIcons
        }
    }
}
