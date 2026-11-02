package com.casco.kettle.domain

// The session editor's whole-session half, over `SessionEntryForm.kt`'s one entry: what it offers for a
// session, and the one session it writes back. RN spread this over the screen and the store's
// `editEntry`/`removeEntry`; here it is one pure step, so the index rules are tested without a screen.

/**
 * One editable entry's working state. [index] is its position in the session's own entries, **not**
 * History's view of them, which leaves rests out: writing back by History's position would rewrite
 * whichever entry sat at that offset. No [forms] means its last set was removed, so it goes on save.
 */
data class EntryDraft(
    val index: Int,
    val entry: SessionEntry,
    val forms: List<SetForm>,
)

/** What the editor can do with one session. */
sealed interface SessionEditing {
    data object NotFound : SessionEditing

    /**
     * Still being logged: the runner owns its file and writes through its own copy, so an edit made
     * here would be overwritten by the next set, and the screen offers no fields at all.
     */
    data object Running : SessionEditing

    data object NothingToEdit : SessionEditing

    /**
     * [session] with a draft per entry it can rewrite, and the ones it can't in [readOnly]: shown rather
     * than dropped, since a session that lost its EMOM on the way in would read as if the log had.
     */
    data class Editable(
        val session: Session,
        val drafts: List<EntryDraft>,
        val readOnly: List<SessionEntry>,
    ) : SessionEditing
}

/**
 * Session [id] of [logged], the log with its zero work taken out (`SessionLog.logged`), as RN's editor
 * read it: an old session shows without its zero sets, and the indexes count what it shows. The write
 * then leaves the file clean too.
 */
fun sessionEditing(
    logged: List<Session>,
    id: String?,
    system: UnitSystem,
): SessionEditing {
    val session = logged.firstOrNull { it.id == id }
    val drafts =
        session?.entries.orEmpty().mapIndexedNotNull { index, entry ->
            entryFields(entry)?.let { EntryDraft(index, entry, entryToSetForms(entry, system)) }
        }
    return when {
        session == null -> SessionEditing.NotFound
        session.endedAt == null -> SessionEditing.Running
        drafts.isEmpty() -> SessionEditing.NothingToEdit
        else -> SessionEditing.Editable(session, drafts, session.entries.filterIsInstance<SessionEntry.Emom>())
    }
}

/** The first problem in any of [drafts], checked before anything is written, so a save is all or nothing. */
fun firstProblem(drafts: List<EntryDraft>): EntryProblem? =
    drafts.firstNotNullOfOrNull { validateEntryForm(it.entry, it.forms) }

/**
 * [session] with each draft's entry rebuilt in its place, and dropped where nothing of it is left. One
 * rebuild rather than RN's edit-then-remove calls, which had to remove back to front because each
 * removal shifted every later index. Entries without a draft stay exactly as they were.
 */
fun editedSession(
    session: Session,
    drafts: List<EntryDraft>,
    system: UnitSystem,
): Session {
    val byIndex = drafts.associateBy { it.index }
    val entries =
        session.entries.mapIndexedNotNull { index, entry ->
            val draft = byIndex[index] ?: return@mapIndexedNotNull entry
            buildEntry(draft.entry, draft.forms, system)
        }
    return session.copy(entries = entries)
}
