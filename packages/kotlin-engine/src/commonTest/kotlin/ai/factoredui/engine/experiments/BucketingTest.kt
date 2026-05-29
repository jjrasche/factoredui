package ai.factoredui.engine.experiments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Parity tests for traffic bucketing. The expected hash values were generated
 * from the verbatim TS `simpleHash` (flags.ts). These guard the most
 * port-sensitive line in the system: a wrong hash silently re-buckets every
 * user into a different variant.
 */
class BucketingTest {

    @Test
    fun simpleHashMatchesTsReference() {
        assertEquals(5381L, simpleHash(""))
        assertEquals(177670L, simpleHash("a"))
        assertEquals(3547975910L, simpleHash("control"))
        assertEquals(2311739745L, simpleHash("user-123:exp-abc"))
        assertEquals(167186776L, simpleHash("alice:checkout-experiment-2026"))
    }

    @Test
    fun simpleHashHandlesNonAsciiAsUtf16CodeUnits() {
        // Surrogate-pair input must hash identically to JS charCodeAt iteration.
        assertEquals(2774548315L, simpleHash("🎲non-ascii:exp"))
    }

    @Test
    fun bucketIsHashMod100() {
        assertEquals(10, (simpleHash("control") % 100L).toInt())
        assertEquals(45, (simpleHash("user-123:exp-abc") % 100L).toInt())
    }

    @Test
    fun selectsVariantByCumulativeTraffic() {
        val variants = listOf(
            VariantWithTraffic("control", trafficPercentage = 50),
            VariantWithTraffic("treatment", trafficPercentage = 50),
        )
        // "user-123:exp-abc" → bucket 45 → falls in control's [0,50).
        assertEquals("control", selectVariantByHash("user-123", "exp-abc", variants)?.variantKey)
        // "alice:checkout-experiment-2026" → bucket 76 → falls in treatment's [50,100).
        assertEquals(
            "treatment",
            selectVariantByHash("alice", "checkout-experiment-2026", variants)?.variantKey,
        )
    }

    @Test
    fun returnsNullWhenTrafficDoesNotCoverBucket() {
        val variants = listOf(VariantWithTraffic("control", trafficPercentage = 30))
        // bucket 45 is outside control's [0,30).
        assertNull(selectVariantByHash("user-123", "exp-abc", variants))
    }
}
