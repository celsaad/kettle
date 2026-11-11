package com.casco.kettle.shell.exerciseeditor

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.CircuitMember
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.RepsSetLog
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.WorkoutBlock
import com.casco.kettle.shell.files.FakeStoredFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * The exercise editor (#241), case for case from RN's `exercise-editor.test.tsx`, on the harness
 * recipe (#193): in `pt`, since an English assertion passes a hardcoded English string, with the files
 * faked behind [StoredFiles]. Each asserts on the library written, which is what Save writes. Beyond
 * jest's: a write the disk refuses, a rename keeping its id, Recent, and the a11y.
 *
 * The validation's own cases are `:shared`'s (`ExerciseFormTest`); these cover the wiring.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class ExerciseEditorScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var closed = 0

    private lateinit var latest: LatestFiles

    private fun show(
        id: String?,
        files: Files,
    ) {
        latest =
            LatestFiles(files, CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).apply { reread() }
        compose.setContent {
            KettleTheme(darkTheme = false) {
                ExerciseEditorScreen(
                    id,
                    latest,
                    TimeZone.UTC,
                    ExerciseEditorActions(close = { closed += 1 }),
                )
            }
        }
    }

    /** A field, by the name it announces: its label, with its unit where it has one. */
    private fun type(
        name: String,
        text: String,
    ) = compose.onNode(hasContentDescription(name)).performScrollTo().performTextReplacement(text)

    private fun press(label: String) =
        compose.onNode((hasText(label) or hasContentDescription(label)) and isButton).performScrollTo().performClick()

    /** jest's `pressAlertButton`: a button inside the dialog, never a same-named one on the screen under it. */
    private fun ComposeContentTestRule.pressDialogButton(label: String) =
        onNode(hasText(label) and hasAnyAncestor(isDialog())).performClick()

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test
    fun `refuses a blank name`() {
        val files = Files()
        show(null, files)

        press("Salvar")

        compose.onNodeWithText("O nome é obrigatório.").assertExists()
        assertEquals(emptyList<Library>(), files.written)
    }

    // The only guard between a typo and a 0-set exercise, which builds no steps to run.
    @Test
    fun `surfaces a config error and writes nothing`() {
        val files = Files()
        show(null, files)

        type("Nome", "Pull-ups")
        type("Séries", "0")
        press("Salvar")

        compose.onNodeWithText("Séries precisa ser pelo menos 1.").assertExists()
        assertEquals(emptyList<Library>(), files.written)
        assertEquals(0, closed)
    }

    @Test
    fun `stores the config as numbers, not the strings the form holds`() {
        val files = Files()
        show(null, files)

        type("Nome", "Pull-ups")
        type("Séries", "4")
        type("Reps alvo", "6")
        type("Descanso (seg)", "90")
        press("Salvar")

        assertEquals(
            listOf(Exercise("pull-ups", "Pull-ups", ExerciseConfig.Reps(sets = 4, targetRepsMin = 6, restSec = 90.0))),
            files.written.single().exercises,
        )
        assertEquals(1, closed)
    }

    // Each type keys a different config: switching an existing one's would zero every field.
    @Test
    fun `locks the type of an exercise that already exists`() {
        val files = Files(exercises = listOf(pullUps))
        show("pull-ups", files)

        compose.onNode(hasText("HIIT") and isButton).assertIsNotEnabled()
        compose.onNode(hasText("Reps") and isButton).assertIsSelected()
        press("Salvar")

        assertEquals(
            pullUps,
            files.written
                .single()
                .exercises
                .single(),
        )
    }

    @Test
    fun `keeps the id through a rename, so no workout loses it`() {
        val files =
            Files(exercises = listOf(pullUps), workouts = listOf(pushDay(WorkoutBlock.ExerciseBlock("pull-ups"))))
        show("pull-ups", files)

        type("Nome", "Chin-ups")
        press("Salvar")

        assertEquals(listOf(pullUps.copy(name = "Chin-ups")), files.written.single().exercises)
    }

    // weight units

    @Test
    fun `labels the weight field and fills it in the chosen unit`() {
        show("bench-press", Files(exercises = listOf(benchPress), preferences = imperial))

        // "Peso", not "Weight": the label is translated around the unit.
        compose.onNodeWithText("Peso (lb) · opcional").assertExists()
        compose.onNodeWithContentDescription("Peso (lb)").assert(hasText("220.5"))
    }

    @Test
    fun `stores a weight typed in pounds as kilograms`() {
        val files = Files(exercises = listOf(benchPress), preferences = imperial)
        show("bench-press", files)

        type("Peso (lb)", "225")
        press("Salvar")

        assertEquals(
            102.06,
            (
                files.written
                    .single()
                    .exercises
                    .single()
                    .config as ExerciseConfig.Reps
            ).targetWeightKg,
        )
    }

    /** 100 kg shows as 220.5 lb, which converts back to 100.02: an unrelated edit must not move it. */
    @Test
    fun `leaves an untouched weight exactly as stored when saving an unrelated change`() {
        val files = Files(exercises = listOf(benchPress), preferences = imperial)
        show("bench-press", files)

        type("Séries", "6")
        press("Salvar")

        val config =
            files.written
                .single()
                .exercises
                .single()
                .config as ExerciseConfig.Reps
        assertEquals(6L, config.sets)
        assertEquals(100.0, config.targetWeightKg)
    }

    // deleting

    @Test
    fun `refuses to delete an exercise a workout uses as a plain block`() {
        val files =
            Files(exercises = listOf(pullUps), workouts = listOf(pushDay(WorkoutBlock.ExerciseBlock("pull-ups"))))
        show("pull-ups", files)

        press("Excluir exercício")

        compose.onNodeWithText("Exercício em uso").assertExists()
        compose.onNodeWithText("\"Pull-ups\" é usado em Push day. Remova-o desses treinos primeiro.").assertExists()
        compose.onNode(hasText("Excluir") and hasAnyAncestor(isDialog())).assertDoesNotExist()
        assertEquals(emptyList<Library>(), files.written)
    }

    // The branch a click-through misses: a circuit member is a second, differently shaped reference.
    @Test
    fun `also refuses when the only use is inside a circuit`() {
        val circuit =
            WorkoutBlock.Circuit(
                rounds = 3,
                members = listOf(CircuitMember("pull-ups"), CircuitMember("dips")),
            )
        val files = Files(exercises = listOf(pullUps), workouts = listOf(pushDay(circuit)))
        show("pull-ups", files)

        press("Excluir exercício")

        compose.onNodeWithText("Exercício em uso").assertExists()
        assertEquals(emptyList<Library>(), files.written)
    }

    @Test
    fun `deletes an unreferenced exercise once confirmed`() {
        val files = Files(exercises = listOf(pullUps))
        show("pull-ups", files)

        press("Excluir exercício")
        compose.onNodeWithText("Excluir exercício?").assertExists()
        compose.pressDialogButton("Excluir")

        assertEquals(emptyList<Exercise>(), files.written.single().exercises)
        assertEquals(1, closed)
    }

    @Test
    fun `deletes nothing when the confirm is cancelled`() {
        val files = Files(exercises = listOf(pullUps))
        show("pull-ups", files)

        press("Excluir exercício")
        compose.pressDialogButton("Cancelar")

        assertEquals(emptyList<Library>(), files.written)
        assertEquals(0, closed)
    }

    /**
     * The library is read again while the sheet is up, and on `.dev` RN can write it meanwhile. A
     * workout that starts using the exercise while the confirm is open must stop the delete there,
     * or the workout is left pointing at nothing.
     */
    @Test
    fun `checks again at the confirm, against the library as it is by then`() {
        val files = Files(exercises = listOf(pullUps))
        show("pull-ups", files)

        press("Excluir exercício")
        files.rewrite { it.copy(workouts = listOf(pushDay(WorkoutBlock.ExerciseBlock("pull-ups")))) }
        latest.reread()
        compose.pressDialogButton("Excluir")

        compose.onNodeWithText("Exercício em uso").assertExists()
        assertEquals(emptyList<Library>(), files.written)
        assertEquals(0, closed)
    }

    /**
     * Deleted elsewhere while the sheet was up: Save puts it back, with the edit. What was typed is
     * the newer intent, and nothing can point at it, since its delete needed no workout to use it.
     */
    @Test
    fun `puts back an exercise deleted underneath it, with the edit`() {
        val files = Files(exercises = listOf(pullUps))
        show("pull-ups", files)

        type("Séries", "4")
        files.rewrite { it.copy(exercises = emptyList()) }
        latest.reread()
        press("Salvar")

        assertEquals(
            listOf(pullUps.copy(config = (pullUps.config as ExerciseConfig.Reps).copy(sets = 4))),
            files.written.single().exercises,
        )
    }

    // the bundled drawing

    @Test
    fun `shows the art for a seeded exercise, described in the active language`() {
        show("plank", Files(exercises = listOf(plank)))

        compose
            .onNodeWithContentDescription("Prancha: antebraços no chão, corpo mantido em linha reta.")
            .performScrollTo()
            .assertExists()
    }

    // Most of a real library: an empty frame on every one of them would be worse than the silence.
    @Test
    fun `shows nothing for an exercise the user wrote themselves`() {
        show("pull-ups", Files(exercises = listOf(pullUps)))

        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image)).assertDoesNotExist()
    }

    // The sheet is still dismissing after its write lands: a second Save wrote again, and its close
    // popped the screen under the sheet (#277).
    @Test
    fun `writes and closes once for a Save tapped after the first has landed`() {
        val files = Files(exercises = listOf(pullUps))
        show("pull-ups", files)

        press("Salvar")
        press("Salvar")

        assertEquals(1, files.written.size)
        assertEquals(1, closed)
    }

    // Tapped while the sheet was still dismissing after the delete, Save wrote the exercise back (#277).
    @Test
    fun `keeps a deleted exercise deleted through a Save tapped after the delete has landed`() {
        val files = Files(exercises = listOf(pullUps))
        show("pull-ups", files)

        press("Excluir exercício")
        compose.pressDialogButton("Excluir")
        press("Salvar")

        assertEquals(listOf(emptyList<Exercise>()), files.written.map { it.exercises })
        assertEquals(1, closed)
    }

    // A refused write leaves the sheet up, so a retry must still be able to write and close it.
    @Test
    fun `writes and closes on a retried Save once the disk takes it`() {
        val files = Files(exercises = listOf(pullUps)).apply { failure = IOException("disk full") }
        show("pull-ups", files)

        press("Salvar")
        files.failure = null
        press("Salvar")

        assertEquals(1, files.written.size)
        assertEquals(1, closed)
    }

    // Degrading beats losing the edit: the sheet stays open, with it, and says why.
    @Test
    fun `stays open and says why when the disk refuses the write`() {
        val files = Files(exercises = listOf(pullUps)).apply { failure = IOException("disk full") }
        show("pull-ups", files)

        press("Salvar")

        compose.onNodeWithText("Não foi possível salvar: disk full").assertExists()
        assertEquals(0, closed)
    }

    @Test
    fun `shows what the log holds of it, and nothing of a session still running`() {
        val logged = session("done", "2026-07-24T10:00:00.000Z")
        val running = session("running", null)
        show("pull-ups", Files(exercises = listOf(pullUps), sessions = listOf(running, logged)))

        compose.onNodeWithText("Recente").performScrollTo().assertExists()
        compose.onNodeWithText("24 de jul.").assertExists()
    }

    @Test
    fun `shows no Recent for an exercise never logged`() {
        show("pull-ups", Files(exercises = listOf(pullUps)))

        compose.onNodeWithText("Recente").assertDoesNotExist()
    }

    @Test
    fun `titles a new exercise and an edited one apart`() {
        show(null, Files())

        compose.onNode(hasText("Novo exercício") and isHeading()).assertExists()
        compose.onNodeWithText("Excluir exercício").assertDoesNotExist()
    }

    // ja: the language whose brackets, separator and order all differ from English's (#272)

    @Test
    @Config(qualifiers = "ja")
    fun `brackets a unit and marks an optional field the way Japanese does`() {
        show("plank", Files(exercises = listOf(plank)))

        compose.onNodeWithText("休憩（秒）").performScrollTo().assertExists()
        compose.onNodeWithText("ホールド（秒）（任意）").performScrollTo().assertExists()
    }

    @Test
    @Config(qualifiers = "ja")
    fun `lists the workouts using it with the Japanese separator`() {
        val legDay = Workout("leg-day", "Leg day", listOf(WorkoutBlock.ExerciseBlock("pull-ups")))
        val files =
            Files(
                exercises = listOf(pullUps),
                workouts = listOf(pushDay(WorkoutBlock.ExerciseBlock("pull-ups")), legDay),
            )
        show("pull-ups", files)

        press("エクササイズを削除")

        compose
            .onNodeWithText("「Pull-ups」はPush day、Leg dayで使われています。先にそれらのワークアウトから外してください。")
            .assertExists()
    }

    // a11y

    @Test
    fun `every control is a button at least 44dp tall`() {
        show("pull-ups", Files(exercises = listOf(pullUps)))

        for (label in listOf("Reps", "HIIT", "Cancelar", "Salvar", "Excluir exercício")) {
            compose.onNode(hasText(label) and isButton).performScrollTo().assertHeightIsAtLeast(44.dp)
        }
    }

    private class Files(
        exercises: List<Exercise> = emptyList(),
        private val workouts: List<Workout> = emptyList(),
        private val preferences: Preferences? = null,
        private val sessions: List<Session> = emptyList(),
    ) : FakeStoredFiles() {
        private var library = Library(1, exercises, workouts, emptyList())
        val written = mutableListOf<Library>()

        /** Thrown by the next write, as a full disk would. */
        var failure: IOException? = null

        override fun sessionLog() = SessionLog(sessions, emptyList())

        override fun library() = library

        override fun preferences() = preferences

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
private val benchPress =
    Exercise(
        "bench-press",
        "Bench Press",
        ExerciseConfig.Reps(sets = 5, targetRepsMin = 5, targetWeightKg = 100.0, restSec = 120.0),
    )
private val plank = Exercise("plank", "Prancha", ExerciseConfig.TimedHold(sets = 3, holdSecMin = 30.0, restSec = 60.0))
private val imperial = Preferences(UnitSystem.IMPERIAL, ThemePreference.SYSTEM, false, true, null)

private fun pushDay(block: WorkoutBlock) = Workout("push-day", "Push day", listOf(block))

private fun session(
    id: String,
    endedAt: String?,
) = Session(
    1,
    id,
    null,
    null,
    null,
    null,
    "2026-07-24T09:00:00.000Z",
    endedAt,
    listOf(SessionEntry.Reps("pull-ups", listOf(RepsSetLog(reps = 8, restTakenSec = 90.0)))),
)
