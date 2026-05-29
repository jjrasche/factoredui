package ai.factoredui.engine.experiments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Validation contract is the sealed [ExperimentValidationError] type, not the
 * exception message — so these assert on the structured error a cross-repo
 * consumer would match on.
 */
class LifecycleTest {

    private fun variant(key: String, traffic: Int) = VariantDefinition(key, trafficPercentage = traffic)

    private fun definition(vararg variants: VariantDefinition) =
        ExperimentDefinition(name = "exp", componentPath = "checkout", variants = variants.toList())

    @Test
    fun reportsTooFewVariants() {
        val def = definition(variant("control", 100))
        assertEquals(ExperimentValidationError.TooFewVariants, findValidationError(def))
        val ex = assertFailsWith<ExperimentValidationException> { validateDefinition(def) }
        assertEquals(ExperimentValidationError.TooFewVariants, ex.error)
    }

    @Test
    fun reportsMissingControl() {
        val def = definition(variant("a", 50), variant("b", 50))
        assertEquals(ExperimentValidationError.MissingControl, findValidationError(def))
    }

    @Test
    fun reportsTrafficNotHundredWithActual() {
        val def = definition(variant("control", 40), variant("treatment", 50))
        assertEquals(ExperimentValidationError.TrafficNotHundred(90), findValidationError(def))
    }

    @Test
    fun acceptsValidDefinition() {
        val def = definition(variant("control", 50), variant("treatment", 50))
        assertNull(findValidationError(def))
        validateDefinition(def) // should not throw
    }
}
