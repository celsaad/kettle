package com.casco.kettle.shell.form

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.TypeIcon

/** The ✕ that removes [label]'s thing: icon-only, so named, and a 44dp target round a small icon. */
@Composable
internal fun RemoveButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = Box(
    Modifier
        .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
        .clip(RoundedCornerShape(8.dp))
        // Disabled rather than silently doing nothing: TalkBack then says it's unavailable.
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label }
        .alpha(if (enabled) 1f else DISABLED_ALPHA),
    contentAlignment = Alignment.Center,
) { TypeIcon(R.drawable.icon_close, KettleType.default, KettleTheme.colors.textSecondary) }

private const val DISABLED_ALPHA = 0.4f
