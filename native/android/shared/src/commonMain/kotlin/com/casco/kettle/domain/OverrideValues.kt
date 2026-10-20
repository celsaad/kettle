package com.casco.kettle.domain

// The two directions between a patch's values and the raw tree a schema reader reads and the writer
// emits, shared by both of those, by merging an override, and by the conformance runner.

/** A patch value as the raw tree holds it, which is what a schema reader reads. */
internal fun OverrideValue.toRaw(): Any =
    when (this) {
        is OverrideValue.Numeric -> value
        is OverrideValue.Text -> value
    }

/** Raw values back to a patch's: the writer's numbers, or whatever a patch laid over them. */
internal fun overrideValues(raw: Map<String, Any?>): Map<String, OverrideValue> =
    raw.mapValues { (_, value) ->
        when (value) {
            is Number -> OverrideValue.Numeric(value.toDouble())
            else -> OverrideValue.Text(value as String)
        }
    }
