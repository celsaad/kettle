package com.casco.kettle.shell.importer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.R
import com.casco.kettle.shell.form.FieldKind
import com.casco.kettle.shell.form.FieldOptions
import com.casco.kettle.shell.form.FormField
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * Pasted YAML: the toggle, the box, filling it from the clipboard, and Review. An assistant's YAML is
 * text in a chat window, and saving it to a file on a phone to hand back to the picker buys nothing.
 */
@Composable
internal fun PasteSection(importer: Importer) {
    val colors = KettleTheme.colors
    val pasting = importer.pasting
    PickBox(
        stringResource(R.string.import_pasteToggle),
        stringResource(R.string.import_pasteToggleDetail),
        onClick = { importer.pasting = !pasting },
        modifier =
            Modifier.semantics {
                if (pasting) {
                    collapse { true.also { importer.pasting = false } }
                } else {
                    expand { true.also { importer.pasting = true } }
                }
            },
    )
    if (!pasting) return
    Box(Modifier.padding(top = Spacing.two)) {
        FormField(
            importer.pasted,
            importer::edit,
            name = stringResource(R.string.import_pastePlaceholder),
            placeholder = stringResource(R.string.import_pastePlaceholder),
            FieldOptions(kind = FieldKind.Code, minHeight = 132.dp, fill = colors.backgroundElement),
        )
    }
    // The return leg of the trip the brief starts: on a phone the alternative is a long-press, a Paste
    // bubble and a caret, for text that arrived from another app seconds ago.
    Column(
        Modifier.padding(top = Spacing.three - 2.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.one + 2.dp),
    ) {
        CopyButton(stringResource(R.string.import_pasteFromClipboard), colors.accentText, importer::pasteFromClipboard)
        importer.pasteNote?.let {
            // "Empty, or access was denied": Android can't tell the reader which, so neither is claimed.
            Note(
                stringResource(
                    if (it == PasteNote.Empty) R.string.import_clipboardEmpty else R.string.import_clipboardFailed,
                ),
            )
        }
    }
    // Off while a file is being read, as the pack rows are, so a second source can't race the first.
    val canReview = !importer.busy && importer.pasted.isNotBlank()
    Box(
        Modifier
            .padding(top = Spacing.two)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(ButtonShape)
            .background(colors.accentSoft.dimmedUnless(canReview))
            .clickable(enabled = canReview, role = Role.Button, onClick = importer::reviewPaste),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.import_reviewPaste),
            style = KettleType.heading,
            color = colors.accentText.dimmedUnless(canReview),
        )
    }
}
