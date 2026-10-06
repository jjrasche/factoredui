package ai.factoredui.compose.renderer

import ai.factoredui.compose.adapter.ActionHandler
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.testing.SpecVisualCheck
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

private const val TILE_MM = 1524.0

@OptIn(ExperimentalTestApi::class)
class PixelRotationCheck {

    private val cols = 8
    private val rows = 12

    private val tilemap = SpecNode(
        id = "world",
        type = SpecNodeType.TILEMAP,
        props = mapOf(
            "cols" to SpecValue.NumberValue(cols.toDouble()),
            "rows" to SpecValue.NumberValue(rows.toDouble()),
            "tile_area" to SpecValue.NumberValue(25.0),
            "uses" to SpecValue.StringValue("{uses}"),
            "cells" to SpecValue.StringValue("{cells}"),
            "instances" to SpecValue.StringValue("{instances}"),
            "palette" to SpecValue.StringValue("none"),
            "view_state" to SpecValue.StringValue("{view_state}"),
            "look" to SpecValue.StringValue("pixel"),
            "controlled" to SpecValue.BooleanValue(true),
            "on_tile_tap" to SpecValue.StringValue("world.tileTapped"),
            "on_instance_tap" to SpecValue.StringValue("world.instanceTapped"),
        ),
    )

    private class Taps(val tiles: List<Pair<Int, Int>>, val instances: List<String>)

    private fun tapsAtTheFocus(quarterTurns: Int, focusX: Double, focusY: Double, upPx: Float, hasTree: Boolean): Taps {
        val tiles = mutableListOf<Pair<Int, Int>>()
        val instances = mutableListOf<String>()
        runComposeUiTest {
            val actions = mapOf<String, ActionHandler>(
                "world.tileTapped" to { params -> tiles.add((params["col"] as Number).toInt() to (params["row"] as Number).toInt()) },
                "world.instanceTapped" to { params -> instances.add(params["id"] as String) },
            )
            val tree = mapOf("id" to "tree-01", "type" to "lidar_tree", "x_mm" to 1.5 * TILE_MM, "y_mm" to (rows - 10.5) * TILE_MM, "crown_radius_mm" to 3000.0, "rotation_deg" to 0.0)
            val context = RenderContext(
                actions = actions,
                initialData = mapOf(
                    "uses" to listOf(mapOf("id" to "lidar_tree", "label" to "Lidar tree", "sprite" to "tree")),
                    "cells" to emptyList<Any?>(),
                    "instances" to if (hasTree) listOf(tree) else emptyList(),
                    "view_state" to mapOf("quarter_turns" to quarterTurns, "centre_mm" to listOf(focusX * TILE_MM, (rows - focusY) * TILE_MM)),
                ),
            )
            val check = SpecVisualCheck(this, context)
            check.render(tilemap, viewport = 500.dp)
            val region = check.region("world:map")
            check.tapAt("world:map", (region.right.value - region.left.value) / 2, (region.bottom.value - region.top.value) / 2 - upPx)
        }
        return Taps(tiles, instances)
    }

    @Test
    fun aTapAtTheFocusedTileReportsThatWorldTileInEveryQuarterTurn() {
        for (turns in 0..3) assertEquals(listOf(3 to 5), tapsAtTheFocus(turns, 3.5, 5.5, 0f, hasTree = false).tiles, "quarter turns $turns")
    }

    @Test
    fun aTapOnTheDrawnTreeReportsTheTreeInEveryQuarterTurn() {
        for (turns in 0..3) assertEquals(listOf("tree-01"), tapsAtTheFocus(turns, 1.5, 10.5, 40f, hasTree = true).instances, "quarter turns $turns")
    }
}
