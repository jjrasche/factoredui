package ai.factoredui.application

private const val CUT_TOLERANCE_MM = 0.5

private data class Bounds(val west: Double, val south: Double, val floor: Double, val east: Double, val north: Double, val ceiling: Double) {
    fun isEmpty() = east - west < CUT_TOLERANCE_MM || north - south < CUT_TOLERANCE_MM || ceiling - floor < CUT_TOLERANCE_MM
}

private fun number(box: Map<String, Any?>, key: String) = (box[key] as Number).toDouble()

private fun boundsOf(box: Map<String, Any?>): Bounds {
    val halfWidth = number(box, "width_mm") / 2
    val halfDepth = number(box, "depth_mm") / 2
    return Bounds(
        number(box, "x_mm") - halfWidth, number(box, "y_mm") - halfDepth, number(box, "z_mm"),
        number(box, "x_mm") + halfWidth, number(box, "y_mm") + halfDepth, number(box, "z_mm") + number(box, "height_mm"),
    )
}

private fun boxOf(template: Map<String, Any?>, id: String, bounds: Bounds): Map<String, Any?> = template + mapOf(
    "id" to id, "x_mm" to (bounds.west + bounds.east) / 2, "y_mm" to (bounds.south + bounds.north) / 2, "z_mm" to bounds.floor,
    "width_mm" to bounds.east - bounds.west, "depth_mm" to bounds.north - bounds.south, "height_mm" to bounds.ceiling - bounds.floor,
)

/** What is left of [host] once [hole] is taken out of it: up to six boxes that tile the rest. */
private fun subtract(host: Bounds, hole: Bounds): List<Bounds> {
    val clipped = Bounds(maxOf(host.west, hole.west), maxOf(host.south, hole.south), maxOf(host.floor, hole.floor), minOf(host.east, hole.east), minOf(host.north, hole.north), minOf(host.ceiling, hole.ceiling))
    if (clipped.isEmpty()) return listOf(host)
    return listOf(
        Bounds(host.west, host.south, host.floor, clipped.west, host.north, host.ceiling),
        Bounds(clipped.east, host.south, host.floor, host.east, host.north, host.ceiling),
        Bounds(clipped.west, host.south, host.floor, clipped.east, host.north, clipped.floor),
        Bounds(clipped.west, host.south, clipped.ceiling, clipped.east, host.north, host.ceiling),
        Bounds(clipped.west, host.south, clipped.floor, clipped.east, clipped.south, clipped.ceiling),
        Bounds(clipped.west, clipped.north, clipped.floor, clipped.east, host.north, clipped.ceiling),
    ).filterNot { it.isEmpty() }
}

private fun holeOf(region: GeoRegion) = Bounds(region.minMm[0], region.minMm[1], region.minMm[2], region.maxMm[0], region.maxMm[1], region.maxMm[2])

/** The id a piece of a host takes once cuts split it: the host's own id for the first, then numbered. */
internal fun fragmentId(host: String, index: Int) = if (index == 0) host else "$host#${index + 1}"

/** Every box that is the host of a cut, drawn as the boxes around the opening, so the opening is a hole in it and not a box laid over it. */
internal fun withCutsOpened(boxes: List<Map<String, Any?>>, geometry: Geometry): List<Map<String, Any?>> {
    val holesOf = geometry.cuts.groupBy { it.host }.mapValues { (_, cuts) -> cuts.map { holeOf(it.region) } }
    return boxes.flatMap { box ->
        val holes = holesOf[box["id"]] ?: return@flatMap listOf(box)
        val fragments = holes.fold(listOf(boundsOf(box))) { pieces, hole -> pieces.flatMap { subtract(it, hole) } }
        fragments.mapIndexed { index, bounds -> boxOf(box, fragmentId(box["id"] as String, index), bounds) }
    }
}

/** A card for each fragment of a split box, the same as its host's. */
internal fun withFragmentDetails(details: Map<String, Any?>, boxes: List<Map<String, Any?>>): Map<String, Any?> =
    details + boxes.mapNotNull { box ->
        val id = box["id"] as String
        val host = id.substringBeforeLast('#')
        if (id != host) details[host]?.let { id to it } else null
    }
