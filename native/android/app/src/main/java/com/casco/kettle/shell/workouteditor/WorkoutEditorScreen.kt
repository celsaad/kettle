package com.casco.kettle.shell.workouteditor

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.DraftBlock
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Workout
import com.casco.kettle.domain.WorkoutBlock
import com.casco.kettle.domain.WorkoutProblem
import com.casco.kettle.domain.WorkoutSubmit
import com.casco.kettle.domain.adding
import com.casco.kettle.domain.addingCircuit
import com.casco.kettle.domain.moving
import com.casco.kettle.domain.movingBy
import com.casco.kettle.domain.programsUsing
import com.casco.kettle.domain.removing
import com.casco.kettle.domain.submitWorkout
import com.casco.kettle.domain.withCircuit
import com.casco.kettle.domain.withExercise
import com.casco.kettle.domain.withWorkout
import com.casco.kettle.domain.withoutWorkout
import com.casco.kettle.domain.workoutDraftOf
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.files.writeOrRefusal
import com.casco.kettle.shell.form.EditorButtons
import com.casco.kettle.shell.form.FieldKind
import com.casco.kettle.shell.form.FieldOptions
import com.casco.kettle.shell.form.FormField
import com.casco.kettle.shell.form.SheetColumn
import com.casco.kettle.shell.form.dashedBorder
import com.casco.kettle.shell.i18n.deviceUnitSystem
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.list.TextAction
import com.casco.kettle.shell.rn.FileKind
import com.casco.kettle.shell.rn.JsBridge
import com.casco.kettle.shell.theme.DialogAction
import com.casco.kettle.shell.theme.KettleAlertDialog
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Where the workout editor is registered, as Home and Library push it: with an `id` to edit, none to create. */
const val WORKOUT_EDITOR_PATH = "/workout-editor"

/** What `shellRoutes` registers at [WORKOUT_EDITOR_PATH], behind the library flip. */
@Composable
fun WorkoutEditorRoute(params: Map<String, String>) =
    WorkoutEditorScreen(
        params["id"],
        LocalLatestFiles.current,
        WorkoutEditorActions(close = JsBridge::back, libraryChanged = { JsBridge.filesChanged(FileKind.LIBRARY) }),
    )

/**
 * What the editor asks of the shell, as parameters so a test hears each one. [libraryChanged] follows
 * every write, the quick-add's too, so RN's stores read the library again.
 */
class WorkoutEditorActions(
    val close: () -> Unit,
    val libraryChanged: () -> Unit,
)

/**
 * A workout (#242), as RN's `workout-editor.tsx`: its name and its blocks, plain exercises and
 * circuits, in an order set by dragging a block's grip, or by its "Move up" and "Move down" under a
 * screen reader. New exercises can be made from the pickers without leaving the workout.
 *
 * Save-at-the-end, as the exercise editor is, except for a quick-added exercise: that is written to the
 * library at once, as RN's was, so the block that uses it never points at nothing.
 *
 * One `LazyColumn` holds the whole sheet, so it scrolls as one, and only the blocks are reorderable
 * items in it (`docs/research/compose-reorder.md`). The draft reorders live, during the drag, which
 * the library needs; RN's moved on drop.
 */
@Composable
fun WorkoutEditorScreen(
    id: String?,
    files: LatestFiles,
    actions: WorkoutEditorActions,
) {
    val locale = LocalConfiguration.current.locales[0]
    val units = files.preferences?.unitSystem ?: deviceUnitSystem(locale)
    // Nothing before the library is read, so a workout that is there is never offered as new.
    val library = files.library ?: return
    val scope = rememberCoroutineScope()
    // Taken from the first read and kept: the shell reads again while the sheet is up, and from then
    // on the draft is the user's.
    val editor = remember(id) { Editor(library.workouts.firstOrNull { it.id == id }, files, scope, actions) }
    Editing(editor, BlockContext(library.exercises, units))
}

/** One edit, from opening to the write: the draft, the open picker, and why a write refused. */
private class Editor(
    /** The workout being edited, or null for a new one: an `id` the library doesn't hold is one too. */
    val existing: Workout?,
    private val files: LatestFiles,
    private val scope: CoroutineScope,
    private val actions: WorkoutEditorActions,
) {
    var draft by mutableStateOf(workoutDraftOf(existing))
    val pickers = Pickers()
    var problem by mutableStateOf<WorkoutProblem?>(null)
        private set
    var failure by mutableStateOf<Failure?>(null)
        private set
    var deleting by mutableStateOf<Deleting?>(null)
        private set
    private var writing = false

    /** Whether the write in flight closes the sheet once it lands: a Save's or a Delete's. */
    private var closing = false

    /**
     * Set once a write has closed the sheet, and never cleared. RN takes a while to dismiss it, and a
     * tap in between would write again: a second close pops the screen under the sheet, and a Save
     * after a Delete writes the deleted workout back.
     */
    private var closed = false

    /** A Save tapped while the quick-add's write was still landing: run once it has, not dropped. */
    private var saveAfterWrite = false

    /** The cancel, and the close that follows a write. */
    val close = actions.close

    fun pick(exerciseId: String) {
        draft = draft.adding(exerciseId)
        pickers.close()
    }

    /** Builds the circuit and closes its picker, taking the selection with it so the next one starts empty. */
    fun addCircuit() {
        if (pickers.selection.size < 2) return
        draft = draft.addingCircuit(pickers.selection)
        pickers.close()
    }

    /**
     * A quick-added exercise, written to the library before it goes into the draft: a block on its own
     * closes the picker, as any pick does; a circuit member is selected and the picker stays up for
     * the next. A refused write keeps the form up, its values in it.
     */
    fun create(exercise: Exercise) {
        if (closed) return
        val mode = pickers.open ?: return
        write(Failure.Kind.Save, { it.withExercise(exercise) }, closes = false) {
            pickers.showQuickAdd(false)
            if (mode == PickerMode.Block) pick(exercise.id) else pickers.toggleMember(exercise.id)
        }
    }

    fun move(
        fromKey: Any,
        toKey: Any,
    ) {
        val from = draft.blocks.indexOfFirst { it.key == fromKey }
        val to = draft.blocks.indexOfFirst { it.key == toKey }
        if (from >= 0 && to >= 0) draft = draft.moving(from, to)
    }

    fun save() {
        if (closed) return
        // Queued only behind the quick-add's write. Behind a Save's, it is a double tap, which would
        // write twice and close twice, the second close popping the screen under the sheet; behind a
        // Delete's, it would write the deleted workout back.
        if (writing) {
            if (!closing) saveAfterWrite = true
            return
        }
        failure = null
        when (val submitted = submitWorkout(draft, existing)) {
            is WorkoutSubmit.Refused -> {
                problem = submitted.problem
            }

            // Deleted elsewhere while the sheet was up, it goes back in with the edit, as an exercise does.
            is WorkoutSubmit.Built -> {
                problem = null
                write(Failure.Kind.Save, { it.withWorkout(submitted.workout) }, closes = true, close)
            }
        }
    }

    /** Refuses while a program points at it, since a delete would leave a week resolving to nothing. */
    fun askToDelete() {
        deleting = inUse() ?: Deleting.Confirm
    }

    fun dismissDelete() {
        deleting = null
    }

    /** Asks [inUse] again: the library is read again while the confirm is up. */
    fun delete() {
        if (closed) return
        val workout = existing ?: return
        deleting = inUse()
        if (deleting == null) write(Failure.Kind.Delete, { it.withoutWorkout(workout.id) }, closes = true, close)
    }

    private fun inUse(): Deleting.InUse? {
        val workout = existing ?: return null
        val usedBy = files.library?.let { programsUsing(it, workout.id) }.orEmpty()
        return Deleting.InUse(usedBy.map { it.name }).takeIf { usedBy.isNotEmpty() }
    }

    /**
     * [change] applied to the library as it is now, not as it was when the sheet opened, then
     * [written], which [closes] the sheet or doesn't. Not cancelled with the screen once it starts,
     * since RN's stores must hear a file that changed.
     */
    private fun write(
        kind: Failure.Kind,
        change: (Library) -> Library,
        closes: Boolean,
        written: () -> Unit,
    ) {
        val library = files.library ?: return
        if (writing) return
        writing = true
        closing = closes
        scope.launch {
            withContext(NonCancellable) {
                val refused =
                    writeOrRefusal {
                        files.writeLibrary(change(library))
                        actions.libraryChanged()
                    }
                failure = refused?.let { Failure(kind, it) }
                writing = false
                closing = false
                if (refused == null) {
                    if (closes) closed = true
                    written()
                }
                if (saveAfterWrite) {
                    saveAfterWrite = false
                    // Not after a refusal: its reason would be replaced before it was read.
                    if (refused == null) save()
                }
            }
        }
    }

    data class Failure(
        val kind: Kind,
        val detail: String,
    ) {
        enum class Kind { Save, Delete }
    }

    sealed interface Deleting {
        data object Confirm : Deleting

        /** [programs]' names, verbatim. */
        data class InUse(
            val programs: List<String>,
        ) : Deleting
    }
}

@Composable
private fun Editing(
    editor: Editor,
    context: BlockContext,
) {
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to -> editor.move(from.key, to.key) }
    val blocks = editor.draft.blocks
    LazyColumn(
        Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding(),
        state = listState,
        contentPadding = PaddingValues(start = Spacing.three, end = Spacing.three, bottom = Spacing.four),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.two + 2.dp),
    ) {
        item(key = HEADER_KEY) { Column(SheetColumn) { Header(editor) } }
        items(blocks, key = { it.key }) { draft ->
            ReorderableItem(reorder, key = draft.key, modifier = SheetColumn) { dragging ->
                val lift by animateDpAsState(if (dragging) 8.dp else 0.dp, label = "lift")
                // RN's pickup haptic, so a grab that took reads differently from one that missed.
                val haptic = LocalHapticFeedback.current
                val handle =
                    Modifier.draggableHandle(
                        onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    )
                // A block whose exercise is gone draws nothing, as in RN, but still counts here: it is
                // still in the workout, and in the file Save writes.
                val place =
                    BlockPlace(blocks.indexOf(draft), blocks.size, handle) {
                        editor.draft = editor.draft.movingBy(draft.key, it)
                    }
                Box(Modifier.shadow(lift, RoundedCornerShape(16.dp))) {
                    Block(draft, context, place, editor)
                }
            }
        }
        footer(editor, context)
    }
}

@Composable
private fun Header(editor: Editor) {
    val colors = KettleTheme.colors
    val title = if (editor.existing == null) R.string.workoutEditor_newTitle else R.string.workoutEditor_editTitle
    Text(
        stringResource(title),
        Modifier.semantics { heading() },
        style = KettleType.subtitle,
        color = colors.text,
    )
    Text(
        stringResource(R.string.common_name),
        Modifier.padding(top = Spacing.three - 2.dp, bottom = Spacing.one),
        style = KettleType.small,
        color = colors.textSecondary,
    )
    FormField(
        editor.draft.name,
        { editor.draft = editor.draft.copy(name = it) },
        name = stringResource(R.string.common_name),
        placeholder = stringResource(R.string.workoutEditor_namePlaceholder),
        FieldOptions(kind = FieldKind.Text, fill = colors.backgroundElement),
    )
    Text(
        pluralText(
            R.plurals.workoutEditor_blockCount,
            R.string.workoutEditor_blockCount_zero,
            editor.draft.blocks.size,
        ),
        Modifier.padding(top = Spacing.two),
        style = KettleType.default,
        color = colors.textSecondary,
    )
}

/** One block: a circuit's card, or a plain block's row. A block whose exercise is gone draws nothing, as in RN. */
@Composable
private fun Block(
    draft: DraftBlock,
    context: BlockContext,
    place: BlockPlace,
    editor: Editor,
) {
    val key = draft.key
    when (val block = draft.block) {
        is WorkoutBlock.Circuit -> {
            CircuitCard(
                block,
                context,
                place,
                onChange = { changed -> editor.draft = editor.draft.withCircuit(key) { changed } },
                onRemove = { editor.draft = editor.draft.removing(key) },
            )
        }

        is WorkoutBlock.ExerciseBlock -> {
            val exercise = context.exercise(block.exerciseId) ?: return
            BlockRow(exercise, block, context.units, place, onRemove = { editor.draft = editor.draft.removing(key) })
        }
    }
}

/** Everything after the blocks: the add buttons and their pickers, the refusal, Save, and Delete. */
private fun LazyListScope.footer(
    editor: Editor,
    context: BlockContext,
) = item(key = FOOTER_KEY) {
    val colors = KettleTheme.colors
    Column(SheetColumn) {
        Row(Modifier.padding(top = Spacing.one), horizontalArrangement = Arrangement.spacedBy(Spacing.two - 2.dp)) {
            for (mode in PickerMode.entries) AddButton(editor.pickers, mode)
        }
        editor.pickers.open?.let { mode ->
            ExercisePicker(
                editor.pickers,
                mode,
                context,
                PickerActions(editor::pick, editor::addCircuit, editor::create),
            )
        }
        refusalText(editor)?.let {
            Text(it, Modifier.padding(top = Spacing.two), style = KettleType.small, color = colors.accentText)
        }
        EditorButtons(stringResource(R.string.common_save), onCancel = editor.close, onPrimary = editor::save)
        val existing = editor.existing
        if (existing != null) {
            Box(Modifier.fillMaxWidth().padding(top = Spacing.three - 2.dp), contentAlignment = Alignment.Center) {
                TextAction(
                    stringResource(R.string.workoutEditor_deleteWorkout),
                    KettleType.smallMedium,
                    colors.textSecondary,
                    onClick = editor::askToDelete,
                )
            }
            DeleteDialog(editor, existing)
        }
    }
}

/**
 * "+ Add block" or "+ New circuit", reading "Close" while its picker is open. `minHeight`: at half
 * width the label wraps in any language wordier than English, and RN's fixed 48 clipped it.
 */
@Composable
private fun RowScope.AddButton(
    pickers: Pickers,
    mode: PickerMode,
) {
    val colors = KettleTheme.colors
    val open = pickers.open == mode
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .dashedBorder(1.5.dp, colors.border, 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button) { pickers.toggle(mode) }
            .semantics {
                if (open) {
                    collapse { true.also { pickers.toggle(mode) } }
                } else {
                    expand { true.also { pickers.toggle(mode) } }
                }
            }.padding(Spacing.two),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(
                when {
                    open -> R.string.common_close
                    mode == PickerMode.Block -> R.string.workoutEditor_addBlock
                    else -> R.string.workoutEditor_newCircuit
                },
            ),
            style = KettleType.heading,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun refusalText(editor: Editor): String? =
    when (editor.problem) {
        WorkoutProblem.NameRequired -> stringResource(R.string.common_nameRequired)
        WorkoutProblem.NoId -> stringResource(R.string.common_couldNotDeriveId)
        null -> null
    } ?: editor.failure?.let {
        stringResource(
            if (it.kind == Editor.Failure.Kind.Save) R.string.common_saveFailed else R.string.common_deleteFailed,
            it.detail,
        )
    }

/** Delete's two questions: refused while a program uses it, else confirmed before anything goes. */
@Composable
private fun DeleteDialog(
    editor: Editor,
    workout: Workout,
) {
    val deleting = editor.deleting ?: return
    // Every name here is the user's, interpolated verbatim.
    when (deleting) {
        is Editor.Deleting.InUse -> {
            KettleAlertDialog(
                title = stringResource(R.string.workoutEditor_inUseTitle),
                body =
                    stringResource(
                        R.string.workoutEditor_inUseBody,
                        workout.name,
                        deleting.programs.joinToString(", "),
                    ),
                onDismissRequest = editor::dismissDelete,
                // RN's buttonless alert, which Android titles with the platform's own OK.
                confirm = DialogAction(stringResource(android.R.string.ok), editor::dismissDelete),
            )
        }

        Editor.Deleting.Confirm -> {
            KettleAlertDialog(
                title = stringResource(R.string.workoutEditor_deleteConfirmTitle),
                body = stringResource(R.string.common_deleteConfirmBody, workout.name),
                onDismissRequest = editor::dismissDelete,
                confirm = DialogAction(stringResource(R.string.common_delete), editor::delete),
                dismiss = DialogAction(stringResource(R.string.common_cancel), editor::dismissDelete),
            )
        }
    }
}

private const val HEADER_KEY = "header"
private const val FOOTER_KEY = "footer"
