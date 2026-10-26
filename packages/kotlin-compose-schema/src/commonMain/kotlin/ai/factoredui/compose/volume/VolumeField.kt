package ai.factoredui.compose.volume

/** A dense scalar field on a regular grid of cubic cells, read from the `field` prop of a volume. Only the cells in [inside] carry a reading: [frames] holds, per frame, one value per inside cell in [inside] order, NaN where there is none. */
class VolumeField(
    val originMm: VolumePoint,
    val stepMm: Double,
    val countI: Int,
    val countJ: Int,
    val countK: Int,
    val inside: IntArray,
    val frames: List<DoubleArray>,
    val radiusCells: Double,
    val opacity: Double,
    val isCells: Boolean = false,
    val notLoaded: Set<Int> = emptySet(),
) {
    fun centreOf(cell: Int): VolumePoint {
        val i = cell % countI
        val j = (cell / countI) % countJ
        val k = cell / (countI * countJ)
        return VolumePoint(originMm.xMm + (i + 0.5) * stepMm, originMm.yMm + (j + 0.5) * stepMm, originMm.zMm + (k + 0.5) * stepMm)
    }

    fun span(): VolumeSpanMm = VolumeSpanMm(
        west = originMm.xMm, south = originMm.yMm, floor = originMm.zMm,
        east = originMm.xMm + countI * stepMm, north = originMm.yMm + countJ * stepMm, ceiling = originMm.zMm + countK * stepMm,
    )
}

class VolumeFieldRead(val field: VolumeField?, val notices: List<String>)

private const val DEFAULT_RADIUS_CELLS = 1.0
private const val DEFAULT_OPACITY = 0.5
private const val DEFAULT_CELL_OPACITY = 1.0

private fun numberOf(raw: Any?): Double? = (raw as? Number)?.toDouble()?.takeIf { it.isFinite() }

/** The cells a run-length mask marks inside, as indices with i running fastest, then j, then k: runs alternate outside, inside, starting outside. Null when the runs are not whole numbers or do not add up to [total]; a missing mask is every cell. */
fun fieldInsideCells(rawMask: Any?, total: Int): IntArray? {
    if (rawMask == null) return IntArray(total) { it }
    val runs = (rawMask as? List<*>)?.map { numberOf(it)?.takeIf { run -> run >= 0 && run == kotlin.math.floor(run) }?.toInt() } ?: return null
    if (runs.any { it == null } || runs.sumOf { it!!.toLong() } != total.toLong()) return null
    val cells = ArrayList<Int>()
    var position = 0
    runs.forEachIndexed { index, run ->
        if (index % 2 == 1) for (cell in position until position + run!!) cells.add(cell)
        position += run!!
    }
    return cells.toIntArray()
}

private class FramesRead(val frames: List<DoubleArray>, val notices: List<String>, val notLoaded: Set<Int>)

private fun framesOf(rawValues: Any?, scale: Double, insideCount: Int): FramesRead {
    val raw = (rawValues as? List<*>).orEmpty()
    val notices = ArrayList<String>()
    val frames = raw.mapIndexed { index, frame ->
        val readings = (frame as? List<*>).orEmpty()
        if (frame != null && readings.size != insideCount) notices.add("volume field: frame $index has ${readings.size} values for $insideCount inside cells; the missing ones are not coloured.")
        DoubleArray(insideCount) { cell -> numberOf(readings.getOrNull(cell))?.let { it / scale } ?: Double.NaN }
    }
    return FramesRead(frames, notices, raw.indices.filter { raw[it] == null }.toSet())
}

fun readVolumeField(raw: Any?): VolumeFieldRead {
    val fields = raw as? Map<*, *> ?: return VolumeFieldRead(null, emptyList())
    val origin = (fields["origin_mm"] as? List<*>)?.map(::numberOf)
    val counts = (fields["counts"] as? List<*>)?.map { numberOf(it)?.toInt() }
    val step = numberOf(fields["step_mm"])
    if (origin == null || origin.size != 3 || origin.any { it == null } || counts == null || counts.size != 3 || counts.any { it == null || it < 1 } || step == null || step <= 0.0) {
        return VolumeFieldRead(null, listOf("volume field: origin_mm must be [x, y, z], step_mm a positive number and counts three whole numbers of 1 or more; it is not drawn."))
    }
    val (countI, countJ, countK) = counts.map { it!! }
    val total = countI * countJ * countK
    val inside = fieldInsideCells(fields["mask"], total)
        ?: return VolumeFieldRead(null, listOf("volume field: mask must be whole run lengths, outside then inside alternating, adding up to $total cells; it is not drawn."))
    val scale = numberOf(fields["scale"])?.takeIf { it > 0.0 } ?: 1.0
    val read = framesOf(fields["values"], scale, inside.size)
    val (isCells, styleNotices) = styleOf(fields["style"])
    val field = VolumeField(
        VolumePoint(origin[0]!!, origin[1]!!, origin[2]!!), step, countI, countJ, countK, inside, read.frames,
        numberOf(fields["radius_cells"])?.takeIf { it > 0.0 } ?: DEFAULT_RADIUS_CELLS,
        numberOf(fields["opacity"])?.coerceIn(0.0, 1.0) ?: if (isCells) DEFAULT_CELL_OPACITY else DEFAULT_OPACITY,
        isCells,
        read.notLoaded,
    )
    return VolumeFieldRead(field, read.notices + styleNotices)
}

private fun styleOf(raw: Any?): Pair<Boolean, List<String>> = when (raw) {
    null, "soft" -> false to emptyList()
    "cells" -> true to emptyList()
    else -> false to listOf("volume field: style must be soft or cells, not '$raw'; soft is drawn.")
}

/** The span of [first] and [second] together; whichever exists when only one does. */
fun unionSpan(first: VolumeSpanMm?, second: VolumeSpanMm?): VolumeSpanMm? {
    if (first == null || second == null) return first ?: second
    return VolumeSpanMm(
        west = minOf(first.west, second.west), south = minOf(first.south, second.south), floor = minOf(first.floor, second.floor),
        east = maxOf(first.east, second.east), north = maxOf(first.north, second.north), ceiling = maxOf(first.ceiling, second.ceiling),
    )
}
