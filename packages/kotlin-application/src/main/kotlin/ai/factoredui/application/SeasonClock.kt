package ai.factoredui.application

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor

private const val MINUTES_PER_DAY = 1440.0
private val UNIT_MINUTES = mapOf("minute" to 1.0, "minutes" to 1.0, "hour" to 60.0, "hours" to 60.0, "day" to MINUTES_PER_DAY, "days" to MINUTES_PER_DAY)
private val DATE_TIME = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH)

internal class SeasonClock(val stepMinutes: Double?, val start: LocalDateTime?)

private fun startOf(text: String?): LocalDateTime? {
    if (text == null) return null
    return runCatching { LocalDateTime.parse(text) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(text).toLocalDateTime() }.getOrNull()
        ?: runCatching { LocalDate.parse(text).atStartOfDay() }.getOrNull()
}

internal fun clockOf(season: JsonObject): SeasonClock {
    val clock = season["clock"] as? JsonObject ?: return SeasonClock(null, null)
    val step = (clock["step"] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
    val unit = (clock["unit"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    val minutes = if (step != null && unit != null) UNIT_MINUTES[unit]?.times(step) else null
    return SeasonClock(minutes, startOf((clock["start"] as? JsonPrimitive)?.takeIf { it.isString }?.content))
}

private fun twoDigits(value: Int) = value.toString().padStart(2, '0')

internal fun stepLabel(clock: SeasonClock, step: Double): String {
    val minutesPer = clock.stepMinutes ?: return "Step ${figureText(step, null)}"
    val elapsed = step * minutesPer
    clock.start?.let { return it.plusMinutes(elapsed.toLong()).format(DATE_TIME) }
    val day = floor(elapsed / MINUTES_PER_DAY).toInt()
    val intoDay = (elapsed - day * MINUTES_PER_DAY).toInt()
    return "Day ${day + 1}, ${twoDigits(intoDay / 60)}:${twoDigits(intoDay % 60)}"
}
