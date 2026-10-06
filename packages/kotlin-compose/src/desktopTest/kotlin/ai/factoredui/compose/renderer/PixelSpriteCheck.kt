package ai.factoredui.compose.renderer

import ai.factoredui.compose.adapter.ActionHandler
import ai.factoredui.compose.pixel.EMBEDDED_PIXEL_ATLAS
import ai.factoredui.compose.schema.SpecNode
import ai.factoredui.compose.schema.SpecNodeType
import ai.factoredui.compose.schema.SpecValue
import ai.factoredui.compose.testing.SpecVisualCheck
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SIDE_MM = 1524.0

@OptIn(ExperimentalTestApi::class)
class PixelSpriteCheck {

    private val parcelTiles = 16

    private val tilemap = SpecNode(
        id = "world",
        type = SpecNodeType.TILEMAP,
        props = mapOf(
            "cols" to SpecValue.NumberValue(parcelTiles.toDouble()),
            "rows" to SpecValue.NumberValue(parcelTiles.toDouble()),
            "tile_area" to SpecValue.NumberValue(25.0),
            "uses" to SpecValue.StringValue("{uses}"),
            "cells" to SpecValue.StringValue("{cells}"),
            "footprints" to SpecValue.StringValue("{footprints}"),
            "instances" to SpecValue.StringValue("{instances}"),
            "palette" to SpecValue.StringValue("none"),
            "view_state" to SpecValue.StringValue("{view_state}"),
            "look" to SpecValue.StringValue("pixel"),
            "controlled" to SpecValue.BooleanValue(true),
            "on_tile_tap" to SpecValue.StringValue("world.tileTapped"),
            "on_instance_tap" to SpecValue.StringValue("world.instanceTapped"),
        ),
    )

    private fun uses(tractorColour: String = "#3060D0") = listOf(
        mapOf("id" to "hoop_house", "label" to "Hoop house", "sprite" to "arch"),
        mapOf("id" to "lidar_tree", "label" to "Lidar tree", "sprite" to "tree"),
        mapOf("id" to "tractor", "label" to "Tractor", "sprite" to "block", "color" to tractorColour),
    )

    private fun instance(id: String, type: String, groundX: Double, groundY: Double, crownMm: Double? = null) = mapOf(
        "id" to id, "type" to type, "x_mm" to groundX * SIDE_MM, "y_mm" to (parcelTiles - groundY) * SIDE_MM, "crown_radius_mm" to crownMm, "rotation_deg" to 0.0,
    )

    private fun centredOn(groundX: Double, groundY: Double) = mapOf("centre_mm" to listOf(groundX * SIDE_MM, (parcelTiles - groundY) * SIDE_MM))

    private fun contextOf(
        footprints: List<Map<String, Any?>> = emptyList(),
        instances: List<Map<String, Any?>> = emptyList(),
        centre: Map<String, Any?>,
        tractorColour: String = "#3060D0",
        actions: Map<String, ActionHandler> = emptyMap(),
    ) = RenderContext(
        actions = actions,
        initialData = mapOf("uses" to uses(tractorColour), "cells" to emptyList<Any?>(), "footprints" to footprints, "instances" to instances, "view_state" to centre),
    )

    private fun spriteColours(name: String): Set<Int> {
        val sprite = EMBEDDED_PIXEL_ATLAS.manifest.scales.getValue("32-5ft").byName.getValue(name)
        return EMBEDDED_PIXEL_ATLAS.sheets("32-5ft")[sprite.sheet].crop(sprite.x, sprite.y, sprite.width, sprite.height).argb.filter { (it ushr 24) == 0xFF }.toSet()
    }

    private fun centreColours(check: SpecVisualCheck, half: Int, liftPx: Int = 0): List<Int> {
        val region = check.region("world:map")
        val image = check.png().toPixelMap()
        val centreX = ((region.left.value + region.right.value) / 2).toInt()
        val centreY = ((region.top.value + region.bottom.value) / 2).toInt() - liftPx
        return (centreY - half until centreY + half).flatMap { y -> (centreX - half until centreX + half).map { x -> image[x, y].toArgb() } }
    }

    private fun tapFromCentre(check: SpecVisualCheck, upPx: Float) {
        val region = check.region("world:map")
        check.tapAt("world:map", (region.right.value - region.left.value) / 2, (region.bottom.value - region.top.value) / 2 - upPx)
    }

    @Test
    fun aHoopHouseFootprintIsDrawnFromTheHoopHouseArt() = runComposeUiTest {
        val footprint = mapOf("id" to "h", "use" to "hoop_house", "col" to 3, "row" to 5, "width" to 10, "height" to 5)
        val check = SpecVisualCheck(this, contextOf(footprints = listOf(footprint), centre = centredOn(8.0, 7.5)))
        check.render(tilemap, viewport = 500.dp)
        val hoop = spriteColours("hoop_house/48x24/SE")
        val block = centreColours(check, 20, liftPx = 30)
        assertTrue(block.count { it in hoop } > block.size * 0.9, "the middle of the footprint is hoop-house art")
    }

    @Test
    fun aBlueTractorIsDrawnInTheBlueRampAndNotTheRedOne() = runComposeUiTest {
        val check = SpecVisualCheck(this, contextOf(instances = listOf(instance("t1", "tractor", 8.0, 8.0)), centre = centredOn(8.0, 8.0)))
        check.render(tilemap, viewport = 500.dp)
        val ramps = EMBEDDED_PIXEL_ATLAS.manifest.swaps.getValue("tractor").ramps
        val block = centreColours(check, 45, liftPx = 20)
        assertTrue(block.count { it in ramps.getValue("Blue") } > 20, "the body is blue")
        assertEquals(0, block.count { it in ramps.getValue("Red") }, "no red is left")
    }

    @Test
    fun tappingTheDrawnCrownReportsTheTreeAndTappingGroundBelowReportsTheTile() = runComposeUiTest {
        val instanceTaps = mutableListOf<Map<String, Any?>>()
        val tileTaps = mutableListOf<Map<String, Any?>>()
        val actions = mapOf<String, ActionHandler>(
            "world.instanceTapped" to { params -> instanceTaps.add(params) },
            "world.tileTapped" to { params -> tileTaps.add(params) },
        )
        val check = SpecVisualCheck(this, contextOf(instances = listOf(instance("tree-01", "lidar_tree", 8.0, 8.0, crownMm = 3000.0)), centre = centredOn(8.0, 8.0), actions = actions))
        check.render(tilemap, viewport = 500.dp)
        tapFromCentre(check, upPx = 40f)
        assertEquals(listOf("tree-01"), instanceTaps.map { it["id"] })
        assertTrue(tileTaps.isEmpty())
        tapFromCentre(check, upPx = -60f)
        assertEquals(1, tileTaps.size, "well below the sprite is ground")
        assertEquals(1, instanceTaps.size)
    }
}
