package ai.factoredui.compose.renderer

import ai.factoredui.compose.score.ScoreShade
import ai.factoredui.compose.score.rankByScore
import ai.factoredui.compose.score.scoreOf
import ai.factoredui.compose.score.scoreParts
import kotlin.math.roundToInt

private const val DEFAULT_TOP = 20.0
private const val PERCENT = 100.0

private fun TessellatedFeature.geoBounds(): List<Double>? = bounds?.let {
    listOf(worldXToLon(it.minX), worldYToLat(it.maxY), worldXToLon(it.maxX), worldYToLat(it.minY))
}

private fun scoredFeaturesOf(tessellation: GeomapTessellation): Map<String, TessellatedFeature> =
    tessellation.layers.flatMap { it.features }.filter { it.values.isNotEmpty() }.associateBy { it.featureId }

private fun roundedText(figure: Double): String = ((figure * PERCENT).roundToInt() / PERCENT).toString()

private const val TENTH = 10.0

/** A part's share of the whole score as a percent with one decimal, so a small share still reads as one. */
private fun shareText(contribution: Double, score: Double?): String {
    if (score == null || score == 0.0) return "0.0%"
    return "${((contribution / score) * PERCENT * TENTH).roundToInt() / TENTH}%"
}

private fun percentText(percentile: Double): String = (percentile * PERCENT).roundToInt().toString()

internal fun shadeOutTopCount(raw: Any?): Int = ((raw as? Number)?.toDouble() ?: DEFAULT_TOP).toInt().coerceAtLeast(0)

/** The best things under the live score, best first, as rows a spec's list can bind: rank, score, text for both, and the lon/lat box to frame it. */
internal fun shadeOutTop(shade: ScoreShade, tessellation: GeomapTessellation, top: Int): List<Map<String, Any?>> {
    val features = scoredFeaturesOf(tessellation)
    return rankByScore(shade, features.map { it.key to it.value.values }, top).map { thing ->
        mapOf(
            "id" to thing.id,
            "rank" to thing.rank.toDouble(),
            "score" to thing.score,
            "score_text" to roundedText(thing.score),
            "percentile" to thing.percentile,
            "percentile_text" to thing.percentile?.let(::percentText),
            "bounds" to features.getValue(thing.id).geoBounds(),
            "info" to features.getValue(thing.id).info,
        )
    }
}

/** The first selected thing that has values, with each weighted field's value, weight and share of its score; null when none is selected. */
internal fun shadeOutSelected(shade: ScoreShade, tessellation: GeomapTessellation, selection: Set<String>): Map<String, Any?>? {
    val features = scoredFeaturesOf(tessellation)
    val feature = selection.firstNotNullOfOrNull { features[it] } ?: return null
    val score = scoreOf(shade, feature.values)
    val rank = score?.let { own -> features.values.count { other -> (scoreOf(shade, other.values) ?: Double.NEGATIVE_INFINITY) > own } + 1 }
    return mapOf(
        "id" to feature.featureId,
        "score" to score,
        "score_text" to score?.let(::roundedText),
        "rank" to rank?.toDouble(),
        "percentile" to score?.let { shade.ranks?.percentileOf(it) },
        "bounds" to feature.geoBounds(),
        "info" to feature.info,
        "parts" to scoreParts(shade, feature.values).map { part ->
            mapOf(
                "field" to part.field,
                "label" to part.label,
                "value" to part.value,
                "value_text" to (part.value?.let(::roundedText) ?: "no figure"),
                "weight" to part.weight,
                "contribution" to part.contribution,
                "contribution_text" to roundedText(part.contribution),
                "share_text" to shareText(part.contribution, score),
            )
        },
    )
}
