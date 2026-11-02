package ai.factoredui.compose.renderer

import ai.factoredui.compose.schema.GeomapPattern
import ai.factoredui.compose.schema.GeomapPatternKind
import ai.factoredui.compose.score.ScoreShade
import ai.factoredui.compose.score.figureText
import ai.factoredui.compose.score.rankAmong
import ai.factoredui.compose.score.rankByScore
import ai.factoredui.compose.score.scoreBoundsOf
import ai.factoredui.compose.score.scorePartsOf
import ai.factoredui.compose.score.unmeasuredFigures
import kotlin.math.roundToInt

private const val DEFAULT_TOP = 20.0
private const val PERCENT = 100.0

private const val UNSETTLED_HATCH_SPACING = 7f

internal fun unsettledMarkFor(
    shade: ScoreShade,
    values: Map<String, Double?>,
    figures: Map<String, Double?>,
    inkHex: String = "#000000",
): GeomapPattern? {
    if (!shade.isRaw) return null
    val bounds = scoreBoundsOf(shade, values, figures) ?: return null
    if (bounds.isSettled) return null
    return GeomapPattern(GeomapPatternKind.HATCH, shade.unsettledMark ?: inkHex, spacing = UNSETTLED_HATCH_SPACING)
}

private fun TessellatedFeature.geoBounds(): List<Double>? = bounds?.let {
    listOf(worldXToLon(it.minX), worldYToLat(it.maxY), worldXToLon(it.maxX), worldYToLat(it.minY))
}

private fun scoredFeaturesOf(shade: ScoreShade, tessellation: GeomapTessellation): Map<String, TessellatedFeature> =
    tessellation.layers.flatMap { it.features }.filter { shade.hasScoreInput(it.values, it.figures) }.associateBy { it.featureId }

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
    val features = scoredFeaturesOf(shade, tessellation)
    return rankByScore(shade, features.map { it.key to it.value.values }, top, features.mapValues { it.value.figures }).map { thing ->
        mapOf(
            "id" to thing.id,
            "rank" to thing.rank.toDouble(),
            "score" to thing.score,
            "score_low" to thing.low,
            "score_high" to thing.high,
            "settled" to thing.isSettled,
            "score_text" to roundedText(thing.score),
            "percentile" to thing.percentile,
            "percentile_text" to thing.percentile?.let(::percentText),
            "bounds" to features.getValue(thing.id).geoBounds(),
            "info" to features.getValue(thing.id).info,
        )
    }
}

internal fun shadeOutUnmeasuredText(shade: ScoreShade, tessellation: GeomapTessellation): String {
    val missing = scoredFeaturesOf(shade, tessellation).values.flatMap { unmeasuredFigures(shade, it.figures) }
    return missing.groupingBy { it }.eachCount().entries.sortedBy { it.key }
        .joinToString("; ") { (figure, count) -> "$count have no ${shade.figureInfo[figure]?.label ?: figure}" }
}

/** The first selected thing that has values, with each weighted field's value, weight and share of its score; null when none is selected. */
internal fun shadeOutSelected(shade: ScoreShade, tessellation: GeomapTessellation, selection: Set<String>): Map<String, Any?>? {
    val features = scoredFeaturesOf(shade, tessellation)
    val feature = selection.firstNotNullOfOrNull { features[it] } ?: return null
    val bounds = scoreBoundsOf(shade, feature.values, feature.figures)
    val score = bounds?.middle
    val rank = bounds?.let { own -> rankAmong(own, features.values.mapNotNull { scoreBoundsOf(shade, it.values, it.figures) }) }
    return mapOf(
        "id" to feature.featureId,
        "score" to score,
        "score_low" to bounds?.low,
        "score_high" to bounds?.high,
        "settled" to bounds?.isSettled,
        "missing" to shade.weights.filterValues { it > 0.0 }.keys.filter { shade.isRaw && feature.figures[it] == null },
        "score_text" to score?.let(::roundedText),
        "rank" to rank?.toDouble(),
        "percentile" to score?.let { shade.ranks?.percentileOf(it) },
        "bounds" to feature.geoBounds(),
        "info" to feature.info,
        "figures" to shade.figureInfo.filterKeys { it !in shade.fieldFigures.values && !(shade.isRaw && it in shade.weights) }.map { (figure, info) ->
            mapOf("figure" to figure, "label" to info.label, "value" to feature.figures[figure], "text" to figureText(info, feature.figures[figure]))
        },
        "parts" to scorePartsOf(shade, feature.values, feature.figures).map { part ->
            val figure = shade.fieldFigures[part.field] ?: part.field.takeIf { shade.isRaw }
            val info = figure?.let { shade.figureInfo[it] }
            mapOf(
                "field" to part.field,
                "label" to part.label,
                "value" to part.value,
                "value_text" to (part.value?.let(::roundedText) ?: "no figure"),
                "figure_text" to (figure?.let { figureText(shade.figureInfo[it], feature.figures[it]) } ?: ""),
                "good" to shade.scalings[part.field]?.good?.wire,
                "unit" to info?.unit,
                "source" to info?.source,
                "evidence" to info?.evidence,
                "weight" to part.weight,
                "contribution" to part.contribution,
                "contribution_text" to roundedText(part.contribution),
                "share_text" to shareText(part.contribution, score),
            )
        },
    )
}
