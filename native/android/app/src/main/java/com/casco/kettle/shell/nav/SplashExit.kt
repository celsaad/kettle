package com.casco.kettle.shell.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import com.casco.kettle.shell.files.LatestFiles

/**
 * Lets the system splash go (#235): at the first layout after [files] are read, a frame waited for,
 * since composing isn't laying out. Normally that is native Home's, the destination the app opens on.
 *
 * In the destinations rather than in Home, which a cold-start link can steer away from before the read
 * lands: Home's effect would be cancelled with it and the splash would never go. On the read, which
 * waits for the library's seed (`LibraryGate`), so a fresh install's first frame isn't an empty Home.
 * Home's `reportFullyDrawn` waits for the library too.
 */
@Composable
internal fun SplashExit(
    files: LatestFiles,
    onShown: () -> Unit,
) {
    val shown by rememberUpdatedState(onShown)
    // The log is null only until the first read lands, so this runs to the end once per activity.
    val read = files.sessionLog != null
    LaunchedEffect(read) {
        if (!read) return@LaunchedEffect
        withFrameNanos {}
        shown()
    }
}

/** What lets the splash go: the activity's [SplashHold] in the app, provided with the files. */
val LocalSplashExit = staticCompositionLocalOf<() -> Unit> { {} }
