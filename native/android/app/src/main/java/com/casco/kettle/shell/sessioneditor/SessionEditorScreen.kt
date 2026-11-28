package com.casco.kettle.shell.sessioneditor

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.EntryField
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.LocatedProblem
import com.casco.kettle.domain.RemovedSet
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionEditing
import com.casco.kettle.domain.SessionEntry
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.addingSet
import com.casco.kettle.domain.changing
import com.casco.kettle.domain.editedSession
import com.casco.kettle.domain.exerciseName
import com.casco.kettle.domain.firstProblem
import com.casco.kettle.domain.removingSet
import com.casco.kettle.domain.restoring
import com.casco.kettle.domain.sessionEditing
import com.casco.kettle.domain.sessionEntryResult
import com.casco.kettle.domain.sessionStartDay
import com.casco.kettle.domain.wholeSeconds
import com.casco.kettle.domain.workoutNameFor
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.files.writeOrRefusal
import com.casco.kettle.shell.form.EditorTitleBar
import com.casco.kettle.shell.history.entryResultText
import com.casco.kettle.shell.i18n.deviceUnitSystem
import com.casco.kettle.shell.i18n.formatClock
import com.casco.kettle.shell.i18n.formatMonthDay
import com.casco.kettle.shell.library.ExerciseBadge
import com.casco.kettle.shell.nav.LocalNavigator
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone

/** What the route table draws at `Destination.SessionEditor`, as History's Edit pushes it, with an `id`. */
@Composable
fun SessionEditorRoute(params: Map<String, String>) {
    val navigator = LocalNavigator.current
    SessionEditorScreen(
        params["id"],
        LocalLatestFiles.current,
        TimeZone.currentSystemDefault(),
        SessionEditorActions(close = { navigator.back() }),
    )
}

/** What the editor asks of the shell, as a parameter so a test hears it. */
class SessionEditorActions(
    val close: () -> Unit,
)

/**
 * Corrects what a finished session logged (#239), as RN's session editor did until the sessions
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
    // Worked out from the first read of the log and kept, as RN's editor copied its drafts once: the
    // log can change while the sheet is up, and from then on what's typed is the user's. Once more when
    // the session's end lands: the receipt's Edit log (#416) can open this before the finished copy is
    // in the log, which would otherwise hold "still running" for good.
    val shown = logged?.firstOrNull { it.id == id }
    val editing = logged?.let { remember(id, units, shown?.endedAt) { sessionEditing(it, id, units) } }
    val editor =
        (editing as? SessionEditing.Editable)?.let {
            remember(
                it,
            ) { Editor(it, units, files, scope, actions) }
        }
    Column(Modifier.fillMaxSize().imePadding()) {
        Sheet {
            EditorTitleBar(
                stringResource(R.string.sessionEditor_title),
                editor?.let { it::save },
                editor?.let { refusalText(it, library) },
            )
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            // The Undo bar clears the nav bar itself while it's up; padding here too left a bar-high gap.
            val navBar = if (editor?.lastRemoved == null) Modifier.navigationBarsPadding() else Modifier
            Sheet(Modifier.padding(bottom = Spacing.four).then(navBar)) {
                shown?.let { Subtitle(it, library, zone) }
                editing?.let { Body(it, editor, library, units) }
            }
        }
        // Keyed on a count of removals, so each is a node of its own and announced: keyed on the removal,
        // two identical ones in a row were equal and the second said nothing (#456).
        editor?.lastRemoved?.let { removed -> key(editor.removals) { Sheet { UndoBar(removed, editor, library) } } }
    }
}

/** What the sheet shows for [editing]: the tables, or why there are none. */
@Composable
private fun Body(
    editing: SessionEditing,
    editor: Editor?,
    library: Library,
    units: UnitSystem,
) = when (editing) {
    SessionEditing.NotFound -> Notice(R.string.sessionEditor_notFoundTitle, R.string.sessionEditor_notFoundBody)
    SessionEditing.Running -> Notice(R.string.sessionEditor_runningTitle, R.string.sessionEditor_runningBody)
    SessionEditing.NothingToEdit -> Notice(R.string.sessionEditor_emptyTitle, R.string.sessionEditor_emptyBody)
    is SessionEditing.Editable -> editor?.let { Editing(it, library, units) }
}

/** The editor's column: its content width, centred, inside the sheet's inset. */
@Composable
private fun Sheet(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.three), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = MaxContentWidth).fillMaxWidth().then(modifier)) { content() }
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
        stringResource(R.string.sessionEditor_subtitle, formatMonthDay(day, locale), name),
        Modifier.padding(top = 2.dp),
    )
}

/**
 * One session's edit, from the first keystroke to the write: the drafts, the one cell open, the last
 * removal Undo can put back, and why Save refused, the form's problem or the disk's.
 */
internal class Editor(
    private val editing: SessionEditing.Editable,
    private val units: UnitSystem,
    private val files: LatestFiles,
    private val scope: CoroutineScope,
    private val actions: SessionEditorActions,
) {
    var drafts by mutableStateOf(editing.drafts)
        private set

    /** The set last removed, for the Undo bar; null once undone, or with nothing removed. */
    var lastRemoved by mutableStateOf<RemovedSet?>(null)
        private set

    /** Every removal so far, which the Undo bar is keyed on. */
    var removals by mutableIntStateOf(0)
        private set

    /** The one cell being edited, if any: one at a time, as the board has it. */
    var editingCell by mutableStateOf<CellKey?>(null)
        private set
    var problem by mutableStateOf<LocatedProblem?>(null)
        private set
    var failure by mutableStateOf<String?>(null)
        private set
    private var saving = false

    /**
     * Set once the write has closed the sheet, and never cleared. RN takes a while to dismiss it, and
     * a Save in between would write again, its second close popping the screen under the sheet.
     */
    private var closed = false

    val session get() = editing.session

    fun startEditing(cell: CellKey) {
        editingCell = cell
    }

    /**
     * Closes [cell], and only it: a cell losing focus because another was tapped mustn't close the one
     * just opened.
     */
    fun stopEditing(cell: CellKey) {
        if (editingCell == cell) editingCell = null
    }

    fun setField(
        draft: Int,
        row: Int,
        field: EntryField,
        text: String,
    ) {
        drafts =
            drafts.changing(draft) { forms ->
                val edited = forms[row].copy(values = forms[row].values + (field to text))
                forms.mapIndexed { i, form -> if (i == row) edited else form }
            }
    }

    /**
     * One set goes at once, the last of an exercise too, with Undo to put it back exactly (#442): nothing
     * is written until Save, and closing the sheet discards it, so no removal needs asking about first.
     */
    fun remove(
        draft: Int,
        row: Int,
    ) {
        val (rest, removed) = drafts.removingSet(draft, row) ?: return
        editingCell = null
        // A refusal about the set that just went would point at nothing.
        problem = null
        drafts = rest
        lastRemoved = removed
        removals++
    }

    /**
     * Puts the last removal back. An open cell closes first: its key names a row by position, and the
     * restored set would slide under it, so the next keystroke would land in a set the user didn't open.
     */
    fun undo() {
        val removed = lastRemoved ?: return
        editingCell = null
        problem = null
        drafts = drafts.restoring(removed)
        lastRemoved = null
    }

    fun addSet(draft: Int) {
        drafts = drafts.addingSet(draft)
    }

    /**
     * Checks every entry before writing anything, so a refusal leaves the file as it was rather than
     * half-corrected. Not cancelled with the screen once the write starts: a write that lands is shown
     * only by the call that made it, so one cut off after the disk would keep listing the session as
     * it was.
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
                    writeOrRefusal { files.writeSession(edited) }
                saving = false
                if (failure == null) {
                    closed = true
                    actions.close()
                }
            }
        }
    }
}

/**
 * The session in its own order (#442): a table per exercise it can correct, a read-only line per rest
 * ("Rest · 1:34 taken"), and an EMOM as its result, shown rather than dropped, since a session that
 * lost its EMOM on the way in would read as if the log had.
 */
@Composable
private fun Editing(
    editor: Editor,
    library: Library,
    units: UnitSystem,
) {
    val resources = LocalResources.current
    Secondary(stringResource(R.string.sessionEditor_hint), Modifier.padding(top = Spacing.two))
    val byIndex = editor.drafts.withIndex().associateBy { it.value.index }
    var rests = false
    editor.session.entries.forEachIndexed { at, entry ->
        val draft = byIndex[at]
        when {
            // An entry whose last set was removed goes on save; until then it is simply not shown.
            draft != null -> {
                if (draft.value.forms.isNotEmpty()) {
                    EntryTable(
                        TableContext(
                            editor,
                            draft.index,
                            draft.value,
                            units,
                            exerciseName(library.exercises, entry.exercise),
                        ),
                    )
                }
            }

            entry is SessionEntry.Rest -> {
                rests = true
                Secondary(
                    stringResource(
                        R.string.sessionEditor_restLine,
                        formatClock(wholeSeconds(entry.restTakenSec).toDouble()),
                    ),
                    Modifier.padding(top = Spacing.two),
                )
            }

            else -> {
                ReadOnlyEntry(
                    exerciseName(library.exercises, entry.exercise),
                    entry,
                    resources.entryResultText(sessionEntryResult(entry), units),
                )
            }
        }
    }
    if (rests) Secondary(stringResource(R.string.sessionEditor_restNote), Modifier.padding(top = Spacing.three))
}

/** An entry the editor can't rewrite, an EMOM's minute-by-minute log: its name, its result, and why. */
@Composable
private fun ReadOnlyEntry(
    name: String,
    entry: SessionEntry,
    result: String,
) = Column(Modifier.padding(top = Spacing.four), verticalArrangement = Arrangement.spacedBy(Spacing.one)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.two)) {
        Text(name, Modifier.weight(1f), style = KettleType.rowTitle, color = KettleTheme.colors.text)
        ExerciseBadge(entry)
    }
    Text(result, style = KettleType.smallMedium, color = KettleTheme.colors.text)
    Secondary(stringResource(R.string.sessionEditor_readOnlyNote))
}

/** Which cell is being edited: an entry's draft, a row of it, and a field of the row. */
internal data class CellKey(
    val draft: Int,
    val row: Int,
    val field: EntryField,
)

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
