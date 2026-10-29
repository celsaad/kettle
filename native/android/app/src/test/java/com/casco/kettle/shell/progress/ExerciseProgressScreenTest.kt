package com.casco.kettle.shell.progress

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.StoredFiles
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.days

/**
 * Exercise progress (#200), case for case from RN's `exercise-progress.test.tsx`, on the harness recipe
 * (#193): in `pt`, since an English assertion passes a hardcoded English string, with the files
 * faked behind [StoredFiles] and the clock a parameter.
 *
 * What the row measures is `ExerciseProgressTest`'s job in `:shared`. These cover the screen's wiring:
 * it reads the row for the exercise it was opened with, prints it in the reader's units, lists the
 * sessions newest first with their marks, and degrades to a line when the row is gone. The chart is a
 * Canvas with no semantics, so its geometry is `ProgressChartLayoutTest`'s and the running app's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class ExerciseProgressScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-08-18T12:00:00.000Z")

    /** One weekly session per weight, oldest first, the last one a week ago. */
    private fun show(
        weights: List<Double>,
        units: UnitSystem = UnitSystem.METRIC,
        exerciseId: String = "rdl",
    ) {
        val sessions =
            weights.mapIndexed { index, weightKg ->
                val startedAt = (now - ((weights.size - index) * 7).days).toString()
                Session(1, startedAt, null, null, null, null, startedAt, startedAt, listOf(loaded(weightKg)))
            }
        val files =
            LatestFiles(
                Files(SessionLog(sessions.reversed(), emptyList()), units),
                CoroutineScope(Dispatchers.Unconfined),
                io = Dispatchers.Unconfined,
            ).apply { reread() }
        compose.setContent {
            KettleTheme(darkTheme = false) {
                ExerciseProgressScreen(exerciseId, files, now, TimeZone.UTC)
            }
        }
    }

    @Test
    fun `shows where the exercise is now and how far it moved`() {
        show(listOf(10.0, 12.0, 12.0))

        // The name is user data and renders verbatim, as the sheet's heading.
        compose.onNodeWithText("Dumbbell RDL").assert(isHeading())
        compose.onNodeWithText("+2 kg").assertExists()
        compose.onNodeWithText("desde ", substring = true).assertExists()
    }

    /** Kilograms are stored; the reader's units are shown, in the headline and the list alike. */
    @Test
    fun `prints weights in the reader's units`() {
        show(listOf(10.0, 12.0), units = UnitSystem.IMPERIAL)

        compose.onNodeWithText("+4.4 lb").assertExists()
        compose.onAllNodesWithText("26.5 lb", substring = true).fetchSemanticsNodes().let {
            assertEquals(2, it.size)
        }
    }

    /**
     * The list is the chart's table: every session, newest first, the one that set a new best marked.
     * The second beat the first; the third only matched it, so it isn't marked: a tie is not a record.
     */
    @Test
    fun `lists every session newest first, marking the one that set a new best`() {
        show(listOf(10.0, 12.0, 12.0))

        val values =
            compose
                .onAllNodes(SemanticsMatcher("a weight") { node -> node.text()?.matches(WEIGHT) == true })
                .fetchSemanticsNodes()
                .map { it.text() }
        // The headline's comes first, then the list: 12, 12 (the new best), 10.
        assertEquals(listOf("12 kg", "12 kg", "12 kg · nova melhor marca", "10 kg"), values)
    }

    /** A stale route still names the exercise and says why the sheet is empty, rather than a blank. */
    @Test
    fun `says there is nothing to chart when the exercise no longer has a row`() {
        show(listOf(10.0))

        compose.onNodeWithText("Dumbbell RDL").assertExists()
        compose.onNodeWithText("Nada para mostrar deste exercício nas últimas 8 semanas.").assertExists()
    }

    @Test
    fun `is translated`() {
        show(listOf(10.0, 12.0))

        compose.onNodeWithText("SESSÕES · ÚLTIMAS 8 SEMANAS").assertExists()
        compose.onNodeWithText("desde ", substring = true).assertExists()
        compose.onNodeWithText("Nova melhor marca").assertExists()
        // The exercise name is the user's and is never translated.
        compose.onNodeWithText("Dumbbell RDL").assertExists()
    }

    /** Nothing here is a control: the × is the sheet's, and the chart is a drawing the list repeats. */
    @Test
    fun `offers nothing to press`() {
        show(listOf(10.0, 12.0))

        assertEquals(0, compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
    }

    private class Files(
        private val log: SessionLog,
        private val units: UnitSystem,
    ) : StoredFiles {
        override fun sessionLog() = log

        override fun library() = Library(1, listOf(rdl), emptyList(), emptyList())

        override fun preferences() = Preferences(units, ThemePreference.SYSTEM, false, true, null)

        override fun deleteSession(id: String) = error("this screen deletes nothing")
    }
}

private val rdl = Exercise("rdl", "Dumbbell RDL", ExerciseConfig.Reps(sets = 3, targetRepsMin = 8, restSec = 60.0))

private fun loaded(weightKg: Double) =
    SessionEntry.Reps("rdl", listOf(RepsSetLog(reps = 8, weightKg = weightKg, restTakenSec = 60.0)))

private val WEIGHT = Regex("""^1[02] kg.*""")

private fun androidx.compose.ui.semantics.SemanticsNode.text() =
    config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
