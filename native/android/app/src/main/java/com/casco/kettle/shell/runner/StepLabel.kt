package com.casco.kettle.shell.runner

import android.content.res.Resources
import com.casco.kettle.domain.IntervalVariant
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.StepInputs
import com.casco.kettle.shell.R

// The step header's label line (#409), and what a reader hears for it inside a circuit.

/**
 * The header's label line (#409): the step's kind and where it sits, "Reps · set 1 of 4". Inside a
 * circuit it is the circuit's round instead, which is the count a member's own sets give way to there.
 * An AMRAP's is the round under way, which only its [inputs] know (#417).
 */
fun Resources.stepLabel(
    step: RunnerStep,
    inputs: StepInputs,
): String {
    // A rest says so even inside a circuit; its spoken line (circuitSpoken) still gives the round.
    val circuit = step.circuit?.takeIf { step !is RunnerStep.Rest }
    if (circuit != null) return getString(R.string.session_header_circuit, "${circuit.round}", "${circuit.rounds}")
    return when (step) {
        is RunnerStep.Reps -> getString(R.string.session_header_reps, "${step.setIndex}", "${step.setTotal}")
        is RunnerStep.Hold -> getString(R.string.session_header_hold, "${step.setIndex}", "${step.setTotal}")
        is RunnerStep.Rest -> getString(R.string.session_header_rest)
        is RunnerStep.Interval -> intervalLabel(step, inputs.roundsCompleted + 1)
    }
}

private fun Resources.intervalLabel(
    step: RunnerStep.Interval,
    amrapRound: Long,
): String {
    val index = "${step.setIndex}"
    val total = "${step.setTotal}"
    return when (step.variant) {
        IntervalVariant.HIIT -> getString(R.string.session_header_work, index, total)
        IntervalVariant.EMOM -> getString(emomLabel(step), index, total)
        IntervalVariant.AMRAP -> getString(R.string.session_header_amrap, "$amrapRound")
        IntervalVariant.CARDIO -> getString(R.string.session_header_cardio)
    }
}

/** "Minute" only where an interval is one; a 40-second EMOM counts intervals. */
private fun emomLabel(step: RunnerStep.Interval): Int =
    if (step.isMinute) {
        R.string.session_header_emomMinute
    } else {
        R.string.session_header_emomInterval
    }

/**
 * What a reader hears for the label line inside a circuit: the round, and which member of it this is,
 * which the line itself leaves out. Null outside one, where the line says it all, and on a rest: a rest
 * carries the position of the work it follows, and a description replaces the words it describes, so a
 * reader would hear the finished member's position and never "Rest".
 */
fun Resources.circuitSpoken(step: RunnerStep): String? =
    step.circuit?.takeIf { step !is RunnerStep.Rest }?.let {
        // Placeholders by name, alphabetically: index, member, memberTotal, total.
        getString(
            R.string.session_circuit_accessibility,
            "${it.round}",
            "${it.member}",
            "${it.memberTotal}",
            "${it.rounds}",
        )
    }

/** An EMOM whose intervals are minutes, which its header and its rest call them; any other length counts intervals. */
internal val RunnerStep.Interval.isMinute: Boolean get() = targetSec == MINUTE_SEC

private const val MINUTE_SEC = 60.0
