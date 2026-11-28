package com.casco.kettle.shell.importer

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import com.casco.kettle.domain.Library
import com.casco.kettle.shell.exerciseeditor.ExerciseEditorActions
import com.casco.kettle.shell.exerciseeditor.ExerciseEditorScreen
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A share or a `kettle://` link arriving while the exercise editor is open (#569): it opens over the
 * editor, and back finds what was typed there. The real editor in the real shell, so what's held is its
 * own `remember`ed form; what arrives over it is a stand-in. In `pt`, as the editor's tests are.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class ShareOverEditorTest {
    @get:Rule
    val compose = shellRule()

    private val navigator = Navigator()
    private val opener = SharedOpener(navigator, SharedLibraryInbox())

    @Before
    fun show() {
        val files =
            LatestFiles(Files(), CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).apply { reread() }
        compose.showShell(navigator, files) { route ->
            if (route.destination == Destination.ExerciseEditor) {
                ExerciseEditorScreen(route.params["id"], files, ExerciseEditorActions(close = { navigator.back() }))
            } else {
                Text(standInName(route))
            }
        }
        go { push(Route(Destination.ExerciseEditor)) }
        compose.onNode(hasContentDescription(NAME)).performTextReplacement(DRAFT)
    }

    private fun go(move: Navigator.() -> Unit) {
        compose.runOnUiThread { navigator.move() }
        compose.waitForIdle()
    }

    private fun draftShown() = compose.onNode(hasContentDescription(NAME)).assertTextContains(DRAFT)

    @Test
    fun `a share over the editor opens on top, and back finds the draft`() {
        compose.runOnUiThread { opener.open(SharedLibrary.Text("version: 1")) }
        compose.waitForIdle()
        assertEquals(Destination.Import, navigator.sheet?.destination)
        compose.onNodeWithText(standInName(navigator.sheet!!)).assertIsDisplayed()
        // Kept, but out of reach while it's covered.
        compose.onNode(hasContentDescription(NAME)).assertDoesNotExist()

        go { back() }

        assertEquals(Destination.ExerciseEditor, navigator.sheet?.destination)
        draftShown()
    }

    /** A pushed screen over a sheet hides the sheet's frame altogether: the editor is kept all the same. */
    @Test
    fun `a link to a pushed screen over the editor keeps the draft for back`() {
        go { arrive(Route.fromLink("kettle://settings")!!) }
        compose.onNodeWithText("Screen Settings").assertIsDisplayed()

        go { back() }

        draftShown()
    }

    private class Files : FakeStoredFiles() {
        override fun library() = Library(1, emptyList(), emptyList(), emptyList())
    }

    private companion object {
        const val NAME = "Nome"
        const val DRAFT = "Swing com halter"
    }
}
