package ai.factoredui.compose.renderer

import ai.factoredui.compose.layout.GroundPoint
import ai.factoredui.compose.layout.TileShape
import ai.factoredui.compose.layout.TileView
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.testing.SpecVisualCheck
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TerrainCostsCheck {

    private val size = 32
    private val centre = size / 2.0

    private fun tilemap() = SpecNode(
        id = "world",
        type = SpecNodeType.TILEMAP,
        props = mapOf(
            "cols" to SpecValue.NumberValue(size.toDouble()),
            "rows" to SpecValue.NumberValue(size.toDouble()),
            "view" to SpecValue.StringValue("top"),
            "tile_area" to SpecValue.NumberValue(100.0),
            "uses" to SpecValue.StringValue("{uses}"),
            "cells" to SpecValue.StringValue("{cells}"),
            "terrain" to SpecValue.StringValue("{terrain}"),
            "terrain_mode" to SpecValue.StringValue("{terrain_mode}"),
            "contours" to SpecValue.StringValue("{contours}"),
            "contour_interval_mm" to SpecValue.StringValue("{contour_interval_mm}"),
            "palette" to SpecValue.StringValue("none"),
        ),
    )

    private fun cone(): List<Int> = (0 until (size + 1) * (size + 1)).map {
        maxOf(0, (400 - 100 * hypot(it % (size + 1) - centre, it / (size + 1) - centre)).roundToInt())
    }

    private fun contextOf() = RenderContext(
        initialData = mapOf(
            "uses" to listOf(mapOf("id" to "tree", "label" to "Tree", "color" to "#1F5E2B")),
            "cells" to emptyList<Any?>(),
            "terrain" to mapOf("cols" to size, "rows" to size, "version" to 1L, "heights_mm" to cone(), "cut_fill_mm" to cone().map { 0 }),
            "terrain_mode" to null,
            "contours" to true,
            "contour_interval_mm" to "50",
        ),
    )

    private fun contourRingsAlongTheEastRay(viewport: Dp): Int {
        var rings = 0
        runComposeUiTest {
            val check = SpecVisualCheck(this, contextOf())
            check.render(tilemap(), viewport = viewport)
            val placed = PlacedTiles(check, TileShape.SQUARE, TileView.TOP, size, size)
            val (startX, rayY) = placed.screenAt(GroundPoint(centre.toFloat() + 0.05f, centre.toFloat()))
            val (endX, _) = placed.screenAt(GroundPoint(centre.toFloat() + 3.95f, centre.toFloat()))
            val row = check.png().rowOfPixels(startX, endX, rayY)
            val darker = row.indices.map { index -> luminance(row.first()) - luminance(row[index]) > 0.12f }
            rings = darker.indices.count { darker[it] && (it == 0 || !darker[it - 1]) }
        }
        return rings
    }

    @Test
    fun aPhoneKeepsOnlyTheIndexContourWhereATileIsTooSmallForThinOnes() {
        assertEquals(1, contourRingsAlongTheEastRay(590.dp))
    }

    @Test
    fun aDesktopDrawsEveryContourAtTheSameTileSize() {
        assertEquals(7, contourRingsAlongTheEastRay(610.dp))
    }

    private fun luminance(color: Color) = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
}

private fun ImageBitmap.rowOfPixels(startX: Float, endX: Float, y: Float): List<Color> {
    val pixels = toPixelMap()
    return (startX.toInt()..endX.toInt()).map { x -> pixels[x, y.toInt()] }
}
