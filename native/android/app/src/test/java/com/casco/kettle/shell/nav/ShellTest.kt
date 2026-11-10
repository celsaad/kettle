package com.casco.kettle.shell.nav

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionParams
import com.casco.kettle.shell.files.FakeStoredFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.LightColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * The shell laid out as the app lays it out, driven by its [Navigator] (#309). Each route draws a
 * stand-in naming it, so these tests hold where the shell puts a screen, not what the screen draws.
 */
@RunWith(RobolectricTestRunner::class)
// Native graphics throughout: the sheet pictures the screen before every move, which legacy mode can't.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var shell: ShellLayout
    private lateinit var navigator: Navigator

    /** Each time the shell let the system splash go. */
    private var splashExits = 0

    /** What each route draws: its destination's name and its params, unless a test says otherwise. */
    private var screen: @Composable (Route) -> Unit = { route ->
        Text("Screen ${route.destination.name} ${route.params.values.joinToString()}".trim())
    }

    private fun show(files: LatestFiles = noFiles): Navigator {
        navigator = Navigator()
        compose.runOnUiThread {
            val activity = compose.activity
            shell =
                ShellLayout(
                    activity,
                    navigator,
                    // As MainActivity's: a tab reads the files.
                    theme = {
                        CompositionLocalProvider(
                            LocalLatestFiles provides files,
                            LocalSplashExit provides { splashExits++ },
                            LocalNavigator provides navigator,
                        ) { KettleTheme(darkTheme = false, it) }
                    },
                    content = { screen(it) },
                ) { content, shell -> ModalSheet(content, shell, navigator, { LightColors }, {}) }
            navigator.moves = shell.sheet
            activity.setContentView(shell)
        }
        compose.waitForIdle()
        return navigator
    }

    /** A move on the navigator, then its frames, and the transition it starts played out. */
    private fun go(
        settle: Boolean = true,
        move: Navigator.() -> Unit,
    ) {
        compose.runOnUiThread { navigator.move() }
        // Frames by hand: Robolectric dispatches no pre-draw of its own here, and the sheet waits on them.
        repeat(FRAMES) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_MS))
            compose.runOnUiThread { shell.viewTreeObserver.dispatchOnPreDraw() }
            compose.waitForIdle()
        }
        if (!settle) return
        // The transition's animator, on the main looper rather than Compose's clock.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ARRIVED))
        compose.waitForIdle()
    }

    private fun unreadFiles() =
        LatestFiles(
            object : FakeStoredFiles() {
                override fun deleteSession(id: String) = Unit

                override fun writeSession(session: Session) = Unit

                override fun writeLibrary(library: Library) = Unit

                override fun writePreferences(preferences: Preferences) = Unit
            },
            CoroutineScope(Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
        )

    private val noFiles = unreadFiles()

    /**
     * The splash goes at the first layout after the files are read (#235), library or not, and in the
     * shell rather than in Home: a cold-start link can go somewhere other than Home before the read lands,
     * and Home's own effect would then have been cancelled, leaving the splash up for good.
     */
    @Test
    fun `lets the splash go once the files are read, whichever tab is showing`() {
        val files = unreadFiles()
        show(files = files)
        go { select(Tab.History) }
        assertEquals(0, splashExits)

        compose.runOnUiThread { files.reread() }
        compose.waitForIdle()
        assertEquals(1, splashExits)

        compose.runOnUiThread { files.reread() }
        compose.waitForIdle()
        assertEquals(1, splashExits)
    }

    private fun closeButton(): View {
        val found = ArrayList<View>()
        shell.findViewsWithText(found, "Close", View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
        return found.single()
    }

    private fun View.parentView() = parent as View

    /**
     * A tab by its label. By its role too: a tab's screen can carry its tab's name as its heading, as
     * History's does.
     */
    private fun tab(label: String) =
        compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    @Test
    fun `the bar opens on Workouts, with every tab a selectable Tab`() {
        show()

        tab("Workouts").assertIsSelected()
        for (label in listOf("Library", "History", "Programs")) {
            tab(label)
                .assertIsNotSelected()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        }
        compose.onNodeWithText("Screen Workouts").assertIsDisplayed()
    }

    @Test
    fun `a tap selects the tab and shows its screen`() {
        show()

        tab("History").performClick()

        tab("History").assertIsSelected()
        tab("Workouts").assertIsNotSelected()
        assertEquals(Tab.History, navigator.tab)
        compose.onNodeWithText("Screen History").assertIsDisplayed()
    }

    /**
     * Every tab shows its label (#265), so the label is each tab's whole name and the icon adds none: a
     * description on the icon as well would have TalkBack read the tab twice. Selected or not, since the
     * old bar described the icon of every tab but the selected one.
     */
    @Test
    fun `every tab is named once, by its label, whichever is selected`() {
        show()
        tab("History").performClick()
        compose.mainClock.advanceTimeBy(ANIMATIONS_SETTLED)

        for (label in listOf("Workouts", "Library", "History", "Programs")) {
            tab(label)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(AnnotatedString(label))))
                .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        }
    }

    @Test
    fun `every tab is at least 48dp each way`() {
        show()

        for (label in listOf("Workouts", "Library", "History", "Programs")) {
            tab(label).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }

    /** The sheet's back, with its predictive shrink, is on only while a sheet shows; the tabs' is the app's. */
    @Test
    fun `the sheet takes back only while a sheet shows`() {
        show()
        compose.runOnIdle { assertEquals(false, shell.sheet.backCallback.isEnabled) }

        go { push(Route(Destination.Settings)) }
        compose.runOnIdle { assertEquals(true, shell.sheet.backCallback.isEnabled) }

        go { back() }
        compose.runOnIdle { assertEquals(false, shell.sheet.backCallback.isEnabled) }
    }

    @Test
    fun `the bar leaves while a sheet shows, and comes back with the tab`() {
        show()

        go { push(Route(Destination.Import)) }

        tab("Workouts").assertDoesNotExist()

        go { back() }

        tab("Workouts").assertIsSelected()
    }

    /**
     * A tab is opaque in the theme's background however little its screen paints, top edge included
     * (#227). Without it the window's own Material gray showed through, under History's header and in
     * the strip behind the status bar.
     */
    @Test
    fun `a tab paints the theme's background behind its screen`() {
        screen = { Box(Modifier.fillMaxSize().testTag("see-through")) }
        show()

        tab("History").performClick()

        // Drawn by hand, as captureToImage waits on a draw pass Robolectric never dispatches here. The
        // screen's bounds are in the shell's own coordinates: Destinations is the shell's top child.
        val bounds = compose.onNodeWithTag("see-through").fetchSemanticsNode().boundsInRoot
        val pixels =
            compose.runOnIdle {
                Bitmap.createBitmap(shell.width, shell.height, Bitmap.Config.ARGB_8888).also { shell.draw(Canvas(it)) }
            }
        for (y in listOf(bounds.top, bounds.center.y, bounds.bottom - 1)) {
            assertEquals(LightColors.background.toArgb(), pixels.getPixel(bounds.center.x.toInt(), y.toInt()))
        }
    }

    @Test
    fun `a pushed sheet shows its screen in the sheet's view, given the route's params`() {
        show()

        go { push(Route(Destination.ExerciseProgress, mapOf("exerciseId" to "kb-swing"))) }

        compose.onNodeWithText("Screen ExerciseProgress kb-swing").assertIsDisplayed()
        compose.runOnIdle { assertEquals(View.VISIBLE, shell.nativeSheet.visibility) }
    }

    /**
     * What composed has to be what was pushed, or the arrival waits out the backstop, the whole window
     * held. Within a few frames, well short of it, the transition has started and shown the chrome,
     * which is gone until one does.
     */
    @Test
    fun `a sheet arrives within frames, not at the backstop`() {
        show()

        go(settle = false) { push(Route(Destination.ExerciseProgress, mapOf("exerciseId" to "kb-swing"))) }

        compose.runOnIdle { assertEquals(View.VISIBLE, closeButton().parentView().visibility) }
    }

    /** Where a sheet's content starts, under the strip the chrome draws over. */
    @Test
    fun `a sheet's content starts below the chrome's strip`() {
        show()

        go { push(Route(Destination.Support)) }

        compose.onNodeWithText("Screen Support").assertTopPositionInRootIsEqualTo(SheetStripHeight)
    }

    /** Settings → Support, and back: the sheet's view shows each in turn. */
    @Test
    fun `a sheet pushed over a sheet replaces it, and back brings the first back`() {
        show()
        go { push(Route(Destination.Settings)) }

        go { push(Route(Destination.Support)) }

        compose.onNodeWithText("Screen Support").assertIsDisplayed()
        compose.onNodeWithText("Screen Settings").assertDoesNotExist()

        go { back() }

        compose.onNodeWithText("Screen Settings").assertIsDisplayed()
    }

    /** Closing it lands on the tab it opened over, with that tab's layer back. */
    @Test
    fun `closing a sheet over a tab shows the tab again`() {
        show()
        tab("History").performClick()

        go { push(Route(Destination.SessionEditor, mapOf("id" to "legs-1"))) }

        compose.onNodeWithText("Screen SessionEditor legs-1").assertIsDisplayed()
        compose.runOnIdle { assertEquals(View.INVISIBLE, shell.destinations.visibility) }

        go { back() }

        compose.onNodeWithText("Screen History").assertIsDisplayed()
        compose.onNodeWithText("Screen SessionEditor legs-1").assertDoesNotExist()
    }

    /** The runner's slot shows over everything, the sheet it opened from included, and its pop brings that back. */
    @Test
    fun `the runner shows over the sheet it opened from, and its close shows the sheet again`() {
        show()
        go { push(Route(Destination.ProgramDetail)) }

        go { openRunner(RunnerStart.Planned(SessionParams.Workout("bench"))) }

        compose.onNodeWithText("Screen Session bench").assertIsDisplayed()
        compose.runOnIdle { assertEquals(View.VISIBLE, shell.nativeRunner.visibility) }
        compose.runOnIdle { assertEquals(View.INVISIBLE, shell.nativeSheet.visibility) }
        tab("Workouts").assertDoesNotExist()

        go { closeRunner() }

        compose.onNodeWithText("Screen Session bench").assertDoesNotExist()
        compose.onNodeWithText("Screen ProgramDetail").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "pt")
    fun `the tabs are labelled in Portuguese`() {
        show()
        for (label in listOf("Treinos", "Biblioteca", "Histórico", "Programas")) tab(label).assertExists()
    }

    @Test
    @Config(qualifiers = "ja")
    fun `the tabs are labelled in Japanese`() {
        show()
        for (label in listOf("ワークアウト", "ライブラリ", "履歴", "プログラム")) tab(label).assertExists()
    }

    private companion object {
        const val ANIMATIONS_SETTLED = 1_000L

        /** Past the sheet's hold backstop and the transition. */
        const val ARRIVED = 1_000L
        const val FRAMES = 4
        const val FRAME_MS = 16L
    }
}
