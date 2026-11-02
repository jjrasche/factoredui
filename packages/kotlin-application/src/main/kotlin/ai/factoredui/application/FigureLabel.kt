package ai.factoredui.application

data class FigureLabel(val text: String, val unit: String?)

private val UNIT_WORDS = mapOf(
    "lb" to "lb", "f" to "°F", "w" to "W", "kw" to "kW", "kwh" to "kWh", "ft" to "ft", "in" to "in", "mm" to "mm", "m" to "m",
    "percent" to "%", "hours" to "h", "hour" to "h", "usd" to "$",
)

private val UNIT_PHRASES = mapOf(listOf("btu", "per", "hour") to "BTU/h")

private fun sentence(words: List<String>): String = words.joinToString(" ").replaceFirstChar { it.uppercase() }

private fun unitAt(end: List<String>): Pair<Int, String>? {
    UNIT_PHRASES.entries.firstOrNull { (phrase, _) -> end.takeLast(phrase.size) == phrase }?.let { (phrase, unit) -> return phrase.size to unit }
    return UNIT_WORDS[end.last()]?.let { 1 to it }
}

fun sentenceCase(name: String): String = sentence(name.lowercase().split('_', '-').filter { it.isNotEmpty() })

fun figureLabelOf(name: String): FigureLabel {
    val words = name.lowercase().split('_', '-').filter { it.isNotEmpty() }
    val unit = if (words.size > 1) unitAt(words) else null
    return if (unit == null) FigureLabel(sentence(words), null) else FigureLabel(sentence(words.dropLast(unit.first)), unit.second)
}

private fun groupedFigure(value: Double): String = if (value % 1.0 == 0.0) "%,d".format(value.toLong()) else "%,.3f".format(value).trimEnd('0').trimEnd('.')

fun figureText(value: Double, unit: String?): String = when (unit) {
    null -> groupedFigure(value)
    "$" -> "$" + groupedFigure(value)
    else -> "${groupedFigure(value)} $unit"
}

private val SNAKE_WORD = Regex("[a-z][a-z0-9]*(?:_[a-z0-9]+)+")

fun readableText(text: String): String = SNAKE_WORD.replace(text) { it.value.replace('_', ' ') }