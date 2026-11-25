package com.casco.kettle.shell.workouteditor

import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Program
import com.casco.kettle.domain.ProgramWeek
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.WorkoutBlock
import com.casco.kettle.shell.files.FakeStoredFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import java.io.IOException
import java.util.concurrent.Executor

/**
 * The workout editor tests' shared harness: the screen on faked files, the helpers that drive it, and
 * the fixtures. Split out of `WorkoutEditorScreenTest` when it outgrew detekt's class size, as the
 * import and program editor suites were.
 */
abstract class WorkoutEditorHarness {
    @get:Rule
    val compose = createComposeRule()

    protected var closed = 0

    protected lateinit var latest: LatestFiles

    protected lateinit var focus: FocusManager

    protected fun show(
        id: String? = null,
        files: Files = Files(),
        io: CoroutineDispatcher = Dispatchers.Unconfined,
    ): Files {
        latest = LatestFiles(files, CoroutineScope(Dispatchers.Unconfined), io = io).apply { reread() }
        compose.setContent {
            focus = LocalFocusManager.current
            KettleTheme(darkTheme = false) {
                WorkoutEditorScreen(
                    id,
                    latest,
                    WorkoutEditorActions(close = { closed += 1 }),
                )
            }
        }
        return files
    }

    /**
     * [matcher]'s node, scrolled into the list first: a `LazyColumn` composes only what's on screen,
     * so a row below the fold isn't in the tree to find until the list has been scrolled to it.
     */
    protected fun node(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(matcher)
        return compose.onNode(matcher)
    }

    protected fun named(label: String) = node(hasText(label) or hasContentDescription(label))

    protected fun press(label: String) =
        node((hasText(label) or hasContentDescription(label)) and isControl).performClick()

    protected fun type(
        name: String,
        text: String,
    ) = node(hasContentDescription(name)).performTextReplacement(text)

    /** A button inside the dialog, never a same-named one on the screen under it. */
    protected fun ComposeContentTestRule.pressDialogButton(label: String) =
        onNode(hasText(label) and hasAnyAncestor(isDialog())).performClick()

    /** A control inside the dialog, by its words or its name: the time picker's steppers. */
    protected fun pressInDialog(label: String) =
        compose.onNode((hasText(label) or hasContentDescription(label)) and hasAnyAncestor(isDialog())).performClick()

    protected val isControl =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button) or
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    /**
     * Runs [io]'s queued file work, and the main thread's after it, until neither has any: a write's
     * continuation resumes on the main thread, and may queue the next write.
     */
    protected fun settle(io: Steps) {
        repeat(SETTLE_ROUNDS) {
            io.run()
            compose.waitForIdle()
        }
    }

    protected fun grip(name: String) = named("Reordenar $name")

    /** The blocks on screen, by their grips: the board drops RN's "3 blocks" line. */
    protected fun blockCount() =
        compose.onAllNodes(hasContentDescription("Reordenar", substring = true)).fetchSemanticsNodes().size

    /**
     * Runs the grip's custom action [label], as TalkBack's Actions menu does. Through the semantics
     * themselves, not `performCustomAccessibilityActionWithLabel`, which is experimental API.
     */
    protected fun SemanticsNodeInteraction.moveWith(label: String) {
        val action = fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == label }
        compose.runOnIdle { action.action() }
    }

    protected fun Files.savedWorkout(id: String) = written.last().workouts.single { it.id == id }

    protected fun addBlocks(vararg names: String) {
        for (name in names) {
            press("+ Adicionar exercício")
            press(name)
        }
    }

    protected fun buildCircuit(vararg names: String) {
        press("+ Adicionar circuito")
        for (name in names) press(name)
        press("Adicionar circuito (${names.size})")
    }

    protected class Files(
        private val workouts: List<Workout> = emptyList(),
        private val programs: List<Program> = emptyList(),
    ) : FakeStoredFiles() {
        private var library = Library(1, listOf(pullUps, dips, rows, rest), workouts, programs)
        val written = mutableListOf<Library>()

        /** Thrown by the next write, as a full disk would. */
        var failure: IOException? = null

        override fun library() = library

        /** RN writing the library behind the editor's back, as `.dev`'s second writer can. */
        fun rewrite(change: (Library) -> Library) {
            library = change(library)
        }

        override fun writeLibrary(library: Library) {
            failure?.let { throw it }
            written += library
            this.library = library
        }
    }
}

internal val pullUps =
    Exercise("pull-ups", "Pull-ups", ExerciseConfig.Reps(sets = 3, targetRepsMin = 8, restSec = 90.0))
internal val dips = Exercise("dips", "Dips", ExerciseConfig.Reps(sets = 3, targetRepsMin = 10, restSec = 90.0))

/** A third, so a removal has a circuit it may remove from. */
internal val rows = Exercise("rows", "Rows", ExerciseConfig.Reps(sets = 3, targetRepsMin = 10, restSec = 90.0))
internal val rest = Exercise("rest", "Rest", ExerciseConfig.Rest(durationSec = 60.0))

internal val base = Program("base", "Base", listOf(ProgramWeek.Training(week = 1, workoutId = "push-day")))

internal fun pushDay(vararg blocks: WorkoutBlock) =
    Workout("push-day", "Push day", blocks.toList().ifEmpty { listOf(WorkoutBlock.ExerciseBlock("pull-ups")) })

internal fun Workout.exerciseIds() = blocks.map { (it as WorkoutBlock.ExerciseBlock).exerciseId }

internal const val SETTLE_ROUNDS = 3

/** A dispatcher that runs nothing until told to, so a test holds a write or a read mid-way. */
class Steps : Executor {
    private val queued = ArrayDeque<Runnable>()

    override fun execute(task: Runnable) {
        queued += task
    }

    fun run() {
        while (queued.isNotEmpty()) queued.removeFirst().run()
    }

    fun runFirst() = queued.removeFirst().run()
}

internal fun hasStateDescription(text: String) =
    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

internal fun SemanticsNodeInteraction.actionLabels() =
    fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
