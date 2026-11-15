package com.casco.kettle.domain

/**
 * Whether a rest step follows [index], for the step-list tests: it can't be assumed, since back-to-back
 * sets (`rest_sec: 0`) emit none.
 */
internal fun restFollows(
    steps: List<RunnerStep>,
    index: Int,
): Boolean = steps.getOrNull(index + 1) is RunnerStep.Rest
