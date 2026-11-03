package com.casco.kettle.shell.files

import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionLog

/**
 * The base every screen test's [StoredFiles] fake extends (#274): nothing on disk to read, and every
 * write a failure naming itself, so a test overrides only what its screen reads and writes. A screen
 * that starts writing something new then fails its own test loudly, rather than a new method on
 * [StoredFiles] meaning an edit to every fake.
 */
open class FakeStoredFiles : StoredFiles {
    override fun sessionLog(): SessionLog = SessionLog.Empty

    override fun library(): Library? = null

    override fun preferences(): Preferences? = null

    override fun deleteSession(id: String): Unit = unexpected("deleteSession")

    override fun writeSession(session: Session): Unit = unexpected("writeSession")

    override fun writePreferences(preferences: Preferences): Unit = unexpected("writePreferences")

    override fun writeLibrary(library: Library): Unit = unexpected("writeLibrary")

    private fun unexpected(write: String): Nothing = error("$write isn't expected from this screen")
}
