package ai.factoredui.application

import kotlinx.serialization.json.JsonObject

private const val CUT_STYLE = "cut"
private const val OVERALL_OFFSET_MM = 900.0
private const val MIN_LABELLED_MM = 1000.0

private class View(val id: String, val title: String, val range: (Extent, GeoPiece) -> Pair<Double, Double>, val nearDistance: (Extent, GeoPiece) -> Double, val span: (Extent) -> Double)

private val VIEWS = listOf(
    View("south", "South elevation", { _, p -> p.xMm to p.xMm + p.dxMm }, { e, p -> p.yMm - e.south }, { it.north - it.south }),
    View("north", "North elevation", { e, p -> (e.west + e.east - p.xMm - p.dxMm) to (e.west + e.east - p.xMm) }, { e, p -> e.north - p.yMm - p.dyMm }, { it.north - it.south }),
    View("east", "East elevation", { _, p -> p.yMm to p.yMm + p.dyMm }, { e, p -> e.east - p.xMm - p.dxMm }, { it.east - it.west }),
    View("west", "West elevation", { e, p -> (e.south + e.north - p.yMm - p.dyMm) to (e.south + e.north - p.yMm) }, { e, p -> p.xMm - e.west }, { it.east - it.west }),
)

internal fun flatDimensions(horizontalLow: Double, horizontalHigh: Double, verticalLow: Double, verticalHigh: Double): List<Map<String, Any?>> {
    val belowY = verticalLow - OVERALL_OFFSET_MM
    val leftX = horizontalLow - OVERALL_OFFSET_MM
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
    val xBreaks = (bodies + holes).flatMap { shape -> (shape["x_mm"] as Double).let { listOf(it, it + (shape["width_mm"] as Double)) } }
    val zBreaks = listOf(extent.floor, extent.ceiling) + listOfNotNull(0.0.takeIf { extent.floor < 0.0 && extent.ceiling > 0.0 }) + shown.flatMap { listOf(it.zMm, it.zMm + it.dzMm) } +
        geometry.cuts.filter { it.host in ids }.flatMap { listOf(it.region.minMm[2], it.region.maxMm[2]) }
    val chains = chainShapes("chain:x", xBreaks, ChainSide.BELOW, extent.floor - CHAIN_OFFSET_MM) + chainShapes("chain:z", zBreaks, ChainSide.LEFT, low - CHAIN_OFFSET_MM)
    return bodies + holes + labels + chains + groundLine(low, high) + flatDimensions(low, high, extent.floor, extent.ceiling)
}

internal class FlatDrawing(val id: String, val title: String, val shapes: List<Map<String, Any?>>)

/** One elevation per side of the building: the pieces nearest the viewer, the holes cut in them, and the dimensions of the whole. */
internal fun elevationDrawings(geometry: Geometry): List<FlatDrawing> {
    val extent = extentOf(geometry.pieces) ?: return emptyList()
    return VIEWS.map { view -> FlatDrawing(view.id, view.title, elevationShapes(view, extent, geometry)) }
}

internal fun blueprintElevationNodes(geometry: Geometry): List<JsonObject> =
    elevationDrawings(geometry).map { flatNode("blueprint-${it.id}", it.title, it.shapes, geometry) }

private const val LAYER_ROW_MM = 200.0
private const val LAYER_LABEL_GAP_MM = 300.0

/** A leader from a point in a layer to the words naming it. */
private fun layerLabel(piece: GeoPiece, layer: GeoLayer, index: Int, anchor: Pair<Double, Double>, text: Pair<Double, Double>, align: String): List<Map<String, Any?>> = listOf(
    lineShape("layer:${piece.id}/leader/$index", "dimension", anchor.first, anchor.second, text.first, text.second),
    mapOf("id" to "size:layer:${piece.id}/$index", "kind" to "label", "style" to "dimension", "x_mm" to text.first, "y_mm" to text.second, "text" to "${layer.name}: ${layer.material} ${dimensionText(layer.thicknessMm)}", "align" to align, "dx_dp" to if (align == "end") -4 else if (align == "start") 4 else 0, "dy_dp" to 0),
)

/** A piece cut by the section as its layers, outside to inside: a line between each pair and a leader to words naming each, listed outermost first from the top, standing to the right of an upright piece and above a flat one. */
internal fun layerShapes(piece: GeoPiece, extent: Extent): List<Map<String, Any?>> {
    val isUpright = piece.dxMm <= piece.dzMm
    val total = piece.layers.sumOf { it.thicknessMm }
    val outsideIsLow = if (isUpright) piece.xMm + piece.dxMm / 2 < (extent.west + extent.east) / 2 else piece.zMm + piece.dzMm / 2 < (extent.floor + extent.ceiling) / 2
    val start = if (isUpright) piece.xMm else piece.zMm
    val thickness = if (isUpright) piece.dxMm else piece.dzMm
    val scale = if (total > 0) thickness / total else 1.0
    val offsets = piece.layers.runningFold(0.0) { sum, layer -> sum + layer.thicknessMm * scale }
    fun at(distanceFromOutside: Double) = if (outsideIsLow) start + distanceFromOutside else start + thickness - distanceFromOutside
    val boundaries = offsets.drop(1).dropLast(1).mapIndexed { index, distance ->
        if (isUpright) lineShape("layer:${piece.id}/boundary/$index", "seam", at(distance), piece.zMm, at(distance), piece.zMm + piece.dzMm)
        else lineShape("layer:${piece.id}/boundary/$index", "seam", piece.xMm, at(distance), piece.xMm + piece.dxMm, at(distance))
    }
    val count = piece.layers.size
    val words = piece.layers.flatMapIndexed { index, layer ->
        val middle = at((offsets[index] + offsets[index + 1]) / 2)
        val fromTop = (count - 1) / 2.0 - index
        if (isUpright) {
            val row = piece.zMm + piece.dzMm / 2 + fromTop * LAYER_ROW_MM
            layerLabel(piece, layer, index, middle to row, (piece.xMm + piece.dxMm + LAYER_LABEL_GAP_MM) to row, "start")
        } else {
            val centreX = piece.xMm + piece.dxMm / 2
            layerLabel(piece, layer, index, (centreX + fromTop * LAYER_ROW_MM) to middle, centreX to (piece.zMm + piece.dzMm + LAYER_LABEL_GAP_MM + (count - 1 - index) * LAYER_ROW_MM), "center")
        }
    }
    return boundaries + words
}

private const val SAME_LABEL_SPAN_MM = 1500.0

private fun thicknessOf(piece: GeoPiece) = if (piece.dxMm <= piece.dzMm) piece.dxMm else piece.dzMm

/** Pieces of one system and thickness standing close together, which one label names. */
private fun repeatedRuns(pieces: List<GeoPiece>): List<List<GeoPiece>> =
    pieces.groupBy { it.system to thicknessOf(it) }.values.flatMap { group ->
        group.sortedBy { it.xMm }.fold(emptyList<List<GeoPiece>>()) { runs, piece ->
            val last = runs.lastOrNull()
            if (last != null && piece.xMm - last.last().xMm < SAME_LABEL_SPAN_MM) runs.dropLast(1) + listOf(last + piece) else runs + listOf(listOf(piece))
        }
    }

private fun hasPieceBelow(piece: GeoPiece, passed: List<GeoPiece>) =
    passed.any { it !== piece && it.zMm + it.dzMm in (piece.zMm - 1)..(piece.zMm + 1) && it.xMm < piece.xMm + piece.dxMm && it.xMm + it.dxMm > piece.xMm }

/** The total thickness of a piece the section passes through, until the domain gives the layers it is built of; a run of the same piece is named once, centred under its middle one. */
private fun thicknessLabel(run: List<GeoPiece>, passed: List<GeoPiece>): Map<String, Any?> {
    val piece = run[run.size / 2]
    val isUpright = piece.dxMm <= piece.dzMm
    val isRepeated = run.size > 1
    val beside = isUpright && !isRepeated
    val isAbove = !beside && hasPieceBelow(piece, passed)
    val at = if (beside) (piece.xMm + piece.dxMm) to (piece.zMm + piece.dzMm / 2) else (piece.xMm + piece.dxMm / 2) to if (isAbove) piece.zMm + piece.dzMm else piece.zMm
    val repeats = if (isRepeated) " (${run.size} alike)" else ""
    return mapOf(
        "id" to "size:thickness:${piece.id}", "kind" to "label", "style" to "dimension", "x_mm" to at.first, "y_mm" to at.second,
        "text" to "thickness ${dimensionText(thicknessOf(piece))}$repeats", "align" to if (beside) "start" else "center", "dx_dp" to if (beside) 6 else 0, "dy_dp" to if (beside) 0 else if (isAbove) -12 else 12,
    )
}

private fun cutsPlane(piece: GeoPiece, southMm: Double) = piece.yMm <= southMm && southMm <= piece.yMm + piece.dyMm

/** The building cut by a vertical plane through the middle of its depth, looking north: what the plane passes through is drawn heavy. */
internal fun sectionDrawing(geometry: Geometry): FlatDrawing? {
    val extent = extentOf(geometry.pieces) ?: return null
    val planeMm = (extent.south + extent.north) / 2
    val passed = geometry.pieces.filter { cutsPlane(it, planeMm) }
    val bodies = passed.map { rectShape(it.id, CUT_STYLE, it.xMm, it.zMm, it.dxMm, it.dzMm) }
    val holes = geometry.cuts.filter { it.region.minMm[1] <= planeMm && planeMm <= it.region.maxMm[1] }.map {
        rectShape("hole:${it.host}|${it.opening}", "hole", it.region.minMm[0], it.region.minMm[2], it.region.maxMm[0] - it.region.minMm[0], it.region.maxMm[2] - it.region.minMm[2])
    }
    val xBreaks = passed.flatMap { listOf(it.xMm, it.xMm + it.dxMm) }
    val zBreaks = listOf(extent.floor, extent.ceiling) + passed.flatMap { listOf(it.zMm, it.zMm + it.dzMm) }
    val chains = chainShapes("chain:x", xBreaks, ChainSide.BELOW, extent.floor - CHAIN_OFFSET_MM) + chainShapes("chain:z", zBreaks, ChainSide.LEFT, extent.west - CHAIN_OFFSET_MM)
    val shapes = bodies + holes + passed.filter { it.layers.isNotEmpty() }.flatMap { layerShapes(it, extent) } + repeatedRuns(passed.filter { it.layers.isEmpty() }).map { thicknessLabel(it, passed) } + chains + groundLine(extent.west, extent.east) + flatDimensions(extent.west, extent.east, extent.floor, extent.ceiling)
    return FlatDrawing("section", "Section through the middle of the depth (${dimensionText(planeMm)} from the south edge), looking north", shapes)
}

internal fun blueprintSectionNode(geometry: Geometry): JsonObject? = sectionDrawing(geometry)?.let { flatNode("blueprint-${it.id}", it.title, it.shapes, geometry) }

private fun planView(geometry: Geometry, expansion: Expansion): JsonObject =
    specNode("blueprint-plan-view", "column", mapOf("gap" to 4), listOf(textNode("blueprint-plan-title", "Plan", variant = "caption", color = MUTED_INK), blueprintPlanNode(geometry, expansion)))

/** Every flat drawing of the building: the plan, the four elevations and the section. */
internal fun blueprintDrawingsNode(geometry: Geometry, expansion: Expansion): JsonObject =
    specNode(
        "drawings", "column", mapOf("gap" to 12),
        listOf(textNode("drawings-title", "Drawings", variant = "subheading", bold = true), planView(geometry, expansion)) + blueprintElevationNodes(geometry) + listOfNotNull(blueprintSectionNode(geometry)),
    )
