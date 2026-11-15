package com.casco.kettle.shell.runner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.rememberWidestText

// What sits round a clock on the interval, AMRAP and hold screens (#471): the line under its bar, and
// how a stopped one reads.

/**
 * The line under a clock's bar (#471): [start], the clock's length, at one end and [end], what the clock
 * leads to, at the other; [start] alone where nothing is said of what's next. Measured first, as #496's
 * layouts are: where the two can't share a line at large text, they stack, each centred, since halves
 * broke "Depois, descanso de 0:20" over three lines beside a short "de 0:40".
 */
@Composable
internal fun ClockHint(
    start: String,
    end: String?,
) {
    if (end == null) return Secondary(start)
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val need =
            rememberWidestText(listOf(start), KettleType.small) + Spacing.two +
                rememberWidestText(listOf(end), KettleType.small)
        if (need <= maxWidth) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Secondary(start)
                Secondary(end)
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Secondary(start)
                Text(end, style = KettleType.small, color = runnerColors.textSecondary, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * "Paused" over a clock that's stopped (#471), polite so TalkBack says it as the pause lands; the clock
 * and its bar dim under it ([clockInk], [barFill]), so a glance tells a stopped clock from a running one.
 */
@Composable
internal fun PausedHeading(paused: Boolean) {
    if (!paused) return
    Text(
        stringResource(R.string.session_paused),
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = KettleType.subtitle,
        color = runnerColors.text,
    )
}

@Composable
internal fun clockInk(paused: Boolean): Color = if (paused) runnerColors.textSecondary else runnerColors.numeral

@Composable
internal fun barFill(paused: Boolean): Color = if (paused) runnerColors.textSecondary else runnerColors.accent
