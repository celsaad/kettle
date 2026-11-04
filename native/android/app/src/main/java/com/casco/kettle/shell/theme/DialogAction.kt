package com.casco.kettle.shell.theme

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** One of a dialog's buttons: its words and what it does. */
internal data class DialogAction(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * An alert in Kettle's surfaces rather than Material's defaults, which aren't Kettle's palette and
 * were never contrast-checked against it: `accentText` on `backgroundElement` is RN's checked pairing.
 * [dismiss] null for a notice with one button, as RN's buttonless `Alert` is.
 */
@Composable
internal fun KettleAlertDialog(
    title: String,
    body: String,
    onDismissRequest: () -> Unit,
    confirm: DialogAction,
    dismiss: DialogAction? = null,
) {
    val colors = KettleTheme.colors
    val buttons = ButtonDefaults.textButtonColors(contentColor = colors.accentText)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(confirm.onClick, colors = buttons) { Text(confirm.label) } },
        dismissButton = dismiss?.let { { TextButton(it.onClick, colors = buttons) { Text(it.label) } } },
        containerColor = colors.backgroundElement,
        titleContentColor = colors.text,
        textContentColor = colors.textSecondary,
    )
}
