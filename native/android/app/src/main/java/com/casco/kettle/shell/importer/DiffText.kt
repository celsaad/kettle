package com.casco.kettle.shell.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.DiffLabel
import com.casco.kettle.domain.DiffValue
import com.casco.kettle.domain.FieldChange
import com.casco.kettle.domain.FieldUnit
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.shell.R
import com.casco.kettle.shell.exerciseeditor.label
import com.casco.kettle.shell.form.unitSuffix
import com.casco.kettle.shell.i18n.equipmentText
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.TypeIcon

// What an updated item's diff says (#443): a row per field, "Most reps 12 → 15", and a field the update
// leaves out as "cleared", marked with the warning glyph so it reads with a shape and not only words.

/** One updated item's fields, or a line saying it is replaced with an identical definition. */
@Composable
internal fun DiffRows(
    changes: List<FieldChange>,
    units: UnitSystem,
) {
    val colors = KettleTheme.colors
    Column(
        Modifier.padding(top = Spacing.one, bottom = Spacing.one),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (change in changes) DiffRow(change, units)
        // Merge classifies by id, so re-importing your own export lands every item here, unchanged.
        if (changes.isEmpty()) {
            Text(
                stringResource(R.string.import_diff_sameValues),
                style = KettleType.small,
                color = colors.textSecondary,
            )
        }
    }
}

/** "Target weight  35 lb → cleared", read as "Target weight: was 35 lb, now cleared". */
@Composable
private fun DiffRow(
    change: FieldChange,
    units: UnitSystem,
) {
    val colors = KettleTheme.colors
    val label = labelText(change.label)
    val was = valueText(change.from, units)
    val cleared = change.clears
    val clearedWord = stringResource(R.string.import_diff_cleared)
    // "cleared" where a value goes; a list that goes keeps its — on screen, the warning mark saying
    // the rest. TalkBack can't hear the mark, so it hears "cleared" for both.
    val now =
        when (change.to) {
            DiffValue.Unset -> clearedWord
            else -> valueText(change.to, units)
        }
    val spoken = if (cleared) clearedWord else now
    val description = stringResource(R.string.import_diff_rowAccessibility, label, spoken, was)
    Row(
        Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.one),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = KettleType.small, color = colors.textSecondary)
        Text("$was → $now", style = KettleType.small, color = colors.text)
        if (cleared) TypeIcon(R.drawable.icon_warning, KettleType.small, colors.accentText)
    }
}

@Composable
internal fun labelText(label: DiffLabel): String =
    stringResource(
        when (label) {
            DiffLabel.Name -> R.string.common_name
            DiffLabel.Type -> R.string.import_diff_type
            DiffLabel.Notes -> R.string.import_diff_notes
            DiffLabel.Equipment -> R.string.import_diff_equipment
            DiffLabel.Blocks -> R.string.import_diff_blocks
            DiffLabel.Exercises -> R.string.import_diff_exercises
            DiffLabel.Weeks -> R.string.import_diff_weeks
            DiffLabel.WeeksEdited -> R.string.import_diff_weeksChanged
            DiffLabel.BlocksEdited -> R.string.import_diff_blocksEdited
            DiffLabel.WeeksAdded -> R.string.import_diff_weeksAdded
            DiffLabel.WeeksRemoved -> R.string.import_diff_weeksRemoved
            DiffLabel.WeeksLosing -> R.string.import_diff_weeksLosing
            is DiffLabel.Field -> label.name.label
        },
    )

@Composable
internal fun valueText(
    value: DiffValue,
    units: UnitSystem,
): String =
    when (value) {
        DiffValue.Unset -> stringResource(R.string.import_diff_unset)
        is DiffValue.Text -> value.text
        is DiffValue.Type -> stringResource(value.type.label)
        is DiffValue.Measured -> measuredText(value.number, value.unit, units)
        is DiffValue.Items -> itemsText(value.items)
    }

/** Equipment items, each known one in the reader's language and the rest as written, as a list reads. */
@Composable
private fun itemsText(items: List<String>): String {
    val separator = stringResource(R.string.format_listSeparator)
    return items.map { equipmentText(it) }.joinToString(separator)
}

/**
 * A value with its unit, as the rest of the app writes one ("90 s", "5 min", "400 m"), not with a
 * field label's suffix: the locale decides the spacing, and ja writes 90秒 with no space before it.
 */
@Composable
internal fun measuredText(
    number: String,
    unit: FieldUnit,
    units: UnitSystem,
): String =
    when (unit) {
        FieldUnit.Weight -> stringResource(R.string.format_weight, unitSuffix(unit, units).orEmpty(), number)
        FieldUnit.Seconds -> stringResource(R.string.format_seconds, number)
        FieldUnit.Minutes -> stringResource(R.string.format_minutes, number)
        FieldUnit.Metres -> stringResource(R.string.format_metres, number)
    }
