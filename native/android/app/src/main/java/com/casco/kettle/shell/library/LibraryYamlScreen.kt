package com.casco.kettle.shell.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.serializeLibraryYaml
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.form.SheetColumn
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/** What the route table draws at `Destination.LibraryYaml`: the whole file, or one exercise of it. */
@Composable
fun LibraryYamlRoute(params: Map<String, String> = emptyMap()) =
    LibraryYamlScreen(LocalLatestFiles.current, params[EXERCISE_PARAM])

/**
 * The library as YAML, read-only (#413): the app's own serialisation, so it reads as an export would
 * rather than as the file was last hand-written, numbered and coloured (#444). Weights are the file's
 * kilograms whatever the reader's units, and it says so above them. No editing here.
 *
 * With [exerciseId], Exercise detail's YAML (#444): that one exercise as a complete file, `version`,
 * the exercise, and empty workouts and programs, so Copy and Share hand over something that imports on
 * its own. An id the library doesn't have says so, as Exercise detail does.
 */
@Composable
fun LibraryYamlScreen(
    files: LatestFiles,
    exerciseId: String? = null,
) {
    val colors = KettleTheme.colors
    val library = files.library
    val exercise = exerciseId?.let { id -> library?.exercises?.firstOrNull { it.id == id } }
    val yaml =
        remember(library, exerciseId) {
            when {
                library == null -> null
                exerciseId == null -> serializeLibraryYaml(library)
                else -> exercise?.let { serializeLibraryYaml(Library(1, listOf(it), emptyList(), emptyList())) }
            }
        }
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            SheetColumn
                .padding(horizontal = Spacing.three)
                .padding(bottom = Spacing.four)
                .navigationBarsPadding(),
        ) {
            if (exerciseId == null) {
                FileNameTitle()
                Text(stringResource(R.string.file_unitsNote), style = KettleType.small, color = colors.textSecondary)
            } else {
                ExerciseHeader(exercise?.name, files)
            }
            if (yaml == null && library != null) {
                Text(
                    stringResource(R.string.exerciseDetail_missing),
                    Modifier.padding(top = Spacing.three),
                    style = KettleType.default,
                    color = colors.textSecondary,
                )
            }
            yaml?.let {
                YamlCode(it, Modifier.padding(top = Spacing.three))
                if (exerciseId != null) SendActions(it)
            }
        }
    }
}

/**
 * The exercise's name, the user's, over "As YAML", then what the document is: kilograms, as the file
 * keeps them, said against the pounds the app shows where it does, and written out fresh.
 */
@Composable
private fun ExerciseHeader(
    name: String?,
    files: LatestFiles,
) {
    val colors = KettleTheme.colors
    val units = files.unitsOrDevice()
    name?.let { Text(it, style = KettleType.smallMedium, color = colors.textSecondary) }
    Text(
        stringResource(R.string.yamlView_asYaml),
        Modifier.padding(top = Spacing.half).semantics { heading() },
        style = KettleType.subtitle,
        color = colors.text,
    )
    val weights = if (units == UnitSystem.IMPERIAL) R.string.yamlView_unitsImperial else R.string.yamlView_unitsMetric
    // Two texts, not one joined with a space: ja ends a sentence with 。 and wants no space after it.
    Text(
        stringResource(weights),
        Modifier.padding(top = Spacing.two),
        style = KettleType.small,
        color = colors.textSecondary,
    )
    Text(stringResource(R.string.yamlView_fresh), style = KettleType.small, color = colors.textSecondary)
}
