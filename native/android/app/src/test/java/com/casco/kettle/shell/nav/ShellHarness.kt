package com.casco.kettle.shell.nav

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.casco.kettle.shell.files.FakeStoredFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** The rule every shell test drives the app's one view in. */
internal fun shellRule() = createAndroidComposeRule<ComponentActivity>()

/** Files read at once, from a fake that holds nothing. */
internal fun readFiles() =
    LatestFiles(FakeStoredFiles(), CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined)

/**
 * The shell as `MainActivity` lays it out, its one view ([ShellView]) given [navigator], with each route
 * drawn by [screen]: the activity's locals, a light scheme, and the files [files] reads.
 */
internal fun AndroidComposeTestRule<*, out ComponentActivity>.showShell(
    navigator: Navigator,
    files: LatestFiles = readFiles(),
    onSplash: () -> Unit = {},
    before: (ComponentActivity) -> Unit = {},
    screen: @Composable (Route) -> Unit,
): ShellView {
    lateinit var shell: ShellView
    runOnUiThread {
        before(activity)
        shell =
            ShellView(
                activity,
                navigator,
                theme = {
                    CompositionLocalProvider(
                        LocalLatestFiles provides files,
                        LocalSplashExit provides onSplash,
                        LocalNavigator provides navigator,
                    ) { KettleTheme(darkTheme = false, it) }
                },
                content = { screen(it) },
            )
        activity.setContentView(shell)
    }
    waitForIdle()
    return shell
}

/** The shell's motion, which the shell hands the navigator to hear its moves. */
internal val Navigator.motion: ShellMotion get() = moves as ShellMotion

/** The name a stand-in screen draws for [route]: its destination and its params. */
internal fun standInName(route: Route) = "Screen ${route.destination.name} ${route.params.values.joinToString()}".trim()
