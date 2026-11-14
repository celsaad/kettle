package com.casco.kettle.shell.settings

/** What a backup said, ok or not: one line, so a stale "backed up" can't sit under a fresh failure. */
internal data class BackupMessage(
    val ok: Boolean,
    val text: String,
)
