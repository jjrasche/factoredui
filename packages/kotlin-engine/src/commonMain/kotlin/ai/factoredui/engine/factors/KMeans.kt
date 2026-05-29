package ai.factoredui.engine.factors

import kotlin.math.floor
import kotlin.random.Random

/**
 * K-means clustering with k-means++ initialization.
 *
 * Pure, deterministic given a seed. Ported bit-for-bit from
 * `packages/core/src/factors/clustering.ts` — including the Mulberry32 PRNG —
 * so a seed produces identical labels/centroids across the TS and Kotlin
 * implementations (KMeansTest asserts against reference vectors generated from
 * the TS source). The integer/shift ops are 32-bit-defined on every Kotlin
 * target, so the reference vectors hold on JVM, native, and wasm alike.
 */

private const val MAX_ITERATIONS = 50
private const val CONVERGENCE_THRESHOLD = 1e-6

data class KMeansResult(
    val labels: List<Int>,
    val centroids: List<List<Double>>,
    val iterations: Int,
)

/**
 * Cluster [points] into [k] groups. [seed] makes the run reproducible; when
 * absent a random seed is used (the TS used `Date.now()` — `Random.nextInt()`
 * serves the same "arbitrary, non-deterministic" purpose and is multiplatform).
 */
fun kMeans(points: List<List<Double>>, k: Int, seed: Int? = null): KMeansResult {
    val pts = points.map { it.toDoubleArray() }
    val rng = createSeededRng(seed ?: Random.nextInt())
    val centroids = initializeCentroids(pts, k, rng)
    val labels = IntArray(pts.size)

    var iteration = 0
    while (iteration < MAX_ITERATIONS) {
        assignPointsToCentroids(pts, centroids, labels)
        val maxShift = updateCentroids(pts, centroids, labels, k)
        iteration++
        if (maxShift < CONVERGENCE_THRESHOLD) break
    }

    return KMeansResult(
        labels = labels.toList(),
        centroids = centroids.map { it.toList() },
        iterations = iteration,
    )
}

private fun initializeCentroids(
    points: List<DoubleArray>,
    k: Int,
    rng: () -> Double,
): MutableList<DoubleArray> {
    val centroids = mutableListOf<DoubleArray>()
    val firstIndex = floor(rng() * points.size).toInt()
    centroids.add(points[firstIndex].copyOf())

    for (c in 1 until k) {
        val distances = points.map { nearestCentroidDistance(it, centroids) }
        val totalDistance = distances.sum()
        val threshold = rng() * totalDistance

        var cumulative = 0.0
        for (i in points.indices) {
            cumulative += distances[i]
            if (cumulative >= threshold) {
                centroids.add(points[i].copyOf())
                break
            }
        }
    }

    return centroids
}

private fun nearestCentroidDistance(point: DoubleArray, centroids: List<DoubleArray>): Double {
    var minDist = Double.POSITIVE_INFINITY
    for (centroid in centroids) {
        val dist = squaredEuclidean(point, centroid)
        if (dist < minDist) minDist = dist
    }
    return minDist
}

private fun assignPointsToCentroids(
    points: List<DoubleArray>,
    centroids: List<DoubleArray>,
    labels: IntArray,
) {
    for (i in points.indices) {
        var minDist = Double.POSITIVE_INFINITY
        var bestCluster = 0
        for (c in centroids.indices) {
            val dist = squaredEuclidean(points[i], centroids[c])
            if (dist < minDist) {
                minDist = dist
                bestCluster = c
            }
        }
        labels[i] = bestCluster
    }
}

private fun updateCentroids(
    points: List<DoubleArray>,
    centroids: MutableList<DoubleArray>,
    labels: IntArray,
    k: Int,
): Double {
    val dimensions = points[0].size
    var maxShift = 0.0

    for (c in 0 until k) {
        val newCentroid = DoubleArray(dimensions)
        var count = 0
        for (i in points.indices) {
            if (labels[i] != c) continue
            count++
            for (d in 0 until dimensions) newCentroid[d] += points[i][d]
        }
        if (count == 0) continue
        for (d in 0 until dimensions) newCentroid[d] /= count

        val shift = squaredEuclidean(centroids[c], newCentroid)
        if (shift > maxShift) maxShift = shift
        centroids[c] = newCentroid
    }

    return maxShift
}

private fun squaredEuclidean(a: DoubleArray, b: DoubleArray): Double {
    var sum = 0.0
    for (d in a.indices) {
        val diff = a[d] - b[d]
        sum += diff * diff
    }
    return sum
}

/**
 * Mulberry32 PRNG. Kotlin `Int` arithmetic wraps to 32-bit two's complement,
 * which reproduces JS `| 0` and `Math.imul`; `ushr` reproduces `>>>`; masking
 * to `0xFFFFFFFF` reproduces the final `>>> 0` before the divide.
 */
private fun createSeededRng(seed: Int): () -> Double {
    var state = seed
    return {
        state += 0x6d2b79f5
        var t = (state xor (state ushr 15)) * (1 or state)
        t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
        val unsigned = (t xor (t ushr 14)).toLong() and 0xFFFFFFFFL
        unsigned.toDouble() / 4294967296.0
    }
}
