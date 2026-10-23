package ai.factoredui.compose.layout

enum class SheetStop(val wire: String) {
    PEEK("peek"),
    HALF("half"),
    FULL("full"),
    ;

    fun up(): SheetStop = entries[minOf(ordinal + 1, entries.lastIndex)]

    fun down(): SheetStop = entries[maxOf(ordinal - 1, 0)]

    companion object {
        fun named(wire: String?): SheetStop? = entries.firstOrNull { it.wire == wire }
    }
}

data class SheetProps(val stop: SheetStop, val peekDp: Double, val halfDp: Double, val sideDp: Double, val notices: List<String>)

private const val DEFAULT_PEEK_DP = 96.0
private const val DEFAULT_HALF_DP = 320.0
private const val DEFAULT_SIDE_DP = 360.0

private fun sizeOf(resolved: Map<String, Any?>, key: String, default: Double, notices: MutableList<String>): Double {
    if (resolved[key] == null) return default
    val given = (resolved[key] as? Number)?.toDouble()
    if (given != null && given > 0.0) return given
    val shown = if (given != null) given.toString().removeSuffix(".0") else resolved[key].toString()
    notices += "sheet $key must be a number above zero, not '$shown'; ${default.toInt()} is used."
    return default
}

fun readSheetProps(resolved: Map<String, Any?>): SheetProps {
    val notices = mutableListOf<String>()
    val given = resolved["stop"] as? String
    val named = SheetStop.named(given)
    if (resolved["stop"] != null && named == null) notices += "sheet stop must be peek, half or full, not '${resolved["stop"]}'; peek is used."
    return SheetProps(
        stop = named ?: SheetStop.PEEK,
        peekDp = sizeOf(resolved, "peek_height", DEFAULT_PEEK_DP, notices),
        halfDp = sizeOf(resolved, "half_height", DEFAULT_HALF_DP, notices),
        sideDp = sizeOf(resolved, "side_width", DEFAULT_SIDE_DP, notices),
        notices = notices,
    )
}
