package ai.factoredui.compose.volume

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** The four corners of a box's underside and of its top, in the order of [footprintCorners]; the top ring is above the bottom ring corner for corner. */
data class BoxRings(val low: List<VolumePoint>, val high: List<VolumePoint>) {
    val all: List<VolumePoint> get() = low + high
}

private fun degreesToRadians(degrees: Double) = degrees * PI / 180.0

/** A box that is not pitched has its rings straight above its footprint; a pitched one is tipped about its own width axis through its centre and then turned, so a sheet tipped by 30 degrees lies on a 30 degree slope. */
fun boxRings(box: VolumeBox): BoxRings {
    val footprint = footprintCorners(box)
    if (box.pitchDeg == 0.0) {
        return BoxRings(footprint.map { VolumePoint(it.first, it.second, box.zMm) }, footprint.map { VolumePoint(it.first, it.second, box.zMm + box.heightMm) })
    }
    val tip = degreesToRadians(box.pitchDeg)
    val turn = degreesToRadians(box.rotationDeg)
    val centreZ = box.zMm + box.heightMm / 2
    fun cornerAt(signX: Int, signY: Int, signZ: Int): VolumePoint {
        val x = signX * box.widthMm / 2
        val y = signY * box.depthMm / 2
        val z = signZ * box.heightMm / 2
        val tippedY = y * cos(tip) - z * sin(tip)
        val tippedZ = y * sin(tip) + z * cos(tip)
        return VolumePoint(box.xMm + x * cos(turn) - tippedY * sin(turn), box.yMm + x * sin(turn) + tippedY * cos(turn), centreZ + tippedZ)
    }
    val signs = listOf(-1 to -1, 1 to -1, 1 to 1, -1 to 1)
    return BoxRings(signs.map { (sx, sy) -> cornerAt(sx, sy, -1) }, signs.map { (sx, sy) -> cornerAt(sx, sy, 1) })
}

private fun minus(a: VolumePoint, b: VolumePoint) = Direction(a.xMm - b.xMm, a.yMm - b.yMm, a.zMm - b.zMm)

private fun cross(a: Direction, b: Direction) = Direction(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)

private fun centreOf(points: List<VolumePoint>) = VolumePoint(points.sumOf { it.xMm } / points.size, points.sumOf { it.yMm } / points.size, points.sumOf { it.zMm } / points.size)

private fun outwardNormal(face: List<VolumePoint>, boxCentre: VolumePoint): Direction {
    val raw = cross(minus(face[1], face[0]), minus(face[3], face[0]))
    val length = sqrt(raw.x * raw.x + raw.y * raw.y + raw.z * raw.z)
    if (length == 0.0) return Direction(0.0, 0.0, 1.0)
    val outward = minus(centreOf(face), boxCentre)
    val sign = if (raw.x * outward.x + raw.y * outward.y + raw.z * outward.z < 0.0) -1.0 else 1.0
    return Direction(sign * raw.x / length, sign * raw.y / length, sign * raw.z / length)
}

/** The outward normals of a box's five drawn faces, in the order they are drawn: the top, then the side from each underside corner to the next. */
fun boxFaceNormals(rings: BoxRings): List<Direction> {
    val centre = centreOf(rings.all)
    val top = listOf(rings.high[0], rings.high[1], rings.high[2], rings.high[3])
    val sides = rings.low.indices.map { i -> listOf(rings.low[i], rings.low[(i + 1) % 4], rings.high[(i + 1) % 4], rings.high[i]) }
    return (listOf(top) + sides).map { outwardNormal(it, centre) }
}
