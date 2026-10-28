package com.casco.kettle.shell.stats

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
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
import com.casco.kettle.domain.RepsSetLog
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.TimedHoldSetLog
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.StoredFiles
import com.casco.kettle.shell.rn.Stacking
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Stats (#201), case for case from RN's `analytics.test.tsx`, on the harness recipe (#193): in `pt`,
 * since an English assertion passes a hardcoded English string, with the files faked behind
 * [StoredFiles] and the clock, zone and first weekday parameters.
 *
 * The windowing, the measure, the fixed-hold rule and the calendar's arithmetic are `:shared`'s
 * (`ExerciseProgressTest`, `HistoryStatsTest`). What these cover is what the screen adds: a signed
 * delta in the reader's words, flat rows folded away until asked for, which empty line shows when, the
 * week rows named for a screen reader, and the way into an exercise's progress.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class StatsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    /** A Tuesday: the current week began yesterday, on the Monday the tests start weeks on. */
    private val now = Instant.parse("2026-08-18T12:00:00.000Z")

    private val opened = mutableListOf<Triple<Stacking, String, Map<String, String>>>()

    private fun show(
        exercises: List<Exercise>,
        sessions: List<Session>,
    ) {
        val files =
            LatestFiles(
                Files(SessionLog(sessions, emptyList()), exercises),
                CoroutineScope(Dispatchers.Unconfined),
                io = Dispatchers.Unconfined,
            ).apply { reread() }
        compose.setContent {
            KettleTheme(darkTheme = false) {
                StatsScreen(files, now, TimeZone.UTC, DayOfWeek.MONDAY) { how, path, params ->
                    opened += Triple(how, path, params)
                }
            }
        }
    }

    /** One weekly session per weight, oldest first, the last one a week ago. */
    private fun withSessions(weights: List<Double>) =
        show(
            listOf(rdl),
            weights.mapIndexed { index, weightKg -> session((weights.size - index) * 7, loaded(weightKg)) },
        )

    @Test
    fun `names the exercise, what it is at now, and what it moved by`() {
        withSessions(listOf(10.0, 12.0))

        // The name is user data and renders verbatim. 12 kg beat 10 kg inside the window, so it is marked.
        compose.onNodeWithText("Dumbbell RDL").assertExists()
        compose.onNodeWithText("12 kg · melhor marca").assertExists()
        compose.onNodeWithText("+2 kg").assertExists()
    }

    @Test
    fun `signs a loss without dressing it as a gain or a record`() {
        withSessions(listOf(12.0, 10.0))

        compose.onNodeWithText("−2 kg").assertExists()
        compose.onNodeWithText("10 kg").assertExists()
    }

    /**
     * A flat row is a real answer, so it stays reachable, but behind one row so a dozen of them can't
     * bury the exercise that moved. Its expand and collapse actions are what TalkBack reads as state.
     */
    @Test
    fun `folds a flat exercise away until the held-steady row is opened`() {
        withSessions(listOf(10.0, 10.0))

        compose.onNodeWithText("Nada mudou nas últimas 8 semanas.").assertExists()
        compose.onNodeWithText("Dumbbell RDL").assertDoesNotExist()
        val toggle = compose.onNode(hasText("1 sem mudança") and isButton)
        toggle.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)).assertHeightIsAtLeast(48.dp)

        toggle.performScrollTo().performClick()

        compose.onNodeWithText("Dumbbell RDL").assertExists()
        compose.onNodeWithText("sem mudança").assertExists()
        compose.onNodeWithText("+0 kg").assertDoesNotExist()
        toggle.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
    }

    // One session is not a trend, and the screen says so rather than showing an empty heading.
    @Test
    fun `invites a second session instead of listing a single one`() {
        withSessions(listOf(10.0))

        compose.onNodeWithText("Treine algo duas vezes e a evolução aparece aqui.").assertExists()
        compose.onNodeWithText("Dumbbell RDL").assertDoesNotExist()
    }

    /** Someone whose repeated work is all fixed-length stretching has trained plenty twice. */
    @Test
    fun `explains an empty section when everything repeated was a fixed-length hold`() {
        show(listOf(hurdler), listOf(session(14, stretch), session(7, stretch)))

        compose.onNodeWithText("foi uma isometria com tempo fixo", substring = true).assertExists()
        compose.onNodeWithText("Treine algo duas vezes e a evolução aparece aqui.").assertDoesNotExist()
        compose.onNodeWithText("Hurdler").assertDoesNotExist()
    }

    @Test
    fun `says fixed-length holds were left out when other rows are shown`() {
        show(listOf(rdl, hurdler), listOf(session(14, loaded(10.0), stretch), session(7, loaded(12.0), stretch)))

        compose.onNodeWithText("Isometrias com tempo fixo não têm como subir", substring = true).assertExists()
        compose.onNodeWithText("Dumbbell RDL").assertExists()
        compose.onNodeWithText("Hurdler").assertDoesNotExist()
    }

    /** The cells are a drawing, so each week's row has to say what its cells show. */
    @Test
    fun `names each calendar week's sessions and time for a screen reader`() {
        val start = now - 60.minutes
        show(listOf(rdl), listOf(session(start, start + 30.minutes, loaded(60.0))))

        compose.onNode(describedAs(Regex("^Semana de .+: 1 sessão, 0h 30min$"))).assertExists()
        // The three before it are rest, and say so in the plural their zero takes.
        assertEquals(
            3,
            compose.onAllNodes(describedAs(Regex("^Semana de .+: 0 sessões, 0h 0min$"))).fetchSemanticsNodes().size,
        )
    }

    /** The THIS WEEK tiles count the week the calendar's last row shows, and a day streak. */
    @Test
    fun `counts this week's sessions, time and streak`() {
        val start = now - 60.minutes
        show(listOf(rdl), listOf(session(start, start + 90.minutes, loaded(60.0))))

        // Each tile is one node, its number read with what it counts.
        compose.onNode(hasText("1") and hasText("sessões")).assertExists()
        compose.onNode(hasText("1h 30min") and hasText("tempo")).assertExists()
        compose.onNode(hasText("1") and hasText("dias seguidos")).assertExists()
    }

    // The row is the way into the exercise's own progress screen, and it has to say which exercise.
    @Test
    fun `opens an exercise's progress screen from its row`() {
        withSessions(listOf(10.0, 12.0))

        compose
            .onNode(hasText("Dumbbell RDL") and isButton)
            .assertHeightIsAtLeast(48.dp)
            .assert(clickLabel("ver evolução"))
            .performScrollTo()
            .performClick()

        assertEquals(listOf(Triple(Stacking.Push, "/exercise-progress", mapOf("exerciseId" to "rdl"))), opened)
    }

    // History's header already says the all-time totals, one screen back, in the same words.
    @Test
    fun `does not repeat the all-time totals`() {
        withSessions(listOf(10.0, 12.0))

        compose.onNodeWithText("Todo o período").assertDoesNotExist()
    }

    /** Before the log is read there's nothing to say, rather than a "train something twice" that flashes. */
    @Test
    fun `says nothing about progress before the log is read`() {
        compose.setContent {
            KettleTheme(darkTheme = false) {
                StatsScreen(
                    LatestFiles(
                        Files(SessionLog(emptyList(), emptyList()), emptyList()),
                        CoroutineScope(Dispatchers.Unconfined),
                    ),
                    now,
                    TimeZone.UTC,
                    DayOfWeek.MONDAY,
                ) { _, _, _ -> }
            }
        }

        compose.onNodeWithText("Seus números").assert(isHeading())
        compose.onNodeWithText("Treine algo duas vezes e a evolução aparece aqui.").assertDoesNotExist()
    }

    @Test
    fun `is translated`() {
        withSessions(listOf(10.0, 12.0))

        compose.onNodeWithText("Seus números").assert(isHeading())
        compose.onNodeWithText("FICANDO MAIS FORTE").assertExists()
        compose.onNodeWithText("DIAS DE TREINO · ÚLTIMAS 4 SEMANAS").assertExists()
        compose.onNodeWithText("Descanso").assertExists()
        compose.onNodeWithText("12 kg · melhor marca").assertExists()
        // The exercise name is the user's and is never translated, sitting among strings that are.
        compose.onNodeWithText("Dumbbell RDL").assertExists()
    }

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun clickLabel(label: String) =
        SemanticsMatcher("click label $label") { node ->
            node.config.getOrNull(SemanticsActions.OnClick)?.label == label
        }

    private fun describedAs(pattern: Regex) =
        SemanticsMatcher("described as $pattern") { node ->
            node.config
                .getOrNull(SemanticsProperties.ContentDescription)
                .orEmpty()
                .any(pattern::matches)
        }

    private fun session(
        daysAgo: Int,
        vararg entries: SessionEntry,
    ): Session {
        val at = now - daysAgo.days
        return session(at, at, *entries)
    }

    private fun session(
        startedAt: Instant,
        endedAt: Instant,
        vararg entries: SessionEntry,
    ) = Session(
        1,
        startedAt.toString(),
        null,
        null,
        null,
        null,
        startedAt.toString(),
        endedAt.toString(),
        entries.toList(),
    )

    private class Files(
        private val log: SessionLog,
        private val exercises: List<Exercise>,
    ) : StoredFiles {
        override fun sessionLog() = log

        override fun library() = Library(1, exercises, emptyList(), emptyList())

        override fun preferences() = Preferences(UnitSystem.METRIC, ThemePreference.SYSTEM, false, true, null)
    }
}

private val rdl = Exercise("rdl", "Dumbbell RDL", ExerciseConfig.Reps(sets = 3, targetRepsMin = 8, restSec = 60.0))

private val hurdler =
    Exercise("hurdler", "Hurdler", ExerciseConfig.TimedHold(sets = 1, holdSecMin = 45.0, restSec = 30.0))

private fun loaded(weightKg: Double) =
    SessionEntry.Reps("rdl", listOf(RepsSetLog(reps = 8, weightKg = weightKg, restTakenSec = 60.0)))

private val stretch = SessionEntry.TimedHold("hurdler", listOf(TimedHoldSetLog(holdSec = 45.0, restTakenSec = 30.0)))
