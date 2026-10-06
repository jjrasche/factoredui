package ai.factoredui.compose.terrain

import ai.factoredui.compose.layout.GroundPoint

const val INDEX_CONTOUR_EVERY = 5

class ContourLine(val levelMm: Long, val points: List<GroundPoint>, val isClosed: Boolean)

fun contourLevels(minMm: Int, maxMm: Int, intervalMm: Int): List<Long> {
    require(intervalMm > 0) { "a contour interval must be positive, got $intervalMm" }
    val first = minMm.toLong().floorDiv(intervalMm.toLong()) + 1
    val last = (maxMm.toLong() - 1).floorDiv(intervalMm.toLong())
    return (first..last).map { it * intervalMm }
}

fun isIndexContour(levelMm: Long, intervalMm: Int): Boolean = (levelMm / intervalMm) % INDEX_CONTOUR_EVERY == 0L

fun labelAnchor(line: ContourLine): GroundPoint =
    if (line.isClosed) line.points.minWith(compareBy<GroundPoint>({ it.y }, { it.x })) else line.points[line.points.size / 2]

class ContourLabelPlacement(val line: ContourLine, val anchor: GroundPoint)

fun contourLabelPlacements(lines: List<ContourLine>, intervalMm: Int, minPoints: Int, minSpacingTiles: Float): List<ContourLabelPlacement> {
    val placed = mutableListOf<ContourLabelPlacement>()
    for (line in lines) {
        if (!isIndexContour(line.levelMm, intervalMm) || line.points.size < minPoints) continue
        val anchor = labelAnchor(line)
        if (placed.none { isWithin(it.anchor, anchor, minSpacingTiles) }) placed += ContourLabelPlacement(line, anchor)
    }
    return placed
}

private fun isWithin(first: GroundPoint, second: GroundPoint, distance: Float): Boolean {
    val dx = first.x - second.x
    val dy = first.y - second.y
    return dx * dx + dy * dy < distance * distance
}

fun contourPolylines(grid: TerrainGrid, intervalMm: Int): List<ContourLine> =
    contourLevels(grid.minMm, grid.maxMm, intervalMm).flatMap { level -> ContourTracer(grid, level).polylines() }

private enum class CellEdge { NORTH, EAST, SOUTH, WEST }

private const val NORTH_WEST_BIT = 8
private const val NORTH_EAST_BIT = 4
private const val SOUTH_EAST_BIT = 2
private const val SOUTH_WEST_BIT = 1

private fun edgePairsFor(case: Int, isCentreAbove: Boolean): List<Pair<CellEdge, CellEdge>> = when (case) {
    1, 14 -> listOf(CellEdge.WEST to CellEdge.SOUTH)
    2, 13 -> listOf(CellEdge.SOUTH to CellEdge.EAST)
    3, 12 -> listOf(CellEdge.WEST to CellEdge.EAST)
    4, 11 -> listOf(CellEdge.NORTH to CellEdge.EAST)
    6, 9 -> listOf(CellEdge.NORTH to CellEdge.SOUTH)
    7, 8 -> listOf(CellEdge.WEST to CellEdge.NORTH)
    5 -> if (isCentreAbove) listOf(CellEdge.WEST to CellEdge.NORTH, CellEdge.SOUTH to CellEdge.EAST) else listOf(CellEdge.NORTH to CellEdge.EAST, CellEdge.WEST to CellEdge.SOUTH)
    10 -> if (isCentreAbove) listOf(CellEdge.NORTH to CellEdge.EAST, CellEdge.WEST to CellEdge.SOUTH) else listOf(CellEdge.WEST to CellEdge.NORTH, CellEdge.SOUTH to CellEdge.EAST)
    else -> emptyList()
}

private class ContourTracer(private val grid: TerrainGrid, private val levelMm: Long) {
    private val neighbours = HashMap<Int, MutableList<Int>>()

    fun polylines(): List<ContourLine> {
        linkCrossings()
        val visited = HashSet<Int>()
        val openStarts = neighbours.filterValues { it.size == 1 }.keys.sorted()
        val open = openStarts.mapNotNull { start -> if (start in visited) null else trace(start, visited) }
        val closed = neighbours.keys.sorted().mapNotNull { start -> if (start in visited) null else trace(start, visited) }
        return open + closed
    }

    private fun isAbove(vertexCol: Int, vertexRow: Int): Boolean = grid.heightAt(vertexCol, vertexRow) >= levelMm

    private fun linkCrossings() {
        for (row in 0 until grid.rows) {
            for (col in 0 until grid.cols) {
                edgePairsFor(caseOf(col, row), isCentreAbove(col, row)).forEach { (from, to) -> link(edgeKey(col, row, from), edgeKey(col, row, to)) }
            }
        }
    }

    private fun caseOf(col: Int, row: Int): Int =
        (if (isAbove(col, row)) NORTH_WEST_BIT else 0) or
            (if (isAbove(col + 1, row)) NORTH_EAST_BIT else 0) or
            (if (isAbove(col + 1, row + 1)) SOUTH_EAST_BIT else 0) or
            (if (isAbove(col, row + 1)) SOUTH_WEST_BIT else 0)

    private fun isCentreAbove(col: Int, row: Int): Boolean {
        val cornerSum = grid.heightAt(col, row).toLong() + grid.heightAt(col + 1, row) + grid.heightAt(col + 1, row + 1) + grid.heightAt(col, row + 1)
        return cornerSum >= 4 * levelMm
    }

    private fun link(from: Int, to: Int) {
        neighbours.getOrPut(from) { mutableListOf() }.add(to)
        neighbours.getOrPut(to) { mutableListOf() }.add(from)
    }

    private fun horizontalKey(col: Int, row: Int): Int = 2 * (row * grid.cols + col)

    private fun verticalKey(col: Int, row: Int): Int = 2 * (row * grid.vertexCols + col) + 1

    private fun edgeKey(col: Int, row: Int, edge: CellEdge): Int = when (edge) {
        CellEdge.NORTH -> horizontalKey(col, row)
        CellEdge.SOUTH -> horizontalKey(col, row + 1)
        CellEdge.WEST -> verticalKey(col, row)
        CellEdge.EAST -> verticalKey(col + 1, row)
    }

    private fun trace(start: Int, visited: MutableSet<Int>): ContourLine {
        val keys = mutableListOf(start)
        visited.add(start)
        var previous = -1
        var current = start
        var isClosed = false
        while (true) {
            val next = neighbours.getValue(current).firstOrNull { it != previous && (it == start || it !in visited) } ?: break
            if (next == start) {
                isClosed = true
                break
            }
            keys.add(next)
            visited.add(next)
            previous = current
            current = next
        }
        return ContourLine(levelMm, keys.map { crossingOf(it) }, isClosed)
    }

    private fun crossingOf(key: Int): GroundPoint {
        val index = key / 2
        return if (key % 2 == 0) {
            val col = index % grid.cols
            val row = index / grid.cols
            GroundPoint(col + crossingFraction(grid.heightAt(col, row), grid.heightAt(col + 1, row)), row.toFloat())
        } else {
            val col = index % grid.vertexCols
            val row = index / grid.vertexCols
            GroundPoint(col.toFloat(), row + crossingFraction(grid.heightAt(col, row), grid.heightAt(col, row + 1)))
        }
    }

    private fun crossingFraction(fromMm: Int, toMm: Int): Float = ((levelMm - fromMm).toDouble() / (toMm - fromMm)).toFloat()
}
