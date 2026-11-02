package com.casco.kettle.shell.support

import com.casco.kettle.domain.SupporterState
import com.casco.kettle.domain.parseSupporterState
import com.casco.kettle.domain.serializeSupporterState
import com.casco.kettle.storage.writeAtomically
import java.io.File
import java.io.IOException

/** `supporter.json`, as a boundary Support's test can fake. Native is its only writer from M4 (#188). */
interface SupporterFile {
    /** Never throws: a missing, unreadable or refused file is the empty state. */
    fun read(): SupporterState

    /** Whether the write landed, so a tip that won't survive a restart can say so. */
    fun write(state: SupporterState): Boolean
}

/**
 * [SupporterFile] where RN keeps it, `src/storage/tip-file.ts`'s. Both read it the same way
 * (`conformance/supporter.yaml`), and RN Settings reads it until M5, so its format is frozen until then.
 */
class DiskSupporterFile(
    private val file: File,
) : SupporterFile {
    override fun read(): SupporterState =
        try {
            if (file.exists()) parseSupporterState(file.readText()) else SupporterState.EMPTY
        } catch (_: IOException) {
            SupporterState.EMPTY
        } catch (_: SecurityException) {
            SupporterState.EMPTY
        }

    /**
     * Whole or not at all, so a write cut short leaves the last tip count rather than a truncated file,
     * which would read as no tips at all.
     */
    override fun write(state: SupporterState): Boolean =
        try {
            writeAtomically(file, serializeSupporterState(state))
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
}
