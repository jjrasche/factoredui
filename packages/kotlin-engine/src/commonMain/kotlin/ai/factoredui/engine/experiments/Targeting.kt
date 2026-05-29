package ai.factoredui.engine.experiments

import ai.factoredui.engine.factors.Factor

/**
 * Targeting predicate engine.
 *
 * Ported from `packages/core/src/experiment/targeting.ts`. Pure: decides
 * whether a user is eligible for an experiment given their factors and device
 * metadata. AND semantics across rules; empty rules ⇒ eligible.
 */

/**
 * True when [factors] + [deviceMetadata] satisfy every rule in [rules].
 * A missing factor (for a factor rule) or absent metadata (for a metadata rule)
 * fails that rule, matching the TS short-circuit-to-false behaviour.
 */
fun evaluateTargeting(
    factors: List<Factor>,
    rules: List<TargetingRule>,
    deviceMetadata: DeviceMetadata? = null,
): Boolean {
    if (rules.isEmpty()) return true
    val factorsByName = factors.associate { it.factorName to it.value }
    return rules.all { rule -> evaluateRule(factorsByName, rule, deviceMetadata) }
}

private fun evaluateRule(
    factorsByName: Map<String, Double>,
    rule: TargetingRule,
    deviceMetadata: DeviceMetadata?,
): Boolean = when (rule) {
    is MetadataRule -> evaluateMetadataRule(rule, deviceMetadata)
    is FactorRule -> {
        val value = factorsByName[rule.factor]
        if (value == null) false else compareNumeric(value, rule.operator, rule.threshold)
    }
}

private fun evaluateMetadataRule(rule: MetadataRule, metadata: DeviceMetadata?): Boolean {
    if (metadata == null) return false
    val fieldValue = metadata.field(rule.field) ?: return false
    return compareString(fieldValue, rule.operator, rule.value)
}

private fun compareNumeric(value: Double, operator: TargetingOperator, threshold: Double): Boolean =
    when (operator) {
        TargetingOperator.GT -> value > threshold
        TargetingOperator.GTE -> value >= threshold
        TargetingOperator.LT -> value < threshold
        TargetingOperator.LTE -> value <= threshold
        TargetingOperator.EQ -> value == threshold
    }

/**
 * Case-insensitive string comparison. `gte`/`lte` are lexicographic (UTF-16
 * code-unit order, like JS string `<`/`>=`), NOT semver — preserved from the TS
 * so version-string rules behave identically.
 */
private fun compareString(value: String, operator: MetadataOperator, target: String): Boolean {
    val lower = value.lowercase()
    val targetLower = target.lowercase()
    return when (operator) {
        MetadataOperator.EQ -> lower == targetLower
        MetadataOperator.NEQ -> lower != targetLower
        MetadataOperator.CONTAINS -> lower.contains(targetLower)
        MetadataOperator.GTE -> lower >= targetLower
        MetadataOperator.LTE -> lower <= targetLower
    }
}
