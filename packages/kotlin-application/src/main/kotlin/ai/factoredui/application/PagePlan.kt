package ai.factoredui.application

import kotlinx.serialization.json.JsonObject
import kotlin.math.cos
import kotlin.math.sin

private val SYSTEM_COLOURS = listOf("#3b7dd8", "#c07a1f", "#2e8b57", "#a23b8c", "#c0392b", "#2a9d9d", "#7a6a2f", "#6b5bd2", "#6d4c41", "#d81b60")
private const val FILL_ALPHA = "40"
private const val LABEL_INSET_DP = 6
private const val LABEL_ROW_DP = 14
private const val LABEL_FIRST_ROW_DP = 12
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

private fun isRect(item: ExpandedObject) = item.widthMm != null && item.depthMm != null

/** A system whose footprints sit on a higher level than another footprint is seen over it, so its outline is dashed. */
private fun overheadSystems(expansion: Expansion): Set<String> {
    val rects = expansion.objects.filter(::isRect)
    val lowest = rects.minOfOrNull { it.level ?: 0 } ?: return emptySet()
    return rects.filter { (it.level ?: 0) > lowest }.map { it.system }.toSet()
}

private fun styleOf(colour: String, isOverhead: Boolean): Map<String, Any?> =
    mapOf("stroke" to colour, "fill" to colour + FILL_ALPHA, "dash" to isOverhead)

private fun shape(id: String, kind: String, style: String?, vararg fields: Pair<String, Any?>): Map<String, Any?> =
    mapOf("id" to id, "kind" to kind, "style" to style) + fields

private fun rectShape(item: ExpandedObject) =
    shape(item.id, "rect", item.system, "x_mm" to item.xMm, "y_mm" to item.yMm, "width_mm" to item.widthMm, "depth_mm" to item.depthMm)

private fun lineShape(item: ExpandedObject): Map<String, Any?> {
    val reachesEast = item.widthMm != null
    val toX = if (reachesEast) item.xMm + item.widthMm!! else item.xMm
    val toY = if (reachesEast) item.yMm else item.yMm + item.depthMm!!
    return shape(item.id, "line", item.system, "x_mm" to item.xMm, "y_mm" to item.yMm, "x2_mm" to toX, "y2_mm" to toY)
}

private fun markerShape(item: ExpandedObject) = shape(item.id, "marker", item.system, "x_mm" to item.xMm, "y_mm" to item.yMm)

private fun instanceShape(item: ExpandedInstance): Map<String, Any?> {
    val half = item.lengthMm / 2
    val turn = Math.toRadians(item.rotationDeg)
    val reachX = half * cos(turn)
    val reachY = half * sin(turn)
    return shape(
        item.id, "line", item.system,
        "x_mm" to item.xMm - reachX, "y_mm" to item.yMm - reachY, "x2_mm" to item.xMm + reachX, "y2_mm" to item.yMm + reachY,
    )
}

private fun rectLabels(rects: List<ExpandedObject>): List<Map<String, Any?>> {
    val seenAt = mutableMapOf<Pair<Double, Double>, Int>()
    return rects.map { item ->
        val corner = item.xMm to item.yMm + item.depthMm!!
        val row = seenAt.merge(corner, 1, Int::plus)!! - 1
        shape(
            "label:${item.id}", "label", null,
            "x_mm" to corner.first, "y_mm" to corner.second,
            "text" to "${item.system} ${formatMm(item.widthMm!!)} × ${formatMm(item.depthMm)} mm",
            "align" to "start", "dx_dp" to LABEL_INSET_DP, "dy_dp" to LABEL_FIRST_ROW_DP + LABEL_ROW_DP * row,
        )
    }
}

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

internal fun planNode(file: SystemFile, expansion: Expansion): JsonObject {
    val order = systemOrder(file, expansion)
    val overhead = overheadSystems(expansion)
    val rects = expansion.objects.filter(::isRect).sortedByDescending { it.level ?: 0 }
    val lines = expansion.objects.filter { !isRect(it) && (it.widthMm != null || it.depthMm != null) }
    val markers = expansion.objects.filter { it.widthMm == null && it.depthMm == null }
    val drawn = rects.map(::rectShape) + lines.map(::lineShape) + markers.map(::markerShape) + expansion.instances.map(::instanceShape)
    val shapes = drawn + rectLabels(rects) + northArrow(expansion.extentMm)
    val styled = order.filter { system -> drawn.any { it["style"] == system } }
    return specNode(
        "plan", "plan",
        mapOf(
            "shapes" to shapes,
            "styles" to styled.associateWith { styleOf(colourOfSystem(order, it), it in overhead) } + (NORTH_STYLE to mapOf("stroke" to NORTH_INK, "weight" to 2)),
            "legend" to styled.map { mapOf("label" to it, "style" to it) },
            "empty_text" to "The expansion has nothing to draw.",
        ),
    )
}
