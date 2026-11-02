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

data class TextToken(val size: Int, val line: Int, val weight: Int)

data class TypeScale(val display: TextToken, val heading: TextToken, val subheading: TextToken, val body: TextToken, val caption: TextToken, val label: TextToken, val mono: TextToken)

data class SpaceScale(val xs: Int, val sm: Int, val md: Int, val lg: Int, val xl: Int, val xxl: Int, val gutter: Int)

data class RadiusScale(val sm: Int, val md: Int, val lg: Int, val xl: Int)

data class ControlHeights(val sm: Int, val md: Int, val lg: Int, val xl: Int)

data class ElevationSteps(val surface: Float, val raised: Float, val overlay: Float)

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
