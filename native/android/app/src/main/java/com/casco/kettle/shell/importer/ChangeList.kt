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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.COLLAPSED_CHANGES
import com.casco.kettle.domain.ChangedItem
import com.casco.kettle.domain.ImportPreview
import com.casco.kettle.domain.LibraryItemKind
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.collapsed
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.list.ListRowMinHeight
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.theme.Chevron
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.TypeIcon

/**
 * The review (#443): "Review the changes", the three tiles, the note that updates replace whole, then
 * what's updated, each opening to its field diff, and what's new. Updated first: it's the half that can
 * take something away.
 */
@Composable
internal fun PreviewSection(
    preview: ImportPreview,
    units: UnitSystem,
    importer: Importer,
) {
    val colors = KettleTheme.colors
    // Not remembered here: the preview diffs each item once, and this is a sum over its answers.
    val cleared = preview.clearedTotal(units)
    Text(
        stringResource(R.string.import_reviewTitle),
        Modifier.padding(top = Spacing.three).semantics { heading() },
        style = KettleType.heading,
        color = colors.text,
    )
    Row(Modifier.padding(top = Spacing.two), horizontalArrangement = Arrangement.spacedBy(Spacing.two)) {
        CountTile(preview.newCount, stringResource(R.string.import_new), colors.accentText)
        CountTile(preview.updatedCount, stringResource(R.string.import_updated), colors.accentCalmText)
        CountTile(
            cleared,
            pluralText(R.plurals.import_clearedTile, R.string.import_clearedTile_zero, cleared),
            if (cleared > 0) colors.accentText else colors.text,
            warn = cleared > 0,
        )
    }
    Text(
        stringResource(R.string.import_updateNote),
        Modifier.padding(top = Spacing.two),
        style = KettleType.small,
        color = colors.textSecondary,
    )
    ChangeList(preview, importer, units) { item ->
        if (!item.isNew) {
            // Asked per row drawn, open or folded: a folded row still says what it clears.
            DiffRows(preview.changes(item, units), units)
        }
    }
}

/**
 * What landed, announced: the screen changes under the reader without focus moving, so TalkBack would
 * otherwise read none of it. A pane title rather than a live region: Compose sends a live region's
 * event only when its content changes, and this card is new, so on a device nothing was spoken. A pane
 * appearing is the event TalkBack reads for a section that wasn't there, and it speaks RN's own
 * announcement. The rows are the review's, without the diffs, which describe a "before" the write has
 * already replaced.
 */
@Composable
internal fun Applied(
    preview: ImportPreview,
    importer: Importer,
) {
    val colors = KettleTheme.colors
    val new = preview.newCount
    val updated = preview.updatedCount
    val announcement = stringResource(R.string.import_applied_announcement, "$new", "$updated")
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
                pluralText(R.plurals.import_applied_updatedCount, R.string.import_applied_updatedCount_zero, updated),
            style = KettleType.small,
            color = colors.textSecondary,
        )
    }
    ChangeList(preview, importer, units = null, under = null)
}

/**
 * A count and its noun. The fields-cleared one, when there are any, is outlined with the warning glyph:
 * a shape, so it isn't told only by its colour.
 */
@Composable
private fun RowScope.CountTile(
    count: Int,
    noun: String,
    tint: Color,
    warn: Boolean = false,
) {
    val colors = KettleTheme.colors
    Column(
        Modifier
            .weight(1f)
            .clip(TileShape)
            .background(colors.backgroundElement)
            .border(1.dp, if (warn) colors.accentText else colors.border, TileShape)
            .padding(vertical = Spacing.two + 2.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.one), verticalAlignment = Alignment.CenterVertically) {
            if (warn) TypeIcon(R.drawable.icon_warning, KettleType.default, tint)
            Text("$count", style = KettleType.subtitle, color = tint)
        }
        Text(noun, style = KettleType.small, color = colors.textSecondary)
    }
}

/**
 * The items, updated then new, each under its heading, the first few until "Show all": the review's
 * and the result's, so the two can't drift on how a long list behaves. [under] draws an updated item's
 * diff when it's opened; the result has none, the "before" being gone.
 */
@Composable
private fun ChangeList(
    preview: ImportPreview,
    importer: Importer,
    /** The reader's units, for what an update clears; none for the result, whose "before" is gone. */
    units: UnitSystem?,
    under: (@Composable (ChangedItem) -> Unit)?,
) {
    val colors = KettleTheme.colors
    val ordered = preview.items.filterNot { it.isNew } + preview.items.filter { it.isNew }
    // Each section cut on its own, so a run of updates can't push "New" out of sight behind Show all.
    val shown = ordered.groupBy { it.isNew }.values.flatMap { it.collapsed(importer.showAll) }
    val open = remember(preview) { mutableStateMapOf<ChangedItem, Boolean>() }
    Column(Modifier.padding(top = Spacing.two)) {
        for ((isNew, items) in shown.groupBy { it.isNew }) {
            Text(
                stringResource(if (isNew) R.string.import_sectionNew else R.string.import_sectionUpdated),
                Modifier.padding(top = Spacing.three, bottom = Spacing.one).semantics { heading() },
                style = KettleType.heading,
                color = colors.text,
            )
            for (item in items) {
                val diff = under?.takeIf { !item.isNew }
                val clears =
                    units?.takeIf { !item.isNew }?.let {
                        preview.clearedBy(item, it)
                    } ?: 0
                ItemRow(
                    preview,
                    item,
                    open[item] != false,
                    clears,
                    onToggle =
                        diff?.let {
                            {
                                open[item] =
                                    open[item] == false
                            }
                        },
                )
                if (diff != null && open[item] != false) diff(item)
            }
        }
        if (ordered.isEmpty()) {
            Text(stringResource(R.string.import_noChanges), style = KettleType.small, color = colors.textSecondary)
        }
        if (ordered.groupBy { it.isNew }.values.any { it.size > COLLAPSED_CHANGES }) {
            ShowAllToggle(
                ordered.size,
                importer,
            )
        }
    }
}

/**
 * One item: its name, the user's, over "db-row · exercise", what the file says. An update opens on its
 * diff, the review being for what changes, and folds on a tap; folded, it still says what it clears.
 */
@Composable
private fun ItemRow(
    preview: ImportPreview,
    item: ChangedItem,
    open: Boolean,
    clears: Int,
    onToggle: (() -> Unit)?,
) {
    val colors = KettleTheme.colors
    ListRowSeparator()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ListRowMinHeight)
            .then(
                if (onToggle == null) {
                    Modifier
                } else {
                    Modifier.clickable(role = Role.Button, onClick = onToggle).semantics {
                        if (open) collapse { true.also { onToggle() } } else expand { true.also { onToggle() } }
                    }
                },
            ).padding(vertical = Spacing.one),
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(preview.name(item), style = KettleType.heading, color = colors.text)
            Text(
                stringResource(R.string.import_itemSub, item.id, stringResource(item.kindLabel)),
                style = KettleType.small,
                color = colors.textSecondary,
            )
            if (clears > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.half),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TypeIcon(R.drawable.icon_warning, KettleType.small, colors.accentText)
                    Text(
                        pluralText(R.plurals.import_clearsCount, R.string.import_clearsCount_zero, clears),
                        style = KettleType.small,
                        color = colors.accentText,
                    )
                }
            }
        }
        if (onToggle != null) Chevron(expanded = open)
    }
}

private val ChangedItem.kindLabel: Int
    get() =
        when (kind) {
            LibraryItemKind.Exercise -> R.string.import_kind_exercise
            LibraryItemKind.Workout -> R.string.import_kind_workout
            LibraryItemKind.Program -> R.string.import_kind_program
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
