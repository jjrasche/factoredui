package ai.factoredui.application

import kotlinx.serialization.json.JsonObject

private const val CUT_STYLE = "cut"
private const val MIN_LABELLED_MM = 1000.0

private class View(val id: String, val title: String, val range: (Extent, GeoPiece) -> Pair<Double, Double>, val nearDistance: (Extent, GeoPiece) -> Double, val span: (Extent) -> Double)

private val VIEWS = listOf(
    View("south", "South elevation", { _, p -> p.xMm to p.xMm + p.dxMm }, { e, p -> p.yMm - e.south }, { it.north - it.south }),
    View("north", "North elevation", { e, p -> (e.west + e.east - p.xMm - p.dxMm) to (e.west + e.east - p.xMm) }, { e, p -> e.north - p.yMm - p.dyMm }, { it.north - it.south }),
    View("east", "East elevation", { _, p -> p.yMm to p.yMm + p.dyMm }, { e, p -> e.east - p.xMm - p.dxMm }, { it.east - it.west }),
    View("west", "West elevation", { e, p -> (e.south + e.north - p.yMm - p.dyMm) to (e.south + e.north - p.yMm) }, { e, p -> p.xMm - e.west }, { it.east - it.west }),
)

internal fun flatDimensions(horizontalLow: Double, horizontalHigh: Double, verticalLow: Double, verticalHigh: Double): List<Map<String, Any?>> {
    val belowY = verticalLow - DIMENSION_OFFSET_MM
    val leftX = horizontalLow - DIMENSION_OFFSET_MM
    fun label(id: String, x: Double, y: Double, text: String, dxDp: Int, dyDp: Int) =
        mapOf("id" to id, "kind" to "label", "style" to "dimension", "x_mm" to x, "y_mm" to y, "text" to text, "align" to "center", "dx_dp" to dxDp, "dy_dp" to dyDp)
    return listOf(
        lineShape("dim:width", "dimension", horizontalLow, belowY, horizontalHigh, belowY),
        lineShape("dim:width/start", "dimension", horizontalLow, belowY - TICK_MM, horizontalLow, belowY + TICK_MM),
        lineShape("dim:width/end", "dimension", horizontalHigh, belowY - TICK_MM, horizontalHigh, belowY + TICK_MM),
        label("dim:width/text", (horizontalLow + horizontalHigh) / 2, belowY, dimensionText(horizontalHigh - horizontalLow), 0, 14),
        lineShape("dim:height", "dimension", leftX, verticalLow, leftX, verticalHigh),
        lineShape("dim:height/start", "dimension", leftX - TICK_MM, verticalLow, leftX + TICK_MM, verticalLow),
        lineShape("dim:height/end", "dimension", leftX - TICK_MM, verticalHigh, leftX + TICK_MM, verticalHigh),
        label("dim:height/text", leftX, (verticalLow + verticalHigh) / 2, "${formatFigure(verticalHigh - verticalLow)} mm", 0, -8),
        label("dim:height/text-imperial", leftX, (verticalLow + verticalHigh) / 2, feetInches(verticalHigh - verticalLow), 0, 8),
    )
}

private fun pieceLabel(piece: GeoPiece, low: Double, high: Double): Map<String, Any?> =
    mapOf("id" to "size:${piece.id}", "kind" to "label", "style" to "dimension", "x_mm" to (low + high) / 2, "y_mm" to piece.zMm + piece.dzMm / 2, "text" to "${formatFigure(high - low)} × ${formatFigure(piece.dzMm)}", "align" to "center", "dx_dp" to 0, "dy_dp" to 0)

private fun groundLine(low: Double, high: Double): Map<String, Any?> = lineShape("ground", "seam", low - DIMENSION_OFFSET_MM / 2, 0.0, high + DIMENSION_OFFSET_MM / 2, 0.0)

private fun flatNode(id: String, title: String, shapes: List<Map<String, Any?>>, geometry: Geometry): JsonObject =
    specNode(
        id, "plan",
        mapOf(
            "ground" to BLUEPRINT_GROUND, "shapes" to shapes, "styles" to blueprintStyles(), "legend" to emptyList<Any>(),
            "empty_text" to "The geometry has nothing to draw.", "selected" to SELECTION_PATH, "details" to detailsOfBlueprint(geometry),
        ),
    ).let { node -> specNode("$id-view", "column", mapOf("gap" to 4), listOf(textNode("$id-title", title, variant = "caption", color = MUTED_INK), node)) }

private fun elevationShapes(view: View, extent: Extent, geometry: Geometry): List<Map<String, Any?>> {
    val shown = geometry.pieces.filter { view.nearDistance(extent, it) < view.span(extent) / 2 }
    val ids = shown.map { it.id }.toSet()
    val bodies = shown.map { piece ->
        val (low, high) = view.range(extent, piece)
        rectShape(piece.id, "piece", low, piece.zMm, high - low, piece.dzMm)
    }
    val labels = shown.filter { it.dzMm >= MIN_LABELLED_MM && view.range(extent, it).let { (low, high) -> high - low >= MIN_LABELLED_MM } }.map { piece -> view.range(extent, piece).let { (low, high) -> pieceLabel(piece, low, high) } }
    val holes = geometry.cuts.filter { it.host in ids }.map { cut ->
        val probe = geometry.pieces.first { it.id == cut.host }
        val (low, high) = view.range(extent, GeoPiece(cut.host, probe.system, cut.region.minMm[0], cut.region.minMm[1], cut.region.minMm[2], cut.region.maxMm[0] - cut.region.minMm[0], cut.region.maxMm[1] - cut.region.minMm[1], cut.region.maxMm[2] - cut.region.minMm[2]))
        rectShape("hole:${cut.host}|${cut.opening}", "hole", low, cut.region.minMm[2], high - low, cut.region.maxMm[2] - cut.region.minMm[2])
    }
    val (low, high) = view.range(extent, GeoPiece("extent", "", extent.west, extent.south, extent.floor, extent.east - extent.west, extent.north - extent.south, extent.ceiling - extent.floor))
    return bodies + holes + labels + groundLine(low, high) + flatDimensions(low, high, extent.floor, extent.ceiling)
}

/** One elevation per side of the building: the pieces nearest the viewer, the holes cut in them, and the dimensions of the whole. */
internal fun blueprintElevationNodes(geometry: Geometry): List<JsonObject> {
    val extent = extentOf(geometry.pieces) ?: return emptyList()
    return VIEWS.map { view -> flatNode("blueprint-${view.id}", view.title, elevationShapes(view, extent, geometry), geometry) }
}

private fun cutsPlane(piece: GeoPiece, southMm: Double) = piece.yMm <= southMm && southMm <= piece.yMm + piece.dyMm

/** The building cut by a vertical plane through the middle of its depth, looking north: what the plane passes through is drawn heavy. */
internal fun blueprintSectionNode(geometry: Geometry): JsonObject? {
    val extent = extentOf(geometry.pieces) ?: return null
    val planeMm = (extent.south + extent.north) / 2
    val passed = geometry.pieces.filter { cutsPlane(it, planeMm) }
    val bodies = passed.map { rectShape(it.id, CUT_STYLE, it.xMm, it.zMm, it.dxMm, it.dzMm) }
    val holes = geometry.cuts.filter { it.region.minMm[1] <= planeMm && planeMm <= it.region.maxMm[1] }.map {
        rectShape("hole:${it.host}|${it.opening}", "hole", it.region.minMm[0], it.region.minMm[2], it.region.maxMm[0] - it.region.minMm[0], it.region.maxMm[2] - it.region.minMm[2])
    }
    val shapes = bodies + holes + groundLine(extent.west, extent.east) + flatDimensions(extent.west, extent.east, extent.floor, extent.ceiling)
    return flatNode("blueprint-section", "Section through the middle of the depth (${dimensionText(planeMm)} from the south edge), looking north", shapes, geometry)
}

private fun planView(geometry: Geometry, expansion: Expansion): JsonObject =
    specNode("blueprint-plan-view", "column", mapOf("gap" to 4), listOf(textNode("blueprint-plan-title", "Plan", variant = "caption", color = MUTED_INK), blueprintPlanNode(geometry, expansion)))

/** Every flat drawing of the building: the plan, the four elevations and the section. */
internal fun blueprintDrawingsNode(geometry: Geometry, expansion: Expansion): JsonObject =
    specNode(
        "drawings", "column", mapOf("gap" to 12),
        listOf(textNode("drawings-title", "Drawings", variant = "subheading", bold = true), planView(geometry, expansion)) + blueprintElevationNodes(geometry) + listOfNotNull(blueprintSectionNode(geometry)),
    )
