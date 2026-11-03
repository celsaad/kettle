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
import com.casco.kettle.shell.i18n.deviceUnitSystem
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
import java.io.IOException

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
 * What Settings asks of the platform and the shell, as parameters so a test hears each one.
 * [preferencesChanged] follows each write that landed, so RN's stores read the file again (#220);
 * [open] pushes a route, Import's or Support's.
 */
class SettingsActions(
    val backup: BackupActions,
    val export: ExportActions,
    val contact: ContactActions,
    val reminder: ReminderActions,
    val preferencesChanged: () -> Unit,
    val open: (path: String) -> Unit,
)

/**
 * Settings (#237), as RN's `settings.tsx`, which Play keeps until the preferences flip releases. Native
 * is the only writer of `preferences.json` from then: every change rewrites the whole file through
 * [files], so one setting can't drop another.
 *
 * [canChooseFolder] is whether this device has a folder picker at all; without one the backup rows
 * would advertise something that can never work, so the export copy stands in, as it did in RN where a
 * chosen folder wouldn't survive a relaunch. [version] is the app's, printed at the foot and prefilled
 * in the mail subject. The iOS Files-app copy isn't ported: Android never showed it.
 */
@Composable
fun SettingsScreen(
    files: LatestFiles,
    supporter: SupporterFile,
    canChooseFolder: Boolean,
    version: String?,
    actions: SettingsActions,
) {
    val units = deviceUnitSystem(LocalConfiguration.current.locales[0])
    val preferences = preferencesOrDefault(files.preferences, units)
    val sessions = files.sessionLog?.sessions.orEmpty()
    val scope = rememberCoroutineScope()
    val save = remember(files, units, actions) { PreferencesSaver(files, units, scope, actions.preferencesChanged) }
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
            DataSection(sessions, scope, actions.export) { actions.open(IMPORT_PATH) }
            SupportSection(supporter) { actions.open(SUPPORT_PATH) }
            Section(R.string.settings_files) {
                if (canChooseFolder) {
                    BackupFolder(preferences.backupFolderUri, files.sessionLog?.sessions, save, scope, actions.backup)
                } else {
                    ExportCopy()
                }
            }
            Contact(version, actions.contact)
            Counts(files.library, sessions.size)
            version?.let { Version(it) }
        }
    }
}

/** The four settings that are choices, each one a write of the whole file. */
@Composable
private fun PreferenceSections(
    preferences: Preferences,
    save: PreferencesSaver,
    reminder: ReminderActions,
) {
    Appearance(preferences.themePreference) { theme -> save.launch { it.copy(themePreference = theme) } }
    Units(preferences.unitSystem) { chosen -> save.launch { it.copy(unitSystem = chosen) } }
    Sounds(preferences.sessionSounds) { on -> save.launch { it.copy(sessionSounds = on) } }
    Reminder(preferences.restDayReminder) { on ->
        save.launch({ it.copy(restDayReminder = on) }) { written -> reminder.changed(written.preferences) }
        // Asked here and nowhere else (#221): the reminder is the only thing that posts on its own.
        if (on) reminder.askNotifications()
    }
}

@Composable
private fun Version(version: String) =
    Text(
        stringResource(R.string.settings_version, version),
        Modifier
            .fillMaxWidth()
            .padding(top = Spacing.four),
        style = KettleType.small,
        color = KettleTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
    )

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
    private val changed: () -> Unit,
) {
    suspend fun write(change: (Preferences) -> Preferences): Written {
        // Read when the write runs, not when it was asked for, so a second quick tap builds on the first.
        val next = change(preferencesOrDefault(files.preferences, units))
        // Past the sheet closing, as the session editor's Save: a write cut off there could land with
        // RN never told, and its choice held over every read for the rest of the run.
        val landed =
            withContext(NonCancellable) {
                try {
                    files.writePreferences(next)
                    changed()
                    true
                } catch (_: IOException) {
                    false
                }
            }
        return Written(next, landed)
    }

    fun launch(
        change: (Preferences) -> Preferences,
        onDone: (Written) -> Unit,
    ) = scope.launch { onDone(write(change)) }

    fun launch(change: (Preferences) -> Preferences) = launch(change) {}
}

// RN's routes: Import is RN's until the library flip, Support native behind its stub. The two share no
// code, so change both.
internal const val IMPORT_PATH = "/import"
internal const val SUPPORT_PATH = "/support"
