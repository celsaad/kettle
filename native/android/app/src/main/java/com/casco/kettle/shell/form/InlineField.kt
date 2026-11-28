package com.casco.kettle.shell.form

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp

// A value a tap turns into a field: the session editor's cells (#442) and the exercise editor's counts
// (#463), so the two edit a number the same way.

/** The cell as a field, focused at once, done when focus leaves it: the next cell tapped, or the keyboard closed. */
@Composable
internal fun InlineField(
    value: String,
    name: String,
    onChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // Not state: whether focus has arrived yet. The field starts unfocused, and that first report isn't
    // a leaving; only a loss after it had focus closes it.
    val focused = remember { booleanArrayOf(false) }
    Box(
        Modifier.onFocusChanged {
            if (it.hasFocus) {
                focused[0] = true
            } else if (focused[0]) {
                onDone()
            }
        },
    ) {
        FormField(value, onChange, name = name, placeholder = "\u2013", FieldOptions(minHeight = 44.dp, focus = focus))
    }
}
