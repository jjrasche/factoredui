package ai.factoredui.engine.experiments

/**
 * Experiment authoring validation.
 *
 * The failure contract is a sealed type, not prose. Cross-repo consumers
 * (agent-platform's experiment-author UI) match on the [ExperimentValidationError]
 * case and render their own copy — so factored-ui can change wording freely and
 * nobody string-parses an error message. This is how "parity matters" is
 * honoured structurally rather than textually.
 *
 * The store-bound create/start lifecycle (insert experiment + variants, draft →
 * running) is the Postgres layer's job; this is the I/O-free precondition it
 * runs first.
 */
sealed interface ExperimentValidationError {
    /** Fewer than two variants — an experiment needs at least a control + one treatment. */
    object TooFewVariants : ExperimentValidationError

    /** No variant keyed "control" — there is no baseline to measure against. */
    object MissingControl : ExperimentValidationError

    /** Variant traffic percentages do not sum to 100. */
    data class TrafficNotHundred(val actual: Int) : ExperimentValidationError
}

/** Thrown by [validateDefinition]; carries the structured [error]. */
class ExperimentValidationException(val error: ExperimentValidationError) :
    IllegalArgumentException(error.toString())

/**
 * Returns the first validation error for [definition], or null when valid.
 * Prefer this over [validateDefinition] when you want to branch on the failure
 * (e.g. render author-facing copy) rather than catch an exception.
 */
fun findValidationError(definition: ExperimentDefinition): ExperimentValidationError? {
    if (definition.variants.size < 2) return ExperimentValidationError.TooFewVariants
    if (definition.variants.none { it.variantKey == "control" }) return ExperimentValidationError.MissingControl
    val totalTraffic = definition.variants.sumOf { it.trafficPercentage }
    if (totalTraffic != 100) return ExperimentValidationError.TrafficNotHundred(totalTraffic)
    return null
}

/** Throws [ExperimentValidationException] when [definition] is invalid. */
fun validateDefinition(definition: ExperimentDefinition) {
    findValidationError(definition)?.let { throw ExperimentValidationException(it) }
}
