package com.casco.kettle.shell.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType

/**
 * A screen's sections as tabs (#413): equal widths over a hairline, the one showing in the text's ink,
 * bold and underlined, the rest secondary. Each is a selectable tab, so a reader hears which is on. The
 * labels wrap rather than clip at a large text size, which the 48dp minimum leaves room to grow into.
 */
@Composable
fun <T> UnderlineTabs(
    items: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val colors = KettleTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .selectableGroup()
            .drawBehind {
                val y = size.height - HAIRLINE.toPx() / 2
                drawLine(colors.border, Offset(0f, y), Offset(size.width, y), HAIRLINE.toPx())
            },
    ) {
        items.forEach { (value, label) ->
            val active = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(active, role = Role.Tab) { onSelect(value) }
                    .drawBehind {
                        if (active) {
                            val y = size.height - UNDERLINE.toPx() / 2
                            drawLine(colors.text, Offset(0f, y), Offset(size.width, y), UNDERLINE.toPx())
                        }
                    }.padding(horizontal = 4.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = KettleType.default.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
                    color = if (active) colors.text else colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private val HAIRLINE = 1.dp
private val UNDERLINE = 3.dp
