package com.casco.kettle.shell.form

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
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
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.TypeIcon

/** A − or + on a stepper: named by [label], since its icon says nothing, in a 44dp target. */
@Composable
internal fun StepButton(
    @DrawableRes icon: Int,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = KettleTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            .clip(shape)
            .border(1.dp, colors.border, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            // Dimmed and announced disabled where it can't move, as the week menu's moves are.
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        contentAlignment = Alignment.Center,
    ) { TypeIcon(icon, KettleType.default, colors.textSecondary) }
}

private const val DISABLED_ALPHA = 0.4f
