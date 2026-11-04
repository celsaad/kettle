package com.casco.kettle.shell.importer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.casco.kettle.domain.ChangedItem
import com.casco.kettle.domain.ImportPreview
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.MergeError
import com.casco.kettle.domain.MergeResult
import com.casco.kettle.domain.ParseResult
import com.casco.kettle.domain.mergeLibraries
import com.casco.kettle.domain.parseLibraryYaml
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.writeOrRefusal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What Import asks of the shell, as parameters so a test hears each one. [pickFile] is the system
 * document picker and the read after it; [libraryChanged] follows the write. [copy] and [share] say
 * whether they took, so neither a "Copied" nor silence is ever claimed over one that didn't.
 */
class ImportActions(
    val pickFile: suspend () -> FilePick,
    val close: () -> Unit,
    val libraryChanged: () -> Unit,
    val copy: (String) -> Boolean,
    /** Null where the clipboard refused to be read; blank where it held nothing. */
    val readClipboard: () -> String?,
    val share: (String) -> Boolean,
)

/** Why the clipboard didn't fill the box: nothing came back, or it couldn't be read at all. */
enum class PasteNote { Empty, Refused }

/** What the system document picker gave back. [Failed] carries the platform's own words. */
sealed interface FilePick {
    data object Cancelled : FilePick

    data class Picked(
        val name: String,
        val sizeBytes: Long,
        val text: String,
    ) : FilePick

    data class Failed(
        val detail: String,
    ) : FilePick
}

/** Where a preview came from, which its card names: a file, a paste, or a bundled pack. */
sealed interface ImportSource {
    data class File(
        val name: String,
        val sizeBytes: Long,
    ) : ImportSource

    data class Pasted(
        val lines: Int,
    ) : ImportSource

    data class Pack(
        val id: String,
    ) : ImportSource
}

/**
 * Why nothing was merged, as data the screen words. The parse and merge refusals are about the content,
 * and their ids and details are the file's own, never translated; the rest are about getting the file
 * in or the library out.
 */
sealed interface ImportRefusal {
    data class InvalidYaml(
        val detail: String,
    ) : ImportRefusal

    data class SchemaMismatch(
        val detail: String,
    ) : ImportRefusal

    data class Merge(
        val error: MergeError,
    ) : ImportRefusal

    data object LibraryNotLoaded : ImportRefusal

    data class ReadFailed(
        val detail: String,
    ) : ImportRefusal

    data class SaveFailed(
        val detail: String,
    ) : ImportRefusal
}

/**
 * Whether whoever wrote the YAML can act on this, which is what earns it a report to hand back. A
 * file that couldn't be read, a disk that wouldn't take the write or a library not yet read are not
 * the content's fault, and offering an assistant "no space left on device" would ask it to fix
 * something it can't reach.
 */
val ImportRefusal.aboutContent: Boolean
    get() = this is ImportRefusal.InvalidYaml || this is ImportRefusal.SchemaMismatch || this is ImportRefusal.Merge

/** A preview waiting on Merge: what it came from, and what merging it would write. */
data class ReadyMerge(
    val source: ImportSource,
    val preview: ImportPreview,
)

/**
 * One visit to Import (#244), as RN's `import.tsx` held it in hooks: the sources, the preview, the one
 * write, and what landed. Every source reaches [review], so a pack or a paste is refused for exactly
 * the reasons and in the words a file is: there is no second path that could accept what this refuses.
 */
internal class Importer(
    private val files: LatestFiles,
    private val scope: CoroutineScope,
    private val actions: ImportActions,
) {
    /** The picker and the read after it, which is when a second source could race the first. */
    var busy by mutableStateOf(false)
        private set
    var refusal by mutableStateOf<ImportRefusal?>(null)
        private set
    var ready by mutableStateOf<ReadyMerge?>(null)
        private set

    /**
     * What landed, kept at the write rather than read off the preview: once the write lands the two
     * libraries a diff compares are the same, and every row would claim no change. The diffs are left
     * behind on purpose, being about a "before" that no longer exists.
     */
    var applied by mutableStateOf<List<ChangedItem>?>(null)
        private set

    var pasting by mutableStateOf(false)
    var pasted by mutableStateOf("")
        private set

    var pasteNote by mutableStateOf<PasteNote?>(null)
        private set

    /** The brief out and the refusal back: the two hand-offs to an assistant. */
    val handoff = Handoff(scope, actions)

    /**
     * One flag for both lists, which never show together: someone who opened the full list to find an
     * id is the same person reading what landed. No reset between files, since there is no second file:
     * the sources are gone once a preview is up, and the only ways out are Cancel and Merge.
     */
    var showAll by mutableStateOf(false)

    private var writing by mutableStateOf(false)

    /** Merge can be pressed: a preview is up and nothing is in flight. */
    val canMerge: Boolean get() = ready != null && !busy && !writing

    /** Cancel and Done. */
    val close = actions.close

    fun pickFile() {
        if (busy) return
        clear()
        busy = true
        scope.launch {
            try {
                when (val pick = actions.pickFile()) {
                    FilePick.Cancelled -> Unit
                    is FilePick.Failed -> refuse(ImportRefusal.ReadFailed(pick.detail))
                    is FilePick.Picked -> review(pick.text, ImportSource.File(pick.name, pick.sizeBytes))
                }
            } finally {
                // A failed pick leaves the picker usable, rather than a dead screen.
                busy = false
            }
        }
    }

    /**
     * Typing clears the clipboard's note too: it describes one attempt at filling the box, and left
     * standing beside a box filled by hand it would still claim there was nothing to paste.
     */
    fun edit(text: String) {
        pasted = text
        pasteNote = null
    }

    /**
     * Fills the box from the clipboard and stops there: the box is where the chat's prose that came along
     * with the YAML can be seen, and Review stays the only way past it. A blank clipboard says so rather
     * than leaving a tap that visibly did nothing.
     */
    fun pasteFromClipboard() {
        pasteNote = null
        val text = actions.readClipboard()
        when {
            text == null -> pasteNote = PasteNote.Refused
            text.isBlank() -> pasteNote = PasteNote.Empty
            else -> pasted = text
        }
    }

    /** Trimmed, so the count describes the YAML and not the blank lines a chat window appended. */
    fun reviewPaste() {
        if (busy) return
        clear()
        val text = pasted.trim()
        // Blank never reaches the parser, whose "the input is empty" would be an accusation before
        // anything was done wrong.
        pasteNote = null
        if (text.isEmpty()) return
        review(text, ImportSource.Pasted(text.split('\n').size))
    }

    /**
     * [library] is the pack already in the reader's language: what a merge writes is the user's own
     * data from then on, so it's frozen at the language of the tap, never re-picked on a later change.
     */
    fun reviewPack(
        id: String,
        library: Library,
    ) {
        if (busy) return
        clear()
        reviewLibrary(library, ImportSource.Pack(id))
    }

    /**
     * Writes the preview's library, then says what landed rather than closing: the change is a few ids
     * folded into dozens, so a sheet that vanished was the only sign it happened, and read the same as a
     * button that did nothing. Not cancelled with the screen once it starts, since RN's stores must hear
     * a file that changed.
     */
    fun merge() {
        val merge = ready ?: return
        if (!canMerge) return
        writing = true
        scope.launch {
            withContext(NonCancellable) {
                val refused =
                    writeOrRefusal {
                        files.writeLibrary(merge.preview.merged)
                        actions.libraryChanged()
                    }
                writing = false
                if (refused == null) {
                    applied = merge.preview.items
                    ready = null
                } else {
                    refuse(ImportRefusal.SaveFailed(refused))
                }
            }
        }
    }

    private fun clear() {
        refusal = null
        ready = null
    }

    /** Every refusal comes through here, so none can stand under a "Copied" left from a different one. */
    private fun refuse(why: ImportRefusal) {
        refusal = why
        handoff.forget()
    }

    private fun review(
        text: String,
        source: ImportSource,
    ) {
        when (val parsed = parseLibraryYaml(text)) {
            is ParseResult.Ok -> reviewLibrary(parsed.value, source)
            is ParseResult.InvalidYaml -> refuse(ImportRefusal.InvalidYaml(parsed.detail))
            is ParseResult.SchemaMismatch -> refuse(ImportRefusal.SchemaMismatch(parsed.detail))
        }
    }

    private fun reviewLibrary(
        incoming: Library,
        source: ImportSource,
    ) {
        val current = files.library
        if (current == null) {
            refuse(ImportRefusal.LibraryNotLoaded)
            return
        }
        when (val merged = mergeLibraries(current, incoming)) {
            is MergeResult.Ok -> ready = ReadyMerge(source, ImportPreview(current, merged.library, merged.summary))
            is MergeResult.Refused -> refuse(ImportRefusal.Merge(merged.error))
        }
    }
}
