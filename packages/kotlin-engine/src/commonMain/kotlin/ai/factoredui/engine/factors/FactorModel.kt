package ai.factoredui.engine.factors

/**
 * Factor domain types — the output contracts of the factor engine.
 *
 * Ported from the shapes in `packages/core/src/types.ts` + the `factors`
 * modules. The TypeScript prototype delegated all persistence to a
 * `FactoredStore` interface whose SQL implementation was deleted with
 * `adapter-supabase` (2026-04-24). These types are the surviving contract: the
 * fresh Postgres SQL (kotlin-server) produces rows shaped like these.
 *
 * Pure and multiplatform — no Postgres, no Compose. Timestamps are ISO-8601
 * strings (matching the TS `string` fields); the SQL layer converts to/from
 * `TIMESTAMPTZ`.
 */

/**
 * Three-tier factor classification. A pure data discriminator carried on every
 * factor row — no per-tier branch exists in the code, the tier only labels what
 * a factor measures.
 *
 * - [ALARM] — "something is wrong": completion rate, drop-off, error rate.
 * - [DIAGNOSTIC] — "what the user experienced": hesitation, rage/dead clicks.
 * - [STRUCTURAL] — "what the page is": Lighthouse/CLS/LCP, from offline runs.
 *
 * Wire form is lowercase (`alarm`/`diagnostic`/`structural`), matching the
 * `enum.name.lowercase()` convention used by ingest + the SQL views.
 */
enum class FactorTier {
    ALARM,
    DIAGNOSTIC,
    STRUCTURAL;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String): FactorTier = valueOf(value.uppercase())
    }
}

/** A single computed factor value for one user on one component. */
data class Factor(
    val userId: String,
    val componentPath: String,
    val factorName: String,
    val factorTier: FactorTier,
    val value: Double,
    val computedAt: String,
)

/**
 * Cross-user rollup of one factor on one component — the row the factor
 * dashboard binds to. `stddevValue` is nullable: Postgres `stddev` returns
 * null for a single sample, and that distinction is meaningful (not zero).
 */
data class ComponentFactorAggregate(
    val componentPath: String,
    val factorName: String,
    val factorTier: FactorTier,
    val userCount: Int,
    val avgValue: Double,
    val medianValue: Double,
    val p95Value: Double,
    val minValue: Double,
    val maxValue: Double,
    val stddevValue: Double?,
)

/** A point in a factor's time series. */
data class FactorSnapshot(
    val factorName: String,
    val factorTier: String,
    val value: Double,
    val snapshotAt: String,
)

/** Change in a factor between two points in time. */
data class FactorDelta(
    val factorName: String,
    val before: Double,
    val after: Double,
    val delta: Double,
)

/** A user's behavioural-cluster assignment. */
data class UserCluster(
    val userId: String,
    val clusterId: Int,
    val assignedAt: String,
)
