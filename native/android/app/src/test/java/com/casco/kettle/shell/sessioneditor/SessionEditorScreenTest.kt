package com.casco.kettle.shell.sessioneditor

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.EmomMinuteLog
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.RepsSetLog
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.TimedHoldSetLog
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.Workout
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.StoredFiles
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * The session editor (#239), case for case from RN's `session-editor.test.tsx`, on the harness recipe
 * (#193): in `pt`, since an English assertion passes a hardcoded English string, with the files faked
 * behind [StoredFiles]. Each asserts on the one session written, which is what native's Save does.
 * Beyond jest's: a write the disk refuses, and the a11y.
 *
 * The arithmetic is `:shared`'s (`SessionEntryFormTest`); these cover the wiring.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class SessionEditorScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var closed = 0
    private var sessionsChanged = 0

    private lateinit var latest: LatestFiles

    private fun show(files: Files = Files(aSession())) {
        latest =
            LatestFiles(files, CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).apply { reread() }
        compose.setContent {
            KettleTheme(darkTheme = false) {
                SessionEditorScreen(
                    "session-1",
                    latest,
                    TimeZone.UTC,
                    SessionEditorActions(close = { closed += 1 }, sessionsChanged = { sessionsChanged += 1 }),
                )
            }
        }
    }

    /** A field, by the name it announces: the set it's in, then its label, since every row has a "Reps". */
    private fun field(name: String) = compose.onNode(hasContentDescription(name))

    private fun type(
        name: String,
        text: String,
    ) = field(name).performScrollTo().performTextReplacement(text)

    private fun press(label: String) =
        compose.onNode((hasText(label) or hasContentDescription(label)) and isButton).performScrollTo().performClick()

    /** jest's `pressAlertButton`: a button inside the dialog, never a same-named one on the screen under it. */
    private fun ComposeContentTestRule.pressDialogButton(label: String) =
        onNode(hasText(label) and hasAnyAncestor(isDialog())).performClick()

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    // correcting a logged set

    @Test
    fun `writes the edited value back to the entry it came from`() {
        val files = Files(aSession())
        show(files)

        type("Série 2 · Reps", "7")
        press("Salvar")

        val squat =
            squatEntry.copy(
                sets = listOf(squatEntry.sets[0], squatEntry.sets[1].copy(reps = 7)),
            )
        assertEquals(listOf(aSession(listOf(squat, restEntry, plankEntry))), files.written)
        assertEquals(1, sessionsChanged)
        assertEquals(1, closed)
    }

    /**
     * The plank sits at 2 in `session.entries`, where History shows it at 1, having filtered the rest
     * out. Writing by History's position would put this correction on the rest entry.
     */
    @Test
    fun `uses the raw entry index, not the rest-filtered position History shows`() {
        val files = Files(aSession())
        show(files)

        type("Série 1 · Isometria (sec)", "60")
        press("Salvar")

        assertEquals(
            listOf(squatEntry, restEntry, plankEntry.copy(sets = listOf(TimedHoldSetLog(60.0, 60.0)))),
            files.written.single().entries,
        )
    }

    @Test
    fun `leaves untouched entries alone rather than rewriting them`() {
        val files = Files(aSession())
        show(files)

        type("Série 1 · Reps", "10")
        press("Salvar")

        val entries = files.written.single().entries
        assertEquals(restEntry, entries[1])
        assertEquals(plankEntry, entries[2])
    }

    @Test
    fun `blocks the write and says why when a value is invalid`() {
        val files = Files(aSession())
        show(files)

        type("Série 1 · RPE", "88")
        press("Salvar")

        compose.onNodeWithText("RPE pode ser no máximo 10.").assertExists()
        assertEquals(emptyList<Session>(), files.written)
        assertEquals(0, closed)
    }

    @Test
    fun `converts a load typed in pounds back to kilograms`() {
        val files = Files(aSession(), preferences = imperial)
        show(files)

        type("Série 1 · Carga (lb)", "135")
        press("Salvar")

        val written = files.written.single().entries[0] as SessionEntry.Reps
        assertEquals(61.23, written.sets[0].weightKg)
        // The set left alone keeps its stored kilograms, not the 60.01 a trip through pounds makes.
        assertEquals(60.0, written.sets[1].weightKg)
    }

    @Test
    fun `closes without writing anything when cancelled`() {
        val files = Files(aSession())
        show(files)

        type("Série 1 · Reps", "99")
        press("Cancelar")

        assertEquals(emptyList<Session>(), files.written)
        assertEquals(1, closed)
    }

    // Degrading beats losing the correction: the screen stays open, with the edit, and says why.
    @Test
    fun `stays open and says why when the disk refuses the write`() {
        val files = Files(aSession()).apply { failure = IOException("disk full") }
        show(files)

        type("Série 1 · Reps", "10")
        press("Salvar")

        compose.onNodeWithText("Não foi possível salvar: disk full").assertExists()
        assertEquals(0, closed)
        assertEquals(0, sessionsChanged)
    }

    // A SecurityException often carries no message; the refusal must still show, or Save looks dead.
    @Test
    fun `says the write was refused even when the disk gives no reason`() {
        val files = Files(aSession()).apply { failure = IOException() }
        show(files)

        press("Salvar")

        compose.onNodeWithText("Não foi possível salvar: IOException").assertExists()
        assertEquals(0, closed)
    }

    /**
     * The shell rereads the log while the sheet is up (a native sheet coming up, the app returning to
     * the front). RN's editor copied its drafts once; a reread must not throw away what the user has
     * typed.
     */
    @Test
    fun `keeps what was typed when the log is read again underneath it`() {
        val files = Files(aSession())
        show(files)

        type("Série 2 · Reps", "7")
        files.rewrite(aSession(listOf(squatEntry, restEntry)))
        latest.reread()
        compose.waitForIdle()

        field("Série 2 · Reps").assert(hasText("7"))
    }

    // a value typed down to zero

    @Test
    fun `says the set will go, then drops it on save`() {
        val files = Files(aSession())
        show(files)
        compose.onNodeWithText(ZERO_HINT).assertDoesNotExist()

        type("Série 1 · Reps", "0")
        compose.onNodeWithText(ZERO_HINT).assertExists()
        press("Salvar")

        assertEquals(squatEntry.copy(sets = listOf(squatEntry.sets[1])), files.written.single().entries[0])
    }

    @Test
    fun `removes the exercise when its only set goes to zero`() {
        val files = Files(aSession())
        show(files)

        type("Série 1 · Isometria (sec)", "0")
        press("Salvar")

        assertEquals(listOf(squatEntry, restEntry), files.written.single().entries)
    }

    @Test
    fun `shows an old session without its zero sets, and indexes what it shows`() {
        val skipped = SessionEntry.Reps("back-squat", listOf(RepsSetLog(reps = 0, restTakenSec = 90.0)))
        val files = Files(aSession(listOf(skipped, restEntry, plankEntry)))
        show(files)

        compose.onNodeWithText("Back Squat").assertDoesNotExist()
        type("Série 1 · Isometria (sec)", "50")
        press("Salvar")

        assertEquals(
            listOf(restEntry, plankEntry.copy(sets = listOf(TimedHoldSetLog(50.0, 60.0)))),
            files.written.single().entries,
        )
    }

    // removing

    @Test
    fun `drops one set without confirming, since the entry survives it`() {
        val files = Files(aSession())
        show(files)

        press("Remover série · Série 1")

        compose.onNode(isDialog()).assertDoesNotExist()
        press("Salvar")
        assertEquals(squatEntry.copy(sets = listOf(squatEntry.sets[1])), files.written.single().entries[0])
    }

    // The last set takes the exercise with it, the one step the user can't infer from the button.
    @Test
    fun `confirms before the last set takes the exercise with it`() {
        val files = Files(aSession())
        show(files)

        press("Remover exercício")
        compose.onNodeWithText("Remover este exercício?").assertExists()
        compose.pressDialogButton("Excluir")
        press("Salvar")

        assertEquals(listOf(squatEntry, restEntry), files.written.single().entries)
    }

    @Test
    fun `leaves the entry alone unless the confirm is accepted`() {
        val files = Files(aSession())
        show(files)

        press("Remover exercício")
        compose.pressDialogButton("Cancelar")
        press("Salvar")

        assertEquals(plankEntry, files.written.single().entries[2])
    }

    // sessions that can't be edited

    /** The runner writes an in-flight session through its own copy, so an edit here would be overwritten. */
    @Test
    fun `refuses one that is still running, and offers no fields`() {
        show(Files(aSession().copy(endedAt = null)))

        compose.onNodeWithText("Esta sessão ainda está em andamento").assertExists()
        compose.onNodeWithText("Salvar").assertDoesNotExist()
    }

    @Test
    fun `says so when the session is gone rather than rendering an empty form`() {
        show(Files())

        compose.onNodeWithText("Sessão não encontrada").assertExists()
        compose.onNodeWithText("Salvar").assertDoesNotExist()
    }

    @Test
    fun `says so when nothing in the session has editable fields`() {
        show(Files(aSession(listOf(restEntry, SessionEntry.Emom("clean", listOf(EmomMinuteLog(3)))))))

        compose.onNodeWithText("Nada para editar").assertExists()
    }

    // an entry the editor can't rewrite

    @Test
    fun `is shown read-only rather than hidden`() {
        val emom = SessionEntry.Emom("clean", listOf(EmomMinuteLog(3), EmomMinuteLog(3)))
        val files = Files(aSession(listOf(squatEntry, emom)))
        show(files)

        compose.onNodeWithText("Registros minuto a minuto não são editáveis aqui.").assertExists()
        compose.onNodeWithText("2 intervalos · 6 repetições").assertExists()
        // Still saveable: the squat is editable, and the EMOM is written back as it was.
        press("Salvar")
        assertEquals(listOf(squatEntry, emom), files.written.single().entries)
    }

    // in Portuguese

    @Test
    fun `translates its chrome and keeps the user's own names verbatim`() {
        show()

        compose.onNode(hasText("Editar sessão") and isHeading()).assertExists()
        // User data is never translated: the workout's and the exercise's names are the YAML's.
        compose.onNodeWithText("Leg day · 29 JUL.").assertExists()
        compose.onNodeWithText("Back Squat").assertExists()
    }

    @Test
    fun `translates a validation failure`() {
        show()

        type("Série 1 · Reps", "")
        press("Salvar")

        compose.onNodeWithText("Preencha o campo Reps.").assertExists()
    }

    // a11y

    @Test
    fun `every control is a button at least 44dp tall`() {
        show()

        for (label in listOf("Remover série · Série 1", "Remover exercício", "Cancelar", "Salvar")) {
            compose
                .onNode((hasText(label) or hasContentDescription(label)) and isButton)
                .performScrollTo()
                .assertHeightIsAtLeast(44.dp)
        }
    }

    @Test
    fun `names each field by its set, since every row's labels are alike`() {
        show()

        for (name in listOf("Série 1 · Reps", "Série 2 · Reps", "Série 2 · Carga (kg)", "Série 1 · Isometria (sec)")) {
            field(name).assertExists()
        }
    }

    private class Files(
        vararg sessions: Session,
        private val preferences: Preferences? = null,
    ) : StoredFiles {
        private val sessions = sessions.toMutableList()
        val written = mutableListOf<Session>()

        /** Thrown by the next write, as a full disk would. */
        var failure: IOException? = null

        override fun sessionLog() = SessionLog(sessions.toList(), emptyList())

        override fun library() = Library(1, listOf(backSquat, plank), listOf(legDay), emptyList())

        override fun preferences() = preferences

        override fun deleteSession(id: String) = error("The session editor deletes nothing")

        /** RN writing the file behind the editor's back, as `.dev`'s second writer can. */
        fun rewrite(session: Session) = sessions.replaceAll { if (it.id == session.id) session else it }

        override fun writeSession(session: Session) {
            failure?.let { throw it }
            written += session
            sessions.replaceAll { if (it.id == session.id) session else it }
        }

        override fun writeLibrary(library: Library) = error("Not a library writer")

        override fun writePreferences(preferences: Preferences) = error("The session editor writes no preferences")
    }

    private companion object {
        const val ZERO_HINT = "Séries com 0 são removidas ao salvar."
    }
}

private val backSquat =
    Exercise("back-squat", "Back Squat", ExerciseConfig.Reps(sets = 2, targetRepsMin = 8, restSec = 90.0))
private val plank = Exercise("plank", "Plank", ExerciseConfig.TimedHold(sets = 1, holdSecMax = 45.0, restSec = 60.0))
private val legDay = Workout("leg-day", "Leg day", emptyList())
private val imperial = Preferences(UnitSystem.IMPERIAL, ThemePreference.SYSTEM, false, true, null)

private val squatEntry =
    SessionEntry.Reps(
        "back-squat",
        listOf(
            RepsSetLog(reps = 8, weightKg = 60.0, rpe = 8.0, restTakenSec = 90.0),
            RepsSetLog(reps = 6, weightKg = 60.0, rpe = 9.0, restTakenSec = 120.0),
        ),
    )
private val restEntry = SessionEntry.Rest("rest", 120.0)
private val plankEntry = SessionEntry.TimedHold("plank", listOf(TimedHoldSetLog(45.0, 60.0)))

private fun aSession(entries: List<SessionEntry> = listOf(squatEntry, restEntry, plankEntry)) =
    Session(
        1,
        "session-1",
        "leg-day",
        null,
        null,
        null,
        "2026-07-29T09:00:00.000Z",
        "2026-07-29T10:00:00.000Z",
        entries,
    )
