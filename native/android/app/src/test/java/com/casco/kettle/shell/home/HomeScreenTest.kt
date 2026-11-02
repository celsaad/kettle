package com.casco.kettle.shell.home

import androidx.activity.FullyDrawnReporter
import androidx.activity.FullyDrawnReporterOwner
import androidx.activity.compose.LocalFullyDrawnReporterOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
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
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.WorkoutBlock
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.StoredFiles
import com.casco.kettle.shell.nav.Tab
import com.casco.kettle.shell.rn.Stacking
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Home (#235), case for case from RN's `workouts-list.test.tsx`, `workouts-next-up.test.tsx` and
 * `workouts-startup-marker.test.tsx`, on the harness recipe (#193): in `pt`, since an English
 * assertion passes a hardcoded English string, with the files faked behind [StoredFiles], the clock
 * fixed (every session that a day boundary could move is logged at that instant, so the device's
 * zone can't), and every route heard through a parameter. Beyond jest's: what each control sends, the
 * targets' size, the splash's exit and re-tap-to-top.
 *
 * A queued workout's name is on the card and on its row, so a row is matched as the button carrying
 * the name, which the card's name never is. The screen is a tall one, so the rows under the header are
 * all composed without a scroll before each.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-w411dp-h1600dp")
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<Triple<Stacking, String, Map<String, String>>>()
    private var shown = 0
    private var drawn = 0

    /**
     * The activity's own reporter waits for a frame to be drawn, which Robolectric never does, so this
     * one reports as soon as Home's reporter lets go: what's under test is when Home lets go.
     */
    private val reporter =
        object : FullyDrawnReporterOwner {
            override val fullyDrawnReporter = FullyDrawnReporter(Runnable::run) { drawn++ }
        }
    private val reselects = MutableSharedFlow<Tab>(extraBufferCapacity = 1)

    private fun show(files: Files): LatestFiles {
        val latest =
            LatestFiles(files, CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).apply { reread() }
        showLatest(latest)
        return latest
    }

    private fun showLatest(latest: LatestFiles) =
        compose.setContent {
            CompositionLocalProvider(LocalFullyDrawnReporterOwner provides reporter) {
                KettleTheme(darkTheme = false) {
                    HomeScreen(
                        latest,
                        reselects,
                        open = { how, path, params -> opened += Triple(how, path, params) },
                        onShown = { shown++ },
                        clock = FixedClock,
                    )
                }
            }
        }

    private fun row(name: String) = compose.onNode(hasText(name) and isButton)

    private fun button(text: String) = compose.onNode(hasText(text) and isButton)

    private fun search(query: String) = compose.onNode(hasSetTextAction()).performTextInput(query)

    private fun text(value: String) = compose.onNodeWithText(value)

    private fun top(node: SemanticsNodeInteraction) = node.getBoundsInRoot().top

    // The list: workouts-list.test.tsx.

    /** A library is hand-written and shared, so the order in the file is the order its author meant. */
    @Test
    fun `lists workouts in the order the library file wrote them`() {
        show(listFiles())

        assertTrue(top(row("Zercher day")) < top(row("Bench day")))
        assertTrue(top(row("Bench day")) < top(row("Amrap day")))
    }

    /** The placeholder carries the whole library's count, which used to be a line under the title. */
    @Test
    fun `is translated`() {
        show(listFiles())

        text("Treinos").assert(isHeading())
        compose.onNode(hasSetTextAction() and hasText("Buscar 3 treinos")).assertExists()
    }

    @Test
    fun `search narrows the list to matching names`() {
        show(listFiles())

        search("bench")

        row("Bench day").assertExists()
        row("Zercher day").assertDoesNotExist()
        row("Amrap day").assertDoesNotExist()
    }

    @Test
    fun `search ignores case and matches anywhere in the name`() {
        show(listFiles())

        search("DAY")

        listOf("Zercher day", "Bench day", "Amrap day").forEach { row(it).assertExists() }
    }

    /** "No workouts yet" is right on a fresh install and wrong in front of someone who mistyped one. */
    @Test
    fun `search says nothing matched rather than claiming there are no workouts`() {
        show(listFiles())

        search("zzz")

        text("Nada encontrado").assertExists()
        text("Nenhum treino ainda").assertDoesNotExist()
    }

    @Test
    fun `still tells a new library it has no workouts yet`() {
        show(Files(library(workouts = emptyList())))

        text("Nenhum treino ainda").assertExists()
        text("Nada encontrado").assertDoesNotExist()
    }

    // Nothing to search in, so the box would only be something else to tap past.
    @Test
    fun `offers no search box on an empty library`() {
        show(Files(library(workouts = emptyList())))

        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    // Keyed off the whole library: tied to what's visible it would vanish under the finger typing, and
    // with it the way to clear the search. Jest also pinned the placeholder's count staying at 3, which
    // a Compose field can't show: its placeholder goes once there is text, RN's reason the count never
    // narrows.
    @Test
    fun `keeps the search box while a search narrows the list to nothing`() {
        show(listFiles())

        search("zzz")

        row("Zercher day").assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText("zzz")).assertExists()
    }

    @Test
    fun `a row opens its workout in the editor, and its start button starts it`() {
        show(listFiles())

        row("Bench day").assertHeightIsAtLeast(48.dp).performClick()
        compose
            .onNode(hasContentDescription("Iniciar Bench day") and isButton)
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .performClick()

        assertEquals(
            listOf(
                Triple(Stacking.Push, "/workout-editor", mapOf("id" to "bench")),
                Triple(Stacking.Push, "/session", mapOf("workoutId" to "bench")),
            ),
            opened,
        )
    }

    // The card: workouts-next-up.test.tsx.

    /** The title and the gear sit above everything an empty library affects, and the gear is the way out. */
    @Test
    fun `with no workouts keeps the screen chrome rather than rendering nothing`() {
        show(Files(library(workouts = emptyList())))

        text("Treinos").assertExists()
        compose.onNode(hasContentDescription("Configurações") and isButton).assertExists()
        text("Nenhum treino ainda").assertExists()
        text("Iniciar treino").assertDoesNotExist()
        // No card saying the library is empty: the list's own empty state already does.
        text("PRÓXIMO").assertDoesNotExist()
    }

    @Test
    fun `with no workouts offers a way out, so the empty state is not a dead end`() {
        show(Files(library(workouts = emptyList())))

        compose
            .onNode(hasContentDescription("Novo treino") and isButton)
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .performClick()

        assertEquals(listOf(Triple(Stacking.Push, "/workout-editor", emptyMap<String, String>())), opened)
    }

    /** The only route to Settings until the preferences flip, so losing it would strand the user. */
    @Test
    fun `the gear opens Settings`() {
        show(Files(library(workouts = emptyList())))

        compose
            .onNode(hasContentDescription("Configurações") and isButton)
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .performClick()

        assertEquals(listOf(Triple(Stacking.Push, "/settings", emptyMap<String, String>())), opened)
    }

    @Test
    fun `still suggests a workout when the library has one`() {
        show(seededFiles())

        // Twice: once on the card, once on its row.
        compose.onAllNodesWithText("Push day").assertCountEquals(2)
        text("Iniciar treino").assertExists()
        text("Nenhum treino ainda").assertDoesNotExist()
    }

    @Test
    fun `the card starts its program slot with that week's params`() {
        show(Files(library(programs = listOf(program(ProgramWeek.Training(1, "Day 1", "push-day"))))))

        text("PRÓXIMO · Semana 1 · Day 1").assertExists()
        button("Iniciar treino").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(
            listOf(Triple(Stacking.Push, "/session", mapOf("programId" to "base", "week" to "1", "day" to "Day 1"))),
            opened,
        )
    }

    // First-run guidance, derived from history rather than persisted: never having logged a session is
    // what being new means.

    @Test
    fun `first-run guidance is shown to someone who has never logged a session`() {
        show(seededFiles())

        text("PRIMEIRA VEZ?").assertExists()
        text("Comece o treino abaixo").assertExists()
        text("Deixe do seu jeito").assertExists()
        text("Sua biblioteca é um arquivo").assertExists()
    }

    @Test
    fun `first-run guidance is gone once a session has been logged`() {
        show(seededFiles(sessions = listOf(session())))

        text("PRIMEIRA VEZ?").assertDoesNotExist()
        // The card going away, not the tab.
        text("Iniciar treino").assertExists()
    }

    /**
     * Step 3's claim is honest because the starter-pack link sits directly under it, and that order is
     * the one thing here that isn't structural: the link before the next-up card, nothing between.
     */
    @Test
    fun `renders the ownership claim directly above the link that acts on it`() {
        show(seededFiles())

        val claim = top(text("Sua biblioteca é um arquivo"))
        val link = top(text("Adicionar um programa inicial"))
        val card = top(text("PRÓXIMO"))

        assertTrue(claim < link)
        assertTrue(link < card)
    }

    // A brand-new user who cleared the seeded library: step one would point at a workout that isn't there.
    @Test
    fun `first-run guidance stays out of the empty state, which is its own single instruction`() {
        show(Files(library(workouts = emptyList())))

        text("PRIMEIRA VEZ?").assertDoesNotExist()
        text("Nenhum treino ainda").assertExists()
    }

    /** Gated on a queued workout, not on any card: a rest card has no Start button for step one. */
    @Test
    fun `first-run guidance stays out of a rest day, which has no workout to point at`() {
        show(restDayFiles())

        text("Dia de descanso").assertExists()
        text("Iniciar treino").assertDoesNotExist()
        text("PRIMEIRA VEZ?").assertDoesNotExist()
        text("Comece o treino abaixo").assertDoesNotExist()
    }

    // The starter-pack link, gated on no sessions and some workouts rather than on the first-run card.

    @Test
    fun `the starter-pack link is offered to someone who has never logged a session, and opens import`() {
        show(seededFiles())

        button("Adicionar um programa inicial").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(listOf(Triple(Stacking.Push, "/import", emptyMap<String, String>())), opened)
    }

    @Test
    fun `the starter-pack link is gone once a session has been logged`() {
        show(seededFiles(sessions = listOf(session())))

        text("Adicionar um programa inicial").assertDoesNotExist()
    }

    /** The case the first-run gate gets wrong: a new user on a program that opens with a rest day. */
    @Test
    fun `the starter-pack link is offered on a rest day, which the first-run card stays out of`() {
        show(restDayFiles())

        text("Dia de descanso").assertExists()
        text("PRIMEIRA VEZ?").assertDoesNotExist()
        text("Adicionar um programa inicial").assertExists()
    }

    /** An empty library gets the offer from its empty state, so the header's stands down. */
    @Test
    fun `the starter-pack link appears exactly once on an empty library, from the empty state`() {
        show(Files(library(workouts = emptyList())))

        text("Nenhum treino ainda").assertExists()
        compose.onAllNodesWithText("Adicionar um programa inicial").assertCountEquals(1)
    }

    /** The two used to be copies, and the empty state's was the one nothing pressed. */
    @Test
    fun `the starter-pack link opens import from the empty state too`() {
        show(Files(library(workouts = emptyList())))

        button("Adicionar um programa inicial").performClick()

        assertEquals(listOf(Triple(Stacking.Push, "/import", emptyMap<String, String>())), opened)
    }

    // A long workout: the card names no block, and its one summary line is what scales.

    private fun longFiles(): Files {
        val names =
            listOf(
                "Squat",
                "Bench",
                "Row",
                "Press",
                "Curl",
                "Dip",
                "Lunge",
                "Plank",
                "Fly",
                "Pulldown",
                "Calf",
                "Crunch",
            )
        val exercises = names.map { Exercise(it.lowercase(), it, ExerciseConfig.Reps(3, 10, restSec = 60.0)) }
        val long = Workout("long", "Long day", exercises.map { WorkoutBlock.ExerciseBlock(it.id) })
        return Files(Library(1, exercises, listOf(long), emptyList()))
    }

    @Test
    fun `a long workout names no individual block on the card`() {
        show(longFiles())

        text("Squat").assertDoesNotExist()
        text("Dip").assertDoesNotExist()
        text("Crunch").assertDoesNotExist()
        compose.onNodeWithText("a mais", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a long workout is described in a single summary line instead`() {
        show(longFiles())

        // 12 × 3 sets × 60s of rest is 36 minutes. Twice: the card and the row.
        compose.onAllNodesWithText("12 blocos · repetições · ~36 min").assertCountEquals(2)
        text("Iniciar treino").assertExists()
    }

    // The card's height caps: line caps rather than a fixed height, which clips at large text sizes.

    /** A note far longer than any card should render, which is what a real program week can carry. */
    private val longNote =
        "Deload week: keep every working set at RPE 7 or below, add a set only if the last one moved fast, " +
            "and stop entirely if the bar speed drops. Sleep is the priority this week."

    /**
     * The cap a text was laid out with, which is the mechanism, as jest read `numberOfLines`: Robolectric
     * measures no real glyph widths, so nothing ever wraps for a rendered line count to show.
     */
    private fun maxLines(node: SemanticsNodeInteraction): Int {
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single().layoutInput.maxLines
    }

    @Test
    fun `caps a week's note, which is unbounded user text`() {
        show(
            Files(
                library(programs = listOf(program(ProgramWeek.Training(1, workoutId = "push-day", notes = longNote)))),
            ),
        )

        assertEquals(2, maxLines(text(longNote)))
    }

    /** The card's copy only: the row is one line of a scrolling list, not what stands above it. */
    @Test
    fun `caps the workout name, which is also the user's to make as long as they like`() {
        show(
            Files(
                library(programs = listOf(program(ProgramWeek.Training(1, workoutId = "push-day", notes = longNote)))),
            ),
        )

        val copies = compose.onAllNodesWithText("Push day")
        copies.assertCountEquals(2)
        assertEquals(listOf(2, Int.MAX_VALUE), (0..1).map { maxLines(copies[it]) }.sorted())
    }

    @Test
    fun `caps a rest day's note the same way`() {
        val weeks = arrayOf(ProgramWeek.Training(1, "Day 1", "push-day"), ProgramWeek.RestDay(1, "Day 2", longNote))
        show(Files(library(programs = listOf(program(*weeks))), sessions = listOf(programSession("Day 1"))))

        assertEquals(2, maxLines(text(longNote)))
    }

    // The empty session, outside the card's conditional so every branch carries it.

    @Test
    fun `offers the empty session alongside a queued workout`() {
        show(seededFiles())

        text("Começar uma sessão vazia").assertExists()
    }

    @Test
    fun `offers the empty session with no workouts to run at all`() {
        show(Files(library(workouts = emptyList())))

        text("Nenhum treino ainda").assertExists()
        text("Começar uma sessão vazia").assertExists()
    }

    @Test
    fun `starts the empty session with the ad-hoc flag rather than a workout id`() {
        show(seededFiles())

        button("Começar uma sessão vazia").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(listOf(Triple(Stacking.Push, "/session", mapOf("adhoc" to "1"))), opened)
    }

    // A scheduled rest day: no Start button, which is the whole claim, and two ways not to be stuck.

    /** A three-slot week whose middle slot is rest, with the first slot logged today. */
    private fun restDayProgramFiles(sessions: List<Session> = listOf(programSession("Day 1"))) =
        Files(
            library(
                workouts = listOf(pushDay, Workout("pull-day", "Pull day", emptyList())),
                programs =
                    listOf(
                        program(
                            ProgramWeek.Training(1, "Day 1", "push-day"),
                            ProgramWeek.RestDay(1, "Day 2", "Walk, nothing heavy."),
                            ProgramWeek.Training(1, "Day 3", "pull-day"),
                        ),
                    ),
            ),
            sessions = sessions,
        )

    @Test
    fun `a rest day replaces the workout card and offers nothing to start`() {
        show(restDayProgramFiles())

        text("Dia de descanso").assertExists()
        text("DIA DE DESCANSO · Semana 1 · Day 2").assertExists()
        text("Iniciar treino").assertDoesNotExist()
        text("PRÓXIMO").assertDoesNotExist()
    }

    /** User data, so it stays in the language it was written in on a translated screen. */
    @Test
    fun `a rest day shows the week's own note, which is the user's own text`() {
        show(restDayProgramFiles())

        text("Walk, nothing heavy.").assertExists()
    }

    @Test
    fun `a rest day is not followed by the empty-library state`() {
        show(restDayProgramFiles())

        text("Nenhum treino ainda").assertDoesNotExist()
    }

    @Test
    fun `a rest day jumps to the next slot that runs something when asked`() {
        show(restDayProgramFiles())

        button("Treinar mesmo assim").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(
            listOf(Triple(Stacking.Push, "/session", mapOf("programId" to "base", "week" to "1", "day" to "Day 3"))),
            opened,
        )
    }

    // Logging only zeros is skipping the workout, so Day 1 stays up next. Read the raw sessions rather
    // than the logged ones and this fails.
    @Test
    fun `a rest day is not reached by a session that logged nothing but zeros`() {
        val zeros =
            programSession(
                "Day 1",
            ).copy(entries = listOf(SessionEntry.Reps("pushups", listOf(RepsSetLog(0, restTakenSec = 0.0)))))
        show(restDayProgramFiles(listOf(zeros)))

        text("Dia de descanso").assertDoesNotExist()
        text("Iniciar treino").assertExists()
    }

    @Test
    fun `a rest day leaves the empty session available, so it is never a dead end`() {
        show(restDayProgramFiles())

        text("Começar uma sessão vazia").assertExists()
    }

    // The startup marker: workouts-startup-marker.test.tsx. Where the benchmark stops its clock, so it
    // must not fire on a frame drawn before the library has loaded.

    @Test
    fun `reports Home drawn once its loaded content lays out`() {
        show(seededFiles())
        compose.waitForIdle()

        assertEquals(1, drawn)
    }

    @Test
    fun `does not report while the library is still loading`() {
        val files = Files(library = null)
        val latest = show(files)
        compose.waitForIdle()

        assertEquals(0, drawn)

        files.library = library()
        latest.reread()
        compose.waitForIdle()

        assertEquals(1, drawn)
    }

    /**
     * The splash waits for Home's first layout after the files are read, and not for the library: on a
     * fresh install RN writes the seed only once it has started, and a splash waiting on that would
     * hang the app whenever RN couldn't.
     */
    @Test
    fun `lets the splash go once the files have been read, library or not`() {
        val files = Files(library = null)
        val latest = LatestFiles(files, CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined)
        showLatest(latest)
        compose.waitForIdle()
        assertEquals(0, shown)

        latest.reread()
        compose.waitForIdle()
        assertEquals(1, shown)

        files.library = library()
        latest.reread()
        compose.waitForIdle()
        assertEquals(1, shown)
    }

    /** #153 dropped re-tap-to-top with RN's bar; each tab's port brings it back for its own list. */
    @Test
    fun `a tap on the Workouts tab while it shows scrolls back to the top`() {
        val many = (1..60).map { Workout("w$it", "Workout $it", emptyList()) }
        show(Files(library(workouts = many), sessions = listOf(session())))
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(many.size)
        text("Treinos").assertDoesNotExist()

        reselects.tryEmit(Tab.Library)
        compose.waitForIdle()
        text("Treinos").assertDoesNotExist()

        reselects.tryEmit(Tab.Workouts)
        compose.waitForIdle()
        text("Treinos").assertIsDisplayed()
    }

    @Test
    fun `scrolls to reach the last row`() {
        val many = (1..60).map { Workout("w$it", "Workout $it", emptyList()) }
        show(Files(library(workouts = many), sessions = listOf(session())))

        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Workout 60"))

        row("Workout 60").assertIsDisplayed()
    }

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private class Files(
        var library: Library?,
        private val sessions: List<Session> = emptyList(),
    ) : StoredFiles {
        override fun sessionLog() = SessionLog(sessions, emptyList())

        override fun library() = library

        override fun preferences(): Preferences? = null

        override fun deleteSession(id: String) = error("Home deletes nothing")

        override fun writeSession(session: Session) = error("Home writes no session")
    }

    private fun library(
        workouts: List<Workout> = listOf(pushDay),
        programs: List<Program> = emptyList(),
    ) = Library(1, listOf(pushups), workouts, programs)

    /** The seeded shape a first run has: something to run, nothing logged yet. */
    private fun seededFiles(sessions: List<Session> = emptyList()) = Files(library(), sessions)

    private fun listFiles() =
        Files(
            library(workouts = listOf(zercher, bench, never)),
            sessions = listOf(session("bench", "2026-07-20T09:00:00Z"), session("zercher", "2026-07-01T09:00:00Z")),
        )

    /** A program whose very first slot is a rest day, opened by someone who has logged nothing. */
    private fun restDayFiles() =
        Files(
            library(
                programs =
                    listOf(
                        program(ProgramWeek.RestDay(1, "Day 1"), ProgramWeek.Training(1, "Day 2", "push-day")),
                    ),
            ),
        )

    private fun program(vararg weeks: ProgramWeek) = Program("base", "Base", weeks.toList())

    private fun session(
        workout: String = "push-day",
        startedAt: String = "2026-07-29T09:00:00Z",
    ) = Session(1, startedAt, workout, null, null, null, startedAt, startedAt, listOf(oneSet))

    /** A session of [day] in week 1 of `base`, logged at [Now], so its rest slot is what's next. */
    private fun programSession(day: String) =
        Session(1, "s", "push-day", "base", 1, day, Now.toString(), Now.toString(), listOf(oneSet))

    private object FixedClock : Clock {
        override fun now() = Now
    }
}

private val Now = Instant.parse("2026-07-29T18:00:00Z")
private val pushups = Exercise("pushups", "Push-ups", ExerciseConfig.Reps(3, 10, restSec = 60.0))
private val oneSet = SessionEntry.Reps("pushups", listOf(RepsSetLog(10, restTakenSec = 60.0)))
private val pushDay = Workout("push-day", "Push day", listOf(WorkoutBlock.ExerciseBlock("pushups")))
private val zercher = Workout("zercher", "Zercher day", emptyList())
private val bench = Workout("bench", "Bench day", emptyList())
private val never = Workout("never", "Amrap day", emptyList())
