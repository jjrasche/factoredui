package ai.factoredui.compose.renderer

import ai.factoredui.compose.pixel.EMBEDDED_PIXEL_ATLAS
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.testing.SpecVisualCheck
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PixelLookCheck {

    private val parcelTiles = 8

    private fun tilemap(look: String?, tiles: Int = parcelTiles) = SpecNode(
        id = "world",
        type = SpecNodeType.TILEMAP,
        props = buildMap {
            put("cols", SpecValue.NumberValue(tiles.toDouble()))
            put("rows", SpecValue.NumberValue(tiles.toDouble()))
            put("tile_area", SpecValue.NumberValue(25.0))
            put("uses", SpecValue.StringValue("{uses}"))
            put("cells", SpecValue.StringValue("{cells}"))
            put("footprints", SpecValue.StringValue("{footprints}"))
            put("palette", SpecValue.StringValue("none"))
            put("view_state", SpecValue.StringValue("{view_state}"))
            look?.let { put("look", SpecValue.StringValue(it)) }
        },
    )

    private val uses = listOf(
        mapOf("id" to "path", "label" to "Path", "color" to "#D9C9A3", "sprite" to "flat"),
        mapOf("id" to "pond", "label" to "Pond", "color" to "#5B9BD5", "sprite" to "water"),
    )

    private fun contextOf(footprints: List<Map<String, Any?>> = emptyList(), centreMm: List<Double>? = null) = RenderContext(
        initialData = mapOf("uses" to uses, "cells" to emptyList<Any?>(), "footprints" to footprints, "view_state" to centreMm?.let { mapOf("centre_mm" to it) }),
    )

    private fun patternColours(name: String): Set<Int> {
        val scale = EMBEDDED_PIXEL_ATLAS.manifest.scales.getValue("32-5ft")
        val sprite = scale.byName.getValue(name)
        return EMBEDDED_PIXEL_ATLAS.sheets("32-5ft")[sprite.sheet].crop(sprite.x, sprite.y, sprite.width, sprite.height).argb.toSet()
    }

    private fun PixelMap.centreBlock(half: Int): List<Int> =
        (height / 2 - half until height / 2 + half).flatMap { y -> (width / 2 - half until width / 2 + half).map { x -> this[x, y].toArgb() } }

    private fun mapPixels(check: SpecVisualCheck): PixelMap {
        val region = check.region("world:map")
        val image = check.png().toPixelMap()
        val left = region.left.value.toInt()
        val top = region.top.value.toInt()
        val width = (region.right.value - region.left.value).toInt()
        val height = (region.bottom.value - region.top.value).toInt()
        return PixelMap(IntArray(width * height) { image[left + it % width, top + it / width].toArgb() }, width, height, 0, width)
    }

    @Test
    fun theGroundIsGrassArtWithNoGridLinesBetweenTiles() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf())
        check.render(tilemap("pixel"), viewport = 400.dp)
        val grass = patternColours("ground-grass/0")
        val stray = mapPixels(check).centreBlock(20).filterNot { it in grass }
        assertEquals(emptyList(), stray.distinct().map { it.toUInt().toString(16) }, "every pixel at the parcel centre is a grass art colour")
    }

    @Test
    fun aSmallParcelOpensAtTheLargestWholeZoomThatFitsSoEachArtPixelIsAThreeByThreeBlock() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf())
        check.render(tilemap("pixel", tiles = 3), viewport = 400.dp)
        val pixels = mapPixels(check)
        val row = (0 until pixels.width).map { pixels[it, pixels.height / 2].toArgb() }.subList(pixels.width / 2 - 90, pixels.width / 2 + 90)
        val runs = mutableListOf<Int>()
        var length = 1
        for (index in 1 until row.size) if (row[index] == row[index - 1]) length++ else { runs.add(length); length = 1 }
        val inner = runs.drop(1)
        assertTrue(inner.size > 10, "the row crosses many art pixels")
        assertTrue(inner.all { it % 3 == 0 }, "runs of equal colour are whole art pixels: $inner")
    }

    @Test
    fun aPathFootprintIsPathArtAndAPondIsWaterArt() = runComposeUiTest {
        val footprints = listOf(
            mapOf("id" to "p", "use" to "path", "col" to 0, "row" to 0, "width" to 4, "height" to 4),
            mapOf("id" to "w", "use" to "pond", "col" to 4, "row" to 4, "width" to 4, "height" to 4),
        )
        val pathCheck = SpecVisualCheck(this, contextOf(footprints, centreMm = listOf(2 * 1524.0, (parcelTiles - 2) * 1524.0)))
        pathCheck.render(tilemap("pixel"), viewport = 400.dp)
        val path = patternColours("ground-path/0")
        assertTrue(mapPixels(pathCheck).centreBlock(10).all { it in path }, "the centre of the path footprint is path art")
    }

    @Test
    fun theFocusOfTheViewStateIsAtTheCentreOfTheMap() = runComposeUiTest {
        val footprints = listOf(mapOf("id" to "w", "use" to "pond", "col" to 4, "row" to 4, "width" to 4, "height" to 4))
        val check = SpecVisualCheck(this, contextOf(footprints, centreMm = listOf(6 * 1524.0, (parcelTiles - 6) * 1524.0)))
        check.render(tilemap("pixel"), viewport = 400.dp)
        val water = patternColours("ground-water/0")
        assertTrue(mapPixels(check).centreBlock(10).all { it in water }, "the pond the view centres on is water art")
    }
}
