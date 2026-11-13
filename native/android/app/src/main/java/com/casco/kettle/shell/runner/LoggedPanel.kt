package com.casco.kettle.shell.runner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.LoggedUnit
import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * "Logged set 1 · 10 × 48.5 lb" with Undo beside it: a tinted panel, shown only straight after a commit,
 * so Undo is never beside Back for the same step. Undo is the engine's Prev, which takes back exactly
 * what this names and moves to the step it came from.
 */
@Composable
internal fun LoggedPanel(
    logged: LoggedUnit,
    context: StepContext,
) = LoggedPanel(LocalResources.current.loggedText(logged, context.units)) { context.send(RunnerCommand.Prev) }

/** The panel over [logged]'s words, with [undo] for Undo: an AMRAP's round takes back only its count. */
@Composable
internal fun LoggedPanel(
    logged: String,
    undo: () -> Unit,
) {
    Row(
        Modifier
            .clip(PanelShape)
            .background(runnerColors.backgroundElement)
            // The pill 8dp in from the top, the bottom and the right alike, with the target's own 2dp of
            // slack: at 4dp its outline all but met the panel's rounded corner on the S25+.
            .padding(start = Spacing.three, top = PILL_INSET, end = PILL_INSET, bottom = PILL_INSET),
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(Spacing.one),
        ) {
            Text(
                stringResource(R.string.session_logged_title),
                style = KettleType.default,
                color = runnerColors.textSecondary,
            )
            Text(logged, style = KettleType.default, color = runnerColors.text)
        }
        OutlinedPill(stringResource(R.string.session_undo), undo)
    }
}

private val PILL_INSET = 6.dp
private val PanelShape = RoundedCornerShape(18.dp)
