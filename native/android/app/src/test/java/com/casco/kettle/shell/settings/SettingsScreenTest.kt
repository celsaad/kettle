package com.casco.kettle.shell.settings

import android.net.Uri
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
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
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.SupporterState
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.Workout
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.StoredFiles
import com.casco.kettle.shell.support.SupporterFile
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.storage.BackupFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.Executor

/**
 * Settings (#237), case for case from RN's `settings-backup`, `settings-sounds` and `settings-contact`
 * tests, on the harness recipe (#193): in `pt`, since an English assertion passes a hardcoded English
 * string, with the files faked behind [StoredFiles] and the platform behind [SettingsActions]. Where
 * jest read the store, these read the file written, which is what native's write is. Beyond jest's:
 * the grant that wasn't kept, the reminder's permission, an export that couldn't share, and the a11y.
 *
 * The backup itself is `:shared`'s (`BackupTest`); these cover the wiring.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val files = Files()
    private val actions = Actions()
    private lateinit var latest: LatestFiles

    private fun show(
        preferences: Preferences? = withFolder(null),
        canChooseFolder: Boolean = true,
        supporter: SupporterState = SupporterState.EMPTY,
        reads: CoroutineDispatcher = Dispatchers.Unconfined,
    ) {
        files.stored = preferences
        latest = LatestFiles(files, CoroutineScope(reads), io = Dispatchers.Unconfined).apply { reread() }
        compose.setContent {
            KettleTheme(darkTheme = false) {
                SettingsScreen(latest, FixedSupporter(supporter), canChooseFolder, "9.9.9", actions.settings)
            }
        }
    }

    private fun press(label: String) = compose.onNode(hasText(label) and isButton).performScrollTo().performClick()

    private fun text(label: String) = compose.onNodeWithText(label, substring = true).performScrollTo()

    private fun missing(label: String) = compose.onNodeWithText(label, substring = true).assertDoesNotExist()

    /** The sounds pair is first on screen; the reminder's carries the same two words below it. */
    private fun sounds(label: String) = compose.onAllNodes(hasText(label) and isButton)[0]

    private fun reminder(label: String) = compose.onAllNodes(hasText(label) and isButton)[1]

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    // choosing a folder

    @Test
    fun `invites the choice when none has been made`() {
        show()

        text("Pasta de backup")
        text("Ainda não escolhida. Selecione uma pasta neste aparelho.")
        // Nothing to forget yet, so the row that undoes the choice isn't there to be tapped.
        missing("Esquecer esta pasta")
    }

    @Test
    fun `shows the chosen folder rather than the raw tree URI`() {
        show(withFolder(FOLDER))

        text("Documents/Kettle")
        missing(FOLDER)
        text("Esquecer esta pasta")
    }

    @Test
    fun `keeps the chosen folder`() {
        show()

        press("Pasta de backup")

        assertEquals(FOLDER, files.written.last().backupFolderUri)
        assertEquals(1, actions.preferencesChanged)
        text("Documents/Kettle")
    }

    /** A mis-tap opens the picker; backing out of it must not read as "clear my folder". */
    @Test
    fun `leaves an existing folder alone when the picker is cancelled`() {
        actions.pick = FolderPick.Cancelled
        show(withFolder(FOLDER))

        press("Pasta de backup")

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
        assertEquals(0, actions.preferencesChanged)
    }

    // And the grant, natively: a folder whose permission wasn't kept stops working at the restart too.
    @Test
    fun `says so when the folder's grant could not be kept`() {
        actions.pick = FolderPick.Picked(FOLDER, kept = false)
        show()

        press("Pasta de backup")

        text("não pôde ser salva")
    }

    // In a translated sentence, never the platform's own words for it.
    @Test
    fun `says so when there's no picker to open`() {
        actions.pick = FolderPick.Unavailable
        show()

        press("Pasta de backup")

        text("Backup em pasta não está disponível neste aparelho.")
    }

    @Test
    fun `forgets the folder on request`() {
        show(withFolder(FOLDER))

        press("Esquecer esta pasta")

        assertEquals(null, files.written.single().backupFolderUri)
        missing("Esquecer esta pasta")
    }

    // backing up now

    @Test
    fun `is disabled until a folder is chosen`() {
        show()

        text("Escolha uma pasta primeiro.")
        compose.onNode(hasText("Fazer backup agora") and isButton).assertIsNotEnabled()
    }

    @Test
    fun `backs up into the folder the user chose`() {
        files.log = SessionLog(listOf(aSession), emptyList())
        show(withFolder(FOLDER))

        press("Fazer backup agora")

        assertEquals(listOf(FOLDER to listOf(aSession)), actions.backedUp)
        text("Backup feito.")
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
        actions.backUpAnswer = { answer.await() }
        show(withFolder(FOLDER))

        press("Fazer backup agora")

        text("Gravando…")
        compose.onNode(hasText("Fazer backup agora") and isButton).assertIsNotEnabled()

        answer.complete(null)
        compose.waitForIdle()
        missing("Gravando…")
        text("Backup feito.")
    }

    // The one failure carrying a platform string, inside a translated sentence.
    @Test
    fun `renders why a backup failed, reason included`() {
        actions.backUpAnswer = { BackupFailure.WriteFailed("disk full") }
        show(withFolder(FOLDER))

        press("Fazer backup agora")

        text("Não foi possível gravar o backup: disk full")
    }

    @Test
    fun `points at re-choosing the folder when the grant has gone`() {
        actions.backUpAnswer = { BackupFailure.Unreachable }
        show(withFolder(FOLDER))

        press("Fazer backup agora")

        text("Escolha-a de novo")
    }

    /** No picker to choose with: the rows would advertise a backup that can never happen. */
    @Test
    fun `falls back to the export copy where a folder can't be chosen`() {
        show(canChooseFolder = false)

        missing("Pasta de backup")
        missing("Fazer backup agora")
        text("Exportar envia o exercises.yaml")
    }

    @Test
    fun `the backup rows are translated`() {
        show(withFolder(FOLDER))

        text("Pasta de backup")
        compose.onNode(hasText("Fazer backup agora") and isButton).performScrollTo()
        text("Esquecer esta pasta")
    }

    // session sounds

    @Test
    fun `mutes the session cues on request`() {
        show()

        sounds("Desligado").performScrollTo().performClick()

        assertFalse(files.written.single().sessionSounds)
    }

    @Test
    fun `turns them back on`() {
        show(withFolder(null).copy(sessionSounds = false))

        sounds("Ligado").performScrollTo().performClick()

        assertTrue(files.written.single().sessionSounds)
    }

    /**
     * The state, not just the label: it's what TalkBack is told. The reminder is on in this fixture, so
     * its pair reads the other way round, which makes the first two about this control.
     */
    @Test
    fun `shows the stored choice rather than the default`() {
        show(withFolder(null).copy(sessionSounds = false, restDayReminder = true))

        sounds("Desligado").assertIsSelected()
        sounds("Ligado").assertIsNotSelected()
        reminder("Ligado").assertIsSelected()
    }

    // One file, so a change here can't drop the folder, the units or the theme.
    @Test
    fun `writes the whole preferences file, not just its own field`() {
        show(Preferences(UnitSystem.IMPERIAL, ThemePreference.DARK, true, true, FOLDER))

        sounds("Desligado").performScrollTo().performClick()

        assertEquals(listOf(Preferences(UnitSystem.IMPERIAL, ThemePreference.DARK, true, false, FOLDER)), files.written)
    }

    // A fresh install has no file: the first change writes the device's units and the rest's defaults.
    @Test
    fun `a first change writes the defaults beside it`() {
        show(preferences = null)

        sounds("Desligado").performScrollTo().performClick()

        assertEquals(listOf(Preferences(UnitSystem.METRIC, ThemePreference.SYSTEM, false, false, null)), files.written)
    }

    @Test
    fun `the sounds row is translated`() {
        show()

        compose.onNode(hasText("SONS DA SESSÃO") and isHeading()).performScrollTo()
        text("no silencioso")
    }

    // appearance, units and the reminder

    /** The chrome follows `LatestFiles` (`SystemBarIconsTest`), so the write has to land there at once. */
    @Test
    fun `pins the scheme chosen, and says so`() {
        show()

        press("Escuro")

        assertEquals(ThemePreference.DARK, latest.preferences?.themePreference)
        assertEquals(ThemePreference.DARK, files.written.single().themePreference)
        text("Fixado em escuro, ignorando o dispositivo.")
    }

    @Test
    fun `says which scheme the device is on while following it`() {
        show()

        text("Seguindo o dispositivo, que está definido como claro.")
    }

    @Test
    fun `switches the units`() {
        show()

        press("Imperial")

        assertEquals(UnitSystem.IMPERIAL, files.written.single().unitSystem)
    }

    /** Asked here and only here (#221), and the alarm reconciled with the setting just written. */
    @Test
    fun `switching the reminder on asks to notify, and reconciles the alarm`() {
        show()

        reminder("Ligado").performScrollTo().performClick()

        assertEquals(1, actions.askedNotifications)
        assertEquals(listOf(true), actions.reminders.map { it.restDayReminder })
    }

    @Test
    fun `switching it off reconciles without asking`() {
        show(withFolder(null).copy(restDayReminder = true))

        reminder("Desligado").performScrollTo().performClick()

        assertEquals(0, actions.askedNotifications)
        assertEquals(listOf(false), actions.reminders.map { it.restDayReminder })
    }

    // data

    @Test
    fun `exports the history it shows`() {
        files.log = SessionLog(listOf(aSession), emptyList())
        show()

        text("Compartilhe sua única sessão registrada em um só arquivo.")
        press("Exportar histórico")

        assertEquals(listOf(listOf(aSession)), actions.exportedHistories)
    }

    // Disabled rather than hidden: the row answers "is my log exportable?" before the first session.
    @Test
    fun `can't export a history with nothing logged`() {
        show()

        text("Nada registrado ainda — conclua uma sessão primeiro.")
        compose.onNode(hasText("Exportar histórico") and isButton).assertIsNotEnabled()
    }

    /** A row that spells out what it's about to do owes a reason when nothing happens. */
    @Test
    fun `says why an export didn't open`() {
        actions.exportAnswer = "exercises.yaml"
        show()

        press("Exportar biblioteca")

        text("Não foi possível compartilhar esse arquivo: exercises.yaml")
    }

    @Test
    fun `opens Import and Support`() {
        show()

        press("Importar biblioteca")
        press("Apoie o Kettle")

        assertEquals(listOf("/import", "/support"), actions.routes)
    }

    @Test
    fun `thanks a past supporter`() {
        show(supporter = SupporterState(2, "2026-10-01T09:00:00.000Z", null))

        text("Obrigado por apoiar o Kettle.")
    }

    /** Library's count leaves the built-in rest out, so the two screens agree. Each row reads as one. */
    @Test
    fun `counts what is in the library, the built-in rest left out`() {
        files.library =
            Library(
                1,
                listOf(
                    Exercise("squat", "Squat", ExerciseConfig.Reps(sets = 3, targetRepsMin = 5, restSec = 90.0)),
                    Exercise("rest", "Rest", ExerciseConfig.Rest(durationSec = 60.0)),
                ),
                listOf(Workout("a", "A", emptyList()), Workout("b", "B", emptyList())),
                emptyList(),
            )
        files.log = SessionLog(listOf(aSession), emptyList())
        show()

        compose.onNode(hasText("Exercícios") and hasText("1")).performScrollTo()
        compose.onNode(hasText("Treinos") and hasText("2")).performScrollTo()
        compose.onNode(hasText("Programas") and hasText("0")).performScrollTo()
        compose.onNode(hasText("Sessões") and hasText("1")).performScrollTo()
    }

    @Test
    fun `prints the version at the foot`() {
        show()

        text("Kettle 9.9.9")
    }

    // getting in touch

    @Test
    fun `shows the address rather than making the user tap to find out what it is`() {
        show()

        text(FEEDBACK_EMAIL)
    }

    /** Subject only, and only the version, which is already printed at the foot of the screen. */
    @Test
    fun `prefills the subject with the version, and nothing else`() {
        show()

        press("Escrever para o desenvolvedor")

        val url = actions.opened.single()
        assertTrue(url.startsWith("mailto:$FEEDBACK_EMAIL?subject="))
        assertTrue(Uri.decode(url).contains("Kettle 9.9.9 — comentários"))
        assertFalse(url.contains("body="))
    }

    @Test
    fun `opens the issue tracker in the browser`() {
        show()

        press("Relatar um problema no GitHub")

        assertEquals(listOf(ISSUES_URL), actions.opened)
    }

    @Test
    fun `copies the address instead of failing silently when nothing can open it`() {
        actions.opens = false
        show()

        press("Escrever para o desenvolvedor")

        assertEquals(listOf(FEEDBACK_EMAIL), actions.copied)
        text("Nenhum app pôde abrir isso, então $FEEDBACK_EMAIL foi copiado para a área de transferência.")
    }

    @Test
    fun `does not claim to have copied when the clipboard refused`() {
        actions.opens = false
        actions.copies = false
        show()

        press("Escrever para o desenvolvedor")

        text("também não deu para copiar")
    }

    @Test
    fun `degrades the same way for the issue tracker`() {
        actions.opens = false
        show()

        press("Relatar um problema no GitHub")

        assertEquals(listOf(ISSUES_URL), actions.copied)
    }

    // The address and the URL are values, not prose, and are never translated.
    @Test
    fun `is translated, except for the address itself`() {
        show()

        compose.onNode(hasText("FALE COM A GENTE") and isHeading()).performScrollTo()
        text("Escrever para o desenvolvedor")
        text(FEEDBACK_EMAIL)
    }

    // a11y

    @Test
    fun `every control is a button, 48dp at least, and sections are headings`() {
        show(withFolder(FOLDER))

        for (label in listOf("Claro", "Pasta de backup", "Fazer backup agora", "Escrever para o desenvolvedor")) {
            compose
                .onAllNodes(hasText(label) and isButton)[0]
                .performScrollTo()
                .assert(isButton)
                .assertHeightIsAtLeast(48.dp)
        }
        compose.onNode(hasText("APARÊNCIA") and isHeading()).assertExists()
        compose.onNodeWithText("Configurações").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    private class Files : StoredFiles {
        var stored: Preferences? = null
        var library: Library? = null
        var log = SessionLog.Empty
        var failing = false
        val written = mutableListOf<Preferences>()

        override fun sessionLog() = log

        override fun library() = library

        override fun preferences() = stored

        override fun deleteSession(id: String) = error("Settings deletes no session")

        override fun writeSession(session: Session) = error("Settings writes no session")

        override fun writePreferences(preferences: Preferences) {
            if (failing) throw IOException("disk full")
            written += preferences
            stored = preferences
        }
    }

    private class Actions {
        var pick: FolderPick = FolderPick.Picked(FOLDER, kept = true)
        var backUpAnswer: suspend () -> BackupFailure? = { null }
        val backedUp = mutableListOf<Pair<String, List<Session>>>()
        var exportAnswer: String? = null
        val exportedHistories = mutableListOf<List<Session>>()
        var opens = true
        var copies = true
        val opened = mutableListOf<String>()
        val copied = mutableListOf<String>()
        var askedNotifications = 0
        val reminders = mutableListOf<Preferences>()
        var preferencesChanged = 0
        val routes = mutableListOf<String>()

        val settings =
            SettingsActions(
                BackupActions(
                    pickFolder = { pick },
                    backUp = { uri, sessions ->
                        backedUp += uri to sessions
                        backUpAnswer()
                    },
                ),
                ExportActions(
                    library = { exportAnswer },
                    history = { sessions ->
                        exportedHistories += sessions
                        exportAnswer
                    },
                ),
                ContactActions(
                    openLink = { url ->
                        if (opens) opened += url
                        opens
                    },
                    copy = { text ->
                        copied += text
                        copies
                    },
                ),
                ReminderActions(askNotifications = { askedNotifications += 1 }, changed = { reminders += it }),
                preferencesChanged = { preferencesChanged += 1 },
                open = { routes += it },
            )
    }

    private class FixedSupporter(
        private val state: SupporterState,
    ) : SupporterFile {
        override fun read() = state

        override fun write(state: SupporterState) = error("Settings writes no tip")
    }
}

private const val FOLDER = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FKettle"

private fun withFolder(uri: String?) = Preferences(UnitSystem.METRIC, ThemePreference.SYSTEM, false, true, uri)

private val aSession =
    Session(
        1,
        "2026-10-01-a",
        "leg-day",
        null,
        null,
        null,
        "2026-10-01T09:00:00.000Z",
        "2026-10-01T10:00:00.000Z",
        emptyList(),
    )
