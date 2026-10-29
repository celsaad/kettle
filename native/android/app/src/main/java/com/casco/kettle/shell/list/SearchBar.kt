package com.casco.kettle.shell.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * The search box over a list, as RN's `components/search-bar.tsx`, which every list screen shares. The
 * placeholder is its name too, read with the field as Material's `TextField` reads its own: the visible
 * words are what Voice Access matches, and a separate label would break that. It owns its top margin,
 * since every screen places it the same distance below the block above it.
 */
@Composable
fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val colors = KettleTheme.colors
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value,
        onValueChange,
        Modifier
            .padding(top = Spacing.three)
            .fillMaxWidth(),
        textStyle = KettleType.default.copy(fontSize = 14.sp, color = colors.text),
        singleLine = true,
        cursorBrush = SolidColor(colors.text),
        decorationBox = { field ->
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .background(colors.backgroundElement, shape)
                    .border(1.dp, colors.border, shape)
                    .padding(horizontal = Spacing.two + 6.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.two),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⌕", Modifier.clearAndSetSemantics {}, color = colors.textSecondary)
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
