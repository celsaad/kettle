package com.casco.kettle.domain

/**
 * Each exercise's rest between its sets, by member key, for Workout preview (#439): the rest step right
 * after one of its sets. Keys are per block, and a circuit's rests carry one of their own, so neither a
 * Rest block nor a circuit's rest lands on an exercise. A HIIT's rest between rounds is emitted the same
 * way, so it counts, and an exercise with one set or no rest has none.
 */
fun restsBetweenSets(steps: List<RunnerStep>): Map<String, Double> =
    steps
        .zipWithNext()
        .mapNotNull { (work, rest) ->
            if (work is WorkStep && rest is RunnerStep.Rest && rest.memberKey == work.memberKey) {
                work.memberKey to rest.seconds
            } else {
                null
            }
        }.distinctBy { it.first }
        .toMap()
