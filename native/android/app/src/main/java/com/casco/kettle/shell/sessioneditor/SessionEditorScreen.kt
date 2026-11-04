package com.casco.kettle.shell.sessioneditor

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.EntryDraft
import com.casco.kettle.domain.EntryField
import com.casco.kettle.domain.EntryProblem
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionEditing
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.SetForm
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.editedSession
import com.casco.kettle.domain.entryFields
import com.casco.kettle.domain.exerciseName
import com.casco.kettle.domain.firstProblem
import com.casco.kettle.domain.isZeroForm
import com.casco.kettle.domain.sessionEditing
import com.casco.kettle.domain.sessionEntryResult
import com.casco.kettle.domain.sessionStartDay
import com.casco.kettle.domain.workoutNameFor
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.files.writeOrRefusal
import com.casco.kettle.shell.form.EditorButtons
import com.casco.kettle.shell.form.FormField
import com.casco.kettle.shell.history.entryResultText
import com.casco.kettle.shell.i18n.deviceUnitSystem
import com.casco.kettle.shell.i18n.formatMonthBadge
import com.casco.kettle.shell.library.ExerciseBadge
import com.casco.kettle.shell.list.TextAction
import com.casco.kettle.shell.rn.FileKind
import com.casco.kettle.shell.rn.JsBridge
import com.casco.kettle.shell.theme.DialogAction
import com.casco.kettle.shell.theme.KettleAlertDialog
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.card
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone

/** Where the session editor is registered, as History's Edit pushes it, with the session's `id`. */
const val SESSION_EDITOR_PATH = "/session-editor"

/** What `shellRoutes` registers at [SESSION_EDITOR_PATH], in every build. */
@Composable
fun SessionEditorRoute(params: Map<String, String>) =
    SessionEditorScreen(
        params["id"],
        LocalLatestFiles.current,
        TimeZone.currentSystemDefault(),
        SessionEditorActions(close = JsBridge::back, sessionsChanged = { JsBridge.filesChanged(FileKind.SESSIONS) }),
    )

/**
 * What the editor asks of the shell, as parameters so a test hears each one. [sessionsChanged] follows
 * a write, so RN's stores re-read the log.
 */
class SessionEditorActions(
    val close: () -> Unit,
    val sessionsChanged: () -> Unit,
)

/**
 * Corrects what a finished session logged (#239), as RN's `session-editor.tsx` did until the sessions
 * flip (#240) made this the only writer of an edited session file: a mis-typed rep count, a load
 * entered before the plates changed, an RPE tapped in the wrong column.
 *
 * Save-at-the-end, as the other editors: Remove edits this screen's copy, so closing puts a mistaken
 * removal back, and the one write is the session's file, whole, when Save asks for it. Reads the log
 * with its zero work taken out, as RN's did, so the file it writes is clean too.
 */
@Composable
fun SessionEditorScreen(
    id: String?,
    files: LatestFiles,
    zone: TimeZone,
    actions: SessionEditorActions,
) {
    val locale = LocalConfiguration.current.locales[0]
    val units = files.preferences?.unitSystem ?: deviceUnitSystem(locale)
    val library = files.library ?: EmptyLibrary
    val logged = files.sessionLog?.logged
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.three)
                .padding(bottom = Spacing.four)
                .navigationBarsPadding(),
        ) {
            Text(
                stringResource(R.string.sessionEditor_title),
                Modifier.semantics { heading() },
                style = KettleType.subtitle,
                color = KettleTheme.colors.text,
            )
            // Nothing more before the log is read, so a session that is there is never called gone.
            if (logged == null) return@Column
            // Worked out from the first read of the log and kept, as RN's editor copied its drafts once:
            // the shell reads again while the sheet is up, and from then on what's typed is the user's.
            val editing = remember(id, units) { sessionEditing(logged, id, units) }
            logged.firstOrNull { it.id == id }?.let { Subtitle(it, library, zone) }
            when (editing) {
                SessionEditing.NotFound -> {
                    Notice(R.string.sessionEditor_notFoundTitle, R.string.sessionEditor_notFoundBody)
                }

                SessionEditing.Running -> {
                    Notice(R.string.sessionEditor_runningTitle, R.string.sessionEditor_runningBody)
                }

                SessionEditing.NothingToEdit -> {
                    Notice(R.string.sessionEditor_emptyTitle, R.string.sessionEditor_emptyBody)
                }

                is SessionEditing.Editable -> {
                    val editor = remember(editing) { Editor(editing, units, files, scope, actions) }
                    Editing(editor, library, units)
                }
            }
        }
    }
}

/** Which session this is: its workout, verbatim, and the day it started. Nothing where that isn't a day. */
@Composable
private fun Subtitle(
    session: Session,
    library: Library,
    zone: TimeZone,
) {
    val day = sessionStartDay(session, zone) ?: return
    val locale = LocalConfiguration.current.locales[0]
    // The workout's own name, interpolated rather than translated.
    val name = workoutNameFor(session, library) ?: stringResource(R.string.format_adHocSession)
    Secondary(
        stringResource(R.string.sessionEditor_subtitle, day.dayOfMonth, formatMonthBadge(day, locale), name),
        Modifier.padding(top = 2.dp),
    )
}

/**
 * One session's edit, from the first keystroke to the write: the drafts, the entry whose last set is
 * being asked about, and why Save refused, the form's problem or the disk's.
 */
private class Editor(
    private val editing: SessionEditing.Editable,
    private val units: UnitSystem,
    private val files: LatestFiles,
    private val scope: CoroutineScope,
    private val actions: SessionEditorActions,
) {
    var drafts by mutableStateOf(editing.drafts)
        private set
    var confirming by mutableStateOf<Int?>(null)
        private set
    var problem by mutableStateOf<EntryProblem?>(null)
        private set
    var failure by mutableStateOf<String?>(null)
        private set
    private var saving = false

    /**
     * Set once the write has closed the sheet, and never cleared. RN takes a while to dismiss it, and
     * a Save in between would write again, its second close popping the screen under the sheet.
     */
    private var closed = false

    val readOnly get() = editing.readOnly

    /** Writes nothing: the drafts are this screen's copy, and go with it. */
    fun cancel() = actions.close()

    fun setField(
        draft: Int,
        row: Int,
        field: EntryField,
        text: String,
    ) = update(draft) { forms ->
        val edited = forms[row].copy(values = forms[row].values + (field to text))
        forms.mapIndexed { i, form -> if (i == row) edited else form }
    }

    /**
     * One set goes as asked. The last one takes the exercise with it, a bigger thing than the button
     * says, so that one is asked about first.
     */
    fun remove(
        draft: Int,
        row: Int,
    ) {
        if (drafts[draft].forms.size > 1) drop(draft, row) else confirming = draft
    }

    fun dismissRemove() {
        confirming = null
    }

    fun confirmRemove(draft: Int) {
        confirming = null
        drop(draft, 0)
    }

    /**
     * Checks every entry before writing anything, so a refusal leaves the file as it was rather than
     * half-corrected. Not cancelled with the screen once the write starts: RN's stores must hear a
     * file that changed, or they keep listing the session as it was.
     */
    fun save() {
        if (saving || closed) return
        failure = null
        problem = firstProblem(drafts)
        if (problem != null) return
        val edited = editedSession(editing.session, drafts, units)
        saving = true
        scope.launch {
            withContext(NonCancellable) {
                failure =
                    writeOrRefusal {
                        files.writeSession(edited)
                        actions.sessionsChanged()
                        closed = true
                        actions.close()
                    }
                saving = false
            }
        }
    }

    private fun drop(
        draft: Int,
        row: Int,
    ) = update(draft) { forms -> forms.filterIndexed { i, _ -> i != row } }

    private fun update(
        draft: Int,
        change: (List<SetForm>) -> List<SetForm>,
    ) {
        drafts = drafts.mapIndexed { i, entry -> if (i == draft) entry.copy(forms = change(entry.forms)) else entry }
    }
}

@Composable
private fun Editing(
    editor: Editor,
    library: Library,
    units: UnitSystem,
) {
    val colors = KettleTheme.colors
    Secondary(stringResource(R.string.sessionEditor_intro), Modifier.padding(top = Spacing.two))
    editor.drafts.forEachIndexed { index, draft ->
        // An entry whose last set was removed goes on save; until then it is simply not shown.
        if (draft.forms.isNotEmpty()) {
            val name = exerciseName(library.exercises, draft.entry.exercise)
            EntryCard(name, draft.entry) { DraftRows(editor, index, draft, units) }
        }
    }
    // Listed rather than hidden: a session that lost its EMOM on the way in would read as if the log had.
    val resources = LocalResources.current
    for (entry in editor.readOnly) {
        EntryCard(exerciseName(library.exercises, entry.exercise), entry) {
            Text(
                resources.entryResultText(sessionEntryResult(entry)),
                style = KettleType.smallMedium,
                color = colors.text,
            )
            Secondary(stringResource(R.string.sessionEditor_readOnlyNote))
        }
    }
    val refusal =
        editor.problem?.let { problemText(it) }
            ?: editor.failure?.let { stringResource(R.string.common_saveFailed, it) }
    refusal?.let { Text(it, Modifier.padding(top = Spacing.two), style = KettleType.small, color = colors.accentText) }
    EditorButtons(stringResource(R.string.common_save), onCancel = editor::cancel, onPrimary = editor::save)

    editor.confirming?.let { draft ->
        RemoveDialog(
            exerciseName(library.exercises, editor.drafts[draft].entry.exercise),
            onDismiss = editor::dismissRemove,
            onConfirm = { editor.confirmRemove(draft) },
        )
    }
}

@Composable
private fun EntryCard(
    name: String,
    entry: SessionEntry,
    content: @Composable () -> Unit,
) = Column(
    Modifier
        .padding(top = Spacing.three - 2.dp)
        .fillMaxWidth()
        .card(16.dp)
        .padding(Spacing.two + 4.dp),
    verticalArrangement = Arrangement.spacedBy(Spacing.one),
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.two)) {
        // User data, verbatim. It takes the leftover width and ellipsizes rather than push the badge off.
        Text(
            name,
            Modifier.weight(1f),
            style = KettleType.heading,
            color = KettleTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ExerciseBadge(entry)
    }
    content()
}

/** One entry's rows, each its fields, its Remove, and the zero hint while saving would drop it. */
@Composable
private fun DraftRows(
    editor: Editor,
    index: Int,
    draft: EntryDraft,
    units: UnitSystem,
) {
    val colors = KettleTheme.colors
    val fields = entryFields(draft.entry).orEmpty()
    // The one-result types log one row for the whole entry, with no "Set 1" to put on it.
    val perSet = draft.entry is SessionEntry.Reps || draft.entry is SessionEntry.TimedHold
    val lastSet = draft.forms.size == 1
    draft.forms.forEachIndexed { row, form ->
        val setLabel = stringResource(R.string.sessionEditor_setLabel, row + 1)
        Column(
            Modifier
                .padding(top = Spacing.one)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.one),
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = 1.dp).background(colors.border))
            if (perSet) Text(setLabel.uppercase(), style = KettleType.code, color = colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.two - 2.dp)) {
                for (field in fields) {
                    val label = fieldLabel(field, units)
                    Column(Modifier.weight(1f)) {
                        Secondary(label, Modifier.padding(bottom = 4.dp))
                        FormField(
                            form.values[field].orEmpty(),
                            { editor.setField(index, row, field, it) },
                            // Every row has the same labels, so the set is what tells them apart.
                            name = if (perSet) "$setLabel · $label" else label,
                            placeholder = if (field.optional) "—" else "0",
                        )
                    }
                }
            }
            val remove =
                stringResource(if (lastSet) R.string.sessionEditor_removeEntry else R.string.sessionEditor_removeSet)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextAction(
                    remove,
                    KettleType.small,
                    colors.textSecondary,
                    onClick = { editor.remove(index, row) },
                    // As the fields: "Remove set" reads the same on every row.
                    label = if (lastSet) null else "$remove · $setLabel",
                )
            }
            // Typing 0 is how a set gets cleared, and it goes on save without Remove's confirm, so it's
            // said here, and only while it applies.
            if (isZeroForm(draft.entry, form, units)) Secondary(stringResource(R.string.sessionEditor_zeroRemoved))
        }
    }
}

@Composable
private fun RemoveDialog(
    name: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    KettleAlertDialog(
        title = stringResource(R.string.sessionEditor_removeEntryConfirmTitle),
        body = stringResource(R.string.sessionEditor_removeEntryConfirmBody, name),
        onDismissRequest = onDismiss,
        confirm = DialogAction(stringResource(R.string.common_delete), onConfirm),
        dismiss = DialogAction(stringResource(R.string.common_cancel), onDismiss),
    )
}

/** Why the editor offers nothing to edit. */
@Composable
private fun Notice(
    @StringRes title: Int,
    @StringRes body: Int,
) = Column(Modifier.padding(top = Spacing.three), verticalArrangement = Arrangement.spacedBy(Spacing.one)) {
    Text(
        stringResource(title),
        Modifier.semantics {
            heading()
        },
        style = KettleType.heading,
        color = KettleTheme.colors.text,
    )
    Secondary(stringResource(body))
}

@Composable
private fun Secondary(
    text: String,
    modifier: Modifier = Modifier,
) = Text(text, modifier, style = KettleType.small, color = KettleTheme.colors.textSecondary)

private val EmptyLibrary = Library(1, emptyList(), emptyList(), emptyList())
