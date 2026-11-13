package com.casco.kettle.shell.programeditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.ConfigProblem
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.OverrideForm
import com.casco.kettle.domain.Program
import com.casco.kettle.domain.ProgramDraft
import com.casco.kettle.domain.ProgramOverride
import com.casco.kettle.domain.ProgramProblem
import com.casco.kettle.domain.ProgramSubmit
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.addingWeek
import com.casco.kettle.domain.choosingWorkout
import com.casco.kettle.domain.programDraftOf
import com.casco.kettle.domain.removingWeek
import com.casco.kettle.domain.submitProgram
import com.casco.kettle.domain.togglingRest
import com.casco.kettle.domain.withOverrides
import com.casco.kettle.domain.withProgram
import com.casco.kettle.domain.withoutProgram
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LibraryWriteRefusal
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.files.libraryWriteOrRefusal
import com.casco.kettle.shell.files.text
import com.casco.kettle.shell.form.EditorButtons
import com.casco.kettle.shell.form.FieldKind
import com.casco.kettle.shell.form.FieldOptions
import com.casco.kettle.shell.form.FormField
import com.casco.kettle.shell.form.SheetColumn
import com.casco.kettle.shell.form.dashedBorder
import com.casco.kettle.shell.i18n.deviceUnitSystem
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.list.TextAction
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.LocalNavigator
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.theme.DialogAction
import com.casco.kettle.shell.theme.KettleAlertDialog
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the route table draws at `Destination.ProgramEditor`, as Program detail and Programs push it:
 * `id` to edit, none to create.
 */
@Composable
fun ProgramEditorRoute(params: Map<String, String>) {
    val navigator = LocalNavigator.current
    ProgramEditorScreen(
        params["id"],
        LocalLatestFiles.current,
        ProgramEditorActions(
            close = { navigator.back() },
            // Navigate dismisses every sheet first, as RN's `dismissTo('/programs')` did. A back would
            // land on Program detail, which looks the program up by id and finds nothing.
            deleted = { navigator.navigate(Route(Destination.Programs)) },
        ),
    )
}

/**
 * What the editor asks of the shell, as parameters so a test hears each one. [deleted] is the close
 * after a delete, past the detail sheet that opened the editor.
 */
class ProgramEditorActions(
    val close: () -> Unit,
    val deleted: () -> Unit,
)

/**
 * A program (#243), as RN's `program-editor.tsx`: its name and its weeks, each a workout or a rest
 * day, with a number, an optional day for a second session in the week, notes, and the per-week
 * overrides. Save-at-the-end: nothing reaches the library until Save, and a refusal writes nothing.
 */
@Composable
fun ProgramEditorScreen(
    id: String?,
    files: LatestFiles,
    actions: ProgramEditorActions,
) {
    val locale = LocalConfiguration.current.locales[0]
    val units = files.preferences?.unitSystem ?: deviceUnitSystem(locale)
    // Nothing before the library is read, so a program that is there is never offered as new.
    val library = files.library ?: return
    val scope = rememberCoroutineScope()
    // Taken from the first read and kept: the shell reads again while the sheet is up, and from then
    // on the draft is the user's.
    val editor = remember(id) { Editor(library.programs.firstOrNull { it.id == id }, files, scope, actions) }
    Editing(editor, WeekContext(library, units))
}

/** What every week card reads: the library its workouts and overrides resolve in, and the reader's units. */
internal class WeekContext(
    val library: Library,
    val units: UnitSystem,
) {
    val workouts: List<Workout> get() = library.workouts

    fun workout(id: String) = workouts.firstOrNull { it.id == id }
}

/** A week's overrides panel, when open: choosing what to patch, or the form for one patch. Absent is closed. */
internal sealed interface OverridePanel {
    data object Choosing : OverridePanel

    /** [problem] is the confirm's refusal, cleared by the next keystroke. */
    data class Editing(
        val form: OverrideForm,
        val problem: ConfigProblem? = null,
    ) : OverridePanel
}

/** One edit, from opening to the write: the draft, what is open on each week, and why a write refused. */
internal class Editor(
    /** The program being edited, or null for a new one: an `id` the library doesn't hold is one too. */
    val existing: Program?,
    private val files: LatestFiles,
    private val scope: CoroutineScope,
    private val actions: ProgramEditorActions,
) {
    var draft by mutableStateOf(programDraftOf(existing))
        private set
    var problem by mutableStateOf<ProgramProblem?>(null)
        private set
    var failure by mutableStateOf<Failure?>(null)
        private set

    /** Delete's confirm is up. No in-use guard asks first, unlike a workout's: nothing points at a program. */
    var confirmingDelete by mutableStateOf(false)

    /** The week whose workout picker is open, by key: one at a time, as RN had it. */
    var pickerOpen by mutableStateOf<Int?>(null)
        private set

    /**
     * Each week's overrides panel, by week key. Here rather than in the card, so a card scrolled out
     * of the list and back keeps a half-typed override.
     */
    val panels = mutableStateMapOf<Int, OverridePanel>()

    private var writing = false

    /**
     * Set once a write has closed the sheet, and never cleared. RN takes a while to dismiss it, and a
     * tap in between would write again: a second close pops the screen under the sheet, and a Save
     * after a Delete writes the deleted program back.
     */
    private var closed = false

    val close = actions.close

    fun edit(change: (ProgramDraft) -> ProgramDraft) {
        draft = change(draft)
    }

    fun addWeek(workouts: List<Workout>) = edit { it.addingWeek(workouts) }

    fun removeWeek(key: Int) {
        edit { it.removingWeek(key) }
        panels.remove(key)
        pickerOpen = null
    }

    /** Hides the picker and the overrides with the workout they belong to. */
    fun toggleRest(
        key: Int,
        workouts: List<Workout>,
    ) {
        edit { it.togglingRest(key, workouts) }
        panels.remove(key)
        pickerOpen = null
    }

    fun togglePicker(key: Int) {
        pickerOpen = if (pickerOpen == key) null else key
    }

    /** An open overrides panel goes with the workout it was choosing among. */
    fun chooseWorkout(
        key: Int,
        workoutId: String,
    ) {
        edit { it.choosingWorkout(key, workoutId) }
        panels.remove(key)
        pickerOpen = null
    }

    fun setOverrides(
        key: Int,
        overrides: List<ProgramOverride>,
    ) = edit { it.withOverrides(key, overrides) }

    fun save() {
        if (closed || writing) return
        failure = null
        when (val submitted = submitProgram(draft, existing)) {
            is ProgramSubmit.Refused -> {
                problem = submitted.problem
            }

            // Deleted elsewhere while the sheet was up, it goes back in with the edit, as a workout does.
            is ProgramSubmit.Built -> {
                problem = null
                write(Failure.Kind.Save, { it.withProgram(submitted.program) }, close)
            }
        }
    }

    fun delete() {
        confirmingDelete = false
        if (closed) return
        val program = existing ?: return
        write(Failure.Kind.Delete, { it.withoutProgram(program.id) }, actions.deleted)
    }

    /**
     * [change] applied to the library as it is now, not as it was when the sheet opened, then
     * [written], which closes the sheet. Not cancelled with the screen once it starts, since RN's
     * stores must hear a file that changed.
     */
    private fun write(
        kind: Failure.Kind,
        change: (Library) -> Library,
        written: () -> Unit,
    ) {
        val library = files.library ?: return
        // A Delete confirmed while a Save is landing, or the reverse: a second write would close twice.
        if (writing) return
        writing = true
        scope.launch {
            withContext(NonCancellable) {
                val refused =
                    libraryWriteOrRefusal { files.writeLibrary(change(library)) }
                failure = refused?.let { Failure(kind, it) }
                writing = false
                if (refused == null) {
                    closed = true
                    written()
                }
            }
        }
    }

    data class Failure(
        val kind: Kind,
        val refusal: LibraryWriteRefusal,
    ) {
        enum class Kind { Save, Delete }
    }
}

@Composable
private fun Editing(
    editor: Editor,
    context: WeekContext,
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = Spacing.three, end = Spacing.three, bottom = Spacing.four),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.two + 2.dp),
    ) {
        item(key = HEADER_KEY) { Column(SheetColumn) { Header(editor, context) } }
        items(editor.draft.weeks, key = { it.key }) { week ->
            WeekCard(week, editor, context, SheetColumn)
        }
        item(key = FOOTER_KEY) { Column(SheetColumn) { Footer(editor, context) } }
    }
}

@Composable
private fun Header(
    editor: Editor,
    context: WeekContext,
) {
    val colors = KettleTheme.colors
    val title = if (editor.existing == null) R.string.programEditor_newTitle else R.string.programEditor_editTitle
    Text(stringResource(title), Modifier.semantics { heading() }, style = KettleType.subtitle, color = colors.text)
    Text(
        stringResource(R.string.common_name),
        Modifier.padding(top = Spacing.three - 2.dp, bottom = Spacing.one),
        style = KettleType.small,
        color = colors.textSecondary,
    )
    FormField(
        editor.draft.name,
        { name -> editor.edit { it.copy(name = name) } },
        name = stringResource(R.string.common_name),
        placeholder = stringResource(R.string.programEditor_namePlaceholder),
        FieldOptions(kind = FieldKind.Text, fill = colors.backgroundElement),
    )
    Text(
        pluralText(
            R.plurals.programEditor_weekCount,
            R.string.programEditor_weekCount_zero,
            editor.draft.weekCount,
        ),
        Modifier.padding(top = Spacing.two),
        style = KettleType.default,
        color = colors.textSecondary,
    )
    if (context.workouts.isEmpty()) {
        Text(
            stringResource(R.string.programEditor_noWorkoutsHint),
            Modifier.padding(top = Spacing.one),
            style = KettleType.small,
            color = colors.textSecondary,
        )
    }
}

/** Everything after the weeks: adding one, the refusal, Save, and Delete. */
@Composable
private fun Footer(
    editor: Editor,
    context: WeekContext,
) {
    val colors = KettleTheme.colors
    // Every week needs a workout, so with none to pick, adding one could only make a week Save refuses.
    // Disabled, which TalkBack announces, and the hint under the count says where to go instead.
    val canAdd = context.workouts.isNotEmpty()
    Box(
        Modifier
            .padding(top = Spacing.one)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .dashedBorder(1.5.dp, colors.border, 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = canAdd, role = Role.Button) { editor.addWeek(context.workouts) }
            .alpha(if (canAdd) 1f else DISABLED_ALPHA)
            .padding(Spacing.two),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.programEditor_addWeek), style = KettleType.heading, color = colors.textSecondary)
    }
    refusalText(editor)?.let {
        Text(it, Modifier.padding(top = Spacing.two), style = KettleType.small, color = colors.accentText)
    }
    EditorButtons(stringResource(R.string.common_save), onCancel = editor.close, onPrimary = editor::save)
    val existing = editor.existing ?: return
    Box(Modifier.fillMaxWidth().padding(top = Spacing.three - 2.dp), contentAlignment = Alignment.Center) {
        TextAction(
            stringResource(R.string.programEditor_deleteProgram),
            KettleType.smallMedium,
            colors.textSecondary,
            onClick = { editor.confirmingDelete = true },
        )
    }
    if (editor.confirmingDelete) {
        KettleAlertDialog(
            title = stringResource(R.string.programEditor_deleteConfirmTitle),
            // The name is the user's, interpolated verbatim.
            body = stringResource(R.string.common_deleteConfirmBody, existing.name),
            onDismissRequest = { editor.confirmingDelete = false },
            confirm = DialogAction(stringResource(R.string.common_delete), editor::delete),
            dismiss = DialogAction(stringResource(R.string.common_cancel)) { editor.confirmingDelete = false },
        )
    }
}

@Composable
private fun refusalText(editor: Editor): String? =
    editor.problem?.let { problemText(it) } ?: editor.failure?.let {
        it.refusal.text(
            if (it.kind == Editor.Failure.Kind.Save) R.string.common_saveFailed else R.string.common_deleteFailed,
        )
    }

@Composable
private fun problemText(problem: ProgramProblem): String =
    when (problem) {
        ProgramProblem.NameRequired -> {
            stringResource(R.string.common_nameRequired)
        }

        ProgramProblem.NoWeeks -> {
            stringResource(R.string.programEditor_addAtLeastOneWeek)
        }

        ProgramProblem.WeekNeedsWorkout -> {
            stringResource(R.string.programEditor_everyWeekNeedsWorkout)
        }

        ProgramProblem.NoTrainingWeek -> {
            stringResource(R.string.programEditor_needsOneWorkoutWeek)
        }

        // RN's label: the number, and the day in brackets when there is one. The day is the user's.
        is ProgramProblem.DuplicateWeek -> {
            stringResource(
                R.string.programEditor_duplicateWeek,
                "${problem.week}" + problem.day?.let { " ($it)" }.orEmpty(),
            )
        }

        ProgramProblem.NoId -> {
            stringResource(R.string.common_couldNotDeriveId)
        }
    }

internal const val DISABLED_ALPHA = 0.4f

private const val HEADER_KEY = "header"
private const val FOOTER_KEY = "footer"
