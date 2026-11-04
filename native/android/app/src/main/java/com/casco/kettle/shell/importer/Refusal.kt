package com.casco.kettle.shell.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.MergeError
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * A refusal, and when it's about the content, the same refusal framed for whoever wrote the YAML, to
 * copy back to them: the merge's names both ends of the break, and the loop only closes if that
 * sentence can get back without being retyped from a screenshot. The rejected YAML isn't in it: an
 * assistant that just wrote it still has it, and a hand-edited file is on disk.
 */
@Composable
internal fun RefusalBlock(
    refusal: ImportRefusal,
    importer: Importer,
) {
    val colors = KettleTheme.colors
    val message = refusalText(refusal)
    Column(
        Modifier.padding(top = Spacing.two),
        verticalArrangement = Arrangement.spacedBy(Spacing.two - 2.dp),
    ) {
        Text(message, style = KettleType.small, color = colors.accentText)
        if (!refusal.aboutContent) return@Column
        val report = "${stringResource(R.string.import_repairPrompt)}\n\n$message"
        CopyAction(importer.handoff, CopyTarget.Report, stringResource(R.string.import_copyError)) { report }
    }
}

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
