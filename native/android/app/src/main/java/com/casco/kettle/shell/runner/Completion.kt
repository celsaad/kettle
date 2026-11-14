package com.casco.kettle.shell.runner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.ExerciseAgainstLast
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionRecord
import com.casco.kettle.domain.againstLastTime
import com.casco.kettle.domain.sessionDurationMinutes
import com.casco.kettle.domain.sessionRecords
import com.casco.kettle.domain.sessionSetCount
import com.casco.kettle.domain.withoutZeroWork
import com.casco.kettle.shell.R
import com.casco.kettle.shell.history.entryResultText
import com.casco.kettle.shell.home.weekLabel
import com.casco.kettle.shell.list.ListRowMinHeight
import com.casco.kettle.shell.list.ListRowVerticalPadding
import com.casco.kettle.shell.progress.Readings
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.TypeIcon

/**
 * Completion (#416), a receipt: which day this was, the session's three numbers, each exercise against
 * last time with a PR inline where it beat everything before it, what the program has next, and where
 * the backup went, with Done and Edit log pinned under it. The backup's failure keeps its one line.
 */
@Composable
internal fun Completion(
    view: RunnerView.Completed,
    environment: RunnerEnvironment,
    actions: RunnerActions,
) {
    val session = view.session
    // Zero work out first, as every training view reads the log: logging 0 is how the runner skips, and
    // a skipped 60 kg × 0 is no result, let alone a PR. The log is reread on every write, so the finished
    // session is in it: both selectors skip it.
    val logged = remember(session) { withoutZeroWork(session) }
    val records =
        remember(logged, environment.priorSessions) {
            sessionRecords(logged, environment.priorSessions, view.plan.exercises)
        }
    val rows =
        remember(logged, environment.priorSessions) {
            againstLastTime(logged, environment.priorSessions, view.plan.exercises)
        }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = Spacing.four),
            verticalArrangement = Arrangement.spacedBy(Spacing.three),
        ) {
            Heading(view)
            Tiles(logged, exercises = rows.size)
            if (rows.isNotEmpty()) AgainstLastTime(rows, records, environment)
            NextInProgramLine(view, environment)
            Backup(view.backup, environment.backupFolder)
        }
        Pinned(onEditLog = { actions.editLog(session.id) }, onDone = actions.done)
    }
}

/** "Week 3 · Day 5 done" for a program day, "Workout complete" otherwise, then the workout's name. */
@Composable
private fun Heading(view: RunnerView.Completed) {
    val session = view.session
    val week = session.programWeek
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.one)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.one), verticalAlignment = Alignment.CenterVertically) {
            TypeIcon(R.drawable.icon_check, KettleType.smallMedium, runnerColors.text)
            Text(
                if (week != null) {
                    stringResource(R.string.session_complete_dayDone, weekLabel(week, session.programDay))
                } else {
                    stringResource(R.string.session_complete_title)
                },
                style = KettleType.smallMedium,
                color = runnerColors.textSecondary,
            )
        }
        Text(
            LocalResources.current.sessionName(view.plan.workout?.name),
            Modifier.semantics { heading() },
            style = KettleType.subtitle,
            color = runnerColors.text,
        )
    }
}

/** Time, Sets and Exercises in a ruled row, as Overview's tiles. */
@Composable
private fun Tiles(
    session: Session,
    exercises: Int,
) {
    Column {
        Rule()
        Row(Modifier.height(IntrinsicSize.Min)) {
            Tile(
                stringResource(R.string.format_minutes, sessionDurationMinutes(session)),
                stringResource(R.string.session_complete_time),
            )
            Divider()
            Tile(
                sessionSetCount(session).toString(),
                stringResource(R.string.session_complete_sets),
                Modifier.padding(start = Spacing.three),
            )
            Divider()
            Tile(
                exercises.toString(),
                stringResource(R.string.session_complete_exercises),
                Modifier.padding(start = Spacing.three),
            )
        }
        Rule()
    }
}

@Composable
private fun RowScope.Tile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) = Column(
    Modifier
        .weight(1f)
        .padding(vertical = Spacing.two)
        .then(modifier)
        .semantics(mergeDescendants = true) {},
) {
    Text(value, style = KettleType.heading, color = runnerColors.text)
    Text(label, style = KettleType.small, color = runnerColors.textSecondary)
}

@Composable
private fun Rule() = Box(Modifier.fillMaxWidth().height(1.dp).background(runnerColors.outline))

@Composable
private fun Divider() = Box(Modifier.fillMaxHeight().width(1.dp).background(runnerColors.outline))

/** One row per exercise, then the 1RM note once, only where a 1RM shows. */
@Composable
private fun AgainstLastTime(
    rows: List<ExerciseAgainstLast>,
    records: List<SessionRecord>,
    environment: RunnerEnvironment,
) = Column {
    Text(
        stringResource(R.string.session_complete_againstLastTime),
        Modifier.padding(bottom = Spacing.one).semantics { heading() },
        style = KettleType.smallMedium,
        color = runnerColors.textSecondary,
    )
    rows.forEach { row ->
        // The record on the measure the row shows: one exercise can set a weight PR and a reps one.
        ComparisonRow(row, records.firstOrNull { it.exerciseId == row.exerciseId && it.kind == row.kind }, environment)
    }
    if (records.any { it is SessionRecord.HeaviestSet && it.oneRepMaxKg != null }) {
        Text(
            stringResource(R.string.session_complete_oneRepMaxNote),
            Modifier.padding(top = Spacing.two),
            style = KettleType.small,
            color = runnerColors.textSecondary,
        )
    }
}

/**
 * An exercise: its name with a PR badge where it beat everything before it, today's numbers, the
 * estimated 1RM under a heaviest-set PR, and the change since last time on the right. One node for a
 * screen reader, which hears the signed change ("+5 kg") the drawn ▲ stands for.
 */
@Composable
private fun ComparisonRow(
    row: ExerciseAgainstLast,
    record: SessionRecord?,
    environment: RunnerEnvironment,
) {
    val resources = LocalResources.current
    val units = environment.units
    val readings = row.kind?.let { Readings(resources, it, units) }
    val today = resources.entryResultText(row.result)
    val oneRepMax = (record as? SessionRecord.HeaviestSet)?.oneRepMaxKg?.let { resources.oneRepMaxText(it, units) }
    val (shown, spoken) = changeTexts(row, readings, resources)
    val badge = record?.let { stringResource(R.string.session_complete_recordBadge) }
    val description = listOfNotNull(row.exerciseName, badge, today, oneRepMax, spoken).joinToString(", ")
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Rule()
        // The list rows' own metrics: a list of peers is rows, on the runner's field as in the shell.
        Row(
            Modifier.fillMaxWidth().heightIn(min = ListRowMinHeight).padding(vertical = ListRowVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(Spacing.two),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.half)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.two),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.exerciseName,
                        Modifier.weight(1f, fill = false),
                        style = KettleType.heading,
                        color = runnerColors.text,
                    )
                    if (badge != null) RecordBadge()
                }
                Text(today, style = KettleType.small, color = runnerColors.textSecondary)
                oneRepMax?.let { Text(it, style = KettleType.small, color = runnerColors.textSecondary) }
            }
            shown?.let { Text(it, style = KettleType.smallMedium, color = runnerColors.text) }
        }
    }
}

/** Edit log and Done, pinned under the receipt: Done is the one filled, the way back to Today. */
@Composable
private fun Pinned(
    onEditLog: () -> Unit,
    onDone: () -> Unit,
) = Column {
    Rule()
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.three),
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
    ) {
        OutlineButton(stringResource(R.string.session_complete_editLog), onEditLog)
        PrimaryButton(stringResource(R.string.session_complete_done), onDone, Modifier.weight(1f))
    }
}
