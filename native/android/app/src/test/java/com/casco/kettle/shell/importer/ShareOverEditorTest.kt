package com.casco.kettle.shell.importer

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.shell.exerciseeditor.ExerciseEditorRoute
import com.casco.kettle.shell.files.FakeStoredFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.Navigator
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.nav.shellRule
import com.casco.kettle.shell.nav.showShell
import com.casco.kettle.shell.nav.standInName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A share or a `kettle://` link arriving while the exercise editor is open (#569): it opens over the
 * editor, and back finds the editor as it was left, draft, units and open confirm included. The real
 * editor route in the real shell, so what's held is its own `remember`ed form; what arrives over it is a
 * stand-in. In `pt`, as the editor's tests are.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class ShareOverEditorTest {
    @get:Rule
    val compose = shellRule()

    private val navigator = Navigator()
    private val opener = SharedOpener(navigator, SharedLibraryInbox())
    private val files = Files()
    private val latest =
        LatestFiles(files, CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).apply { reread() }

    private fun showEditor(id: String? = null) {
        compose.showShell(navigator, latest) { route ->
            if (route.destination ==
                Destination.ExerciseEditor
            ) {
                ExerciseEditorRoute(route.params)
            } else {
                Text(standInName(route))
            }
        }
        go { push(Route(Destination.ExerciseEditor, listOfNotNull(id?.let { "id" to it }).toMap())) }
    }

    private fun typeDraft() = compose.onNode(hasContentDescription(NAME)).performTextReplacement(DRAFT)

    private fun go(move: Navigator.() -> Unit) {
        compose.runOnUiThread { navigator.move() }
        compose.waitForIdle()
    }

    private fun share() = go { opener.open(SharedLibrary.Text("version: 1")) }

    private fun draftShown() = compose.onNode(hasContentDescription(NAME)).assertTextContains(DRAFT)

    private fun editorOutOfReach() = compose.onNode(hasContentDescription(NAME)).assertDoesNotExist()

    private val confirm get() = compose.onNode(isDialog())

    @Test
    fun `a share over the editor opens on top, and back finds the draft`() {
        showEditor()
        typeDraft()
        share()
        assertEquals(Destination.Import, navigator.sheet?.destination)
        compose.onNodeWithText(standInName(navigator.sheet!!)).assertIsDisplayed()
        // Kept, but out of reach while it's covered.
        editorOutOfReach()

        go { back() }

        assertEquals(Destination.ExerciseEditor, navigator.sheet?.destination)
        draftShown()
    }

    /** A pushed screen over a sheet: the editor stays in its frame under it, out of reach. */
    @Test
    fun `a link to a pushed screen over the editor keeps the draft for back`() {
        showEditor()
        typeDraft()
        go { arrive(Route.fromLink("kettle://settings")!!) }
        compose.onNodeWithText("Screen Settings").assertIsDisplayed()
        editorOutOfReach()

        go { back() }

        draftShown()
    }

    /** Settings is exactly what a link puts over the editor, and it switches the units the form is in. */
    @Test
    fun `a units switch while the editor is covered keeps the draft`() {
        showEditor()
        typeDraft()
        go { arrive(Route(Destination.Settings)) }

        files.preferences = preferences(UnitSystem.IMPERIAL)
        compose.runOnUiThread { latest.reread() }
        compose.waitForIdle()
        go { back() }

        draftShown()
    }

    /** Its window would float over the share, live: confirmed there, it deleted and closed the share. */
    @Test
    fun `a share hides an open confirm, and back shows it again`() {
        files.exercises = listOf(pullUps)
        compose.runOnUiThread { latest.reread() }
        showEditor("pull-ups")
        compose
            .onNode(hasText("Excluir exercício") and isButton)
            .performScrollTo()
            .performClick()
        confirm.assertExists()

        share()
        confirm.assertDoesNotExist()

        go { back() }
        confirm.assertExists()
        assertEquals(emptyList<Library>(), files.written)
    }

    private class Files : FakeStoredFiles() {
        var exercises = emptyList<Exercise>()
        var preferences: Preferences? = preferences(UnitSystem.METRIC)
        val written = mutableListOf<Library>()

        override fun library() = Library(1, exercises, emptyList(), emptyList())

        override fun preferences() = preferences

        override fun writeLibrary(library: Library) {
            written += library
        }
    }

    private companion object {
        const val NAME = "Nome"
        const val DRAFT = "Swing com halter"
        val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        val pullUps = Exercise("pull-ups", "Pull-ups", ExerciseConfig.Reps(sets = 3, targetRepsMin = 8, restSec = 90.0))

        fun preferences(units: UnitSystem) = Preferences(units, ThemePreference.SYSTEM, false, true, null)
    }
}
