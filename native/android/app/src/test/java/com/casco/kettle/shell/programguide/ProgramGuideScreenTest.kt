package com.casco.kettle.shell.programguide

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.rn.Stacking
import com.casco.kettle.shell.theme.KettleTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The program guide (#199), case for case from `program-guide.test.tsx`, on the harness recipe (#193):
 * the class runs in `pt`, since an English assertion passes a hardcoded English string, and the
 * screen's two ways out are parameters, so the test hears which one it took.
 *
 * The import hand-off matters most: the guide used to end by describing the route to Import to
 * someone with a program just written and nowhere to put it. The prose cases pin what a wording change
 * can't say it broke: the code spans, and the samples that must not follow the UI language.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class ProgramGuideScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<Pair<Stacking, String>>()
    private var dones = 0

    private fun show() =
        compose.setContent {
            KettleTheme(darkTheme = false) {
                ProgramGuideScreen(open = { how, path -> opened += how to path }, onDone = { dones++ })
            }
        }

    /** A `<c>` left as text: the bundle's markup reaching the reader instead of being parsed. */
    private fun assertNoTagShows() = compose.onAllNodesWithText("<c>", substring = true).assertCountEquals(0)

    private val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test
    fun `hands off to import rather than describing where it is`() {
        show()

        compose
            .onNode(hasClickAction() and hasText("Abrir importação"))
            .performScrollTo()
            .assert(button)
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        // Replace, not push: why is at the call site.
        assertEquals(listOf(Stacking.Replace to "/import"), opened)
        assertEquals(0, dones)
    }

    @Test
    fun `still closes`() {
        show()

        compose
            .onNode(hasClickAction() and hasText("Concluir"))
            .performScrollTo()
            .assert(button)
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals(1, dones)
        assertEquals(emptyList<Pair<Stacking, String>>(), opened)
    }

    @Test
    fun `translates its controls`() {
        show()

        compose.onNodeWithText("Escrevendo um programa à mão").assertExists()
        compose.onNodeWithText("Abrir importação").assertExists()
        compose.onNodeWithText("Concluir").assertExists()
    }

    /** `<c>` read as markup, not shown as text: the paragraph reads whole, with its tokens in code. */
    @Test
    fun `the prose renders a code span as part of its sentence, not as markup`() {
        show()

        val sentence =
            compose
                .onNodeWithText(
                    "Uma entrada de semana com rest_day: true e sem workout é uma folga programada",
                    substring = true,
                ).fetchSemanticsNode()
        val text = checkNotNull(sentence.config.getOrNull(SemanticsProperties.Text)).single()
        val code = text.spanStyles.filter { it.item.fontFamily == FontFamily.Monospace }
        assertEquals(
            listOf("rest_day: true", "workout", "notes", "overrides"),
            code.map { text.substring(it.start, it.end) },
        )
        assertNoTagShows()
    }

    @Test
    fun `the prose is translated, sample YAML aside`() {
        show()

        compose.onNodeWithText("3. Campos").assertExists()
        compose.onNodeWithText("Um programa aponta para treinos pelo id", substring = true).assertExists()
        assertNoTagShows()

        // The sample is the file the reader is about to write, so it stays as the importer needs it:
        // English notes and all. The key shows twice, once in the Portuguese sentence and once in the
        // sample, since a YAML key reads the same in every locale.
        compose.onAllNodesWithText("rest_day: true", substring = true).assertCountEquals(2)
        compose.onNodeWithText("notes: Walk, stretch, nothing heavy.", substring = true).assertExists()
    }
}
