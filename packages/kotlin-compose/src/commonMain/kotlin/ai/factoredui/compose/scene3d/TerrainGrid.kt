package ai.factoredui.compose.scene3d

import ai.factoredui.compose.math.Matrix4
import ai.factoredui.compose.math.Vec3
import kotlin.math.abs
import kotlin.math.min

internal const val TERRAIN_CHUNK_CELL_SPAN = 64

// Batched-painter terrain: buffers prebuilt once per edit, scratch reused per frame,
// far-to-near grid traversal instead of a per-frame sort (plot-twin Q-003 memo).
// Assumes a regular axis-aligned grid: vertex (ix, iz) at index iz*(cellsX+1)+ix.
class TerrainGrid(
    val cellsX: Int,
    val cellsZ: Int,
    packedVertices: List<Float>,
    val cellColors: IntArray,
) {
    val vertexCountX = cellsX + 1
    val vertexCountZ = cellsZ + 1
    private val vertexCount = vertexCountX * vertexCountZ

    internal val worldX = FloatArray(vertexCount) { packedVertices[it * 3] }
    internal val worldY = FloatArray(vertexCount) { packedVertices[it * 3 + 1] }
    internal val worldZ = FloatArray(vertexCount) { packedVertices[it * 3 + 2] }

    internal val columnX = FloatArray(vertexCountX) { worldX[it] }
    internal val rowZ = FloatArray(vertexCountZ) { worldZ[it * vertexCountX] }

    val chunksX = (cellsX + TERRAIN_CHUNK_CELL_SPAN - 1) / TERRAIN_CHUNK_CELL_SPAN
    val chunksZ = (cellsZ + TERRAIN_CHUNK_CELL_SPAN - 1) / TERRAIN_CHUNK_CELL_SPAN
    val chunks: List<TerrainChunk> = buildChunkGrid()

    internal val screenX = FloatArray(vertexCount)
    internal val screenY = FloatArray(vertexCount)
    internal val vertexVisible = BooleanArray(vertexCount)
    internal val batchPositions = FloatArray(TERRAIN_CHUNK_CELL_SPAN * TERRAIN_CHUNK_CELL_SPAN * 2 * 6)
    internal val batchColors = IntArray(TERRAIN_CHUNK_CELL_SPAN * TERRAIN_CHUNK_CELL_SPAN * 2 * 3)

    private fun buildChunkGrid(): List<TerrainChunk> {
        val grid = ArrayList<TerrainChunk>(chunksX * chunksZ)
        for (chunkZ in 0 until chunksZ) {
            for (chunkX in 0 until chunksX) {
                val startX = chunkX * TERRAIN_CHUNK_CELL_SPAN
                val startZ = chunkZ * TERRAIN_CHUNK_CELL_SPAN
                grid.add(
                    TerrainChunk(
                        cellStartX = startX,
                        cellStartZ = startZ,
                        cellCountX = min(TERRAIN_CHUNK_CELL_SPAN, cellsX - startX),
                        cellCountZ = min(TERRAIN_CHUNK_CELL_SPAN, cellsZ - startZ),
                    ),
                )
            }
        }
        return grid
    }
}

class TerrainChunk(
    val cellStartX: Int,
    val cellStartZ: Int,
    val cellCountX: Int,
    val cellCountZ: Int,
)

internal fun TerrainGrid.projectVertices(base: Vec3, viewProjection: Matrix4, width: Float, height: Float) {
    val m = viewProjection.m
    for (vertex in worldX.indices) {
        val x = worldX[vertex] + base.x
        val y = worldY[vertex] + base.y
        val z = worldZ[vertex] + base.z
        val clipW = m[3] * x + m[7] * y + m[11] * z + m[15]
        if (clipW <= Vec3.EPSILON) {
            vertexVisible[vertex] = false
            continue
        }
        val clipX = m[0] * x + m[4] * y + m[8] * z + m[12]
        val clipY = m[1] * x + m[5] * y + m[9] * z + m[13]
        screenX[vertex] = (clipX / clipW * 0.5f + 0.5f) * width
        screenY[vertex] = (1f - (clipY / clipW * 0.5f + 0.5f)) * height
        vertexVisible[vertex] = true
    }
}

internal inline fun forEachGridIndexFarToNear(
    countX: Int,
    countZ: Int,
    eyeX: Float,
    eyeZ: Float,
    centerX: Float,
    centerZ: Float,
    action: (ix: Int, iz: Int) -> Unit,
) {
    val xAscending = eyeX >= centerX
    val zAscending = eyeZ >= centerZ
    val zOuter = abs(eyeZ - centerZ) >= abs(eyeX - centerX)
    if (zOuter) {
        for (stepZ in 0 until countZ) {
            val iz = if (zAscending) stepZ else countZ - 1 - stepZ
            for (stepX in 0 until countX) {
                action(if (xAscending) stepX else countX - 1 - stepX, iz)
            }
        }
    } else {
        for (stepX in 0 until countX) {
            val ix = if (xAscending) stepX else countX - 1 - stepX
            for (stepZ in 0 until countZ) {
                action(ix, if (zAscending) stepZ else countZ - 1 - stepZ)
            }
        }
    }
}

internal inline fun TerrainGrid.forEachChunkFarToNear(
    eyeLocalX: Float,
    eyeLocalZ: Float,
    action: (TerrainChunk) -> Unit,
) {
    val centerX = (columnX[0] + columnX[vertexCountX - 1]) * 0.5f
    val centerZ = (rowZ[0] + rowZ[vertexCountZ - 1]) * 0.5f
    forEachGridIndexFarToNear(chunksX, chunksZ, eyeLocalX, eyeLocalZ, centerX, centerZ) { chunkX, chunkZ ->
        action(chunks[chunkZ * chunksX + chunkX])
    }
}

internal fun TerrainGrid.fillChunkBatch(chunk: TerrainChunk, eyeLocalX: Float, eyeLocalZ: Float): Int {
    val chunkCenterX = (columnX[chunk.cellStartX] + columnX[chunk.cellStartX + chunk.cellCountX]) * 0.5f
    val chunkCenterZ = (rowZ[chunk.cellStartZ] + rowZ[chunk.cellStartZ + chunk.cellCountZ]) * 0.5f
    var write = 0
    forEachGridIndexFarToNear(
        chunk.cellCountX, chunk.cellCountZ, eyeLocalX, eyeLocalZ, chunkCenterX, chunkCenterZ,
    ) { stepX, stepZ ->
        val cellX = chunk.cellStartX + stepX
        val cellZ = chunk.cellStartZ + stepZ
        val v00 = cellZ * vertexCountX + cellX
        val v10 = v00 + 1
        val v01 = v00 + vertexCountX
        val v11 = v01 + 1
        val cell = cellZ * cellsX + cellX
        write = writeTriangle(write, v00, v10, v11, cellColors[cell * 2])
        write = writeTriangle(write, v00, v11, v01, cellColors[cell * 2 + 1])
    }
    return write
}

private fun TerrainGrid.writeTriangle(writeVertex: Int, a: Int, b: Int, c: Int, color: Int): Int {
    if (!vertexVisible[a] || !vertexVisible[b] || !vertexVisible[c]) return writeVertex
    var position = writeVertex * 2
    batchPositions[position++] = screenX[a]
    batchPositions[position++] = screenY[a]
    batchPositions[position++] = screenX[b]
    batchPositions[position++] = screenY[b]
    batchPositions[position++] = screenX[c]
    batchPositions[position] = screenY[c]
    batchColors[writeVertex] = color
    batchColors[writeVertex + 1] = color
    batchColors[writeVertex + 2] = color
    return writeVertex + 3
}
