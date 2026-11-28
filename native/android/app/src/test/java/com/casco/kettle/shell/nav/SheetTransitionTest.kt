package com.casco.kettle.shell.nav

import androidx.activity.BackEventCompat
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.SessionParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The transitions table (#155), and the shell playing every case of it in its one composition (#377),
 * driven by the [Navigator]. Each route draws a stand-in that says whether it's composed and where it is,
 * so these hold what the screen shows at a move's first frame, part way and at its end: what arrives is
 * there from the first frame (nothing blank), and what leaves stays live until it has gone.
 *
 * Opacity and scale aren't positions, so the fades are held as which screens are there; [ShellMotionTest]
 * holds what each frame sets.
 */
@RunWith(RobolectricTestRunner::class)
class SheetTransitionTest {
    @get:Rule
    val compose = shellRule()

    private lateinit var navigator: Navigator
    private lateinit var shell: ShellView

    /** The stand-ins composed now, by name. */
    private val alive = mutableSetOf<String>()
    private val placed = mutableMapOf<String, LayoutCoordinates>()

    /** Each stand-in's composition, by name: the last one to compose it. */
    private val instances = mutableMapOf<String, Int>()
    private var compositions = 0

    private fun show() {
        navigator = Navigator()
        shell =
            compose.showShell(navigator) { route ->
                val name = standInName(route)
                // Which composition of the screen this is: a fresh one counts up.
                val instance = remember { ++compositions }
                SideEffect { instances[name] = instance }
                DisposableEffect(name) {
                    alive += name
                    onDispose {
                        alive -= name
                        placed -= name
                    }
                }
                Text(name, Modifier.onGloballyPositioned { placed[name] = it })
            }
    }

    /** A move, played out. */
    private fun go(move: Navigator.() -> Unit) {
        compose.runOnUiThread { navigator.move() }
        compose.waitForIdle()
    }

    /** A move and the first frame that draws it, with the clock stopped there. */
    private fun start(move: Navigator.() -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { navigator.move() }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun advance(millis: Long) = compose.mainClock.advanceTimeBy(millis)

    private fun finish() {
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    private val playing get() = compose.runOnIdle { navigator.motion.playing?.transition }

    private fun at(name: String): Offset = compose.runOnIdle { placed.getValue(name).positionInRoot() }

    private fun isAlive(name: String) = compose.runOnIdle { name in alive }

    private val height get() = shell.height.toFloat()
    private val width get() = shell.width.toFloat()

    /** Where a sheet's screen rests: 12dp under the status bar, which has none here, then the strip. */
    private val sheetRest get() = with(compose.density) { (12f + SheetStripHeight.value) * density }

    private val support = Route(Destination.Support)
    private val import = Route(Destination.Import)
    private val settings = Route(Destination.Settings)
    private val dips = Route(Destination.ExerciseDetail, mapOf("id" to "dips"))
    private val base = Route(Destination.ProgramDetail, mapOf("programId" to "base"))
    private val preview = Route(Destination.WorkoutPreview, mapOf("workoutId" to "bench"))
    private val bench = RunnerStart.Planned(SessionParams.Workout("bench"))

    @Test
    fun `a push from a tab slides the sheet up, and back slides it down`() {
        assertEquals(Transition.Open, transitionBetween(Place.Tab, Place.Sheet, MoveKind.Push))
        assertEquals(Transition.Close, transitionBetween(Place.Sheet, Place.Tab, MoveKind.Back))
    }

    /** Your file → Import, Import → Program guide, Workout preview → Workout editor. */
    @Test
    fun `a push inside the sheet moves along the shared axis, and back reverses it`() {
        assertEquals(Transition.Forward, transitionBetween(Place.Sheet, Place.Sheet, MoveKind.Push))
        assertEquals(Transition.Backward, transitionBetween(Place.Sheet, Place.Sheet, MoveKind.Back))
    }

    /** Program guide → Import replaces; a link, a program's delete and route-check navigate from the tabs. */
    @Test
    fun `a replace, and a navigate, fade through in place`() {
        assertEquals(Transition.FadeThrough, transitionBetween(Place.Sheet, Place.Sheet, MoveKind.Replace))
        assertEquals(Transition.FadeThrough, transitionBetween(Place.Sheet, Place.Sheet, MoveKind.Navigate))
        assertEquals(Transition.FadeIn, transitionBetween(Place.Tab, Place.Sheet, MoveKind.Navigate))
        assertEquals(Transition.FadeOut, transitionBetween(Place.Sheet, Place.Tab, MoveKind.Navigate))
    }

    /** Workout preview → session, the way into the runner from a sheet. */
    @Test
    fun `the sheet grows into the runner, and shrinks back out of it`() {
        assertEquals(Transition.Grow, transitionBetween(Place.Sheet, Place.Runner, MoveKind.Push))
        assertEquals(Transition.Shrink, transitionBetween(Place.Runner, Place.Sheet, MoveKind.Back))
    }

    @Test
    fun `moves that never touch a sheet move nothing of it`() {
        assertEquals(Transition.None, transitionBetween(Place.Tab, Place.Tab, MoveKind.Navigate))
        assertEquals(Transition.None, transitionBetween(Place.Tab, Place.Runner, MoveKind.Push))
    }

    /** The runner opened over a tab or a pushed screen closes onto it, and nothing of a sheet is left to move. */
    @Test
    fun `leaving the runner for a tab or a pushed screen releases the sheet`() {
        assertEquals(Transition.Release, transitionBetween(Place.Runner, Place.Tab, MoveKind.Back))
        assertEquals(Transition.Release, transitionBetween(Place.Runner, Place.Pushed, MoveKind.Back))
        // Program detail's Start: the runner opens over it as over a tab.
        assertEquals(Transition.None, transitionBetween(Place.Pushed, Place.Runner, MoveKind.Push))
    }

    /** #489: Today → Settings, Library → Exercise detail → Program detail, and back. */
    @Test
    fun `a pushed screen slides in from the side over a tab or another, and back slides it out`() {
        assertEquals(Transition.PushIn, transitionBetween(Place.Tab, Place.Pushed, MoveKind.Push))
        assertEquals(Transition.PushOut, transitionBetween(Place.Pushed, Place.Tab, MoveKind.Back))
        assertEquals(Transition.PushIn, transitionBetween(Place.Pushed, Place.Pushed, MoveKind.Push))
        assertEquals(Transition.PushOut, transitionBetween(Place.Pushed, Place.Pushed, MoveKind.Back))
    }

    /** A link, a restore or a delete reaches or leaves a pushed screen in place, as it does a sheet. */
    @Test
    fun `a navigate to or from a pushed screen fades in place`() {
        assertEquals(Transition.PushFadeIn, transitionBetween(Place.Tab, Place.Pushed, MoveKind.Navigate))
        assertEquals(Transition.PushFadeOut, transitionBetween(Place.Pushed, Place.Tab, MoveKind.Navigate))
        assertEquals(Transition.PushFadeOut, transitionBetween(Place.Pushed, Place.Pushed, MoveKind.Replace))
    }

    /** Settings → Support or Your file: a sheet covers a pushed screen as it covers a tab, and closes back onto it. */
    @Test
    fun `a sheet opens over a pushed screen as over a tab, and closes back onto it`() {
        assertEquals(Transition.Open, transitionBetween(Place.Pushed, Place.Sheet, MoveKind.Push))
        assertEquals(Transition.Close, transitionBetween(Place.Sheet, Place.Pushed, MoveKind.Back))
    }

    // The table, played.

    /** The first frame already draws the sheet's screen, below the frame, over the live tab: nothing blank. */
    @Test
    fun `Open slides the arriving sheet up from below the frame`() {
        show()

        start { push(support) }

        assertEquals(Transition.Open, playing)
        assertTrue(isAlive("Screen Today"))
        assertTrue(at("Screen Support").y >= height)
        advance(OPEN_MS / 2)
        assertTrue(at("Screen Support").y in sheetRest..height)
        finish()
        assertNull(playing)
        assertEquals(sheetRest, at("Screen Support").y)
    }

    @Test
    fun `Close slides the leaving sheet down, live, over the tab`() {
        show()
        go { push(support) }

        start { back() }

        assertEquals(Transition.Close, playing)
        assertTrue(isAlive("Screen Support"))
        assertEquals(sheetRest, at("Screen Support").y)
        advance(CLOSE_MS / 2)
        assertTrue(at("Screen Support").y > sheetRest)
        finish()
        assertEquals(false, isAlive("Screen Support"))
        assertTrue(isAlive("Screen Today"))
    }

    @Test
    fun `FadeIn and FadeOut reach and leave a sheet in place`() {
        show()

        start { navigate(support) }
        assertEquals(Transition.FadeIn, playing)
        assertTrue(isAlive("Screen Support"))
        finish()

        start { navigate(Route(Destination.Today)) }
        assertEquals(Transition.FadeOut, playing)
        advance(FADE_MS / 2)
        assertTrue(isAlive("Screen Support"))
        assertTrue(isAlive("Screen Today"))
        finish()
        assertEquals(false, isAlive("Screen Support"))
    }

    /** Import → Support: the old screen leaves to the start as the new one comes in from the end. */
    @Test
    fun `Forward hands the sheet over along the shared axis, both screens live`() {
        show()
        go { push(import) }

        start { push(support) }

        assertEquals(Transition.Forward, playing)
        advance(SHARED_AXIS_MS / 2)
        assertTrue(at("Screen Support").x > 0f)
        assertTrue(at("Screen Import").x < 0f)
        finish()
        assertEquals(false, isAlive("Screen Import"))
        assertEquals(0f, at("Screen Support").x)
    }

    @Test
    fun `Backward hands it back the other way`() {
        show()
        go { push(import) }
        go { push(support) }

        start { back() }

        assertEquals(Transition.Backward, playing)
        advance(SHARED_AXIS_MS / 2)
        assertTrue(at("Screen Import").x < 0f)
        assertTrue(at("Screen Support").x > 0f)
        finish()
        assertEquals(false, isAlive("Screen Support"))
        assertEquals(0f, at("Screen Import").x)
    }

    @Test
    fun `FadeThrough replaces a sheet in place`() {
        show()
        go { push(Route(Destination.ProgramGuide)) }

        start { replace(import) }

        assertEquals(Transition.FadeThrough, playing)
        advance(FADE_MS / 4)
        assertTrue(isAlive("Screen ProgramGuide"))
        assertTrue(isAlive("Screen Import"))
        finish()
        assertEquals(false, isAlive("Screen ProgramGuide"))
    }

    /** Settings → Support, and back: the pushed screen stays live under the scrim throughout. */
    @Test
    fun `Open and Close over a pushed screen leave it live under the sheet`() {
        show()
        go { push(settings) }

        start { push(support) }
        assertEquals(Transition.Open, playing)
        assertTrue(at("Screen Support").y >= height)
        finish()

        start { back() }
        assertEquals(Transition.Close, playing)
        advance(CLOSE_MS / 2)
        assertTrue(at("Screen Support").y > sheetRest)
        assertEquals(0f, at("Screen Settings").x)
        finish()
        assertEquals(false, isAlive("Screen Support"))
        assertTrue(isAlive("Screen Settings"))
    }

    /** A link to a sheet from a pushed screen, and from a sheet to a pushed screen, fade in place. */
    @Test
    fun `a navigate between a pushed screen and a sheet fades the sheet`() {
        show()
        go { push(settings) }

        start { navigate(support) }
        assertEquals(Transition.FadeIn, playing)
        finish()

        start { navigate(settings) }
        assertEquals(Transition.FadeOut, playing)
        advance(FADE_MS / 4)
        assertTrue(isAlive("Screen Support"))
        assertTrue(isAlive("Screen Settings"))
        finish()
        assertEquals(false, isAlive("Screen Support"))
    }

    /**
     * Workout preview → session: the sheet grows under the runner and stays composed there, and as the
     * runner goes it shrinks from full frame back to its place.
     */
    @Test
    fun `Grow and Shrink take the sheet into the runner and back`() {
        show()
        go { push(preview) }

        start { openRunner(bench) }
        assertEquals(Transition.Grow, playing)
        assertTrue(isAlive("Screen Session bench"))
        finish()
        assertTrue(isAlive("Screen WorkoutPreview bench"))

        start { closeRunner() }
        assertEquals(Transition.Shrink, playing)
        assertEquals(false, isAlive("Screen Session bench"))
        // Full frame at the start: the strip from the very top.
        val strip = with(compose.density) { SheetStripHeight.toPx() }
        assertEquals(strip, at("Screen WorkoutPreview bench").y)
        advance(RUNNER_MS / 2)
        assertTrue(at("Screen WorkoutPreview bench").y in strip..sheetRest)
        finish()
        assertEquals(sheetRest, at("Screen WorkoutPreview bench").y)
    }

    @Test
    fun `Release leaves the runner for a tab or a pushed screen with nothing to play`() {
        show()
        start { openRunner(bench) }
        assertNull(playing)
        finish()

        start { closeRunner() }
        assertNull(playing)
        finish()

        go { push(settings) }
        start { openRunner(bench) }
        assertNull(playing)
        finish()
        start { closeRunner() }
        assertNull(playing)
        assertEquals(0f, at("Screen Settings").x)
    }

    @Test
    fun `a tab switch plays nothing`() {
        show()

        start { select(Tab.Progress) }

        assertNull(playing)
    }

    @Test
    fun `PushIn slides a pushed screen in from the end over the live tab`() {
        show()

        start { push(settings) }

        assertEquals(Transition.PushIn, playing)
        assertEquals(width, at("Screen Settings").x)
        assertTrue(isAlive("Screen Today"))
        advance(PUSH_MS / 2)
        assertTrue(at("Screen Settings").x in 0f..width)
        finish()
        assertEquals(0f, at("Screen Settings").x)
    }

    /** Exercise detail → Program detail: the one beneath stays live, for a back to uncover. */
    @Test
    fun `PushIn over a pushed screen and PushOut back to it`() {
        show()
        go { push(dips) }

        start { push(base) }
        assertEquals(Transition.PushIn, playing)
        advance(PUSH_MS / 2)
        assertEquals(0f, at("Screen ExerciseDetail dips").x)
        assertTrue(at("Screen ProgramDetail base").x > 0f)
        finish()
        assertTrue(isAlive("Screen ExerciseDetail dips"))

        start { back() }
        assertEquals(Transition.PushOut, playing)
        advance(POP_MS / 2)
        assertTrue(at("Screen ProgramDetail base").x in 0f..width)
        assertEquals(0f, at("Screen ExerciseDetail dips").x)
        finish()
        assertEquals(false, isAlive("Screen ProgramDetail base"))
    }

    @Test
    fun `PushOut slides the leaving screen out by its side, live, over the tab`() {
        show()
        go { push(settings) }

        start { back() }

        assertEquals(Transition.PushOut, playing)
        assertEquals(0f, at("Screen Settings").x)
        advance(POP_MS / 2)
        assertTrue(at("Screen Settings").x > 0f)
        finish()
        assertEquals(false, isAlive("Screen Settings"))
    }

    @Test
    fun `PushFadeIn and PushFadeOut reach and leave a pushed screen in place`() {
        show()

        start { navigate(settings) }
        assertEquals(Transition.PushFadeIn, playing)
        // In place, not from the side: only the fade's 92% scale, about its bottom centre, moves it in.
        assertTrue(at("Screen Settings").x < width * (1 - ShellMotion.FADE_SCALE))
        finish()
        assertEquals(0f, at("Screen Settings").x)

        start { navigate(Route(Destination.Today)) }
        assertEquals(Transition.PushFadeOut, playing)
        advance(FADE_MS / 4)
        assertTrue(isAlive("Screen Settings"))
        finish()
        assertEquals(false, isAlive("Screen Settings"))
    }

    @Test
    fun `a pushed screen replaced fades out over the one in its place`() {
        show()
        go { push(dips) }

        start { replace(base) }

        assertEquals(Transition.PushFadeOut, playing)
        advance(FADE_MS / 4)
        assertTrue(isAlive("Screen ExerciseDetail dips"))
        assertTrue(isAlive("Screen ProgramDetail base"))
        finish()
        assertEquals(false, isAlive("Screen ExerciseDetail dips"))
    }

    /** A second move part way through the first ends the first where it ends, and plays from there. */
    @Test
    fun `a move during a transition ends it and starts its own`() {
        show()
        start { push(import) }
        advance(OPEN_MS / 4)

        compose.runOnUiThread { navigator.push(support) }
        compose.mainClock.advanceTimeByFrame()

        assertEquals(Transition.Forward, playing)
        finish()
        assertEquals(sheetRest, at("Screen Support").y)
        assertEquals(false, isAlive("Screen Import"))
    }

    /**
     * A route opened again while its last opening is still leaving is a new screen: the one leaving held
     * a discarded editor's draft, and its effects have run.
     */
    private fun assertReopenedFresh(route: Route) {
        show()
        go { push(route) }
        val name = standInName(route)
        val first = instances.getValue(name)

        start { back() }
        advance(REOPEN_MS)
        compose.runOnUiThread { navigator.push(route) }
        compose.mainClock.advanceTimeByFrame()
        finish()

        assertTrue("composed afresh, not opening ${instances[name]} again", instances.getValue(name) > first)
    }

    @Test
    fun `a sheet opened again during its close is a fresh screen`() = assertReopenedFresh(import)

    @Test
    fun `a pushed screen opened again as it leaves is a fresh screen`() = assertReopenedFresh(settings)

    // A move part way through another starts from where that one had got to.

    @Test
    fun `back during an Open closes from where the sheet had got to`() {
        show()
        start { push(support) }
        advance(CUT_MS)
        val there = at("Screen Support").y
        assertTrue(there > sheetRest)

        compose.runOnUiThread { navigator.back() }
        compose.mainClock.advanceTimeByFrame()

        assertEquals(Transition.Close, playing)
        assertTrue("from ${at("Screen Support").y}, not $there", at("Screen Support").y >= there - 1f)
        finish()
    }

    @Test
    fun `back during a PushIn pushes out from where the screen had got to`() {
        show()
        start { push(settings) }
        advance(CUT_MS)
        val there = at("Screen Settings").x
        assertTrue(there > 0f)

        compose.runOnUiThread { navigator.back() }
        compose.mainClock.advanceTimeByFrame()

        assertEquals(Transition.PushOut, playing)
        assertTrue("from ${at("Screen Settings").x}, not $there", at("Screen Settings").x >= there - 1f)
        finish()
    }

    @Test
    fun `a grab during an Open holds the sheet where it had got to`() {
        show()
        start { push(support) }
        advance(CUT_MS)
        val there = at("Screen Support").y

        compose.onNodeWithContentDescription("Drag handle").performTouchInput {
            down(center)
            moveBy(Offset(0f, GRAB_PX))
        }
        compose.mainClock.advanceTimeByFrame()

        assertNull(playing)
        // The injected touch's own time lets the Open run on a few ms before the drag takes it; at rest it
        // would be a whole Open short of there.
        val held = at("Screen Support").y
        assertTrue("held at $held, not near $there", held >= there - TOUCH_SLACK_PX)
        compose.onNodeWithContentDescription("Drag handle").performTouchInput { up() }
        finish()
    }

    /**
     * A short drag on a sheet caught part way up its Open stays: only the hand's own travel counts towards
     * closing it, not how far down the Open had it when the hand took it.
     */
    @Test
    fun `a short grabber drag during an Open leaves the sheet open`() {
        show()
        start { push(support) }
        advance(CUT_MS)

        val grabber = compose.onNodeWithContentDescription("Drag handle")
        grabber.performTouchInput {
            down(center)
            repeat(DRAG_STEPS) { moveBy(Offset(0f, SHORT_DRAG_PX / DRAG_STEPS), delayMillis = DRAG_STEP_MS) }
        }
        compose.mainClock.advanceTimeByFrame()
        // The hand took the sheet part way up, more than a quarter of the frame below its rest.
        assertNull(playing)
        assertTrue("at ${at("Screen Support").y}", at("Screen Support").y > sheetRest + height / 4)
        // Held still before letting go, so the release is no fling.
        grabber.performTouchInput {
            repeat(DRAG_STEPS) { moveBy(Offset.Zero, delayMillis = HOLD_STEP_MS) }
            up()
        }
        finish()

        assertEquals(listOf(support), navigator.sheets)
        assertEquals(sheetRest, at("Screen Support").y)
    }

    // Predictive back.

    private fun backStarted() =
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(backEvent(0f)) }

    private fun backAt(progress: Float) =
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(backEvent(progress)) }

    private fun backEvent(progress: Float) = BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT)

    /** The sheet shrinks towards its bottom as the gesture goes, so its screen's top comes down. */
    @Test
    fun `a predictive back shrinks the sheet, and its commit closes it from there`() {
        show()
        go { push(support) }

        backStarted()
        backAt(1f)
        compose.waitForIdle()
        assertTrue(at("Screen Support").y > sheetRest)

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(emptyList<Route>(), navigator.sheets)
        assertEquals(false, isAlive("Screen Support"))
    }

    /** Sheet over sheet: the commit goes back one sheet, not the whole stack. */
    @Test
    fun `a predictive back over a stacked sheet goes back one sheet`() {
        show()
        go { push(import) }
        go { push(support) }

        backStarted()
        backAt(PART_WAY)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        assertEquals(listOf(import), navigator.sheets)
        assertEquals(sheetRest, at("Screen Import").y)
    }

    @Test
    fun `a cancelled predictive back settles the sheet back, and goes nowhere`() {
        show()
        go { push(support) }

        backStarted()
        backAt(PART_WAY)
        compose.waitForIdle()
        // Shrunk first, so the settle has somewhere to come back from.
        assertTrue(at("Screen Support").y > sheetRest)

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()

        assertEquals(listOf(support), navigator.sheets)
        assertEquals(sheetRest, at("Screen Support").y)
    }

    /** A pushed screen starts out by its side under the gesture, then leaves from there (#489). */
    @Test
    fun `a predictive back slides a pushed screen towards its side`() {
        show()
        go { push(settings) }

        backStarted()
        backAt(1f)
        compose.waitForIdle()
        assertEquals(width * ShellMotion.PREDICTIVE_SLIDE, at("Screen Settings").x)

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(emptyList<Route>(), navigator.sheets)
    }

    private companion object {
        const val OPEN_MS = ShellMotion.OPEN_MS.toLong()
        const val CLOSE_MS = ShellMotion.CLOSE_MS.toLong()
        const val SHARED_AXIS_MS = ShellMotion.SHARED_AXIS_MS.toLong()
        const val FADE_MS = ShellMotion.FADE_MS.toLong()
        const val RUNNER_MS = ShellMotion.RUNNER_MS.toLong()
        const val PUSH_MS = ShellMotion.PUSH_MS.toLong()
        const val POP_MS = ShellMotion.POP_MS.toLong()
        const val PART_WAY = 0.5f

        /** Into a Close or a PushOut, with what leaves still on screen. */
        const val REOPEN_MS = 50L

        /** Into an Open or a PushIn: a few frames, well short of its end. */
        const val CUT_MS = 48L
        const val GRAB_PX = 40f
        const val TOUCH_SLACK_PX = 20f

        /** Well short of a quarter of the frame, in steps slow enough to be no fling. */
        const val SHORT_DRAG_PX = 32f
        const val DRAG_STEPS = 2
        const val DRAG_STEP_MS = 8L
        const val HOLD_STEP_MS = 60L
    }
}
