package com.casco.kettle.shell.sessioneditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.EntryField
import com.casco.kettle.domain.SetForm
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.canAddSet
import com.casco.kettle.domain.isZeroForm
import com.casco.kettle.domain.jsNumber
import com.casco.kettle.domain.wholeSeconds
import com.casco.kettle.shell.R
import com.casco.kettle.shell.form.DurationDialog
import com.casco.kettle.shell.form.FieldOptions
import com.casco.kettle.shell.form.FormField
import com.casco.kettle.shell.library.ExerciseBadge
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.list.TextAction
import com.casco.kettle.shell.runner.formatClock
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.workouteditor.RemoveButton

/**
 * One exercise as a table (#442): its name and type, a header row, a row per set, the set's number,
 * each value underlined to say a tap changes it, and its ×; then "+ Add a set" where it logs sets.
 */
@Composable
internal fun EntryTable(context: TableContext) {
    val colors = KettleTheme.colors
    Column(Modifier.padding(top = Spacing.four)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.two)) {
            // User data, verbatim. It takes the leftover width and ellipsizes rather than push the badge off.
            Text(
                context.name,
                Modifier.weight(1f),
                style = KettleType.heading,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ExerciseBadge(context.draft.entry)
        }
        HeaderRow(context)
        context.draft.forms.forEachIndexed { row, form -> SetRow(context, row, form) }
        if (context.draft.canAddSet) {
            TextAction(
                stringResource(R.string.sessionEditor_addSet),
                KettleType.smallMedium,
                colors.text,
                onClick = { context.editor.addSet(context.index) },
                // Every table has one: the exercise is what tells them apart.
                label = stringResource(R.string.sessionEditor_addSetTo, context.name),
            )
        }
    }
}

/** "Set | Reps | Load (lb) | Effort": a heading per column, and room where the × goes. */
@Composable
private fun HeaderRow(context: TableContext) {
    val colors = KettleTheme.colors
    Row(Modifier.padding(top = Spacing.two, bottom = Spacing.one), verticalAlignment = Alignment.CenterVertically) {
        if (context.perSet) {
            Text(
                stringResource(R.string.sessionEditor_colSet),
                Modifier.width(SET_WIDTH),
                style = KettleType.small,
                color = colors.textSecondary,
            )
        }
        for (field in context.fields) {
            Text(
                columnLabel(field, context.units),
                Modifier.weight(1f),
                style = KettleType.small,
                color = colors.textSecondary,
                textAlign = TextAlign.End,
            )
        }
        Box(Modifier.width(REMOVE_WIDTH))
    }
}

/** A hold's column is what was held, "Held"; any other is its field's name, with its unit. */
@Composable
private fun columnLabel(
    field: EntryField,
    units: UnitSystem,
): String = if (field == EntryField.Hold) stringResource(R.string.sessionEditor_colHeld) else fieldLabel(field, units)

/** One set: its number, its values, its ×, and the zero hint while saving would drop it. */
@Composable
private fun SetRow(
    context: TableContext,
    row: Int,
    form: SetForm,
) {
    val colors = KettleTheme.colors
    val setLabel = stringResource(R.string.sessionEditor_setLabel, row + 1)
    ListRowSeparator()
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        if (context.perSet) {
            Text("${row + 1}", Modifier.width(SET_WIDTH), style = KettleType.default, color = colors.textSecondary)
        }
        for (field in context.fields) Cell(context, row, form, field, setLabel)
        // Named by its set and its exercise, "Remove set 2 of Back Squat": every row has the same ×.
        val remove =
            if (context.perSet) {
                stringResource(R.string.sessionEditor_removeSetOf, "${row + 1}", context.name)
            } else {
                stringResource(R.string.sessionEditor_removeEntryOf, context.name)
            }
        Box(Modifier.width(REMOVE_WIDTH), contentAlignment = Alignment.CenterEnd) {
            RemoveButton(remove) { context.editor.remove(context.index, row) }
        }
    }
    // Typing 0 is how a set gets cleared, and it goes on save, so it's said here, while it applies.
    if (isZeroForm(context.draft.entry, form, context.units)) {
        Text(stringResource(R.string.sessionEditor_zeroRemoved), style = KettleType.small, color = colors.textSecondary)
    }
}

/**
 * One value. Shown, it's underlined, a control named "Set 2 · Reps, 12" that a tap edits; one cell
 * edits at a time. A hold opens the time picker, the others a field that closes when it loses focus.
 */
@Composable
private fun RowScope.Cell(
    context: TableContext,
    row: Int,
    form: SetForm,
    field: EntryField,
    setLabel: String,
) {
    val editor = context.editor
    val label = columnLabel(field, context.units)
    val name = if (context.perSet) setFieldName(setLabel, label) else label
    val value = form.values[field].orEmpty()
    val key = CellKey(context.index, row, field)
    val editing = editor.editingCell == key
    Box(Modifier.weight(1f).padding(start = Spacing.one), contentAlignment = Alignment.CenterEnd) {
        if (editing && field != EntryField.Hold) {
            InlineField(value, name, { editor.setField(context.index, row, field, it) }) { editor.stopEditing(key) }
        } else {
            ShownValue(
                shown(field, value),
                name,
                value.isBlank(),
            ) { editor.startEditing(key) }
        }
    }
    if (editing && field == EntryField.Hold) {
        val seconds = value.toDoubleOrNull() ?: 0.0
        DurationDialog(name, seconds, onDone = {
            editor.stopEditing(key)
            if (it != seconds) editor.setField(context.index, row, field, jsNumber(it))
        }, onDismiss = { editor.stopEditing(key) })
    }
}

/** A hold as the clock reads it, "0:45"; a blank optional value as a dash; anything else as typed. */
private fun shown(
    field: EntryField,
    value: String,
): String =
    when {
        value.isBlank() -> BLANK
        field == EntryField.Hold -> value.toDoubleOrNull()?.let { formatClock(wholeSeconds(it).toDouble()) } ?: value
        else -> value
    }

/** The value, dotted-underlined, the whole cell its target. Its name says the value, or "none". */
@Composable
private fun ShownValue(
    text: String,
    name: String,
    blank: Boolean,
    onTap: () -> Unit,
) {
    val colors = KettleTheme.colors
    val description =
        stringResource(
            R.string.sessionEditor_cellValue,
            name,
            if (blank) stringResource(R.string.sessionEditor_noValue) else text,
        )
    Box(
        Modifier
            // The whole cell, not just its digits: a "5" alone is a target a finger misses.
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onTap)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text,
            Modifier.drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(
                    colors.textSecondary,
                    Offset(0f, y),
                    Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())),
                )
            },
            style = KettleType.default,
            color = if (blank) colors.textSecondary else colors.text,
            textAlign = TextAlign.End,
        )
    }
}

/** The cell as a field, focused at once, done when focus leaves it: the next cell tapped, or the keyboard closed. */
@Composable
private fun InlineField(
    value: String,
    name: String,
    onChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // Not state: whether focus has arrived yet. The field starts unfocused, and that first report isn't
    // a leaving; only a loss after it had focus closes it.
    val focused = remember { booleanArrayOf(false) }
    Box(
        Modifier.onFocusChanged {
            if (it.hasFocus) {
                focused[0] = true
            } else if (focused[0]) {
                onDone()
            }
        },
    ) {
        FormField(value, onChange, name = name, placeholder = BLANK, FieldOptions(minHeight = 44.dp, focus = focus))
    }
}

private val SET_WIDTH = 36.dp
private val REMOVE_WIDTH = 44.dp

/** A blank value's dash, as the log's own tables write none: a placeholder, not an icon, so read as "none". */
private const val BLANK = "\u2013"
