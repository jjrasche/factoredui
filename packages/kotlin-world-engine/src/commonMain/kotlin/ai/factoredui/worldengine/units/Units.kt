package ai.factoredui.worldengine.units

import ai.factoredui.worldengine.expression.ExpressionException
import ai.factoredui.worldengine.text.isPythonSpace
import ai.factoredui.worldengine.text.pythonStrip
import kotlin.math.pow

enum class BaseDimension(val label: String) { FT("ft"), LB("lb"), USD("usd"), HOUR("hour"), TILE("tile"), HEAD("head") }

class Dimension private constructor(private val exponents: Map<BaseDimension, Int>) {
    fun exponentOf(base: BaseDimension): Int = exponents[base] ?: 0

    fun combine(other: Dimension, sign: Int): Dimension =
        Dimension(BaseDimension.entries.associateWith { exponentOf(it) + sign * other.exponentOf(it) }.filterValues { it != 0 })

    override fun equals(other: Any?): Boolean = other is Dimension && other.exponents == exponents

    override fun hashCode(): Int = exponents.hashCode()

    override fun toString(): String = describeDimension(this)

    companion object {
        val NONE: Dimension = Dimension(emptyMap())

        fun of(vararg powers: Pair<BaseDimension, Int>): Dimension =
            powers.fold(NONE) { built, (base, power) -> built.combine(Dimension(mapOf(base to power)), 1) }
    }
}

data class UnitDefinition(val factor: Double, val dimension: Dimension)

data class ParsedUnit(val factor: Double, val dimension: Dimension)

val UNIT_TABLE: Map<String, UnitDefinition> = linkedMapOf(
    "ft" to UnitDefinition(1.0, Dimension.of(BaseDimension.FT to 1)),
    "sq_ft" to UnitDefinition(1.0, Dimension.of(BaseDimension.FT to 2)),
    "acre" to UnitDefinition(43560.0, Dimension.of(BaseDimension.FT to 2)),
    "lb" to UnitDefinition(1.0, Dimension.of(BaseDimension.LB to 1)),
    "ton" to UnitDefinition(2000.0, Dimension.of(BaseDimension.LB to 1)),
    "usd" to UnitDefinition(1.0, Dimension.of(BaseDimension.USD to 1)),
    "second" to UnitDefinition(1.0 / 3600.0, Dimension.of(BaseDimension.HOUR to 1)),
    "minute" to UnitDefinition(1.0 / 60.0, Dimension.of(BaseDimension.HOUR to 1)),
    "hour" to UnitDefinition(1.0, Dimension.of(BaseDimension.HOUR to 1)),
    "day" to UnitDefinition(24.0, Dimension.of(BaseDimension.HOUR to 1)),
    "year" to UnitDefinition(8760.0, Dimension.of(BaseDimension.HOUR to 1)),
    "tile" to UnitDefinition(1.0, Dimension.of(BaseDimension.TILE to 1)),
    "head" to UnitDefinition(1.0, Dimension.of(BaseDimension.HEAD to 1)),
)

fun describeDimension(dimension: Dimension): String {
    val parts = BaseDimension.entries.filter { dimension.exponentOf(it) != 0 }.map { describePower(it, dimension.exponentOf(it)) }
    return if (parts.isEmpty()) "dimensionless" else parts.joinToString(" ")
}

private fun describePower(base: BaseDimension, power: Int): String = if (power == 1) base.label else "${base.label}^$power"

fun parseUnit(text: String?): ParsedUnit {
    val cleaned = pythonStrip(text ?: "")
    if (cleaned == "" || cleaned == "1") return ParsedUnit(1.0, Dimension.NONE)
    val parts = scanUnitParts(cleaned) ?: throw ExpressionException("syntax", "unit '$cleaned' is not name(^n) joined by * or /")
    return parts.fold(ParsedUnit(1.0, Dimension.NONE)) { built, part -> applyUnitPart(built, part) }
}

private data class UnitPart(val sign: Int, val name: String, val times: Int)

private fun applyUnitPart(built: ParsedUnit, part: UnitPart): ParsedUnit {
    val definition = UNIT_TABLE[part.name] ?: throw ExpressionException("unknown_word", "unit '${part.name}' is not in the unit table")
    val exponent = part.sign * part.times
    return ParsedUnit(built.factor * definition.factor.pow(exponent), built.dimension.combine(definition.dimension, exponent))
}

private class UnitScanner(private val text: String) {
    var at = 0

    fun isDone(): Boolean = at >= text.length

    fun skipSpace() {
        while (at < text.length && isPythonSpace(text[at])) at++
    }

    fun takeOperator(): Char? {
        val found = text.getOrNull(at)?.takeIf { it == '*' || it == '/' } ?: return null
        at++
        return found
    }

    fun takeName(): String? {
        val start = at
        while (at < text.length && (text[at] in 'a'..'z' || text[at] == '_')) at++
        return if (at > start) text.substring(start, at) else null
    }

    fun takePower(): Int? {
        if (text.getOrNull(at) != '^') return 1
        val start = ++at
        while (at < text.length && text[at].isDigit()) at++
        if (at == start) return null
        return text.substring(start, at).map { it.digitToInt() }.fold(0) { total, digit -> total * 10 + digit }
    }
}

private fun scanUnitParts(cleaned: String): List<UnitPart>? {
    val scanner = UnitScanner(cleaned)
    val first = scanUnitPart(scanner, 1) ?: return null
    val parts = mutableListOf(first)
    while (!scanner.isDone()) {
        scanner.skipSpace()
        val operator = scanner.takeOperator() ?: return null
        scanner.skipSpace()
        parts += scanUnitPart(scanner, if (operator == '/') -1 else 1) ?: return null
    }
    return parts
}

private fun scanUnitPart(scanner: UnitScanner, sign: Int): UnitPart? {
    val name = scanner.takeName() ?: return null
    val times = scanner.takePower() ?: return null
    return UnitPart(sign, name, times)
}
