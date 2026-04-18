package ai.factoredui.compose.schema

import ai.factoredui.compose.experiments.ControlExperiments
import ai.factoredui.compose.experiments.InMemoryExperiments
import ai.factoredui.compose.observability.Observability
import ai.factoredui.compose.schema.ActionRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests that observability hooks fire at the right moments and that the
 * InMemoryExperiments implementation records exposure correctly.
 */
class ObservabilityTest {

    /** Capture-all observability for assertions. */
    private class RecordingObservability : Observability {
        val renders = mutableListOf<String>()
        val interactions = mutableListOf<Pair<String, String>>()

        override fun onRender(nodeId: String) { renders.add(nodeId) }
        override fun onInteraction(nodeId: String, action: ActionRef) {
            interactions.add(nodeId to action.action)
        }
    }

    @Test
    fun observabilityOnRenderFiresWithNodeId() {
        val obs = RecordingObservability()
        obs.onRender("header-text")
        assertEquals(listOf("header-text"), obs.renders)
    }

    @Test
    fun observabilityOnInteractionFiresWithNodeIdAndAction() {
        val obs = RecordingObservability()
        obs.onInteraction("submit-btn", ActionRef(action = "submit"))
        assertEquals(listOf("submit-btn" to "submit"), obs.interactions)
    }

    @Test
    fun controlExperimentsAlwaysReturnsControlVariant() {
        val variant = ControlExperiments.assignVariant("checkout-redesign")
        assertEquals("control", variant.variantKey)
        assertEquals("checkout-redesign", variant.slotId)
    }

    @Test
    fun inMemoryExperimentsReturnsConfiguredVariant() {
        val experiments = InMemoryExperiments(
            assignments = mapOf("checkout-redesign" to "treatment"),
        )
        val variant = experiments.assignVariant("checkout-redesign")
        assertEquals("treatment", variant.variantKey)
    }

    @Test
    fun inMemoryExperimentsDefaultsToControlForUnknownSlot() {
        val experiments = InMemoryExperiments()
        val variant = experiments.assignVariant("unknown-slot")
        assertEquals("control", variant.variantKey)
    }

    @Test
    fun inMemoryExperimentsRecordsExposureLog() {
        val experiments = InMemoryExperiments(
            assignments = mapOf("hero-banner" to "treatment"),
        )
        val variant = experiments.assignVariant("hero-banner")
        experiments.logExposure("hero-banner", variant)

        val log = experiments.recordedExposures()
        assertEquals(1, log.size)
        assertEquals("hero-banner", log[0].first)
        assertEquals("treatment", log[0].second.variantKey)
    }
}
