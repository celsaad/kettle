package com.casco.kettle.shell.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * The floating +, as RN's FAB on every tab that has one: the accent in dark, the ink in light. Its
 * 52dp is a minimum rather than a fixed size, per AGENTS.md's controls rule, and [label] names it,
 * since a "+" says nothing. A list under it pads its bottom by `Spacing.six + Spacing.four` to clear it.
 */
@Composable
fun NewItemButton(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = KettleTheme.colors
    Box(
        modifier
            .padding(end = Spacing.three, bottom = Spacing.four)
            .sizeIn(minWidth = FabSize, minHeight = FabSize)
            .clip(FabShape)
            .background(if (KettleTheme.dark) colors.accent else colors.text)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "+",
            Modifier.clearAndSetSemantics {},
            style = FabPlusStyle,
            color = colors.onAccent,
        )
    }
}

private val FabSize = 52.dp
private val FabShape = RoundedCornerShape(16.dp)
private val FabPlusStyle = KettleType.title.copy(fontSize = 26.sp, lineHeight = 28.sp)
