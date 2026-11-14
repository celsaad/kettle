package com.casco.kettle.shell.programdetail

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.CircuitMember
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.OverrideValue
import com.casco.kettle.domain.Program
import com.casco.kettle.domain.ProgramOverride
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
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Instant

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

    private val opened = mutableListOf<Route>()
    private val started = mutableListOf<RunnerStart>()
    private lateinit var files: Files
    private lateinit var latest: LatestFiles

    private fun show(
        library: Library? = Library(1, emptyList(), listOf(pushDay), listOf(base)),
        programId: String = base.id,
        sessions: List<Session> = emptyList(),
        clock: Clock = Clock.System,
    ) {
        files = Files(library, sessions)
        latest =
            LatestFiles(
                files,
                CoroutineScope(Dispatchers.Unconfined),
                io = Dispatchers.Unconfined,
            ).apply {
                reread()
            }
        compose.setContent {
            KettleTheme(darkTheme = false) {
                ProgramDetailScreen(
                    programId,
                    latest,
                    { opened += it },
                    { started += it },
                    clock,
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

        compose.onNode(hasText("Push day") and isButton).assertExists()
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

        assertEquals(listOf(RunnerStart.Planned(SessionParams.ProgramSlot("base", 1, "Day 1"))), started)
    }

    // How the runner resolves a missing day is not this screen's; here, that the start has none.
    @Test
    fun `starts a week with no day without one`() {
        val plain = Program("plain", "Plain", listOf(ProgramWeek.Training(2, workoutId = "push-day")))
        show(Library(1, emptyList(), listOf(pushDay), listOf(plain)), "plain")

        control("Começar Semana 2").performClick()

        assertEquals(listOf(RunnerStart.Planned(SessionParams.ProgramSlot("plain", 2, null))), started)
    }

    // The schema takes `day: ""`, and RN tested `week.day` for truth, so an empty one is no day at all,
    // in the label and the name it gives the start control. The start still carries it, as RN's push did.
    @Test
    fun `labels a week with an empty day as one with none`() {
        val blank = Program("blank", "Blank", listOf(ProgramWeek.Training(3, "", "push-day")))
        show(Library(1, emptyList(), listOf(pushDay), listOf(blank)), "blank")

        compose.onNodeWithText("Semana 3").assertExists()
        control("Começar Semana 3").performClick()

        assertEquals(listOf(RunnerStart.Planned(SessionParams.ProgramSlot("blank", 3, ""))), started)
    }

    @Test
    fun `is translated`() {
        show()

        compose.onNodeWithText("Base building").assert(isHeading())
        compose.onNodeWithText("Dia de descanso").assertExists()
        control("Começar Semana 1 · Day 1").assertExists()
        control("Editar programa").assertExists()
        compose.onNodeWithText("Semanas").assert(isHeading())
        compose.onNode(hasText("Iniciar") and isButton).assertExists()
        // Day labels and notes are user data and stay in the language they were written in.
        compose.onNodeWithText("Walk, nothing heavy.").assertExists()
    }

    @Test
    fun `Edit opens RN's editor on this program`() {
        show()

        control("Editar programa").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()

        assertEquals(listOf(Route(Destination.ProgramEditor, mapOf("id" to "base"))), opened)
    }

    /** #437: the next slot, as Today counts it, with Start and the preview that carries the full note. */
    @Test
    fun `the next card starts the program's next slot, and opens its preview`() {
        show(
            library = Library(1, emptyList(), listOf(pushDay, legDay), listOf(twoWeeks)),
            programId = "two",
            sessions = listOf(ran(1, "Mon")),
        )

        compose.onNodeWithText("Próximo · Semana 1 · Wed").assertExists()
        compose
            .onNode(hasText("Iniciar") and isButton)
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        compose.onNode(hasText("Prévia e nota completa") and isButton).performScrollTo().performClick()

        assertEquals(listOf(RunnerStart.Planned(SessionParams.ProgramSlot("two", 1, "Wed"))), started)
        assertEquals(
            listOf(Route(Destination.WorkoutPreview, mapOf("programId" to "two", "week" to "1", "day" to "Wed"))),
            opened,
        )
    }

    /** Position, not completion: the week it stands in opens, its next day tagged, the one before "Earlier". */
    @Test
    fun `opens on the week it stands in, tags the next day, and switches weeks by tab`() {
        show(
            library = Library(1, emptyList(), listOf(pushDay, legDay), listOf(twoWeeks)),
            programId = "two",
            sessions = listOf(ran(1, "Mon")),
        )

        compose.onNodeWithText("Dia 2 de 2 é o próximo").assertExists()
        compose.onNode(hasContentDescription("Semana 1, Esta semana")).assertIsSelected()
        compose.onNode(hasText("Mon") and hasText("Anterior") and isButton).assertExists()

        compose
            .onNode(
                hasContentDescription("Semana 2, 1 dia alterado"),
            ).performScrollTo()
            .assertIsNotSelected()
            .performClick()

        compose.onNodeWithText("Semana 2").assert(isHeading())
        compose.onNode(hasText("Leg day") and isButton).assertExists()
    }

    /**
     * Today moves past a rest day once the calendar has served it, and Program detail follows it rather
     * than its own reading: Mon logged three days ago, Tue's rest is spent, so week 2's Wed is next on
     * both, and the header says week 2 as the programs list does.
     */
    @Test
    fun `follows Today past a rest day already served`() {
        val withRest =
            Program(
                "rest",
                "With rest",
                listOf(
                    ProgramWeek.Training(1, "Mon", "push-day"),
                    ProgramWeek.RestDay(1, "Tue"),
                    ProgramWeek.Training(2, "Wed", "leg-day"),
                ),
            )
        show(
            library = Library(1, emptyList(), listOf(pushDay, legDay), listOf(withRest)),
            programId = "rest",
            sessions = listOf(ran(1, "Mon").copy(program = "rest")),
            clock = fixedAt("2026-08-04T12:00:00Z"),
        )

        compose.onNodeWithText("Próximo · Semana 2 · Wed").assertExists()
        compose.onNodeWithText("Ativo, semana 2", substring = true).assertExists()
    }

    /** A 3-day week with day 2 next: "of 3" and "day 2" can't trade places unnoticed. */
    @Test
    fun `says which day of the week is next`() {
        val three =
            Program(
                "three",
                "Three",
                listOf(
                    ProgramWeek.Training(1, "Mon", "push-day"),
                    ProgramWeek.Training(1, "Wed", "push-day"),
                    ProgramWeek.Training(1, "Fri", "push-day"),
                ),
            )
        show(
            library = Library(1, emptyList(), listOf(pushDay), listOf(three)),
            programId = "three",
            sessions = listOf(ran(1, "Mon").copy(program = "three")),
        )

        compose.onNodeWithText("Dia 2 de 3 é o próximo").assertExists()
    }

    /** A picked week the editor then deletes falls back to the week the program stands in, list and all. */
    @Test
    fun `falls back to the current week when the picked one is gone`() {
        show(library = Library(1, emptyList(), listOf(pushDay, legDay), listOf(twoWeeks)), programId = "two")
        compose.onNode(hasContentDescription("Semana 2, 1 dia alterado")).performScrollTo().performClick()
        compose.onNode(hasText("Leg day") and isButton).assertExists()

        val weekOne = twoWeeks.copy(weeks = twoWeeks.weeks.filter { it.week == 1L })
        files.library = Library(1, emptyList(), listOf(pushDay, legDay), listOf(weekOne))
        latest.reread()
        compose.waitForIdle()

        compose.onNode(hasContentDescription("Semana 1, Esta semana")).assertIsSelected()
        compose.onNode(hasText("Mon") and isButton).assertExists()
    }

    /** Only the program Today runs is "Active": another program's header claims no week. */
    @Test
    fun `says Active only for the program Today runs`() {
        val other = Program("other", "Other", listOf(ProgramWeek.Training(1, "Mon", "push-day")))
        show(
            library = Library(1, emptyList(), listOf(pushDay, legDay), listOf(twoWeeks, other)),
            programId = "other",
            sessions = listOf(ran(1, "Mon")),
        )

        compose.onNodeWithText("Ativo", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a day row opens that day's preview`() {
        show()

        compose
            .onNode(hasText("Push day") and isButton)
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals(
            listOf(
                Route(
                    Destination.WorkoutPreview,
                    mapOf(
                        "programId" to "base",
                        "week" to "1",
                        "day" to "Day 1",
                    ),
                ),
            ),
            opened,
        )
    }

    @Test
    fun `counts the workouts the program runs`() {
        show(library = Library(1, emptyList(), listOf(pushDay, legDay), listOf(twoWeeks)), programId = "two")

        compose.onNodeWithText("2 treinos neste programa").performScrollTo().assertExists()
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
    private fun control(label: String) = compose.onNode(hasContentDescription(label) and isButton).performScrollTo()

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun num(value: Double) = OverrideValue.Numeric(value)

    private class Files(
        var library: Library?,
        private val sessions: List<Session> = emptyList(),
    ) : FakeStoredFiles() {
        override fun sessionLog() = SessionLog(sessions, emptyList())

        override fun library() = library
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
private val legDay = Workout("leg-day", "Leg day", emptyList())
private val twoWeeks =
    Program(
        "two",
        "Two weeks",
        listOf(
            ProgramWeek.Training(1, "Mon", "push-day"),
            ProgramWeek.Training(1, "Wed", "push-day"),
            ProgramWeek.Training(
                2,
                "Mon",
                "leg-day",
                overrides = listOf(ProgramOverride.ExerciseOverride("x", mapOf("sets" to OverrideValue.Numeric(4.0)))),
            ),
        ),
    )

/** A finished session run as [day] of [week] of the two-week program. */
private fun ran(
    week: Long,
    day: String,
) = Session(
    1,
    "s-$week-$day",
    "push-day",
    "two",
    week,
    day,
    "2026-08-01T10:00:00.000Z",
    "2026-08-01T10:30:00.000Z",
    // Some work, or the log hides a finished session that logged none.
    listOf(SessionEntry.Reps("pull-ups", listOf(RepsSetLog(8, restTakenSec = 60.0)))),
)

/** A clock stopped at [at], so rest days can pass in a test. */
private fun fixedAt(at: String) =
    object : Clock {
        override fun now() = Instant.parse(at)
    }
