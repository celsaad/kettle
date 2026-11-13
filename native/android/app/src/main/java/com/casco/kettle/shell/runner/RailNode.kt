package com.casco.kettle.shell.runner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.StopState
import com.casco.kettle.domain.TimelineStop
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.RunnerPalette
import com.casco.kettle.shell.theme.Spacing

// Coming up's line (#415): the rail every stop hangs on, its nodes, and the circuit loop off it. All
// decorative: each stop's words carry its state for a reader ("Now", or Done as its state).

/** What sits on the rail at a stop. */
internal enum class RailNode { Stop, Now, Tick, End, None }

/**
 * One stop: the rail beside [content], the line drawn the stop's whole height so it meets the next
 * whatever the text's length. The line is the text's ink as far as the session has come and dim beyond:
 * above a stop when it's reached, below it once it's done. The circuit you're in ([RailNode.None]) is
 * reached all down its loop.
 */
@Composable
internal fun RailRow(
    node: RailNode,
    state: StopState,
    first: Boolean,
    last: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = runnerColors
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(RAIL_GAP)) {
        Box(
            Modifier.width(RAIL_WIDTH).fillMaxHeight().drawBehind {
                val x = size.width / 2
                val middle = NODE_CENTER.toPx()
                val stroke = LINE.toPx()
                val reached = if (state == StopState.NEXT) colors.outline else colors.text
                val beyond = if (state == StopState.DONE || node == RailNode.None) colors.text else colors.outline
                if (!first) drawLine(reached, Offset(x, 0f), Offset(x, middle), stroke)
                if (!last) drawLine(beyond, Offset(x, middle), Offset(x, size.height), stroke)
                drawNode(node, state, Offset(x, middle), colors)
            },
        )
        Box(Modifier.weight(1f).padding(bottom = if (last) 0.dp else STOP_GAP)) { content() }
    }
}

/** A node at [center]: ticked when done, ringed and dotted for the stop on screen, an empty ring ahead. */
internal fun DrawScope.drawNode(
    node: RailNode,
    state: StopState,
    center: Offset,
    colors: RunnerPalette,
) {
    val stroke = LINE.toPx()
    when (node) {
        // The circuit you're in has no node of its own: its loop is the stop.
        RailNode.None -> {}

        RailNode.Tick -> {
            val half = TICK_HALF.toPx()
            val ink = if (state == StopState.NEXT) colors.outline else colors.text
            drawLine(ink, Offset(center.x - half, center.y), Offset(center.x + half, center.y), stroke)
        }

        RailNode.End -> {
            val side = STOP_NODE.toPx()
            val topLeft = Offset(center.x - side / 2, center.y - side / 2)
            val corner = CornerRadius(END_CORNER.toPx())
            drawRoundRect(colors.backgroundElement, topLeft, Size(side, side), corner)
            drawRoundRect(colors.outline, topLeft, Size(side, side), corner, style = Stroke(stroke))
        }

        RailNode.Now -> {
            drawCircle(colors.backgroundElement, NOW_NODE.toPx() / 2, center)
            drawCircle(colors.text, (NOW_NODE.toPx() - stroke) / 2, center, style = Stroke(stroke))
            drawCircle(colors.text, NOW_DOT.toPx() / 2, center)
        }

        RailNode.Stop -> {
            val radius = STOP_NODE.toPx() / 2
            if (state == StopState.DONE) {
                drawCircle(colors.text, radius, center)
                drawCheck(center, radius, colors)
            } else {
                drawCircle(colors.backgroundElement, radius, center)
                drawCircle(colors.outline, radius - stroke / 2, center, style = Stroke(stroke))
            }
        }
    }
}

private fun DrawScope.drawCheck(
    center: Offset,
    radius: Float,
    colors: RunnerPalette,
) {
    val start = Offset(center.x - CHECK_LEFT * radius, center.y)
    val elbow = Offset(center.x - CHECK_ELBOW * radius, center.y + CHECK_RISE * radius)
    val end = Offset(center.x + CHECK_RIGHT * radius, center.y - CHECK_RISE * radius)
    val path =
        Path().apply {
            moveTo(start.x, start.y)
            lineTo(elbow.x, elbow.y)
            lineTo(end.x, end.y)
        }
    drawPath(path, colors.onAccent, style = Stroke(CHECK.toPx()))
}

/**
 * The circuit you're in, as the canvas draws it: a loop off the line with the round and its dots, the
 * rests it runs, each member done (with this round's logged value), now or next, and the rounds after.
 */
@Composable
internal fun LoopCard(
    stop: TimelineStop.Loop,
    entryOf: (String) -> SessionEntry?,
) {
    val resources = LocalResources.current
    val colors = runnerColors
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = LOOP_STROKE.toPx()
                drawRoundRect(
                    colors.outline,
                    Offset(stroke / 2, stroke / 2),
                    Size(size.width - stroke, size.height - stroke),
                    CornerRadius(LOOP_CORNER.toPx()),
                    style = Stroke(stroke),
                )
            }.padding(Spacing.three),
        verticalArrangement = Arrangement.spacedBy(Spacing.two),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.session_comingUp_circuitRound, "${stop.round}", "${stop.rounds}"),
                Modifier.weight(1f),
                style = KettleType.default.copy(fontWeight = FontWeight.Bold),
                color = colors.text,
            )
            RoundDots(stop.round, stop.rounds)
        }
        Secondary(resources.loopRests(stop))
        stop.members.forEach { member ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.two)) {
                Canvas(Modifier.size(RAIL_WIDTH)) {
                    drawNode(nodeFor(member.state), member.state, Offset(size.width / 2, NODE_CENTER.toPx()), colors)
                }
                StateLine(
                    member.state,
                    member.step.exerciseName,
                    done = { resources.roundValue(entryOf(member.memberKey), stop.round) },
                    now = { resources.upcomingTarget(member.step) },
                    next = { resources.upcomingTarget(member.step) },
                )
            }
        }
        resources.thenRounds(stop)?.let { Secondary(it) }
    }
}

/** A dot per round: filled behind, ringed for the one under way, dim ahead. Decorative: the line says it. */
@Composable
private fun RoundDots(
    round: Long,
    rounds: Long,
) {
    if (rounds > MAX_DOTS) return
    val colors = runnerColors
    Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(DOT_GAP)) {
        for (index in 1..rounds) {
            Canvas(Modifier.size(DOT)) {
                val radius = size.minDimension / 2
                val stroke = LINE.toPx()
                when {
                    index < round -> drawCircle(colors.text, radius)
                    index == round -> drawCircle(colors.text, radius - stroke / 2, style = Stroke(stroke))
                    else -> drawCircle(colors.outline, radius - stroke / 2, style = Stroke(stroke))
                }
            }
        }
    }
}

private val RAIL_WIDTH = 24.dp

/** The canvas's 12dp between the rail and a stop's words, on the scale. */
private val RAIL_GAP = Spacing.two + Spacing.one

/** The gap under a stop's words before the next stop's node. */
private val STOP_GAP = Spacing.three
private val NODE_CENTER = 12.dp
private val LINE = 2.dp
private val STOP_NODE = 16.dp
private val NOW_NODE = 24.dp
private val NOW_DOT = 10.dp
private val TICK_HALF = 5.dp
private val END_CORNER = 3.dp
private val CHECK = 1.75.dp

// The tick's points, as fractions of the node's radius from its centre.
private const val CHECK_LEFT = 0.45f
private const val CHECK_ELBOW = 0.1f
private const val CHECK_RISE = 0.35f
private const val CHECK_RIGHT = 0.5f
private val LOOP_STROKE = 1.5.dp
private val LOOP_CORNER = 18.dp
private val DOT = 10.dp
private val DOT_GAP = Spacing.one

/** Past this many rounds the dots stop reading as a count; the line's "round 2 of 12" carries it. */
private const val MAX_DOTS = 12
