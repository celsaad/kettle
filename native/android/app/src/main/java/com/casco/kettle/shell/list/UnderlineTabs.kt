package com.casco.kettle.shell.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.rememberWidestText

/**
 * A screen's sections as tabs (#413): equal widths over a hairline, the one showing in the text's ink,
 * bold and underlined, the rest secondary. Each is a selectable tab, so a reader hears which is on.
 *
 * At a large text size the labels can outgrow equal thirds, and a label squeezed narrower than its own
 * word breaks mid-word ("Workout / s", #480). So the labels are measured first: while the widest fits
 * an equal share they stay equal, and once it doesn't, each tab takes its label's own width and the row
 * scrolls sideways, keeping the selected tab in view, rather than shrinking the reader's text.
 */
@Composable
fun <T> UnderlineTabs(
    items: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val colors = KettleTheme.colors
    // Bold, as the selected label is drawn, so a label never grows past its width when it's picked.
    val widest = rememberWidestText(items.map { it.second }, labelStyle(true))
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fits = items.isEmpty() || (widest + TAB_INSET * 2) * items.size <= maxWidth
        val scroll = rememberScrollState()
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (fits) Modifier else Modifier.horizontalScroll(scroll))
                .selectableGroup()
                .drawBehind {
                    val y = size.height - HAIRLINE.toPx() / 2
                    drawLine(colors.border, Offset(0f, y), Offset(size.width, y), HAIRLINE.toPx())
                },
        ) {
            items.forEach { (value, label) ->
                val active = value == selected
                val inView = remember { BringIntoViewRequester() }
                if (active && !fits) LaunchedEffect(selected) { inView.bringIntoView() }
                Box(
                    (if (fits) Modifier.weight(1f) else Modifier)
                        .heightIn(min = 48.dp)
                        .bringIntoViewRequester(inView)
                        .selectable(active, role = Role.Tab) { onSelect(value) }
                        .drawBehind {
                            if (active) {
                                val y = size.height - UNDERLINE.toPx() / 2
                                drawLine(colors.text, Offset(0f, y), Offset(size.width, y), UNDERLINE.toPx())
                            }
                        }.padding(horizontal = if (fits) TAB_INSET else SCROLLING_INSET, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = labelStyle(active),
                        color = if (active) colors.text else colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun labelStyle(active: Boolean) =
    KettleType.default.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)

private val TAB_INSET = Spacing.one

/** Wide enough apart that tabs at their own widths still read as separate targets. */
private val SCROLLING_INSET = Spacing.three
private val HAIRLINE = 1.dp
private val UNDERLINE = 3.dp
