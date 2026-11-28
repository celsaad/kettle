package com.casco.kettle.shell.library

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.Program
import com.casco.kettle.domain.ProgramWeek
import com.casco.kettle.domain.RepsSetLog
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.TimedHoldSetLog
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.WorkoutBlock
import com.casco.kettle.shell.files.FakeStoredFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.Route
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
import kotlin.time.Instant

/**
 * Exercise detail (#413): what the exercise is, how it has gone, and what uses it, apart from the
 * editor. In `pt`, so a hardcoded English string would show (AGENTS.md).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class ExerciseDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<Route>()

    private fun show(
        id: String? = "squat",
        files: Files = Files(),
    ) {
        val latest =
            LatestFiles(files, CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).apply { reread() }
        compose.setContent {
            KettleTheme(darkTheme = false) {
                ExerciseDetailScreen(id, latest, Now, TimeZone.UTC) { opened += it }
            }
        }
    }

    private fun button(text: String) = compose.onNode(hasText(text) and isButton)

    private fun text(value: String) = compose.onNodeWithText(value)

    @Test
    fun `says what the exercise is, by its type, its name, its target and its notes`() {
        show()

        text("Reps").assertExists()
        text("Squat").assert(isHeading())
        text("3 × 5 · 60 kg · descanso 2:00").assertExists()
        text("Brace before every rep.").assertExists()
    }

    @Test
    fun `YAML opens the exercise's own YAML, and Edit opens the form`() {
        show()

        button("YAML").assertHeightIsAtLeast(48.dp).performClick()
        button("Editar").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(
            listOf(
                Route(Destination.LibraryYaml, mapOf(EXERCISE_PARAM to "squat")),
                Route(Destination.ExerciseEditor, mapOf("id" to "squat")),
            ),
            opened,
        )
    }

    /** Stats' own reading, so detail and Getting stronger show one number: 62.5 kg, up 2.5 since the first. */
    @Test
    fun `shows Stats' reading of it over the trend window`() {
        show(
            files =
                Files(
                    sessions =
                        listOf(
                            session("b", "2026-07-24T10:00:00Z", 62.5),
                            session("a", "2026-07-10T10:00:00Z", 60.0),
                        ),
                ),
        )

        text("Progresso").assert(isHeading())
        // What the number is, by its kind (#491): a top set would misname a hold or a bodyweight rep.
        text("Maior carga · 8 semanas").assertExists()
        text("8 semanas").assertDoesNotExist()
        text("62.5 kg").assertExists()
        // Getting stronger's form (#414): the arrow drawn, the signed change said.
        text("▲ 2.5 kg").assert(hasContentDescription("+2.5 kg"))
        text("Não treinado nas últimas 8 semanas.").assertDoesNotExist()
    }

    @Test
    fun `says so when it wasn't trained in the window`() {
        show()

        text("Não treinado nas últimas 8 semanas.").assertExists()
        // No reading, so no kind to name: only the window.
        text("8 semanas").assertExists()
    }

    /** Getting stronger needs two sessions; one is still training, and the page doesn't call it none. */
    @Test
    fun `says one session is too few to chart, not that it wasn't trained`() {
        show(files = Files(sessions = listOf(session("a", "2026-07-24T10:00:00Z", 62.5))))

        text("Sessões insuficientes nas últimas 8 semanas para o gráfico: são precisas duas.").assertExists()
        text("Não treinado nas últimas 8 semanas.").assertDoesNotExist()
    }

    /** Stats leaves a fixed-length hold out, its log being its config; trained, it still isn't "not trained". */
    @Test
    fun `says a fixed-length hold has no trend, not that it wasn't trained`() {
        show(
            "plank",
            Files(
                sessions =
                    listOf("2026-07-10T10:00:00Z", "2026-07-24T10:00:00Z").map {
                        held(it, "plank", 45.0)
                    },
            ),
        )

        text("Cada sessão registra o tempo definido, então não há tendência para mostrar.").assertExists()
        text("Não treinado nas últimas 8 semanas.").assertDoesNotExist()
    }

    /** A run logs its real time and distance, so it isn't told it copies its setting, as a hold is. */
    @Test
    fun `says cardio work has no single number to chart, not that it logs its setting`() {
        show(
            "run",
            Files(
                sessions =
                    listOf("2026-07-10T10:00:00Z", "2026-07-24T10:00:00Z").map {
                        Session(
                            1,
                            it,
                            null,
                            null,
                            null,
                            null,
                            it,
                            it,
                            listOf(SessionEntry.Cardio("run", 1500.0, 5000.0)),
                        )
                    },
            ),
        )

        text("Este tipo de treino não tem um número único para o gráfico.").assertExists()
        text("Cada sessão registra o tempo definido, então não há tendência para mostrar.").assertDoesNotExist()
    }

    /** No list under the chart here, as Stats' sheet has: the drawing says its sessions itself. */
    @Test
    fun `gives TalkBack the chart's sessions in words, newest first`() {
        show(
            files =
                Files(
                    sessions =
                        listOf(
                            session("b", "2026-07-24T10:00:00Z", 62.5),
                            session("a", "2026-07-10T10:00:00Z", 60.0),
                        ),
                ),
        )

        compose
            .onNode(hasContentDescription("62.5 kg · nova melhor marca", substring = true))
            .assert(hasContentDescription("60 kg", substring = true))
            .assertExists()
    }

    /** The editor's Recent, moved here: what was logged, and nothing of a session still running. */
    @Test
    fun `lists recent sessions, logged ones only`() {
        show(
            files =
                Files(
                    sessions =
                        listOf(
                            session("running", "2026-07-28T10:00:00Z", 70.0, ended = false),
                            session("done", "2026-07-24T10:00:00Z", 62.5),
                        ),
                ),
        )

        text("Sessões recentes").performScrollTo().assert(isHeading())
        text("24 de jul.").assertExists()
        text("28 de jul.").assertDoesNotExist()
    }

    @Test
    fun `has no recent sessions heading for an exercise never logged`() {
        show()

        text("Sessões recentes").assertDoesNotExist()
    }

    /** A workout that uses it, and a program that runs that workout, each opening what it names. */
    @Test
    fun `lists what uses it, workouts then programs through them, and opens each`() {
        show()

        button("Leg day")
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .assert(hasText("treino"))
            .performClick()
        button("Base").performScrollTo().assert(hasText("programa, por meio de Leg day")).performClick()

        assertEquals(
            listOf(
                Route(Destination.WorkoutEditor, mapOf("id" to "leg-day")),
                Route(Destination.ProgramDetail, mapOf("programId" to "base")),
            ),
            opened,
        )
        button("Push day").assertDoesNotExist()
    }

    @Test
    fun `says so when nothing uses it yet`() {
        show("lunge")

        text("Ainda não está em nenhum treino.").performScrollTo().assertExists()
    }

    /** A stale route, the exercise deleted since: said, rather than a blank sheet. */
    @Test
    fun `says the exercise is gone when the library no longer has it`() {
        show("deleted")

        text("Este exercício não está mais na sua biblioteca.").assertExists()
        button("Editar").assertDoesNotExist()
    }

    /**
     * Getting stronger still lists an exercise deleted from the library (#489), and its row opens here:
     * the log's id stands in for the name, and how it went stays, with nothing to edit.
     */
    @Test
    fun `still shows how a deleted exercise went, by the id the log keeps`() {
        val withoutSquat = fixture.copy(exercises = fixture.exercises - squat)
        show(
            files =
                Files(
                    sessions =
                        listOf(
                            session("b", "2026-07-24T10:00:00Z", 62.5),
                            session("a", "2026-07-10T10:00:00Z", 60.0),
                        ),
                    library = withoutSquat,
                ),
        )

        text("squat").assert(isHeading())
        text("Este exercício não está mais na sua biblioteca.").assertExists()
        text("Progresso").assert(isHeading())
        text("62.5 kg").assertExists()
        text("Sessões recentes").performScrollTo().assertExists()
        button("Editar").assertDoesNotExist()
        button("YAML").assertDoesNotExist()
        // Nothing can use an exercise the library doesn't hold.
        text("Usado em").assertDoesNotExist()
        compose.onNode(hasContentDescription("Voltar") and isButton).assertExists()
    }

    /** Kilograms are stored; the reader's units are shown, in the reading and its rise alike. */
    @Test
    fun `prints its progress in the reader's units`() {
        show(
            files =
                Files(
                    sessions =
                        listOf(
                            session("b", "2026-07-24T10:00:00Z", 62.0),
                            session("a", "2026-07-10T10:00:00Z", 60.0),
                        ),
                    preferences = Preferences(UnitSystem.IMPERIAL, ThemePreference.SYSTEM, false, false, null),
                ),
        )

        text("136.7 lb").assertExists()
        text("▲ 4.4 lb").assert(hasContentDescription("+4.4 lb"))
    }

    /** Bodyweight reps read as most reps, and a dip points down; its colour is checked on a device. */
    @Test
    fun `names a bodyweight exercise's reading as most reps, and points a dip down`() {
        show(
            "pushups",
            Files(sessions = listOf(pushed("2026-07-10T10:00:00Z", 12), pushed("2026-07-24T10:00:00Z", 10))),
        )

        text("Mais repetições · 8 semanas").assertExists()
        text("▼ 2 repetições").assert(hasContentDescription("−2 repetições"))
    }

    /** A hold with a range is charted, and reads as the longest hold. */
    @Test
    fun `names a ranged hold's reading as the longest hold`() {
        show(
            "side-plank",
            Files(
                sessions =
                    listOf(
                        held("2026-07-10T10:00:00Z", "side-plank", 30.0),
                        held("2026-07-24T10:00:00Z", "side-plank", 40.0),
                    ),
            ),
        )

        text("Isometria mais longa · 8 semanas").assertExists()
    }

    /** Pushed (#489): a Back of its own, in place of the sheet's ×. */
    @Test
    fun `has a 48dp Back`() {
        show()

        compose.onNode(hasContentDescription("Voltar") and isButton).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `shows nothing before the library is read`() {
        show(files = Files(library = null))

        text("Este exercício não está mais na sua biblioteca.").assertDoesNotExist()
        text("Squat").assertDoesNotExist()
        // A restore after process death lands here first, and needs a way out before the read.
        compose.onNode(hasContentDescription("Voltar") and isButton).assertExists()
    }

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    class Files(
        private val sessions: List<Session> = emptyList(),
        private val library: Library? = fixture,
        private val preferences: Preferences? = null,
    ) : FakeStoredFiles() {
        override fun sessionLog() = SessionLog(sessions, emptyList())

        override fun library() = library

        override fun preferences() = preferences
    }
}

private val Now = Instant.parse("2026-07-29T18:00:00Z")
private val squat =
    Exercise(
        "squat",
        "Squat",
        ExerciseConfig.Reps(3, 5, restSec = 120.0, targetWeightKg = 60.0),
        notes = "Brace before every rep.",
    )
private val lunge = Exercise("lunge", "Lunge", ExerciseConfig.Reps(3, 10, restSec = 60.0))
private val plank =
    Exercise("plank", "Plank", ExerciseConfig.TimedHold(1, holdSecMin = 45.0, restSec = 30.0))
private val run = Exercise("run", "Run", ExerciseConfig.Cardio(durationSec = 1500.0))
private val pushups = Exercise("pushups", "Push-ups", ExerciseConfig.Reps(3, 10, restSec = 60.0))
private val sidePlank =
    Exercise("side-plank", "Side plank", ExerciseConfig.TimedHold(1, 20.0, holdSecMax = 60.0, restSec = 30.0))
private val fixture =
    Library(
        1,
        listOf(squat, lunge, plank, run, pushups, sidePlank),
        listOf(
            Workout("leg-day", "Leg day", listOf(WorkoutBlock.ExerciseBlock("squat"))),
            Workout("push-day", "Push day", listOf(WorkoutBlock.ExerciseBlock("pushups"))),
        ),
        listOf(Program("base", "Base", listOf(ProgramWeek.Training(1, "Day 1", "leg-day")))),
    )

/** A session of three sets of five squats at [kg], started at [at], finished unless [ended] says not. */
private fun session(
    id: String,
    at: String,
    kg: Double,
    ended: Boolean = true,
) = Session(
    1,
    id,
    "leg-day",
    null,
    null,
    null,
    at,
    if (ended) at else null,
    listOf(SessionEntry.Reps("squat", List(3) { RepsSetLog(5, weightKg = kg, restTakenSec = 120.0) })),
)

/** A finished session of one set of [reps] push-ups, unweighted, started at [at]. */
private fun pushed(
    at: String,
    reps: Long,
) = Session(
    1,
    at,
    "push-day",
    null,
    null,
    null,
    at,
    at,
    listOf(SessionEntry.Reps("pushups", listOf(RepsSetLog(reps, restTakenSec = 60.0)))),
)

/** A finished session of one [sec]-second hold of [exercise], started at [at]. */
private fun held(
    at: String,
    exercise: String,
    sec: Double,
) = Session(
    1,
    at,
    null,
    null,
    null,
    null,
    at,
    at,
    listOf(SessionEntry.TimedHold(exercise, listOf(TimedHoldSetLog(sec, restTakenSec = 30.0)))),
)
