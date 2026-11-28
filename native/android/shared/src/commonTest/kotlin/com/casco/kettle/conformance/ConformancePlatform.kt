package com.casco.kettle.conformance

// What the corpus runner needs from its platform beyond `:shared`'s own file reads: where the corpus
// is, which each platform's Gradle task hands it its own way, and, on an update run, where goldens go.

/** The repo's `conformance/` directory with `/` separators, or null where the build didn't say. */
internal expect fun conformanceDirSetting(): String?

/**
 * Writes a golden, given its path and text, on a `-PupdateConformance` run; null on a checking run.
 * Always null on iOS: the goldens have one writer, the JVM, so the simulator only ever checks them.
 */
internal expect fun goldenWriter(): ((path: String, text: String) -> Unit)?
