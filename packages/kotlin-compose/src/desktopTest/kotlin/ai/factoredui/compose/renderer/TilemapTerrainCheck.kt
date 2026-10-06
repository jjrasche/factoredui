package ai.factoredui.compose.renderer

import ai.factoredui.compose.layout.GroundPoint
import ai.factoredui.compose.layout.TileShape
import ai.factoredui.compose.layout.TileView
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.terrain.CUT_COLOUR
import ai.factoredui.compose.terrain.HEAT_RAMP
import ai.factoredui.compose.terrain.NEUTRAL_COLOUR
import ai.factoredui.compose.testing.SpecVisualCheck
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TilemapTerrainCheck {

    private val cols = 8
    private val rows = 8

    private fun tilemap(view: String = "top") = SpecNode(
        id = "world",
        type = SpecNodeType.TILEMAP,
        props = mapOf(
            "cols" to SpecValue.NumberValue(cols.toDouble()),
            "rows" to SpecValue.NumberValue(rows.toDouble()),
            "view" to SpecValue.StringValue(view),
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

    private fun heightsOf(height: (vertexCol: Int, vertexRow: Int) -> Int): List<Int> =
        (0 until (cols + 1) * (rows + 1)).map { height(it % (cols + 1), it / (cols + 1)) }

    private fun distance(col: Int, row: Int): Double = hypot(col - 4.0, row - 4.0)

    private fun terrainOf(heights: List<Int>, cutFill: List<Int> = heights.map { 0 }) =
        mapOf("cols" to cols, "rows" to rows, "version" to 1L, "heights_mm" to heights, "cut_fill_mm" to cutFill)

    private fun contextOf(terrain: Map<String, Any?>?, mode: String? = null, isContoursShown: Boolean = false, intervalMm: Int = 50) = RenderContext(
        initialData = mapOf(
            "uses" to listOf(mapOf("id" to "tree", "label" to "Tree", "color" to "#1F5E2B")),
            "cells" to emptyList<Any?>(),
            "terrain" to terrain,
            "terrain_mode" to mode,
            "contours" to isContoursShown,
            "contour_interval_mm" to intervalMm.toString(),
        ),
    )

    private fun luminance(color: Color) = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

    private fun Color.isNear(argb: Int, tolerance: Float = 0.08f): Boolean {
        val other = Color(argb)
        return abs(red - other.red) < tolerance && abs(green - other.green) < tolerance && abs(blue - other.blue) < tolerance
    }

    private fun bowl() = heightsOf { col, row -> minOf(1000, (250 * distance(col, row)).roundToInt()) }

    private fun cone() = heightsOf { col, row -> maxOf(0, (400 - 100 * distance(col, row)).roundToInt()) }

    @Test
    fun aBowlInHillshadeHasALitSouthEastWallAndAShadedNorthWestWall() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf(terrainOf(bowl()), mode = "hillshade"))
        check.render(tilemap(), viewport = 500.dp)
        val placed = PlacedTiles(check, TileShape.SQUARE, TileView.TOP, cols, rows)
        val lit = luminance(placed.pixelAtGround(GroundPoint(5.4f, 5.4f)))
        val shaded = luminance(placed.pixelAtGround(GroundPoint(2.6f, 2.6f)))
        assertTrue(lit > shaded + 0.25f, "the wall facing the north-west sun is lit ($lit) and the wall facing away is shaded ($shaded)")
    }

    @Test
    fun aBowlInIsometricHillshadeIsShadedTheSameWay() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf(terrainOf(bowl()), mode = "hillshade"))
        check.render(tilemap(view = "iso"), viewport = 500.dp)
        val placed = PlacedTiles(check, TileShape.SQUARE, TileView.ISO, cols, rows)
        val lit = luminance(placed.pixelAtGround(GroundPoint(5.4f, 5.4f)))
        val shaded = luminance(placed.pixelAtGround(GroundPoint(2.6f, 2.6f)))
        assertTrue(lit > shaded + 0.25f, "lit $lit against shaded $shaded through the isometric skew")
    }

    @Test
    fun heatModeShowsTheLowColourOnLowGroundAndTheHighColourOnHighGround() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf(terrainOf(heightsOf { col, _ -> 100 * col }), mode = "heat"))
        check.render(tilemap(), viewport = 500.dp)
        val placed = PlacedTiles(check, TileShape.SQUARE, TileView.TOP, cols, rows)
        val low = placed.pixelAtGround(GroundPoint(0.1f, 4.5f))
        val high = placed.pixelAtGround(GroundPoint(7.9f, 4.5f))
        assertTrue(low.isNear(HEAT_RAMP.first()), "the west edge is the low colour, got $low")
        assertTrue(high.isNear(HEAT_RAMP.last()), "the east edge is the high colour, got $high")
        assertTrue(placed.pixelAtGround(GroundPoint(3.9f, 1.5f)).isNear(HEAT_RAMP[2]), "the middle is the middle colour")
    }

    @Test
    fun cutFillShowsTheCutColourWhereADigWasAndNeutralElsewhere() = runComposeUiTest {
        val dug = heightsOf { col, row -> if (col in 2..3 && row in 5..6) -137 else 0 }
        val check = SpecVisualCheck(this, contextOf(terrainOf(cone(), cutFill = dug), mode = "cutfill"))
        check.render(tilemap(), viewport = 500.dp)
        val placed = PlacedTiles(check, TileShape.SQUARE, TileView.TOP, cols, rows)
        assertTrue(placed.pixelAtGround(GroundPoint(2.5f, 5.5f)).isNear(CUT_COLOUR), "the dug tile is the cut colour")
        assertTrue(placed.pixelAtGround(GroundPoint(5.5f, 2.5f)).isNear(NEUTRAL_COLOUR, 0.04f), "an untouched tile is neutral")
    }

    @Test
    fun contoursAtFiftyMillimetresOverAConeDrawSevenRingsAlongARay() = runComposeUiTest {
        val context = contextOf(terrainOf(cone()), isContoursShown = false, intervalMm = 50)
        val check = SpecVisualCheck(this, context)
        check.render(tilemap(), viewport = 500.dp)
        val placed = PlacedTiles(check, TileShape.SQUARE, TileView.TOP, cols, rows)
        val (startX, rayY) = placed.screenAt(GroundPoint(4.05f, 4f))
        val (endX, _) = placed.screenAt(GroundPoint(7.95f, 4f))
        val without = check.png().toPixelMapRow(startX, endX, rayY)
        context.setBinding("contours", true)
        waitForIdle()
        val with = check.png().toPixelMapRow(startX, endX, rayY)
        val darker = with.indices.map { luminance(without[it]) - luminance(with[it]) > 0.12f }
        val rings = darker.indices.count { darker[it] && (it == 0 || !darker[it - 1]) }
        assertEquals(7, rings, "rings at 50 to 350 mm cross the ray from the summit to the east edge")
    }

    @Test
    fun withTerrainModeOffAndNoContoursTheGroundIsUntouched() = runComposeUiTest {
        val plain = SpecVisualCheck(this, contextOf(null))
        plain.render(tilemap(), viewport = 500.dp)
        val before = PlacedTiles(plain, TileShape.SQUARE, TileView.TOP, cols, rows)
        val centres = listOf(2 to 2, 3 to 2, 5 to 6, 0 to 7)
        val oldGround = centres.map { (col, row) -> before.pixelAt(col, row) }
        assertTrue(oldGround.first().isNear(0xFFCFE0A8.toInt(), 0.01f), "tile 2,2 is the light ground colour")
        val withTerrain = SpecVisualCheck(this, contextOf(terrainOf(cone())))
        withTerrain.render(tilemap(), viewport = 500.dp)
        val after = PlacedTiles(withTerrain, TileShape.SQUARE, TileView.TOP, cols, rows)
        assertEquals(oldGround, centres.map { (col, row) -> after.pixelAt(col, row) })
        assertEquals(0, onAllNodesWithTag("world:terrain-legend").fetchSemanticsNodes().size)
    }

    @Test
    fun theLegendNamesTheLowestAndHighestGroundInMetresAndFeet() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf(terrainOf(heightsOf { col, _ -> 223_891 + 100 * col }), mode = "heat"))
        check.render(tilemap(), viewport = 500.dp)
        onNodeWithText("low 223.89 m / 734.55 ft").assertExists()
        onNodeWithText("high 224.69 m / 737.18 ft").assertExists()
        assertEquals(1, onAllNodesWithTag("world:terrain-legend").fetchSemanticsNodes().size)
    }
}

private fun ImageBitmap.toPixelMapRow(startX: Float, endX: Float, y: Float): List<Color> {
    val pixels = toPixelMap()
    return (startX.toInt()..endX.toInt()).map { x -> pixels[x, y.toInt()] }
}
