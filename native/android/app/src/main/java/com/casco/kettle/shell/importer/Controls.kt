package com.casco.kettle.shell.importer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.form.dashedBorder
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

// The controls Import's sources and hand-offs share.

/**
 * A dashed source box, off and spinning while [busy]. No leading glyph, deliberately: RN's two
 * outlined squares read as unchecked checkboxes, implying a selection these rows never had, and the
 * labels already tell them apart.
 */
@Composable
internal fun PickBox(
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
) {
    val colors = KettleTheme.colors
    Row(
        Modifier
            .padding(top = Spacing.three - 2.dp)
            .fillMaxWidth()
            .clip(BoxShape)
            .background(colors.backgroundElement)
            .dashedBorder(1.dp, colors.border, 14.dp)
            .clickable(enabled = !busy, role = Role.Button, onClick = onClick)
            .then(modifier)
            .padding(Spacing.two + 4.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = KettleType.heading, color = colors.text)
            Text(detail, style = KettleType.small, color = colors.textSecondary)
        }
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = colors.accentText, strokeWidth = 2.dp)
    }
}

/** A line beside a control saying why it didn't do what it says, without taking the screen over. */
@Composable
internal fun Note(text: String) = Text(text, style = KettleType.small, color = KettleTheme.colors.textSecondary)
