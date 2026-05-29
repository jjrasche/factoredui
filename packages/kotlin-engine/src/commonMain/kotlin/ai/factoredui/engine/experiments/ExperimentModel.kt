package ai.factoredui.engine.experiments

import ai.factoredui.engine.factors.FactorDelta

/**
 * Experiment domain types.
 *
 * Ported from the `packages/core/src/experiment` modules + `types.ts`. The TS
 * prototype delegated persistence to a `FactoredStore` interface (SQL deleted
 * with `adapter-supabase`, 2026-04-24); these types are the surviving contract.
 * Pure and multiplatform. Enums use lowercase wire forms (`enum.name.lowercase()`).
 */

/** Platform a session/experiment targets. */
enum class Platform {
    WEB,
    IOS,
    ANDROID;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String): Platform = valueOf(value.uppercase())
    }
}

// --- Targeting ---

enum class TargetingOperator { GT, GTE, LT, LTE, EQ }

enum class MetadataOperator { EQ, NEQ, CONTAINS, GTE, LTE }

enum class MetadataField {
    OS_NAME,
    OS_VERSION,
    MANUFACTURER,
    MODEL,
    APP_VERSION,
    APP_BUILD,
    PLATFORM,
}

/**
 * A single targeting predicate. The TS union had a third "legacy" factor shape
 * distinguished only by a missing `type` discriminator; it is behaviourally
 * identical to [FactorRule], so it collapses into it here (the legacy/typed
 * distinction is a JSON-parsing concern handled at the SQL layer).
 */
sealed interface TargetingRule

data class FactorRule(
    val factor: String,
    val operator: TargetingOperator,
    val threshold: Double,
) : TargetingRule

data class MetadataRule(
    val field: MetadataField,
    val operator: MetadataOperator,
    val value: String,
) : TargetingRule

/** Device facts a metadata rule can match against. All fields optional. */
data class DeviceMetadata(
    val osName: String? = null,
    val osVersion: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val appVersion: String? = null,
    val appBuild: String? = null,
    val platform: String? = null,
) {
    fun field(field: MetadataField): String? = when (field) {
        MetadataField.OS_NAME -> osName
        MetadataField.OS_VERSION -> osVersion
        MetadataField.MANUFACTURER -> manufacturer
        MetadataField.MODEL -> model
        MetadataField.APP_VERSION -> appVersion
        MetadataField.APP_BUILD -> appBuild
        MetadataField.PLATFORM -> platform
    }
}

// --- Variants & assignment ---

/** A variant as stored, with its traffic share (used for hash bucketing). */
data class VariantWithTraffic(
    val variantKey: String,
    val trafficPercentage: Int,
    val config: Map<String, Any?> = emptyMap(),
)

/** The result of evaluating a flag for a user. */
data class ExperimentAssignment(
    val experimentId: String,
    val variantKey: String,
    val config: Map<String, Any?>,
)

// --- Lifecycle / authoring ---

data class VariantDefinition(
    val variantKey: String,
    val config: Map<String, Any?> = emptyMap(),
    val trafficPercentage: Int,
)

data class ExperimentDefinition(
    val name: String,
    val description: String? = null,
    val componentPath: String,
    val variants: List<VariantDefinition>,
    val targetingRules: List<TargetingRule> = emptyList(),
    val platforms: List<Platform> = emptyList(),
)

data class CreatedExperiment(
    val id: String,
    val name: String,
    val status: String,
    val componentPath: String,
)

// --- Governance ---

enum class ThresholdOperator { GT, LT, GTE, LTE, EQ }

enum class ThresholdAction { ALERT, EXPERIMENT }

data class Threshold(
    val id: String,
    val factorName: String,
    val componentPath: String?,
    val operator: ThresholdOperator,
    val value: Double,
    val action: ThresholdAction,
)

data class FactorVerdict(
    val factorName: String,
    val bestVariant: String,
    val bestDelta: Double,
    val controlDelta: Double,
    val isSignificant: Boolean,
)

enum class GovernanceAction { CONCLUDE, FLAG_REVIEW, CONTINUE }

data class GovernanceVerdict(
    val action: GovernanceAction,
    val winningVariant: String?,
    val factorVerdicts: List<FactorVerdict>,
)

/** Per-variant factor deltas for a running experiment. */
data class VariantResult(
    val variantKey: String,
    val userCount: Int,
    val factorDeltas: List<FactorDelta>,
)
