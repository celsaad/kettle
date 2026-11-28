package com.casco.kettle.shell.nav

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.casco.kettle.shell.theme.LightColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The shell laid out as the app lays it out, driven by its [Navigator] (#309). Each route draws a
 * stand-in naming it, so these tests hold where the shell puts a screen, not what the screen draws.
 * How each move animates is [SheetTransitionTest]'s.
 */
@RunWith(RobolectricTestRunner::class)
// Native graphics for the one test that draws the shell by hand.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellTest {
    @get:Rule
    val compose = shellRule()

    private lateinit var shell: ShellView
    private lateinit var navigator: Navigator

    /** Each time the shell let the system splash go. */
    private var splashExits = 0

    /** What each route draws: its destination's name and its params, unless a test says otherwise. */
    private var screen: @Composable (Route) -> Unit = { Text(standInName(it)) }

    private fun show(files: LatestFiles = readFiles()): Navigator {
        navigator = Navigator()
        shell = compose.showShell(navigator, files, onSplash = { splashExits++ }) { screen(it) }
        return navigator
    }

    /** A move on the navigator, and the transition it starts played out. */
    private fun go(move: Navigator.() -> Unit) {
        compose.runOnUiThread { navigator.move() }
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

    /**
     * The splash goes at the first layout after the files are read (#235), library or not, and in the
     * shell rather than in Home: a cold-start link can go somewhere other than Home before the read lands,
     * and Home's own effect would then have been cancelled, leaving the splash up for good.
     */
    @Test
    fun `lets the splash go once the files are read, whichever tab is showing`() {
        val files = unreadFiles()
        show(files = files)
        go { select(Tab.Progress) }
        assertEquals(0, splashExits)

        compose.runOnUiThread { files.reread() }
        compose.waitForIdle()
        assertEquals(1, splashExits)

        compose.runOnUiThread { files.reread() }
        compose.waitForIdle()
        assertEquals(1, splashExits)
    }

    /**
     * A tab by its label. By its role too: a tab's screen can carry its tab's name as its heading, as
     * History's does.
     */
    private fun tab(label: String) =
        compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    @Test
    fun `the bar opens on Today, with every tab a selectable Tab`() {
        show()

        tab("Today").assertIsSelected()
        for (label in listOf("Library", "Progress")) {
            tab(label)
                .assertIsNotSelected()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        }
        compose.onNodeWithText("Screen Today").assertIsDisplayed()
    }

    @Test
    fun `a tap selects the tab and shows its screen`() {
        show()

        tab("Progress").performClick()

        tab("Progress").assertIsSelected()
        tab("Today").assertIsNotSelected()
        assertEquals(Tab.Progress, navigator.tab)
        compose.onNodeWithText("Screen Progress").assertIsDisplayed()
    }

    /**
     * Every tab shows its label (#265), so the label is each tab's whole name and the icon adds none: a
     * description on the icon as well would have TalkBack read the tab twice. Selected or not, since the
     * old bar described the icon of every tab but the selected one.
     */
    @Test
    fun `every tab is named once, by its label, whichever is selected`() {
        show()
        tab("Progress").performClick()
        compose.mainClock.advanceTimeBy(ANIMATIONS_SETTLED)

        for (label in listOf("Today", "Library", "Progress")) {
            tab(label)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(AnnotatedString(label))))
                .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        }
    }

    @Test
    fun `every tab is at least 48dp each way`() {
        show()

        for (label in listOf("Today", "Library", "Progress")) {
            tab(label).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }

    /**
     * The shell's back, with its predictive motion, is on only while a sheet or a pushed screen shows;
     * the tabs' is the activity's, which this test's activity doesn't have.
     */
    @Test
    fun `the shell takes back only while a sheet or a pushed screen shows`() {
        show()
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle { assertEquals(false, dispatcher.hasEnabledCallbacks()) }

        go { push(Route(Destination.Support)) }
        compose.runOnIdle { assertEquals(true, dispatcher.hasEnabledCallbacks()) }

        go { back() }
        go { push(Route(Destination.Settings)) }
        compose.runOnIdle { assertEquals(true, dispatcher.hasEnabledCallbacks()) }

        go { back() }
        compose.runOnIdle { assertEquals(false, dispatcher.hasEnabledCallbacks()) }
    }

    /** The system's back over a sheet goes back, and over the last one closes it. */
    @Test
    fun `back from the dispatcher takes the top sheet off`() {
        show()
        go { push(Route(Destination.Import)) }
        go { push(Route(Destination.Support)) }

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        assertEquals(listOf(Route(Destination.Import)), navigator.sheets)
    }

    @Test
    fun `the bar is out of reach under a sheet, and reachable again with the tab`() {
        show()

        go { push(Route(Destination.Import)) }

        tab("Today").assertDoesNotExist()

        go { back() }

        tab("Today").assertIsSelected()
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

        tab("Progress").performClick()

        // Drawn by hand, as captureToImage waits on a draw pass Robolectric never dispatches here.
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
    fun `a pushed sheet shows its screen, given the route's params`() {
        show()

        go { push(Route(Destination.ExerciseEditor, mapOf("id" to "kb-swing"))) }

        compose.onNodeWithText("Screen ExerciseEditor kb-swing").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    /** Where a sheet's content starts: 12dp under the status bar, which has none here, and under the strip. */
    @Test
    fun `a sheet's content starts below the chrome's strip`() {
        show()

        go { push(Route(Destination.Support)) }

        compose.onNodeWithText("Screen Support").assertTopPositionInRootIsEqualTo(12.dp + SheetStripHeight)
    }

    /** Import → Your file's sibling, and back: the sheet shows each in turn. */
    @Test
    fun `a sheet pushed over a sheet replaces it, and back brings the first back`() {
        show()
        go { push(Route(Destination.Import)) }

        go { push(Route(Destination.Support)) }

        compose.onNodeWithText("Screen Support").assertIsDisplayed()
        compose.onNodeWithText("Screen Import").assertDoesNotExist()

        go { back() }

        compose.onNodeWithText("Screen Import").assertIsDisplayed()
    }

    /**
     * A pushed screen (#489) fills the frame from the top, with no grabber or × and no bar, and back
     * pops it to the tab it covered.
     */
    @Test
    fun `a pushed screen fills the frame with no sheet chrome, and back pops it`() {
        show()
        tab("Progress").performClick()

        go { push(Route(Destination.Settings)) }

        compose.onNodeWithText("Screen Settings").assertTopPositionInRootIsEqualTo(0.dp)
        compose.onNodeWithContentDescription("Close").assertDoesNotExist()
        compose.onNodeWithContentDescription("Drag handle").assertDoesNotExist()
        tab("Today").assertDoesNotExist()

        go { back() }

        compose.onNodeWithText("Screen Progress").assertIsDisplayed()
        compose.onNodeWithText("Screen Settings").assertDoesNotExist()
    }

    /** Settings → Support: the sheet covers the pushed screen as it covers a tab, and closes onto it. */
    @Test
    fun `a sheet over a pushed screen is a sheet, and closes back onto it`() {
        show()
        go { push(Route(Destination.Settings)) }

        go { push(Route(Destination.Support)) }

        compose.onNodeWithText("Screen Support").assertTopPositionInRootIsEqualTo(12.dp + SheetStripHeight)
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
        // Under the scrim, live, and out of TalkBack's reach.
        compose.onNodeWithText("Screen Settings").assertDoesNotExist()

        go { back() }

        compose.onNodeWithText("Screen Settings").assertTopPositionInRootIsEqualTo(0.dp)
        compose.onNodeWithContentDescription("Close").assertDoesNotExist()
    }

    /**
     * A pushed screen stays composed under what covers it, so it hears each time it shows again: Settings
     * re-reads the supporter file after a tip in the Support sheet over it, and Exercise detail retakes its
     * clock.
     */
    @Test
    fun `a pushed screen hears each time it shows again from under a sheet, a pushed screen or the runner`() {
        screen = { Text("${standInName(it)} shown again ${LocalUncovered.current}") }
        show()
        go { push(Route(Destination.Settings)) }
        compose.onNodeWithText("Screen Settings shown again 0").assertIsDisplayed()

        go { push(Route(Destination.Support)) }
        go { back() }
        compose.onNodeWithText("Screen Settings shown again 1").assertIsDisplayed()

        go { push(Route(Destination.ExerciseDetail, mapOf("id" to "dips"))) }
        go { back() }
        compose.onNodeWithText("Screen Settings shown again 2").assertIsDisplayed()

        go { openRunner(RunnerStart.Planned(SessionParams.Workout("bench"))) }
        go { closeRunner() }
        compose.onNodeWithText("Screen Settings shown again 3").assertIsDisplayed()
    }

    /** Exercise detail's Used in → Program detail, and back. */
    @Test
    fun `a pushed screen over a pushed screen goes back to it`() {
        show()
        go { push(Route(Destination.ExerciseDetail, mapOf("id" to "dips"))) }

        go { push(Route(Destination.ProgramDetail, mapOf("programId" to "base"))) }

        compose.onNodeWithText("Screen ProgramDetail base").assertIsDisplayed()
        compose.onNodeWithText("Screen ExerciseDetail dips").assertDoesNotExist()

        go { back() }

        compose.onNodeWithText("Screen ExerciseDetail dips").assertTopPositionInRootIsEqualTo(0.dp)
        compose.runOnIdle { assertEquals(true, compose.activity.onBackPressedDispatcher.hasEnabledCallbacks()) }
    }

    /**
     * Closing it lands on the tab it opened over. The tab stays drawn under the sheet's scrim, and while
     * it's covered TalkBack can't reach it.
     */
    @Test
    fun `closing a sheet over a tab shows the tab again`() {
        show()
        tab("Progress").performClick()

        go { push(Route(Destination.SessionEditor, mapOf("id" to "legs-1"))) }

        compose.onNodeWithText("Screen SessionEditor legs-1").assertIsDisplayed()
        compose.onNodeWithText("Screen Progress").assertDoesNotExist()

        go { back() }

        compose.onNodeWithText("Screen Progress").assertIsDisplayed()
        compose.onNodeWithText("Screen SessionEditor legs-1").assertDoesNotExist()
    }

    /** The runner's slot shows over everything, the sheet it opened from included, and its pop brings that back. */
    @Test
    fun `the runner shows over the sheet it opened from, and its close shows the sheet again`() {
        show()
        go { push(Route(Destination.ProgramDetail)) }

        go { openRunner(RunnerStart.Planned(SessionParams.Workout("bench"))) }

        compose.onNodeWithText("Screen Session bench").assertIsDisplayed()
        compose.onNodeWithText("Screen ProgramDetail").assertDoesNotExist()
        tab("Today").assertDoesNotExist()

        go { closeRunner() }

        compose.onNodeWithText("Screen Session bench").assertDoesNotExist()
        compose.onNodeWithText("Screen ProgramDetail").assertIsDisplayed()
    }

    /** Program detail is pushed, and its Start opens the runner over it the same way (#489). */
    @Test
    fun `the runner over a sheet over a pushed screen comes back to the sheet`() {
        show()
        go { push(Route(Destination.Settings)) }
        go { push(Route(Destination.WorkoutPreview, mapOf("workoutId" to "bench"))) }

        go { openRunner(RunnerStart.Planned(SessionParams.Workout("bench"))) }
        go { closeRunner() }

        compose.onNodeWithText("Screen WorkoutPreview bench").assertTopPositionInRootIsEqualTo(12.dp + SheetStripHeight)
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "pt")
    fun `the tabs are labelled in Portuguese`() {
        show()
        for (label in listOf("Hoje", "Biblioteca", "Progresso")) tab(label).assertExists()
    }

    @Test
    @Config(qualifiers = "ja")
    fun `the tabs are labelled in Japanese`() {
        show()
        for (label in listOf("今日", "ライブラリ", "進捗")) tab(label).assertExists()
    }

    private companion object {
        const val ANIMATIONS_SETTLED = 1_000L
    }
}
