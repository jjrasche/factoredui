package ai.factoredui.compose.renderer

import ai.factoredui.compose.math.Camera
import ai.factoredui.compose.math.project
import ai.factoredui.compose.ramp.ColourRamp
import ai.factoredui.compose.ramp.rampColourFor
import ai.factoredui.compose.scene3d.drawTriangleBatch
import ai.factoredui.compose.volume.VolumeField
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

private const val RIM_POINTS = 6
private const val VERTICES_PER_SPLAT = RIM_POINTS * 3
private const val MM_PER_METRE = 1000f
private const val MAX_ALPHA = 255

/** A field's cells as soft discs on screen: [cells] are indices into the field's inside cells, far to near, and [positions] holds [VERTICES_PER_SPLAT] vertices of two floats for each, a fan round the cell's centre. Depends on the view, not on the frame. */
internal class FieldSplats(val cells: IntArray, val positions: FloatArray)

internal fun fieldSplatsOf(field: VolumeField, camera: Camera, widthPx: Float, heightPx: Float): FieldSplats {
    val view = camera.viewMatrix()
    val lens = camera.projectionMatrix(widthPx / heightPx)
    val eye = camera.eyePosition()
    val pixelsPerMetreAtOne = heightPx / (2f * tan(camera.fovYRadians / 2))
    val radiusMetres = (field.radiusCells * field.stepMm).toFloat() / MM_PER_METRE
    val centres = field.inside.map { cell -> field.centreOf(cell).let { worldOf(it.xMm, it.yMm, it.zMm) } }
    val distances = FloatArray(centres.size) { (centres[it] - eye).length() }
    val farToNear = centres.indices.sortedByDescending { distances[it] }
    val drawn = ArrayList<Int>(centres.size)
    val positions = FloatArray(centres.size * VERTICES_PER_SPLAT * 2)
    var written = 0
    for (cell in farToNear) {
        val centre = centres[cell].project(view, lens, widthPx, heightPx)
        if (!centre.visible) continue
        val radius = radiusMetres * pixelsPerMetreAtOne / distances[cell]
        for (corner in 0 until RIM_POINTS) {
            val from = 2 * PI.toFloat() * corner / RIM_POINTS
            val to = 2 * PI.toFloat() * (corner + 1) / RIM_POINTS
            val vertices = floatArrayOf(centre.x, centre.y, centre.x + radius * cos(from), centre.y + radius * sin(from), centre.x + radius * cos(to), centre.y + radius * sin(to))
            vertices.copyInto(positions, written)
            written += vertices.size
        }
        drawn.add(cell)
    }
    return FieldSplats(drawn.toIntArray(), positions.copyOf(written))
}

/** The colour of each splat vertex at [frame]: the ramp's colour at the cell's reading, at [VolumeField.opacity] in the middle and clear at the rim; clear throughout where the cell has no reading. */
internal fun fieldSplatColours(field: VolumeField, splats: FieldSplats, frame: Int, ramp: ColourRamp): IntArray {
    val readings = field.frames.getOrNull(frame)
    val centreAlpha = (field.opacity * MAX_ALPHA).toInt().coerceIn(0, MAX_ALPHA)
    val colours = IntArray(splats.cells.size * VERTICES_PER_SPLAT)
    splats.cells.forEachIndexed { splat, cell ->
        val reading = readings?.get(cell)?.takeUnless { it.isNaN() }
        val rgb = if (reading == null) 0 else (rampColourFor(ramp, reading) and 0xFFFFFFL).toInt()
        val centre = if (reading == null) 0 else (centreAlpha shl 24) or rgb
        val rim = rgb
        for (corner in 0 until RIM_POINTS) {
            val first = splat * VERTICES_PER_SPLAT + corner * 3
            colours[first] = centre
            colours[first + 1] = rim
            colours[first + 2] = rim
        }
    }
    return colours
}

internal fun DrawScope.drawFieldSplats(splats: FieldSplats, colours: IntArray) {
    if (splats.cells.isEmpty()) return
    drawIntoCanvas { canvas -> drawTriangleBatch(canvas, splats.positions, colours, splats.cells.size * VERTICES_PER_SPLAT) }
}
