package ai.factoredui.worldbuilder

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun lidarHost() = WorldBuilderHost(openSession(File(designDirectory(), "worlds/parcel-lidar-sample.world.json").path))

@Suppress("UNCHECKED_CAST")
private fun WorldBuilderHost.footprints(): List<Map<String, Any?>> = bindings()["footprints"] as List<Map<String, Any?>>

@Suppress("UNCHECKED_CAST")
private fun WorldBuilderHost.instances(): List<Map<String, Any?>> = bindings()["instances"] as List<Map<String, Any?>>

class WorldBuilderFootprintsTest {

    @Test
    fun aTwoTileObjectIsOneFootprintWithItsWidthAndHeight() {
        val host = parcelHost()
        host.tap(3, 3, "hoop_house")
        val footprint = host.footprints().single()
        assertEquals(listOf("hoop_house", 3, 3, 2, 1), listOf(footprint["use"], footprint["col"], footprint["row"], footprint["width"], footprint["height"]))
        assertTrue((footprint["id"] as String).isNotEmpty())
    }

    @Test
    fun theTileCellsAreNoLongerSentOneRecordPerTile() {
        val host = parcelHost()
        host.tap(3, 3, "hoop_house")
        assertEquals(emptyList<Any?>(), host.bindings()["cells"])
    }

    @Test
    fun aRemovedObjectLeavesNoFootprint() {
        val host = parcelHost()
        host.tap(3, 3, "hoop_house")
        host.tap(3, 3, "erase")
        assertEquals(emptyList<Map<String, Any?>>(), host.footprints())
    }

    @Test
    fun theLidarSampleReachesTheRendererAsTenPlainRecordsWithNumbers() {
        val instances = lidarHost().instances()
        assertEquals(10, instances.size)
        val first = instances.first()
        assertTrue(first["x_mm"] is Number && first["y_mm"] is Number, "positions arrive as numbers: $first")
        assertTrue(first["crown_radius_mm"] is Number || first["crown_radius_mm"] == null, "crown is a number or absent: $first")
        assertEquals("measured", first["provenance"])
    }
}
