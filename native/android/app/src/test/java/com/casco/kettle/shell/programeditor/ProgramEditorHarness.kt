package com.casco.kettle.shell.programeditor

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.casco.kettle.domain.CircuitMember
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.OverrideValue
import com.casco.kettle.domain.Program
import com.casco.kettle.domain.ProgramOverride
import com.casco.kettle.domain.ProgramWeek
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.WorkoutBlock
import com.casco.kettle.shell.files.FakeStoredFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Rule
import java.io.IOException
import java.util.concurrent.Executor

/** The program editor tests' shared harness: the screen on faked files, and what each test reads back. */
abstract class ProgramEditorHarness {
    @get:Rule
    val compose = createComposeRule()

    protected var closed = 0
    protected var deleted = 0

    /** [io] holds the file work until it's run, for a test that needs a write caught mid-way. */
    protected fun show(
        id: String? = null,
        files: Files = Files(),
        io: Steps? = null,
    ): Files {
        val dispatcher = io?.asCoroutineDispatcher() ?: Dispatchers.Unconfined
        val latest = LatestFiles(files, CoroutineScope(Dispatchers.Unconfined), io = dispatcher).apply { reread() }
        io?.run()
        compose.setContent {
            KettleTheme(darkTheme = false) {
                ProgramEditorScreen(
                    id,
                    latest,
                    ProgramEditorActions(
                        close = { closed += 1 },
                        deleted = { deleted += 1 },
                    ),
                )
            }
        }
        return files
    }

    protected val isControl =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button) or
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)

    protected fun labelled(label: String) = hasText(label) or hasContentDescription(label)

    /** A node showing or named [label]; a field's label and the field it names are both one. */
    protected fun named(label: String) = compose.onAllNodes(labelled(label)).onFirst()

    protected fun press(
        label: String,
        index: Int = 0,
    ) = compose.onAllNodes(labelled(label) and isControl)[index].performClick()

    protected fun type(
        name: String,
        text: String,
        index: Int = 0,
    ) = compose.onAllNodes(hasContentDescription(name))[index].performTextReplacement(text)

    /** jest's `pressAlertButton`: a button inside the dialog, never a same-named one on the screen under it. */
    protected fun ComposeContentTestRule.pressDialogButton(label: String) =
        onNode(hasText(label) and hasAnyAncestor(isDialog())).performClick()

    /** Names the draft and adds [count] weeks: the shortest path to a program Save takes. */
    protected fun draftWith(
        count: Int,
        name: String = "Base",
    ): Files {
        val files = show()
        type("Nome", name)
        repeat(count) { press("+ Adicionar semana") }
        return files
    }

    protected fun Files.savedWeeks() =
        written
            .last()
            .programs
            .single()
            .weeks

    protected fun absent(text: String) = compose.onAllNodesWithText(text).assertCountEquals(0)

    // validation

    protected fun Files.savedOverrides() = (savedWeeks().single() as ProgramWeek.Training).overrides

    protected class Files(
        private val workouts: List<Workout> = listOf(pushDay, pullDay, empty, bareDay),
        private val programs: List<Program> = emptyList(),
    ) : FakeStoredFiles() {
        private val library = Library(1, listOf(pullUps, dips, rows), workouts, programs)
        val written = mutableListOf<Library>()

        /** Thrown by the next write, as a full disk would. */
        var failure: IOException? = null

        override fun library() = written.lastOrNull() ?: library

        override fun writeLibrary(library: Library) {
            failure?.let { throw it }
            written += library
        }
    }
}

internal val pullUps =
    Exercise("pull-ups", "Pull-ups", ExerciseConfig.Reps(sets = 4, targetRepsMin = 6, restSec = 90.0))
internal val dips = Exercise("dips", "Dips", ExerciseConfig.Reps(sets = 4, targetRepsMin = 6, restSec = 90.0))
internal val rows = Exercise("rows", "Rows", ExerciseConfig.Reps(sets = 3, targetRepsMin = 8, restSec = 60.0))

internal val finisher =
    WorkoutBlock.Circuit(
        id = "finisher",
        rounds = 3,
        restBetweenExercisesSec = 15.0,
        restBetweenRoundsSec = 60.0,
        members = listOf(CircuitMember("pull-ups"), CircuitMember("dips")),
    )
internal val pushDay = Workout("push-day", "Push day", listOf(WorkoutBlock.ExerciseBlock("pull-ups"), finisher))
internal val pullDay = Workout("pull-day", "Pull day", listOf(WorkoutBlock.ExerciseBlock("rows")))
internal val empty = Workout("empty", "Empty", emptyList())

/** A circuit with no rests of its own, which the panel still shows as 0. */
internal val bareDay =
    Workout(
        "bare-day",
        "Bare day",
        listOf(
            WorkoutBlock.Circuit(
                id = "bare",
                rounds = 3,
                members = listOf(CircuitMember("pull-ups"), CircuitMember("dips")),
            ),
        ),
    )

internal fun finisherRest(seconds: Double) =
    ProgramOverride.BlockOverride("finisher", mapOf("rest_between_exercises_sec" to OverrideValue.Numeric(seconds)))

internal fun base(
    vararg overrides: ProgramOverride,
    workoutId: String = "push-day",
) = Program(
    "base",
    "Base",
    listOf(ProgramWeek.Training(1, workoutId = workoutId, overrides = overrides.toList().ifEmpty { null })),
)

internal fun exerciseOverride(
    id: String,
    vararg config: Pair<String, Double>,
) = ProgramOverride.ExerciseOverride(id, config.associate { (key, value) -> key to OverrideValue.Numeric(value) })

internal const val SETTLE_ROUNDS = 3

/** A dispatcher that runs nothing until told to, so a test holds a write mid-way. */
class Steps : Executor {
    private val queued = ArrayDeque<Runnable>()

    override fun execute(task: Runnable) {
        queued += task
    }

    fun run() {
        while (queued.isNotEmpty()) queued.removeFirst().run()
    }
}

/** A text that reads like an i18next key path, "programEditor.week". */
internal val KEY_PATH = Regex("^[a-z][a-zA-Z]*\\.[a-zA-Z.]+$")

internal fun hasTextMatching(pattern: Regex) =
    SemanticsMatcher("text matches $pattern") { node ->
        node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { pattern.matches(it.text) }
    }
