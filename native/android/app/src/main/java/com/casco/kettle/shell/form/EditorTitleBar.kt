package com.casco.kettle.shell.form

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * An editor's pinned top (#438, #440): its title and Save, and under them why Save refused, pinned with
 * it: at the foot of a long editor it would never be seen. The sheet's × is the cancel, so there is none.
 */
@Composable
internal fun EditorTitleBar(
    title: String,
    onSave: () -> Unit,
    refusal: String?,
) {
    val colors = KettleTheme.colors
    Row(Modifier.padding(vertical = Spacing.two), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = KettleType.subtitle, color = colors.text)
        // The heading's size: white on the accent is 3.64:1, AA for large text only (theme.ts).
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.accent)
                .clickable(role = Role.Button, onClick = onSave)
                .padding(horizontal = Spacing.three),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.common_save), style = KettleType.heading, color = colors.onAccent)
        }
    }
    refusal?.let {
        Text(it, Modifier.padding(bottom = Spacing.two), style = KettleType.small, color = colors.accentText)
    }
}
