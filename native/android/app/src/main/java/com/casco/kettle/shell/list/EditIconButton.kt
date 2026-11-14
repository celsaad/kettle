package com.casco.kettle.shell.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.TypeIcon

/**
 * The pencil beside a detail screen's title, Program detail's and Workout preview's. Icon-only, so it
 * carries the [label] its pencil can't give it.
 */
@Composable
internal fun EditIconButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        TypeIcon(R.drawable.icon_edit, KettleType.heading, KettleTheme.colors.textSecondary)
    }
}
