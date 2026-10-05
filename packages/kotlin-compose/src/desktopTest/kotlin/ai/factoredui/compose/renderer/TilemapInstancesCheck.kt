package ai.factoredui.compose.renderer

import ai.factoredui.compose.adapter.ActionHandler
import ai.factoredui.compose.layout.GroundPoint
import ai.factoredui.compose.layout.TileShape
import ai.factoredui.compose.layout.TileView
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.testing.SpecVisualCheck
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val TILE_SIDE_MM = 7620.0

@OptIn(ExperimentalTestApi::class)
class TilemapInstancesCheck {

    private val cols = 5
    private val rows = 4

    private fun tilemap() = SpecNode(
        id = "world",
        type = SpecNodeType.TILEMAP,
        props = mapOf(
            "cols" to SpecValue.NumberValue(cols.toDouble()),
            "rows" to SpecValue.NumberValue(rows.toDouble()),
            "view" to SpecValue.StringValue("top"),
            "tile_area" to SpecValue.NumberValue(625.0),
            "uses" to SpecValue.StringValue("{uses}"),
            "cells" to SpecValue.StringValue("{cells}"),
            "footprints" to SpecValue.StringValue("{footprints}"),
            "instances" to SpecValue.StringValue("{instances}"),
            "selected_use" to SpecValue.StringValue("{brush}"),
            "counts" to SpecValue.StringValue("{counts}"),
            "on_tile_tap" to SpecValue.StringValue("world.tileTapped"),
            "on_instance_tap" to SpecValue.StringValue("world.instanceTapped"),
            "controlled" to SpecValue.StringValue("{controlled}"),
        ),
    )

    private val uses = listOf(
        mapOf("id" to "tree", "label" to "Tree", "color" to "#1F5E2B", "sprite" to "tree"),
        mapOf("id" to "shed", "label" to "Shed", "color" to "#8B5A2B", "sprite" to "block"),
        mapOf("id" to "paddock", "label" to "Paddock", "color" to "#B7C94A", "sprite" to "fence"),
    )

    private fun instance(id: String, groundX: Double, groundY: Double, crownMm: Double = 1000.0) = mapOf(
        "id" to id,
        "type" to "tree",
        "x_mm" to groundX * TILE_SIDE_MM,
        "y_mm" to (rows - groundY) * TILE_SIDE_MM,
        "height_mm" to 9000.0,
        "crown_radius_mm" to crownMm,
        "rotation_deg" to 0.0,
    )

    private fun footprint(id: String, use: String, col: Int, row: Int, width: Int, height: Int) =
        mapOf("id" to id, "use" to use, "col" to col, "row" to row, "width" to width, "height" to height)

    private fun contextOf(
        footprints: List<Map<String, Any?>> = emptyList(),
        instances: List<Map<String, Any?>> = emptyList(),
        actions: Map<String, ActionHandler> = emptyMap(),
    ) = RenderContext(
        actions = actions,
        initialData = mapOf("uses" to uses, "cells" to emptyList<Any?>(), "footprints" to footprints, "instances" to instances, "brush" to "tree", "controlled" to true),
    )

    private fun luminance(color: Color) = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

    private fun Color.isNear(other: Color, tolerance: Float = 0.06f) =
        abs(red - other.red) < tolerance && abs(green - other.green) < tolerance && abs(blue - other.blue) < tolerance

    private fun PlacedTiles.darkPixelsAround(ground: GroundPoint): Int =
        (-16..2 step 2).sumOf { dy -> (-6..6 step 2).count { dx -> luminance(pixelAtGround(ground, dx.toFloat(), dy.toFloat())) < 0.55f } }

    @Test
    fun aTreeInstanceStandsAtItsFreePositionAndNotAtTheNearestTileCentre() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf(instances = listOf(instance("t1", 2.9, 1.1))))
        check.render(tilemap(), viewport = 500.dp)
        val placed = PlacedTiles(check, TileShape.SQUARE, TileView.TOP, cols, rows)
        assertTrue(placed.darkPixelsAround(GroundPoint(2.9f, 1.1f)) >= 6, "the tree is drawn at the millimetre position")
        assertEquals(0, placed.darkPixelsAround(GroundPoint(2.5f, 1.5f)), "the tile centre beside it stays ground")
    }

    @Test
    fun aBiggerCrownDrawsAWiderTree() = runComposeUiTest {
        val small = SpecVisualCheck(this, contextOf(instances = listOf(instance("t1", 2.5, 2.0, crownMm = 600.0))))
        small.render(tilemap(), viewport = 500.dp)
        val smallWidth = PlacedTiles(small, TileShape.SQUARE, TileView.TOP, cols, rows).darkPixelsAround(GroundPoint(2.5f, 2.0f))
        val large = SpecVisualCheck(this, contextOf(instances = listOf(instance("t1", 2.5, 2.0, crownMm = 4000.0))))
        large.render(tilemap(), viewport = 500.dp)
        val largeWidth = PlacedTiles(large, TileShape.SQUARE, TileView.TOP, cols, rows).darkPixelsAround(GroundPoint(2.5f, 2.0f))
        assertTrue(largeWidth > smallWidth, "a 4 m crown covers more pixels than a 0.6 m one: $largeWidth vs $smallWidth")
    }

    @Test
    fun aMultiTileFenceHasPostsOnlyAtItsOuterCorners() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf(footprints = listOf(footprint("p1", "paddock", 1, 1, 2, 2))))
        check.render(tilemap(), viewport = 500.dp)
        val placed = PlacedTiles(check, TileShape.SQUARE, TileView.TOP, cols, rows)
        val rail = Color(0xFFB7C94A).let { Color(it.red * 0.65f, it.green * 0.65f, it.blue * 0.65f) }
        assertTrue(placed.pixelAtGround(GroundPoint(1f, 1f), 0f, -6f).isNear(rail, 0.12f), "an outer corner carries a post")
        assertTrue(!placed.pixelAtGround(GroundPoint(2f, 2f), 0f, -6f).isNear(rail, 0.12f), "the shared interior vertex carries none")
    }

    @Test
    fun aMultiTileBlockIsOneRoofOverTheWholeFootprint() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf(footprints = listOf(footprint("s1", "shed", 1, 1, 2, 2))))
        check.render(tilemap(), viewport = 500.dp)
        val placed = PlacedTiles(check, TileShape.SQUARE, TileView.TOP, cols, rows)
        val ground = Color(0xFFCFE0A8)
        assertTrue(!placed.pixelAtGround(GroundPoint(2.6f, 1.8f)).isNear(ground), "a point over the far tile of the footprint is roof, not ground")
        assertTrue(placed.pixelAtGround(GroundPoint(4.5f, 3.5f)).isNear(ground), "a tile outside the footprint is ground")
    }

    @Test
    fun footprintTilesAreCountedAndEveryDeclaredUseStaysAtZero() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf(footprints = listOf(footprint("s1", "shed", 1, 1, 2, 2), footprint("s2", "shed", 4, 0, 1, 1))))
        check.render(tilemap(), viewport = 500.dp)
        assertEquals(mapOf("tree" to 0, "shed" to 5, "paddock" to 0), check.binding("counts"))
    }

    @Test
    fun tappingATreeReportsItsIdAndTappingGroundReportsTheTile() = runComposeUiTest {
        val instanceTaps = mutableListOf<Map<String, Any?>>()
        val tileTaps = mutableListOf<Map<String, Any?>>()
        val actions = mapOf<String, ActionHandler>(
            "world.instanceTapped" to { params -> instanceTaps.add(params) },
            "world.tileTapped" to { params -> tileTaps.add(params) },
        )
        val check = SpecVisualCheck(this, contextOf(instances = listOf(instance("t1", 3.0, 1.0)), actions = actions))
        check.render(tilemap(), viewport = 500.dp)
        val placed = PlacedTiles(check, TileShape.SQUARE, TileView.TOP, cols, rows)
        placed.tapGround(GroundPoint(3.0f, 1.0f))
        assertEquals(listOf("t1"), instanceTaps.map { it["id"] })
        assertTrue(tileTaps.isEmpty(), "a tap on a tree is not also a tile tap")
        placed.tapGround(GroundPoint(0.5f, 3.5f))
        assertEquals(listOf(0.0, 3.0), listOf(tileTaps.single()["col"], tileTaps.single()["row"]).map { (it as Number).toDouble() })
        assertEquals(1, instanceTaps.size)
    }
}
