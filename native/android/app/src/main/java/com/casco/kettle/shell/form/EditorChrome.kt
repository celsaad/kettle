package com.casco.kettle.shell.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import java.io.IOException

// What every editor ends with (#274): Cancel beside the step the screen is for, the dialogs it asks
// in, and what it says when the disk refuses. Shared so the next editor can't drift from these.

/**
 * Cancel and [primary], as RN's editors laid them out: the primary the wider of the two (`flex: 1.4`
 * against 1), each at least RN's height and growing with the reader's text size. [compact] is the
 * quick-add's smaller pair, for a form inside a card.
 */
@Composable
fun EditorButtons(
    primary: String,
    onCancel: () -> Unit,
    onPrimary: () -> Unit,
    compact: Boolean = false,
) {
    val colors = KettleTheme.colors
    val shape = RoundedCornerShape(if (compact) 10.dp else 15.dp)
    val height = if (compact) 44.dp else 52.dp
    val style = if (compact) KettleType.small else KettleType.heading
    Row(
        Modifier.padding(top = if (compact) Spacing.two + 2.dp else Spacing.four),
        horizontalArrangement = Arrangement.spacedBy(if (compact) Spacing.one + 2.dp else Spacing.three - 4.dp),
    ) {
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = height)
                .clip(shape)
                .border(if (compact) 1.dp else 1.5.dp, colors.border, shape)
                .clickable(role = Role.Button, onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.common_cancel), style = style, color = colors.textSecondary) }
        Box(
            Modifier
                .weight(PRIMARY_WEIGHT)
                .heightIn(min = height)
                .clip(shape)
                .background(colors.accent)
                .clickable(role = Role.Button, onClick = onPrimary),
            contentAlignment = Alignment.Center,
        ) { Text(primary, style = style, color = colors.onAccent) }
    }
}

/** One of a dialog's buttons: its words and what it does. */
data class DialogAction(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * An alert in Kettle's surfaces rather than Material's defaults, which aren't Kettle's palette and
 * were never contrast-checked against it: `accentText` on `backgroundElement` is RN's checked pairing.
 * [dismiss] null for a notice with one button, as RN's buttonless `Alert` is.
 */
@Composable
fun KettleAlertDialog(
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

/**
 * Runs [write], and returns why the disk refused it, or null once it landed: a full disk, or a
 * permission withdrawn (a `SecurityException`, which often has no message, so its name stands in and
 * the refusal still says something rather than Save looking dead).
 */
suspend fun refusal(write: suspend () -> Unit): String? =
    try {
        write()
        null
    } catch (e: IOException) {
        e.message ?: e.javaClass.simpleName
    } catch (e: SecurityException) {
        e.message ?: e.javaClass.simpleName
    }

/** RN's `flex: 1.4` against Cancel's 1: the primary is the wider, as the step the screen is for. */
private const val PRIMARY_WEIGHT = 1.4f
