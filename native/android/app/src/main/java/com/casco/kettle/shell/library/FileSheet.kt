package com.casco.kettle.shell.library

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casco.kettle.domain.AssistantBrief
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Session
import com.casco.kettle.shell.R
import com.casco.kettle.shell.export.FileShare
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.files.ShippedContent
import com.casco.kettle.shell.files.StoragePaths
import com.casco.kettle.shell.form.SheetColumn
import com.casco.kettle.shell.i18n.deviceUnitSystem
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.importer.IMPORT_SHARE_FAILED_PARAM
import com.casco.kettle.shell.importer.IMPORT_SOURCE_PARAM
import com.casco.kettle.shell.importer.ImportStart
import com.casco.kettle.shell.importer.briefWords
import com.casco.kettle.shell.importer.packStrings
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.LocalNavigator
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.settings.ActionRow
import com.casco.kettle.shell.settings.BackupActions
import com.casco.kettle.shell.settings.ExportActions
import com.casco.kettle.shell.settings.Outcome
import com.casco.kettle.shell.settings.PreferencesSaver
import com.casco.kettle.shell.settings.rememberBackupActions
import com.casco.kettle.shell.settings.rememberCanChooseFolder
import com.casco.kettle.shell.system.shareText
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import kotlinx.coroutines.launch

/** The library's file name on disk, which the sheet is titled with and Library's header links by. */
const val LIBRARY_FILE_NAME = "exercises.yaml"

/** What the file sheet does outside itself: open a route, share a file, and the backup folder's IO. */
class FileSheetActions(
    val open: (Route) -> Unit,
    val export: ExportActions,
    /** Null on a device with no folder picker, where the backup row would promise what can't work. */
    val backup: BackupActions?,
    /** False where no share sheet opened. */
    val shareText: (String) -> Boolean,
    /** The library's JSON Schema, which the brief carries. */
    val schema: () -> String,
)

/** What the route table draws at `Destination.YourFile`: the sheet on SAF, the share sheet and the shell. */
@Composable
fun FileSheetRoute() {
    val activity = LocalActivity.current ?: return
    val navigator = LocalNavigator.current
    val paths = remember { StoragePaths(activity.filesDir) }
    val share = remember { FileShare(activity, paths) }
    val backup = rememberBackupActions(activity, paths)
    val canChooseFolder = rememberCanChooseFolder(activity)
    val content = remember { ShippedContent(activity.assets) }
    val packs = remember { content.packs().map { it.id } }
    val actions =
        remember(backup, navigator) {
            FileSheetActions(
                open = navigator::push,
                export = ExportActions(library = share::library, history = share::history),
                backup = backup.takeIf { canChooseFolder },
                shareText = activity::shareText,
                schema = content::librarySchema,
            )
        }
    FileSheetScreen(LocalLatestFiles.current, packs, actions)
}

/**
 * Your file (#413): everything that moves the library in or out, gathered from Import, Settings and
 * History. The assistant first, since that is the trip the file's format is for; then the three ways
 * in, which open Import on that one source, so its preview and Merge are the only path to a write; then
 * the ways out, and the backup folder. [packIds] are the starter packs, named on their row.
 */
@Composable
fun FileSheetScreen(
    files: LatestFiles,
    packIds: List<String>,
    actions: FileSheetActions,
) {
    val colors = KettleTheme.colors
    val scope = rememberCoroutineScope()
    var failure by remember { mutableStateOf<String?>(null) }
    val share: (suspend () -> String?) -> Unit = { exporter ->
        failure = null
        scope.launch { failure = exporter() }
    }
    val importOn = { start: ImportStart ->
        actions.open(Route(Destination.Import, mapOf(IMPORT_SOURCE_PARAM to start.param)))
    }
    val sessions = files.sessionLog?.sessions.orEmpty()
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            SheetColumn
                .padding(horizontal = Spacing.three)
                .padding(bottom = Spacing.four)
                .navigationBarsPadding(),
        ) {
            FileNameTitle()
            files.library?.let { Text(counts(it), style = KettleType.small, color = colors.textSecondary) }
            AssistantCard(files.library, actions) {
                actions.open(
                    Route(
                        Destination.Import,
                        mapOf(IMPORT_SOURCE_PARAM to ImportStart.Assistant.param, IMPORT_SHARE_FAILED_PARAM to "true"),
                    ),
                )
            }
            WaysIn(packIds, importOn)
            WaysOut(sessions, actions, share)
            failure?.let { Outcome(stringResource(R.string.settings_exportFailed, it), accent = true) }
            actions.backup?.let { backup ->
                val units = deviceUnitSystem(LocalConfiguration.current.locales[0])
                val save = remember(files, units) { PreferencesSaver(files, units, scope) }
                BackupRow(files.preferences?.backupFolderUri, files.sessionLog?.sessions, save, scope, backup)
            }
        }
    }
}

/** The three ways in, each Import on that one source; the packs' row names them. */
@Composable
private fun WaysIn(
    packIds: List<String>,
    importOn: (ImportStart) -> Unit,
) = Column(Modifier.padding(top = Spacing.two)) {
    ActionRow(stringResource(R.string.file_openFile), stringResource(R.string.file_openFileDetail)) {
        importOn(ImportStart.File)
    }
    ActionRow(stringResource(R.string.file_paste), stringResource(R.string.file_pasteDetail)) {
        importOn(ImportStart.Paste)
    }
    ActionRow(
        stringResource(R.string.file_packs),
        packIds.map { stringResource(packStrings(it).name) }.joinToString(" · "),
    ) { importOn(ImportStart.Packs) }
}

/** Take it with you: the YAML view, and the library and the log out through the share sheet. */
@Composable
private fun WaysOut(
    sessions: List<Session>,
    actions: FileSheetActions,
    share: (suspend () -> String?) -> Unit,
) {
    Text(
        stringResource(R.string.file_takeItWithYou),
        Modifier.padding(top = Spacing.four, bottom = Spacing.one).semantics { heading() },
        style = KettleType.heading,
        color = KettleTheme.colors.text,
    )
    ActionRow(stringResource(R.string.file_openAsYaml), stringResource(R.string.file_openAsYamlDetail)) {
        actions.open(Route(Destination.LibraryYaml))
    }
    ActionRow(stringResource(R.string.file_shareFile), stringResource(R.string.settings_exportLibraryDetail)) {
        share(actions.export.library)
    }
    // Disabled rather than hidden with nothing logged, as Settings' row: it answers "is my log
    // exportable?" for someone asking before they start.
    ActionRow(
        stringResource(R.string.file_exportLog),
        if (sessions.isEmpty()) {
            stringResource(R.string.settings_exportHistoryEmpty)
        } else {
            pluralText(
                R.plurals.settings_exportHistoryDetail,
                R.string.settings_exportHistoryDetail_zero,
                sessions.size,
            )
        },
        enabled = sessions.isNotEmpty(),
    ) { share { actions.export.history(sessions) } }
}

/** "17 exercises · 7 workouts · 2 programs": exercises as Library lists them, rest blocks not among them. */
@Composable
private fun counts(library: Library): String {
    val exercises = library.exercises.count { it.config !is ExerciseConfig.Rest }
    // Placeholders are numbered alphabetically: exercises, programs, workouts.
    return stringResource(
        R.string.file_counts,
        pluralText(R.plurals.library_exerciseCount, R.string.library_exerciseCount_zero, exercises),
        pluralText(R.plurals.programs_count, R.string.programs_count_zero, library.programs.size),
        pluralText(R.plurals.build_workoutCount, R.string.build_workoutCount_zero, library.workouts.size),
    )
}

/** The file's name as a sheet's title, in monospace: this one's, and the YAML view's. */
@Composable
internal fun FileNameTitle() =
    Text(
        LIBRARY_FILE_NAME,
        Modifier.semantics { heading() },
        style = KettleType.subtitle.copy(fontFamily = FontFamily.Monospace, fontSize = 22.sp),
        color = KettleTheme.colors.text,
    )

/**
 * The assistant, a card since it's the one thing here that isn't a row of peers: what it does, and
 * Share the brief, which opens the share sheet on the brief itself, as Import's does. The reply comes
 * back by Paste YAML below. Where no share sheet opens, it falls back to Import's assistant source
 * ([onFallback]), opened saying the share sheet didn't, above the copy button that covers it. Off,
 * and drawn dimmed, until [library] is read: the brief is the ids in it.
 */
@Composable
private fun AssistantCard(
    library: Library?,
    actions: FileSheetActions,
    onFallback: () -> Unit,
) {
    val colors = KettleTheme.colors
    val words = briefWords()
    // Once per sheet, as Import's block reads it, rather than off the assets on every press.
    val schema = remember(actions) { actions.schema() }
    val onShare = {
        library?.let {
            if (!actions.shareText(AssistantBrief.build(it, words, schema))) onFallback()
        }
        Unit
    }
    Column(
        Modifier
            .padding(top = Spacing.three)
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.backgroundElement)
            .border(1.dp, colors.border, CardShape)
            .padding(Spacing.three),
        verticalArrangement = Arrangement.spacedBy(Spacing.two),
    ) {
        Text(
            stringResource(R.string.file_assistantTitle),
            Modifier.semantics { heading() },
            style = KettleType.heading,
            color = colors.text,
        )
        Text(stringResource(R.string.file_assistantBody), style = KettleType.small, color = colors.textSecondary)
        // `accent` under white is 3.64:1, which this 20sp label clears as large text (theme.ts).
        Text(
            stringResource(R.string.file_shareBrief),
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(ButtonShape)
                .background(colors.accent)
                .alpha(if (library != null) 1f else DISABLED_ALPHA)
                .clickable(enabled = library != null, role = Role.Button, onClick = onShare)
                .padding(vertical = Spacing.two),
            style = KettleType.heading.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onAccent,
            textAlign = TextAlign.Center,
        )
    }
}

private val CardShape = RoundedCornerShape(18.dp)
private val ButtonShape = RoundedCornerShape(14.dp)

/** A control that can't act yet, as Settings' rows draw one. */
private const val DISABLED_ALPHA = 0.5f
