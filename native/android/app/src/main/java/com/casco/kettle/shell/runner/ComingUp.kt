package com.casco.kettle.shell.runner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.StopState
import com.casco.kettle.domain.TimelineStop
import com.casco.kettle.domain.comingUpTimeline
import com.casco.kettle.shell.R
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * Coming up as a subway map (#415): the whole session on one line, done stops ticked with what was
 * logged, the stop on screen ringed with its target (no clock: the screen behind has it), what's ahead
 * dim, standalone rests as ticks, and the circuit you're in as a loop off the line. Opens scrolled to
 * the stop on screen. An ad-hoc session has no end node.
 */
@Composable
internal fun ComingUp(
    view: RunnerView.Running,
    onClose: () -> Unit,
) {
    val snapshot = view.snapshot
    val ends = !snapshot.adhoc
    val timeline = remember(snapshot.steps, snapshot.stepIndex) { comingUpTimeline(snapshot.steps, snapshot.stepIndex) }
    val entryOf = { memberKey: String -> snapshot.entryIndex[memberKey]?.let(view.session.entries::getOrNull) }
    val title = stringResource(R.string.session_comingUp_title)
    val now = timeline.stops.indexOfFirst { it.state == StopState.NOW }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, now - 1))
    // The backdrop is hidden from a reader: the × already closes, and a second "Close" covering the
    // whole screen would only be one more stop to swipe past.
    OverlaySheet(null, onClose, title, inset = false) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(title, Modifier.semantics { heading() }, style = KettleType.heading, color = runnerColors.text)
                Secondary(
                    pluralText(
                        R.plurals.session_comingUp_progress,
                        R.string.session_comingUp_progress_zero,
                        timeline.total,
                        "${timeline.done}",
                    ),
                )
            }
            SymbolButton(R.drawable.icon_close, stringResource(R.string.common_close), onClose, look = SymbolLook.Close)
        }
        LazyColumn(Modifier.heightIn(max = LIST_MAX_HEIGHT), state = list) {
            itemsIndexed(timeline.stops) { index, stop ->
                StopRow(stop, first = index == 0, last = !ends && index == timeline.stops.lastIndex, entryOf)
            }
            if (ends) {
                item {
                    RailRow(RailNode.End, StopState.NEXT, first = timeline.stops.isEmpty(), last = true) {
                        Text(
                            stringResource(R.string.session_comingUp_end),
                            style = KettleType.default,
                            color = runnerColors.text,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StopRow(
    stop: TimelineStop,
    first: Boolean,
    last: Boolean,
    entryOf: (String) -> SessionEntry?,
) {
    val resources = LocalResources.current
    when (stop) {
        is TimelineStop.Exercise -> {
            RailRow(nodeFor(stop.state), stop.state, first, last) {
                StateLine(
                    stop.state,
                    stop.step.exerciseName,
                    done = { resources.doneDetail(stop, entryOf(stop.memberKey)) },
                    now = { resources.nowDetail(stop.step) },
                    next = { resources.nextDetail(stop) },
                )
            }
        }

        is TimelineStop.Rest -> {
            RailRow(RailNode.Tick, stop.state, first, last) {
                val done = stringResource(R.string.session_comingUp_done)
                Text(
                    stringResource(R.string.session_comingUp_rest, formatClock(stop.seconds)),
                    // A tick says nothing to a reader, so a rest behind says so, as a done exercise does.
                    Modifier.semantics { if (stop.state == StopState.DONE) stateDescription = done },
                    style = KettleType.small,
                    color = if (stop.state == StopState.NOW) runnerColors.text else runnerColors.textSecondary,
                )
            }
        }

        is TimelineStop.Loop -> {
            RailRow(RailNode.None, stop.state, first, last) { LoopCard(stop, entryOf) }
        }

        is TimelineStop.Circuit -> {
            RailRow(RailNode.Stop, stop.state, first, last) {
                val kicker =
                    pluralText(
                        R.plurals.session_comingUp_circuitRounds,
                        R.string.session_comingUp_circuitRounds_zero,
                        stop.rounds,
                    )
                val names = stop.names.joinToString(" · ")
                if (stop.state == StopState.DONE) DoneLine(kicker, names) else NextLine(kicker, names)
            }
        }
    }
}

/** A stop's node on the rail: ringed and dotted for the one on screen, a plain stop otherwise. */
internal fun nodeFor(state: StopState): RailNode = if (state == StopState.NOW) RailNode.Now else RailNode.Stop

/** A stop's words by its state, the sheet's and the loop's alike: ticked and logged, Now and its target, or ahead. */
@Composable
internal fun StateLine(
    state: StopState,
    name: String,
    done: () -> String?,
    now: () -> String,
    next: () -> String?,
) {
    when (state) {
        StopState.DONE -> DoneLine(name, done())
        StopState.NOW -> NowStop(name, now())
        StopState.NEXT -> NextLine(name, next())
    }
}

/** "✓ Inverted Rows · 3 sets · 10, 9, 8": the name in the text's ink, the rest secondary. Read as done. */
@Composable
internal fun DoneLine(
    name: String,
    detail: String?,
) {
    val done = stringResource(R.string.session_comingUp_done)
    StopText(name, detail, Modifier.semantics(mergeDescendants = true) { stateDescription = done })
}

@Composable
internal fun NextLine(
    name: String,
    detail: String?,
) = StopText(name, detail, Modifier.semantics(mergeDescendants = true) {})

@Composable
private fun StopText(
    name: String,
    detail: String?,
    modifier: Modifier,
) {
    val ink = runnerColors.text
    // The bundle's own "a · b", so a language can order or join the two its way; the name is then found
    // in it to take the ink.
    val line = if (detail == null) name else stringResource(R.string.session_upcoming_detail, name, detail)
    Text(
        buildAnnotatedString {
            append(line)
            val start = line.indexOf(name)
            if (start >=
                0
            ) {
                addStyle(SpanStyle(color = ink, fontWeight = FontWeight.SemiBold), start, start + name.length)
            }
        },
        modifier,
        style = KettleType.default,
        color = runnerColors.textSecondary,
    )
}

/** The stop on screen: "Now", its name large, and its target. */
@Composable
internal fun NowStop(
    name: String,
    detail: String,
) {
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.half)) {
        Text(
            stringResource(R.string.session_comingUp_now),
            style = KettleType.small.copy(fontWeight = FontWeight.Bold),
            color = runnerColors.text,
        )
        Text(name, style = KettleType.heading, color = runnerColors.text)
        Text(detail, style = KettleType.small, color = runnerColors.textSecondary)
    }
}

private val LIST_MAX_HEIGHT = 520.dp
