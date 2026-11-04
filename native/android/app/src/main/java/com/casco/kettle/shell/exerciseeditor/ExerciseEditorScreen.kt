package com.casco.kettle.shell.exerciseeditor

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
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseDraft
import com.casco.kettle.domain.ExerciseProblem
import com.casco.kettle.domain.ExerciseSubmit
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.draftOf
import com.casco.kettle.domain.exerciseHistory
import com.casco.kettle.domain.submitExercise
import com.casco.kettle.domain.withExercise
import com.casco.kettle.domain.withoutExercise
import com.casco.kettle.domain.workoutsUsing
import com.casco.kettle.shell.R
import com.casco.kettle.shell.art.ExerciseArt
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.files.writeOrRefusal
import com.casco.kettle.shell.form.EditorButtons
import com.casco.kettle.shell.history.entryResultText
import com.casco.kettle.shell.i18n.deviceUnitSystem
import com.casco.kettle.shell.i18n.formatMonthDay
import com.casco.kettle.shell.list.TextAction
import com.casco.kettle.shell.rn.FileKind
import com.casco.kettle.shell.rn.JsBridge
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
import kotlinx.datetime.TimeZone

/** Where the exercise editor is registered, as Library pushes it: with an `id` to edit, none to create. */
const val EXERCISE_EDITOR_PATH = "/exercise-editor"

/** What `shellRoutes` registers at [EXERCISE_EDITOR_PATH], behind the library flip. */
@Composable
fun ExerciseEditorRoute(params: Map<String, String>) =
    ExerciseEditorScreen(
        params["id"],
        LocalLatestFiles.current,
        TimeZone.currentSystemDefault(),
        ExerciseEditorActions(close = JsBridge::back, libraryChanged = { JsBridge.filesChanged(FileKind.LIBRARY) }),
    )

/**
 * What the editor asks of the shell, as parameters so a test hears each one. [libraryChanged] follows
 * every write, so RN's stores read the library again.
 */
class ExerciseEditorActions(
    val close: () -> Unit,
    val libraryChanged: () -> Unit,
)

/**
 * An exercise's plan (#241), as RN's `exercise-editor.tsx`: its name, type, config and notes, the
 * bundled drawing of a seeded one, and Recent, what the log holds of it. Native's first writer of the
 * library, and the only one once the library flip (#222) releases.
 *
 * Save-at-the-end: the form is this screen's copy, so closing writes nothing, and Save writes the
 * whole library once, with this exercise in it, as it is on disk by then.
 */
@Composable
fun ExerciseEditorScreen(
    id: String?,
    files: LatestFiles,
    zone: TimeZone,
    actions: ExerciseEditorActions,
) {
    val locale = LocalConfiguration.current.locales[0]
    val units = files.preferences?.unitSystem ?: deviceUnitSystem(locale)
    val library = files.library
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
            // Nothing before the library is read, so an exercise that is there is never offered as new.
            if (library == null) return@Column
            // Taken from the first read and kept, as RN's form copied the exercise once: the shell reads
            // again while the sheet is up, and from then on what's typed is the user's. Keyed on the
            // units too, so the weight is never shown in one unit and converted back in the other.
            val editor =
                remember(
                    id,
                    units,
                ) { Editor(library.exercises.firstOrNull { it.id == id }, units, files, scope, actions) }
            Editing(editor, files, units, zone)
        }
    }
}

/** One edit, from the first keystroke to the write: the form, and why Save or Delete refused. */
private class Editor(
    /** The exercise being edited, or null for a new one: an `id` the library doesn't hold is one too. */
    val existing: Exercise?,
    private val units: UnitSystem,
    private val files: LatestFiles,
    private val scope: CoroutineScope,
    private val actions: ExerciseEditorActions,
) {
    var draft by mutableStateOf(existing?.let { draftOf(it, units) } ?: ExerciseDraft.New)
    var problem by mutableStateOf<ExerciseProblem?>(null)
        private set
    var failure by mutableStateOf<Failure?>(null)
        private set
    var deleting by mutableStateOf<Deleting?>(null)
        private set
    private var writing = false

    /**
     * Set once a write has closed the sheet, and never cleared. RN takes a while to dismiss it, and a
     * tap in between would write again: a second close pops the screen under the sheet, and a Save
     * after a Delete writes the deleted exercise back.
     */
    private var closed = false

    fun cancel() = actions.close()

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

    /** Refuses while any workout uses it, plain or in a circuit, since a delete would orphan them. */
    fun askToDelete() {
        deleting = inUse() ?: Deleting.Confirm
    }

    fun dismissDelete() {
        deleting = null
    }

    /**
     * Asks [inUse] again: the library is read again while the confirm is up, and a workout that
     * started using the exercise meanwhile would be left pointing at nothing.
     */
    fun delete() {
        if (closed) return
        val exercise = existing ?: return
        deleting = inUse()
        if (deleting == null) write(Failure.Kind.Delete) { it.withoutExercise(exercise.id) }
    }

    /** The workouts using this exercise in the library as it is now, as a refusal; null for none. */
    private fun inUse(): Deleting.InUse? {
        val exercise = existing ?: return null
        val usedBy = files.library?.let { workoutsUsing(it, exercise.id) }.orEmpty()
        return Deleting.InUse(usedBy.map { it.name }).takeIf { usedBy.isNotEmpty() }
    }

    /**
     * [change] applied to the library as it is now, not as it was when the sheet opened: the shell
     * reads it again meanwhile, and a write from the opening copy would undo whatever landed since.
     * Not cancelled with the screen once it starts, since RN's stores must hear a file that changed.
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
                    writeOrRefusal {
                        files.writeLibrary(change(library))
                        actions.libraryChanged()
                    }
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
        val detail: String,
    ) {
        enum class Kind { Save, Delete }
    }

    sealed interface Deleting {
        data object Confirm : Deleting

        /** [workouts]' names, verbatim. */
        data class InUse(
            val workouts: List<String>,
        ) : Deleting
    }
}

@Composable
private fun Editing(
    editor: Editor,
    files: LatestFiles,
    units: UnitSystem,
    zone: TimeZone,
) {
    val colors = KettleTheme.colors
    val existing = editor.existing
    Text(
        stringResource(if (existing == null) R.string.exerciseEditor_newTitle else R.string.exerciseEditor_editTitle),
        Modifier.semantics { heading() },
        style = KettleType.subtitle,
        color = colors.text,
    )
    ExerciseFormFields(editor.draft, { editor.draft = it }, units, typeLocked = existing != null)

    // Only for a saved exercise: the bundled set is keyed by id, which a new one doesn't have yet.
    if (existing != null) {
        Box(Modifier.fillMaxWidth().padding(top = Spacing.three), contentAlignment = Alignment.Center) {
            ExerciseArt(existing.id)
        }
    }
    ExerciseNotesField(editor.draft, { editor.draft = it })
    if (existing != null) Recent(existing, files, zone)

    val refusal =
        editor.problem?.let { exerciseProblemText(it) }
            ?: editor.failure?.let {
                val format =
                    if (it.kind ==
                        Editor.Failure.Kind.Save
                    ) {
                        R.string.common_saveFailed
                    } else {
                        R.string.common_deleteFailed
                    }
                stringResource(format, it.detail)
            }
    refusal?.let { Text(it, Modifier.padding(top = Spacing.two), style = KettleType.small, color = colors.accentText) }

    EditorButtons(stringResource(R.string.common_save), onCancel = editor::cancel, onPrimary = editor::save)
    if (existing != null) {
        Box(Modifier.fillMaxWidth().padding(top = Spacing.three - 2.dp), contentAlignment = Alignment.Center) {
            TextAction(
                stringResource(R.string.exerciseEditor_deleteExercise),
                KettleType.smallMedium,
                colors.textSecondary,
                onClick = editor::askToDelete,
            )
        }
        DeleteDialog(editor, existing)
    }
}

/**
 * The last few times this exercise was logged, newest first, and the same as bars, oldest first so
 * they read left to right. Nothing at all for one never logged, a new library's usual case. Read live
 * from the log rather than with the form, since it isn't anything the user is editing.
 */
@Composable
private fun Recent(
    exercise: Exercise,
    files: LatestFiles,
    zone: TimeZone,
) {
    val log = files.sessionLog ?: return
    val history = remember(log, exercise.id) { exerciseHistory(log.logged, exercise.id) }
    if (history.isEmpty()) return
    val colors = KettleTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val resources = LocalResources.current
    // A started_at that isn't an instant has no day to show, which RN printed as "Invalid Date".
    val days = history.map { entry -> entry.startedAt?.let { formatMonthDay(it, locale, zone) }.orEmpty() }
    Column(Modifier.padding(top = Spacing.three - 2.dp)) {
        Text(
            stringResource(R.string.exerciseEditor_recent),
            Modifier.padding(bottom = Spacing.one),
            style = KettleType.small,
            color = colors.textSecondary,
        )
        VolumeChart(history.zip(days).map { (entry, day) -> entry.volume to day }.reversed())
        Column(Modifier.padding(top = Spacing.one), verticalArrangement = Arrangement.spacedBy(Spacing.one + 2.dp)) {
            history.zip(days).forEach { (entry, day) ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.two),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(day, Modifier.weight(1f), style = KettleType.small, color = colors.textSecondary)
                    Text(resources.entryResultText(entry.result), style = KettleType.smallMedium, color = colors.text)
                }
            }
        }
    }
}

/** Delete's two questions: refused while a workout uses it, else confirmed before anything goes. */
@Composable
private fun DeleteDialog(
    editor: Editor,
    exercise: Exercise,
) {
    val deleting = editor.deleting ?: return
    // Every name here is the user's, interpolated verbatim.
    when (deleting) {
        is Editor.Deleting.InUse -> {
            KettleAlertDialog(
                title = stringResource(R.string.exerciseEditor_inUseTitle),
                body =
                    stringResource(
                        R.string.exerciseEditor_inUseBody,
                        exercise.name,
                        deleting.workouts.joinToString(", "),
                    ),
                onDismissRequest = editor::dismissDelete,
                // RN's buttonless alert, which Android titles with the platform's own OK.
                confirm = DialogAction(stringResource(android.R.string.ok), editor::dismissDelete),
            )
        }

        Editor.Deleting.Confirm -> {
            KettleAlertDialog(
                title = stringResource(R.string.exerciseEditor_deleteConfirmTitle),
                body = stringResource(R.string.common_deleteConfirmBody, exercise.name),
                onDismissRequest = editor::dismissDelete,
                confirm = DialogAction(stringResource(R.string.common_delete), editor::delete),
                dismiss = DialogAction(stringResource(R.string.common_cancel), editor::dismissDelete),
            )
        }
    }
}
