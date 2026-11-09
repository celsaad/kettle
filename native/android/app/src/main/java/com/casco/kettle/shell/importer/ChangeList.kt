package com.casco.kettle.shell.importer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.COLLAPSED_CHANGES
import com.casco.kettle.domain.ChangedItem
import com.casco.kettle.domain.DiffLabel
import com.casco.kettle.domain.DiffValue
import com.casco.kettle.domain.FieldChange
import com.casco.kettle.domain.ImportPreview
import com.casco.kettle.domain.LibraryItemKind
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.collapsed
import com.casco.kettle.shell.R
import com.casco.kettle.shell.exerciseeditor.label
import com.casco.kettle.shell.form.unitSuffix
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.TypeIcon

/** The tiles, the changed ids with what each updated one loses, and the note that updates replace. */
@Composable
internal fun PreviewSection(
    preview: ImportPreview,
    units: UnitSystem,
    importer: Importer,
) {
    val colors = KettleTheme.colors
    Row(Modifier.padding(top = Spacing.three - 2.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.two)) {
        CountTile("${preview.newCount}", stringResource(R.string.import_new), colors.accentSoft, colors.accentText)
        CountTile(
            "${preview.updatedCount}",
            stringResource(R.string.import_updated),
            colors.accentCalmSoft,
            colors.accentCalmText,
        )
        CountTile(
            "${preview.workoutCount}",
            pluralText(R.plurals.import_workoutNoun, R.string.import_workoutNoun_zero, preview.workoutCount),
            colors.backgroundElement,
            colors.text,
            bordered = true,
        )
    }
    ChangeList(preview.items, importer) { item ->
        // Only updates, and only what moved. A byte-identical update says so instead of an empty indent:
        // merge classifies by id, so re-importing your own export lands every item here.
        if (!item.isNew) ChangedFields(preview.changes(item, units), units)
    }
    Text(
        stringResource(R.string.import_updateNote),
        Modifier.padding(top = Spacing.two + 4.dp),
        style = KettleType.small,
        color = colors.textSecondary,
    )
}

/**
 * What landed, announced: the screen changes under the reader without focus moving, so TalkBack would
 * otherwise read none of it. A pane title rather than a live region: Compose sends a live region's
 * event only when its content changes, and this card is new, so on a device nothing was spoken. A pane
 * appearing is the event TalkBack reads for a section that wasn't there, and it speaks RN's own
 * announcement. The rows are the preview's, without the diffs, which describe a "before" the write has
 * already replaced.
 */
@Composable
internal fun Applied(
    items: List<ChangedItem>,
    importer: Importer,
) {
    val colors = KettleTheme.colors
    val new = items.count { it.isNew }
    val announcement = stringResource(R.string.import_applied_announcement, "$new", "${items.size - new}")
    Column(
        Modifier
            .padding(top = Spacing.three - 2.dp)
            .fillMaxWidth()
            .clip(BoxShape)
            .background(colors.accentSoft)
            .semantics(mergeDescendants = true) { paneTitle = announcement }
            .padding(Spacing.two + 4.dp),
    ) {
        Text(stringResource(R.string.import_applied_title), style = KettleType.heading, color = colors.accentText)
        Text(
            pluralText(R.plurals.import_applied_newCount, R.string.import_applied_newCount_zero, new) + " · " +
                pluralText(
                    R.plurals.import_applied_updatedCount,
                    R.string.import_applied_updatedCount_zero,
                    items.size - new,
                ),
            style = KettleType.small,
            color = colors.textSecondary,
        )
    }
    ChangeList(items, importer) {}
}

@Composable
private fun RowScope.CountTile(
    count: String,
    noun: String,
    fill: Color,
    tint: Color,
    bordered: Boolean = false,
) {
    val colors = KettleTheme.colors
    Column(
        Modifier
            .weight(1f)
            .clip(TileShape)
            .background(fill)
            .then(if (bordered) Modifier.border(1.dp, colors.border, TileShape) else Modifier)
            .padding(vertical = Spacing.two + 2.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(count, style = KettleType.subtitle, color = tint)
        Text(noun, style = KettleType.small, color = colors.textSecondary)
    }
}

/** The rows the preview and the result share, so the two can't drift apart on how a long list behaves. */
@Composable
private fun ChangeList(
    items: List<ChangedItem>,
    importer: Importer,
    under: @Composable (ChangedItem) -> Unit,
) {
    val colors = KettleTheme.colors
    Column(
        Modifier.padding(top = Spacing.three - 2.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.two - 1.dp),
    ) {
        for (item in items.collapsed(importer.showAll)) {
            Column {
                ChangedRow(item)
                under(item)
            }
        }
        if (items.isEmpty()) {
            Text(stringResource(R.string.import_noChanges), style = KettleType.small, color = colors.textSecondary)
        }
        if (items.size > COLLAPSED_CHANGES) ShowAllToggle(items.size, importer)
    }
}

@Composable
private fun ChangedRow(item: ChangedItem) {
    val colors = KettleTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.two + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Decoration: the noun at the end of the row says new or updated.
        Box(Modifier.width(MARK_WIDTH), contentAlignment = Alignment.Center) {
            TypeIcon(
                if (item.isNew) R.drawable.icon_add else R.drawable.icon_refresh,
                KettleType.default,
                if (item.isNew) colors.accentText else colors.accentCalmText,
            )
        }
        // The id, never a name: it's what the file the reader holds says.
        Text(item.id, Modifier.weight(1f), style = KettleType.smallMedium, color = colors.text)
        Text(stringResource(item.noun), style = KettleType.small, color = colors.textSecondary)
    }
}

private val ChangedItem.noun: Int
    get() =
        when (kind) {
            LibraryItemKind.Exercise -> if (isNew) R.string.import_newExercise else R.string.import_updatedExercise
            LibraryItemKind.Workout -> if (isNew) R.string.import_newWorkout else R.string.import_updatedWorkout
            LibraryItemKind.Program -> if (isNew) R.string.import_newProgram else R.string.import_updatedProgram
        }

/** Indented past the glyph column, so the lines read as part of the id above them. */
@Composable
private fun ChangedFields(
    changes: List<FieldChange>,
    units: UnitSystem,
) {
    val colors = KettleTheme.colors
    Column(
        Modifier.padding(top = 2.dp, start = MARK_WIDTH + Spacing.two + 2.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        for (change in changes) {
            Text(
                "${labelText(change.label)}: ${valueText(change.from, units)} → ${valueText(change.to, units)}",
                style = KettleType.small,
                color = colors.textSecondary,
            )
        }
        if (changes.isEmpty()) {
            Text(
                stringResource(R.string.import_diff_sameValues),
                style = KettleType.small,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun labelText(label: DiffLabel): String =
    stringResource(
        when (label) {
            DiffLabel.Name -> R.string.common_name
            DiffLabel.Type -> R.string.import_diff_type
            DiffLabel.Notes -> R.string.import_diff_notes
            DiffLabel.Blocks -> R.string.import_diff_blocks
            DiffLabel.Exercises -> R.string.import_diff_exercises
            DiffLabel.Weeks -> R.string.import_diff_weeks
            DiffLabel.WeeksEdited -> R.string.import_diff_weeksChanged
            is DiffLabel.Field -> label.name.label
        },
    )

@Composable
private fun valueText(
    value: DiffValue,
    units: UnitSystem,
): String =
    when (value) {
        DiffValue.Unset -> stringResource(R.string.import_diff_unset)
        is DiffValue.Text -> value.text
        is DiffValue.Type -> stringResource(value.type.label)
        is DiffValue.Measured -> listOfNotNull(value.number, unitSuffix(value.unit, units)).joinToString(" ")
    }

/**
 * "Show all 30 changes", the count of the whole list rather than of what's hidden: it answers "how long
 * is this" without making the reader add. Expand and collapse carry the state, as History's rows do.
 */
@Composable
private fun ShowAllToggle(
    total: Int,
    importer: Importer,
) {
    val colors = KettleTheme.colors
    val expanded = importer.showAll
    Box(
        Modifier
            .padding(top = Spacing.one + 2.dp)
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(TileShape)
            .border(1.dp, colors.border, TileShape)
            .clickable(role = Role.Button) { importer.showAll = !expanded }
            .semantics {
                if (expanded) {
                    collapse { true.also { importer.showAll = false } }
                } else {
                    expand { true.also { importer.showAll = true } }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (expanded) {
                stringResource(R.string.import_showFewer)
            } else {
                pluralText(R.plurals.import_showAll, R.string.import_showAll_zero, total)
            },
            style = KettleType.smallMedium,
            color = colors.textSecondary,
        )
    }
}

private val TileShape = RoundedCornerShape(12.dp)

private val MARK_WIDTH = 18.dp
