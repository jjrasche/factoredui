package ai.factoredui.compose.renderer

import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class TextToken(val size: Int, val line: Int, val weight: Int = 400)

data class TypeScale(
    val display: TextToken = TextToken(40, 48, 600),
    val heading: TextToken = TextToken(24, 32, 600),
    val subheading: TextToken = TextToken(18, 24, 600),
    val body: TextToken = TextToken(16, 24),
    val caption: TextToken = TextToken(13, 18),
    val label: TextToken = TextToken(14, 20, 500),
    val mono: TextToken = TextToken(16, 24, 500),
)

data class SpaceScale(val xs: Int = 4, val sm: Int = 8, val md: Int = 12, val lg: Int = 16, val xl: Int = 24, val xxl: Int = 32, val gutter: Int = 16)

data class RadiusScale(val sm: Int = 8, val md: Int = 12, val lg: Int = 16, val xl: Int = 24)

data class ControlHeights(val sm: Int = 36, val md: Int = 48, val lg: Int = 56, val xl: Int = 72)

data class ElevationSteps(val surface: Float = 0.06f, val raised: Float = 0.10f, val overlay: Float = 0.14f)

val DARK_SERIES = listOf(0xFF56B4E9, 0xFFE69F00, 0xFF2FCB9A, 0xFFF0E442, 0xFFCC79A7, 0xFF8E9BFF, 0xFFFF8A4C, 0xFFB8C0CC).map(::Color)

val LIGHT_SERIES = listOf(0xFF0072B2, 0xFFD55E00, 0xFF007A5A, 0xFF8A7B00, 0xFFAA4499, 0xFF4B3FBF, 0xFF0B7285, 0xFF4D5560).map(::Color)

private fun Color(argb: Long): Color = Color(argb.toInt())

private fun style(token: TextToken, family: FontFamily? = null): TextStyle =
    TextStyle(fontSize = token.size.sp, lineHeight = token.line.sp, fontWeight = FontWeight(token.weight), fontFamily = family)

fun SpecTheme.typography(): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = style(type.display), displayMedium = style(type.display), displaySmall = style(type.heading),
        headlineLarge = style(type.heading), headlineMedium = style(type.heading), headlineSmall = style(type.subheading),
        titleLarge = style(type.subheading), titleMedium = style(type.subheading), titleSmall = style(type.label),
        bodyLarge = style(type.body), bodyMedium = style(type.body), bodySmall = style(type.caption),
        labelLarge = style(type.label), labelMedium = style(type.caption), labelSmall = style(type.caption),
    )
}

fun SpecTheme.monoStyle(): TextStyle = style(type.mono, FontFamily.Monospace)

fun SpecTheme.displayMonoStyle(): TextStyle = style(type.display, FontFamily.Monospace)

fun SpecTheme.shapes(): Shapes = Shapes(
    extraSmall = RoundedCornerShape(radius.sm / 2),
    small = RoundedCornerShape(radius.sm),
    medium = RoundedCornerShape(radius.md),
    large = RoundedCornerShape(radius.lg),
    extraLarge = RoundedCornerShape(radius.xl),
)

fun ControlHeights.dpOf(size: String): androidx.compose.ui.unit.Dp = when (size) {
    "sm" -> sm.dp
    "lg" -> lg.dp
    "xl" -> xl.dp
    else -> md.dp
}
