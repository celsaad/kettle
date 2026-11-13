package com.casco.kettle.shell.runner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.EndRow
import com.casco.kettle.domain.EndUnit
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * End early (#417), as the canvas draws it: a sheet saying what's saved, each exercise's progress in its
 * own unit ([EndRow]), "Keep going" the main action and "Save and end" the quiet one, so the wide button
 * a hand lands on keeps the session. No one total heads the rows, as the canvas's "5 of 12 sets" does: a
 * workout mixing sets, rounds and minutes has no unit to sum them in.
 *
 * The rows are a summary to read, not peers to act on, so they're a compact table between two hairlines
 * rather than [com.casco.kettle.shell.list.ListRow]s, which would make a long workout's sheet a scroll.
 */
@Composable
internal fun EndSheet(
    rows: List<EndRow>,
    onKeepGoing: () -> Unit,
    onEnd: () -> Unit,
) {
    val title = stringResource(R.string.session_end_title)
    // The backdrop still closes it, but unnamed: Keep going below is the same way out, and a reader
    // would otherwise meet two controls of one name.
    OverlaySheet(null, onKeepGoing, title) {
        Column {
            Text(title, Modifier.semantics { heading() }, style = KettleType.heading, color = runnerColors.text)
            Text(
                stringResource(R.string.session_end_body),
                Modifier.padding(top = Spacing.one),
                style = KettleType.small,
                color = runnerColors.textSecondary,
            )
        }
        if (rows.isNotEmpty()) {
            Column {
                RunnerHairline()
                Column(
                    Modifier
                        .heightIn(
                            max = ROWS_MAX_HEIGHT,
                        ).verticalScroll(rememberScrollState())
                        .padding(vertical = Spacing.one),
                ) {
                    rows.forEach { ProgressRow(it) }
                }
                RunnerHairline()
            }
        }
        PrimaryButton(stringResource(R.string.session_end_keepGoing), onKeepGoing, Modifier.fillMaxWidth())
        OutlineButton(
            stringResource(R.string.session_end_saveAndEnd),
            onEnd,
            Modifier.fillMaxWidth(),
            look = ButtonLook.Compact,
        )
    }
}

/** One exercise: its name, verbatim, and how far it got. Read as one line. */
@Composable
private fun ProgressRow(row: EndRow) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = ROW_PAD).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
    ) {
        Text(row.name, Modifier.weight(1f), style = KettleType.small, color = runnerColors.text)
        Text(progressText(row), style = KettleType.small, color = runnerColors.textSecondary)
    }
}

@Composable
private fun progressText(row: EndRow): String =
    when {
        row.done == 0 -> stringResource(R.string.session_end_notStarted)
        row.unit == EndUnit.ONCE -> stringResource(R.string.session_end_done)
        else -> pluralText(plural(row.unit), zero(row.unit), row.total, "${row.done}")
    }

private fun plural(unit: EndUnit): Int =
    when (unit) {
        EndUnit.SETS, EndUnit.ONCE -> R.plurals.session_end_sets
        EndUnit.ROUNDS -> R.plurals.session_end_rounds
        EndUnit.MINUTES -> R.plurals.session_end_minutes
        EndUnit.INTERVALS -> R.plurals.session_end_intervals
    }

private fun zero(unit: EndUnit): Int =
    when (unit) {
        EndUnit.SETS, EndUnit.ONCE -> R.string.session_end_sets_zero
        EndUnit.ROUNDS -> R.string.session_end_rounds_zero
        EndUnit.MINUTES -> R.string.session_end_minutes_zero
        EndUnit.INTERVALS -> R.string.session_end_intervals_zero
    }

private val ROWS_MAX_HEIGHT = 240.dp
private val ROW_PAD = 4.dp
