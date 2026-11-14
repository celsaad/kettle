package com.casco.kettle.shell.home

import android.content.Context
import androidx.activity.FullyDrawnReporter
import androidx.activity.FullyDrawnReporterOwner
import androidx.activity.compose.LocalFullyDrawnReporterOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.OverrideValue
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.Program
import com.casco.kettle.domain.ProgramOverride
import com.casco.kettle.domain.ProgramWeek
import com.casco.kettle.domain.RepsSetLog
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.SessionParams
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.WorkoutBlock
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.FakeStoredFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.nav.Section
import com.casco.kettle.shell.nav.Tab
import com.casco.kettle.shell.nav.routeParams
import com.casco.kettle.shell.programdetail.PROGRAM_ID_PARAM
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Home (#235), case for case from RN's `workouts-list.test.tsx`, `workouts-next-up.test.tsx` and
 * `workouts-startup-marker.test.tsx`, on the harness recipe (#193): in `pt`, since an English
 * assertion passes a hardcoded English string, with the files faked behind [StoredFiles], the clock
 * fixed (every session that a day boundary could move is logged at that instant, so the device's
 * zone can't), and every route heard through a parameter. Beyond jest's: what each control sends, the
 * targets' size and re-tap-to-top. The splash's exit is the shell's (`ShellTest`).
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

    private val opened = mutableListOf<Route>()

    /** Every start, which opens the runner. */
    private val started = mutableListOf<RunnerStart>()
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
                        open = { opened += it },
                        startRunner = { started += it },
                        clock = clock,
                    )
                }
            }
        }

    /**
     * A list row's button. The next-up card's text is a button too since #280, holding the queued
     * workout's name, so the card is told apart by the click label only its preview has.
     */
    private fun row(name: String) = compose.onNode(hasText(name) and isButton and !opensPreview)

    private val opensPreview =
        SemanticsMatcher("opens the workout preview") {
            it.config.getOrNull(SemanticsActions.OnClick)?.label ==
                ApplicationProvider.getApplicationContext<Context>().getString(R.string.today_previewAction)
        }

    private fun button(text: String) = compose.onNode(hasText(text) and isButton)

    private fun text(value: String) = compose.onNodeWithText(value)

    private fun top(node: SemanticsNodeInteraction) = node.getBoundsInRoot().top

    /** Empty session's start, a plus named by what it does (#412). */
    private fun emptySession() = compose.onNode(hasContentDescription("Começar uma sessão vazia") and isButton)

    // The card: workouts-next-up.test.tsx.

    /** The title and the gear sit above everything an empty library affects, and the gear is the way out. */
    @Test
    fun `with no workouts keeps the screen chrome rather than rendering nothing`() {
        show(Files(library(workouts = emptyList())))

        text("Hoje").assertExists()
        compose.onNode(hasContentDescription("Configurações") and isButton).assertExists()
        text("Nenhum treino ainda").assertExists()
        text("Iniciar treino").assertDoesNotExist()
        // No card saying the library is empty: the list's own empty state already does.
        compose.onNodeWithText("Próximo", substring = true).assertDoesNotExist()
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

        assertEquals(listOf(Route(Destination.Settings)), opened)
    }

    @Test
    fun `still suggests a workout when the library has one`() {
        show(seededFiles())

        // Once: "Off the plan" leaves out the workout the card already offers.
        compose.onAllNodesWithText("Push day").assertCountEquals(1)
        text("Iniciar treino").assertExists()
        text("Nenhum treino ainda").assertDoesNotExist()
    }

    @Test
    fun `the card starts its program slot with that week's params`() {
        show(Files(library(programs = listOf(program(ProgramWeek.Training(1, "Day 1", "push-day"))))))

        text("Próximo · Day 1 · 1 exercício, 3 séries").assertExists()
        button("Iniciar treino").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(listOf(RunnerStart.Planned(SessionParams.ProgramSlot("base", 1, "Day 1"))), started)
    }

    // The schema takes `day: ""`, and RN tested the day for truth, so an empty one is none, and the
    // slot falls back to its week.
    @Test
    fun `labels a week with an empty day by its number alone`() {
        show(Files(library(programs = listOf(program(ProgramWeek.Training(1, "", "push-day"))))))

        text("Próximo · Semana 1 · 1 exercício, 3 séries").assertExists()
    }

    // No program queued it, so there is no slot to name.
    @Test
    fun `names no slot for a workout no program queued`() {
        show(seededFiles())

        text("Próximo · 1 exercício, 3 séries").assertExists()
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
        val card = top(text("Próximo · 1 exercício, 3 séries"))

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

        assertEquals(listOf(Route(Destination.Import)), opened)
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

        assertEquals(listOf(Route(Destination.Import)), opened)
    }

    // A long workout: the card lists four exercises and counts the rest, which the preview spells out.

    private fun longFiles(
        units: UnitSystem? = null,
        overrides: List<ProgramOverride>? = null,
    ): Files {
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
        val exercises =
            names.map {
                val kg = if (it == "Squat") 60.0 else null
                Exercise(it.lowercase(), it, ExerciseConfig.Reps(3, 10, targetWeightKg = kg, restSec = 60.0))
            }
        val long = Workout("long", "Long day", exercises.map { WorkoutBlock.ExerciseBlock(it.id) })
        val programs =
            overrides?.let { listOf(Program("p", "P", listOf(ProgramWeek.Training(1, "A", "long", overrides = it)))) }
        return Files(Library(1, exercises, listOf(long), programs.orEmpty()), units = units)
    }

    @Test
    fun `a long workout lists its first four exercises and counts the rest`() {
        show(longFiles())

        text("Squat").assertExists()
        text("Press").assertExists()
        text("Curl").assertDoesNotExist()
        text("Crunch").assertDoesNotExist()
        text("+8 exercícios a mais").assertExists()
    }

    // The estimate counted a reps set as its rest alone, so 12 × 3 sets read as 36 minutes (#412).
    @Test
    fun `a long workout is counted in exercises and sets, with no time estimate`() {
        show(longFiles())

        text("Próximo · 12 exercícios, 36 séries").assertExists()
        compose.onNodeWithText(" min", substring = true).assertDoesNotExist()
        text("Iniciar treino").assertExists()
    }

    // Weights are stored in kilograms, and shown in the reader's units; the rest of the list has none.
    @Test
    fun `shows an exercise's load in the reader's units`() {
        show(longFiles())

        text("60 kg").assertExists()
    }

    @Test
    fun `shows an exercise's load in pounds to a reader who picked them`() {
        show(longFiles(UnitSystem.IMPERIAL))

        text("132.3 lb").assertExists()
        text("60 kg").assertDoesNotExist()
    }

    // A program week's override is the load the session runs, so it is the one the card shows.
    @Test
    fun `shows the load a program week sets, not the library's`() {
        val heavier = mapOf("target_weight" to OverrideValue.Numeric(80.0))
        show(longFiles(overrides = listOf(ProgramOverride.ExerciseOverride("squat", heavier))))

        text("80 kg").assertExists()
        text("60 kg").assertDoesNotExist()
    }

    // The program the card's slot is from, and its week (#412).

    @Test
    fun `names the program and its week, and opens the program`() {
        show(Files(library(programs = listOf(program(ProgramWeek.Training(1, "Day 1", "push-day"))))))

        text("Base").assertExists()
        button("Semana 1 de 1").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(listOf(Route(Destination.ProgramDetail, mapOf(PROGRAM_ID_PARAM to "base"))), opened)
    }

    @Test
    fun `shows no program or strip for a workout no program queued`() {
        show(seededFiles())

        compose.onNodeWithText("Semana", substring = true).assertDoesNotExist()
        compose.onNode(hasContentDescription("Esta semana do programa, em ordem")).assertDoesNotExist()
    }

    // Position, not completion (#408): what is behind the card's slot, the slot itself, and the rest.
    @Test
    fun `strips the week by where each slot stands against the card's`() {
        show(restDayProgramFiles())

        compose.onNode(hasContentDescription("Esta semana do programa, em ordem")).assertExists()
        compose.onNode(hasContentDescription("Day 1, anterior")).assertExists()
        compose.onNode(hasContentDescription("Day 2, descanso, hoje")).assertExists()
        compose.onNode(hasContentDescription("Day 3")).assertExists()
    }

    @Test
    fun `strips a queued slot as next, numbered where a day has no name`() {
        val weeks = arrayOf(ProgramWeek.Training(1, null, "push-day"), ProgramWeek.RestDay(1))
        show(Files(library(programs = listOf(program(*weeks)))))

        compose.onNode(hasContentDescription("1, próximo")).assertExists()
        compose.onNode(hasContentDescription("2, descanso")).assertExists()
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

        // Unmerged: the note is one of the texts inside the card's preview button.
        assertEquals(2, maxLines(compose.onNodeWithText(longNote, useUnmergedTree = true)))
    }

    /** The card's name: "Off the plan" leaves the card's workout out, so it is the only copy. */
    @Test
    fun `caps the workout name, which is also the user's to make as long as they like`() {
        show(
            Files(
                library(programs = listOf(program(ProgramWeek.Training(1, workoutId = "push-day", notes = longNote)))),
            ),
        )

        assertEquals(2, maxLines(compose.onNodeWithText("Push day", useUnmergedTree = true)))
    }

    @Test
    fun `caps a rest day's note the same way`() {
        val weeks = arrayOf(ProgramWeek.Training(1, "Day 1", "push-day"), ProgramWeek.RestDay(1, "Day 2", longNote))
        show(Files(library(programs = listOf(program(*weeks))), sessions = listOf(programSession("Day 1"))))

        // Unmerged: the note is one of the texts inside the card's preview button.
        assertEquals(2, maxLines(compose.onNodeWithText(longNote, useUnmergedTree = true)))
    }

    // "Off the plan" (#412): what was run lately, never the card's own, and every workout a link away.

    @Test
    fun `off the plan lists the two run most recently, leaving out the card's, and links to every workout`() {
        val workouts = listOf(pushDay, zercher, bench, never)
        val program = program(ProgramWeek.Training(1, "Day 1", "push-day"))
        val sessions = listOf(session("bench", "2026-07-28T09:00:00Z"), session("zercher", "2026-07-20T09:00:00Z"))
        show(Files(library(workouts = workouts, programs = listOf(program)), sessions = sessions))

        assertTrue(top(row("Bench day")) < top(row("Zercher day")))
        row("Amrap day").assertDoesNotExist()
        button("Todos os treinos").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf(Route.of(Section.WORKOUTS)), opened)
    }

    @Test
    fun `today is translated, its date over its title`() {
        show(seededFiles())

        text("Hoje").assert(isHeading())
        text("Fora do plano").assert(isHeading())
        text("Sessão vazia").assertExists()
        // Wednesday, 29 July in Portuguese, in the locale's own order.
        compose.onNodeWithText("quarta-feira", substring = true).assertExists()
    }

    // The empty session, outside the card's conditional so every branch carries it.

    @Test
    fun `offers the empty session alongside a queued workout`() {
        show(seededFiles())

        emptySession().assertExists()
    }

    @Test
    fun `offers the empty session with no workouts to run at all`() {
        show(Files(library(workouts = emptyList())))

        text("Nenhum treino ainda").assertExists()
        emptySession().assertExists()
    }

    @Test
    fun `starts the empty session with the ad-hoc flag rather than a workout id`() {
        show(seededFiles())

        emptySession().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()

        assertEquals(listOf(RunnerStart.Adhoc), started)
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
        text("Semana 1 · Day 2").assertExists()
        text("Iniciar treino").assertDoesNotExist()
        compose.onNodeWithText("Próximo", substring = true).assertDoesNotExist()
    }

    // What comes next, never when: a run of rest slots means it isn't necessarily tomorrow.
    @Test
    fun `a rest day names the session train anyway starts`() {
        show(restDayProgramFiles())

        text("Próxima sessão: Semana 1 · Day 3 · Pull day.").assertExists()
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

        assertEquals(listOf(RunnerStart.Planned(SessionParams.ProgramSlot("base", 1, "Day 3"))), started)
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

    /**
     * The app returning reads the files again, and that has to move the card on even when nothing in
     * them changed: the day turning is what clears a rest day, and a read of equal files changes no
     * state the card is keyed on.
     */
    @Test
    fun `a rest day clears on a read of unchanged files once its day has gone`() {
        val latest = show(restDayProgramFiles())
        text("Dia de descanso").assertExists()

        clock.at = Now + 2.days
        latest.reread()
        compose.waitForIdle()

        text("Dia de descanso").assertDoesNotExist()
        text("Próximo · Day 3").assertExists()
    }

    /** Off the plan reads the card's workout, so it has to follow the card across the day turning. */
    @Test
    fun `off the plan drops the card's workout once a turned day queues it`() {
        val latest = show(restDayProgramFiles())
        compose.onAllNodesWithText("Pull day").assertCountEquals(1)

        clock.at = Now + 2.days
        latest.reread()
        compose.waitForIdle()

        text("Próximo · Day 3").assertExists()
        compose.onAllNodesWithText("Pull day").assertCountEquals(1)
    }

    @Test
    fun `a rest day leaves the empty session available, so it is never a dead end`() {
        show(restDayProgramFiles())

        emptySession().assertExists()
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

    // The card's preview (#280): everything above Start opens it, and Start stays its own target.

    private fun queuedFiles() = Files(library(programs = listOf(program(ProgramWeek.Training(1, "Day 1", "push-day")))))

    @Test
    fun `tapping the card opens the workout preview on that slot, and starts nothing`() {
        show(queuedFiles())

        compose.onNode(opensPreview).performClick()

        val slot = RunnerStart.Planned(SessionParams.ProgramSlot("base", 1, "Day 1"))
        assertEquals(listOf(Route(Destination.WorkoutPreview, slot.routeParams())), opened)
        assertEquals(emptyList<RunnerStart>(), started)
    }

    @Test
    fun `Start is not the preview button, and opens no route`() {
        show(queuedFiles())

        button("Iniciar treino").performClick()

        assertEquals(listOf(RunnerStart.Planned(SessionParams.ProgramSlot("base", 1, "Day 1"))), started)
        assertEquals(emptyList<Route>(), opened)
        compose.onAllNodes(opensPreview and hasText("Iniciar treino")).assertCountEquals(0)
    }

    @Test
    fun `a rest day's card has no preview button`() {
        show(restDayFiles())

        text("Dia de descanso").assertExists()
        compose.onAllNodes(opensPreview).assertCountEquals(0)
    }

    /** One node, named by its words rather than a label that would drift from them. */
    @Test
    fun `the preview button's name carries the workout's`() {
        show(queuedFiles())

        compose.onNode(opensPreview).assert(hasText("Push day", substring = true))
    }

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private class Files(
        var library: Library?,
        private val sessions: List<Session> = emptyList(),
        private val units: UnitSystem? = null,
    ) : FakeStoredFiles() {
        override fun sessionLog() = SessionLog(sessions, emptyList())

        override fun library() = library

        override fun preferences() = units?.let { Preferences(it, ThemePreference.SYSTEM, false, true, null) }
    }

    private fun library(
        workouts: List<Workout> = listOf(pushDay),
        programs: List<Program> = emptyList(),
    ) = Library(1, listOf(pushups), workouts, programs)

    /** The seeded shape a first run has: something to run, nothing logged yet. */
    private fun seededFiles(sessions: List<Session> = emptyList()) = Files(library(), sessions)

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

    /** [Now], until a test moves it on. */
    private val clock =
        object : Clock {
            var at = Now

            override fun now() = at
        }
}

private val Now = Instant.parse("2026-07-29T18:00:00Z")
private val pushups = Exercise("pushups", "Push-ups", ExerciseConfig.Reps(3, 10, restSec = 60.0))
private val oneSet = SessionEntry.Reps("pushups", listOf(RepsSetLog(10, restTakenSec = 60.0)))
private val pushDay = Workout("push-day", "Push day", listOf(WorkoutBlock.ExerciseBlock("pushups")))
private val zercher = Workout("zercher", "Zercher day", emptyList())
private val bench = Workout("bench", "Bench day", emptyList())
private val never = Workout("never", "Amrap day", emptyList())
