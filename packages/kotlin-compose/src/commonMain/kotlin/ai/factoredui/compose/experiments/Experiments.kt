package ai.factoredui.compose.experiments

/**
 * A/B experiment variant assignment.
 *
 * Matches the ExperimentAssignment shape from @factoredui/core types.ts.
 * `config` carries any variant-specific overrides the spec or app can read.
 */
data class Variant(
    val slotId: String,
    val variantKey: String,
    val config: Map<String, Any?> = emptyMap(),
)

/**
 * Experiment slot interface.
 *
 * Implementations connect to a feature-flag service (LaunchDarkly, PostHog,
 * Supabase experiment tables, etc.). The exposure log feeds into the factor
 * engine so the governor can make statistically significant decisions.
 *
 * Matches the evaluateFlag + exposure pattern from @factoredui/core experiment.ts.
 */
interface Experiments {

    /**
     * Return the variant assigned to [slotId] for [subjectId] — the worker /
     * user the UI is being rendered for. Null means "the ambient subject the
     * implementation already knows" (e.g. an instance constructed per-worker).
     *
     * Must be deterministic per (slotId, subjectId): one implementation can
     * serve many subjects without randomising on each call. A host typically
     * backs this with the engine's
     * `selectVariantByHash(subjectId, experimentId, variants)` and maps the
     * resulting variant to a spec it pushes through a `Flow<Spec>`.
     */
    fun assignVariant(slotId: String, subjectId: String? = null): Variant

    /**
     * Record that [subjectId] was exposed to [variant] in slot [slotId]. Call
     * once when the variant's UI becomes visible, not on every render. The
     * subject is what lets exposure attribute to the right worker so the factor
     * engine can compare per-variant outcomes.
     */
    fun logExposure(slotId: String, variant: Variant, subjectId: String? = null)
}

/**
 * Control-only experiments — always returns the "control" variant.
 * Safe default for new integrations and tests.
 */
object ControlExperiments : Experiments {
    override fun assignVariant(slotId: String, subjectId: String?): Variant =
        Variant(slotId = slotId, variantKey = "control")

    override fun logExposure(slotId: String, variant: Variant, subjectId: String?) = Unit
}

/**
 * In-memory experiments for testing — lets tests inject specific variant outcomes.
 *
 * Usage:
 * ```kotlin
 * val experiments = InMemoryExperiments(mapOf("checkout-redesign" to "treatment"))
 * ```
 */
class InMemoryExperiments(
    private val assignments: Map<String, String> = emptyMap(),
    private val configs: Map<String, Map<String, Any?>> = emptyMap(),
) : Experiments {

    private val exposureLog = mutableListOf<Pair<String, Variant>>()

    override fun assignVariant(slotId: String, subjectId: String?): Variant = Variant(
        slotId = slotId,
        variantKey = assignments[slotId] ?: "control",
        config = configs[slotId] ?: emptyMap(),
    )

    override fun logExposure(slotId: String, variant: Variant, subjectId: String?) {
        exposureLog.add(slotId to variant)
    }

    /** Inspect recorded exposures in tests. */
    fun recordedExposures(): List<Pair<String, Variant>> = exposureLog.toList()
}
