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
private const val SOFT_VERTICES = RIM_POINTS * 3
private const val CELL_VERTICES = 6
private const val MM_PER_METRE = 1000f
private const val MAX_ALPHA = 255
private const val CELL_OVERLAP_PX = 0.3f

/** A field's cells on screen: [cells] are indices into the field's inside cells, far to near, and [positions] holds [verticesPerSplat] vertices of two floats for each: a fan of soft disc round the cell's centre, or two triangles making a solid square. Depends on the view, not on the frame. */
internal class FieldSplats(val cells: IntArray, val positions: FloatArray, val verticesPerSplat: Int)

private fun pixelsPerMetre(camera: Camera, heightPx: Float, distanceMetres: Float): Float =
    camera.orthoHalfHeight?.let { heightPx / (2f * it) } ?: (heightPx / (2f * tan(camera.fovYRadians / 2)) / distanceMetres)

private fun discVertices(centreX: Float, centreY: Float, radius: Float): FloatArray = FloatArray(SOFT_VERTICES * 2).also { out ->
    for (corner in 0 until RIM_POINTS) {
        val from = 2 * PI.toFloat() * corner / RIM_POINTS
        val to = 2 * PI.toFloat() * (corner + 1) / RIM_POINTS
        floatArrayOf(centreX, centreY, centreX + radius * cos(from), centreY + radius * sin(from), centreX + radius * cos(to), centreY + radius * sin(to)).copyInto(out, corner * 6)
    }
}

private fun squareVertices(centreX: Float, centreY: Float, half: Float): FloatArray {
    val left = centreX - half
    val right = centreX + half
    val top = centreY - half
    val bottom = centreY + half
    return floatArrayOf(left, top, right, top, right, bottom, left, top, right, bottom, left, bottom)
}

internal fun fieldSplatsOf(field: VolumeField, camera: Camera, widthPx: Float, heightPx: Float): FieldSplats {
    val view = camera.viewMatrix()
    val lens = camera.projectionMatrix(widthPx / heightPx)
    val eye = camera.eyePosition()
    val extentMetres = (if (field.isCells) field.stepMm / 2 else field.radiusCells * field.stepMm).toFloat() / MM_PER_METRE
    val verticesPerSplat = if (field.isCells) CELL_VERTICES else SOFT_VERTICES
    val centres = field.inside.map { cell -> field.centreOf(cell).let { worldOf(it.xMm, it.yMm, it.zMm) } }
    val distances = FloatArray(centres.size) { (centres[it] - eye).length() }
    val farToNear = centres.indices.sortedByDescending { distances[it] }
    val drawn = ArrayList<Int>(centres.size)
    val positions = FloatArray(centres.size * verticesPerSplat * 2)
    var written = 0
    for (cell in farToNear) {
        val centre = centres[cell].project(view, lens, widthPx, heightPx)
        if (!centre.visible) continue
        val extent = extentMetres * pixelsPerMetre(camera, heightPx, distances[cell])
        val vertices = if (field.isCells) squareVertices(centre.x, centre.y, extent + CELL_OVERLAP_PX) else discVertices(centre.x, centre.y, extent)
        vertices.copyInto(positions, written)
        written += vertices.size
        drawn.add(cell)
    }
    return FieldSplats(drawn.toIntArray(), positions.copyOf(written), verticesPerSplat)
}

/** The colour of each splat vertex at [frame]: the ramp's colour at the cell's reading. A soft disc is at [VolumeField.opacity] in the middle and clear at the rim; a square is at it throughout. Clear throughout where the cell has no reading. */
internal fun fieldSplatColours(field: VolumeField, splats: FieldSplats, frame: Int, ramp: ColourRamp): IntArray {
    val readings = field.frames.getOrNull(frame)
    val centreAlpha = (field.opacity * MAX_ALPHA).toInt().coerceIn(0, MAX_ALPHA)
    val colours = IntArray(splats.cells.size * splats.verticesPerSplat)
    splats.cells.forEachIndexed { splat, cell ->
        val reading = readings?.get(cell)?.takeUnless { it.isNaN() }
        val rgb = if (reading == null) 0 else (rampColourFor(ramp, reading) and 0xFFFFFFL).toInt()
        val solid = if (reading == null) 0 else (centreAlpha shl 24) or rgb
        val first = splat * splats.verticesPerSplat
        if (field.isCells) {
            colours.fill(solid, first, first + CELL_VERTICES)
        } else {
            for (corner in 0 until RIM_POINTS) {
                colours[first + corner * 3] = solid
                colours[first + corner * 3 + 1] = rgb
                colours[first + corner * 3 + 2] = rgb
            }
        }
    }
    return colours
}

internal fun DrawScope.drawFieldSplats(splats: FieldSplats, colours: IntArray) {
    if (splats.cells.isEmpty()) return
    drawIntoCanvas { canvas -> drawTriangleBatch(canvas, splats.positions, colours, splats.cells.size * splats.verticesPerSplat) }
}
