package ai.factoredui.compose.volume

import ai.factoredui.compose.plan.PlanStyle
import ai.factoredui.compose.plan.readPlanStyles

/** A box in millimetres: [xMm], [yMm] is the centre of its footprint on the ground (east, north), [zMm] its underside, turned [rotationDeg] anticlockwise seen from above. A width or depth of 0 is a vertical plane and both 0 is a vertical edge. An [isEstimate] box has a size that was not given, so it is hatched. */
data class VolumeBox(
    val id: String,
    val xMm: Double,
    val yMm: Double,
    val zMm: Double,
    val widthMm: Double,
    val depthMm: Double,
    val heightMm: Double,
    val rotationDeg: Double = 0.0,
    val style: String? = null,
    val isEstimate: Boolean = false,
)

data class VolumeLegendEntry(val label: String, val style: String?, val isHatched: Boolean)

data class VolumePoint(val xMm: Double, val yMm: Double, val zMm: Double)

/** A straight line in millimetres between two points, drawn in [style]. */
data class VolumeLine(val id: String, val from: VolumePoint, val to: VolumePoint, val style: String? = null)

/** Words at a point in millimetres, drawn in the ink of [style]. */
data class VolumeLabel(val id: String, val at: VolumePoint, val text: String, val style: String? = null)

data class VolumeRead(
    val boxes: List<VolumeBox>,
    val styles: Map<String, PlanStyle>,
    val legend: List<VolumeLegendEntry>,
    val notices: List<String>,
    val lines: List<VolumeLine> = emptyList(),
    val labels: List<VolumeLabel> = emptyList(),
)

/** The horizontal and vertical span of a set of boxes, in millimetres, from their turned corners. */
data class VolumeSpanMm(val west: Double, val south: Double, val floor: Double, val east: Double, val north: Double, val ceiling: Double)

private val SIZE_KEYS = listOf("width_mm", "depth_mm", "height_mm")

private fun numberIn(fields: Map<*, *>, key: String): Double? = (fields[key] as? Number)?.toDouble()?.takeIf { it.isFinite() }

private fun boxOf(index: Int, fields: Map<*, *>): Pair<VolumeBox?, String?> {
    val id = fields["id"] as? String ?: "box $index"
    val x = numberIn(fields, "x_mm")
    val y = numberIn(fields, "y_mm")
    val z = numberIn(fields, "z_mm")
    if (x == null || y == null || z == null) return null to "volume box '$id': x_mm, y_mm and z_mm must be numbers; it is not drawn."
    val sizes = SIZE_KEYS.map { numberIn(fields, it) }
    if (sizes.any { it == null || it < 0 }) return null to "volume box '$id': width_mm, depth_mm and height_mm must be numbers of 0 or more; it is not drawn."
    val (width, depth, height) = sizes.map { it!! }
    return VolumeBox(id, x, y, z, width, depth, height, numberIn(fields, "rotation_deg") ?: 0.0, fields["style"] as? String, fields["estimate"] as? Boolean ?: false) to null
}

private fun legendOf(resolved: Any?): List<VolumeLegendEntry> =
    (resolved as? List<*>).orEmpty().mapNotNull { entry ->
        val fields = entry as? Map<*, *> ?: return@mapNotNull null
        val label = fields["label"] as? String ?: return@mapNotNull null
        VolumeLegendEntry(label, fields["style"] as? String, fields["hatched"] as? Boolean ?: false)
    }

private fun pointOf(raw: Any?): VolumePoint? {
    val numbers = (raw as? List<*>)?.map { (it as? Number)?.toDouble()?.takeIf { value -> value.isFinite() } }
    if (numbers == null || numbers.size != 3 || numbers.any { it == null }) return null
    return VolumePoint(numbers[0]!!, numbers[1]!!, numbers[2]!!)
}

private fun linesOf(resolved: Any?): Pair<List<VolumeLine>, List<String>> {
    val entries = (resolved as? List<*>).orEmpty().mapIndexed { index, entry ->
        val fields = entry as? Map<*, *>
        val id = fields?.get("id") as? String ?: "line $index"
        val from = pointOf(fields?.get("from"))
        val to = pointOf(fields?.get("to"))
        if (fields == null || from == null || to == null) null to "volume line '$id': from and to must each be [x_mm, y_mm, z_mm]; it is not drawn."
        else VolumeLine(id, from, to, fields["style"] as? String) to null
    }
    return entries.mapNotNull { it.first } to entries.mapNotNull { it.second }
}

private fun labelsOf(resolved: Any?): Pair<List<VolumeLabel>, List<String>> {
    val entries = (resolved as? List<*>).orEmpty().mapIndexed { index, entry ->
        val fields = entry as? Map<*, *>
        val id = fields?.get("id") as? String ?: "label $index"
        val at = pointOf(fields?.get("at"))
        val text = fields?.get("text") as? String
        if (fields == null || at == null || text == null) null to "volume label '$id': at must be [x_mm, y_mm, z_mm] and text must be words; it is not drawn."
        else VolumeLabel(id, at, text, fields["style"] as? String) to null
    }
    return entries.mapNotNull { it.first } to entries.mapNotNull { it.second }
}

fun readVolume(boxes: Any?, styles: Any?, legend: Any?, lines: Any? = null, labels: Any? = null): VolumeRead {
    val entries = (boxes as? List<*>).orEmpty().mapIndexed { index, entry ->
        (entry as? Map<*, *>)?.let { boxOf(index, it) } ?: (null to "volume box $index is not an object; it is not drawn.")
    }
    val readStyles = readPlanStyles(styles)
    val drawn = entries.mapNotNull { it.first }
    val unknownStyles = drawn.mapNotNull { it.style }.filter { it !in readStyles }.distinct()
        .map { "volume style '$it' is not in styles; its boxes are drawn plain." }
    val (readLines, lineNotices) = linesOf(lines)
    val (readLabels, labelNotices) = labelsOf(labels)
    return VolumeRead(drawn, readStyles, legendOf(legend), entries.mapNotNull { it.second } + unknownStyles + lineNotices + labelNotices, readLines, readLabels)
}

/** The four ground corners of a box as east-north pairs, turned about its centre. */
fun footprintCorners(box: VolumeBox): List<Pair<Double, Double>> {
    val turn = box.rotationDeg * kotlin.math.PI / 180.0
    val cosTurn = kotlin.math.cos(turn)
    val sinTurn = kotlin.math.sin(turn)
    val halfWidth = box.widthMm / 2
    val halfDepth = box.depthMm / 2
    return listOf(-1 to -1, 1 to -1, 1 to 1, -1 to 1).map { (sx, sy) ->
        val dx = sx * halfWidth
        val dy = sy * halfDepth
        (box.xMm + dx * cosTurn - dy * sinTurn) to (box.yMm + dx * sinTurn + dy * cosTurn)
    }
}

fun volumeSpan(boxes: List<VolumeBox>): VolumeSpanMm? {
    if (boxes.isEmpty()) return null
    val corners = boxes.flatMap(::footprintCorners)
    return VolumeSpanMm(
        west = corners.minOf { it.first }, south = corners.minOf { it.second }, floor = boxes.minOf { it.zMm },
        east = corners.maxOf { it.first }, north = corners.maxOf { it.second }, ceiling = boxes.maxOf { it.zMm + it.heightMm },
    )
}

private const val COINCIDENT_TOLERANCE_MM = 1.0

private class VerticalPlane(val alongX: Double, val alongY: Double, val offset: Double, val from: Double, val to: Double, val low: Double, val high: Double)

private fun planeOf(box: VolumeBox): VerticalPlane? {
    val isFlatInDepth = box.depthMm == 0.0
    if (isFlatInDepth == (box.widthMm == 0.0)) return null
    val turn = box.rotationDeg * kotlin.math.PI / 180.0
    val alongX = if (isFlatInDepth) kotlin.math.cos(turn) else -kotlin.math.sin(turn)
    val alongY = if (isFlatInDepth) kotlin.math.sin(turn) else kotlin.math.cos(turn)
    val length = if (isFlatInDepth) box.widthMm else box.depthMm
    val centreAlong = alongX * box.xMm + alongY * box.yMm
    val offset = -alongY * box.xMm + alongX * box.yMm
    return VerticalPlane(alongX, alongY, offset, centreAlong - length / 2, centreAlong + length / 2, box.zMm, box.zMm + box.heightMm)
}

private fun isWithin(value: Double, from: Double, to: Double) = value >= from - COINCIDENT_TOLERANCE_MM && value <= to + COINCIDENT_TOLERANCE_MM

/** Whether [upper] is a vertical plane lying inside another vertical plane [host], in the same flat surface. */
fun isDrawnOn(upper: VolumeBox, host: VolumeBox): Boolean {
    val inner = planeOf(upper) ?: return false
    val outer = planeOf(host) ?: return false
    val ends = listOf(inner.from, inner.to).map { along ->
        val x = inner.alongX * along - inner.alongY * inner.offset
        val y = inner.alongY * along + inner.alongX * inner.offset
        (outer.alongX * x + outer.alongY * y) to (-outer.alongY * x + outer.alongX * y)
    }
    val isSameSurface = ends.all { kotlin.math.abs(it.second - outer.offset) <= COINCIDENT_TOLERANCE_MM }
    val isInside = ends.all { isWithin(it.first, outer.from, outer.to) }
    return isSameSurface && isInside && isWithin(inner.low, outer.low, outer.high) && isWithin(inner.high, outer.low, outer.high)
}

/** [farToNear] with every plane that lies in another plane moved to just after it, so the thing on a surface is never painted under it. */
fun <T> paintOnTop(farToNear: List<T>, boxOf: (T) -> VolumeBox): List<T> {
    val order = farToNear.toMutableList()
    for (upper in farToNear) {
        val upperAt = order.indexOf(upper)
        val hostAt = order.indexOfLast { it !== upper && isDrawnOn(boxOf(upper), boxOf(it)) }
        if (hostAt > upperAt) {
            order.removeAt(upperAt)
            order.add(hostAt, upper)
        }
    }
    return order
}
