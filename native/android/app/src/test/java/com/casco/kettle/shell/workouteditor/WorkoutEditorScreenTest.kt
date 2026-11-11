package com.casco.kettle.shell.workouteditor

import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.CircuitMember
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
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.Executor

/**
 * The workout editor (#242), case for case from RN's `workout-editor.test.tsx`, plus the move half of
 * `reorderable-list.test.tsx` as the custom actions that replace RN's slider, on the harness recipe
 * (#193): in `pt`, with the files faked behind `StoredFiles`. Each asserts on the library written.
 *
 * The draft's own rules are `:shared`'s (`WorkoutEditingTest`); these cover the wiring. A drag itself
 * is a device check, since the library owns its hit-testing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class WorkoutEditorScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var closed = 0

    private lateinit var latest: LatestFiles

    private lateinit var focus: FocusManager

    private fun show(
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
    private fun node(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(matcher)
        return compose.onNode(matcher)
    }

    private fun named(label: String) = node(hasText(label) or hasContentDescription(label))

    private fun press(label: String) =
        node((hasText(label) or hasContentDescription(label)) and isControl).performClick()

    private fun type(
        name: String,
        text: String,
    ) = node(hasContentDescription(name)).performTextReplacement(text)

    /** jest's `pressAlertButton`: a button inside the dialog, never a same-named one on the screen under it. */
    private fun ComposeContentTestRule.pressDialogButton(label: String) =
        onNode(hasText(label) and hasAnyAncestor(isDialog())).performClick()

    private val isControl =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button) or
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    /**
     * Runs [io]'s queued file work, and the main thread's after it, until neither has any: a write's
     * continuation resumes on the main thread, and may queue the next write.
     */
    private fun settle(io: Steps) {
        repeat(SETTLE_ROUNDS) {
            io.run()
            compose.waitForIdle()
        }
    }

    private fun grip(name: String) = named("Reordenar $name")

    /**
     * Runs the grip's custom action [label], as TalkBack's Actions menu does. Through the semantics
     * themselves, not `performCustomAccessibilityActionWithLabel`, which is experimental API.
     */
    private fun SemanticsNodeInteraction.moveWith(label: String) {
        val action = fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == label }
        compose.runOnIdle { action.action() }
    }

    private fun Files.savedWorkout(id: String) = written.last().workouts.single { it.id == id }

    private fun addBlocks(vararg names: String) {
        for (name in names) {
            press("+ Adicionar bloco")
            press(name)
        }
    }

    private fun buildCircuit(vararg names: String) {
        press("+ Novo circuito")
        for (name in names) press(name)
        press("Adicionar circuito (${names.size})")
    }

    // saving

    @Test
    fun `refuses a blank name instead of writing an unnamed workout`() {
        val files = show()

        press("Salvar")

        named("O nome é obrigatório.").assertExists()
        assertEquals(emptyList<Library>(), files.written)
        assertEquals(0, closed)
    }

    // An id of "" is unreachable by every lookup by id, this editor's own included.
    @Test
    fun `refuses a name that yields no id, rather than saving one that cannot be addressed`() {
        val files = show()

        type("Nome", "🏋️🏋️")
        press("Salvar")

        named("Não foi possível gerar um id a partir desse nome.").assertExists()
        assertEquals(emptyList<Library>(), files.written)
    }

    @Test
    fun `derives the id from the name and trims it`() {
        val files = show()

        type("Nome", "  Push day  ")
        press("Salvar")

        assertEquals("Push day", files.savedWorkout("push-day").name)
        assertEquals(1, closed)
    }

    // Program weeks point at workouts by id: re-deriving it would orphan every one.
    @Test
    fun `keeps an existing id when the workout is renamed`() {
        val files = show("push-day", Files(workouts = listOf(pushDay())))

        type("Nome", "Upper body")
        press("Salvar")

        assertEquals(listOf(pushDay().copy(name = "Upper body")), files.written.single().workouts)
    }

    // Degrading beats losing the edit: the sheet stays open, with it, and says why.
    @Test
    fun `stays open and says why when the disk refuses the write`() {
        val files = Files().apply { failure = IOException("disk full") }
        show(files = files)

        type("Nome", "Push day")
        press("Salvar")

        named("Não foi possível salvar: disk full").assertExists()
        assertEquals(0, closed)
    }

    // blocks

    @Test
    fun `adds a picked exercise as a block and closes the picker`() {
        show()

        addBlocks("Pull-ups")

        named("1 bloco").assertExists()
        // Once, not twice: the row is there and the picker that listed it is gone.
        compose.onAllNodesWithText("Pull-ups").assertCountEquals(1)
    }

    @Test
    fun `removes a block`() {
        show()
        addBlocks("Pull-ups")

        press("Remover Pull-ups")

        named("0 blocos").assertExists()
    }

    // RN's shipped sized round its glyph, 14×30, and a missed grab read as a drag that wouldn't start.
    @Test
    fun `gives the grip a full touch target`() {
        show()
        addBlocks("Pull-ups")

        grip("Pull-ups").assertWidthIsAtLeast(44.dp).assertHeightIsAtLeast(44.dp)
    }

    // moving, under a screen reader

    @Test
    fun `moves a block up and down by its grip's actions, and saves that order`() {
        val files = show()
        addBlocks("Pull-ups", "Dips", "Rows")

        grip("Rows").moveWith("Mover para cima")
        grip("Pull-ups").moveWith("Mover para baixo")
        type("Nome", "Pull day")
        press("Salvar")

        // Rows up past Dips, then Pull-ups down past Rows.
        assertEquals(listOf("rows", "pull-ups", "dips"), files.savedWorkout("pull-day").exerciseIds())
    }

    // RN keyed rows by slot, so the second "move up" moved the new neighbour, undoing the first.
    @Test
    fun `moves the same block on a second move up`() {
        val files = show()
        addBlocks("Pull-ups", "Dips", "Rows")

        grip("Rows").moveWith("Mover para cima")
        grip("Rows").moveWith("Mover para cima")
        type("Nome", "Pull day")
        press("Salvar")

        assertEquals(listOf("rows", "pull-ups", "dips"), files.savedWorkout("pull-day").exerciseIds())
    }

    // Clamped by not offering the move, rather than offering one that does nothing.
    @Test
    fun `offers no move up on the first block and no move down on the last`() {
        show()
        addBlocks("Pull-ups", "Dips")

        assertEquals(listOf("Mover para baixo"), grip("Pull-ups").actionLabels())
        assertEquals(listOf("Mover para cima"), grip("Dips").actionLabels())
    }

    // The state is what TalkBack reads with the grip, and again when a move changes it.
    @Test
    fun `says each block's position, and the new one after a move`() {
        show()
        addBlocks("Pull-ups", "Dips")

        grip("Dips").assert(hasStateDescription("Posição 2 de 2"))
        grip("Dips").moveWith("Mover para cima")
        grip("Dips").assert(hasStateDescription("Posição 1 de 2"))
    }

    // circuits

    @Test
    fun `will not build a circuit from a single exercise`() {
        show()
        press("+ Novo circuito")
        press("Pull-ups")

        named("Adicionar circuito (1)").assertIsNotEnabled()
        named("0 blocos").assertExists()
    }

    @Test
    fun `builds a circuit from two, then clears the selection`() {
        show()

        buildCircuit("Pull-ups", "Dips")

        named("1 bloco").assertExists()
        named("Circuito").assertExists()
        // Reopened empty: the last circuit's members must not ride into the next.
        press("+ Novo circuito")
        named("Adicionar circuito (0)").assertExists()
    }

    @Test
    fun `deselects a member that is tapped twice`() {
        show()
        press("+ Novo circuito")

        press("Pull-ups")
        press("Dips")
        press("Pull-ups")

        named("Adicionar circuito (1)").assertExists()
    }

    // -1 rounds builds no steps: a workout that starts and at once ends.
    @Test
    fun `does not let rounds fall below one`() {
        show()
        buildCircuit("Pull-ups", "Dips")
        named("3 rodadas · 15s / 60s de descanso").assertExists()

        repeat(4) { press("Diminuir Rodadas") }

        named("1 rodada · 15s / 60s de descanso").assertExists()
    }

    @Test
    fun `removes a member without removing the circuit`() {
        show()
        buildCircuit("Pull-ups", "Dips", "Rows")

        press("Remover Dips do circuito")

        named("Circuito").assertExists()
        named("1 bloco").assertExists()
        compose.onNodeWithText("Dips").assertDoesNotExist()
    }

    // The schema needs two members. Unavailable, not enabled and ignoring the tap.
    @Test
    fun `marks the remove control unavailable on a two-member circuit`() {
        show()
        buildCircuit("Pull-ups", "Dips")

        named("Remover Dips do circuito").assertIsNotEnabled()
    }

    @Test
    fun `leaves it available while there is a member to spare`() {
        show()
        buildCircuit("Pull-ups", "Dips", "Rows")

        named("Remover Dips do circuito").assertIsEnabled()
    }

    // `Number('-5') || 0` is -5, and the schema is non-negative, so this reached RN's file.
    @Test
    fun `keeps a negative circuit rest out of the library`() {
        val files = show()
        buildCircuit("Pull-ups", "Dips")

        type("Descanso/exercício (s)", "-5")
        type("Nome", "Circuit day")
        press("Salvar")

        val circuit = files.savedWorkout("circuit-day").blocks.single() as WorkoutBlock.Circuit
        assertEquals(0.0, circuit.restBetweenExercisesSec)
    }

    // 501 rounds is a library that won't parse on the next launch, and RN reseeded it silently.
    @Test
    fun `does not let rounds climb past the schema ceiling`() {
        val atCeiling =
            WorkoutBlock.Circuit(
                rounds = 500,
                restBetweenExercisesSec = 15.0,
                restBetweenRoundsSec = 60.0,
                members = listOf(CircuitMember("pull-ups"), CircuitMember("dips")),
            )
        val files = show("push-day", Files(workouts = listOf(pushDay(atCeiling))))
        named("500 rodadas · 15s / 60s de descanso").assertExists()

        press("Aumentar Rodadas")
        press("Salvar")

        assertEquals(500L, (files.savedWorkout("push-day").blocks.single() as WorkoutBlock.Circuit).rounds)
    }

    // quick-adding an exercise from a picker

    /**
     * The form's Nome, not the workout's: the last in the tree, since the sheet's own is above it, and
     * may not be in the tree at all once the list has scrolled past it.
     */
    private fun quickAddName() = compose.onAllNodes(hasContentDescription("Nome")).onLast()

    /** Fills the embedded form for the default reps type and submits it. */
    private fun createExercise(name: String) {
        press("+ Novo exercício")
        quickAddName().performTextReplacement(name)
        type("Séries", "3")
        type("Reps alvo", "8")
        type("Descanso (seg)", "90")
        press("Criar e adicionar")
    }

    @Test
    fun `adds the new exercise as a block, writes it to the library, and closes the picker`() {
        val files = show()
        press("+ Adicionar bloco")

        createExercise("Ring Row")

        named("1 bloco").assertExists()
        compose.onAllNodesWithText("Ring Row").assertCountEquals(1)
        assertEquals(
            "ring-row",
            files.written
                .single()
                .exercises
                .last()
                .id,
        )
        assertEquals(0, closed)
    }

    @Test
    fun `selects the new exercise as a circuit member and leaves the picker open for the next one`() {
        show()
        press("+ Novo circuito")

        createExercise("Ring Row")

        named("Adicionar circuito (1)").assertExists()
        press("Pull-ups")
        named("Adicionar circuito (2)").assertExists()
    }

    /**
     * The read after the quick-add's write is launched, not awaited. A Save in between built on the
     * library from before it, and wrote a workout pointing at an exercise the file no longer held.
     */
    @Test
    fun `saves a quick-added exercise with the workout, though the read after its write hasn't landed`() {
        val io = Steps()
        val files = Files()
        show(files = files, io = io.asCoroutineDispatcher())
        io.run()
        press("+ Adicionar bloco")
        createExercise("Ring Row")
        // The write lands; the read it launches doesn't, yet.
        io.runFirst()

        type("Nome", "Row day")
        press("Salvar")
        settle(io)

        val saved = files.written.last()
        assertEquals("ring-row", saved.exercises.last().id)
        assertEquals(listOf("ring-row"), saved.workouts.single().exerciseIds())
    }

    // A Save while the quick-add was still being written did nothing, and wanted a second tap.
    @Test
    fun `saves once the quick-add's write lands, when Save is tapped while it is landing`() {
        val io = Steps()
        val files = Files()
        show(files = files, io = io.asCoroutineDispatcher())
        io.run()
        type("Nome", "Row day")
        press("+ Adicionar bloco")
        createExercise("Ring Row")

        press("Salvar")
        settle(io)

        assertEquals(listOf("ring-row"), files.savedWorkout("row-day").exerciseIds())
        assertEquals(1, closed)
    }

    // A double tap: queued behind its own write, the second Save wrote again and closed twice, and
    // the second close popped the screen under the sheet.
    @Test
    fun `writes and closes once for a double-tapped Save`() {
        val io = Steps()
        val files = Files()
        show(files = files, io = io.asCoroutineDispatcher())
        io.run()
        type("Nome", "Push day")

        press("Salvar")
        press("Salvar")
        settle(io)

        assertEquals(1, files.written.size)
        assertEquals(1, closed)
    }

    // Queued behind the delete, a Save wrote the deleted workout back into the library.
    @Test
    fun `writes nothing more for a Save tapped while a delete is landing`() {
        val io = Steps()
        val files = Files(workouts = listOf(pushDay()))
        show("push-day", files, io.asCoroutineDispatcher())
        io.run()

        press("Excluir treino")
        compose.pressDialogButton("Excluir")
        press("Salvar")
        settle(io)

        assertEquals(listOf(emptyList<Workout>()), files.written.map { it.workouts })
        assertEquals(1, closed)
    }

    // The sheet is still dismissing after its write lands: a second Save wrote again, and its close
    // popped the screen under the sheet (#277).
    @Test
    fun `writes and closes once for a Save tapped after the first has landed`() {
        val files = Files(workouts = listOf(pushDay()))
        show("push-day", files)

        press("Salvar")
        press("Salvar")

        assertEquals(1, files.written.size)
        assertEquals(1, closed)
    }

    // Tapped while the sheet was still dismissing after the delete, Save wrote the workout back (#277).
    @Test
    fun `keeps a deleted workout deleted through a Save tapped after the delete has landed`() {
        val files = Files(workouts = listOf(pushDay()))
        show("push-day", files)

        press("Excluir treino")
        compose.pressDialogButton("Excluir")
        press("Salvar")

        assertEquals(listOf(emptyList<Workout>()), files.written.map { it.workouts })
        assertEquals(1, closed)
    }

    // A refused write leaves the sheet up, so a retry must still be able to write and close it.
    @Test
    fun `writes and closes on a retried Save once the disk takes it`() {
        val files = Files(workouts = listOf(pushDay())).apply { failure = IOException("disk full") }
        show("push-day", files)

        press("Salvar")
        files.failure = null
        press("Salvar")

        assertEquals(1, files.written.size)
        assertEquals(1, closed)
    }

    // A closed sheet has nothing further to write, a quick-added exercise included.
    @Test
    fun `writes no quick-added exercise once the sheet has closed`() {
        val files = Files(workouts = listOf(pushDay()))
        show("push-day", files)
        press("+ Adicionar bloco")
        press("+ Novo exercício")

        press("Salvar")
        quickAddName().performTextReplacement("Ring Row")
        type("Séries", "3")
        type("Reps alvo", "8")
        type("Descanso (seg)", "90")
        press("Criar e adicionar")

        assertEquals(1, files.written.size)
        assertEquals(1, closed)
    }

    // The picker is a lazy list item, disposed once scrolled out of view, and the form with it.
    @Test
    fun `keeps what was typed into the quick-add through scrolling it out of view`() {
        show("push-day", Files(workouts = listOf(pushDay(*Array(40) { WorkoutBlock.ExerciseBlock("pull-ups") }))))
        press("+ Adicionar bloco")
        press("+ Novo exercício")
        quickAddName().performTextReplacement("Ring Row")

        // A lazy list keeps a focused item composed, so the loss needs the focus gone, as dismissing
        // the keyboard leaves it.
        compose.runOnIdle { focus.clearFocus() }
        compose.onNode(hasScrollToNodeAction()).performScrollToIndex(0)
        compose.onNodeWithText("Criar e adicionar").assertDoesNotExist()
        named("Criar e adicionar")

        quickAddName().assert(hasText("Ring Row"))
    }

    @Test
    fun `keeps the form open with its values when the save is refused`() {
        show(files = Files().apply { failure = IOException("disk full") })
        press("+ Adicionar bloco")

        createExercise("Ring Row")

        named("Não foi possível salvar: disk full").assertExists()
        // Still the form, its name still in it: closing it would throw away what was typed.
        named("Criar e adicionar").assertExists()
        quickAddName().assert(hasText("Ring Row"))
        named("0 blocos").assertExists()
    }

    // deleting

    @Test
    fun `refuses to delete a workout a program still points at`() {
        val files = show("push-day", Files(workouts = listOf(pushDay()), programs = listOf(base)))

        press("Excluir treino")

        compose.onNodeWithText("Treino em uso").assertExists()
        compose.onNodeWithText("\"Push day\" é usado em Base. Remova-o desses programas primeiro.").assertExists()
        compose.onNode(hasText("Excluir") and hasAnyAncestor(isDialog())).assertDoesNotExist()
        assertEquals(emptyList<Library>(), files.written)
    }

    @Test
    fun `deletes an unreferenced workout once confirmed`() {
        val files = show("push-day", Files(workouts = listOf(pushDay())))

        press("Excluir treino")
        compose.onNodeWithText("Excluir treino?").assertExists()
        assertEquals(emptyList<Library>(), files.written)
        compose.pressDialogButton("Excluir")

        assertEquals(emptyList<Workout>(), files.written.single().workouts)
        assertEquals(1, closed)
    }

    @Test
    fun `deletes nothing when the confirm is cancelled`() {
        val files = show("push-day", Files(workouts = listOf(pushDay())))

        press("Excluir treino")
        compose.pressDialogButton("Cancelar")

        assertEquals(emptyList<Library>(), files.written)
        assertEquals(0, closed)
    }

    /** The library is read again while the confirm is up: a program that started using it stops the delete. */
    @Test
    fun `checks again at the confirm, against the library as it is by then`() {
        val files = show("push-day", Files(workouts = listOf(pushDay())))

        press("Excluir treino")
        files.rewrite { it.copy(programs = listOf(base)) }
        latest.reread()
        compose.pressDialogButton("Excluir")

        compose.onNodeWithText("Treino em uso").assertExists()
        assertEquals(emptyList<Library>(), files.written)
    }

    // the rest

    @Test
    fun `titles a new workout and an edited one apart`() {
        show()

        compose.onNode(hasText("Novo treino") and isHeading()).assertExists()
        compose.onNodeWithText("Excluir treino").assertDoesNotExist()
    }

    @Test
    fun `shows a rest block's own duration, marked as an override`() {
        val rest =
            WorkoutBlock.ExerciseBlock(
                "rest",
                com.casco.kettle.domain
                    .BlockConfigOverride(durationSec = 45.0),
            )
        show("push-day", Files(workouts = listOf(pushDay(rest))))

        named("45 segundos").assertExists()
        named("SUBSTITUÍDO").assertExists()
    }

    // Truncated to pick the plural, 2.5 s read as "2 segundos".
    @Test
    fun `shows a fractional override as it is`() {
        val rest =
            WorkoutBlock.ExerciseBlock(
                "rest",
                com.casco.kettle.domain
                    .BlockConfigOverride(durationSec = 2.5),
            )
        show("push-day", Files(workouts = listOf(pushDay(rest))))

        named("2.5 segundos").assertExists()
    }

    @Test
    fun `every control is a button or checkbox at least 44dp tall`() {
        show("push-day", Files(workouts = listOf(pushDay())))
        press("+ Novo circuito")

        for (label in listOf(
            "+ Adicionar bloco",
            "Fechar",
            "+ Novo exercício",
            "Pull-ups",
            "Remover Pull-ups",
            "Salvar",
        )) {
            node((hasText(label) or hasContentDescription(label)) and isControl).assertHeightIsAtLeast(44.dp)
        }
    }

    @Test
    fun `says which picker is open`() {
        show()

        named("+ Adicionar bloco").assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
        press("+ Adicionar bloco")
        named("Fechar").assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
    }

    private class Files(
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

private val pullUps = Exercise("pull-ups", "Pull-ups", ExerciseConfig.Reps(sets = 3, targetRepsMin = 8, restSec = 90.0))
private val dips = Exercise("dips", "Dips", ExerciseConfig.Reps(sets = 3, targetRepsMin = 10, restSec = 90.0))

/** A third, so a removal has a circuit it may remove from. */
private val rows = Exercise("rows", "Rows", ExerciseConfig.Reps(sets = 3, targetRepsMin = 10, restSec = 90.0))
private val rest = Exercise("rest", "Rest", ExerciseConfig.Rest(durationSec = 60.0))

private val base = Program("base", "Base", listOf(ProgramWeek.Training(week = 1, workoutId = "push-day")))

private fun pushDay(vararg blocks: WorkoutBlock) =
    Workout("push-day", "Push day", blocks.toList().ifEmpty { listOf(WorkoutBlock.ExerciseBlock("pull-ups")) })

private fun Workout.exerciseIds() = blocks.map { (it as WorkoutBlock.ExerciseBlock).exerciseId }

private const val SETTLE_ROUNDS = 3

/** A dispatcher that runs nothing until told to, so a test holds a write or a read mid-way. */
private class Steps : Executor {
    private val queued = ArrayDeque<Runnable>()

    override fun execute(task: Runnable) {
        queued += task
    }

    fun run() {
        while (queued.isNotEmpty()) queued.removeFirst().run()
    }

    fun runFirst() = queued.removeFirst().run()
}

private fun hasStateDescription(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

private fun SemanticsNodeInteraction.actionLabels() =
    fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
