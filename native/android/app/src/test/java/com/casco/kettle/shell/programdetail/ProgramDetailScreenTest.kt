package com.casco.kettle.shell.programdetail

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.CircuitMember
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.OverrideValue
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.Program
import com.casco.kettle.domain.ProgramOverride
import com.casco.kettle.domain.ProgramWeek
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.WorkoutBlock
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.StoredFiles
import com.casco.kettle.shell.rn.Stacking
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Program detail (#233), case for case from RN's `program-detail.test.tsx`, on the harness recipe
 * (#193): in `pt`, since an English assertion passes a hardcoded English string, with the library
 * faked behind [StoredFiles] and every route it opens heard through `open`. Beyond jest's: Edit and
 * Close, the targets' size, a program that's gone, and nothing said before the library is read.
 *
 * Which override keys apply is `:shared`'s (`OverrideNotesTest`); this covers its words.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class ProgramDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<Triple<Stacking, String, Map<String, String>>>()
    private var closed = 0

    private fun show(
        library: Library? = Library(1, emptyList(), listOf(pushDay), listOf(base)),
        programId: String = base.id,
    ) {
        val latest =
            LatestFiles(Files(library), CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).apply {
                reread()
            }
        compose.setContent {
            KettleTheme(darkTheme = false) {
                ProgramDetailScreen(
                    programId,
                    latest,
                    { how, path, params -> opened += Triple(how, path, params) },
                    { closed++ },
                )
            }
        }
    }

    /** A library whose one program runs [workout] for one week, with [override] on it. */
    private fun showOverride(
        workout: Workout,
        override: ProgramOverride,
    ) = show(
        Library(
            1,
            listOf(pullUps, dips),
            listOf(workout),
            listOf(
                Program(
                    "p",
                    "P",
                    listOf(ProgramWeek.Training(1, workoutId = workout.id, overrides = listOf(override))),
                ),
            ),
        ),
        "p",
    )

    @Test
    fun `lists a training week with its workout and a way to start it`() {
        show()

        compose.onNodeWithText("Push day").assertExists()
        // By label: the control is a triangle, and the label has to say which week.
        control("Começar Semana 1 · Day 1").assertExists()
    }

    @Test
    fun `names a rest week as one instead of looking its workout up`() {
        show()

        compose.onNodeWithText("Dia de descanso").assertExists()
        compose.onNodeWithText("Walk, nothing heavy.").assertExists()
    }

    @Test
    fun `offers exactly one start button — the training week has one, the rest week does not`() {
        show()

        compose.onAllNodes(hasContentDescription("Começar Semana", substring = true) and isButton).assertCountEquals(1)
    }

    @Test
    fun `starts the training week, not the rest week`() {
        show()

        control("Começar Semana 1 · Day 1").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()

        assertEquals(
            listOf(Triple(Stacking.Push, "/session", mapOf("programId" to "base", "week" to "1", "day" to "Day 1"))),
            opened,
        )
    }

    // RN's push left an undefined `day` out, and the runner reads its absence as "no day".
    @Test
    fun `starts a week with no day without one`() {
        val plain = Program("plain", "Plain", listOf(ProgramWeek.Training(2, workoutId = "push-day")))
        show(Library(1, emptyList(), listOf(pushDay), listOf(plain)), "plain")

        control("Começar Semana 2").performClick()

        assertEquals(listOf(Triple(Stacking.Push, "/session", mapOf("programId" to "plain", "week" to "2"))), opened)
    }

    // The schema takes `day: ""`, and RN tested `week.day` for truth, so an empty one is no day at all,
    // in the label and the name it gives the start control. The push still carries it, as RN's did.
    @Test
    fun `labels a week with an empty day as one with none`() {
        val blank = Program("blank", "Blank", listOf(ProgramWeek.Training(3, "", "push-day")))
        show(Library(1, emptyList(), listOf(pushDay), listOf(blank)), "blank")

        compose.onNodeWithText("Semana 3").assertExists()
        control("Começar Semana 3").performClick()

        assertEquals(
            listOf(Triple(Stacking.Push, "/session", mapOf("programId" to "blank", "week" to "3", "day" to ""))),
            opened,
        )
    }

    @Test
    fun `is translated`() {
        show()

        compose.onNodeWithText("Base building").assert(isHeading())
        compose.onNodeWithText("Dia de descanso").assertExists()
        control("Começar Semana 1 · Day 1").assertExists()
        control("Editar programa").assertExists()
        compose.onNode(hasText("Fechar") and isButton).assertExists()
        // Day labels and notes are user data and stay in the language they were written in.
        compose.onNodeWithText("Walk, nothing heavy.").assertExists()
    }

    @Test
    fun `Edit opens RN's editor on this program`() {
        show()

        control("Editar programa").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()

        assertEquals(listOf(Triple(Stacking.Push, "/program-editor", mapOf("id" to "base"))), opened)
    }

    @Test
    fun `Close closes the sheet`() {
        show()

        compose.onNode(hasText("Fechar") and isButton).assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(1, closed)
    }

    @Test
    fun `says so when the program is gone`() {
        show(programId = "deleted")

        compose.onNodeWithText("Programa não encontrado.").assertExists()
    }

    /** Null is a library not read yet: a program that is there must not be called gone meanwhile. */
    @Test
    fun `says nothing before there is a library`() {
        show(library = null)

        compose.onNodeWithText("Programa não encontrado.").assertDoesNotExist()
    }

    @Test
    fun `an override where only some keys are unknown marks only the key that does nothing`() {
        showOverride(
            circuitWorkout,
            ProgramOverride.BlockOverride("finisher", mapOf("rounds" to num(2.0), "exercisez" to num(2.0))),
        )

        // `rounds: 2` reaches the runner, so it carries no note; the number as JS writes it, not `2.0`.
        compose.onNodeWithText("Circuito (finisher): rounds → 2").assertExists()
        compose
            .onNodeWithText(
                "Circuito (finisher): exercisez → 2 · não aplicado — não existe essa configuração para alterar",
            ).assertExists()
    }

    @Test
    fun `marks an exercise override the week does not run as not applied`() {
        // The week runs pull-ups; the override names dips, which is in the library but not in the week.
        showOverride(pullUpsOnly, ProgramOverride.ExerciseOverride("dips", mapOf("sets" to num(5.0))))

        compose.onNodeWithText("dips: sets → 5").assertExists()
        compose.onNodeWithText("não aplicado — nada nesta semana corresponde").assertExists()
    }

    @Test
    fun `still names an exercise the week does run`() {
        showOverride(pullUpsOnly, ProgramOverride.ExerciseOverride("pull-ups", mapOf("sets" to num(5.0))))

        compose.onNodeWithText("Pull-ups: sets → 5").assertExists()
        compose.onNodeWithText("não aplicado", substring = true).assertDoesNotExist()
    }

    @Test
    fun `marks a patch the schema refuses as not applied, once, under all its keys`() {
        showOverride(pullUpsOnly, ProgramOverride.ExerciseOverride("pull-ups", mapOf("sets" to num(-1.0))))

        compose.onNodeWithText("Pull-ups: sets → -1").assertExists()
        compose.onNodeWithText("não aplicado — este valor não é permitido").assertExists()
    }

    @Test
    fun `translates the circuit label`() {
        showOverride(circuitWorkout, ProgramOverride.BlockOverride("finisher", mapOf("rounds" to num(2.0))))

        compose.onNodeWithText("Circuito (finisher): rounds → 2").assertExists()
    }

    /** An icon-only control, named by its label since it has no words of its own. */
    private fun control(label: String) = compose.onNode(hasContentDescription(label) and isButton)

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun num(value: Double) = OverrideValue.Numeric(value)

    private class Files(
        private val library: Library?,
    ) : StoredFiles {
        override fun sessionLog() = SessionLog(emptyList(), emptyList())

        override fun library() = library

        override fun preferences(): Preferences? = null

        override fun deleteSession(id: String) = error("Program detail deletes nothing")

        override fun writeSession(session: Session) = error("Program detail writes no session")

        override fun writePreferences(preferences: Preferences) = error("Program detail writes no preferences")
    }
}

private val pushDay = Workout("push-day", "Push day", emptyList())
private val base =
    Program(
        "base",
        "Base building",
        listOf(
            ProgramWeek.Training(1, "Day 1", "push-day"),
            ProgramWeek.RestDay(1, "Day 2", notes = "Walk, nothing heavy."),
        ),
    )
private val pullUps = Exercise("pull-ups", "Pull-ups", ExerciseConfig.Reps(3, 8, restSec = 90.0))
private val dips = Exercise("dips", "Dips", ExerciseConfig.Reps(3, 10, restSec = 90.0))
private val pullUpsOnly = Workout("w", "W", listOf(WorkoutBlock.ExerciseBlock("pull-ups")))
private val circuitWorkout =
    Workout(
        "w",
        "W",
        listOf(
            WorkoutBlock.Circuit("finisher", 3, members = listOf(CircuitMember("pull-ups"), CircuitMember("pull-ups"))),
        ),
    )
