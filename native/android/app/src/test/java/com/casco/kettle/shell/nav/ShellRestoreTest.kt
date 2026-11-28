package com.casco.kettle.shell.nav

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.theme.KettleTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A stacked screen's saved state finds it again after the activity is made again (#377): the shell
 * keys each screen by the id it was opened with, so the navigator saves those ids beside the routes
 * and puts them back.
 */
@RunWith(RobolectricTestRunner::class)
class ShellRestoreTest {
    @get:Rule
    val compose = shellRule()

    @Test
    fun `a stacked screen's saved state survives the stack being saved and put back`() {
        val tester = StateRestorationTester(compose)
        val files = readFiles()
        // What the activity before saved: the new one's navigator is restored from it, as MainActivity's is.
        var saved: Pair<List<String>, LongArray>? = null
        lateinit var navigator: Navigator
        tester.setContent {
            val made = remember { Navigator().also { n -> saved?.let { (paths, ids) -> n.restore(paths, ids) } } }
            SideEffect { navigator = made }
            CompositionLocalProvider(
                LocalLatestFiles provides files,
                LocalSplashExit provides {},
                LocalNavigator provides made,
            ) {
                KettleTheme(darkTheme = false) {
                    Shell(made) { route ->
                        var taps by rememberSaveable { mutableIntStateOf(0) }
                        Text("${standInName(route)} $taps", Modifier.clickable { taps++ })
                    }
                }
            }
        }
        compose.runOnUiThread { navigator.push(Route(Destination.Import)) }
        compose.waitForIdle()
        repeat(TAPS) { compose.onNodeWithText("Screen Import $it").performClick() }
        compose.onNodeWithText("Screen Import $TAPS").assertIsDisplayed()

        saved = navigator.saved() to navigator.savedIds()
        tester.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Screen Import $TAPS").assertIsDisplayed()
    }

    private companion object {
        const val TAPS = 3
    }
}
