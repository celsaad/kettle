package com.casco.kettle.shell.list

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
 * The "start this one" control at the end of a row: Home's list and Program detail's weeks.
 *
 * Accent, after a detour through grey: a grey triangle beside a grey chevron stopped announcing itself
 * as the way to start anything. The accent budget is spent on the *fill* instead, so the next-up card's
 * Start is the only solid accent block on Home and these are a tint behind a glyph. The glyph is
 * `accentText`, not `accent`, which is what passes: `accent` on `accentSoft` measures 2.90:1 in light
 * mode, under the 3:1 a meaningful graphic needs, where `accentText` measures 4.53:1 light and 6.58:1
 * dark.
 *
 * A drawn [glyph] with no words, so [label] is its only name, and it has to say *which* row: a column
 * of identical triangles otherwise announces itself the same all the way down. The target is 48dp
 * while the drawn circle stays 36, so it doesn't crowd the name beside it.
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
            Modifier.size(36.dp).clip(CircleShape).background(colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            if (glyph == StartGlyph.Plus) {
                Canvas(Modifier.size(PLUS_SIZE)) {
                    val stroke = PLUS_STROKE.toPx()
                    drawLine(
                        colors.accentText,
                        Offset(0f, center.y),
                        Offset(size.width, center.y),
                        stroke,
                        StrokeCap.Round,
                    )
                    drawLine(
                        colors.accentText,
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
                drawPath(triangle, colors.accentText)
            }
        }
    }
}

private val PLUS_SIZE = 12.dp
private val PLUS_STROKE = 2.dp

/** What [RowStartButton] draws: a triangle to start a workout, a plus for one that begins with nothing (#412). */
enum class StartGlyph { Play, Plus }
