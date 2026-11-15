package com.casco.kettle.shell.form

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * The outlined small button of Import's copies and paste and the YAML view's Copy and Share, named by
 * its own text; a [modifier] sizes it, its label centred. Not a live region, though Import's "Copied"
 * changes its words under focus: from Android 13 the system announces a copy itself, and on a device a
 * live region here was read on the way back to idle, not on "Copied" (Compose fires from the node's
 * previous state). Import's route confirms a copy below 13 instead.
 */
@Composable
internal fun SmallButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(SmallShape)
            .border(1.dp, KettleTheme.colors.border, SmallShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.two + 2.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = KettleType.smallMedium, color = color) }
}

private val SmallShape = RoundedCornerShape(12.dp)
