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
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.RepsSetLog
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.Workout
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.StoredFiles
import com.casco.kettle.shell.nav.Tab
import com.casco.kettle.shell.rn.Stacking
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

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

    private val opened = mutableListOf<Triple<Stacking, String, Map<String, String>>>()
    private val exported = mutableListOf<String?>()
    private var sessionsChanged = 0
    private val reselects = MutableSharedFlow<Tab>(extraBufferCapacity = 1)

    private fun show(files: Files = Files(push, legs)) {
        val latest =
            LatestFiles(files, CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).apply { reread() }
        compose.setContent {
            KettleTheme(darkTheme = false) {
                HistoryScreen(
                    latest,
                    TimeZone.UTC,
                    reselects,
                    HistoryActions(
                        open = { how, path, params -> opened += Triple(how, path, params) },
                        export = { exported += it },
                        sessionsChanged = { sessionsChanged += 1 },
                    ),
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
        // RN's stores re-list the log once native has written it (#187).
        assertEquals(1, sessionsChanged)
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
        assertEquals(0, sessionsChanged)
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
        assertEquals(0, sessionsChanged)
        compose.onNodeWithText("Excluir sessão?").assertDoesNotExist()
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
    fun `stays quiet when there is no history at all`() {
        show(Files())

        compose.onNodeWithText("Nada encontrado").assertDoesNotExist()
    }

    @Test
    fun `opens the editor for the session whose row is open`() {
        show()

        row("Leg day").performClick()
        action("Editar").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(listOf(Triple(Stacking.Push, "/session-editor", mapOf("id" to "legs-1"))), opened)
    }

    // The runner owns a running session's file, so an edit from here would be lost at the next set.
    @Test
    fun `offers no Edit on a session that is still running`() {
        show(Files(push.copy(endedAt = null)))

        row("Push day").performClick()

        action("Excluir").assertExists()
        action("Editar").assertDoesNotExist()
    }

    /** RN keeps export until M5; native asks it to share, one session or the whole log. */
    @Test
    fun `exports the open session, or the whole log from the header`() {
        show()

        row("Leg day").performClick()
        action("Exportar").assertHeightIsAtLeast(48.dp).performClick()
        action("Exportar tudo").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(listOf("legs-1", null), exported)
    }

    @Test
    fun `states the all-time totals in one line`() {
        show()

        // 1h, and the leg session's 2 days and 1h: 50h in all, in Stats' "0h 0min" form.
        compose.onNodeWithText("2 sessões · 50h 0min · 2 séries").assertExists()
    }

    // Six cards' worth of chrome is what the line replaced; a stray tile label means it grew back.
    @Test
    fun `draws no stat cards at all`() {
        show()

        compose.onNodeWithText("Esta semana").assertDoesNotExist()
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

    @Test
    fun `opens the stats screen`() {
        show()

        action("Números").performClick()

        assertEquals(listOf(Triple(Stacking.Push, "/analytics", emptyMap<String, String>())), opened)
    }

    @Test
    fun `offers the stats screen on an empty log, unlike export`() {
        show(Files())

        action("Números").assertHeightIsAtLeast(48.dp)
        action("Exportar tudo").assertDoesNotExist()
    }

    // A zero set is how the runner skips; a session of nothing but skips was no session at all.
    @Test
    fun `leaves out a session that logged no work`() {
        val skipped = SessionEntry.Reps("dips", listOf(RepsSetLog(reps = 0, restTakenSec = 60.0)))
        show(Files(push, legs.copy(entries = listOf(skipped))))

        row("Leg day").assertDoesNotExist()
        compose.onNodeWithText("1 sessão · 1h 0min · 1 série").assertExists()
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
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(many.size)
        compose.onNodeWithText("Histórico").assertDoesNotExist()

        reselects.tryEmit(Tab.Library)
        compose.waitForIdle()
        compose.onNodeWithText("Histórico").assertDoesNotExist()

        reselects.tryEmit(Tab.History)
        compose.waitForIdle()
        compose.onNodeWithText("Histórico").assertIsDisplayed()
    }

    @Test
    fun `is translated`() {
        show(Files(push, legs, push.copy(id = "ad-hoc", workout = null)))

        compose.onNodeWithText("Histórico").assert(isHeading())
        compose.onNodeWithText("3 sessões · ", substring = true).assertExists()
        action("Números").assertExists()
        action("Exportar tudo").assertExists()
        row("Sessão avulsa").assertExists()
        // The field is named by its placeholder, read inside the field's own node.
        compose.onNode(hasSetTextAction() and hasText("Buscar treinos")).assertExists()
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
    ) : StoredFiles {
        private val sessions = sessions.toMutableList()
        val deleted = mutableListOf<String>()

        /** Thrown by the next delete, as a full disk or a locked file would. */
        var failure: IOException? = null

        override fun sessionLog() = SessionLog(sessions.toList(), errors)

        override fun library() = Library(1, listOf(pullUps, dips), listOf(pushDay, legDay), emptyList())

        override fun preferences(): Preferences? = null

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
