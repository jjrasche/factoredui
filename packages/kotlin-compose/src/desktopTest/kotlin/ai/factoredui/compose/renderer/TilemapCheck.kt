package ai.factoredui.compose.renderer

import ai.factoredui.compose.adapter.ActionHandler
import ai.factoredui.compose.layout.TileShape
import ai.factoredui.compose.layout.TileView
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.testing.SpecVisualCheck
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TilemapCheck {

    private val cols = 5
    private val rows = 4

    private fun tilemap(shape: String = "square", view: String = "top") = SpecNode(
        id = "world",
        type = SpecNodeType.TILEMAP,
        props = mapOf(
            "cols" to SpecValue.NumberValue(cols.toDouble()),
            "rows" to SpecValue.NumberValue(rows.toDouble()),
            "shape" to SpecValue.StringValue(shape),
            "view" to SpecValue.StringValue(view),
            "tile_area" to SpecValue.NumberValue(625.0),
            "uses" to SpecValue.StringValue("{uses}"),
            "cells" to SpecValue.StringValue("{cells}"),
            "selected_use" to SpecValue.StringValue("{brush}"),
            "counts" to SpecValue.StringValue("{counts}"),
            "areas" to SpecValue.StringValue("{areas}"),
            "on_tile_tap" to SpecValue.StringValue("world.tileTapped"),
        ),
    )

    private val uses = listOf(
        mapOf("id" to "path", "label" to "Path", "color" to "#D2B48C", "sprite" to "flat"),
        mapOf("id" to "pond", "label" to "Pond", "color" to "#3B82C4", "sprite" to "water"),
        mapOf("id" to "tree", "label" to "Tree", "color" to "#1F5E2B", "sprite" to "tree"),
        mapOf("id" to "shed", "label" to "Shed", "color" to "#8B5A2B", "sprite" to "block"),
    )

    private fun contextOf(
        cells: List<Map<String, Any?>> = emptyList(),
        brush: String = "path",
        actions: Map<String, ActionHandler> = emptyMap(),
    ) = RenderContext(
        actions = actions,
        initialData = mapOf("uses" to uses, "cells" to cells, "brush" to brush),
    )

    private fun Color.isNear(other: Color, tolerance: Float = 0.06f) =
        abs(red - other.red) < tolerance && abs(green - other.green) < tolerance && abs(blue - other.blue) < tolerance

    @Test
    fun aPlacedFlatUseColoursItsTileAndAnEmptyTileShowsTheGround() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf(cells = listOf(mapOf("col" to 1, "row" to 1, "use" to "path"))))
        check.render(tilemap(), viewport = 500.dp)
        val placed = PlacedTiles(check, TileShape.SQUARE, TileView.TOP, cols, rows)
        assertTrue(placed.pixelAt(1, 1).isNear(Color(0xFFD2B48C)), "the path tile takes its colour")
        assertTrue(placed.pixelAt(0, 0).isNear(Color(0xFFCFE0A8)), "an empty tile shows the ground")
    }

    @Test
    fun tappingATileWithABrushPlacesItAndTappingAgainRemovesIt() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf())
        check.render(tilemap(), viewport = 500.dp)
        val placed = PlacedTiles(check, TileShape.SQUARE, TileView.TOP, cols, rows)
        placed.tap(2, 1)
        assertEquals(listOf(mapOf("col" to 2, "row" to 1, "use" to "path")), check.binding("cells"))
        assertEquals(mapOf("path" to 1, "pond" to 0, "tree" to 0, "shed" to 0), check.binding("counts"), "every declared use is counted, even at zero")
        assertEquals(mapOf("path" to 625L, "pond" to 0L, "tree" to 0L, "shed" to 0L), check.binding("areas"), "areas are whole numbers when they are whole")
        placed.tap(2, 1)
        assertEquals(emptyList<Any?>(), check.binding("cells"))
        assertEquals(mapOf("path" to 0, "pond" to 0, "tree" to 0, "shed" to 0), check.binding("counts"))
    }

    @Test
    fun aHexTapPicksTheHexUnderThePointer() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf())
        check.render(tilemap(shape = "hex"), viewport = 500.dp)
        val placed = PlacedTiles(check, TileShape.HEX, TileView.TOP, cols, rows)
        placed.tap(3, 3)
        assertEquals(listOf(mapOf("col" to 3, "row" to 3, "use" to "path")), check.binding("cells"), "an odd hex row sits half a tile right, where a square grid would pick the next column")
    }

    @Test
    fun anIsometricTapPicksTheTileUnderThePointer() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf())
        check.render(tilemap(view = "iso"), viewport = 500.dp)
        val placed = PlacedTiles(check, TileShape.SQUARE, TileView.ISO, cols, rows)
        placed.tap(4, 3)
        assertEquals(listOf(mapOf("col" to 4, "row" to 3, "use" to "path")), check.binding("cells"))
    }

    @Test
    fun tappingABrushSwatchSelectsThatBrushForTheNextTap() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf())
        check.render(tilemap(), viewport = 500.dp)
        check.tap("world:brush:pond")
        assertEquals("pond", check.binding("brush"))
        PlacedTiles(check, TileShape.SQUARE, TileView.TOP, cols, rows).tap(0, 0)
        assertEquals(listOf(mapOf("col" to 0, "row" to 0, "use" to "pond")), check.binding("cells"))
    }

    @Test
    fun theTileTapActionCarriesTheColumnRowAndResultingUse() = runComposeUiTest {
        var fired: Map<String, Any?> = emptyMap()
        val capture: ActionHandler = { params -> fired = params }
        val check = SpecVisualCheck(this, contextOf(actions = mapOf("world.tileTapped" to capture)))
        check.render(tilemap(), viewport = 500.dp)
        PlacedTiles(check, TileShape.SQUARE, TileView.TOP, cols, rows).tap(1, 2)
        assertEquals(1.0, (fired["col"] as Number).toDouble())
        assertEquals(2.0, (fired["row"] as Number).toDouble())
        assertEquals("path", fired["use"])
    }

    @Test
    fun aTreeRisesAboveItsTileWhileAFlatUseDoesNot() = runComposeUiTest {
        val cells = listOf(mapOf("col" to 2, "row" to 2, "use" to "tree"), mapOf("col" to 3, "row" to 1, "use" to "path"))
        val check = SpecVisualCheck(this, contextOf(cells = cells))
        check.render(tilemap(view = "iso"), viewport = 500.dp)
        val placed = PlacedTiles(check, TileShape.SQUARE, TileView.ISO, cols, rows)
        assertTrue(placed.pixelAt(2, 2, dy = -30f).isNear(Color(0xFF1F5E2B), 0.1f), "canopy stands above the tree tile")
        assertTrue(!placed.pixelAt(3, 1, dy = -30f).isNear(Color(0xFFD2B48C), 0.05f), "a flat tile paints nothing above itself")
    }

    @Test
    fun aBlockRaisesAWallInItsOwnColour() = runComposeUiTest {
        val cells = listOf(mapOf("col" to 2, "row" to 2, "use" to "shed"))
        val check = SpecVisualCheck(this, contextOf(cells = cells))
        check.render(tilemap(view = "iso"), viewport = 500.dp)
        val placed = PlacedTiles(check, TileShape.SQUARE, TileView.ISO, cols, rows)
        val roof = placed.pixelAt(2, 2, dy = -32f)
        assertTrue(roof.isNear(Color(0xFF9E7850), 0.08f) && roof.alpha > 0.9f, "a one-tile block lifts its brown roof half a tile width above the ground: $roof")
    }

    @Test
    fun theShapeAndTheViewChangeWhereATileIsDrawn() = runComposeUiTest {
        val squareAt = PlacedTiles(SpecVisualCheck(this, contextOf()).also { it.render(tilemap(), viewport = 500.dp) }, TileShape.SQUARE, TileView.TOP, cols, rows).screenOf(3, 1)
        val isoAt = PlacedTiles(SpecVisualCheck(this, contextOf()).also { it.render(tilemap(view = "iso"), viewport = 500.dp) }, TileShape.SQUARE, TileView.ISO, cols, rows).screenOf(3, 1)
        assertTrue(squareAt != isoAt, "iso and top place the same tile at different spots")
    }
}
