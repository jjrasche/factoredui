package ai.factoredui.compose.scene3d

import kotlin.math.cos
import kotlin.math.sin

internal fun rollingHillsMesh(cellsPerSide: Int): Scene3dMesh {
    val extent = 20f
    val vertsPerSide = cellsPerSide + 1
    val vertices = ArrayList<Float>(vertsPerSide * vertsPerSide * 3)
    for (iz in 0 until vertsPerSide) {
        for (ix in 0 until vertsPerSide) {
            val x = (ix.toFloat() / cellsPerSide - 0.5f) * extent
            val z = (iz.toFloat() / cellsPerSide - 0.5f) * extent
            vertices.add(x)
            vertices.add(hillHeight(x, z))
            vertices.add(z)
        }
    }
    val triColors = ArrayList<String>(cellsPerSide * cellsPerSide * 2)
    for (iz in 0 until cellsPerSide) {
        for (ix in 0 until cellsPerSide) {
            val x = (ix.toFloat() / cellsPerSide - 0.5f) * extent
            val z = (iz.toFloat() / cellsPerSide - 0.5f) * extent
            val tone = ((hillHeight(x, z) + 1.6f) / 3.2f).coerceIn(0f, 1f)
            triColors.add(hillColor(tone, shade = 1.0f))
            triColors.add(hillColor(tone, shade = 0.92f))
        }
    }
    return Scene3dMesh(
        vertices = vertices,
        triColors = triColors,
        gridCellsX = cellsPerSide,
        gridCellsZ = cellsPerSide,
    )
}

private fun hillHeight(x: Float, z: Float): Float =
    1.2f * sin(x * 0.45f) * cos(z * 0.35f) + 0.4f * sin(x * 1.3f + z * 0.9f)

private fun hillColor(tone: Float, shade: Float): String {
    val red = ((60f + tone * 90f) * shade).toInt()
    val green = ((95f + tone * 110f) * shade).toInt()
    val blue = (52f * shade).toInt()
    return channelHex(red) + channelHex(green) + channelHex(blue)
}

private fun channelHex(value: Int): String = value.coerceIn(0, 255).toString(16).padStart(2, '0')
