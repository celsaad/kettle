package com.casco.kettle.shell.home

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Program
import com.casco.kettle.domain.ProgramWeek
import com.casco.kettle.domain.RepsSetLog
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.SessionParams
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.WorkoutBlock
import com.casco.kettle.shell.files.FakeStoredFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.nav.Tab
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Library's Workouts section (#412), the list Today held until it narrowed to what's next: its tests
 * moved with it from HomeScreenTest, case for case from RN's `workouts-list.test.tsx`. In `pt`, since
 * an English assertion passes a hardcoded English string, with the files faked behind
 * [com.casco.kettle.shell.files.StoredFiles] and every route heard through a parameter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-w411dp-h1600dp")
class WorkoutsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<Route>()

    /** Every start, which opens the runner. */
    private val started = mutableListOf<RunnerStart>()
    private val reselects = MutableSharedFlow<Tab>(extraBufferCapacity = 1)

    private fun show(files: Files) {
        val latest =
            LatestFiles(files, CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).apply { reread() }
        compose.setContent {
            KettleTheme(darkTheme = false) {
                WorkoutsScreen(latest, reselects, open = { opened += it }, startRunner = { started += it })
            }
        }
    }

    private fun row(name: String) = compose.onNode(hasText(name) and isButton)

    private fun search(query: String) = compose.onNode(hasSetTextAction()).performTextInput(query)

    private fun text(value: String) = compose.onNodeWithText(value)

    private fun top(node: SemanticsNodeInteraction) = node.getBoundsInRoot().top

    /** A library is hand-written and shared, so the order in the file is the order its author meant. */
    @Test
    fun `lists workouts in the order the library file wrote them`() {
        show(listFiles())

        assertTrue(top(row("Zercher day")) < top(row("Bench day")))
        assertTrue(top(row("Bench day")) < top(row("Amrap day")))
    }

    /** The placeholder carries the whole library's count, which used to be a line under the title. */
    @Test
    fun `is translated`() {
        show(listFiles())

        compose.onNode(hasSetTextAction() and hasText("Buscar 3 treinos")).assertExists()
    }

    @Test
    fun `search narrows the list to one, and keeps the field to clear it with`() {
        show(listFiles())

        search("bench")

        row("Bench day").assertExists()
        row("Zercher day").assertDoesNotExist()
        row("Amrap day").assertDoesNotExist()
        // Down to one, and the field is still there to clear it with.
        compose.onNode(hasSetTextAction() and hasText("bench")).assertExists()
    }

    @Test
    fun `search ignores case and matches anywhere in the name`() {
        show(listFiles())

        search("DAY")

        listOf("Zercher day", "Bench day", "Amrap day").forEach { row(it).assertExists() }
    }

    /** "No workouts yet" is right on a fresh install and wrong in front of someone who mistyped one. */
    @Test
    fun `search says nothing matched rather than claiming there are no workouts`() {
        show(listFiles())

        search("zzz")

        text("Nada encontrado").assertExists()
        text("Nenhum treino ainda").assertDoesNotExist()
    }

    @Test
    fun `still tells a new library it has no workouts yet`() {
        show(Files(library(workouts = emptyList())))

        text("Nenhum treino ainda").assertExists()
        text("Nada encontrado").assertDoesNotExist()
    }

    // Nothing to search in, so the box would only be something else to tap past.
    @Test
    fun `offers no search box on an empty library`() {
        show(Files(library(workouts = emptyList())))

        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    // Keyed off the whole library: tied to what's visible it would vanish under the finger typing, and
    // with it the way to clear the search. Jest also pinned the placeholder's count staying at 3, which
    // a Compose field can't show: its placeholder goes once there is text, RN's reason the count never
    // narrows.
    @Test
    fun `keeps the search box while a search narrows the list to nothing`() {
        show(listFiles())

        search("zzz")

        row("Zercher day").assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText("zzz")).assertExists()
    }

    @Test
    fun `a row opens its workout in the editor, and its start button starts it`() {
        show(listFiles())

        row("Bench day").assertHeightIsAtLeast(48.dp).performClick()
        compose
            .onNode(hasContentDescription("Iniciar Bench day") and isButton)
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .performClick()

        assertEquals(listOf(Route(Destination.WorkoutEditor, mapOf("id" to "bench"))), opened)
        assertEquals(listOf(RunnerStart.Planned(SessionParams.Workout("bench"))), started)
    }

    @Test
    fun `with no workouts offers a way out, so the empty state is not a dead end`() {
        show(Files(library(workouts = emptyList())))

        compose
            .onNode(
                hasText("Adicionar um programa inicial") and isButton,
            ).assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals(listOf(Route(Destination.Import)), opened)
    }

    /** The program Today runs, so a row says it's in it rather than every program it might be in. */
    @Test
    fun `a workout the running program trains says so`() {
        val program = Program("base", "Base", listOf(ProgramWeek.Training(1, "Day 1", "bench")))
        show(Files(library(workouts = listOf(zercher, bench), programs = listOf(program))))

        row("Bench day").assert(hasText("No seu programa · ", substring = true))
        compose.onAllNodesWithText("No seu programa", substring = true).assertCountEquals(1)
    }

    /** #153 dropped re-tap-to-top with RN's bar; each tab's port brings it back for its own list. */
    @Test
    fun `a tap on the Library tab while Workouts shows scrolls back to the top`() {
        val many = (1..60).map { Workout("w$it", "Workout $it", emptyList()) }
        show(Files(library(workouts = many), sessions = listOf(session())))
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(many.size)
        compose.onNode(hasSetTextAction()).assertDoesNotExist()

        reselects.tryEmit(Tab.Today)
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()

        reselects.tryEmit(Tab.Library)
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
    }

    @Test
    fun `scrolls to reach the last row`() {
        val many = (1..60).map { Workout("w$it", "Workout $it", emptyList()) }
        show(Files(library(workouts = many), sessions = listOf(session())))

        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Workout 60"))

        row("Workout 60").assertIsDisplayed()
    }

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private class Files(
        var library: Library?,
        private val sessions: List<Session> = emptyList(),
    ) : FakeStoredFiles() {
        override fun sessionLog() = SessionLog(sessions, emptyList())

        override fun library() = library
    }

    private fun library(
        workouts: List<Workout> = listOf(pushDay),
        programs: List<Program> = emptyList(),
    ) = Library(1, listOf(pushups), workouts, programs)

    private fun listFiles() = Files(library(workouts = listOf(zercher, bench, never)))

    private fun session() = Session(1, "s", "push-day", null, null, null, NOW, NOW, listOf(oneSet))
}

private const val NOW = "2026-07-29T09:00:00Z"
private val pushups = Exercise("pushups", "Push-ups", ExerciseConfig.Reps(3, 10, restSec = 60.0))
private val oneSet = SessionEntry.Reps("pushups", listOf(RepsSetLog(10, restTakenSec = 60.0)))
private val pushDay = Workout("push-day", "Push day", listOf(WorkoutBlock.ExerciseBlock("pushups")))
private val zercher = Workout("zercher", "Zercher day", emptyList())
private val bench = Workout("bench", "Bench day", emptyList())
private val never = Workout("never", "Amrap day", emptyList())
