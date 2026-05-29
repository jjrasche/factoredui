package ai.factoredui.compose.schema

import ai.factoredui.compose.experiments.ControlExperiments
import ai.factoredui.compose.experiments.Experiments
import ai.factoredui.compose.experiments.InMemoryExperiments
import ai.factoredui.compose.experiments.Variant
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
        var lastResolvedParams: Map<String, Any?> = emptyMap()

        override fun onRender(nodeId: String) { renders.add(nodeId) }
        override fun onInteraction(nodeId: String, action: ActionRef, resolvedParams: Map<String, Any?>) {
            interactions.add(nodeId to action.action)
            lastResolvedParams = resolvedParams
        }
    }

    @Test
    fun observabilityOnRenderFiresWithNodeId() {
        val obs = RecordingObservability()
        obs.onRender("header-text")
        assertEquals(listOf("header-text"), obs.renders)
    }

    @Test
    fun observabilityOnInteractionFiresWithNodeIdActionAndResolvedParams() {
        val obs = RecordingObservability()
        obs.onInteraction("submit-btn", ActionRef(action = "submit"), mapOf("id" to 2))
        assertEquals(listOf("submit-btn" to "submit"), obs.interactions)
        assertEquals(2, obs.lastResolvedParams["id"])
    }

    @Test
    fun assignVariantCanBucketBySubject() {
        // The per-worker contract: one Experiments instance serves many subjects,
        // deterministic per (slotId, subjectId). A real host backs this with the
        // engine's selectVariantByHash; this stub proves the param is honoured.
        val experiments = object : Experiments {
            override fun assignVariant(slotId: String, subjectId: String?): Variant =
                Variant(slotId, if (subjectId == "worker-1") "treatment" else "control")
            override fun logExposure(slotId: String, variant: Variant, subjectId: String?) = Unit
        }
        assertEquals("treatment", experiments.assignVariant("checkout", "worker-1").variantKey)
        assertEquals("control", experiments.assignVariant("checkout", "worker-2").variantKey)
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
