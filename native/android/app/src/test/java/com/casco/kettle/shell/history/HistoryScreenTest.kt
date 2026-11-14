package com.casco.kettle.shell.history

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.RepsSetLog
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.Workout
import com.casco.kettle.shell.files.FakeStoredFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.i18n.formatDayRange
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.nav.Tab
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.Locale
import kotlin.time.Instant

/**
 * History (#202), case for case from RN's `history.test.tsx`, on the harness recipe (#193): in `pt`,
 * since an English assertion passes a hardcoded English string, with the files faked behind
 * [StoredFiles] and every message to the shell heard through [HistoryActions]. Beyond jest's: the
 * delete's cancel and failure, the unreadable-file notice, and re-tap-to-top.
 *
 * The row's numbers are `:shared`'s (`HistoryViewsTest`, `HistoryStatsTest`); the words are
 * [entryResultText]'s (`EntryResultsTest`). These cover what the screen does with them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class HistoryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<Route>()
    private val exported = mutableListOf<String>()
    private val reselects = MutableSharedFlow<Tab>(extraBufferCapacity = 1)

    private fun show(
        files: Files = Files(push, legs),
        runningId: String? = null,
    ) {
        val latest =
            LatestFiles(files, CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).apply { reread() }
        compose.setContent {
            KettleTheme(darkTheme = false) {
                HistoryScreen(
                    latest,
                    HistoryTime(Now, TimeZone.UTC, DayOfWeek.MONDAY),
                    reselects,
                    HistoryActions(
                        open = { opened += it },
                        export = { exported += it },
                    ),
                    runningId,
                )
            }
        }
    }

    /**
     * jest's `pressAlertButton`: a button inside the dialog, never a same-named control on the screen
     * under it, which `onNodeWithText` alone would find too and fail on as ambiguous.
     */
    private fun ComposeContentTestRule.pressDialogButton(label: String) =
        onNode(hasText(label) and hasAnyAncestor(isDialog())).performClick()

    private fun row(name: String) = compose.onNode(hasText(name) and isButton)

    private fun search(query: String) = compose.onNode(hasSetTextAction()).performTextInput(query)

    @Test
    fun `lists every logged session`() {
        show()

        row("Push day").assertExists()
        row("Leg day").assertExists()
    }

    @Test
    fun `shows a session's exercises only once its row is expanded`() {
        show()
        compose.onNodeWithText("Pull-ups").assertDoesNotExist()

        row("Push day").performClick()

        compose.onNodeWithText("Pull-ups").assertExists()
        compose.onNodeWithText("8 repetições").assertExists()
    }

    @Test
    fun `collapses a row that was already open`() {
        show()

        row("Push day").performClick()
        row("Push day").performClick()

        compose.onNodeWithText("Pull-ups").assertDoesNotExist()
    }

    @Test
    fun `opens one row at a time`() {
        show()

        row("Push day").performClick()
        row("Leg day").performClick()

        compose.onNodeWithText("Pull-ups").assertDoesNotExist()
        compose.onNodeWithText("Dips").assertExists()
    }

    /**
     * A screen reader can't read the chevron, so the row says whether it's open by its actions, and its
     * click label says what a double-tap does, which its own words (what it is) don't.
     */
    @Test
    fun `a row is a 48dp button that expands, then collapses`() {
        show()

        row("Push day")
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
            .assert(clickLabel("Mostrar exercícios"))
            .performClick()

        row("Push day")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
            .assert(clickLabel("Ocultar exercícios"))
    }

    // The label History's jest suite pinned through the selector: "1 sets" shipped once.
    @Test
    fun `says a row's time and sets, its count in the plural it takes`() {
        val twoSets =
            legs.copy(
                entries =
                    listOf(
                        SessionEntry.Reps(
                            "dips",
                            listOf(RepsSetLog(reps = 8, restTakenSec = 60.0), RepsSetLog(reps = 6, restTakenSec = 0.0)),
                        ),
                        SessionEntry.Hiit("burpees", roundsCompleted = 3),
                    ),
            )
        show(Files(push, twoSets))

        compose.onNodeWithText("60 min · 1 série").assertExists()
        // A HIIT round is a set too, and two kinds of work make the session mixed.
        compose.onNodeWithText("2940 min · 5 séries · misto").assertExists()
    }

    @Test
    fun `deletes the session whose row was open, not another`() {
        val files = Files(push, legs)
        show(files)

        row("Leg day").performClick()
        action("Excluir").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Excluir sessão?").assertExists()
        compose.onNodeWithText("\"Leg day\" em 27 ", substring = true).assertExists()
        compose.pressDialogButton("Excluir")

        assertEquals(listOf("legs-1"), files.deleted)
        row("Leg day").assertDoesNotExist()
        row("Push day").assertExists()
        compose.onNodeWithText("Excluir sessão?").assertDoesNotExist()
    }

    @Test
    fun `cancelling the delete leaves the session alone`() {
        val files = Files(push, legs)
        show(files)

        row("Leg day").performClick()
        action("Excluir").performClick()
        compose.pressDialogButton("Cancelar")

        assertEquals(emptyList<String>(), files.deleted)
        row("Leg day").assertExists()
        compose.onNodeWithText("Excluir sessão?").assertDoesNotExist()
    }

    /** Degrades rather than crashes: the reason is said in the row, which stays, as does the file. */
    @Test
    fun `a delete that fails says so and keeps the session`() {
        val files = Files(push, legs).apply { failure = IOException("legs-1.yaml") }
        show(files)

        row("Leg day").performClick()
        action("Excluir").performClick()
        compose.pressDialogButton("Excluir")

        compose.onNodeWithText("Não foi possível excluir: legs-1.yaml").assertExists()
        row("Leg day").assertExists()
        compose.onNodeWithText("Excluir sessão?").assertDoesNotExist()
    }

    // A SecurityException often has no message; the row must still say why, not end at the colon.
    @Test
    fun `says a delete was refused even when the disk gives no reason`() {
        val files = Files(push, legs).apply { failure = SecurityException() }
        show(files)

        row("Leg day").performClick()
        action("Excluir").performClick()
        compose.pressDialogButton("Excluir")

        compose.onNodeWithText("Não foi possível excluir: SecurityException").assertExists()
        row("Leg day").assertExists()
    }

    @Test
    fun `narrows both the list and the summary to what matched`() {
        show()

        search("leg")

        row("Push day").assertDoesNotExist()
        row("Leg day").assertExists()
        compose.onNodeWithText("1 de 2 · ", substring = true).assertExists()
    }

    // An ad-hoc session is listed under the stand-in, so searching for the stand-in has to find it.
    @Test
    fun `finds an ad-hoc session by the name it is listed under`() {
        show(Files(push, legs.copy(workout = null)))

        search("avulsa")

        row("Sessão avulsa").assertExists()
        row("Push day").assertDoesNotExist()
    }

    @Test
    fun `says nothing matched instead of going blank`() {
        show()

        search("zzz")

        compose.onNodeWithText("Nada encontrado").assertExists()
        compose.onNodeWithText("Nenhum resultado para \"zzz\"", substring = true).assertExists()
        compose.onNodeWithText("0 de 2 · ", substring = true).assertExists()
    }

    @Test
    fun `says what will fill an empty log, with no search over nothing`() {
        show(Files())

        compose.onNodeWithText("Nada registrado ainda", substring = true).assertExists()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("Nada encontrado").assertDoesNotExist()
    }

    @Test
    fun `opens the editor for the session whose row is open`() {
        show()

        row("Leg day").performClick()
        action("Editar").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(listOf(Route(Destination.SessionEditor, mapOf("id" to "legs-1"))), opened)
    }

    // The runner owns a running session's file, so an edit from here would be lost at the next set.
    @Test
    fun `offers no Edit on a session that is still running`() {
        show(Files(push.copy(endedAt = null)))

        row("Push day").performClick()

        action("Excluir").assertExists()
        action("Editar").assertDoesNotExist()
    }

    // One writer while a session runs (#295): the service writes that file, and a delete from here would
    // come back at the next set logged. An unfinished row nothing is writing, as RN left them, keeps it.
    @Test
    fun `offers no Delete on the session the runner is writing`() {
        show(Files(push.copy(endedAt = null), legs.copy(endedAt = null)), runningId = push.id)

        row("Push day").performClick()
        action("Excluir").assertDoesNotExist()
        action("Exportar").assertExists()

        row("Leg day").performClick()
        action("Excluir").assertExists()
    }

    /** Export shares one session (`FileShare`, #238); the whole log is Your file's since #414. */
    @Test
    fun `exports the open session, and offers no Export all`() {
        show()

        row("Leg day").performClick()
        action("Exportar").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(listOf("legs-1"), exported)
        action("Exportar tudo").assertDoesNotExist()
    }

    /** #414: weeks, each titled and totalled over what it lists, where one all-time line used to be. */
    @Test
    fun `groups sessions by week, titled and totalled, with no all-time line`() {
        show(Files(push, legs, older))

        compose.onNodeWithText("Esta semana").assert(isHeading())
        // 1h, and the leg session's 2 days and 1h: 50h in this week, in Stats' "0h 0min" form.
        compose.onNodeWithText("2 sessões · 50h 0min").assertExists()
        compose
            .onNodeWithText(
                formatDayRange(LocalDate(2026, 7, 13), LocalDate(2026, 7, 19), Locale.forLanguageTag("pt")),
            ).assert(isHeading())
        compose.onNodeWithText("· 2 séries", substring = true).assertDoesNotExist()
    }

    /** #414: an exercise's name finds the sessions it was in, as a workout's does. */
    @Test
    fun `finds a session by an exercise in it`() {
        show()

        search("dips")

        row("Leg day").assertExists()
        row("Push day").assertDoesNotExist()
    }

    @Test
    fun `finds a session by words split between its workout and an exercise`() {
        show()

        search("leg dips")

        row("Leg day").assertExists()
    }

    /** A week outside this year carries its year, or a log over a year long titles two weeks alike. */
    @Test
    fun `gives a week from another year its year`() {
        show(Files(push, older.copy(id = "last-year", startedAt = "2025-07-16T09:00:00.000Z")))

        compose.onNodeWithText("2025", substring = true).assert(isHeading())
        compose.onNodeWithText("Esta semana").assertExists()
    }

    /**
     * #414: a PR badge for a record at the time, and the entry that set it under the numbers rather than
     * the first logged. Dips at 10 beat the 8 before them; pull-ups at 8 only tie, so the line skips them.
     * The first dips session is no record, being the first.
     */
    @Test
    fun `badges the session that set a record, and shows the entry that did`() {
        val better =
            push.copy(
                id = "push-2",
                startedAt = "2026-07-29T12:00:00.000Z",
                entries =
                    listOf(
                        SessionEntry.Reps("pull-ups", listOf(RepsSetLog(reps = 8, restTakenSec = 60.0))),
                        SessionEntry.Reps("dips", listOf(RepsSetLog(reps = 10, restTakenSec = 60.0))),
                    ),
            )
        show(Files(push, legs, better))

        assertEquals(1, compose.onAllNodes(hasText("Recorde")).fetchSemanticsNodes().size)
        compose.onNodeWithText("Dips · 10 repetições").assertExists()
        // Unbadged rows show their first entry.
        compose.onNodeWithText("Dips · 8 repetições").assertExists()
    }

    // Six cards' worth of chrome is what the old summary line replaced; a stray tile label means it grew back.
    @Test
    fun `draws no stat cards at all`() {
        show()

        compose.onNodeWithText("Todo o período").assertDoesNotExist()
        compose.onNodeWithText("dias seguidos").assertDoesNotExist()
        compose.onNodeWithText("séries").assertDoesNotExist()
    }

    // Singular, not "1 sessões": each count is formatted before it goes into the sentence.
    @Test
    fun `names the filtered subset and totals it, while searching`() {
        show()

        search("leg")

        compose.onNodeWithText("1 de 2 · 1 sessão · 49h 0min · 1 série").assertExists()
    }

    // A zero set is how the runner skips; a session of nothing but skips was no session at all.
    @Test
    fun `leaves out a session that logged no work`() {
        val skipped = SessionEntry.Reps("dips", listOf(RepsSetLog(reps = 0, restTakenSec = 60.0)))
        show(Files(push, legs.copy(entries = listOf(skipped))))

        row("Leg day").assertDoesNotExist()
        compose.onNodeWithText("1 sessão · 1h 0min").assertExists()
    }

    /** RN collected these and History is where a missing session would be noticed, so it says why. */
    @Test
    fun `says which session files couldn't be read`() {
        show(Files(push, errors = listOf("broken.yaml (invalidYaml): bad indent")))

        compose.onNodeWithText("1 arquivo de sessão não pôde ser lido ou gravado").assertExists()
        compose.onNodeWithText("broken.yaml (invalidYaml): bad indent").assertExists()
    }

    /** #153 dropped re-tap-to-top with RN's bar; History's port brings it back for its own list. */
    @Test
    fun `a tap on the History tab while it shows scrolls back to the top`() {
        val many = (1..30).map { day -> push.copy(id = "s$day", startedAt = "2026-06-${"%02d".format(day)}T09:00:00Z") }
        show(Files(*many.toTypedArray()))
        val top = compose.onNode(hasSetTextAction())
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(many.size)
        top.assertDoesNotExist()

        reselects.tryEmit(Tab.Library)
        compose.waitForIdle()
        top.assertDoesNotExist()

        reselects.tryEmit(Tab.Progress)
        compose.waitForIdle()
        top.assertIsDisplayed()
    }

    @Test
    fun `is translated`() {
        show(Files(push, legs, push.copy(id = "ad-hoc", workout = null)))

        compose.onNodeWithText("3 sessões · ", substring = true).assertExists()
        row("Sessão avulsa").assertExists()
        // The field is named by its placeholder, read inside the field's own node.
        compose.onNode(hasSetTextAction() and hasText("Filtrar por treino ou exercício")).assertExists()
        // The workout name is the user's and is never translated, sitting among strings that are.
        row("Push day").assertExists()
    }

    private fun action(label: String) = compose.onNode(hasText(label) and isButton)

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun clickLabel(label: String) =
        SemanticsMatcher("click label $label") { node ->
            node.config.getOrNull(SemanticsActions.OnClick)?.label == label
        }

    private class Files(
        vararg sessions: Session,
        private val errors: List<String> = emptyList(),
    ) : FakeStoredFiles() {
        private val sessions = sessions.toMutableList()
        val deleted = mutableListOf<String>()

        /** Thrown by the next delete, as a full disk, a locked file or a withdrawn permission would. */
        var failure: Exception? = null

        override fun sessionLog() = SessionLog(sessions.toList(), errors)

        override fun library() = Library(1, listOf(pullUps, dips), listOf(pushDay, legDay), emptyList())

        override fun deleteSession(id: String) {
            failure?.let { throw it }
            deleted += id
            sessions.removeAll { it.id == id }
        }
    }
}

private val pullUps = Exercise("pull-ups", "Pull-ups", ExerciseConfig.Reps(sets = 3, targetRepsMin = 8, restSec = 60.0))
private val dips = Exercise("dips", "Dips", ExerciseConfig.Reps(sets = 3, targetRepsMin = 8, restSec = 60.0))
private val pushDay = Workout("push-day", "Push day", emptyList())
private val legDay = Workout("leg-day", "Leg day", emptyList())

private fun session(
    id: String,
    workout: String,
    startedAt: String,
    exercise: String,
) = Session(
    1,
    id,
    workout,
    null,
    null,
    null,
    startedAt,
    "2026-07-29T10:00:00.000Z",
    listOf(SessionEntry.Reps(exercise, listOf(RepsSetLog(reps = 8, restTakenSec = 60.0)))),
)

private val push = session("push-1", "push-day", "2026-07-29T09:00:00.000Z", "pull-ups")
private val legs = session("legs-1", "leg-day", "2026-07-27T09:00:00.000Z", "dips")
private val older = session("old-1", "push-day", "2026-07-15T09:00:00.000Z", "pull-ups")

/** A Thursday, in the week from Monday 27 July that push and legs were logged in. */
private val Now = Instant.parse("2026-07-30T12:00:00Z")
