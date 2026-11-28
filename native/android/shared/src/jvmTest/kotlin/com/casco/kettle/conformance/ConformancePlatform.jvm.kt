package com.casco.kettle.conformance

import java.io.File

// Gradle's `ConformanceCorpus` argument provider sets the property, and `-PupdateConformance` the flag.

internal actual fun conformanceDirSetting(): String? =
    System.getProperty("kettle.conformanceDir")?.let { File(it).invariantSeparatorsPath }

internal actual fun goldenWriter(): ((path: String, text: String) -> Unit)? =
    if (System.getProperty("kettle.updateConformance") == "true") { path, text -> File(path).writeText(text) } else null
