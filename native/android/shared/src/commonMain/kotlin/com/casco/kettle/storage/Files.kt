package com.casco.kettle.storage

// The first slice of M8's file layer (#368): the two reads the conformance corpus needs on iOS, over
// plain `String` paths so common code can hold the layout under a root. M8 adds the writes beside
// them (the research note on #402 has the whole surface) rather than replacing these, which is why
// they are production code and not a test helper, and why there is no Okio: neither it nor
// kotlinx-io syncs a file on Apple, so the atomic write stays hand-written either way.

/** The file's text as UTF-8, or null where it is missing or can't be read. */
internal expect fun readTextOrNull(path: String): String?

/** The names of the regular files in [dir], empty where it doesn't exist. Unsorted: callers sort. */
internal expect fun listFileNames(dir: String): List<String>
