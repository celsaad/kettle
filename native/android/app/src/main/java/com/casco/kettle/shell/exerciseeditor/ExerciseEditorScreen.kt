package com.casco.kettle.shell.exerciseeditor

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.ExerciseDraft
import com.casco.kettle.domain.ExerciseProblem
import com.casco.kettle.domain.ExerciseSubmit
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.draftOf
import com.casco.kettle.domain.submitExercise
import com.casco.kettle.domain.withDefaults
import com.casco.kettle.domain.withExercise
import com.casco.kettle.domain.withoutExercise
import com.casco.kettle.domain.workoutsUsing
import com.casco.kettle.shell.R
import com.casco.kettle.shell.art.ExerciseArt
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LibraryWriteRefusal
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.files.libraryWriteOrRefusal
import com.casco.kettle.shell.files.text
import com.casco.kettle.shell.form.EditorTitleBar
import com.casco.kettle.shell.i18n.deviceUnitSystem
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.nav.rememberCloseThis
import com.casco.kettle.shell.theme.DialogAction
import com.casco.kettle.shell.theme.KettleAlertDialog
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the route table draws at `Destination.ExerciseEditor`, as Library pushes it: with an `id` to edit,
 * none to create.
 */
@Composable
fun ExerciseEditorRoute(params: Map<String, String>) {
    ExerciseEditorScreen(
        params["id"],
        LocalLatestFiles.current,
        ExerciseEditorActions(close = rememberCloseThis()),
    )
}

/** What the editor asks of the shell, as a parameter so a test hears it. */
class ExerciseEditorActions(
    val close: () -> Unit,
)

/**
 * An exercise's plan (#241), as RN's exercise editor was, now the empty stub at this path: its
 * name, type, config and notes, and the bundled drawing of a seeded one. What the log holds of it is
 * Exercise detail's since #413, which the form is one tap behind. One of the library's writers, all
 * native from the library flip (#245).
 *
 * Save-at-the-end: the form is this screen's copy, so closing writes nothing, and Save writes the
 * whole library once, with this exercise in it, as it is on disk by then.
 */
@Composable
fun ExerciseEditorScreen(
    id: String?,
    files: LatestFiles,
    actions: ExerciseEditorActions,
) {
    val locale = LocalConfiguration.current.locales[0]
    val units = files.preferences?.unitSystem ?: deviceUnitSystem(locale)
    val library = files.library
    val scope = rememberCoroutineScope()
    // Nothing before the library is read, so an exercise that is there is never offered as new.
    if (library == null) return
    // Taken from the first read and kept, as RN's form copied the exercise once: the shell reads again
    // while the sheet is up, and from then on what's typed is the user's. In the units it opened in, kept
    // with it (#569): the weight is never shown in one unit and converted back in the other, and a units
    // switch in Settings, pushed over the editor by a link, doesn't throw the draft away.
    val editor = remember(id) { Editor(library.exercises.firstOrNull { it.id == id }, units, files, scope, actions) }
    val title = if (editor.existing == null) R.string.exerciseEditor_newTitle else R.string.exerciseEditor_editTitle
    Column(Modifier.fillMaxSize().imePadding()) {
        // Save pinned above what scrolls, as the program and workout editors' (#438, #440): the × is the cancel.
        Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.three), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = MaxContentWidth).fillMaxWidth()) {
                EditorTitleBar(stringResource(title), editor::save, refusalText(editor))
            }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
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
                Editing(editor, editor.units)
            }
        }
    }
}

/** One edit, from the first keystroke to the write: the form, and why Save or Delete refused. */
private class Editor(
    /** The exercise being edited, or null for a new one: an `id` the library doesn't hold is one too. */
    val existing: Exercise?,
    /** What the editor shows and reads a weight in: the units it opened in, whatever Settings says since. */
    val units: UnitSystem,
    private val files: LatestFiles,
    private val scope: CoroutineScope,
    private val actions: ExerciseEditorActions,
) {
    var draft by mutableStateOf(existing?.let { draftOf(it, units) } ?: ExerciseDraft.New.withDefaults())
    var problem by mutableStateOf<ExerciseProblem?>(null)
        private set
    var failure by mutableStateOf<Failure?>(null)
        private set
    var confirmingDelete by mutableStateOf(false)
        private set
    private var writing = false

    /**
     * Set once a write has closed the sheet, and never cleared. RN takes a while to dismiss it, and a
     * tap in between would write again: a second close pops the screen under the sheet, and a Save
     * after a Delete writes the deleted exercise back.
     */
    private var closed = false

    fun save() {
        if (closed) return
        failure = null
        when (val submitted = submitExercise(draft, existing, units)) {
            is ExerciseSubmit.Refused -> {
                problem = submitted.problem
            }

            // Deleted elsewhere while the sheet was up, it goes back in with the edit: what was typed is
            // the newer intent, and nothing can point at it, since its delete needed no workout to use it.
            is ExerciseSubmit.Built -> {
                problem = null
                write(Failure.Kind.Save) { it.withExercise(submitted.exercise) }
            }
        }
    }

    /**
     * The workouts using this exercise, plain or in a circuit, by name, verbatim: Delete is unavailable
     * while there are any, since a delete would orphan them. Read from the library as it is now.
     */
    val usedBy: List<String>
        get() =
            existing
                ?.let { exercise ->
                    files.library?.let { workoutsUsing(it, exercise.id) }
                }.orEmpty()
                .map { it.name }

    fun askToDelete() {
        confirmingDelete = usedBy.isEmpty()
    }

    fun dismissDelete() {
        confirmingDelete = false
    }

    /**
     * Asks [usedBy] again: the library can change while the confirm is up, and a workout that started
     * using the exercise meanwhile would be left pointing at nothing.
     */
    fun delete() {
        if (closed) return
        val exercise = existing ?: return
        confirmingDelete = false
        if (usedBy.isEmpty()) write(Failure.Kind.Delete) { it.withoutExercise(exercise.id) }
    }

    /**
     * [change] applied to the library as it is now, not as it was when the sheet opened: the library
     * can change meanwhile, and a write from the opening copy would undo whatever landed since.
     * Not cancelled with the screen once it starts: a write that lands is shown only by the call that
     * made it, so one cut off after the disk would leave the old library on screen.
     */
    private fun write(
        kind: Failure.Kind,
        change: (Library) -> Library,
    ) {
        val library = files.library ?: return
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
                    actions.close()
                }
            }
        }
    }

    /** A write the disk refused, and why, which a full disk or a withdrawn permission can be. */
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
    units: UnitSystem,
) {
    val existing = editor.existing
    val previousKg = (existing?.config as? ExerciseConfig.Reps)?.targetWeightKg
    ExerciseSheetFields(
        FieldContext(editor.draft, { editor.draft = it }, units, previousKg),
        typeLocked =
            existing != null,
    )
    // Only for a saved exercise: the bundled set is keyed by id, which a new one doesn't have yet.
    if (existing != null) {
        Box(Modifier.fillMaxWidth().padding(top = Spacing.three), contentAlignment = Alignment.Center) {
            ExerciseArt(existing.id)
        }
    }
    ExerciseNotesField(editor.draft, { editor.draft = it })
    existing?.let { IdAndDelete(editor, it) }
}

@Composable
private fun refusalText(editor: Editor): String? =
    editor.problem?.let { exerciseProblemText(it) }
        ?: editor.failure?.let {
            it.refusal.text(
                if (it.kind ==
                    Editor.Failure.Kind.Save
                ) {
                    R.string.common_saveFailed
                } else {
                    R.string.common_deleteFailed
                },
            )
        }

/**
 * The exercise's id, as text, and Delete: unavailable while a workout uses the exercise, with why on
 * the button's state and under it, as the workout editor's (#440). The names are the user's, verbatim.
 */
@Composable
private fun IdAndDelete(
    editor: Editor,
    exercise: Exercise,
) {
    val colors = KettleTheme.colors
    val usedBy = editor.usedBy
    val reason =
        usedBy.takeIf { it.isNotEmpty() }?.let {
            stringResource(
                R.string.exerciseEditor_inUseReason,
                it.joinToString(stringResource(R.string.format_listSeparator)),
            )
        }
    ListRowSeparator(Modifier.padding(top = Spacing.four))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.exerciseEditor_idLabel, exercise.id),
            Modifier.weight(1f),
            style = KettleType.code,
            color = colors.textSecondary,
        )
        val enabled = usedBy.isEmpty()
        Text(
            stringResource(R.string.exerciseEditor_deleteExercise),
            Modifier
                .heightIn(min = 48.dp)
                // Disabled rather than refusing after the tap: TalkBack says it's unavailable, and why.
                .clickable(enabled = enabled, role = Role.Button, onClick = editor::askToDelete)
                .semantics { reason?.let { stateDescription = it } }
                .padding(horizontal = Spacing.two)
                .wrapContentHeight(Alignment.CenterVertically),
            style = KettleType.smallMedium,
            color = if (enabled) colors.accentText else colors.textSecondary,
        )
    }
    // Seen, but not read a second time: the button's state already says it.
    reason?.let { Text(it, Modifier.clearAndSetSemantics {}, style = KettleType.small, color = colors.textSecondary) }
    if (editor.confirmingDelete) {
        KettleAlertDialog(
            title = stringResource(R.string.exerciseEditor_deleteConfirmTitle),
            body = stringResource(R.string.common_deleteConfirmBody, exercise.name),
            onDismissRequest = editor::dismissDelete,
            confirm = DialogAction(stringResource(R.string.common_delete), editor::delete),
            dismiss = DialogAction(stringResource(R.string.common_cancel), editor::dismissDelete),
        )
    }
}
