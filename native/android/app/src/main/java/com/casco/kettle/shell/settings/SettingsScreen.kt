package com.casco.kettle.shell.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.preferencesOrDefault
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.writeOrRefusal
import com.casco.kettle.shell.i18n.deviceUnitSystem
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.support.SupporterFile
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.storage.BackupFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The folder picker's answer. Backing out is [Cancelled], never a failure: it's how a mis-tap closes. */
sealed interface FolderPick {
    data object Cancelled : FolderPick

    /** [kept] is whether the grant was persisted, without which the folder stops working at a restart. */
    data class Picked(
        val uri: String,
        val kept: Boolean,
    ) : FolderPick

    /** No picker to open it with. */
    data object Unavailable : FolderPick
}

/** The backup folder's two: choosing it, and writing into it, each running its own IO off the main thread. */
class BackupActions(
    val pickFolder: suspend () -> FolderPick,
    val backUp: suspend (folderUri: String, sessions: List<Session>) -> BackupFailure?,
)

/** Each answers null where the share sheet opened, or why it didn't. */
class ExportActions(
    val library: suspend () -> String?,
    val history: suspend (sessions: List<Session>) -> String?,
)

class ContactActions(
    /** False where nothing could open [url]. */
    val openLink: (url: String) -> Boolean,
    /** False where the clipboard refused. */
    val copy: (text: String) -> Boolean,
)

/** [changed] hears the reminder's new setting, to reconcile its alarm; [askNotifications] is asked on switch-on. */
class ReminderActions(
    val askNotifications: () -> Unit,
    val changed: (Preferences) -> Unit,
)

/**
 * What Settings asks of the platform and the shell, as parameters so a test hears each one. [open]
 * pushes a route, Your file's or Support's.
 */
class SettingsActions(
    val contact: ContactActions,
    val reminder: ReminderActions,
    val open: (Route) -> Unit,
)

/**
 * Settings (#237), as RN's `settings.tsx`, which Play keeps until the preferences flip releases. Native
 * is the only writer of `preferences.json` from then: every change rewrites the whole file through
 * [files], so one setting can't drop another.
 *
 * Preferences only since #416: Appearance and Units, the two switches, one row to Your file, which
 * holds export, import and the backup folder since #413, and the Kettle rows. [version] is the app's,
 * printed at the foot and prefilled in the mail subject.
 */
@Composable
fun SettingsScreen(
    files: LatestFiles,
    supporter: SupporterFile,
    version: String?,
    actions: SettingsActions,
) {
    val units = deviceUnitSystem(LocalConfiguration.current.locales[0])
    val preferences = preferencesOrDefault(files.preferences, units)
    val scope = rememberCoroutineScope()
    val save = remember(files, units, actions) { PreferencesSaver(files, units, scope) }
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.three)
                .padding(bottom = Spacing.four)
                .navigationBarsPadding(),
        ) {
            Text(
                stringResource(R.string.settings_title),
                Modifier.semantics { heading() },
                style = KettleType.subtitle,
                color = KettleTheme.colors.text,
            )
            PreferenceSections(preferences, save, actions.reminder)
            YourFile { actions.open(Route(Destination.YourFile)) }
            KettleSection(supporter, version, actions.contact) { actions.open(Route(Destination.Support)) }
            Footer(version)
        }
    }
}

/** The four settings, each one a write of the whole file. */
@Composable
private fun PreferenceSections(
    preferences: Preferences,
    save: PreferencesSaver,
    reminder: ReminderActions,
) {
    Appearance(preferences.themePreference) { theme -> save.launch { it.copy(themePreference = theme) } }
    Units(preferences.unitSystem) { chosen -> save.launch { it.copy(unitSystem = chosen) } }
    Switches(
        preferences,
        onSounds = { on -> save.launch { it.copy(sessionSounds = on) } },
        onReminder = { on ->
            save.launch({ it.copy(restDayReminder = on) }) { written -> reminder.changed(written.preferences) }
            // Asked here and nowhere else (#221): the reminder is the only thing that posts on its own.
            if (on) reminder.askNotifications()
        },
    )
}

/**
 * The foot: the version where there is one, and the one promise Settings makes about the data, as the
 * board closes on it, always.
 */
@Composable
private fun Footer(version: String?) =
    Column(Modifier.fillMaxWidth().padding(top = Spacing.four), horizontalAlignment = Alignment.CenterHorizontally) {
        val style = KettleType.small
        val color = KettleTheme.colors.textSecondary
        version?.let { Text(stringResource(R.string.settings_version, it), style = style, color = color) }
        Text(
            stringResource(R.string.settings_sessionsStayNote),
            style = style,
            color = color,
            textAlign = TextAlign.Center,
        )
    }

/** What a change made, and whether it reached the disk. */
internal data class Written(
    val preferences: Preferences,
    val landed: Boolean,
)

/**
 * Writes a change to the preferences, the whole file, applied at once as RN's store applied it: these
 * redraw every weight, or the whole app's colors. A refused write costs the choice at the next launch,
 * and only the folder says so. Where there's no file yet, the first change starts from the device's
 * [units] and the defaults.
 */
internal class PreferencesSaver(
    private val files: LatestFiles,
    private val units: UnitSystem,
    private val scope: CoroutineScope,
) {
    suspend fun write(change: (Preferences) -> Preferences): Written {
        // Read when the write runs, not when it was asked for, so a second quick tap builds on the first.
        val next = change(preferencesOrDefault(files.preferences, units))
        // Past the sheet closing, as the session editor's Save: a write cut off there could land unread,
        // its choice missing from every screen until the next read.
        val landed = withContext(NonCancellable) { writeOrRefusal { files.writePreferences(next) } == null }
        return Written(next, landed)
    }

    fun launch(
        change: (Preferences) -> Preferences,
        onDone: (Written) -> Unit,
    ) = scope.launch { onDone(write(change)) }

    fun launch(change: (Preferences) -> Preferences) = launch(change) {}
}
