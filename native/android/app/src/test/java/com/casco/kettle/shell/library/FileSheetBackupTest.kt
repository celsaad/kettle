package com.casco.kettle.shell.library

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.shell.files.FakeStoredFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.settings.BackupActions
import com.casco.kettle.shell.settings.ExportActions
import com.casco.kettle.shell.settings.FolderPick
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.storage.BackupFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.Executor

/**
 * The backup folder on Your file, the cases Settings' rows had before they moved here: what a pick
 * does to the file, and what the row says when it, or the write that follows, goes wrong. In `pt`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class FileSheetBackupTest {
    @get:Rule
    val compose = createComposeRule()

    private val files = Files()
    private var pick: FolderPick = FolderPick.Picked(FOLDER, kept = true)
    private var backUpAnswer: suspend () -> BackupFailure? = { null }
    private val backedUp = mutableListOf<Pair<String, List<Session>>>()
    private lateinit var latest: LatestFiles

    private fun show(
        preferences: Preferences? = withFolder(null),
        reads: CoroutineDispatcher = Dispatchers.Unconfined,
    ) {
        files.stored = preferences
        latest = LatestFiles(files, CoroutineScope(reads), io = Dispatchers.Unconfined).apply { reread() }
        val actions =
            FileSheetActions(
                open = {},
                export = ExportActions(library = { null }, history = { null }),
                backup =
                    BackupActions(
                        pickFolder = { pick },
                        backUp = { uri, sessions ->
                            backedUp += uri to sessions
                            backUpAnswer()
                        },
                    ),
                shareText = { true },
                schema = { "{}" },
            )
        compose.setContent {
            KettleTheme(darkTheme = false) { FileSheetScreen(latest, emptyList(), actions) }
        }
    }

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun press(label: String) = compose.onNode(hasText(label) and isButton).performScrollTo().performClick()

    private fun text(label: String) = compose.onNodeWithText(label, substring = true).performScrollTo()

    private fun missing(label: String) = compose.onNodeWithText(label, substring = true).assertDoesNotExist()

    private fun changeFolder() {
        press("Pasta de backup")
        compose.onNode(hasText("Trocar pasta") and isButton).performClick()
    }

    @Test
    fun `shows the chosen folder rather than the raw tree URI`() {
        show(withFolder(FOLDER))

        text("Documents/Kettle")
        missing(FOLDER)
    }

    @Test
    fun `keeps the chosen folder`() {
        show()

        press("Pasta de backup")

        assertEquals(FOLDER, files.written.last().backupFolderUri)
        text("Documents/Kettle")
    }

    /** A mis-tap opens the picker; backing out of it must not read as "clear my folder". */
    @Test
    fun `leaves an existing folder alone when the picker is cancelled`() {
        pick = FolderPick.Cancelled
        show(withFolder(FOLDER))

        changeFolder()

        assertEquals(emptyList<Preferences>(), files.written)
        text("Documents/Kettle")
    }

    // The URI is what wouldn't survive the restart: a row left looking set, with nothing to write to.
    @Test
    fun `says so when the folder was chosen but could not be saved`() {
        files.failing = true
        show()

        press("Pasta de backup")

        text("não pôde ser salva")
    }

    // A permission withdrawn is refused like a full disk, not left to escape the write and crash.
    @Test
    fun `says so when the folder could not be saved for want of permission`() {
        files.failing = true
        files.refusal = SecurityException()
        show()

        press("Pasta de backup")

        text("não pôde ser salva")
    }

    // And the grant, natively: a folder whose permission wasn't kept stops working at the restart too.
    @Test
    fun `says so when the folder's grant could not be kept`() {
        pick = FolderPick.Picked(FOLDER, kept = false)
        show()

        press("Pasta de backup")

        text("não pôde ser salva")
    }

    // In a translated sentence, never the platform's own words for it.
    @Test
    fun `says so when there's no picker to open`() {
        pick = FolderPick.Unavailable
        show()

        press("Pasta de backup")

        text("Backup em pasta não está disponível neste aparelho.")
    }

    /**
     * A folder chosen before the log's first read lands: backing up then would write an empty archive
     * over the history already in the folder.
     */
    @Test
    fun `won't back up before the log has been read`() {
        // Reads that never run, so the folder is chosen and the log is still unread.
        show(reads = Executor {}.asCoroutineDispatcher())
        runBlocking { latest.writePreferences(withFolder(FOLDER)) }

        compose.onNode(hasText("Fazer backup agora") and isButton).performScrollTo().assertIsNotEnabled()
    }

    /**
     * A folder backed by a cloud provider can take seconds: a row that neither responds nor changes
     * gets tapped again.
     */
    @Test
    fun `says it is working before the write comes back`() {
        val answer = CompletableDeferred<BackupFailure?>()
        backUpAnswer = { answer.await() }
        show(withFolder(FOLDER))

        press("Fazer backup agora")

        text("Gravando…")
        compose.onNode(hasText("Gravando…") and isButton).assertIsNotEnabled()

        answer.complete(null)
        compose.waitForIdle()
        missing("Gravando…")
        text("Backup feito.")
    }

    // The one failure carrying a platform string, inside a translated sentence.
    @Test
    fun `renders why a backup failed, reason included`() {
        backUpAnswer = { BackupFailure.WriteFailed("disk full") }
        show(withFolder(FOLDER))

        press("Fazer backup agora")

        text("Não foi possível gravar o backup: disk full")
    }

    @Test
    fun `points at re-choosing the folder when the grant has gone`() {
        backUpAnswer = { BackupFailure.Unreachable }
        show(withFolder(FOLDER))

        press("Fazer backup agora")

        text("Escolha-a de novo")
    }

    private class Files : FakeStoredFiles() {
        var stored: Preferences? = null
        var failing = false

        /** What a failing write throws: a full disk, or a permission withdrawn. */
        var refusal: Exception = IOException("disk full")
        val written = mutableListOf<Preferences>()

        override fun sessionLog() = SessionLog.Empty

        override fun library() = null

        override fun preferences() = stored

        override fun writePreferences(preferences: Preferences) {
            if (failing) throw refusal
            written += preferences
            stored = preferences
        }
    }
}

private const val FOLDER = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FKettle"

private fun withFolder(uri: String?) = Preferences(UnitSystem.METRIC, ThemePreference.SYSTEM, false, true, uri)
