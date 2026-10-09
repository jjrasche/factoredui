package ai.factoredui.compose.structure

import kotlin.math.abs
import kotlin.math.floor

const val FOOT_MARK = "′"
const val INCH_MARK = "″"
const val MISSING_FIGURE = "–"

private const val MM_PER_INCH = 25.4
private const val INCHES_PER_FOOT = 12
private const val SIXTEENTHS = 16

/** JavaScript's Math.round: halves go up. kotlin.math.round sends them to the even neighbour, which reads 1/32 in as 0 in. */
internal fun roundHalfUp(value: Double): Long = floor(value + 0.5).toLong()

private fun reducedFraction(sixteenths: Long): String {
    var top = sixteenths
    var bottom = SIXTEENTHS.toLong()
    while (top % 2 == 0L && bottom > 1) {
        top /= 2
        bottom /= 2
    }
    return "$top/$bottom"
}

private fun inchesText(wholeInches: Long, sixteenths: Long, hasFeet: Boolean): String {
    if (sixteenths == 0L) return "$wholeInches"
    val fraction = reducedFraction(sixteenths)
    return if (wholeInches == 0L && !hasFeet) fraction else "$wholeInches $fraction"
}

fun feetInches(millimetres: Double?): String {
    if (millimetres == null || !millimetres.isFinite()) return MISSING_FIGURE
    val sign = if (millimetres < 0) "-" else ""
    val totalSixteenths = roundHalfUp(abs(millimetres) / MM_PER_INCH * SIXTEENTHS)
    val totalInches = totalSixteenths / SIXTEENTHS
    val sixteenths = totalSixteenths % SIXTEENTHS
    val feet = totalInches / INCHES_PER_FOOT
    val inches = totalInches % INCHES_PER_FOOT
    val inchPart = "${inchesText(inches, sixteenths, feet > 0)}$INCH_MARK"
    return if (feet == 0L) "$sign$inchPart" else "$sign$feet$FOOT_MARK $inchPart"
}
