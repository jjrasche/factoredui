package ai.factoredui.application

import kotlinx.serialization.json.JsonObject

internal const val NOMINAL_HEIGHT_MM = 400.0
private const val ON_FOOTPRINT_TOLERANCE_MM = 1.0
private const val ESTIMATE_STYLE_NOTE = "height unknown"

private class Placed(val id: String, val system: String, val xMm: Double, val yMm: Double, val widthMm: Double, val depthMm: Double, val rotationDeg: Double, val level: Int?, val fields: Map<String, Double?>, val isFootprintRect: Boolean)

private class Sized(val heightMm: Double, val isEstimate: Boolean)

private fun isHeightField(name: String) = name.endsWith("height_mm") || name == "above_grade_mm"

/** A footprint that is a full rectangle has one size left to give, its thickness; a line or a point has only its height. */
private fun sizedBy(fields: Map<String, Double?>, isFootprintRect: Boolean): Sized {
    val field = fields.entries.firstOrNull { isHeightField(it.key) || (isFootprintRect && it.key == "thickness_mm") }
    val known = field?.value
    return if (known != null) Sized(known, false) else Sized(NOMINAL_HEIGHT_MM, true)
}

private fun placedObject(item: ExpandedObject) = Placed(
    item.id, item.system, item.xMm + (item.widthMm ?: 0.0) / 2, item.yMm + (item.depthMm ?: 0.0) / 2,
    item.widthMm ?: 0.0, item.depthMm ?: 0.0, 0.0, item.level ?: 0, item.fields, item.widthMm != null && item.depthMm != null,
)

private fun placedInstance(item: ExpandedInstance) = Placed(item.id, item.system, item.xMm, item.yMm, item.lengthMm, 0.0, item.rotationDeg, null, item.fields, false)

private fun baseOfEachLevel(objects: List<ExpandedObject>): Map<Int, Double> {
    val heights = objects.groupBy { it.level ?: 0 }.mapValues { (_, items) -> items.maxOf { sizedBy(it.fields, it.widthMm != null && it.depthMm != null).heightMm } }
    var base = 0.0
    return heights.toSortedMap().mapValues { (_, height) -> base.also { base += height } }
}

private fun isNear(value: Double, target: Double) = kotlin.math.abs(value - target) <= ON_FOOTPRINT_TOLERANCE_MM

private fun isWithin(value: Double, from: Double, to: Double) = value >= from - ON_FOOTPRINT_TOLERANCE_MM && value <= to + ON_FOOTPRINT_TOLERANCE_MM

/** A point lies along a footprint when it is on its line, or on the outline of its rectangle. */
private fun isOnFootprint(item: ExpandedObject, xMm: Double, yMm: Double): Boolean {
    val east = item.xMm + (item.widthMm ?: 0.0)
    val north = item.yMm + (item.depthMm ?: 0.0)
    if (!isWithin(xMm, item.xMm, east) || !isWithin(yMm, item.yMm, north)) return false
    if (item.widthMm == null || item.depthMm == null) return true
    return isNear(xMm, item.xMm) || isNear(xMm, east) || isNear(yMm, item.yMm) || isNear(yMm, north)
}

/** An instance has no level of its own, so it stands on the highest level whose footprint it lies along, else on the ground. */
private fun levelUnder(expansion: Expansion, item: ExpandedInstance): Int? =
    expansion.objects.filter { isOnFootprint(it, item.xMm, item.yMm) }.maxOfOrNull { it.level ?: 0 }

private fun boxNode(placed: Placed, baseMm: Double): Map<String, Any?> {
    val sized = sizedBy(placed.fields, placed.isFootprintRect)
    return mapOf(
        "id" to placed.id, "style" to placed.system, "x_mm" to placed.xMm, "y_mm" to placed.yMm, "z_mm" to baseMm,
        "width_mm" to placed.widthMm, "depth_mm" to placed.depthMm, "height_mm" to sized.heightMm,
        "rotation_deg" to placed.rotationDeg, "estimate" to sized.isEstimate,
    )
}

/** The expansion as stacked boxes: a level stands on the one below, a size the expansion does not give is a hatched stand-in of [NOMINAL_HEIGHT_MM]. */
internal fun volumeNode(file: SystemFile, expansion: Expansion, overlay: Overlay? = null): JsonObject? {
    if (expansion.objects.isEmpty() && expansion.instances.isEmpty()) return null
    val order = systemOrder(file, expansion)
    val bases = baseOfEachLevel(expansion.objects)
    val fromObjects = expansion.objects.map { placedObject(it) to (bases[it.level ?: 0] ?: 0.0) }
    val fromInstances = expansion.instances.map { placedInstance(it) to ((levelUnder(expansion, it)?.let { level -> bases[level] } ?: 0.0) + it.elevationMm) }
    val boxes = (fromObjects + fromInstances).map { (placed, base) -> boxNode(placed, base).let { box -> if (overlay == null) box else box + ("style" to overlayStyleOf(overlay, placed.id)) } }
    val styled = order.filter { system -> boxes.any { it["style"] == system } }
    val hasEstimate = boxes.any { it["estimate"] == true }
    val boxIds = boxes.map { it["id"] as String }
    val keyed = if (overlay == null) styled.map { mapOf("label" to it, "style" to it) } else overlayLegend(overlay, boxIds)
    val legend = keyed + listOfNotNull(if (hasEstimate) mapOf("label" to "$ESTIMATE_STYLE_NOTE, drawn ${formatMm(NOMINAL_HEIGHT_MM)} mm tall", "hatched" to true) else null)
    return specNode(
        "volume", "volume",
        mapOf(
            "boxes" to boxes,
            "styles" to if (overlay == null) styled.associateWith { mapOf("stroke" to colourOfSystem(order, it)) } else overlayStyles(overlay, boxIds) + stopStyles(overlay),
            "legend" to legend,
            "empty_text" to "The expansion has nothing to draw.",
            "selected" to SELECTION_PATH,
            "details" to detailsOf(file, expansion, overlay),
        ),
    )
}
