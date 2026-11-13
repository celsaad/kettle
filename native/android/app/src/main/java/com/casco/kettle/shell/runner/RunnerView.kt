package com.casco.kettle.shell.runner

import com.casco.kettle.domain.CountIn
import com.casco.kettle.domain.LoggedUnit
import com.casco.kettle.domain.RunnerPlan
import com.casco.kettle.domain.RunnerSnapshot
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.Session

/** Where the finish backup (#220) has got to, which the completion screen warns from (#306). */
enum class FinishBackupState { PENDING, NONE, OK, FAILED }

/**
 * What the runner's screen observes (#295): a value that changes only on transitions. The screen
 * works out the seconds itself from the snapshot's clock with a ticker of its own, so nothing here
 * changes per second.
 */
sealed interface RunnerView {
    data object Idle : RunnerView

    /** A session the service is running or counting into. [plan] is what it resolved, for the pickers. */
    sealed interface Active : RunnerView {
        val plan: RunnerPlan.Runnable
        val start: RunnerStart?
    }

    data class CountingIn(
        override val plan: RunnerPlan.Runnable,
        override val start: RunnerStart?,
        val countIn: CountIn,
    ) : Active

    /**
     * [logged] is what Undo would take back, null when there's nothing to: kept in the engine's memory
     * only, so the view is told it rather than reading it off the snapshot.
     */
    data class Running(
        override val plan: RunnerPlan.Runnable,
        val snapshot: RunnerSnapshot,
        val session: Session,
        override val start: RunnerStart? = null,
        val logged: LoggedUnit? = null,
    ) : Active

    /** The session is over and written. Held until the completion screen's Done ([RunnerHost.dismiss]). */
    data class Completed(
        val plan: RunnerPlan.Runnable,
        val session: Session,
        val backup: FinishBackupState,
    ) : RunnerView
}

/** `:shared`'s is internal to it; the runner's screens and notifications count in the same seconds. */
internal const val MS_PER_SECOND = 1000L
