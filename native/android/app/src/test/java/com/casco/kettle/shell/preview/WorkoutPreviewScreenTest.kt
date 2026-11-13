package com.casco.kettle.shell.preview

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import com.casco.kettle.domain.CircuitMember
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.OverrideValue
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.Program
import com.casco.kettle.domain.ProgramOverride
import com.casco.kettle.domain.ProgramWeek
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.SessionParams
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.WorkoutBlock
import com.casco.kettle.shell.files.FakeStoredFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Home's workout preview (#280), in `pt` since an English assertion passes a hardcoded English string,
 * with the library faked behind [StoredFiles]. What the steps say is `:shared`'s; this covers that the
 * screen resolves the start the way the runner does, and its words.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class WorkoutPreviewScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        start: RunnerStart?,
        library: Library,
    ) {
        val latest =
            LatestFiles(Files(library), CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).apply {
                reread()
            }
        compose.setContent { KettleTheme(darkTheme = false) { WorkoutPreviewScreen(start, latest) } }
    }

    private fun library(
        workout: Workout,
        weeks: List<ProgramWeek> = emptyList(),
        exercises: List<Exercise> = listOf(pullUps, dips),
    ) = Library(1, exercises, listOf(workout), if (weeks.isEmpty()) emptyList() else listOf(Program("p", "P", weeks)))

    private fun planned(params: SessionParams) = RunnerStart.Planned(params)

    @Test
    fun `a program week's override reaches the preview, and a bare workout start does not`() {
        val override = ProgramOverride.ExerciseOverride("pull-ups", mapOf("sets" to OverrideValue.Numeric(5.0)))
        val lib = library(pullUpsDay, listOf(ProgramWeek.Training(1, workoutId = "day", overrides = listOf(override))))

        show(planned(SessionParams.ProgramSlot("p", 1, null)), lib)
        compose.onNodeWithText("5 séries", substring = true).assertExists()
        compose.onNodeWithText("3 séries", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the same workout started bare shows the original set count`() {
        val override = ProgramOverride.ExerciseOverride("pull-ups", mapOf("sets" to OverrideValue.Numeric(5.0)))
        val lib = library(pullUpsDay, listOf(ProgramWeek.Training(1, workoutId = "day", overrides = listOf(override))))

        show(planned(SessionParams.Workout("day")), lib)
        compose.onNodeWithText("3 séries", substring = true).assertExists()
        compose.onNodeWithText("5 séries", substring = true).assertDoesNotExist()
    }

    @Test
    fun `lists the first exercise, with its sets and target`() {
        show(planned(SessionParams.Workout("day")), library(pullUpsDay))

        compose.onNodeWithText("Pull-ups").assertExists()
        compose.onNodeWithText("3 séries · alvo 8").assertExists()
        compose.onNodeWithText("FIM DO TREINO").assertExists()
    }

    @Test
    fun `lists every member of a circuit under its rounds kicker`() {
        val circuit = Workout("day", "Day", listOf(WorkoutBlock.Circuit(null, 3, members = members)))
        show(planned(SessionParams.Workout("day")), library(circuit))

        compose.onNodeWithText("CIRCUITO · 3 RODADAS").assertExists()
        compose.onNodeWithText("Pull-ups").assertExists()
        compose.onNodeWithText("Dips").assertExists()
    }

    @Test
    fun `shows a reps exercise's target weight in the metric unit`() {
        val heavy = Exercise("pull-ups", "Pull-ups", ExerciseConfig.Reps(3, 8, targetWeightKg = 20.0, restSec = 90.0))
        show(planned(SessionParams.Workout("day")), library(pullUpsDay, exercises = listOf(heavy)))

        compose.onNodeWithText("3 séries · alvo 8 · 20 kg").assertExists()
    }

    @Test
    fun `a workout that is not in the library is not found`() {
        show(planned(SessionParams.Workout("gone")), library(pullUpsDay))

        compose.onNodeWithText(NOT_FOUND).assertExists()
    }

    /** A preview of a workout no one picked would be a wrong answer, so no start is a missing one. */
    @Test
    fun `no start at all is not found, never the runner's first-workout fallback`() {
        show(null, library(pullUpsDay))

        compose.onNodeWithText(NOT_FOUND).assertExists()
        compose.onNodeWithText("Pull-ups").assertDoesNotExist()
    }

    @Test
    fun `a rest week says so`() {
        val lib = library(pullUpsDay, listOf(ProgramWeek.RestDay(1)))

        show(planned(SessionParams.ProgramSlot("p", 1, null)), lib)

        compose.onNodeWithText("Esta semana do programa é um dia de descanso", substring = true).assertExists()
    }

    @Test
    fun `a workout with nothing to run says so, by name`() {
        val empty = Workout("day", "Empty day", listOf(WorkoutBlock.ExerciseBlock("missing")))
        show(planned(SessionParams.Workout("day")), library(empty))

        compose.onNodeWithText("\"Empty day\" não tem blocos", substring = true).assertExists()
    }

    /** The list under it is what will run, which isn't the whole workout, so the preview says so. */
    @Test
    fun `a workout too long to run in full carries the runner's note, and still lists what will run`() {
        val big = Exercise("big", "Big", ExerciseConfig.Reps(500, 5, restSec = 60.0))
        val long = Workout("day", "Long day", List(22) { WorkoutBlock.ExerciseBlock("big") })
        show(planned(SessionParams.Workout("day")), library(long, exercises = listOf(big)))

        compose.onNodeWithText("\"Long day\" pede mais do que o Kettle consegue rodar", substring = true).assertExists()
        compose.onAllNodesWithText("Big").onFirst().assertExists()
    }

    @Test
    fun `a workout that runs in full carries no note`() {
        show(planned(SessionParams.Workout("day")), library(pullUpsDay))

        compose.onNodeWithText("pede mais do que o Kettle consegue rodar", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the workout's name is a heading`() {
        show(planned(SessionParams.Workout("day")), library(pullUpsDay))

        compose.onNodeWithText("Pull day").assert(isHeading())
    }

    private class Files(
        private val library: Library,
    ) : FakeStoredFiles() {
        override fun sessionLog() = SessionLog(emptyList(), emptyList())

        override fun library() = library

        // Pinned, so the weight's unit is the file's and not whatever the device's locale says.
        override fun preferences() = Preferences(UnitSystem.METRIC, ThemePreference.SYSTEM, false, true, null)
    }
}

private const val NOT_FOUND = "Este treino não está mais na sua biblioteca."
private val pullUps = Exercise("pull-ups", "Pull-ups", ExerciseConfig.Reps(3, 8, restSec = 90.0))
private val dips = Exercise("dips", "Dips", ExerciseConfig.Reps(3, 10, restSec = 90.0))
private val pullUpsDay = Workout("day", "Pull day", listOf(WorkoutBlock.ExerciseBlock("pull-ups")))
private val members = listOf(CircuitMember("pull-ups"), CircuitMember("dips"))
