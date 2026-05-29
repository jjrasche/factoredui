package ai.factoredui.engine.experiments

import ai.factoredui.engine.factors.Factor
import ai.factoredui.engine.factors.FactorTier
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TargetingTest {

    private fun factor(name: String, value: Double) =
        Factor("u1", "checkout", name, FactorTier.ALARM, value, "2026-01-01T00:00:00Z")

    private val factors = listOf(factor("error_rate", 0.5), factor("completion", 0.8))

    @Test
    fun emptyRulesAlwaysEligible() {
        assertTrue(evaluateTargeting(factors, emptyList()))
    }

    @Test
    fun factorRuleComparesValue() {
        assertTrue(evaluateTargeting(factors, listOf(FactorRule("error_rate", TargetingOperator.GT, 0.3))))
        assertFalse(evaluateTargeting(factors, listOf(FactorRule("error_rate", TargetingOperator.GT, 0.6))))
        assertTrue(evaluateTargeting(factors, listOf(FactorRule("error_rate", TargetingOperator.EQ, 0.5))))
    }

    @Test
    fun missingFactorFailsRule() {
        assertFalse(evaluateTargeting(factors, listOf(FactorRule("nonexistent", TargetingOperator.GT, 0.0))))
    }

    @Test
    fun allRulesMustPass() {
        val rules = listOf(
            FactorRule("error_rate", TargetingOperator.GT, 0.3),
            FactorRule("completion", TargetingOperator.LT, 0.5),
        )
        assertFalse(evaluateTargeting(factors, rules)) // completion 0.8 is not < 0.5
    }

    @Test
    fun metadataRuleIsCaseInsensitive() {
        val metadata = DeviceMetadata(platform = "iOS", manufacturer = "Samsung", osVersion = "14")
        assertTrue(
            evaluateTargeting(
                emptyList(),
                listOf(MetadataRule(MetadataField.PLATFORM, MetadataOperator.EQ, "ios")),
                metadata,
            ),
        )
        assertTrue(
            evaluateTargeting(
                emptyList(),
                listOf(MetadataRule(MetadataField.MANUFACTURER, MetadataOperator.CONTAINS, "sams")),
                metadata,
            ),
        )
        assertTrue(
            evaluateTargeting(
                emptyList(),
                listOf(MetadataRule(MetadataField.OS_VERSION, MetadataOperator.GTE, "14")),
                metadata,
            ),
        )
    }

    @Test
    fun metadataRuleFailsWithoutMetadata() {
        assertFalse(
            evaluateTargeting(
                emptyList(),
                listOf(MetadataRule(MetadataField.PLATFORM, MetadataOperator.EQ, "ios")),
                deviceMetadata = null,
            ),
        )
    }

    @Test
    fun metadataRuleFailsWhenFieldAbsent() {
        assertFalse(
            evaluateTargeting(
                emptyList(),
                listOf(MetadataRule(MetadataField.MODEL, MetadataOperator.EQ, "pixel")),
                DeviceMetadata(platform = "android"),
            ),
        )
    }
}
