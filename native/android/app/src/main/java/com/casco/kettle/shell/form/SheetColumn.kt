package com.casco.kettle.shell.form

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import com.casco.kettle.shell.theme.MaxContentWidth

/** An editor sheet's column: as wide as the screen up to [MaxContentWidth], centred past it. */
internal val SheetColumn = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth()
