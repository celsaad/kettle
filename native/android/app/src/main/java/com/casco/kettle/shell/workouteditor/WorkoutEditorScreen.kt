package com.casco.kettle.shell.workouteditor

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.casco.kettle.domain.withMember
import com.casco.kettle.domain.withRestLength
import com.casco.kettle.domain.withWorkout
import com.casco.kettle.domain.withoutWorkout
import com.casco.kettle.domain.workoutDraftOf
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LibraryWriteRefusal
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.files.libraryWriteOrRefusal
import com.casco.kettle.shell.files.text
import com.casco.kettle.shell.form.EditorTitleBar
import com.casco.kettle.shell.form.FieldKind
import com.casco.kettle.shell.form.FieldOptions
import com.casco.kettle.shell.form.FormField
import com.casco.kettle.shell.form.SheetColumn
import com.casco.kettle.shell.i18n.deviceUnitSystem
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.nav.LocalNavigator
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

/**
 * What the route table draws at `Destination.WorkoutEditor`, as Home and Library push it: with an `id` to
 * edit, none to create.
 */
@Composable
fun WorkoutEditorRoute(params: Map<String, String>) {
    val navigator = LocalNavigator.current
    WorkoutEditorScreen(params["id"], LocalLatestFiles.current, WorkoutEditorActions(close = { navigator.back() }))
}

/** What the editor asks of the shell, as a parameter so a test hears it. */
class WorkoutEditorActions(
    val close: () -> Unit,
)

/**
 * A workout (#242), as RN's workout editor was: its name and its blocks, plain exercises and
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
    var confirmingDelete by mutableStateOf(false)
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

    /** A picked exercise: into the circuit the picker was opened from, or as a block at the end. */
    fun pick(exerciseId: String) {
        val into = pickers.open as? PickerMode.IntoCircuit
        draft =
            if (into != null) draft.withCircuit(into.key) { it.withMember(exerciseId) } else draft.adding(exerciseId)
        pickers.close()
    }

    val pickerActions get() = PickerActions(::pick, ::addCircuit, ::create)

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
            // Exhaustive, so a new mode has to say where its quick-added exercise goes.
            when (mode) {
                PickerMode.Circuit -> pickers.toggleMember(exercise.id)
                PickerMode.Block, is PickerMode.IntoCircuit -> pick(exercise.id)
            }
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

    /**
     * The programs pointing at this workout, by name, verbatim: Delete is unavailable while there are
     * any, since a delete would leave a week resolving to nothing. Read from the library as it is now.
     */
    val usedBy: List<String>
        get() =
            existing
                ?.let { workout ->
                    files.library?.let { programsUsing(it, workout.id) }
                }.orEmpty()
                .map { it.name }

    fun askToDelete() {
        confirmingDelete = usedBy.isEmpty()
    }

    fun dismissDelete() {
        confirmingDelete = false
    }

    /** Asks [usedBy] again: the library can change while the confirm is up. */
    fun delete() {
        if (closed) return
        val workout = existing ?: return
        confirmingDelete = false
        if (usedBy.isEmpty()) write(Failure.Kind.Delete, { it.withoutWorkout(workout.id) }, closes = true, close)
    }

    /**
     * [change] applied to the library as it is now, not as it was when the sheet opened, then
     * [written], which [closes] the sheet or doesn't. Not cancelled with the screen once it starts: a
     * write that lands is shown only by the call that made it, so one cut off after the disk would
     * leave the old library on screen.
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
                    libraryWriteOrRefusal { files.writeLibrary(change(library)) }
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
        val refusal: LibraryWriteRefusal,
    ) {
        enum class Kind { Save, Delete }
    }
}

@Composable
private fun Editing(
    editor: Editor,
    context: BlockContext,
) {
    val colors = KettleTheme.colors
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to -> editor.move(from.key, to.key) }
    val blocks = editor.draft.blocks
    val title = if (editor.existing == null) R.string.workoutEditor_newTitle else R.string.workoutEditor_editTitle
    LazyColumn(
        Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding(),
        state = listState,
        contentPadding = PaddingValues(start = Spacing.three, end = Spacing.three, bottom = Spacing.four),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Save pinned at the top, as the program editor's (#438): the × is the cancel.
        stickyHeader(key = HEADER_KEY) {
            Column(SheetColumn.background(colors.background)) {
                EditorTitleBar(stringResource(title), editor::save, refusalText(editor))
            }
        }
        item(key = NAME_KEY) { Column(SheetColumn) { NameAndSection(editor) } }
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
                // Filled, so a row lifted over the others hides them rather than showing through.
                Box(Modifier.shadow(lift, RoundedCornerShape(12.dp)).background(colors.background)) {
                    Block(draft, context, place, editor)
                }
            }
        }
        footer(editor, context)
    }
}

/** The name, then "In this workout", with "Runs top to bottom" beside it. */
@Composable
private fun NameAndSection(editor: Editor) {
    val colors = KettleTheme.colors
    Text(
        stringResource(R.string.common_name),
        Modifier.padding(bottom = Spacing.one),
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
    Row(Modifier.padding(top = Spacing.four, bottom = Spacing.one), verticalAlignment = Alignment.Bottom) {
        Text(
            stringResource(R.string.workoutEditor_inThisWorkout),
            Modifier.weight(1f).semantics { heading() },
            style = KettleType.heading,
            color = colors.text,
        )
        Text(
            stringResource(R.string.workoutEditor_runsTopToBottom),
            style = KettleType.small,
            color = colors.textSecondary,
        )
    }
}

/** One block: a circuit, or a plain block's row. A block whose exercise is gone draws nothing, as in RN. */
@Composable
private fun Block(
    draft: DraftBlock,
    context: BlockContext,
    place: BlockPlace,
    editor: Editor,
) {
    val key = draft.key
    val remove = { editor.draft = editor.draft.removing(key) }
    when (val block = draft.block) {
        is WorkoutBlock.Circuit -> {
            val pickers = editor.pickers
            CircuitSection(
                block,
                context,
                place,
                CircuitActions(
                    change = { changed -> editor.draft = editor.draft.withCircuit(key) { changed } },
                    remove = remove,
                    togglePicker = { pickers.toggle(PickerMode.IntoCircuit(key)) },
                    picking = pickers.isOpen(PickerMode.IntoCircuit(key)),
                ),
            ) { ExercisePicker(pickers, PickerMode.IntoCircuit(key), context, editor.pickerActions) }
        }

        is WorkoutBlock.ExerciseBlock -> {
            val exercise = context.exercise(block.exerciseId) ?: return
            BlockRow(
                exercise,
                block,
                context.units,
                place,
                BlockActions(remove) { seconds, own -> editor.draft = editor.draft.withRestLength(key, seconds, own) },
            )
        }
    }
}

/** Everything after the blocks: the add buttons and their picker, the rest hint, and the id and Delete. */
private fun LazyListScope.footer(
    editor: Editor,
    context: BlockContext,
) = item(key = FOOTER_KEY) {
    val colors = KettleTheme.colors
    Column(SheetColumn) {
        ListRowSeparator()
        Row(Modifier.padding(top = Spacing.three), horizontalArrangement = Arrangement.spacedBy(Spacing.two)) {
            for (mode in listOf(PickerMode.Block, PickerMode.Circuit)) AddButton(editor.pickers, mode)
        }
        val pickers = editor.pickers
        pickers.open?.takeIf { it !is PickerMode.IntoCircuit }?.let { mode ->
            ExercisePicker(pickers, mode, context, editor.pickerActions)
        }
        Text(
            stringResource(R.string.workoutEditor_restHint),
            Modifier.padding(top = Spacing.two),
            style = KettleType.small,
            color = colors.textSecondary,
        )
        editor.existing?.let { IdAndDelete(editor, it) }
    }
}

/**
 * "+ Add exercise" or "+ Add circuit", reading "Close" while its picker is open. `minHeight`: at half
 * width the label wraps in any language wordier than English, and RN's fixed 48 clipped it.
 */
@Composable
private fun RowScope.AddButton(
    pickers: Pickers,
    mode: PickerMode,
) {
    val colors = KettleTheme.colors
    val open = pickers.isOpen(mode)
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(colors.backgroundElement)
            .border(1.dp, colors.border, shape)
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
                    mode == PickerMode.Block -> R.string.workoutEditor_addExercise
                    else -> R.string.workoutEditor_addCircuit
                },
            ),
            style = KettleType.smallMedium,
            color = colors.text,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The workout's id, as text, and Delete: unavailable while a program uses the workout, with why under
 * it, rather than a dialog after the tap. The names are the user's, verbatim.
 */
@Composable
private fun IdAndDelete(
    editor: Editor,
    workout: Workout,
) {
    val colors = KettleTheme.colors
    val usedBy = editor.usedBy
    ListRowSeparator(Modifier.padding(top = Spacing.four))
    val reason =
        usedBy.takeIf { it.isNotEmpty() }?.let {
            stringResource(
                R.string.workoutEditor_inUseReason,
                it.joinToString(stringResource(R.string.format_listSeparator)),
            )
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.workoutEditor_idLabel, workout.id),
            Modifier.weight(1f),
            style = KettleType.code,
            color = colors.textSecondary,
        )
        val enabled = usedBy.isEmpty()
        Text(
            stringResource(R.string.workoutEditor_deleteWorkout),
            Modifier
                .heightIn(min = 48.dp)
                // Disabled rather than refusing after the tap: TalkBack says it's unavailable.
                .clickable(enabled = enabled, role = Role.Button, onClick = editor::askToDelete)
                // Its reason with it, so TalkBack says why it is unavailable on the button itself.
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
            title = stringResource(R.string.workoutEditor_deleteConfirmTitle),
            body = stringResource(R.string.common_deleteConfirmBody, workout.name),
            onDismissRequest = editor::dismissDelete,
            confirm = DialogAction(stringResource(R.string.common_delete), editor::delete),
            dismiss = DialogAction(stringResource(R.string.common_cancel), editor::dismissDelete),
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
        it.refusal.text(
            if (it.kind == Editor.Failure.Kind.Save) R.string.common_saveFailed else R.string.common_deleteFailed,
        )
    }

private const val HEADER_KEY = "header"
private const val NAME_KEY = "name"
private const val FOOTER_KEY = "footer"
