package ai.factoredui.application

internal enum class ChainSide { BELOW, ABOVE, LEFT, RIGHT }

private const val CHAIN_TICK_MM = 80.0
private const val SAME_BREAK_MM = 1.0
private const val LABELLED_SEGMENT_MM = 450.0
private const val LABEL_BESIDE_DP = 8
private const val LABEL_BELOW_DP = 12
private const val LABEL_ABOVE_DP = -4

/** How far a chain of dimensions stands off the drawing; the overall dimension stands beyond it. */
internal const val CHAIN_OFFSET_MM = 220.0

internal fun distinctBreaks(values: List<Double>): List<Double> =
    values.sorted().fold(emptyList()) { kept, value -> if (kept.isNotEmpty() && value - kept.last() < SAME_BREAK_MM) kept else kept + value }

private fun labelAlign(side: ChainSide) = when (side) { ChainSide.LEFT -> "end"; ChainSide.RIGHT -> "start"; else -> "center" }

private fun labelOffsets(side: ChainSide): Pair<Int, Int> = when (side) {
    ChainSide.BELOW -> 0 to LABEL_BELOW_DP
    ChainSide.ABOVE -> 0 to LABEL_ABOVE_DP
    ChainSide.LEFT -> -LABEL_BESIDE_DP to 0
    ChainSide.RIGHT -> LABEL_BESIDE_DP to 0
}

/** A chain of dimensions, one string in feet and inches for each stretch between consecutive breaks, drawn at [lineAt] on the given side; a stretch too short to hold its string has none. */
internal fun chainShapes(prefix: String, rawBreaks: List<Double>, side: ChainSide, lineAt: Double): List<Map<String, Any?>> {
    val breaks = distinctBreaks(rawBreaks)
    if (breaks.size < 2) return emptyList()
    val isAlongX = side == ChainSide.BELOW || side == ChainSide.ABOVE
    fun x(along: Double, across: Double) = if (isAlongX) along else across
    fun y(along: Double, across: Double) = if (isAlongX) across else along
    val (dx, dy) = labelOffsets(side)
    val line = lineShape("$prefix/line", "dimension", x(breaks.first(), lineAt), y(breaks.first(), lineAt), x(breaks.last(), lineAt), y(breaks.last(), lineAt))
    val ticks = breaks.mapIndexed { index, at -> lineShape("$prefix/tick/$index", "dimension", x(at, lineAt - CHAIN_TICK_MM), y(at, lineAt - CHAIN_TICK_MM), x(at, lineAt + CHAIN_TICK_MM), y(at, lineAt + CHAIN_TICK_MM)) }
    val labels = breaks.zipWithNext().mapIndexedNotNull { index, (from, to) ->
        if (to - from < LABELLED_SEGMENT_MM) null
        else mapOf("id" to "$prefix/text/$index", "kind" to "label", "style" to "dimension", "x_mm" to x((from + to) / 2, lineAt), "y_mm" to y((from + to) / 2, lineAt), "text" to feetInches(to - from), "align" to labelAlign(side), "dx_dp" to dx, "dy_dp" to dy)
    }
    return listOf(line) + ticks + labels
}
