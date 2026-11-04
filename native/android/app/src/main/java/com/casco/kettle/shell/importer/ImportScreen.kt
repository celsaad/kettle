package com.casco.kettle.shell.importer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.ShippedContent
import com.casco.kettle.shell.form.SheetColumn
import com.casco.kettle.shell.i18n.deviceUnitSystem
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * Import (#244), as RN's `import.tsx`: a file, a paste or a starter pack, previewed, then merged by id
 * with whole-object replace on confirm. Nothing reaches the library before Merge, and a refusal writes
 * nothing. Around that, the loop with an assistant: the brief out, its YAML back in from the clipboard,
 * and a refusal copied back to it.
 */
@Composable
fun ImportScreen(
    files: LatestFiles,
    content: ShippedContent,
    actions: ImportActions,
) {
    val scope = rememberCoroutineScope()
    val importer = remember { Importer(files, scope, actions) }
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.three, end = Spacing.three, bottom = Spacing.four),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(SheetColumn) {
                Text(
                    stringResource(R.string.import_title),
                    Modifier.semantics { heading() },
                    style = KettleType.subtitle,
                    color = KettleTheme.colors.text,
                )
                Body(importer, files, content, packsFirst(files))
            }
        }
        Footer(importer)
    }
}

@Composable
private fun Body(
    importer: Importer,
    files: LatestFiles,
    content: ShippedContent,
    packsFirst: Boolean,
) {
    val ready = importer.ready
    val applied = importer.applied
    if (ready == null && applied == null) {
        // First for someone who has logged nothing: the one offer here that costs no file and no typing.
        // A returning user came to import a file, and demoting that would trade a real job for a nicety.
        // The brief moves the other way: for a returning user it sits between the file and the paste,
        // the order of the trip out and back; for a first-timer it's last, being the most technical
        // thing here and the least likely to be wanted.
        if (packsFirst) StarterPacks(content, importer)
        PickBox(
            stringResource(R.string.import_chooseFile),
            stringResource(R.string.import_chooseFileDetail),
            onClick = importer::pickFile,
            busy = importer.busy,
        )
        if (!packsFirst) AssistantBlock(importer, files.library, content)
        PasteSection(importer)
        if (!packsFirst) StarterPacks(content, importer)
        if (packsFirst) AssistantBlock(importer, files.library, content)
    }
    ready?.let { SourceCard(it.source) }
    importer.refusal?.let { RefusalBlock(it, importer) }
    if (ready != null) {
        val locale = LocalConfiguration.current.locales[0]
        PreviewSection(ready.preview, files.preferences?.unitSystem ?: deviceUnitSystem(locale), importer)
    }
    applied?.let { Applied(it, importer) }
}

/** What the preview says it's about to merge: the file's name, "Pasted YAML", or the pack's name. */
@Composable
private fun SourceCard(source: ImportSource) {
    val colors = KettleTheme.colors
    val (title, detail) =
        when (source) {
            is ImportSource.File -> {
                source.name to
                    stringResource(R.string.import_pickedFrom, sizeLabel(source.sizeBytes))
            }

            is ImportSource.Pasted -> {
                stringResource(R.string.import_pastedTitle) to
                    pluralText(R.plurals.import_pastedLines, R.string.import_pastedLines_zero, source.lines)
            }

            is ImportSource.Pack -> {
                stringResource(packStrings(source.id).name) to
                    stringResource(R.string.import_packs_sourceDetail)
            }
        }
    Column(
        Modifier
            .padding(top = Spacing.three - 2.dp)
            .fillMaxWidth()
            .clip(BoxShape)
            .background(colors.backgroundElement)
            .border(1.dp, colors.border, BoxShape)
            .padding(Spacing.two + 4.dp),
    ) {
        Text(title, style = KettleType.heading, color = colors.text)
        Text(detail, style = KettleType.small, color = colors.textSecondary)
    }
}

/** RN's: bytes under a kilobyte, one decimal of kilobytes past it, the same in every language. */
private fun sizeLabel(bytes: Long): String =
    if (bytes < KILOBYTE) "$bytes B" else "${"%.1f".format(java.util.Locale.ROOT, bytes / KILOBYTE.toDouble())} KB"

internal val BoxShape = RoundedCornerShape(14.dp)

private const val KILOBYTE = 1024
