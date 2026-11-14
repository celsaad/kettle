package com.casco.kettle.shell.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.AssistantBrief
import com.casco.kettle.domain.Library
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.ShippedContent
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.Spacing

/**
 * What goes out to an assistant before there's any YAML to bring in: the share sheet, and the
 * clipboard beneath it as the fallback. Not before [library] is read: the brief's whole point is the ids
 * in it, and offered empty it would hand an assistant a confident list of nothing.
 *
 * The copy button says "Copy it instead" because Android always has a share sheet above it, so the
 * caption there already names what's copied; RN's full label was for web, which may have none.
 */
@Composable
internal fun AssistantBlock(
    importer: Importer,
    library: Library?,
    content: ShippedContent,
) {
    library ?: return
    val colors = KettleTheme.colors
    val schema = remember(content) { content.librarySchema() }
    val words = briefWords()
    // Built per press rather than kept: it is the library as it is now, and one call apiece for the
    // sheet and the clipboard, so the two payloads can't drift.
    val brief = { AssistantBrief.build(library, words, schema) }
    PickBox(
        stringResource(R.string.import_shareBrief),
        stringResource(R.string.import_copyBriefDetail),
        onClick = { importer.handoff.share(brief()) },
    )
    Column(
        Modifier.padding(top = Spacing.three - 2.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.one + 2.dp),
    ) {
        CopyAction(importer.handoff, CopyTarget.Brief, stringResource(R.string.import_copyBriefInstead), brief)
        if (importer.handoff.shareFailed) Note(stringResource(R.string.import_shareFailed))
    }
}

/** The brief's words in the reader's language, the file sheet's too. */
@Composable
internal fun briefWords() =
    AssistantBrief.Words(
        prompt = stringResource(R.string.import_brief_prompt),
        inventoryHeading = stringResource(R.string.import_brief_inventoryHeading),
        exercisesLabel = stringResource(R.string.import_brief_exercisesLabel),
        workoutsLabel = stringResource(R.string.import_brief_workoutsLabel),
        programsLabel = stringResource(R.string.import_brief_programsLabel),
        none = stringResource(R.string.import_brief_none),
        schemaHeading = stringResource(R.string.import_brief_schemaHeading),
    )

/**
 * A copy button and its outcome: its label says "Copied" while [target]'s last copy holds, keyed by
 * button so only the one pressed says so, and a note under it when the clipboard refused, so it never
 * claims a copy that didn't happen. [text] is built at the press.
 */
@Composable
internal fun CopyAction(
    handoff: Handoff,
    target: CopyTarget,
    idle: String,
    text: () -> String,
) {
    val confirmed = handoff.confirmed(target)
    SmallButton(
        if (confirmed) stringResource(R.string.import_copied) else idle,
        KettleTheme.colors.textSecondary,
    ) { handoff.copy(target, text()) }
    if (handoff.failed(target)) Note(stringResource(R.string.import_copyFailed))
}
