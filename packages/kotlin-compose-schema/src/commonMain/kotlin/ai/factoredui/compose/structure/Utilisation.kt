package ai.factoredui.compose.structure

import ai.factoredui.compose.scene.SceneView
import kotlin.math.abs

data class UtilisationFigure(val value: Double, val text: String, val source: String?)

enum class UtilisationClass { LITTLE, CARRYING, FAILS }

data class FaceShades(val top: Long, val west: Long, val south: Long)

data class LegendStop(val value: Double?, val colour: Long, val label: String)

private const val UTILISATION_KIND = "utilisation"
private const val FOOTPRINT_REF = "footprint"
private const val LITTLE_BELOW = 0.25
private const val AT_CAPACITY = 1.0
private const val GREEN_HUE = 120.0
private const val FAIL_HUE = 300.0
private const val NO_FIGURE_HUE = 210.0
private const val PERCENT = 100
private val LEGEND_VALUES = listOf(0.0, 0.25, 0.5, 0.75, 1.0)

private const val TOP_LIGHTNESS = 52.0
private const val WEST_LIGHTNESS = 42.0
private const val SOUTH_LIGHTNESS = 32.0

val FAIL_COLOUR: Long = hslToArgb(FAIL_HUE, 75.0, 28.0)

val NO_FIGURE_COLOUR: Long = hslToArgb(NO_FIGURE_HUE, 8.0, WEST_LIGHTNESS + 13)

fun utilisationByPart(scene: SceneView): Map<String, UtilisationFigure> {
    val found = LinkedHashMap<String, UtilisationFigure>()
    scene.annotations?.items.orEmpty()
        .filter { it.kind == UTILISATION_KIND && it.refKind.lowercase() == FOOTPRINT_REF && it.refId != null && it.value?.isFinite() == true }
        .forEach { found[it.refId!!] = UtilisationFigure(it.value!!, it.text, it.source) }
    return found
}

fun classifyUtilisation(value: Double): UtilisationClass = when {
    value > AT_CAPACITY -> UtilisationClass.FAILS
    value < LITTLE_BELOW -> UtilisationClass.LITTLE
    else -> UtilisationClass.CARRYING
}

fun percentText(value: Double): String = "${roundHalfUp(value * PERCENT)}%"

fun plainText(text: String): String = text.replace("%%", "%")

/** utilisation.js rounds the hue to a tenth of a degree before colouring; kept so the two pages agree channel for channel. */
private fun rampHue(value: Double): Double = roundHalfUp(GREEN_HUE * (1 - value.coerceAtLeast(0.0) / AT_CAPACITY) * 10) / 10.0

fun utilisationColour(value: Double?): Long = when {
    value == null -> NO_FIGURE_COLOUR
    value > AT_CAPACITY -> FAIL_COLOUR
    else -> hslToArgb(rampHue(value), 70.0, 42.0)
}

private fun shadesOf(hue: Double, saturation: Double, lightnessOffset: Double): FaceShades = FaceShades(
    top = hslToArgb(hue, saturation, TOP_LIGHTNESS + lightnessOffset),
    west = hslToArgb(hue, saturation, WEST_LIGHTNESS + lightnessOffset),
    south = hslToArgb(hue, saturation, SOUTH_LIGHTNESS + lightnessOffset),
)

fun utilisationShades(value: Double?): FaceShades = when {
    value == null -> shadesOf(NO_FIGURE_HUE, 8.0, 13.0)
    value > AT_CAPACITY -> shadesOf(FAIL_HUE, 75.0, -4.0)
    else -> shadesOf(rampHue(value), 70.0, 0.0)
}

fun legendStops(): List<LegendStop> =
    LEGEND_VALUES.map { LegendStop(it, utilisationColour(it), percentText(it)) } + LegendStop(null, FAIL_COLOUR, "over 100% fails")

/** The CSS Color 4 hsl() conversion, channels rounded to the nearest byte as browsers do. */
fun hslToArgb(hueDeg: Double, saturationPercent: Double, lightnessPercent: Double): Long {
    val saturation = saturationPercent / 100
    val lightness = lightnessPercent / 100
    val chroma = (1 - abs(2 * lightness - 1)) * saturation
    fun channel(offset: Double): Long {
        val turn = (offset + hueDeg / 30).mod(12.0)
        val level = lightness - chroma / 2 * maxOf(-1.0, minOf(turn - 3, 9 - turn, 1.0))
        return roundHalfUp(level * 255).coerceIn(0, 255)
    }
    return (0xFFL shl 24) or (channel(0.0) shl 16) or (channel(8.0) shl 8) or channel(4.0)
}
