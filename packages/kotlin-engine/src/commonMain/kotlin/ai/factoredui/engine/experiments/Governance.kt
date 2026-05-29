package ai.factoredui.engine.experiments

import ai.factoredui.engine.factors.FactorDelta
import kotlin.math.abs

/**
 * Governance verdict logic — the gate that decides whether an experiment may
 * conclude, must be flagged for human review, or should keep running.
 *
 * Ported from `packages/core/src/experiment/governance.ts`. Pure. Conservative
 * rules:
 * - Need ≥2 variants, ≥1 threshold, and a `control` variant, else continue.
 * - A treatment whose magnitude is worse than control on any factor ⇒ flag_review.
 * - No significant factor ⇒ continue.
 * - Significant factors disagreeing on the winner ⇒ flag_review.
 * - Exactly one winner ⇒ conclude.
 */

private const val CONTROL_VARIANT = "control"

fun computeGovernanceVerdict(
    results: List<VariantResult>,
    thresholds: List<Threshold>,
): GovernanceVerdict {
    if (results.size < 2 || thresholds.isEmpty()) return emptyVerdict()

    val controlResult = results.firstOrNull { it.variantKey == CONTROL_VARIANT } ?: return emptyVerdict()
    val treatmentResults = results.filter { it.variantKey != CONTROL_VARIANT }
    val factorVerdicts = buildFactorVerdicts(controlResult, treatmentResults, thresholds)

    return deriveVerdict(factorVerdicts)
}

fun isThresholdExceeded(value: Double, operator: ThresholdOperator, threshold: Double): Boolean =
    when (operator) {
        ThresholdOperator.GT -> value > threshold
        ThresholdOperator.LT -> value < threshold
        ThresholdOperator.GTE -> value >= threshold
        ThresholdOperator.LTE -> value <= threshold
        ThresholdOperator.EQ -> value == threshold
    }

private fun buildFactorVerdicts(
    controlResult: VariantResult,
    treatmentResults: List<VariantResult>,
    thresholds: List<Threshold>,
): List<FactorVerdict> {
    val verdicts = mutableListOf<FactorVerdict>()

    for (threshold in thresholds) {
        val controlDelta = findFactorDelta(controlResult.factorDeltas, threshold.factorName)
        val bestTreatment = findBestTreatment(treatmentResults, threshold)
        if (bestTreatment == null || controlDelta == null) continue

        val improvement = abs(bestTreatment.delta) - abs(controlDelta)
        val isSignificant = isThresholdExceeded(improvement, threshold.operator, threshold.value)

        verdicts.add(
            FactorVerdict(
                factorName = threshold.factorName,
                bestVariant = bestTreatment.variantKey,
                bestDelta = bestTreatment.delta,
                controlDelta = controlDelta,
                isSignificant = isSignificant,
            ),
        )
    }

    return verdicts
}

private data class TreatmentCandidate(val variantKey: String, val delta: Double)

private fun findBestTreatment(
    treatmentResults: List<VariantResult>,
    threshold: Threshold,
): TreatmentCandidate? {
    var best: TreatmentCandidate? = null
    for (result in treatmentResults) {
        val delta = findFactorDelta(result.factorDeltas, threshold.factorName) ?: continue
        if (best == null || abs(delta) > abs(best.delta)) {
            best = TreatmentCandidate(result.variantKey, delta)
        }
    }
    return best
}

private fun findFactorDelta(deltas: List<FactorDelta>, factorName: String): Double? =
    deltas.firstOrNull { it.factorName == factorName }?.delta

private fun deriveVerdict(factorVerdicts: List<FactorVerdict>): GovernanceVerdict {
    if (factorVerdicts.isEmpty()) return emptyVerdict()

    val hasWorseningTreatment = factorVerdicts.any { abs(it.bestDelta) < abs(it.controlDelta) }
    if (hasWorseningTreatment) {
        return GovernanceVerdict(GovernanceAction.FLAG_REVIEW, null, factorVerdicts)
    }

    val significantVerdicts = factorVerdicts.filter { it.isSignificant }
    if (significantVerdicts.isEmpty()) {
        return GovernanceVerdict(GovernanceAction.CONTINUE, null, factorVerdicts)
    }

    val winningVariants = significantVerdicts.map { it.bestVariant }.toSet()
    if (winningVariants.size != 1) {
        return GovernanceVerdict(GovernanceAction.FLAG_REVIEW, null, factorVerdicts)
    }

    return GovernanceVerdict(GovernanceAction.CONCLUDE, winningVariants.first(), factorVerdicts)
}

private fun emptyVerdict(): GovernanceVerdict =
    GovernanceVerdict(GovernanceAction.CONTINUE, null, emptyList())
