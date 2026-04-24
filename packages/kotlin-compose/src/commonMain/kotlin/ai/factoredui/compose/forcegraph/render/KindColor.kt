package ai.factoredui.compose.forcegraph.render

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Stable, deterministic color-from-kind mapping. Hashes the kind to an HSL
 * hue at 70% saturation / 55% lightness; visually distinct neighbors and
 * consistent across runs (same kind always gets the same color).
 */
object KindColor {

    fun colorFor(kind: String): Color {
        val hue = (stableHash(kind) and 0xFFFF).toFloat() / 0xFFFF.toFloat() * 360f
        return hslToColor(hue = hue, saturation = 0.7f, lightness = 0.55f)
    }

    /**
     * FNV-1a 32-bit. We use this instead of String.hashCode() because that
     * one is JVM-specific and may change across KMP targets.
     */
    private fun stableHash(input: String): Int {
        var h = 0x811C9DC5.toInt()
        for (ch in input) {
            h = h xor ch.code
            h *= 0x01000193
        }
        return abs(h)
    }

    private fun hslToColor(hue: Float, saturation: Float, lightness: Float): Color {
        val c = (1f - kotlin.math.abs(2f * lightness - 1f)) * saturation
        val hp = hue / 60f
        val x = c * (1f - kotlin.math.abs(hp.mod(2f) - 1f))
        val (r1, g1, b1) = when {
            hp < 1f -> Triple(c, x, 0f)
            hp < 2f -> Triple(x, c, 0f)
            hp < 3f -> Triple(0f, c, x)
            hp < 4f -> Triple(0f, x, c)
            hp < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = lightness - c / 2f
        return Color(
            red = (r1 + m).coerceIn(0f, 1f),
            green = (g1 + m).coerceIn(0f, 1f),
            blue = (b1 + m).coerceIn(0f, 1f),
            alpha = 1f,
        )
    }
}
