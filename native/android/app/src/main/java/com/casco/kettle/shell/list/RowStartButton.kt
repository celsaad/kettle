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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.theme.KettleTheme

/**
 * The "start this one" control at the end of a row, as RN's `components/row-start-button.tsx`, which
 * holds why it is accent and why the glyph is `accentText` on `accentSoft` (the contrast figures).
 * Shared, since Home's list will want it too.
 *
 * A drawn triangle with no words, so [label] is its only name, and it has to say *which* row: a column
 * of identical triangles otherwise announces itself the same all the way down. The target is 48dp
 * while the drawn circle stays 36, so it doesn't crowd the name beside it.
 */
@Composable
fun RowStartButton(
    label: String,
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
