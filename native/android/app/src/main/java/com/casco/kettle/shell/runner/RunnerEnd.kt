package com.casco.kettle.shell.runner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casco.kettle.domain.SessionRecord
import com.casco.kettle.domain.sessionRecords
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.RunnerColors
import com.casco.kettle.shell.theme.Spacing

// Either end of a session: the count-in before it, and the completion screen after.

/**
 * GET READY, the workout's name as the headline and the count as its caption: the name is what a
 * reader checks here. The hint shows only before the first logged session, and only with sounds on.
 */
@Composable
internal fun CountIn(
    view: RunnerView.CountingIn,
    environment: RunnerEnvironment,
    now: Long,
) {
    val resources = LocalResources.current
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.two, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Caps(stringResource(R.string.session_countdown_getReady))
        Text(
            resources.sessionName(view.plan.workout?.name),
            style = KettleType.subtitle,
            color = RunnerColors.text,
            textAlign = TextAlign.Center,
        )
        Numeral("${view.countIn.numeral(now)}", COUNT_NUMERAL, color = RunnerColors.accent, leading = COUNT_LEADING)
        if (environment.priorSessions.isEmpty() && environment.soundsOn) {
            Text(
                stringResource(R.string.session_countdown_firstTimeHint),
                style = KettleType.small,
                color = RunnerColors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Completion (§3.8): the name, what the session beat, the 1RM note once and only when a 1RM shows,
 * and one line when the backup copy failed, pointing to Settings: never a dialog.
 */
@Composable
internal fun Completion(
    view: RunnerView.Completed,
    environment: RunnerEnvironment,
    onDone: () -> Unit,
) {
    val resources = LocalResources.current
    // The log is reread on every write, so the finished session is in it: sessionRecords skips it.
    val records = sessionRecords(view.session, environment.priorSessions, view.plan.exercises)
    CenteredColumn {
        Box(
            Modifier
                .padding(bottom = Spacing.one)
                .size(BADGE)
                .softPill(RunnerColors.accent, RunnerColors.accentSoft)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", style = KettleType.title.copy(fontSize = CHECK, lineHeight = CHECK), color = RunnerColors.accent)
        }
        Text(
            stringResource(R.string.session_complete_title),
            Modifier.semantics { heading() },
            style = KettleType.title,
            color = RunnerColors.text,
            textAlign = TextAlign.Center,
        )
        Secondary(resources.sessionName(view.plan.workout?.name))
        if (records.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = Spacing.three),
                verticalArrangement = Arrangement.spacedBy(Spacing.two),
            ) {
                for (record in records) RecordCard(record, environment)
            }
        }
        if (records.any { it is SessionRecord.HeaviestSet && it.oneRepMaxKg != null }) {
            Notes(stringResource(R.string.session_complete_oneRepMaxNote), Modifier.padding(top = Spacing.half))
        }
        if (view.backup == FinishBackupState.FAILED) {
            Text(
                stringResource(R.string.session_complete_backupFailed),
                Modifier.padding(top = Spacing.two),
                style = KettleType.small,
                color = RunnerColors.accentCalmOnSoft,
                textAlign = TextAlign.Center,
            )
        }
        // Compact rather than full width, as RN's: one choice, and nothing left to weigh it against.
        CompactButton(stringResource(R.string.session_complete_done), onDone, Modifier.padding(top = Spacing.four))
    }
}

@Composable
private fun Notes(
    text: String,
    modifier: Modifier,
) {
    Text(text, modifier, style = KettleType.small, color = RunnerColors.textSecondary, textAlign = TextAlign.Center)
}

@Composable
private fun RecordCard(
    record: SessionRecord,
    environment: RunnerEnvironment,
) {
    val resources = LocalResources.current
    Column(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .runnerCard(16.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.half),
    ) {
        Row(
            Modifier.padding(bottom = Spacing.half),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.two),
        ) {
            RecordBadge()
            Text(record.exerciseName, style = KettleType.smallMedium, color = RunnerColors.textSecondary, maxLines = 1)
        }
        Text(resources.recordText(record, environment.units), style = KettleType.heading, color = RunnerColors.text)
        (record as? SessionRecord.HeaviestSet)?.oneRepMaxKg?.let {
            Secondary(resources.oneRepMaxText(it, environment.units))
        }
    }
}

private val COUNT_NUMERAL = 56.sp

/** RN's count sets 64 on 56: a looser line than the other numerals' solid one. */
private const val COUNT_LEADING = 64f / 56f
private val BADGE = 88.dp
private val CHECK = 44.sp

/**
 * Centred down the screen and scrolling once it can't fit, as RN's `justifyContent: 'center'`: the
 * minimum height is what gives a scrolling column something to centre in.
 */
@Composable
internal fun CenteredColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(vertical = Spacing.five),
            verticalArrangement = Arrangement.spacedBy(Spacing.two, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}
