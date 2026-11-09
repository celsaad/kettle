package com.casco.kettle.shell.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import com.casco.kettle.shell.R

/**
 * A Material Symbols vector where a `Text` used to draw a glyph (#267), drawn one em of [style] across:
 * what the font's own ligature would be at that size, so it sits beside its row's words as the glyph
 * did and grows with them at the reader's text size. Decorative, always: the control around it
 * carries the name and any expanded state.
 */
@Composable
fun TypeIcon(
    @DrawableRes icon: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) = Icon(
    painterResource(icon),
    contentDescription = null,
    modifier.size(with(LocalDensity.current) { style.fontSize.toDp() }).clearAndSetSemantics {},
    tint = color,
)

/** A row's trailing chevron: › for one that opens something, ⌄ for one that is open. */
@Composable
fun Chevron(
    expanded: Boolean = false,
    style: TextStyle = KettleType.default,
) = TypeIcon(
    if (expanded) R.drawable.icon_expand else R.drawable.icon_chevron,
    style,
    KettleTheme.colors.textSecondary,
)
