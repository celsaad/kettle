package com.casco.kettle.shell.settings

import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.REST_DAY_REMINDER_DAYS
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.library.LIBRARY_FILE_NAME
import com.casco.kettle.shell.support.SupporterFile
import kotlinx.coroutines.Dispatchers
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

/**
 * The two on-or-off settings as switches (#416), as the board draws them, under one heading: the
 * session's sounds and the rest-day reminder.
 */
@Composable
internal fun Switches(
    preferences: Preferences,
    onSounds: (Boolean) -> Unit,
    onReminder: (Boolean) -> Unit,
) = Section(R.string.settings_switches) {
    SwitchRow(
        stringResource(R.string.settings_sounds),
        stringResource(R.string.settings_soundsNote),
        preferences.sessionSounds,
        onSounds,
    )
    SwitchRow(
        stringResource(R.string.settings_reminder),
        pluralText(R.plurals.settings_reminderNote, R.string.settings_reminderNote_zero, REST_DAY_REMINDER_DAYS),
        preferences.restDayReminder,
        onReminder,
    )
}

/**
 * Your file, one row (#416): everything that moves the library or the log in or out lives on the file
 * sheet since #413, so Settings keeps only the way there, named by the file it is about.
 */
@Composable
internal fun YourFile(open: () -> Unit) =
    Section(R.string.settings_yourFile) {
        ActionRow(LIBRARY_FILE_NAME, stringResource(R.string.settings_yourFileDetail), onClick = open)
    }

/**
 * Kettle: Support, then the two ways to reach the developer, as the board draws them in one block. The
 * supporter count is read from the file each time Settings opens, since native is its writer (#188).
 */
@Composable
internal fun KettleSection(
    supporter: SupporterFile,
    version: String?,
    contact: ContactActions,
    openSupport: () -> Unit,
) = Section(R.string.settings_kettle) {
    var tipped by remember { mutableStateOf(false) }
    LaunchedEffect(supporter) { tipped = withContext(Dispatchers.IO) { supporter.read().hasTipped } }
    ActionRow(
        stringResource(R.string.support_row),
        stringResource(if (tipped) R.string.support_rowDetailTipped else R.string.support_rowDetail),
        onClick = openSupport,
    )
    Contact(version, contact)
}

/**
 * The two ways to reach the developer. Neither collects anything: each hands off to an app the user
 * already has, and the only thing prefilled is the version, printed at the foot of this screen. Where
 * nothing can open the link, it goes to the clipboard, and the row says so, never claiming a copy the
 * clipboard refused.
 */
@Composable
private fun Contact(
    version: String?,
    contact: ContactActions,
) {
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

/** Constants, not locale keys: a translator who "fixed" either would break the only feedback channel. */
internal const val FEEDBACK_EMAIL = "kettleapp.feedback@gmail.com"
internal const val ISSUES_URL = "https://github.com/celsaad/kettle/issues"
