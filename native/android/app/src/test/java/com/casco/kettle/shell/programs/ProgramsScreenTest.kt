package com.casco.kettle.shell.programs

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.Program
import com.casco.kettle.domain.ProgramWeek
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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

    private val opened = mutableListOf<Triple<Stacking, String, Map<String, String>>>()
    private val reselects = MutableSharedFlow<Tab>(extraBufferCapacity = 1)

    private fun show(files: Files = Files(base, peak)) {
        val latest =
            LatestFiles(files, CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).apply { reread() }
        compose.setContent {
            KettleTheme(darkTheme = false) {
                ProgramsScreen(latest, reselects) { how, path, params -> opened += Triple(how, path, params) }
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
    fun `says nothing below its header before there is a library`() {
        show(Files(library = null))

        compose.onNodeWithText("Programas").assertExists()
        compose.onNodeWithText("Nenhum programa ainda").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test
    fun `is translated`() {
        show()

        compose.onNodeWithText("Programas").assert(isHeading())
        // The field is named by its placeholder, read inside the field's own node.
        compose.onNode(hasSetTextAction() and hasText("Buscar 2 programas")).assertExists()
        compose.onNodeWithText("Semana 1").assertExists()
        compose.onNodeWithText("Sem semanas").assertExists()
        control("Como escrever um programa").assertExists()
        control("Novo programa").assertExists()
        search("zzz")
        compose.onNodeWithText("Nada encontrado").assertExists()
    }

    @Test
    fun `counts distinct weeks with notes or overrides, not the day entries inside them`() {
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
        show(Files(everyWeekTwice))

        compose.onNodeWithText("Semanas 1–4").assertExists()
        compose.onNodeWithText("4 semanas com notas ou substituições").assertExists()
    }

    @Test
    fun `a row opens its program's detail`() {
        show()

        row("Peak week").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(listOf(Triple(Stacking.Push, "/program-detail", mapOf("programId" to "peak"))), opened)
    }

    @Test
    fun `the help link opens the program guide`() {
        show()

        control("Como escrever um programa").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()

        assertEquals(listOf(Triple(Stacking.Push, "/program-guide", emptyMap<String, String>())), opened)
    }

    @Test
    fun `the empty state's link opens the program guide`() {
        show(Files())

        compose.onNode(hasText("Escrever um em YAML →") and isButton).assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(listOf(Triple(Stacking.Push, "/program-guide", emptyMap<String, String>())), opened)
    }

    @Test
    fun `New program opens the editor`() {
        show()

        control("Novo programa").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()

        assertEquals(listOf(Triple(Stacking.Push, "/program-editor", emptyMap<String, String>())), opened)
    }

    /** #153 dropped re-tap-to-top with RN's bar; each tab's port brings it back for its own list. */
    @Test
    fun `a tap on the Programs tab while it shows scrolls back to the top`() {
        val many = (1..30).map { Program("p$it", "Program $it", emptyList()) }
        show(Files(*many.toTypedArray()))
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(many.size)
        compose.onNodeWithText("Programas").assertDoesNotExist()

        reselects.tryEmit(Tab.History)
        compose.waitForIdle()
        compose.onNodeWithText("Programas").assertDoesNotExist()

        reselects.tryEmit(Tab.Programs)
        compose.waitForIdle()
        compose.onNodeWithText("Programas").assertIsDisplayed()
    }

    /** An icon-only control, named by its label since it has no words of its own. */
    private fun control(label: String) = compose.onNode(hasContentDescription(label) and isButton)

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private class Files(
        vararg programs: Program,
        private val library: Library? = Library(1, emptyList(), listOf(pushDay), programs.toList()),
    ) : StoredFiles {
        override fun sessionLog() = SessionLog(emptyList(), emptyList())

        override fun library() = library

        override fun preferences(): Preferences? = null

        override fun deleteSession(id: String) = error("Programs deletes nothing")
    }
}

private val pushDay = Workout("push-day", "Push day", emptyList())
private val base = Program("base", "Base building", listOf(ProgramWeek.Training(1, workoutId = "push-day")))
private val peak = Program("peak", "Peak week", emptyList())
