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
     * Return the variant assigned to [slotId] for the current user/session.
     * Must be deterministic within a session — do not randomise on each call.
     */
    fun assignVariant(slotId: String): Variant

    /**
     * Record that the user was exposed to [variant] in slot [slotId].
     * Call this once when the variant's UI becomes visible, not on every render.
     */
    fun logExposure(slotId: String, variant: Variant)
}

/**
 * Control-only experiments — always returns the "control" variant.
 * Safe default for new integrations and tests.
 */
object ControlExperiments : Experiments {
    override fun assignVariant(slotId: String): Variant =
        Variant(slotId = slotId, variantKey = "control")

    override fun logExposure(slotId: String, variant: Variant) = Unit
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

    override fun assignVariant(slotId: String): Variant = Variant(
        slotId = slotId,
        variantKey = assignments[slotId] ?: "control",
        config = configs[slotId] ?: emptyMap(),
    )

    override fun logExposure(slotId: String, variant: Variant) {
        exposureLog.add(slotId to variant)
    }

    /** Inspect recorded exposures in tests. */
    fun recordedExposures(): List<Pair<String, Variant>> = exposureLog.toList()
}
