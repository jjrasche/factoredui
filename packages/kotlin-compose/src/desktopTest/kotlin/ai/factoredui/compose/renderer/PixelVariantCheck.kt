package ai.factoredui.compose.renderer

import ai.factoredui.compose.layout.TileShape
import ai.factoredui.compose.layout.TileView
import ai.factoredui.compose.layout.tilemapScreenBounds
import ai.factoredui.compose.pixel.EMBEDDED_PIXEL_ATLAS
import ai.factoredui.compose.pixel.fitPixelZoom
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.testing.SpecVisualCheck
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

private const val COARSE_TILE_MM = 7620.0

@OptIn(ExperimentalTestApi::class)
class PixelVariantCheck {

    private val cols = 5
    private val rows = 8

    private val tilemap = SpecNode(
        id = "world",
        type = SpecNodeType.TILEMAP,
        props = mapOf(
            "cols" to SpecValue.NumberValue(cols.toDouble()),
            "rows" to SpecValue.NumberValue(rows.toDouble()),
            "tile_area" to SpecValue.NumberValue(625.0),
            "uses" to SpecValue.StringValue("{uses}"),
            "cells" to SpecValue.StringValue("{cells}"),
            "instances" to SpecValue.StringValue("{instances}"),
            "palette" to SpecValue.StringValue("none"),
            "look" to SpecValue.StringValue("pixel"),
        ),
    )

    private fun spriteColours(variant: String, name: String): Pair<Set<Int>, Int> {
        val sprite = EMBEDDED_PIXEL_ATLAS.manifest.scales.getValue(variant).byName.getValue(name)
        val opaque = EMBEDDED_PIXEL_ATLAS.sheets(variant)[sprite.sheet].crop(sprite.x, sprite.y, sprite.width, sprite.height).argb.filter { (it ushr 24) == 0xFF }
        return opaque.toSet() to opaque.size
    }

    @Test
    fun aTwentyFiveFootWorldOpensWholeAtTheFitZoomWithItsTreeDrawnFromTheTwentyFiveFootArt() = runComposeUiTest {
        val tree = mapOf("id" to "t", "type" to "lidar_tree", "x_mm" to 2.5 * COARSE_TILE_MM, "y_mm" to 4.0 * COARSE_TILE_MM, "crown_radius_mm" to 6000.0, "rotation_deg" to 0.0)
        val context = RenderContext(initialData = mapOf("uses" to listOf(mapOf("id" to "lidar_tree", "label" to "Lidar tree", "sprite" to "tree")), "cells" to emptyList<Any?>(), "instances" to listOf(tree)))
        val check = SpecVisualCheck(this, context)
        check.render(tilemap, viewport = 500.dp)
        val region = check.region("world:map")
        val parcel = tilemapScreenBounds(TileShape.SQUARE, TileView.ISO, cols, rows, 32f)
        val zoom = fitPixelZoom(parcel.maxX - parcel.minX, parcel.maxY - parcel.minY, region.right.value - region.left.value, region.bottom.value - region.top.value, 12f, 6)
        val image = check.png().toPixelMap()
        val pixels = (0 until image.height).flatMap { y -> (0 until image.width).map { x -> image[x, y].toArgb() } }
        val (grass, _) = spriteColours("32-25ft", "ground-grass/0")
        val grassColumns = (0 until image.width).filter { x -> (0 until image.height).any { y -> image[x, y].toArgb() in grass } }
        val drawnWidth = grassColumns.last() - grassColumns.first() + 1
        assertTrue(abs(drawnWidth - (parcel.maxX - parcel.minX) * zoom) <= zoom + 1, "the whole parcel is in view at ${zoom}x: $drawnWidth px wide")
        val (crown, opaque) = spriteColours("32-25ft", "tree-unknown/XL")
        val treePixels = pixels.count { it in crown && it !in grass }
        assertTrue(treePixels in (opaque * zoom * zoom / 4)..(opaque * zoom * zoom * 2), "the tree covers about its 25 ft sprite: $treePixels vs ${opaque * zoom * zoom}")
    }
}
