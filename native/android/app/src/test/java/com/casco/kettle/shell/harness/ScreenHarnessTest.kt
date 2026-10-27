package com.casco.kettle.shell.harness

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/** What the screen reads and writes through. The port's real one wraps the files; the test's records. */
private interface SessionFiles {
    fun list(): List<ProbeSession>

    fun delete(id: String)
}

private data class ProbeSession(
    val id: String,
    val name: String,
)

private class FakeSessionFiles(
    vararg sessions: ProbeSession,
) : SessionFiles {
    private val sessions = sessions.toMutableList()
    val deleted = mutableListOf<String>()

    /** Thrown by the next delete, as a full disk or a revoked folder would. */
    var failure: IOException? = null

    override fun list() = sessions.toList()

    override fun delete(id: String) {
        failure?.let { throw it }
        deleted += id
        sessions.removeAll { it.id == id }
    }
}

@Composable
private fun ProbeScreen(files: SessionFiles) {
    var sessions by remember { mutableStateOf(files.list()) }
    var confirming by remember { mutableStateOf<ProbeSession?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    Column {
        failure?.let { Text(stringResource(R.string.common_deleteFailed, it)) }
        for (session in sessions) {
            Text(
                session.name,
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClickLabel = stringResource(R.string.common_delete), role = Role.Button) {
                        confirming = session
                    },
            )
        }
    }
    confirming?.let { session ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(R.string.history_deleteConfirmTitle)) },
            text = { Text(stringResource(R.string.common_deleteConfirmBody, session.name)) },
            confirmButton = {
                TextButton({
                    confirming = null
                    try {
                        files.delete(session.id)
                        sessions = files.list()
                    } catch (e: IOException) {
                        failure = e.message.orEmpty()
                    }
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton({ confirming = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

/**
 * The pattern each M4 port's screen test copies (#193), held on a probe screen until the first port has
 * a real one to copy instead; that port deletes this file.
 *
 * - The whole class runs in `pt`, for the reason jest's screen tests do: an English assertion passes a
 *   hardcoded English string. Hardcoding the dialog's title fails the confirm test here.
 * - The screen takes its file boundary as a parameter and the test hands it a fake that records, the
 *   way jest mocks the `@/storage` modules: assertions read as what got deleted, not what got written.
 * - So a port's screen needs its boundary as a parameter. One that reaches the files directly has no
 *   seam for a fake, and Kotlin has no module mock to fall back on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class ScreenHarnessTest {
    @get:Rule
    val compose = createComposeRule()

    private val swings = ProbeSession("2026-10-01T07-00-00", "Morning swings")

    /**
     * jest's `pressAlertButton`: a button inside the dialog, never a same-named control on the screen
     * under it, which `onNodeWithText` alone would find too and fail on as ambiguous.
     */
    private fun ComposeContentTestRule.pressDialogButton(label: String) =
        onNode(hasText(label) and hasAnyAncestor(isDialog())).performClick()

    private fun show(files: SessionFiles) = compose.setContent { KettleTheme(darkTheme = false) { ProbeScreen(files) } }

    @Test
    fun `the screen lists what the files hold`() {
        show(FakeSessionFiles(swings))

        compose.onNodeWithText("Morning swings").assertExists()
    }

    /** The a11y props are asserted here too: TalkBack is checked once, at the end of the migration. */
    @Test
    fun `a row is a 48dp button that TalkBack reads as deleting`() {
        show(FakeSessionFiles(swings))

        compose
            .onNodeWithText("Morning swings")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(
                SemanticsMatcher("click label is Excluir") { it.config[SemanticsActions.OnClick].label == "Excluir" },
            ).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `confirming the dialog deletes the file and drops the row`() {
        val files = FakeSessionFiles(swings)
        show(files)

        compose.onNodeWithText("Morning swings").performClick()
        compose.onNodeWithText("Excluir sessão?").assertExists()
        compose.onNodeWithText("\"Morning swings\" será removido permanentemente.").assertExists()
        compose.pressDialogButton("Excluir")

        assertEquals(listOf(swings.id), files.deleted)
        compose.onNodeWithText("Morning swings").assertDoesNotExist()
        compose.onNodeWithText("Excluir sessão?").assertDoesNotExist()
    }

    @Test
    fun `cancelling the dialog leaves the file alone`() {
        val files = FakeSessionFiles(swings)
        show(files)

        compose.onNodeWithText("Morning swings").performClick()
        compose.pressDialogButton("Cancelar")

        assertEquals(emptyList<String>(), files.deleted)
        compose.onNodeWithText("Morning swings").assertExists()
        compose.onNodeWithText("Excluir sessão?").assertDoesNotExist()
    }

    @Test
    fun `a delete that fails says so and keeps the row`() {
        val files = FakeSessionFiles(swings).apply { failure = IOException("disco cheio") }
        show(files)

        compose.onNodeWithText("Morning swings").performClick()
        compose.pressDialogButton("Excluir")

        compose.onNodeWithText("Não foi possível excluir: disco cheio").assertExists()
        compose.onNodeWithText("Morning swings").assertExists()
        compose.onNodeWithText("Excluir sessão?").assertDoesNotExist()
    }
}
