package ai.factoredui.engine.experiments

/**
 * Deterministic traffic bucketing.
 *
 * Ported bit-for-bit from `packages/core/src/experiment/flags.ts`. The hash
 * MUST match the TS implementation exactly: it decides which variant each user
 * lands in, so any divergence re-buckets the entire userbase. BucketingTest
 * asserts against reference values generated from the TS source.
 */

/**
 * DJB2 hash, truncated to unsigned 32 bits each iteration (the TS `>>> 0`).
 * Returns the full hash in `[0, 2^32)` as a [Long]; callers take `% 100`.
 *
 * Iterates UTF-16 code units (`Char.code` == JS `charCodeAt`), so non-ASCII
 * inputs (surrogate pairs) hash identically to the TS version.
 */
fun simpleHash(input: String): Long {
    var hash = 5381L
    for (ch in input) {
        hash = ((hash shl 5) + hash + ch.code.toLong()) and 0xFFFFFFFFL
    }
    return hash
}

/**
 * Pick a variant for [userId] in experiment [experimentId] by hashing
 * `"$userId:$experimentId"` into a 0–99 bucket and walking [variants] in order,
 * accumulating `trafficPercentage`. Returns null only if the buckets don't
 * cover the user's bucket (shouldn't happen when traffic sums to 100).
 *
 * Variant order is significant — the store must return variants in a stable
 * order or assignments shift.
 */
fun selectVariantByHash(
    userId: String,
    experimentId: String,
    variants: List<VariantWithTraffic>,
): VariantWithTraffic? {
    val bucket = (simpleHash("$userId:$experimentId") % 100L).toInt()

    var cumulativeTraffic = 0
    for (variant in variants) {
        cumulativeTraffic += variant.trafficPercentage
        if (bucket < cumulativeTraffic) return variant
    }

    return null
}
