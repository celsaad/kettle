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
 * [SupporterFile] where RN kept it. Native has been its only reader since the preferences flip (#238);
 * `conformance/supporter.yaml` still holds the format to the one 0.13 installs wrote.
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
