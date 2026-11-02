package ai.factoredui.compose.tokens

import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToLong

sealed interface TokenValue

data class ColorValue(val argb: Long) : TokenValue

data class DimensionValue(val px: Double) : TokenValue

data class NumberValue(val value: Double) : TokenValue

data class TypographyValue(val family: String, val sizePx: Double, val weight: Int, val letterSpacingPx: Double, val lineMultiplier: Double) : TokenValue

typealias TokenSet = Map<String, TokenValue>

private fun rgb(hex: Long) = ColorValue(0xFF000000L or hex)

private fun px(value: Int) = DimensionValue(value.toDouble())

private fun type(size: Int, line: Double, weight: Int, family: String = "sans-serif") = TypographyValue(family, size.toDouble(), weight, 0.0, line)

private val SERIES = mapOf(
    "dark" to listOf(0x56B4E9L, 0xE69F00L, 0x2FCB9AL, 0xF0E442L, 0xCC79A7L, 0x8E9BFFL, 0xFF8A4CL, 0xB8C0CCL),
    "light" to listOf(0x0072B2L, 0xD55E00L, 0x007A5AL, 0x8A7B00L, 0xAA4499L, 0x4B3FBFL, 0x0B7285L, 0x4D5560L),
)

private fun colours(mode: String): Map<String, TokenValue> {
    val dark = mode == "dark"
    val base = mapOf(
        "color.ground" to rgb(if (dark) 0x0B1220 else 0xFFFFFF),
        "color.ink" to rgb(if (dark) 0xE8EAEE else 0x111827),
        "color.muted" to rgb(if (dark) 0xA3ACB9 else 0x4B5563),
        "color.accent" to rgb(if (dark) 0x5AB0FF else 0x0B5FCC),
        "color.on-accent" to rgb(if (dark) 0x06111F else 0xFFFFFF),
        "color.status.pass" to rgb(if (dark) 0x3DD68C else 0x0F6F41),
        "color.status.warn" to rgb(if (dark) 0xF5B83D else 0x8A5A00),
        "color.status.fail" to rgb(if (dark) 0xFF7A7A else 0xB3261E),
    )
    return base + SERIES.getValue(mode).mapIndexed { index, hex -> "color.series.${index + 1}" to rgb(hex) }
}

private val SPACE = mapOf("xs" to 4, "sm" to 8, "md" to 12, "lg" to 16, "xl" to 24, "xxl" to 32, "gutter" to 16)

private val RADIUS = mapOf("sm" to 8, "md" to 12, "lg" to 16, "xl" to 24)

private val CONTROL = mapOf("sm" to 36, "md" to 48, "lg" to 56, "xl" to 72)

private val TYPE = mapOf(
    "display" to type(40, 1.2, 600),
    "heading" to type(24, 1.333, 600),
    "subheading" to type(18, 1.333, 600),
    "body" to type(16, 1.5, 400),
    "caption" to type(13, 1.385, 400),
    "label" to type(14, 1.429, 500),
    "mono" to type(16, 1.5, 500, "monospace"),
)

fun defaultTokens(mode: String): TokenSet =
    colours(mode) +
        mapOf("elevation.surface" to NumberValue(0.06), "elevation.raised" to NumberValue(0.10), "elevation.overlay" to NumberValue(0.14)) +
        SPACE.map { (name, value) -> "dimension.space.$name" to px(value) } +
        RADIUS.map { (name, value) -> "dimension.radius.$name" to px(value) } +
        CONTROL.map { (name, value) -> "dimension.control.$name" to px(value) } +
        TYPE.map { (name, value) -> "type.$name" to value }

private fun srgbToLinear(channel: Double): Double = if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

private fun linearToSrgb(channel: Double): Double = if (channel <= 0.0031308) channel * 12.92 else 1.055 * channel.pow(1 / 2.4) - 0.055

private fun channelsOf(argb: Long): List<Double> = listOf(16, 8, 0).map { shift -> srgbToLinear(((argb shr shift) and 0xFF) / 255.0) }

private fun oklabOf(argb: Long): List<Double> {
    val (r, g, b) = channelsOf(argb)
    val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    return listOf(0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s, 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s, 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s)
}

private fun argbOfOklab(lab: List<Double>): Long {
    val (lightness, a, b) = lab
    val l = (lightness + 0.3963377774 * a + 0.2158037573 * b).pow(3)
    val m = (lightness - 0.1055613458 * a - 0.0638541728 * b).pow(3)
    val s = (lightness - 0.0894841775 * a - 1.2914855480 * b).pow(3)
    val linear = listOf(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s, -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s, -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s)
    val channels = linear.map { (linearToSrgb(it.coerceIn(0.0, 1.0)) * 255).roundToLong().coerceIn(0, 255) }
    return 0xFF000000L or (channels[0] shl 16) or (channels[1] shl 8) or channels[2]
}

fun mixColours(from: Long, to: Long, fraction: Double): Long {
    val start = oklabOf(from)
    val end = oklabOf(to)
    return argbOfOklab(start.indices.map { start[it] + (end[it] - start[it]) * fraction })
}

fun relativeLuminance(argb: Long): Double {
    val (r, g, b) = channelsOf(argb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

fun contrastRatio(first: Long, second: Long): Double {
    val (lighter, darker) = listOf(relativeLuminance(first), relativeLuminance(second)).sortedDescending()
    return (lighter + 0.05) / (darker + 0.05)
}
