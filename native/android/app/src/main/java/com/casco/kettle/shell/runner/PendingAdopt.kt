package com.casco.kettle.shell.runner

import com.casco.kettle.domain.RunnerCommand
import com.casco.kettle.domain.RunnerStep

/**
 * A load a tap on Last time or Try set (#410), waiting for its set to be logged before the library takes
 * it as the exercise's target. Held by the screen in memory only: a kill between the tap and the log just
 * leaves the library as it was, which needs no snapshot change.
 */
internal data class PendingAdopt(
    val exerciseId: String,
    val weightKg: Double,
    val visit: Long,
)

/**
 * Whether [command] logs the set [pending] was picked on with its load untouched, so the library takes
 * it now: Log set (an advance) or ending the session (a finish, which logs the set on screen), aimed at
 * that visit, with the load still the one picked. A load changed after the tap, or a step left another
 * way, writes nothing.
 */
internal fun writesLibraryOn(
    pending: PendingAdopt?,
    command: RunnerCommand,
    view: RunnerView.Running?,
): Boolean {
    val aimedAt =
        when (command) {
            is RunnerCommand.Advance -> command.visit
            is RunnerCommand.Finish -> command.visit
            else -> null
        }
    val snapshot = view?.snapshot
    val step = snapshot?.step as? RunnerStep.Reps
    return pending != null &&
        aimedAt == pending.visit &&
        snapshot?.visit == pending.visit &&
        step?.exerciseId == pending.exerciseId &&
        snapshot.inputs.weightKg == pending.weightKg
}

/**
 * The runner's commands with a picked load riding along (#410): [pickLoad] sets the set's load at once and
 * holds it; [send] writes it to the library as the command that logs that set goes out, by [writesLibraryOn].
 * Anything that leaves the set drops it. [actions] and [view] are read when called, so a held command
 * never acts on a composition already gone.
 */
internal class AdoptingSend(
    private val actions: () -> RunnerActions,
    private val view: () -> RunnerView.Running?,
) {
    private var pending: PendingAdopt? = null

    fun pickLoad(
        exerciseId: String,
        weightKg: Double,
        visit: Long,
    ) {
        send(RunnerCommand.SetWeight(weightKg))
        pending = PendingAdopt(exerciseId, weightKg, visit)
    }

    fun send(command: RunnerCommand) {
        val held = pending
        if (held != null && writesLibraryOn(held, command, view())) actions().adopt(held.exerciseId, held.weightKg)
        if (command is RunnerCommand.Advance || command is RunnerCommand.Finish) pending = null
        actions().send(command)
    }
}
