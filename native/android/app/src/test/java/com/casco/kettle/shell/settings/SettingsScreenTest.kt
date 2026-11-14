package com.casco.kettle.shell.settings

import android.net.Uri
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.SupporterState
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.shell.files.FakeStoredFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.support.SupporterFile
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings (#237), trimmed to preferences in #416 (the backup folder's cases moved to
 * `FileSheetBackupTest` with the rows), case for case from RN's `settings-sounds` and `settings-contact`
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
        supporter: SupporterState = SupporterState.EMPTY,
        reads: CoroutineDispatcher = Dispatchers.Unconfined,
        version: String? = "9.9.9",
    ) {
        files.stored = preferences
        latest = LatestFiles(files, CoroutineScope(reads), io = Dispatchers.Unconfined).apply { reread() }
        compose.setContent {
            KettleTheme(darkTheme = false) {
                SettingsScreen(latest, FixedSupporter(supporter), version, actions.settings)
            }
        }
    }

    private fun press(label: String) = compose.onNode(hasText(label) and isButton).performScrollTo().performClick()

    private fun text(label: String) = compose.onNodeWithText(label, substring = true).performScrollTo()

    private fun missing(label: String) = compose.onNodeWithText(label, substring = true).assertDoesNotExist()

    /** Each a switch named by its own title (#416), the whole row toggling it. */
    private fun sounds() = compose.onNode(hasText("Sons da sessão") and isSwitch).performScrollTo()

    private fun reminder() = compose.onNode(hasText("Lembrete") and isSwitch).performScrollTo()

    private val isSwitch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    // session sounds

    @Test
    fun `mutes the session cues on request`() {
        show()

        sounds().performClick()

        assertFalse(files.written.single().sessionSounds)
    }

    @Test
    fun `turns them back on`() {
        show(withFolder(null).copy(sessionSounds = false))

        sounds().performClick()

        assertTrue(files.written.single().sessionSounds)
    }

    /** The state, not just the look: it's what TalkBack is told. Opposite ways round, so neither is a default. */
    @Test
    fun `shows the stored choice rather than the default`() {
        show(withFolder(null).copy(sessionSounds = false, restDayReminder = true))

        sounds().assertIsOff().assertHeightIsAtLeast(48.dp)
        reminder().assertIsOn().assertHeightIsAtLeast(48.dp)
    }

    // One file, so a change here can't drop the folder, the units or the theme.
    @Test
    fun `writes the whole preferences file, not just its own field`() {
        show(Preferences(UnitSystem.IMPERIAL, ThemePreference.DARK, true, true, FOLDER))

        sounds().performClick()

        assertEquals(listOf(Preferences(UnitSystem.IMPERIAL, ThemePreference.DARK, true, false, FOLDER)), files.written)
    }

    // A fresh install has no file: the first change writes the device's units and the rest's defaults.
    @Test
    fun `a first change writes the defaults beside it`() {
        show(preferences = null)

        sounds().performClick()

        assertEquals(listOf(Preferences(UnitSystem.METRIC, ThemePreference.SYSTEM, false, false, null)), files.written)
    }

    @Test
    fun `the sounds row is translated`() {
        show()

        compose.onNode(hasText("SONS E LEMBRETES") and isHeading()).performScrollTo()
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

        reminder().performClick()

        assertEquals(1, actions.askedNotifications)
        assertEquals(listOf(true), actions.reminders.map { it.restDayReminder })
    }

    @Test
    fun `switching it off reconciles without asking`() {
        show(withFolder(null).copy(restDayReminder = true))

        reminder().performClick()

        assertEquals(0, actions.askedNotifications)
        assertEquals(listOf(false), actions.reminders.map { it.restDayReminder })
    }

    // your file

    /** #416: export, import and the backup folder are the file sheet's since #413; Settings keeps the way there. */
    @Test
    fun `opens Your file and Support, and has no data block of its own`() {
        show()

        press("exercises.yaml")
        press("Apoie o Kettle")

        assertEquals(listOf(Route(Destination.YourFile), Route(Destination.Support)), actions.routes)
        text("Exportar, pasta de backup e importar")
        missing("Exportar histórico")
        missing("Pasta de backup")
        missing("Importar biblioteca")
    }

    @Test
    fun `thanks a past supporter`() {
        show(supporter = SupporterState(2, "2026-10-01T09:00:00.000Z", null))

        // The section reads the file on Dispatchers.IO, which waitForIdle doesn't wait for: CI's slower
        // runner asserted before the read landed (#371).
        compose.waitUntil(READ_TIMEOUT_MS) {
            compose
                .onAllNodesWithText(
                    "Obrigado por apoiar o Kettle.",
                    substring = true,
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }
        text("Obrigado por apoiar o Kettle.")
    }

    @Test
    fun `prints the version at the foot, with what leaves the phone`() {
        show()

        text("Kettle 9.9.9")
        text("Nada sai deste dispositivo a menos que você mesmo compartilhe.")
    }

    /** The promise doesn't depend on there being a version to print beside it. */
    @Test
    fun `keeps the privacy note at the foot with no version to print`() {
        show(version = null)

        missing("Kettle 9.9.9")
        text("Nada sai deste dispositivo a menos que você mesmo compartilhe.")
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

        compose.onNode(hasText("KETTLE") and isHeading()).performScrollTo()
        text("Escrever para o desenvolvedor")
        text(FEEDBACK_EMAIL)
    }

    // a11y

    @Test
    fun `every control is a button, 48dp at least, and sections are headings`() {
        show()

        for (label in listOf("Claro", "exercises.yaml", "Escrever para o desenvolvedor")) {
            compose
                .onAllNodes(hasText(label) and isButton)[0]
                .performScrollTo()
                .assert(isButton)
                .assertHeightIsAtLeast(48.dp)
        }
        compose.onNode(hasText("APARÊNCIA") and isHeading()).assertExists()
        compose.onNodeWithText("Configurações").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    private class Files : FakeStoredFiles() {
        var stored: Preferences? = null
        val written = mutableListOf<Preferences>()

        override fun preferences() = stored

        override fun writePreferences(preferences: Preferences) {
            written += preferences
            stored = preferences
        }
    }

    private class Actions {
        var opens = true
        var copies = true
        val opened = mutableListOf<String>()
        val copied = mutableListOf<String>()
        var askedNotifications = 0
        val reminders = mutableListOf<Preferences>()
        val routes = mutableListOf<Route>()

        val settings =
            SettingsActions(
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

/** Long enough for an IO read on a loaded CI runner; a failing wait is a real failure, not a flake. */
private const val READ_TIMEOUT_MS = 5_000L
