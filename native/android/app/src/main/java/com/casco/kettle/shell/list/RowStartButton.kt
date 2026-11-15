package com.casco.kettle.shell.list

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.theme.KettleTheme

/**
 * The "start this one" control at the end of a row: Today's list and rest day, Library's Workouts and
 * Program detail's days.
 *
 * Neutral, as the boards draw it (#490): a 44dp circle edged in `lineStrong` (3:1, #487) with an ink
 * glyph, no fill. It had been an accent tint, after a grey triangle beside a grey chevron stopped
 * announcing itself as the way to start anything; ink and a 3:1 edge answer that without spending the
 * accent, so the next-up card's Start is the one accent block on Today.
 *
 * A drawn [glyph] with no words, so [label] is its only name, and it has to say *which* row: a column
 * of identical triangles otherwise announces itself the same all the way down. The target is 48dp
 * around the drawn 44.
 */
@Composable
fun RowStartButton(
    label: String,
    glyph: StartGlyph = StartGlyph.Play,
    onClick: () -> Unit,
) {
    val colors = KettleTheme.colors
    Box(
        Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        // Decorative geometry, so a fixed size is right: the target above is what grows.
        Box(
            Modifier.size(44.dp).border(1.dp, colors.lineStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (glyph == StartGlyph.Plus) {
                Canvas(Modifier.size(PLUS_SIZE)) {
                    val stroke = PLUS_STROKE.toPx()
                    drawLine(
                        colors.text,
                        Offset(0f, center.y),
                        Offset(size.width, center.y),
                        stroke,
                        StrokeCap.Round,
                    )
                    drawLine(
                        colors.text,
                        Offset(center.x, 0f),
                        Offset(center.x, size.height),
                        stroke,
                        StrokeCap.Round,
                    )
                }
                return@Box
            }
            Canvas(Modifier.padding(start = 2.dp).size(9.dp, 12.dp)) {
                val triangle =
                    Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, size.height / 2)
                        lineTo(0f, size.height)
                        close()
                    }
                drawPath(triangle, colors.text)
            }
        }
    }
}

private val PLUS_SIZE = 12.dp
private val PLUS_STROKE = 2.dp

/** What [RowStartButton] draws: a triangle to start a workout, a plus for one that begins with nothing (#412). */
enum class StartGlyph { Play, Plus }
