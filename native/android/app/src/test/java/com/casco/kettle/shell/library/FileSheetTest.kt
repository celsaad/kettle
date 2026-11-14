package com.casco.kettle.shell.library

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.RepsSetLog
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.Workout
import com.casco.kettle.shell.files.FakeStoredFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.importer.IMPORT_SHARE_FAILED_PARAM
import com.casco.kettle.shell.importer.IMPORT_SOURCE_PARAM
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.settings.BackupActions
import com.casco.kettle.shell.settings.ExportActions
import com.casco.kettle.shell.settings.FolderPick
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Your file (#413): its counts, the ways in (each Import on one source), the ways out, and the backup
 * folder as one row. In `pt`, so a hardcoded English string would show (AGENTS.md).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class FileSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<Route>()
    private var libraryShares = 0
    private val historyShares = mutableListOf<List<Session>>()
    private var folderPicks = 0
    private val backups = mutableListOf<Pair<String, List<Session>>>()
    private var exportAnswer: String? = null
    private val shared = mutableListOf<String>()
    private var shareOpens = true

    private fun show(
        files: Files = Files(),
        canChooseFolder: Boolean = true,
    ): Files {
        val latest =
            LatestFiles(files, CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).apply { reread() }
        val actions =
            FileSheetActions(
                open = { opened += it },
                export =
                    ExportActions(
                        library = { exportAnswer.also { libraryShares++ } },
                        history = { sessions -> exportAnswer.also { historyShares += sessions } },
                    ),
                backup =
                    BackupActions(
                        pickFolder = { FolderPick.Cancelled.also { folderPicks++ } },
                        backUp = { uri, sessions -> null.also { backups += uri to sessions } },
                    ).takeIf { canChooseFolder },
                shareText = { text -> shareOpens.also { shared += text } },
                schema = { "{\"schema\": true}" },
            )
        compose.setContent {
            KettleTheme(darkTheme = false) {
                FileSheetScreen(latest, listOf("steady-strength", "barbell-gym", "kettlebell-basics"), actions)
            }
        }
        return files
    }

    private fun brief() = compose.onNode(hasText("Compartilhar o resumo") and isButton)

    private fun row(title: String) = compose.onNode(hasText(title) and isButton)

    private fun importOn(source: String) = Route(Destination.Import, mapOf(IMPORT_SOURCE_PARAM to source))

    /** Rest is block structure, not an exercise anyone browses for, so it isn't counted, as Library lists. */
    @Test
    fun `is titled with the file's name, and counts what's in it`() {
        show()

        compose.onNodeWithText("exercises.yaml").assert(isHeading())
        compose.onNodeWithText("1 exercício · 1 treino · 0 programas").assertExists()
    }

    @Test
    fun `each way in opens Import on that one source`() {
        show()

        row("Abrir um arquivo .yaml").performClick()
        row("Colar YAML").performClick()
        row("Pacotes iniciais").performClick()

        assertEquals(listOf("file", "paste", "packs").map(::importOn), opened)
    }

    /** It says "Share the brief", so it shares it, rather than opening a second button saying the same. */
    @Test
    fun `Share the brief shares the brief, with the library's ids and the schema in it`() {
        show()

        brief().assertHeightIsAtLeast(48.dp).performClick()

        val text = shared.single()
        assertTrue("pushups" in text && "push-day" in text && "{\"schema\": true}" in text)
        assertEquals(emptyList<Route>(), opened)
    }

    /** No share sheet: Import's assistant source has the copy button that covers it. */
    @Test
    fun `where no share sheet opens, Share the brief falls back to Import's copy`() {
        shareOpens = false
        show()

        brief().performClick()

        assertEquals(
            listOf(
                Route(
                    Destination.Import,
                    mapOf(
                        IMPORT_SOURCE_PARAM to "assistant",
                        IMPORT_SHARE_FAILED_PARAM to "true",
                    ),
                ),
            ),
            opened,
        )
    }

    /** The brief is the library's ids, so it isn't offered empty before the library is read. */
    @Test
    fun `Share the brief waits for the library`() {
        show(Files(library = null))

        brief().assertIsNotEnabled()
    }

    /** The packs' own names, from the import bundle, so the row says what a tap leads to. */
    @Test
    fun `the packs row names the packs`() {
        show()

        compose.onNodeWithText("Firme e Forte · Academia com Barra · Kettlebell Essencial").assertExists()
    }

    @Test
    fun `the ways out open the YAML view and share the two files`() {
        show(Files(sessions = listOf(session)))

        row("Abrir como YAML").performScrollTo().performClick()
        row("Compartilhar arquivo").performScrollTo().performClick()
        row("Exportar registro").performScrollTo().assertIsEnabled().performClick()

        assertEquals(listOf(Route(Destination.LibraryYaml)), opened)
        assertEquals(1, libraryShares)
        assertEquals(listOf(listOf(session)), historyShares)
    }

    /** A row that spells out what it's about to do owes a reason when nothing happens (from Settings, #416). */
    @Test
    fun `says why an export didn't open`() {
        exportAnswer = "exercises.yaml"
        show()

        row("Compartilhar arquivo").performScrollTo().performClick()

        compose
            .onNodeWithText(
                "Não foi possível compartilhar esse arquivo: exercises.yaml",
            ).performScrollTo()
            .assertExists()
    }

    /** What a backup is, and isn't, under the row that makes one (moved from Settings, #416). */
    @Test
    fun `says what the folder gets, and that the log is a record rather than a restore`() {
        show()

        compose
            .onNodeWithText(
                "grava kettle-library.yaml e kettle-history.yaml",
                substring = true,
            ).performScrollTo()
            .assertExists()
        compose.onNodeWithText("O registro de sessões não", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun `without a folder picker, says where the files live instead`() {
        show(canChooseFolder = false)

        compose.onNodeWithText("ficam na pasta do próprio app", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("grava kettle-library.yaml", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the log can't be exported before anything is logged, and says why`() {
        show()

        row("Exportar registro").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Nada registrado ainda — conclua uma sessão primeiro.").assertExists()
    }

    /** A device with no folder picker gets no row promising what can't work. */
    @Test
    fun `offers no backup row where there is no folder picker`() {
        show(canChooseFolder = false)

        compose.onNodeWithText("Pasta de backup").assertDoesNotExist()
    }

    @Test
    fun `with no folder, the backup row chooses one, and there is nothing to back up now`() {
        show()

        row("Pasta de backup").performScrollTo().performClick()

        assertEquals(1, folderPicks)
        compose.onNodeWithText("Fazer backup agora").assertDoesNotExist()
    }

    @Test
    fun `with a folder, Back up now writes into it`() {
        show(Files(sessions = listOf(session), preferences = withFolder))

        row("Fazer backup agora").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()

        // The log as read, not an empty one: backing up nothing would write over the archive in the folder.
        assertEquals(listOf(FOLDER to listOf(session)), backups)
        compose.onNodeWithText("Backup feito.").assertExists()
    }

    /** Settings' two further rows, behind the one: change the folder, or forget it. */
    @Test
    fun `with a folder, the row asks whether to change it or forget it`() {
        val files = show(Files(preferences = withFolder))

        row("Pasta de backup").performScrollTo().performClick()
        compose.onNode(hasText("Trocar pasta") and isButton).assertExists()
        compose.onNode(hasText("Esquecer esta pasta") and isButton).performClick()

        assertNull(files.written.single().backupFolderUri)
        assertEquals(0, folderPicks)
    }

    @Test
    fun `with a folder, changing it asks for a new one`() {
        show(Files(preferences = withFolder))

        row("Pasta de backup").performScrollTo().performClick()
        compose.onNode(hasText("Trocar pasta") and isButton).performClick()

        assertEquals(1, folderPicks)
    }

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    class Files(
        private val sessions: List<Session> = emptyList(),
        private val preferences: Preferences? = null,
        private val library: Library? = seeded,
    ) : FakeStoredFiles() {
        val written = mutableListOf<Preferences>()

        override fun sessionLog() = SessionLog(sessions, emptyList())

        override fun library() = library

        override fun preferences() = written.lastOrNull() ?: preferences

        override fun writePreferences(preferences: Preferences) {
            written += preferences
        }
    }
}

private val seeded =
    Library(
        1,
        listOf(
            Exercise("pushups", "Push-ups", ExerciseConfig.Reps(3, 10, restSec = 60.0)),
            Exercise("breather", "Breather", ExerciseConfig.Rest(60.0)),
        ),
        listOf(Workout("push-day", "Push day", emptyList())),
        emptyList(),
    )
private const val FOLDER = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FKettle"
private val withFolder = Preferences(UnitSystem.METRIC, ThemePreference.SYSTEM, false, true, FOLDER)
private val session =
    Session(
        1,
        "s1",
        "push-day",
        null,
        null,
        null,
        "2026-07-29T09:00:00Z",
        "2026-07-29T09:30:00Z",
        listOf(SessionEntry.Reps("pushups", listOf(RepsSetLog(10, restTakenSec = 60.0)))),
    )
