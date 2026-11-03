package com.casco.kettle.shell.settings

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.REST_DAY_REMINDER_DAYS
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.settingsCounts
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.support.SupporterFile
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.card
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun Appearance(
    preference: ThemePreference,
    onSelect: (ThemePreference) -> Unit,
) = Section(R.string.settings_appearance) {
    Segmented(APPEARANCE, preference, onSelect)
    val locale = LocalConfiguration.current.locales[0]
    // The control's own words, lowercased, so the sentence names what the user just saw.
    val words = APPEARANCE.associate { (value, label) -> value to stringResource(label).lowercase(locale) }
    val device = if (isSystemInDarkTheme()) ThemePreference.DARK else ThemePreference.LIGHT
    Caption(
        if (preference == ThemePreference.SYSTEM) {
            stringResource(R.string.settings_followingDevice, words.getValue(device))
        } else {
            stringResource(R.string.settings_pinnedTo, words.getValue(preference))
        },
    )
}

@Composable
internal fun Units(
    units: UnitSystem,
    onSelect: (UnitSystem) -> Unit,
) = Section(R.string.settings_units) {
    Segmented(UNITS, units, onSelect)
    // Imperial says storage doesn't change, the surprising half: no number in exercises.yaml moves.
    val note =
        if (units ==
            UnitSystem.IMPERIAL
        ) {
            R.string.settings_unitsImperialNote
        } else {
            R.string.settings_unitsMetricNote
        }
    Caption(stringResource(note))
}

@Composable
internal fun Sounds(
    on: Boolean,
    onSelect: (Boolean) -> Unit,
) = Section(R.string.settings_sounds) {
    Segmented(SOUNDS, on, onSelect)
    Caption(stringResource(R.string.settings_soundsNote))
}

@Composable
internal fun Reminder(
    on: Boolean,
    onSelect: (Boolean) -> Unit,
) = Section(R.string.settings_reminder) {
    Segmented(REMINDER, on, onSelect)
    Caption(pluralText(R.plurals.settings_reminderNote, R.string.settings_reminderNote_zero, REST_DAY_REMINDER_DAYS))
}

@Composable
internal fun DataSection(
    sessions: List<Session>,
    scope: CoroutineScope,
    export: ExportActions,
    openImport: () -> Unit,
) = Section(R.string.settings_data) {
    var failure by remember { mutableStateOf<String?>(null) }
    val share = { exporter: suspend () -> String? ->
        failure = null
        scope.launch { failure = exporter() }
    }
    // First: the one row here that changes anything, and the one an assistant-written library arrives by.
    ActionRow(
        stringResource(R.string.settings_importLibrary),
        stringResource(R.string.settings_importLibraryDetail),
        onClick = openImport,
    )
    ActionRow(stringResource(R.string.settings_exportLibrary), stringResource(R.string.settings_exportLibraryDetail)) {
        share(export.library)
    }
    // Disabled rather than hidden with nothing logged: the row answers "is my log exportable?" for
    // someone asking before they start.
    val count = sessions.size
    ActionRow(
        stringResource(R.string.settings_exportHistory),
        if (count == 0) {
            stringResource(R.string.settings_exportHistoryEmpty)
        } else {
            pluralText(R.plurals.settings_exportHistoryDetail, R.string.settings_exportHistoryDetail_zero, count)
        },
        enabled = count > 0,
    ) { share { export.history(sessions) } }
    failure?.let { Outcome(stringResource(R.string.settings_exportFailed, it), accent = true) }
    Caption(stringResource(R.string.settings_sessionsStayNote))
}

/** The supporter count is read from the file each time Settings opens, since native is its writer (#188). */
@Composable
internal fun SupportSection(
    supporter: SupporterFile,
    openSupport: () -> Unit,
) = Section(R.string.settings_support) {
    var tipped by remember { mutableStateOf(false) }
    LaunchedEffect(supporter) { tipped = withContext(Dispatchers.IO) { supporter.read().hasTipped } }
    ActionRow(
        stringResource(R.string.support_row),
        stringResource(if (tipped) R.string.support_rowDetailTipped else R.string.support_rowDetail),
        onClick = openSupport,
    )
}

/**
 * The two ways to reach the developer. Neither collects anything: each hands off to an app the user
 * already has, and the only thing prefilled is the version, printed at the foot of this screen. Where
 * nothing can open the link, it goes to the clipboard, and the row says so, never claiming a copy the
 * clipboard refused.
 */
@Composable
internal fun Contact(
    version: String?,
    contact: ContactActions,
) = Section(R.string.settings_contact) {
    var message by remember { mutableStateOf<String?>(null) }
    val resources = LocalResources.current
    val openOrCopy = { url: String, fallback: String ->
        message =
            when {
                contact.openLink(url) -> null
                contact.copy(fallback) -> resources.getString(R.string.settings_contactCopied, fallback)
                else -> resources.getString(R.string.settings_contactNoApp)
            }
    }
    // Subject only: no body, no device, no logs, or a mail handoff becomes data collection.
    val subject = Uri.encode(stringResource(R.string.settings_contactSubject, version.orEmpty()))
    ActionRow(stringResource(R.string.settings_contactEmail), FEEDBACK_EMAIL) {
        openOrCopy("mailto:$FEEDBACK_EMAIL?subject=$subject", FEEDBACK_EMAIL)
    }
    ActionRow(stringResource(R.string.settings_contactIssues), stringResource(R.string.settings_contactIssuesDetail)) {
        openOrCopy(ISSUES_URL, ISSUES_URL)
    }
    message?.let { Outcome(it, accent = false) }
}

@Composable
internal fun Counts(
    library: Library?,
    sessions: Int,
) = Section(R.string.settings_inYourLibrary) {
    val counts = settingsCounts(library, sessions)
    Column(
        Modifier
            .fillMaxWidth()
            .card(COUNTS_RADIUS)
            .padding(horizontal = Spacing.two + 6.dp, vertical = Spacing.two),
    ) {
        CountRow(R.string.settings_exercises, counts.exercises)
        CountRow(R.string.build_title, counts.workouts)
        CountRow(R.string.programs_title, counts.programs)
        CountRow(R.string.settings_sessions, counts.sessions)
    }
}

/** One count, read as one: its label and its number together. */
@Composable
private fun CountRow(
    @StringRes label: Int,
    value: Int,
) = Row(
    Modifier
        .fillMaxWidth()
        .padding(vertical = Spacing.two - 1.dp)
        .semantics(mergeDescendants = true) {},
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(stringResource(label), style = KettleType.default, color = KettleTheme.colors.textSecondary)
    Text("$value", style = KettleType.heading, color = KettleTheme.colors.text)
}

private val APPEARANCE =
    listOf(
        ThemePreference.LIGHT to R.string.settings_light,
        ThemePreference.DARK to R.string.settings_dark,
        ThemePreference.SYSTEM to R.string.settings_system,
    )

// No "system" option: the device's measurement system only seeds the first value, and unlike the color
// scheme nothing about it changes while the app is open.
private val UNITS =
    listOf(
        UnitSystem.METRIC to R.string.settings_metric,
        UnitSystem.IMPERIAL to R.string.settings_imperial,
    )

// On first, Off first: each default sits where the eye lands, and the two default opposite ways round.
// Separate keys from the reminder's, since they're separate sentences to a translator.
private val SOUNDS = listOf(true to R.string.settings_soundsOn, false to R.string.settings_soundsOff)
private val REMINDER = listOf(false to R.string.settings_reminderOff, true to R.string.settings_reminderOn)

/** Constants, not locale keys: a translator who "fixed" either would break the only feedback channel. */
internal const val FEEDBACK_EMAIL = "kettleapp.feedback@gmail.com"
internal const val ISSUES_URL = "https://github.com/celsaad/kettle/issues"

private val COUNTS_RADIUS = 16.dp
