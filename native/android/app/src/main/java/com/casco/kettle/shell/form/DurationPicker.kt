package com.casco.kettle.shell.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.canAddMinute
import com.casco.kettle.domain.durationParts
import com.casco.kettle.domain.steppingMinutes
import com.casco.kettle.domain.steppingSeconds
import com.casco.kettle.domain.wholeSeconds
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.runner.formatClock
import com.casco.kettle.shell.theme.DialogAction
import com.casco.kettle.shell.theme.KettleAlertDialog
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.TypeIcon

/**
 * A length of time as a button (#440), "2:00", opening the picker that sets it: the workout editor's
 * rests, and the exercise editor's (#441). Named for a screen reader in words, "Rest length, 2
 * minutes. Change", since "2:00" read aloud is a time of day.
 */
@Composable
internal fun DurationButton(
    label: String,
    seconds: Double,
    onChange: (Double) -> Unit,
) {
    val colors = KettleTheme.colors
    // Saveable: the button sits in a lazy list item, disposed while scrolled out of view.
    var open by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(R.string.durationPicker_change, label, spokenDuration(seconds))
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(colors.backgroundElement)
            .border(1.dp, colors.border, shape)
            .clickable(role = Role.Button) { open = true }
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = Spacing.two),
        horizontalArrangement = Arrangement.spacedBy(Spacing.one),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(clockText(seconds), style = KettleType.heading, color = colors.text)
        TypeIcon(R.drawable.icon_expand, KettleType.small, colors.textSecondary)
    }
    if (open) {
        DurationDialog(label, seconds, onDone = {
            open = false
            // Done on an untouched length writes nothing: a circuit's absent rest isn't turned into a 0.
            if (it != seconds) onChange(it)
        }) { open = false }
    }
}

/**
 * The picker: the length large, then minutes and seconds on steppers of their own, the seconds by five
 * and wrapping within the minute. Nothing changes until Done, so Cancel and back leave it as it was.
 */
@Composable
internal fun DurationDialog(
    title: String,
    initial: Double,
    onDone: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var seconds by rememberSaveable { mutableDoubleStateOf(initial) }
    KettleAlertDialog(
        title = title,
        onDismissRequest = onDismiss,
        confirm = DialogAction(stringResource(R.string.common_done)) { onDone(seconds) },
        dismiss = DialogAction(stringResource(R.string.common_cancel), onDismiss),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.two)) {
            Text(
                clockText(seconds),
                Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = KettleType.title,
                color = KettleTheme.colors.text,
                textAlign = TextAlign.Center,
            )
            val (minutes, rest) = durationParts(seconds)
            PartStepper(stringResource(R.string.durationPicker_minutes), "$minutes", canAddMinute(seconds)) {
                seconds =
                    steppingMinutes(seconds, it)
            }
            PartStepper(stringResource(R.string.durationPicker_seconds), "$rest".padStart(2, '0'), canAdd = true) {
                seconds = steppingSeconds(seconds, it)
            }
        }
    }
}

/** "Minutes  − 2 +": the part's name, and its value between the app's own stepper buttons. */
@Composable
private fun PartStepper(
    label: String,
    value: String,
    canAdd: Boolean,
    step: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = KettleType.default, color = KettleTheme.colors.text)
        StepButton(R.drawable.icon_remove, stringResource(R.string.common_decrease, label)) { step(-1) }
        Text(
            value,
            Modifier.widthIn(min = 48.dp),
            style = KettleType.heading,
            color = KettleTheme.colors.text,
            textAlign = TextAlign.Center,
        )
        StepButton(R.drawable.icon_add, stringResource(R.string.common_increase, label), canAdd) { step(+1) }
    }
}

/**
 * A length as the clock shows it, to the whole second the steppers and the spoken length round to: a
 * file's 2.5 s would otherwise show 0:02 and read as 3 seconds.
 */
private fun clockText(seconds: Double): String = formatClock(wholeSeconds(seconds).toDouble())

/** "2 minutes 30 seconds", "45 seconds", or "2 minutes": a length as words, for a screen reader. */
@Composable
internal fun spokenDuration(seconds: Double): String {
    val (minutes, rest) = durationParts(seconds)
    val parts =
        listOfNotNull(
            minutes.takeIf { it > 0 }?.let {
                pluralText(R.plurals.durationPicker_minutesSpoken, R.string.durationPicker_minutesSpoken_zero, it)
            },
            rest.takeIf { it > 0 || minutes == 0 }?.let {
                pluralText(R.plurals.durationPicker_secondsSpoken, R.string.durationPicker_secondsSpoken_zero, it)
            },
        )
    return parts.joinToString(" ")
}
