package com.casco.kettle.shell.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.casco.kettle.domain.ProgramPosition
import com.casco.kettle.domain.nextUpView
import com.casco.kettle.shell.files.LatestFiles
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * Where the program Today runs stands (#413), for Library's rows: the one its card would show, so
 * "Active, week 3" and "In your program" never disagree with Today. Worked out again on every read, as
 * Today's card is, since the day turning alone can move it. Null with no program running.
 */
@Composable
internal fun rememberProgramPosition(
    files: LatestFiles,
    clock: Clock,
): ProgramPosition? {
    val library = files.library
    val log = files.sessionLog
    return remember(library, log, files.reads) {
        library?.let {
            nextUpView(it, log?.logged.orEmpty(), clock.now(), TimeZone.currentSystemDefault())?.position
        }
    }
}
