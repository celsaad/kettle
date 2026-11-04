package com.casco.kettle.shell.importer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.MergeError
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.ShippedContent
import com.casco.kettle.shell.form.FieldKind
import com.casco.kettle.shell.form.FieldOptions
import com.casco.kettle.shell.form.FormField
import com.casco.kettle.shell.form.SheetColumn
import com.casco.kettle.shell.form.dashedBorder
import com.casco.kettle.shell.i18n.deviceUnitSystem
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * Import (#244), as RN's `import.tsx`: a file, a paste or a starter pack, previewed, then merged by id
 * with whole-object replace on confirm. Nothing reaches the library before Merge, and a refusal writes
 * nothing. The repair loop's copy, paste-from-clipboard and the assistant brief are the next slice.
 */
@Composable
fun ImportScreen(
    files: LatestFiles,
    content: ShippedContent,
    actions: ImportActions,
) {
    val scope = rememberCoroutineScope()
    val importer = remember { Importer(files, scope, actions) }
    val packsFirst = packsFirst(files)
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
                Body(importer, files, content, packsFirst)
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
        if (packsFirst) StarterPacks(content, importer)
        Sources(importer)
        if (!packsFirst) StarterPacks(content, importer)
    }
    ready?.let { SourceCard(it.source) }
    importer.refusal?.let {
        Text(
            refusalText(it),
            Modifier.padding(top = Spacing.two),
            style = KettleType.small,
            color = KettleTheme.colors.accentText,
        )
    }
    if (ready != null) {
        val locale = LocalConfiguration.current.locales[0]
        PreviewSection(ready.preview, files.preferences?.unitSystem ?: deviceUnitSystem(locale), importer)
    }
    applied?.let { Applied(it, importer) }
}

/** The file and the paste box, each a dashed box: two actions, unlike the packs' list of peers. */
@Composable
private fun Sources(importer: Importer) {
    val colors = KettleTheme.colors
    PickBox(
        stringResource(R.string.import_chooseFile),
        stringResource(R.string.import_chooseFileDetail),
        onClick = importer::pickFile,
        busy = importer.busy,
    )
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
            { importer.pasted = it },
            name = stringResource(R.string.import_pastePlaceholder),
            placeholder = stringResource(R.string.import_pastePlaceholder),
            FieldOptions(kind = FieldKind.Code, minHeight = 132.dp, fill = colors.backgroundElement),
        )
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

/**
 * A dashed source box, off and spinning while [busy]. No leading glyph, deliberately: RN's two
 * outlined squares read as unchecked checkboxes, implying a selection these rows never had, and the
 * labels already tell them apart.
 */
@Composable
private fun PickBox(
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
) {
    val colors = KettleTheme.colors
    Row(
        Modifier
            .padding(top = Spacing.three - 2.dp)
            .fillMaxWidth()
            .clip(BoxShape)
            .background(colors.backgroundElement)
            .dashedBorder(1.dp, colors.border, 14.dp)
            .clickable(enabled = !busy, role = Role.Button, onClick = onClick)
            .then(modifier)
            .padding(Spacing.two + 4.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = KettleType.heading, color = colors.text)
            Text(detail, style = KettleType.small, color = colors.textSecondary)
        }
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = colors.accentText, strokeWidth = 2.dp)
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

/**
 * Every reason a file is refused, worded here: the parser and the merge return descriptors. The ids and
 * the details are the file's or the platform's own and are never translated; the frame around them is.
 */
@Composable
private fun refusalText(refusal: ImportRefusal): String =
    when (refusal) {
        is ImportRefusal.InvalidYaml -> {
            stringResource(R.string.import_error_invalidYaml, refusal.detail)
        }

        is ImportRefusal.SchemaMismatch -> {
            stringResource(R.string.import_error_schemaMismatch, refusal.detail)
        }

        is ImportRefusal.Merge -> {
            mergeErrorText(refusal.error)
        }

        ImportRefusal.LibraryNotLoaded -> {
            stringResource(R.string.import_libraryNotLoaded)
        }

        is ImportRefusal.ReadFailed -> {
            stringResource(R.string.import_error_readFailed, refusal.detail)
        }

        is ImportRefusal.SaveFailed -> {
            stringResource(R.string.import_error_saveFailed, refusal.detail)
        }
    }

@Composable
private fun mergeErrorText(error: MergeError): String =
    when (error) {
        // Arguments in the order the generated resource's comment gives, which is by name, not position.
        is MergeError.UnknownExercise -> {
            stringResource(R.string.import_error_unknownExercise, error.exerciseId, error.workoutId)
        }

        // The day is the user's own label ("Monday", "Push A"), so it renders verbatim inside the frame.
        is MergeError.UnknownWorkout -> {
            val week =
                error.day?.ifEmpty { null }?.let {
                    stringResource(
                        R.string.import_error_weekWithDay,
                        it,
                        "${error.week}",
                    )
                }
                    ?: "${error.week}"
            stringResource(R.string.import_error_unknownWorkout, error.programId, week, error.workoutId)
        }
    }

internal val BoxShape = RoundedCornerShape(14.dp)

private const val KILOBYTE = 1024
