package com.casco.kettle.shell.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.TypeIcon

/**
 * The search box every list screen shares, one component since four copies of it drifted in RN. The
 * placeholder is its name too, read with the field as Material's `TextField` reads its own: the visible
 * words are what Voice Access matches, and a separate label would break that. It owns its top margin,
 * since every screen places it the same distance below the block above it.
 *
 * Filled and borderless (#413), in [fieldFill].
 */
@Composable
fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = KettleTheme.colors
    BasicTextField(
        value,
        onValueChange,
        modifier
            .padding(top = Spacing.three)
            .fillMaxWidth(),
        textStyle = KettleType.default.copy(fontSize = 14.sp, color = colors.text),
        singleLine = true,
        cursorBrush = SolidColor(colors.text),
        decorationBox = { field ->
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .background(fieldFill(), FieldShape)
                    .padding(horizontal = Spacing.two + 6.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.two),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TypeIcon(R.drawable.icon_search, KettleType.default, colors.textSecondary)
                Box(Modifier.weight(1f)) {
                    // Inside the field's own node, which takes its name from it, as Material's does.
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = KettleType.default.copy(fontSize = 14.sp),
                            color = colors.textSecondary,
                        )
                    }
                    field()
                }
            }
        },
    )
}

/**
 * A filled field's ground: the search box, and Library's Type button beside it. `backgroundSelected` in
 * light, where `textSecondary` on it measures 4.79:1. Not in dark: that one is translucent, and over
 * `background` it composites to #2b2823, where `textSecondary` measures 4.46:1, short of AA's 4.5, so
 * dark takes `backgroundElement`, 4.97:1. `text` clears 12:1 on both.
 */
@Composable
fun fieldFill(): Color =
    if (KettleTheme.dark) KettleTheme.colors.backgroundElement else KettleTheme.colors.backgroundSelected

/** A filled field's corners. */
val FieldShape = RoundedCornerShape(14.dp)
