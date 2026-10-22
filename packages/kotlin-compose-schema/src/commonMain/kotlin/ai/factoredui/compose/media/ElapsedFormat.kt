package ai.factoredui.compose.media

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600

private fun twoDigits(value: Int): String = value.toString().padStart(2, '0')

fun formatElapsed(seconds: Double): String {
    if (seconds.isNaN() || seconds.isInfinite()) return "–"
    val whole = seconds.toInt().coerceAtLeast(0)
    val hours = whole / SECONDS_PER_HOUR
    val minutes = whole % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val rest = twoDigits(whole % SECONDS_PER_MINUTE)
    return if (hours > 0) "$hours:${twoDigits(minutes)}:$rest" else "$minutes:$rest"
}
