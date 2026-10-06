package ai.factoredui.compose.layout

const val FACING_COUNT = 4

fun normalisedTurns(quarterTurns: Int): Int = ((quarterTurns % FACING_COUNT) + FACING_COUNT) % FACING_COUNT

fun rotatedGridSize(quarterTurns: Int, cols: Int, rows: Int): Pair<Int, Int> =
    if (normalisedTurns(quarterTurns) % 2 == 0) cols to rows else rows to cols

fun rotateGround(point: GroundPoint, quarterTurns: Int, cols: Int, rows: Int): GroundPoint = when (normalisedTurns(quarterTurns)) {
    1 -> GroundPoint(rows - point.y, point.x)
    2 -> GroundPoint(cols - point.x, rows - point.y)
    3 -> GroundPoint(point.y, cols - point.x)
    else -> point
}

fun unrotateGround(point: GroundPoint, quarterTurns: Int, cols: Int, rows: Int): GroundPoint {
    val (rotatedCols, rotatedRows) = rotatedGridSize(quarterTurns, cols, rows)
    return rotateGround(point, FACING_COUNT - normalisedTurns(quarterTurns), rotatedCols, rotatedRows)
}

fun rotatedFacing(worldFacing: Int, quarterTurns: Int): Int = normalisedTurns(worldFacing + quarterTurns)
