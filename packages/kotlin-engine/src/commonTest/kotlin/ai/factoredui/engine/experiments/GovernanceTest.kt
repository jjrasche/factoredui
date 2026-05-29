package ai.factoredui.engine.experiments

import ai.factoredui.engine.factors.FactorDelta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GovernanceTest {

    private fun variant(key: String, vararg deltas: Pair<String, Double>) = VariantResult(
        variantKey = key,
        userCount = 100,
        factorDeltas = deltas.map { (name, d) -> FactorDelta(name, before = 0.0, after = d, delta = d) },
    )

    private fun threshold(factor: String, op: ThresholdOperator = ThresholdOperator.GT, value: Double = 0.0) =
        Threshold("t-$factor", factor, componentPath = null, operator = op, value = value, action = ThresholdAction.EXPERIMENT)

    @Test
    fun continuesWithFewerThanTwoVariants() {
        val verdict = computeGovernanceVerdict(listOf(variant("control", "f1" to 0.1)), listOf(threshold("f1")))
        assertEquals(GovernanceAction.CONTINUE, verdict.action)
        assertNull(verdict.winningVariant)
        assertTrue(verdict.factorVerdicts.isEmpty())
    }

    @Test
    fun continuesWithNoThresholds() {
        val results = listOf(variant("control", "f1" to 0.1), variant("treatment", "f1" to 0.3))
        assertEquals(GovernanceAction.CONTINUE, computeGovernanceVerdict(results, emptyList()).action)
    }

    @Test
    fun continuesWithoutControl() {
        val results = listOf(variant("a", "f1" to 0.1), variant("b", "f1" to 0.3))
        assertEquals(GovernanceAction.CONTINUE, computeGovernanceVerdict(results, listOf(threshold("f1"))).action)
    }

    @Test
    fun concludesWithSingleSignificantWinner() {
        val results = listOf(variant("control", "f1" to 0.1), variant("treatment", "f1" to 0.3))
        val verdict = computeGovernanceVerdict(results, listOf(threshold("f1", ThresholdOperator.GT, 0.0)))
        assertEquals(GovernanceAction.CONCLUDE, verdict.action)
        assertEquals("treatment", verdict.winningVariant)
    }

    @Test
    fun flagsReviewWhenTreatmentWorseThanControl() {
        val results = listOf(variant("control", "f1" to 0.3), variant("treatment", "f1" to 0.1))
        val verdict = computeGovernanceVerdict(results, listOf(threshold("f1")))
        assertEquals(GovernanceAction.FLAG_REVIEW, verdict.action)
        assertNull(verdict.winningVariant)
    }

    @Test
    fun continuesWhenNoFactorSignificant() {
        val results = listOf(variant("control", "f1" to 0.1), variant("treatment", "f1" to 0.15))
        // improvement 0.05 does not exceed threshold 0.2
        val verdict = computeGovernanceVerdict(results, listOf(threshold("f1", ThresholdOperator.GT, 0.2)))
        assertEquals(GovernanceAction.CONTINUE, verdict.action)
    }

    @Test
    fun flagsReviewWhenSignificantWinnersDisagree() {
        val results = listOf(
            variant("control", "f1" to 0.1, "f2" to 0.1),
            variant("a", "f1" to 0.5, "f2" to 0.0),
            variant("b", "f1" to 0.0, "f2" to 0.5),
        )
        val thresholds = listOf(threshold("f1"), threshold("f2"))
        val verdict = computeGovernanceVerdict(results, thresholds)
        assertEquals(GovernanceAction.FLAG_REVIEW, verdict.action)
        assertNull(verdict.winningVariant)
    }

    @Test
    fun isThresholdExceededHonoursEachOperator() {
        assertTrue(isThresholdExceeded(0.5, ThresholdOperator.GT, 0.3))
        assertTrue(isThresholdExceeded(0.2, ThresholdOperator.LT, 0.3))
        assertTrue(isThresholdExceeded(0.3, ThresholdOperator.GTE, 0.3))
        assertTrue(isThresholdExceeded(0.3, ThresholdOperator.LTE, 0.3))
        assertTrue(isThresholdExceeded(0.3, ThresholdOperator.EQ, 0.3))
        assertTrue(!isThresholdExceeded(0.3, ThresholdOperator.GT, 0.3))
    }
}
