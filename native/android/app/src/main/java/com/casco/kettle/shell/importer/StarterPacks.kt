package com.casco.kettle.shell.importer

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.casco.kettle.domain.Library
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.ShippedContent
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.list.ListHeaderRule
import com.casco.kettle.shell.list.ListRow
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing

/**
 * The bundled packs, as rows: a list of peers to choose between, where the pick boxes above are two
 * actions. Each row is the pack in the language being read, which is both what its counts are of and
 * what a tap previews, so the merge writes the language of the tap.
 */
@Composable
internal fun StarterPacks(
    content: ShippedContent,
    importer: Importer,
) {
    val colors = KettleTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val packs =
        remember(content, locale) { content.packs().map { it.id to content.pack(it.id, locale.toLanguageTag()) } }
    // A label rather than a heading: at heading size it was a peer of the rows under it, not their title.
    // RN's `label` type uppercases at draw time; the bundle's string is in sentence case.
    Column(Modifier.padding(top = Spacing.three)) {
        Text(
            stringResource(R.string.import_packs_title).uppercase(locale),
            Modifier.semantics { heading() },
            style = KettleType.label,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(Spacing.two))
        Text(stringResource(R.string.import_packs_detail), style = KettleType.small, color = colors.textSecondary)
    }
    ListHeaderRule()
    packs.forEachIndexed { index, (id, library) ->
        if (index > 0) ListRowSeparator()
        // Off while a file is being read: a pack picked in that window would be replaced by the file's
        // preview when its read lands, and Merge would then write the file. No label of its own: the
        // lines inside name it, and Voice Control matches the visible words.
        Column(Modifier.clickable(enabled = !importer.busy, role = Role.Button) { importer.reviewPack(id, library) }) {
            ListRow {
                val strings = packStrings(id)
                Column {
                    Text(stringResource(strings.name), style = KettleType.heading, color = colors.text)
                    Text(stringResource(strings.detail), style = KettleType.small, color = colors.textSecondary)
                    Text(packCounts(library), style = KettleType.small, color = colors.textSecondary)
                }
            }
        }
        // Outside the row's clickable, so the refusal and its Copy error aren't folded into the row's button.
        RefusalAt(RefusalSite.Pack(id), importer)
    }
}

/**
 * Whether the packs lead: nothing logged is what "new here" means everywhere else in the app, and it
 * costs no stored flag. Settled by the first read of the log and kept for the visit, so neither a log
 * that lands late nor a session that lands while the sheet is up reorders it under the reader. Before
 * that read it's the first-run order, as RN's empty store gave.
 */
@Composable
internal fun packsFirst(files: LatestFiles): Boolean {
    val log = files.sessionLog
    // Keyed on whether the log is read, not on the log: worked out again once, when it lands.
    return remember(log != null) { log?.logged.isNullOrEmpty() }
}

/**
 * "9 exercises · 3 workouts · 1 program": three plurals, not one sentence, since a pack with one program
 * and nine exercises has no single count to pluralise against.
 */
@Composable
private fun packCounts(library: Library): String =
    listOf(
        pluralText(
            R.plurals.import_packs_exerciseCount,
            R.string.import_packs_exerciseCount_zero,
            library.exercises.size,
        ),
        pluralText(R.plurals.import_packs_workoutCount, R.string.import_packs_workoutCount_zero, library.workouts.size),
        pluralText(R.plurals.import_packs_programCount, R.string.import_packs_programCount_zero, library.programs.size),
    ).joinToString(" · ")

/** A pack's row strings, as `import.packs.<id>.name` and `.detail` in the bundles. */
internal class PackStrings(
    @param:StringRes val name: Int,
    @param:StringRes val detail: Int,
)

/**
 * Pack [id]'s strings, spelled out rather than looked up by name, which lint refuses. A pack added to
 * `packs.json` without a line here fails `ImportScreenTest`, which lists every pack.
 */
internal fun packStrings(id: String): PackStrings =
    when (id) {
        "steady-strength" -> {
            PackStrings(R.string.import_packs_steady_strength_name, R.string.import_packs_steady_strength_detail)
        }

        "barbell-gym" -> {
            PackStrings(R.string.import_packs_barbell_gym_name, R.string.import_packs_barbell_gym_detail)
        }

        "kettlebell-basics" -> {
            PackStrings(R.string.import_packs_kettlebell_basics_name, R.string.import_packs_kettlebell_basics_detail)
        }

        else -> {
            error("pack $id has no strings")
        }
    }
