package com.casco.kettle.shell.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * Cancel and [primary], as RN's editors laid them out: the primary the wider of the two (`flex: 1.4`
 * against 1), each at least RN's height and growing with the reader's text size. [compact] is the
 * quick-add's smaller pair, for a form inside a card.
 */
@Composable
internal fun EditorButtons(
    primary: String,
    onCancel: () -> Unit,
    onPrimary: () -> Unit,
    compact: Boolean = false,
) {
    val colors = KettleTheme.colors
    val size = if (compact) ButtonSize.Compact else ButtonSize.Sheet
    val shape = RoundedCornerShape(size.radius)
    val style = size.style()
    Row(Modifier.padding(top = size.above), horizontalArrangement = Arrangement.spacedBy(size.gap)) {
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = size.minHeight)
                .clip(shape)
                .border(size.border, colors.border, shape)
                .clickable(role = Role.Button, onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.common_cancel), style = style, color = colors.textSecondary) }
        Box(
            Modifier
                .weight(PRIMARY_WEIGHT)
                .heightIn(min = size.minHeight)
                .clip(shape)
                .background(colors.accent)
                .clickable(role = Role.Button, onClick = onPrimary),
            contentAlignment = Alignment.Center,
        ) { Text(primary, style = style, color = colors.onAccent) }
    }
}

/** RN's two button rows: an editor sheet's, and the quick-add's inside its card. */
private enum class ButtonSize(
    val minHeight: Dp,
    val radius: Dp,
    val border: Dp,
    val gap: Dp,
    val above: Dp,
) {
    Sheet(minHeight = 52.dp, radius = 15.dp, border = 1.5.dp, gap = Spacing.three - 4.dp, above = Spacing.four),
    Compact(minHeight = 44.dp, radius = 10.dp, border = 1.dp, gap = Spacing.one + 2.dp, above = Spacing.two + 2.dp),
    ;

    @Composable
    fun style(): TextStyle = if (this == Sheet) KettleType.heading else KettleType.small
}

/** RN's `flex: 1.4` against Cancel's 1: the primary is the wider, as the step the screen is for. */
private const val PRIMARY_WEIGHT = 1.4f
