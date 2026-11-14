package com.casco.kettle.shell.programs

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Program
import com.casco.kettle.domain.ProgramWeek
import com.casco.kettle.domain.RepsSetLog
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.Workout
import com.casco.kettle.shell.files.FakeStoredFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.nav.Tab
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Programs (#232), case for case from RN's `programs.test.tsx`, on the harness recipe (#193): in `pt`,
 * since an English assertion passes a hardcoded English string, with the library faked behind
 * [StoredFiles] and every route it opens heard through `open`. Beyond jest's: what each control opens,
 * the targets' size, re-tap-to-top, and nothing said before the library is read.
 *
 * The weeks-with-notes count is `:shared`'s (`ProgramWeeksTest`); this covers its words.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class ProgramsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<Route>()
    private val reselects = MutableSharedFlow<Tab>(extraBufferCapacity = 1)

    private fun show(files: Files = Files(base, peak)) {
        val latest =
            LatestFiles(files, CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).apply { reread() }
        compose.setContent {
            KettleTheme(darkTheme = false) {
                ProgramsScreen(latest, reselects, open = { opened += it }, clock = clock)
            }
        }
    }

    private fun row(name: String) = compose.onNode(hasText(name, substring = true) and isButton)

    private fun search(query: String) = compose.onNode(hasSetTextAction()).performTextInput(query)

    @Test
    fun `lists every program`() {
        show()

        row("Base building").assertExists()
        row("Peak week").assertExists()
    }

    @Test
    fun `narrows the list to matching names`() {
        show()

        search("peak")

        row("Peak week").assertExists()
        row("Base building").assertDoesNotExist()
    }

    @Test
    fun `says nothing matched rather than offering to teach the YAML format`() {
        show()

        search("zzz")

        compose.onNodeWithText("Nada encontrado").assertExists()
        compose.onNodeWithText("Nenhum programa ainda").assertDoesNotExist()
    }

    @Test
    fun `still tells a library with no programs that it has none yet`() {
        show(Files())

        compose.onNodeWithText("Nenhum programa ainda").assertExists()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    /** Null is a library not read yet, or not yet seeded on a first launch: neither has "no programs". */
    @Test
    fun `says nothing before there is a library`() {
        show(Files(library = null))

        compose.onNodeWithText("Nenhum programa ainda").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test
    fun `is translated`() {
        show()

        // The field is named by its placeholder, read inside the field's own node.
        compose.onNode(hasSetTextAction() and hasText("Buscar 2 programas")).assertExists()
        compose.onNodeWithText("Ativo, semana 1 · 1 sessão por semana").assertExists()
        compose.onNodeWithText("Sem semanas").assertExists()
        compose.onNode(hasText("Como escrever um programa à mão") and isButton).assertExists()
        search("zzz")
        compose.onNodeWithText("Nada encontrado").assertExists()
    }

    @Test
    fun `counts weeks by number and sessions by the days each one trains`() {
        val everyWeekTwice =
            Program(
                "block",
                "Four week block",
                (1L..4L).flatMap { week ->
                    listOf(
                        ProgramWeek.Training(week, "Day 1", "push-day", notes = "Push a little harder."),
                        ProgramWeek.Training(week, "Day 3", "push-day", notes = "And again."),
                    )
                },
            )
        show(Files(base, everyWeekTwice))

        compose.onNodeWithText("4 semanas · 2 sessões por semana").assertExists()
    }

    /**
     * The program Today runs, which is the last one logged rather than the first in the file, says so and
     * where it stands; the others say how long they run.
     */
    @Test
    fun `the program Today runs reads Active and its week, the others their length`() {
        val twoWeeks =
            Program(
                "two",
                "Two weeks",
                listOf(ProgramWeek.Training(1, "Day 1", "push-day"), ProgramWeek.Training(2, "Day 1", "push-day")),
            )
        show(Files(base, twoWeeks, sessions = listOf(session("two", week = 1, day = "Day 1"))))

        compose.onNodeWithText("Ativo, semana 2 · 1 sessão por semana").assertExists()
        compose.onNodeWithText("1 semana · 1 sessão por semana").assertExists()
    }

    @Test
    fun `a row opens its program's detail`() {
        show()

        row("Peak week").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(listOf(Route(Destination.ProgramDetail, mapOf("programId" to "peak"))), opened)
    }

    @Test
    fun `the guide link under the list opens the program guide`() {
        show()

        compose
            .onNode(
                hasText("Como escrever um programa à mão") and isButton,
            ).assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals(listOf(Route(Destination.ProgramGuide)), opened)
    }

    @Test
    fun `the empty state's link opens the program guide`() {
        show(Files())

        compose.onNode(hasText("Escrever um em YAML →") and isButton).assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(listOf(Route(Destination.ProgramGuide)), opened)
    }

    /** #153 dropped re-tap-to-top with RN's bar; each tab's port brings it back for its own list. */
    @Test
    fun `a tap on the Library tab while Programs shows scrolls back to the top`() {
        val many = (1..30).map { Program("p$it", "Program $it", emptyList()) }
        show(Files(*many.toTypedArray()))
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(many.size)
        compose.onNode(hasSetTextAction()).assertDoesNotExist()

        reselects.tryEmit(Tab.Progress)
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()

        reselects.tryEmit(Tab.Library)
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
    }

    private val clock =
        object : Clock {
            override fun now() = Now
        }

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private class Files(
        vararg programs: Program,
        private val library: Library? = Library(1, emptyList(), listOf(pushDay), programs.toList()),
        private val sessions: List<Session> = emptyList(),
    ) : FakeStoredFiles() {
        override fun sessionLog() = SessionLog(sessions, emptyList())

        override fun library() = library
    }
}

private val pushDay = Workout("push-day", "Push day", emptyList())
private val base = Program("base", "Base building", listOf(ProgramWeek.Training(1, workoutId = "push-day")))
private val peak = Program("peak", "Peak week", emptyList())

private val Now = Instant.parse("2026-07-29T18:00:00Z")

/** A session of [program]'s [week] and [day], logged at [Now]. */
private fun session(
    program: String,
    week: Long,
    day: String,
) = Session(
    1,
    "s",
    "push-day",
    program,
    week,
    day,
    Now.toString(),
    Now.toString(),
    listOf(SessionEntry.Reps("pushups", listOf(RepsSetLog(10, restTakenSec = 60.0)))),
)
