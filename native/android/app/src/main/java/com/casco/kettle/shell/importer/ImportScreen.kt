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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    start: ImportStart? = null,
    shareFailed: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val importer =
        remember {
            Importer(files, scope, actions).also {
                it.pasting = start == ImportStart.Paste
                // The file sheet's Share the brief already failed: say so here, beside the copy that covers it.
                if (shareFailed) it.handoff.sharedElsewhereFailed()
            }
        }
    // The file sheet's "Open a .yaml file" goes straight to the picker, once: not again on a rotation,
    // and not again after it came back cancelled, which leaves the row here to try again.
    var picked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (start == ImportStart.File && !picked) {
            picked = true
            importer.pickFile()
        }
    }
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
                Body(importer, files, content, start)
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
    start: ImportStart?,
) {
    val ready = importer.ready
    val applied = importer.applied
    if (ready == null && applied == null && start != null) {
        Sources(importer, files, content, start)
    } else if (ready == null && applied == null) {
        val packsFirst = packsFirst(files)
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
        RefusalAt(RefusalSite.File, importer)
        if (!packsFirst) AssistantBlock(importer, files.library, content)
        PasteSection(importer)
        if (!packsFirst) StarterPacks(content, importer)
        if (packsFirst) AssistantBlock(importer, files.library, content)
    }
    ready?.let { SourceCard(it.source) }
    // Each source draws its own refusal; only a Merge that wouldn't write lands here, under the preview's card.
    RefusalAt(RefusalSite.Preview, importer)
    if (ready != null) {
        val locale = LocalConfiguration.current.locales[0]
        PreviewSection(ready.preview, files.preferences?.unitSystem ?: deviceUnitSystem(locale), importer)
    }
    applied?.let { Applied(it, importer) }
}

/**
 * One source, as the file sheet's row named it (#413): the file, the paste, the packs, or the brief with
 * the paste its reply comes back by.
 */
@Composable
private fun Sources(
    importer: Importer,
    files: LatestFiles,
    content: ShippedContent,
    start: ImportStart,
) {
    when (start) {
        ImportStart.File -> {
            PickBox(
                stringResource(R.string.import_chooseFile),
                stringResource(R.string.import_chooseFileDetail),
                onClick = importer::pickFile,
                busy = importer.busy,
            )
            RefusalAt(RefusalSite.File, importer)
        }

        ImportStart.Paste -> {
            PasteSection(importer)
        }

        ImportStart.Packs -> {
            StarterPacks(content, importer)
        }

        ImportStart.Assistant -> {
            AssistantBlock(importer, files.library, content)
            PasteSection(importer)
        }
    }
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

/** The param that names the one source the Import sheet opens on. */
const val IMPORT_SOURCE_PARAM = "source"

/** Set by the file sheet when its share sheet didn't open, so Import opens saying so. */
const val IMPORT_SHARE_FAILED_PARAM = "shareFailed"

/** The sources the file sheet opens Import on (#413), by their [param] value. */
enum class ImportStart(
    val param: String,
) {
    File("file"),
    Paste("paste"),
    Packs("packs"),
    Assistant("assistant"),
    ;

    companion object {
        /** The start [param] names, or null for none or one unknown, which shows every source. */
        fun of(param: String?): ImportStart? = entries.firstOrNull { it.param == param }
    }
}
