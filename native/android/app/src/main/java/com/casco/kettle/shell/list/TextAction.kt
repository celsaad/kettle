package com.casco.kettle.shell.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.theme.Spacing

/**
 * A control that is only its words, as RN's `Pressable` around a `Text` with a `hitSlop`: a list
 * header's links and an open row's actions. Its name is the words on screen, so it needs no label, and
 * the words sit centred in a 48dp target that still grows with the reader's text size.
 */
@Composable
fun TextAction(
    text: String,
    style: TextStyle,
    color: Color,
    onClick: () -> Unit,
) = Text(
    text,
    Modifier
        .heightIn(min = 48.dp)
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = Spacing.two)
        .wrapContentHeight(Alignment.CenterVertically),
    style = style,
    color = color,
)
