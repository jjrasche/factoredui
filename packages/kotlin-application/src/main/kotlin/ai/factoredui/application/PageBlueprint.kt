package ai.factoredui.application

import kotlinx.serialization.json.JsonObject
import kotlin.math.round

internal const val BLUEPRINT_GROUND = "#0b2a4a"
private const val LINE_INK = "#d6e6ff"
private const val SEAM_INK = "#6fd0ff"
private const val HOLE_INK = "#ffffff"
private const val DIMENSION_INK = "#ffd479"
internal const val DIMENSION_OFFSET_MM = 450.0
internal const val TICK_MM = 150.0
private const val MM_PER_INCH = 25.4
private const val INCHES_PER_FOOT = 12
private const val FRACTION_DENOMINATOR = 16

private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

/** A length in millimetres as feet, inches and the nearest sixteenth, such as 8'-0" or 4'-1 1/2". */
internal fun feetInches(mm: Double): String {
    val sixteenths = round(mm / MM_PER_INCH * FRACTION_DENOMINATOR).toInt()
    val wholeInches = sixteenths / FRACTION_DENOMINATOR
    val remainder = sixteenths % FRACTION_DENOMINATOR
    val feet = wholeInches / INCHES_PER_FOOT
    val inches = wholeInches % INCHES_PER_FOOT
    val fraction = if (remainder == 0) "" else " ${remainder / gcd(remainder, FRACTION_DENOMINATOR)}/${FRACTION_DENOMINATOR / gcd(remainder, FRACTION_DENOMINATOR)}"
    return "$feet'-$inches$fraction\""
}

internal fun dimensionText(mm: Double) = "${formatFigure(mm)} mm · ${feetInches(mm)}"

internal fun blueprintStyles(): Map<String, Map<String, Any?>> = mapOf(
    "piece" to mapOf("stroke" to LINE_INK, "weight" to 1.4),
    "seam" to mapOf("stroke" to SEAM_INK, "weight" to 1.0),
    "hole" to mapOf("stroke" to HOLE_INK, "weight" to 2.0, "dash" to true),
    "unknown" to mapOf("stroke" to LINE_INK, "weight" to 1.2, "dash" to true),
    "dimension" to mapOf("stroke" to DIMENSION_INK, "weight" to 1.0),
    "cut" to mapOf("stroke" to LINE_INK, "weight" to 2.6),
)

private fun GeoPiece.centreXMm() = xMm + dxMm / 2

private fun GeoPiece.centreYMm() = yMm + dyMm / 2

private fun boxOf(piece: GeoPiece): Map<String, Any?> = mapOf(
    "id" to piece.id, "style" to "piece", "x_mm" to piece.centreXMm(), "y_mm" to piece.centreYMm(), "z_mm" to piece.zMm,
    "width_mm" to piece.dxMm, "depth_mm" to piece.dyMm, "height_mm" to piece.dzMm,
)

private fun lineOf(id: String, style: String, from: List<Double>, to: List<Double>): Map<String, Any?> = mapOf("id" to id, "style" to style, "from" to from, "to" to to)

/** The edges of a region that has some size, so a flat region gives its outline and not the same edge twice. */
private fun boxEdges(idPrefix: String, style: String, region: GeoRegion): List<Map<String, Any?>> {
    val (low, high) = region.minMm to region.maxMm
    fun corner(x: Boolean, y: Boolean, z: Boolean) = listOf(if (x) high[0] else low[0], if (y) high[1] else low[1], if (z) high[2] else low[2])
    val flags = listOf(false, true)
    val edges = flags.flatMap { a -> flags.flatMap { b -> listOf(corner(false, a, b) to corner(true, a, b), corner(a, false, b) to corner(a, true, b), corner(a, b, false) to corner(a, b, true)) } }
    return edges.filter { (from, to) -> from != to }.distinct().mapIndexed { index, (from, to) -> lineOf("$idPrefix/$index", style, from, to) }
}

private fun isSeam(geometry: Geometry, contact: GeoContact): Boolean {
    val systems = geometry.pieces.associate { it.id to it.system }
    return contact.normal == "side" && systems[contact.a] != null && systems[contact.a] == systems[contact.b]
}

private fun seamRegions(geometry: Geometry): List<GeoContact> = geometry.contacts.filter { isSeam(geometry, it) }

internal class Extent(val west: Double, val east: Double, val south: Double, val north: Double, val floor: Double, val ceiling: Double)

internal fun extentOf(pieces: List<GeoPiece>): Extent? {
    if (pieces.isEmpty()) return null
    return Extent(pieces.minOf { it.xMm }, pieces.maxOf { it.xMm + it.dxMm }, pieces.minOf { it.yMm }, pieces.maxOf { it.yMm + it.dyMm }, pieces.minOf { it.zMm }, pieces.maxOf { it.zMm + it.dzMm })
}

private class Dimension(val id: String, val from: List<Double>, val to: List<Double>, val tickAlong: Int, val lengthMm: Double)

private fun overallDimensions(extent: Extent): List<Dimension> {
    val z = extent.floor
    val southY = extent.south - DIMENSION_OFFSET_MM
    val westX = extent.west - DIMENSION_OFFSET_MM
    return listOf(
        Dimension("width", listOf(extent.west, southY, z), listOf(extent.east, southY, z), 1, extent.east - extent.west),
        Dimension("depth", listOf(westX, extent.south, z), listOf(westX, extent.north, z), 0, extent.north - extent.south),
        Dimension("height", listOf(extent.east + DIMENSION_OFFSET_MM, extent.south, extent.floor), listOf(extent.east + DIMENSION_OFFSET_MM, extent.south, extent.ceiling), 0, extent.ceiling - extent.floor),
    )
}

private fun midpoint(from: List<Double>, to: List<Double>) = from.indices.map { (from[it] + to[it]) / 2 }

private fun dimensionLines3d(dimension: Dimension): List<Map<String, Any?>> {
    val tick = DoubleArray(3).also { it[dimension.tickAlong] = TICK_MM }.toList()
    fun shifted(point: List<Double>, sign: Double) = point.indices.map { point[it] + sign * tick[it] }
    return listOf(
        lineOf("dim:${dimension.id}", "dimension", dimension.from, dimension.to),
        lineOf("dim:${dimension.id}/start", "dimension", shifted(dimension.from, -1.0), shifted(dimension.from, 1.0)),
        lineOf("dim:${dimension.id}/end", "dimension", shifted(dimension.to, -1.0), shifted(dimension.to, 1.0)),
    )
}

private fun dimensionLabel3d(dimension: Dimension): Map<String, Any?> =
    mapOf("id" to "dim:${dimension.id}/text", "style" to "dimension", "at" to midpoint(dimension.from, dimension.to), "text" to dimensionText(dimension.lengthMm))

private fun hasFootprint(item: ExpandedObject) = item.widthMm != null && item.depthMm != null

private fun isRaised(item: ExpandedObject) = item.elevationMm != null && item.elevationMm != 0.0

private fun unknownObjects(geometry: Geometry, expansion: Expansion): List<ExpandedObject> {
    val ids = geometry.unmeasured.map { it.id }.toSet()
    return expansion.objects.filter { it.id in ids }
}

/** A piece whose size the engine could not measure, outlined dashed where the expansion places it, or as a dashed upright for a post. */
private fun unknownLines3d(item: ExpandedObject): List<Map<String, Any?>> {
    val z = item.elevationMm?.takeIf { isRaised(item) } ?: return emptyList()
    if (!hasFootprint(item)) {
        val height = item.fields["height_mm"] ?: return emptyList()
        return listOf(lineOf("unknown:${item.id}", "unknown", listOf(item.xMm, item.yMm, z), listOf(item.xMm, item.yMm, z + height)))
    }
    val east = item.xMm + item.widthMm!!
    val north = item.yMm + item.depthMm!!
    val corners = listOf(listOf(item.xMm, item.yMm, z), listOf(east, item.yMm, z), listOf(east, north, z), listOf(item.xMm, north, z))
    return corners.indices.map { lineOf("unknown:${item.id}/$it", "unknown", corners[it], corners[(it + 1) % 4]) }
}

internal fun detailsOfBlueprint(geometry: Geometry): Map<String, Any?> {
    fun row(label: String, value: String) = mapOf("label" to label, "value" to value, "note" to null)
    val measured = geometry.pieces.associate { piece ->
        piece.id to mapOf(
            "title" to piece.id,
            "sections" to listOf(mapOf("heading" to "Blueprint", "rows" to listOf(
                row("system", piece.system), row("east (mm)", dimensionText(piece.dxMm)), row("north (mm)", dimensionText(piece.dyMm)), row("up (mm)", dimensionText(piece.dzMm)),
            ))),
        )
    }
    val unmeasured = geometry.unmeasured.groupBy { it.id }.mapValues { (id, gaps) ->
        mapOf("title" to id, "sections" to listOf(mapOf("heading" to "Not measured", "rows" to gaps.map { row(humanize(it.field), "unknown: ${it.why}") })))
    }
    return measured + unmeasured
}

/** The pieces as boxes, and the lines the engine's geometry gives: panel seams, the holes cut for openings, the dashed unmeasured, and the dimensions of the whole. */
internal fun blueprintVolumeNode(geometry: Geometry, expansion: Expansion): JsonObject {
    val extent = extentOf(geometry.pieces)
    val dimensions = extent?.let(::overallDimensions).orEmpty()
    val lines = seamRegions(geometry).flatMap { boxEdges("seam:${it.a}|${it.b}", "seam", it.region) } +
        geometry.cuts.flatMap { boxEdges("hole:${it.host}|${it.opening}", "hole", it.region) } +
        unknownObjects(geometry, expansion).flatMap(::unknownLines3d) + dimensions.flatMap(::dimensionLines3d)
    return specNode(
        "blueprint-3d", "volume",
        mapOf(
            "mode" to "blueprint", "ground" to BLUEPRINT_GROUND,
            "boxes" to geometry.pieces.map(::boxOf), "lines" to lines, "labels" to dimensions.map(::dimensionLabel3d),
            "styles" to blueprintStyles(), "legend" to emptyList<Any>(), "empty_text" to "The geometry has nothing to draw.",
            "selected" to SELECTION_PATH, "details" to detailsOfBlueprint(geometry),
        ),
    )
}

internal fun rectShape(id: String, style: String, xMm: Double, yMm: Double, widthMm: Double, depthMm: Double): Map<String, Any?> =
    mapOf("id" to id, "kind" to "rect", "style" to style, "x_mm" to xMm, "y_mm" to yMm, "width_mm" to widthMm, "depth_mm" to depthMm)

internal fun lineShape(id: String, style: String, x: Double, y: Double, x2: Double, y2: Double): Map<String, Any?> =
    mapOf("id" to id, "kind" to "line", "style" to style, "x_mm" to x, "y_mm" to y, "x2_mm" to x2, "y2_mm" to y2)

private fun planDimension(extent: Extent) = flatDimensions(extent.west, extent.east, extent.south, extent.north)

private fun planUnknown(item: ExpandedObject): Map<String, Any?>? =
    if (hasFootprint(item)) rectShape("unknown:${item.id}", "unknown", item.xMm, item.yMm, item.widthMm!!, item.depthMm!!)
    else mapOf("id" to "unknown:${item.id}", "kind" to "marker", "style" to "unknown", "x_mm" to item.xMm, "y_mm" to item.yMm)

/** The footprints of the pieces seen from above, with the seams, the holes and the dimensions of the whole. */
internal fun blueprintPlanNode(geometry: Geometry, expansion: Expansion): JsonObject {
    val extent = extentOf(geometry.pieces)
    val seams = seamRegions(geometry).map { contact ->
        val (low, high) = contact.region.minMm to contact.region.maxMm
        lineShape("seam:${contact.a}|${contact.b}", "seam", low[0], low[1], high[0], high[1])
    }
    val holes = geometry.cuts.map { rectShape("hole:${it.host}|${it.opening}", "hole", it.region.minMm[0], it.region.minMm[1], it.region.maxMm[0] - it.region.minMm[0], it.region.maxMm[1] - it.region.minMm[1]) }
    val unknown = unknownObjects(geometry, expansion).mapNotNull(::planUnknown)
    val shapes = geometry.pieces.map { rectShape(it.id, "piece", it.xMm, it.yMm, it.dxMm, it.dyMm) } + seams + holes + unknown + extent?.let(::planDimension).orEmpty()
    return specNode(
        "blueprint-plan", "plan",
        mapOf(
            "ground" to BLUEPRINT_GROUND, "shapes" to shapes, "styles" to blueprintStyles(), "legend" to emptyList<Any>(),
            "empty_text" to "The geometry has nothing to draw.", "selected" to SELECTION_PATH, "details" to detailsOfBlueprint(geometry),
        ),
    )
}

private const val BLUEPRINT_NOTE = "Every line is the engine's geometry: edges in light blue, panel seams in cyan, holes cut for openings in white dashes, pieces with no measured size dashed, and dimensions in amber."

/** The blueprint section: a note, the solid in lines and the plan. */
internal fun blueprintNode(geometried: Geometried, expansion: Expansion): JsonObject {
    val geometry = geometried.geometry
    val failure = geometried.failure?.let { textNode("blueprint-failure", "The geometry could not be read: $it", variant = "caption", color = MUTED_INK) }
    val children = if (geometry == null) listOfNotNull(failure) else listOf(
        textNode("blueprint-note", BLUEPRINT_NOTE, variant = "caption", color = MUTED_INK), blueprintVolumeNode(geometry, expansion), blueprintPlanNode(geometry, expansion),
    ) + blueprintElevationNodes(geometry) + listOfNotNull(blueprintSectionNode(geometry))
    return specNode("blueprint", "column", mapOf("gap" to 8), listOf(textNode("blueprint-title", "Blueprint", variant = "subheading", bold = true)) + children)
}
