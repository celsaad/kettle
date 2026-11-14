package com.casco.kettle.shell.nav

import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.SessionParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigatorTest {
    private val reselected = mutableListOf<Tab>()
    private var runnerClosed = 0
    private val navigator = Navigator(onReselect = reselected::add, onRunnerClosed = { runnerClosed++ })

    private val bench = RunnerStart.Planned(SessionParams.Workout("bench"))

    private val settings = Route(Destination.Settings)
    private val support = Route(Destination.Support)

    private fun editor(id: String) = Route(Destination.ExerciseEditor, mapOf("id" to id))

    @Test
    fun `it opens on Today with the bar up and nothing above it`() {
        assertEquals(Tab.Today, navigator.tab)
        assertTrue(navigator.barShown)
        assertEquals(Place.Tab, navigator.place)
        assertNull(navigator.sheet)
    }

    @Test
    fun `a tap selects a tab, and a tap on the tab showing is a reselect`() {
        navigator.select(Tab.Progress)
        navigator.select(Tab.Progress)

        assertEquals(Tab.Progress, navigator.tab)
        assertEquals(listOf(Tab.Progress), reselected)
    }

    @Test
    fun `a pushed sheet covers the tab and back pops it`() {
        navigator.select(Tab.Library)
        navigator.push(editor("pushups"))

        assertEquals(editor("pushups"), navigator.sheet)
        assertEquals(Place.Sheet, navigator.place)
        assertFalse(navigator.barShown)

        assertTrue(navigator.back())

        assertNull(navigator.sheet)
        assertEquals(Tab.Library, navigator.tab)
        assertTrue(navigator.barShown)
    }

    @Test
    fun `sheets stack, and back walks down them one at a time`() {
        navigator.push(settings)
        navigator.push(support)

        assertEquals(support, navigator.sheet)

        navigator.back()

        assertEquals(settings, navigator.sheet)
    }

    /** The program guide's hand-off to Import: back from Import skips the guide. */
    @Test
    fun `a replace takes the top sheet's place`() {
        navigator.push(Route(Destination.ProgramGuide))
        navigator.replace(Route(Destination.Import))

        assertEquals(Route(Destination.Import), navigator.sheet)

        navigator.back()

        assertNull(navigator.sheet)
    }

    @Test
    fun `back from a tab goes to Today, and from Today leaves the app`() {
        navigator.select(Tab.Library)

        assertTrue(navigator.back())
        assertEquals(Tab.Today, navigator.tab)
        assertFalse(navigator.back())
    }

    /** A link, `NavigateReceiver`, a program's delete: from wherever, as if from the tabs. */
    @Test
    fun `navigate dismisses every sheet first, then goes`() {
        navigator.push(settings)
        navigator.push(support)

        navigator.navigate(Route(Destination.Progress))

        assertNull(navigator.sheet)
        assertEquals(Tab.Progress, navigator.tab)

        navigator.push(settings)
        navigator.navigate(editor("pushups"))

        assertEquals(editor("pushups"), navigator.sheet)
        navigator.back()
        assertNull(navigator.sheet)
    }

    @Test
    fun `the runner fills its slot over everything, the bar gone`() {
        navigator.push(Route(Destination.ProgramDetail))

        navigator.openRunner(bench)

        assertEquals(RunnerSlot(bench), navigator.runner)
        assertEquals(Place.Runner, navigator.place)
        assertTrue(navigator.runnerShown)
        assertFalse(navigator.barShown)
    }

    /** What the stub's `usePreventRemove` did: the runner owns its exits (#297, #302). */
    @Test
    fun `while the runner shows, every other navigation is dropped`() {
        navigator.select(Tab.Progress)
        navigator.push(settings)
        navigator.openRunner(bench)

        navigator.select(Tab.Library)
        navigator.push(support)
        navigator.replace(support)
        navigator.navigate(Route(Destination.Library))
        navigator.navigate(Route(Destination.Session, mapOf("adhoc" to "1")))
        navigator.openRunner(RunnerStart.Adhoc)

        assertTrue("back is taken, not passed on to leave the app", navigator.back())
        assertEquals(RunnerSlot(bench), navigator.runner)
        assertEquals(settings, navigator.sheet)
        assertEquals(Tab.Progress, navigator.tab)
    }

    @Test
    fun `done pops the slot, back to whatever the runner opened over`() {
        navigator.select(Tab.Library)
        navigator.push(Route(Destination.ProgramDetail))
        navigator.openRunner(bench)

        navigator.closeRunner()

        assertNull(navigator.runner)
        assertEquals(Route(Destination.ProgramDetail), navigator.sheet)
        assertEquals(Tab.Library, navigator.tab)
        assertEquals(1, runnerClosed)
    }

    /** `kettle://session`, and route-check's own message for it. */
    @Test
    fun `navigating to the session route opens the runner with the start it names`() {
        navigator.push(settings)

        navigator.navigate(Route(Destination.Session, mapOf("workoutId" to "bench")))

        assertEquals(RunnerSlot(bench), navigator.runner)
        assertEquals("the runner opens over what showed, as a push", settings, navigator.sheet)

        navigator.closeRunner()
        navigator.navigate(Route(Destination.Session))

        assertEquals(RunnerSlot(null), navigator.runner)
    }

    /**
     * What the sheet animates from, in place of RN's route reports: told before a move, while the screen
     * still shows the frame it pictures, and after, with where it went.
     */
    @Test
    fun `each move is told before it lands and after, with its kind`() {
        val heard = mutableListOf<String>()
        navigator.moves =
            object : MoveListener {
                override fun willMove(move: Move) {
                    heard += "will ${move.kind} ${navigator.place}"
                }

                override fun moved(move: Move) {
                    heard += "moved ${move.from}->${move.to} ${navigator.place}"
                }
            }

        navigator.push(settings)
        navigator.replace(support)
        navigator.openRunner(bench)
        navigator.select(Tab.Progress)
        navigator.closeRunner()
        navigator.back()

        assertEquals(
            listOf(
                "will Push Tab",
                "moved Tab->Sheet Sheet",
                "will Replace Sheet",
                "moved Sheet->Sheet Sheet",
                "will Push Sheet",
                "moved Sheet->Runner Runner",
                "will Back Runner",
                "moved Runner->Sheet Sheet",
                "will Back Sheet",
                "moved Sheet->Tab Tab",
            ),
            heard,
        )
    }

    /**
     * An activity made again (a font or display size change, or the process brought back) starts a new
     * navigator; the stack it saved puts the user back where they were, not on Today.
     */
    @Test
    fun `a saved back stack restores the tab and every sheet over it`() {
        navigator.select(Tab.Library)
        navigator.push(editor("pushups"))
        navigator.push(settings)

        val restored = Navigator().apply { restore(navigator.saved()) }

        assertEquals(Tab.Library, restored.tab)
        assertEquals(settings, restored.sheet)
        restored.back()
        assertEquals(editor("pushups"), restored.sheet)
    }

    /** The slot isn't saved: a running session is attached to again at launch, and a done one is gone. */
    @Test
    fun `the runner's slot is not saved`() {
        navigator.push(settings)
        navigator.openRunner(bench)

        val restored = Navigator().apply { restore(navigator.saved()) }

        assertNull(restored.runner)
        assertEquals(settings, restored.sheet)
    }

    /** Saved by a build whose routes differ: what still parses comes back, and nothing else. */
    @Test
    fun `a saved stack restores what still parses`() {
        navigator.restore(listOf("Library", "/nowhere", "/settings", "/exercise-editor?id=%zz"))

        assertEquals(Tab.Library, navigator.tab)
        assertEquals(settings, navigator.sheet)
        navigator.back()
        assertNull(navigator.sheet)

        navigator.restore(listOf("NoSuchTab"))
        assertEquals(Tab.Today, navigator.tab)
    }

    @Test
    fun `opening a tab's route goes to the tab rather than stacking it`() {
        navigator.push(settings)

        navigator.push(Route(Destination.Library))

        assertNull(navigator.sheet)
        assertEquals(Tab.Library, navigator.tab)
    }

    /** #411: a tab keeps the section it was sent to, a saved stack keeps it, and the bar returns to it. */
    @Test
    fun `a tab's section comes back with the tab, saved and restored too`() {
        val stats = Route.of(Section.SESSIONS)
        navigator.navigate(stats)
        navigator.select(Tab.Today)
        navigator.select(Tab.Progress)

        assertEquals(stats, navigator.tabRoute(Tab.Progress))
        assertEquals(stats, Navigator().apply { restore(navigator.saved()) }.tabRoute(Tab.Progress))
    }

    /** A stack the four-tab shell saved names its tab, which lands on the tab and section holding it now. */
    @Test
    fun `a stack saved by the four-tab shell lands on the new tabs`() {
        navigator.restore(listOf("Programs", "/settings"))
        assertEquals(Tab.Library, navigator.tab)
        assertEquals(Section.PROGRAMS, navigator.tabRoute(Tab.Library).section(Tab.Library))
        assertEquals(settings, navigator.sheet)

        // Library was Exercises alone then, and Workouts now leads its switch (#412).
        navigator.restore(listOf("Library"))
        assertEquals(Tab.Library, navigator.tab)
        assertEquals(Section.EXERCISES, navigator.tabRoute(Tab.Library).section(Tab.Library))

        navigator.restore(listOf("History"))
        assertEquals(Tab.Progress, navigator.tab)
        navigator.restore(listOf("Workouts"))
        assertEquals(Tab.Today, navigator.tab)
    }
}
