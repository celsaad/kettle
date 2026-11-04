package com.casco.kettle.shell.importer

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.AssistantBrief
import com.casco.kettle.domain.Library
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.ShippedContent
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
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
        CopyButton(
            copyLabel(importer, CopyTarget.Brief, stringResource(R.string.import_copyBriefInstead)),
            colors.textSecondary,
        ) { importer.handoff.copy(CopyTarget.Brief, brief()) }
        when {
            importer.handoff.shareFailed -> Note(stringResource(R.string.import_shareFailed))

            importer.handoff.copied ==
                Copy(
                    CopyTarget.Brief,
                    took = false,
                )
            -> Note(stringResource(R.string.import_copyFailed))
        }
    }
}

@Composable
private fun briefWords() =
    AssistantBrief.Words(
        prompt = stringResource(R.string.import_brief_prompt),
        inventoryHeading = stringResource(R.string.import_brief_inventoryHeading),
        exercisesLabel = stringResource(R.string.import_brief_exercisesLabel),
        workoutsLabel = stringResource(R.string.import_brief_workoutsLabel),
        programsLabel = stringResource(R.string.import_brief_programsLabel),
        none = stringResource(R.string.import_brief_none),
        schemaHeading = stringResource(R.string.import_brief_schemaHeading),
    )

/** [idle], or "Copied" while [target]'s last copy took: keyed by button, so only the pressed one says so. */
@Composable
internal fun copyLabel(
    importer: Importer,
    target: CopyTarget,
    idle: String,
): String = if (importer.handoff.copied == Copy(target, took = true)) stringResource(R.string.import_copied) else idle

/**
 * The outlined small button the copies and the clipboard paste share. A polite live region: its own
 * words switch to "Copied" while focus stays on it, which TalkBack wouldn't otherwise re-read. No label
 * of its own, since its text names it.
 */
@Composable
internal fun CopyButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(CopyShape)
            .border(1.dp, KettleTheme.colors.border, CopyShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = Spacing.two + 2.dp),
        contentAlignment = Alignment.CenterStart,
    ) { Text(text, style = KettleType.smallMedium, color = color) }
}

/** A line beside a control saying why it didn't do what it says, without taking the screen over. */
@Composable
internal fun Note(text: String) = Text(text, style = KettleType.small, color = KettleTheme.colors.textSecondary)

private val CopyShape = RoundedCornerShape(12.dp)
