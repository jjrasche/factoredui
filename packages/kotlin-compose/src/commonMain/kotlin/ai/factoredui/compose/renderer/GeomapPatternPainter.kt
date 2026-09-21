package ai.factoredui.compose.renderer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import ai.factoredui.compose.schema.GeomapPattern
import ai.factoredui.compose.schema.GeomapPatternKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// The ONE painter for a patterned fill. Map features and legend swatches both call it, so a
// swatch cannot disagree with the parcel it labels.
internal fun DrawScope.paintGeomapPattern(shape: Path, pattern: GeomapPattern) {
    pattern.background?.let(::parseGeomapColor)?.let { drawPath(shape, Color(it)) }
    val ink = parseGeomapColor(pattern.color)?.let { Color(it) } ?: return
    val area = shape.getBounds()
    val spacingPx = pattern.spacing * density
    val lineWidthPx = pattern.lineWidth * density
    clipPath(shape) {
        when (pattern.kind) {
            GeomapPatternKind.HATCH -> drawStripes(area, pattern.angle, spacingPx, lineWidthPx, ink)
            GeomapPatternKind.CROSS -> {
                drawStripes(area, pattern.angle, spacingPx, lineWidthPx, ink)
                drawStripes(area, pattern.angle + 90f, spacingPx, lineWidthPx, ink)
            }
            GeomapPatternKind.DOTS -> drawDots(area, spacingPx, lineWidthPx, ink)
        }
    }
}

private fun DrawScope.drawStripes(area: Rect, angleDegrees: Float, spacingPx: Float, lineWidthPx: Float, ink: Color) {
    if (spacingPx <= 0f) return
    val radians = angleDegrees * PI / 180.0
    val along = Offset(cos(radians).toFloat(), sin(radians).toFloat())
    val across = Offset(-along.y, along.x)
    val reach = hypot(area.width, area.height)
    var offset = -reach / 2f
    while (offset <= reach / 2f) {
        val through = area.center + across * offset
        drawLine(ink, through - along * reach, through + along * reach, strokeWidth = lineWidthPx)
        offset += spacingPx
    }
}

private fun DrawScope.drawDots(area: Rect, spacingPx: Float, radiusPx: Float, ink: Color) {
    if (spacingPx <= 0f) return
    var y = area.top
    while (y <= area.bottom) {
        var x = area.left
        while (x <= area.right) {
            drawCircle(ink, radius = radiusPx, center = Offset(x, y))
            x += spacingPx
        }
        y += spacingPx
    }
}
