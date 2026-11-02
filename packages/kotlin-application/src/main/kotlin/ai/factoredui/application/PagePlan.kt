package ai.factoredui.application

import kotlinx.serialization.json.JsonObject

private val SYSTEM_COLOURS = listOf("#3b7dd8", "#c07a1f", "#2e8b57", "#a23b8c", "#c0392b", "#2a9d9d", "#7a6a2f", "#6b5bd2", "#6d4c41", "#d81b60")
private const val FILL_ALPHA = "40"
private const val LABEL_FIRST_ROW_DP = 12
internal const val SELECTION_PATH = "{selection.ids}"
private const val NORTH_STYLE = "north arrow"
private const val NORTH_INK = "#1a1a1a"
private const val ARROW_INSET = 0.06
private const val ARROW_BASE = 0.14
private const val ARROW_TIP = 0.04
private const val ARROW_HEAD_DROP = 0.03
private const val ARROW_HEAD_SPREAD = 0.025

/** Systems that are drawn come first, so each takes its own colour before any system that draws nothing. */
internal fun systemOrder(file: SystemFile, expansion: Expansion): List<String> {
    val named = file.systems.map { it.name }
    val drawn = (expansion.objects.map { it.system } + expansion.instances.map { it.system }).distinct()
    val drawnInOrder = (named + drawn).distinct().filter { it in drawn }
    return drawnInOrder + named.filterNot { it in drawn }
}

internal fun colourOfSystem(order: List<String>, system: String): String = SYSTEM_COLOURS[order.indexOf(system) % SYSTEM_COLOURS.size]

private fun styleOf(colour: String, isOverhead: Boolean): Map<String, Any?> =
    mapOf("stroke" to colour, "fill" to colour + FILL_ALPHA, "dash" to isOverhead)

private fun shape(id: String, kind: String, style: String?, vararg fields: Pair<String, Any?>): Map<String, Any?> =
    mapOf("id" to id, "kind" to kind, "style" to style) + fields

private fun northArrow(extent: Pair<Double, Double>?): List<Map<String, Any?>> {
    val (east, north) = extent ?: return emptyList()
    val x = east - east * ARROW_INSET
    val base = north - north * ARROW_BASE
    val tip = north - north * ARROW_TIP
    val headDrop = north * ARROW_HEAD_DROP
    val headSpread = east * ARROW_HEAD_SPREAD
    fun stroke(id: String, fromX: Double, fromY: Double, toX: Double, toY: Double) =
        shape("arrow:$id", "line", NORTH_STYLE, "x_mm" to fromX, "y_mm" to fromY, "x2_mm" to toX, "y2_mm" to toY)
    return listOf(
        stroke("shaft", x, base, x, tip),
        stroke("west", x, tip, x - headSpread, tip - headDrop),
        stroke("east", x, tip, x + headSpread, tip - headDrop),
        shape("arrow:label", "label", null, "x_mm" to x, "y_mm" to base, "text" to "N", "dy_dp" to LABEL_FIRST_ROW_DP),
    )
}

private fun covers(upper: GeoPiece, lower: GeoPiece) =
    upper.zMm >= lower.zMm + lower.dzMm && upper.xMm <= lower.xMm && upper.yMm <= lower.yMm &&
        upper.xMm + upper.dxMm >= lower.xMm + lower.dxMm && upper.yMm + upper.dyMm >= lower.yMm + lower.dyMm

/** A style whose pieces sit over another piece's footprint is seen over it, so its outline is dashed. */
private fun overheadStyles(pieces: List<GeoPiece>): Set<String> =
    pieces.filter { upper -> pieces.any { lower -> lower !== upper && covers(upper, lower) } }.map { it.material ?: it.system }.toSet()

/** The footprint of every piece the engine measured, the lowest first so what stands over it is drawn on top. */
internal fun planNode(file: SystemFile, expansion: Expansion, geometry: Geometry, overlay: Overlay? = null): JsonObject {
    val order = systemOrder(file, expansion)
    val extent = extentOf(geometry.pieces)
    val overhead = overheadStyles(geometry.pieces)
    val drawn = geometry.pieces.sortedBy { it.zMm }.map { piece -> rectShape(piece.id, piece.material ?: piece.system, piece.xMm, piece.yMm, piece.dxMm, piece.dyMm) }
    val painted = if (overlay == null) drawn else drawn.map { it + ("style" to overlayStyleOf(overlay, it["id"] as String)) }
    val shapes = painted + northArrow(extent?.let { it.east to it.north })
    val styleNames = drawn.map { it["style"] as String }.toSet()
    val palette = (order + styleNames).distinct()
    val styled = palette.filter { it in styleNames }
    val paintedIds = drawn.map { it["id"] as String }
    val systemStyles = styled.associateWith { styleOf(colourOfSystem(palette, it), it in overhead) }
    val systemLegend = styled.map { mapOf("label" to it, "style" to it) }
    return specNode(
        "plan", "plan",
        mapOf(
            "shapes" to shapes,
            "styles" to (if (overlay == null) systemStyles else overlayStyles(overlay, paintedIds) + stopStyles(overlay)) + (NORTH_STYLE to mapOf("stroke" to NORTH_INK, "weight" to 2)),
            "legend" to (if (overlay == null) systemLegend else overlayLegend(overlay, paintedIds)),
            "empty_text" to "The geometry has nothing to draw.",
            "selected" to SELECTION_PATH,
            "details" to detailsOf(file, expansion, overlay),
        ),
    )
}
